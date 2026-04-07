package com.ollitert.llm.server.service

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
   */
  fun executeStreaming(
    prompt: String,
    timeoutSeconds: Long = 90,
    executor: Executor,
    inferenceLock: Any,
    resetConversation: () -> Unit,
    runInference: InferenceRunner,
    cancelInference: () -> Unit,
    elapsedMs: () -> Long,
    onToken: (partial: String, done: Boolean, thought: String?) -> Unit,
    onError: (error: String) -> Unit,
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
          if (!errorOccurred) {
            onError(t.message ?: "unknown_error")
            try { cancelInference() } catch (_: Throwable) {}
          }
        }
      }
    }
  }

  fun execute(
    prompt: String,
    timeoutSeconds: Long = 30,
    executor: Executor,
    inferenceLock: Any,
    resetConversation: () -> Unit,
    runInference: InferenceRunner,
    cancelInference: () -> Unit,
    elapsedMs: () -> Long,
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
