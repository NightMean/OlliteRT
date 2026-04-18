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

package com.ollitert.llm.server.service

import com.ollitert.llm.server.data.BLOCKING_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.STREAMING_TIMEOUT_SECONDS
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

typealias InferenceRunner = (
  prompt: String,
  onPartial: (partial: String, done: Boolean, thought: String?) -> Unit,
  onError: (message: String) -> Unit,
) -> Unit

object LlmHttpInferenceGateway {

  /**
   * Fires inference on [executor] and delivers tokens via [onToken] as they arrive.
   * Returns immediately; the caller receives the stream via the [PipedOutputStream] pattern.
   * [onToken] is called with (partial, false) for each token and (*, true) once when done.
   * [onError] is called instead of [onToken] if inference fails.
   *
   * @param onCaughtThrowable Optional callback invoked with the full [Throwable] when an
   *   exception is caught during inference. Used by [LlmHttpService] to emit verbose debug
   *   stack traces when debug mode is enabled. The gateway itself only forwards [Throwable.message]
   *   via [onError] — this callback preserves the full stack trace for diagnostics.
   */

  fun executeStreaming(
    prompt: String,
    timeoutSeconds: Long = STREAMING_TIMEOUT_SECONDS,
    executor: Executor,
    inferenceLock: Any,
    resetConversation: () -> Unit,
    runInference: InferenceRunner,
    cancelInference: () -> Unit,
    elapsedMs: () -> Long,
    onToken: (partial: String, done: Boolean, thought: String?) -> Unit,
    onError: (error: String) -> Unit,
    onCaughtThrowable: ((Throwable) -> Unit)? = null,
  ) {
    executor.execute {
      synchronized(inferenceLock) {
        val latch = CountDownLatch(1)
        var errorOccurred = false
        try {
          resetConversation()
          runInference(
            prompt,
            { partial, done, thought ->
              onToken(partial, done, thought)
              if (done) latch.countDown()
            },
            { e ->
              errorOccurred = true
              onError(e)
              try { cancelInference() } catch (_: Throwable) {}
              latch.countDown()
            },
          )
          val completed = latch.await(timeoutSeconds, TimeUnit.SECONDS)
          if (!completed && !errorOccurred) {
            onError("timeout")
            cancelInference()
            resetConversation()
          }
        } catch (t: Throwable) {
          // Reclaim memory before reporting the error if OOM
          if (t is OutOfMemoryError) System.gc()
          onCaughtThrowable?.invoke(t)
          if (!errorOccurred) {
            onError(t.message ?: "unknown_error")
            try { cancelInference() } catch (_: Throwable) {}
          }
        }
      }
    }
  }

  /**
   * @param onCaughtThrowable Optional callback invoked with the full [Throwable] when an
   *   exception is caught during inference. See [executeStreaming] for details.
   */
  fun execute(
    prompt: String,
    timeoutSeconds: Long = BLOCKING_TIMEOUT_SECONDS,
    executor: Executor,
    inferenceLock: Any,
    resetConversation: () -> Unit,
    runInference: InferenceRunner,
    cancelInference: () -> Unit,
    elapsedMs: () -> Long,
    onCaughtThrowable: ((Throwable) -> Unit)? = null,
  ): InferenceResult {
    val sb = StringBuilder()
    val thinkingSb = StringBuilder()
    val inferenceLatch = CountDownLatch(1)
    val lifecycleLatch = CountDownLatch(1)
    var error: String? = null
    val startMs = elapsedMs()
    var firstTokenMs: Long? = null

    executor.execute {
      synchronized(inferenceLock) {
        try {
          resetConversation()
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
              if (done) inferenceLatch.countDown()
            },
            { e -> error = e; inferenceLatch.countDown() },
          )
          val completed = inferenceLatch.await(timeoutSeconds, TimeUnit.SECONDS)
          if (!completed && error == null) {
            error = "timeout"
            cancelInference()
            resetConversation()
          } else if (error != null) {
            cancelInference()
          }
        } catch (t: Throwable) {
          if (t is OutOfMemoryError) System.gc()
          onCaughtThrowable?.invoke(t)
          error = t.message
          inferenceLatch.countDown()
        } finally {
          lifecycleLatch.countDown()
        }
      }
    }

    val completed = lifecycleLatch.await(timeoutSeconds + 5, TimeUnit.SECONDS)
    if (!completed && error == null) {
      error = "timeout"
    }
    val totalMs = elapsedMs() - startMs
    val thinkingResult = thinkingSb.toString().takeIf { it.isNotEmpty() }
    return InferenceResult(
      output = if (error != null) null else sb.toString(),
      thinking = if (error != null) null else thinkingResult,
      error = error,
      totalMs = totalMs,
      ttfbMs = firstTokenMs ?: -1,
    )
  }
}
