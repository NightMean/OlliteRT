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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LlmHttpInferenceGatewayTest {

  private val directExecutor = Executor { it.run() }
  private val lock = Any()
  private var clock = 0L
  private fun tick(): Long { clock += 10; return clock }

  @Test
  fun successfulInferenceReturnsOutput() = runBlocking {
    val result = InferenceGateway.execute(
      prompt = "hello",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("world", false, null)
        onPartial("", true, null)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertEquals("world", result.output)
    assertNull(result.error)
    assertTrue(result.ttfbMs >= 0)
  }

  @Test
  fun multiplePartialsAccumulate() = runBlocking {
    val result = InferenceGateway.execute(
      prompt = "hi",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("a", false, null)
        onPartial("b", false, null)
        onPartial("c", false, null)
        onPartial("", true, null)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertEquals("abc", result.output)
    assertNull(result.error)
  }

  @Test
  fun errorFromInferenceIsReported() = runBlocking {
    val result = InferenceGateway.execute(
      prompt = "fail",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, _, onError ->
        onError("model crashed")
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertNull(result.output)
    assertEquals("model crashed", result.error)
  }

  @Test
  fun exceptionDuringInferenceIsCaught() = runBlocking {
    val result = InferenceGateway.execute(
      prompt = "boom",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = { throw RuntimeException("reset failed") },
      runInference = { _, _, _ -> },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertNull(result.output)
    assertNotNull(result.error)
    assertTrue(result.error.orEmpty().contains("reset failed"))
  }

  @Test
  fun exceptionWithNullMessageReportsUnknownError() = runBlocking {
    val result = InferenceGateway.execute(
      prompt = "null-msg",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = { throw object : RuntimeException(null as String?) {} },
      runInference = { _, _, _ -> },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertNull(result.output)
    assertEquals("unknown_error", result.error)
  }

  @Test
  fun cancelInferenceCalledOnError() = runBlocking {
    var cancelled = false
    InferenceGateway.execute(
      prompt = "x",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, _, onError -> onError("err") },
      cancelInference = { cancelled = true },
      elapsedMs = { tick() },
    )
    assertTrue(cancelled)
  }

  @Test
  fun emptyPartialDoesNotCountAsTtfb() = runBlocking {
    val result = InferenceGateway.execute(
      prompt = "x",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("", false, null)
        onPartial("tok", false, null)
        onPartial("", true, null)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertEquals("tok", result.output)
    assertTrue(result.ttfbMs > 0)
  }

  @Test
  fun totalMsIsTracked() = runBlocking {
    clock = 0
    val result = InferenceGateway.execute(
      prompt = "x",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("ok", false, null)
        onPartial("", true, null)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertTrue(result.totalMs > 0)
  }

  @Test
  fun thinkingContentAccumulates() = runBlocking {
    val result = InferenceGateway.execute(
      prompt = "think",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("", false, "step1 ")
        onPartial("", false, "step2")
        onPartial("answer", false, null)
        onPartial("", true, null)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertEquals("answer", result.output)
    assertEquals("step1 step2", result.thinking)
  }

  @Test
  fun noThinkingReturnsNull() = runBlocking {
    val result = InferenceGateway.execute(
      prompt = "no-think",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("plain", false, null)
        onPartial("", true, null)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertEquals("plain", result.output)
    assertNull(result.thinking)
  }

  // ── execute() onInferenceFinished tests ──────────────────────────────────

  @Test
  fun blockingOnInferenceFinishedCalledInsideLock() = runBlocking {
    var finishedCalled = false
    var lockHeldDuringFinished = false
    InferenceGateway.execute(
      prompt = "x",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("tok", false, null)
        onPartial("", true, null)
      },
      cancelInference = {},
      onInferenceFinished = {
        finishedCalled = true
        lockHeldDuringFinished = Thread.holdsLock(lock)
      },
      elapsedMs = { tick() },
    )
    assertTrue("onInferenceFinished must be called", finishedCalled)
    assertTrue("onInferenceFinished must run inside inferenceLock", lockHeldDuringFinished)
  }

  @Test
  fun blockingOnInferenceFinishedCalledOnError() = runBlocking {
    var finishedCalled = false
    InferenceGateway.execute(
      prompt = "x",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, _, onError -> onError("boom") },
      cancelInference = {},
      onInferenceFinished = { finishedCalled = true },
      elapsedMs = { tick() },
    )
    assertTrue("onInferenceFinished must be called on error path", finishedCalled)
  }

  @Test
  fun blockingOnInferenceFinishedCalledOnException() = runBlocking {
    var finishedCalled = false
    InferenceGateway.execute(
      prompt = "x",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = { throw RuntimeException("crash") },
      runInference = { _, _, _ -> },
      cancelInference = {},
      onInferenceFinished = { finishedCalled = true },
      elapsedMs = { tick() },
    )
    assertTrue("onInferenceFinished must be called on exception path", finishedCalled)
  }

  // ── executeStreaming tests ────────────────────────────────────────────────

  private fun streaming(
    runInference: InferenceFn,
    cancelInference: () -> Unit = {},
    onToken: (String, Boolean, String?) -> Unit,
    onError: (String) -> Unit = { fail("unexpected error: $it") },
    onInferenceFinished: () -> Unit = {},
  ) {
    InferenceGateway.executeStreaming(
      prompt = "p",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = runInference,
      cancelInference = cancelInference,
      onToken = onToken,
      onError = onError,
      onInferenceFinished = onInferenceFinished,
    )
  }

  @Test
  fun streamingTokensAreDeliveredInOrder() {
    val tokens = mutableListOf<String>()
    var doneReceived = false
    streaming(
      runInference = { _, onPartial, _ ->
        onPartial("foo", false, null)
        onPartial("bar", false, null)
        onPartial("", true, null)
      },
      onToken = { partial, done, _ ->
        if (partial.isNotEmpty()) tokens.add(partial)
        if (done) doneReceived = true
      },
    )
    assertEquals(listOf("foo", "bar"), tokens)
    assertTrue(doneReceived)
  }

  @Test
  fun streamingDoneSignalDeliveredWithLastToken() {
    var lastTokenWasDone = false
    streaming(
      runInference = { _, onPartial, _ ->
        onPartial("tok", true, null)
      },
      onToken = { partial, done, _ ->
        if (partial == "tok" && done) lastTokenWasDone = true
      },
    )
    assertTrue(lastTokenWasDone)
  }

  @Test
  fun streamingErrorIsReported() {
    var errorMsg: String? = null
    var cancelled = false
    streaming(
      runInference = { _, _, onError -> onError("boom") },
      cancelInference = { cancelled = true },
      onToken = { _, _, _ -> fail("should not receive tokens on error") },
      onError = { errorMsg = it },
    )
    assertEquals("boom", errorMsg)
    assertTrue(cancelled)
  }

  @Test
  fun streamingExceptionIsReportedAsError() {
    var errorMsg: String? = null
    streaming(
      runInference = { _, _, _ -> throw RuntimeException("crash") },
      onToken = { _, _, _ -> fail("should not receive tokens") },
      onError = { errorMsg = it },
    )
    assertNotNull(errorMsg)
    assertTrue(errorMsg.orEmpty().contains("crash"))
  }

  @Test
  fun streamingThinkingTokensAreForwarded() {
    val thoughts = mutableListOf<String>()
    val tokens = mutableListOf<String>()
    streaming(
      runInference = { _, onPartial, _ ->
        onPartial("", false, "thinking...")
        onPartial("answer", false, null)
        onPartial("", true, null)
      },
      onToken = { partial, _, thought ->
        if (!thought.isNullOrEmpty()) thoughts.add(thought)
        if (partial.isNotEmpty()) tokens.add(partial)
      },
    )
    assertEquals(listOf("thinking..."), thoughts)
    assertEquals(listOf("answer"), tokens)
  }

  // ── Cancellation tests ──────────────────────────────────────────────────

  @Test
  fun cancellationTriggersCancelInference() = runBlocking {
    val threadPool = Executors.newSingleThreadExecutor()
    var cancelled = false
    val inferenceStarted = CountDownLatch(1)
    try {
      val job = launch(Dispatchers.Default) {
        InferenceGateway.execute(
          prompt = "long",
          timeoutSeconds = 30,
          executor = threadPool,
          inferenceLock = lock,
          resetConversation = {},
          runInference = { _, _, _ ->
            inferenceStarted.countDown()
            Thread.sleep(5000)
          },
          cancelInference = { cancelled = true },
          elapsedMs = { tick() },
        )
      }
      assertTrue("inference should start within 5s", inferenceStarted.await(5, TimeUnit.SECONDS))
      job.cancel()
      job.join()
      assertTrue("cancelInference should be called on coroutine cancellation", cancelled)
    } finally {
      threadPool.shutdownNow()
    }
  }

  @Test
  fun concurrentErrorAndTimeoutFirstErrorWins() = runBlocking {
    val threadPool = Executors.newSingleThreadExecutor()
    try {
      val result = InferenceGateway.execute(
        prompt = "race",
        timeoutSeconds = 1,
        executor = threadPool,
        inferenceLock = lock,
        resetConversation = {},
        runInference = { _, _, onError ->
          onError("inference_failed")
          // Don't signal done — let the latch timeout
          Thread.sleep(3000)
        },
        cancelInference = {},
        elapsedMs = { tick() },
      )
      // The onError callback fires first with "inference_failed", then the
      // lifecycleLatch times out. The first error must win.
      assertEquals("inference_failed", result.error)
    } finally {
      threadPool.shutdownNow()
    }
  }

  @Test
  fun cancellationSetsClientDisconnectedError() = runBlocking {
    val threadPool = Executors.newSingleThreadExecutor()
    var inferenceResult: InferenceResult? = null
    val inferenceStarted = CountDownLatch(1)
    try {
      val job = launch(Dispatchers.Default) {
        inferenceResult = InferenceGateway.execute(
          prompt = "long",
          timeoutSeconds = 30,
          executor = threadPool,
          inferenceLock = lock,
          resetConversation = {},
          runInference = { _, _, _ ->
            inferenceStarted.countDown()
            Thread.sleep(5000)
          },
          cancelInference = {},
          elapsedMs = { tick() },
        )
      }
      assertTrue("inference should start within 5s", inferenceStarted.await(5, TimeUnit.SECONDS))
      job.cancel()
      job.join()
      assertNotNull("result should be set after cancellation", inferenceResult)
      assertEquals("client_disconnected", inferenceResult?.error)
    } finally {
      threadPool.shutdownNow()
    }
  }

  // ── onInferenceFinished tests ───────────────────────────────────────────

  @Test
  fun streamingOnInferenceFinishedCalledInsideLock() {
    var finishedCalled = false
    var lockHeldDuringFinished = false
    streaming(
      runInference = { _, onPartial, _ ->
        onPartial("tok", false, null)
        onPartial("", true, null)
      },
      onToken = { _, _, _ -> },
      onInferenceFinished = {
        finishedCalled = true
        lockHeldDuringFinished = Thread.holdsLock(lock)
      },
    )
    assertTrue("onInferenceFinished must be called", finishedCalled)
    assertTrue("onInferenceFinished must run inside inferenceLock", lockHeldDuringFinished)
  }

  @Test
  fun streamingOnInferenceFinishedCalledOnError() {
    var finishedCalled = false
    streaming(
      runInference = { _, _, onError -> onError("boom") },
      onToken = { _, _, _ -> },
      onError = { },
      onInferenceFinished = { finishedCalled = true },
    )
    assertTrue("onInferenceFinished must be called on error path", finishedCalled)
  }

  @Test
  fun streamingOnInferenceFinishedCalledOnException() {
    var finishedCalled = false
    streaming(
      runInference = { _, _, _ -> throw RuntimeException("crash") },
      onToken = { _, _, _ -> },
      onError = { },
      onInferenceFinished = { finishedCalled = true },
    )
    assertTrue("onInferenceFinished must be called on exception path", finishedCalled)
  }

  @Test
  fun streamingOnInferenceFinishedCalledOnTimeout() {
    var finishedCalled = false
    InferenceGateway.executeStreaming(
      prompt = "p",
      timeoutSeconds = 1,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, _, _ -> },
      cancelInference = {},
      onToken = { _, _, _ -> },
      onError = { },
      onInferenceFinished = { finishedCalled = true },
    )
    assertTrue("onInferenceFinished must be called on timeout", finishedCalled)
  }
}
