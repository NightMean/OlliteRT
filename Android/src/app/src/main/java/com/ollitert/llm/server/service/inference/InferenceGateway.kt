/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ollitert.llm.server.service.inference

import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.service.http.*

import android.util.Log
import com.ollitert.llm.server.data.BLOCKING_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.STREAMING_TIMEOUT_SECONDS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

data class InferenceResult(
  val output: String?,
  val thinking: String?,
  val error: String?,
  val totalMs: Long,
  val ttfbMs: Long,
)

typealias InferenceFn = (
  prompt: String,
  onPartial: (partial: String, done: Boolean, thought: String?) -> Unit,
  onError: (message: String) -> Unit,
) -> Unit

private const val TAG = "OlliteRT.Gateway"

private fun reportGatewayFailure(
  onCaughtThrowable: ((Throwable) -> Unit)?,
  message: String,
  throwable: Throwable,
) {
  try {
    onCaughtThrowable?.invoke(throwable)
  } catch (reportingFailure: Throwable) {
    // Diagnostics must not interrupt native cancellation, recovery, or final cleanup.
    Log.w(TAG, "Throwable reporter failed while handling: $message", reportingFailure)
  }
  Log.w(TAG, message, throwable)
}

object InferenceGateway {

  internal enum class CancellationReason {
    CALLER,
    EXTERNAL,
  }

  internal interface InferenceControl {
    /** Returns true only when this signal chose the request's terminal outcome. */
    fun cancel(reason: CancellationReason): Boolean

    /** Ends a protocol-level response successfully while stopping native generation. */
    fun stopSuccessfully(): Boolean
  }

  private sealed interface ExecutionOutcome {
    data object Success : ExecutionOutcome
    data object Stopped : ExecutionOutcome
    data object Timeout : ExecutionOutcome
    data class Cancelled(val reason: CancellationReason) : ExecutionOutcome
    data class Error(val message: String) : ExecutionOutcome
  }

  private enum class ExecutionPhase {
    QUEUED,
    PREPARING,
    DISPATCHING,
    RUNNING,
    SETTLING,
    FINISHED,
  }

  /** Owns native-dispatch and terminal-state decisions for one request. */
  private class RequestExecution(
    private val cancelNative: () -> Unit,
  ) : InferenceControl {
    private val stateLock = Any()
    private val terminalSignal = CountDownLatch(1)
    private val settledSignal = CountDownLatch(1)
    private var phase = ExecutionPhase.QUEUED
    private var outcome: ExecutionOutcome? = null
    private var nativeDispatched = false
    private var nativeCancellationStarted = false

    override fun cancel(reason: CancellationReason): Boolean {
      var settledBeforeDispatch = false
      val accepted = synchronized(stateLock) {
        if (outcome != null || phase == ExecutionPhase.FINISHED) return@synchronized false
        outcome = ExecutionOutcome.Cancelled(reason)
        terminalSignal.countDown()
        if (phase == ExecutionPhase.QUEUED) {
          phase = ExecutionPhase.FINISHED
          settledBeforeDispatch = true
        }
        true
      }
      if (settledBeforeDispatch) settledSignal.countDown()
      return accepted
    }

    override fun stopSuccessfully(): Boolean = complete(ExecutionOutcome.Stopped)

    fun beginPreparation(): Boolean = synchronized(stateLock) {
      if (phase != ExecutionPhase.QUEUED || outcome != null) return@synchronized false
      phase = ExecutionPhase.PREPARING
      true
    }

    /** Linearizes external cancellation with the synchronous native dispatch call. */
    fun dispatch(startNative: () -> Unit): Boolean = synchronized(stateLock) {
      if (phase != ExecutionPhase.PREPARING || outcome != null) return@synchronized false
      phase = ExecutionPhase.DISPATCHING
      nativeDispatched = true
      try {
        startNative()
      } catch (t: Throwable) {
        phase = ExecutionPhase.SETTLING
        throw t
      }
      phase = if (outcome == null) ExecutionPhase.RUNNING else ExecutionPhase.SETTLING
      true
    }

    fun complete(candidate: ExecutionOutcome): Boolean = synchronized(stateLock) {
      if (outcome != null || phase == ExecutionPhase.FINISHED) return@synchronized false
      outcome = candidate
      terminalSignal.countDown()
      true
    }

    fun awaitTerminal(timeoutSeconds: Long): ExecutionOutcome {
      if (!terminalSignal.await(timeoutSeconds, TimeUnit.SECONDS)) {
        complete(ExecutionOutcome.Timeout)
      }
      return synchronized(stateLock) { requireNotNull(outcome) }
    }

    fun settle(
      recover: () -> Unit,
      finish: () -> Unit,
      onFailure: (Throwable) -> Unit,
    ) {
      val terminal = synchronized(stateLock) {
        phase = ExecutionPhase.SETTLING
        requireNotNull(outcome)
      }
      val shouldCancelNative = synchronized(stateLock) {
        if (terminal.requiresNativeCancellation() && nativeDispatched && !nativeCancellationStarted) {
          nativeCancellationStarted = true
          true
        } else {
          false
        }
      }
      if (shouldCancelNative) {
        try {
          cancelNative()
        } catch (t: Throwable) {
          onFailure(t)
        }
      }
      if (terminal.requiresRecovery() && nativeDispatched) {
        try {
          recover()
        } catch (t: Throwable) {
          onFailure(t)
        }
      }
      try {
        finish()
      } catch (t: Throwable) {
        onFailure(t)
      } finally {
        synchronized(stateLock) { phase = ExecutionPhase.FINISHED }
        settledSignal.countDown()
      }
    }

    fun awaitSettlement() {
      settledSignal.await()
    }

    fun acceptsCallbacks(): Boolean = synchronized(stateLock) { outcome == null }

    private fun ExecutionOutcome.requiresNativeCancellation(): Boolean =
      this !is ExecutionOutcome.Success

    private fun ExecutionOutcome.requiresRecovery(): Boolean =
      this !is ExecutionOutcome.Success
  }

  /**
   * Fires inference on [executor] and delivers tokens via [onToken] as they arrive.
   * Returns immediately; the caller receives the stream via [onToken]/[onError] callbacks.
   * [onToken] is called with (partial, done, thought) for each token and (*, true, *) once when done.
   * [onError] is called instead of [onToken] if inference fails.
   *
   * @param onCaughtThrowable Optional callback invoked with the full [Throwable] when an
   *   exception is caught during inference. Used by [ServerService] to emit verbose debug
   *   stack traces when debug mode is enabled. The gateway itself only forwards [Throwable.message]
   *   via [onError] — this callback preserves the full stack trace for diagnostics.
   */

  internal fun executeStreaming(
    prompt: String,
    timeoutSeconds: Long = STREAMING_TIMEOUT_SECONDS,
    executor: Executor,
    inferenceLock: Any,
    resetConversation: () -> Unit,
    runInference: InferenceFn,
    cancelInference: () -> Unit,
    recoverConversation: () -> Unit = resetConversation,
    onToken: (partial: String, done: Boolean, thought: String?) -> Unit,
    onError: (error: String) -> Unit,
    onInferenceFinished: () -> Unit = {},
    onCaughtThrowable: ((Throwable) -> Unit)? = null,
    onExecutionReady: ((InferenceControl) -> Unit)? = null,
  ) {
    val execution = RequestExecution(cancelNative = cancelInference)
    onExecutionReady?.invoke(execution)
    try {
      executor.execute {
        synchronized(inferenceLock) {
          if (!execution.beginPreparation()) return@synchronized
          val terminal = try {
            resetConversation()
            execution.dispatch {
              runInference(
                prompt,
                { partial, done, thought ->
                  if (done) {
                    if (execution.complete(ExecutionOutcome.Success)) {
                      onToken(partial, true, thought)
                    }
                  } else if (execution.acceptsCallbacks()) {
                    onToken(partial, false, thought)
                  }
                },
                { message -> execution.complete(ExecutionOutcome.Error(message)) },
              )
            }
            execution.awaitTerminal(timeoutSeconds)
          } catch (t: Throwable) {
            if (t is OutOfMemoryError) System.gc()
            reportGatewayFailure(onCaughtThrowable, "Streaming inference failed", t)
            execution.complete(ExecutionOutcome.Error(t.message ?: "unknown_error"))
            execution.awaitTerminal(0)
          }
          try {
            when (terminal) {
              ExecutionOutcome.Success -> Unit
              ExecutionOutcome.Stopped -> Unit
              ExecutionOutcome.Timeout -> onError("timeout")
              is ExecutionOutcome.Cancelled -> onError(
                when (terminal.reason) {
                  CancellationReason.CALLER -> "client_disconnected"
                  CancellationReason.EXTERNAL -> "cancelled"
                }
              )
              is ExecutionOutcome.Error -> onError(terminal.message)
            }
          } catch (t: Throwable) {
            reportGatewayFailure(onCaughtThrowable, "Streaming terminal callback failed", t)
          } finally {
            execution.settle(
              recover = recoverConversation,
              finish = onInferenceFinished,
              onFailure = { t ->
                reportGatewayFailure(onCaughtThrowable, "Streaming settlement step failed", t)
              },
            )
          }
        }
      }
    } catch (t: Throwable) {
      reportGatewayFailure(onCaughtThrowable, "Streaming executor rejected inference", t)
      execution.complete(ExecutionOutcome.Error("executor_rejected"))
      try {
        onError("executor_rejected")
      } catch (callbackFailure: Throwable) {
        reportGatewayFailure(onCaughtThrowable, "Streaming rejection callback failed", callbackFailure)
      } finally {
        execution.settle(
          recover = recoverConversation,
          finish = onInferenceFinished,
          onFailure = { failure ->
            reportGatewayFailure(onCaughtThrowable, "Streaming rejection settlement failed", failure)
          },
        )
      }
    }
  }

  /**
   * @param onCaughtThrowable Optional callback invoked with the full [Throwable] when an
   *   exception is caught during inference. See [executeStreaming] for details.
   */
  internal suspend fun execute(
    prompt: String,
    timeoutSeconds: Long = BLOCKING_TIMEOUT_SECONDS,
    executor: Executor,
    inferenceLock: Any,
    resetConversation: () -> Unit,
    runInference: InferenceFn,
    cancelInference: () -> Unit,
    recoverConversation: () -> Unit = resetConversation,
    onInferenceFinished: () -> Unit = {},
    elapsedMs: () -> Long,
    onCaughtThrowable: ((Throwable) -> Unit)? = null,
    onExecutionReady: ((InferenceControl) -> Unit)? = null,
  ): InferenceResult {
    val sb = StringBuilder()
    val thinkingSb = StringBuilder()
    val startMs = elapsedMs()
    var firstTokenMs: Long? = null
    val execution = RequestExecution(cancelNative = cancelInference)
    onExecutionReady?.invoke(execution)

    try {
      executor.execute {
        synchronized(inferenceLock) {
          if (!execution.beginPreparation()) return@synchronized
          try {
            resetConversation()
            execution.dispatch {
              runInference(
                prompt,
                { partial, done, thought ->
                  if (partial.isNotEmpty()) {
                    if (firstTokenMs == null) {
                      firstTokenMs = elapsedMs() - startMs
                    }
                    sb.append(partial)
                  }
                  if (!thought.isNullOrEmpty()) {
                    thinkingSb.append(thought)
                  }
                  if (done) execution.complete(ExecutionOutcome.Success)
                },
                { message -> execution.complete(ExecutionOutcome.Error(message)) },
              )
            }
            execution.awaitTerminal(timeoutSeconds)
          } catch (t: Throwable) {
            if (t is OutOfMemoryError) System.gc()
            reportGatewayFailure(onCaughtThrowable, "Blocking inference failed", t)
            execution.complete(ExecutionOutcome.Error(t.message ?: "unknown_error"))
          } finally {
            execution.settle(
              recover = recoverConversation,
              finish = onInferenceFinished,
              onFailure = { t ->
                reportGatewayFailure(onCaughtThrowable, "Inference settlement step failed", t)
              },
            )
          }
        }
      }
    } catch (t: Throwable) {
      reportGatewayFailure(onCaughtThrowable, "Blocking executor rejected inference", t)
      execution.complete(ExecutionOutcome.Error("executor_rejected"))
      execution.settle(
        recover = recoverConversation,
        finish = onInferenceFinished,
        onFailure = { failure ->
          reportGatewayFailure(onCaughtThrowable, "Blocking rejection settlement failed", failure)
        },
      )
    }

    try {
      withContext(Dispatchers.IO) {
        runInterruptible { execution.awaitSettlement() }
      }
    } catch (_: InterruptedException) {
      execution.cancel(CancellationReason.CALLER)
      execution.awaitSettlement()
    } catch (_: CancellationException) {
      execution.cancel(CancellationReason.CALLER)
      execution.awaitSettlement()
    }
    val totalMs = elapsedMs() - startMs
    val thinkingResult = thinkingSb.toString().takeIf { it.isNotEmpty() }
    val finalError = when (val outcome = execution.awaitTerminal(0)) {
      ExecutionOutcome.Success -> null
      ExecutionOutcome.Stopped -> null
      ExecutionOutcome.Timeout -> "timeout"
      is ExecutionOutcome.Cancelled -> when (outcome.reason) {
        CancellationReason.CALLER -> "client_disconnected"
        CancellationReason.EXTERNAL -> "cancelled"
      }
      is ExecutionOutcome.Error -> outcome.message
    }
    // On error, discard all accumulated tokens — SDK errors may leave the output buffer
    // in a corrupted/incomplete state. The streaming path (executeStreaming) preserves
    // partial output because tokens are already delivered to the client via onToken callbacks.
    return InferenceResult(
      output = if (finalError != null) null else sb.toString(),
      thinking = if (finalError != null) null else thinkingResult,
      error = finalError,
      totalMs = totalMs,
      ttfbMs = firstTokenMs ?: -1,
    )
  }
}
