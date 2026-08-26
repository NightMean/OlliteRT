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

import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class InferenceGatewayTest {

  private val directExecutor = Executor { it.run() }
  private val lock = Any()
  private var clock = 0L
  private fun tick(): Long { clock += 10; return clock }

  @Test
  fun blockingExecutorRejectionSettlesAsError() = runBlocking {
    var finishCount = 0

    val result = InferenceGateway.execute(
      prompt = "hello",
      executor = Executor { throw RejectedExecutionException("shutdown") },
      inferenceLock = lock,
      resetConversation = { fail("preparation must not run") },
      runInference = { _, _, _ -> fail("inference must not run") },
      cancelInference = {},
      onInferenceFinished = { finishCount++ },
      elapsedMs = { tick() },
    )

    assertEquals("executor_rejected", result.error)
    assertEquals(1, finishCount)
  }

  @Test
  fun streamingExecutorRejectionReportsAndSettlesError() {
    val error = AtomicReference<String?>(null)
    var finishCount = 0

    InferenceGateway.executeStreaming(
      prompt = "hello",
      executor = Executor { throw RejectedExecutionException("shutdown") },
      inferenceLock = lock,
      resetConversation = { fail("preparation must not run") },
      runInference = { _, _, _ -> fail("inference must not run") },
      cancelInference = {},
      onToken = { _, _, _ -> fail("tokens must not be emitted") },
      onError = error::set,
      onInferenceFinished = { finishCount++ },
    )

    assertEquals("executor_rejected", error.get())
    assertEquals(1, finishCount)
  }

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
  fun streamingNativeErrorCancelsAndRecoversExactlyOnce() {
    val prepareCount = AtomicInteger(0)
    val recoveryCount = AtomicInteger(0)
    val nativeCancelCount = AtomicInteger(0)
    val errors = mutableListOf<String>()
    var finishCount = 0

    InferenceGateway.executeStreaming(
      prompt = "error",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = { prepareCount.incrementAndGet() },
      runInference = { _, _, onError ->
        onError("native_error")
        onError("late_error")
      },
      cancelInference = { nativeCancelCount.incrementAndGet() },
      recoverConversation = { recoveryCount.incrementAndGet() },
      onToken = { _, _, _ -> fail("should not receive tokens") },
      onError = { errors += it },
      onInferenceFinished = { finishCount++ },
    )

    assertEquals(listOf("native_error"), errors)
    assertEquals(1, nativeCancelCount.get())
    assertEquals(1, prepareCount.get())
    assertEquals(1, recoveryCount.get())
    assertEquals(1, finishCount)
  }

  @Test
  fun streamingTimeoutCancelsAndRecoversExactlyOnce() {
    val prepareCount = AtomicInteger(0)
    val recoveryCount = AtomicInteger(0)
    val nativeCancelCount = AtomicInteger(0)
    val errors = mutableListOf<String>()

    InferenceGateway.executeStreaming(
      prompt = "timeout",
      timeoutSeconds = 1,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = { prepareCount.incrementAndGet() },
      runInference = { _, _, _ -> },
      cancelInference = { nativeCancelCount.incrementAndGet() },
      recoverConversation = { recoveryCount.incrementAndGet() },
      onToken = { _, _, _ -> fail("should not receive tokens") },
      onError = { errors += it },
    )

    assertEquals(listOf("timeout"), errors)
    assertEquals(1, nativeCancelCount.get())
    assertEquals(1, prepareCount.get())
    assertEquals(1, recoveryCount.get())
  }

  @Test
  fun streamingExternalCancellationSettlesBeforeFinish() {
    val threadPool = Executors.newSingleThreadExecutor()
    val cancellationReady = CountDownLatch(1)
    val inferenceStarted = CountDownLatch(1)
    val finished = CountDownLatch(1)
    val cancellation = AtomicReference<InferenceGateway.InferenceControl>()
    val prepareCount = AtomicInteger(0)
    val recoveryCount = AtomicInteger(0)
    val nativeCancelCount = AtomicInteger(0)
    val settlementOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
    try {
      InferenceGateway.executeStreaming(
        prompt = "cancel",
        timeoutSeconds = 30,
        executor = threadPool,
        inferenceLock = lock,
        resetConversation = {
          prepareCount.incrementAndGet()
          settlementOrder += "prepare"
        },
        runInference = { _, _, _ -> inferenceStarted.countDown() },
        cancelInference = {
          nativeCancelCount.incrementAndGet()
          settlementOrder += "cancel"
        },
        recoverConversation = {
          recoveryCount.incrementAndGet()
          settlementOrder += "recover"
        },
        onToken = { _, _, _ -> fail("should not receive tokens") },
        onError = { assertEquals("cancelled", it) },
        onInferenceFinished = {
          settlementOrder += "finish"
          finished.countDown()
        },
        onExecutionReady = {
          cancellation.set(it)
          cancellationReady.countDown()
        },
      )

      assertTrue(cancellationReady.await(5, TimeUnit.SECONDS))
      assertTrue(inferenceStarted.await(5, TimeUnit.SECONDS))
      assertTrue(cancellation.get().cancel(InferenceGateway.CancellationReason.EXTERNAL))
      assertTrue(finished.await(5, TimeUnit.SECONDS))
      assertEquals(listOf("prepare", "cancel", "recover", "finish"), settlementOrder)
      assertEquals(1, nativeCancelCount.get())
      assertEquals(1, prepareCount.get())
      assertEquals(1, recoveryCount.get())
    } finally {
      threadPool.shutdownNow()
    }
  }

  @Test
  fun streamingSuccessfulStopCancelsAndRecoversWithoutError() {
    val control = AtomicReference<InferenceGateway.InferenceControl>()
    val nativeCancelCount = AtomicInteger(0)
    val recoveryCount = AtomicInteger(0)
    val receivedTokens = mutableListOf<String>()
    var doneReceived = false
    val errors = mutableListOf<String>()

    InferenceGateway.executeStreaming(
      prompt = "stop",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("before-stop", false, null)
        assertTrue(control.get().stopSuccessfully())
        onPartial("late", false, null)
        onPartial("", true, null)
      },
      cancelInference = { nativeCancelCount.incrementAndGet() },
      recoverConversation = { recoveryCount.incrementAndGet() },
      onToken = { partial, done, _ ->
        if (partial.isNotEmpty()) receivedTokens += partial
        doneReceived = doneReceived || done
      },
      onError = { errors += it },
      onExecutionReady = { control.set(it) },
    )

    assertEquals(listOf("before-stop"), receivedTokens)
    assertTrue(!doneReceived)
    assertTrue(errors.isEmpty())
    assertEquals(1, nativeCancelCount.get())
    assertEquals(1, recoveryCount.get())
  }

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
      // terminal wait times out. The first error must win.
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

  @Test
  fun cancellationSettlementTimeoutQuarantinesRuntimeUntilLateSettlement() = runBlocking {
    val threadPool = Executors.newSingleThreadExecutor()
    val runtimeHealth = InferenceRuntimeHealth()
    val inferenceStarted = CountDownLatch(1)
    val cancelEntered = CountDownLatch(1)
    val releaseCancel = CountDownLatch(1)
    var cancelledResult: InferenceResult? = null
    try {
      val cancelledJob = launch(Dispatchers.Default) {
        cancelledResult = InferenceGateway.execute(
          prompt = "hung-cancel",
          timeoutSeconds = 30,
          executor = threadPool,
          inferenceLock = lock,
          resetConversation = {},
          runInference = { _, _, _ -> inferenceStarted.countDown() },
          cancelInference = {
            cancelEntered.countDown()
            releaseCancel.await()
          },
          elapsedMs = { tick() },
          runtimeHealth = runtimeHealth,
          postCancelSettlementTimeoutMs = 50,
        )
      }
      assertTrue(inferenceStarted.await(5, TimeUnit.SECONDS))
      cancelledJob.cancel()
      assertTrue(cancelEntered.await(5, TimeUnit.SECONDS))
      cancelledJob.join()

      assertEquals("client_disconnected", cancelledResult?.error)
      assertTrue(runtimeHealth.isQuarantined())
      val rejected = InferenceGateway.execute(
        prompt = "must-not-queue",
        executor = directExecutor,
        inferenceLock = lock,
        resetConversation = {},
        runInference = { _, onPartial, _ -> onPartial("unsafe", true, null) },
        cancelInference = {},
        elapsedMs = { tick() },
        runtimeHealth = runtimeHealth,
      )
      assertEquals(INFERENCE_RUNTIME_QUARANTINED_ERROR, rejected.error)

      releaseCancel.countDown()
      threadPool.submit {}.get(5, TimeUnit.SECONDS)
      assertTrue(!runtimeHealth.isQuarantined())
    } finally {
      releaseCancel.countDown()
      threadPool.shutdownNow()
    }
  }

  // ── onInferenceFinished tests ───────────────────────────────────────────

  @Test
  fun queuedExternalCancellationDoesNotDispatchOrCancelNativeInference() = runBlocking {
    val threadPool = Executors.newSingleThreadExecutor()
    val executorOccupied = CountDownLatch(1)
    val releaseExecutor = CountDownLatch(1)
    val cancellationReady = CountDownLatch(1)
    val cancellation = AtomicReference<InferenceGateway.InferenceControl>()
    val dispatchCount = AtomicInteger(0)
    val nativeCancelCount = AtomicInteger(0)
    try {
      threadPool.execute {
        executorOccupied.countDown()
        releaseExecutor.await(5, TimeUnit.SECONDS)
      }
      assertTrue(executorOccupied.await(5, TimeUnit.SECONDS))

      val deferred = async(Dispatchers.Default) {
        InferenceGateway.execute(
          prompt = "queued",
          timeoutSeconds = 30,
          executor = threadPool,
          inferenceLock = lock,
          resetConversation = {},
          runInference = { _, _, _ -> dispatchCount.incrementAndGet() },
          cancelInference = { nativeCancelCount.incrementAndGet() },
          elapsedMs = { tick() },
          onExecutionReady = {
            cancellation.set(it)
            cancellationReady.countDown()
          },
        )
      }

      assertTrue(cancellationReady.await(5, TimeUnit.SECONDS))
      assertTrue(cancellation.get().cancel(InferenceGateway.CancellationReason.EXTERNAL))
      val result = withTimeout(5_000) { deferred.await() }
      assertEquals("cancelled", result.error)
      assertEquals(0, dispatchCount.get())
      assertEquals(0, nativeCancelCount.get())

      releaseExecutor.countDown()
      threadPool.shutdown()
      assertTrue(threadPool.awaitTermination(5, TimeUnit.SECONDS))
      assertEquals(0, dispatchCount.get())
      assertEquals(0, nativeCancelCount.get())
    } finally {
      releaseExecutor.countDown()
      threadPool.shutdownNow()
    }
  }

  @Test
  fun runningExternalCancellationSettlesExactlyOnceBeforeReturning() = runBlocking {
    val threadPool = Executors.newSingleThreadExecutor()
    val cancellationReady = CountDownLatch(1)
    val inferenceStarted = CountDownLatch(1)
    val cancellation = AtomicReference<InferenceGateway.InferenceControl>()
    val prepareCount = AtomicInteger(0)
    val recoveryCount = AtomicInteger(0)
    val nativeCancelCount = AtomicInteger(0)
    val settlementOrder = java.util.Collections.synchronizedList(mutableListOf<String>())
    try {
      val deferred = async(Dispatchers.Default) {
        InferenceGateway.execute(
          prompt = "running",
          timeoutSeconds = 30,
          executor = threadPool,
          inferenceLock = lock,
          resetConversation = {
            prepareCount.incrementAndGet()
            settlementOrder += "prepare"
          },
          runInference = { _, _, _ -> inferenceStarted.countDown() },
          cancelInference = {
            nativeCancelCount.incrementAndGet()
            settlementOrder += "cancel"
          },
          recoverConversation = {
            recoveryCount.incrementAndGet()
            settlementOrder += "recover"
          },
          onInferenceFinished = { settlementOrder += "finish" },
          elapsedMs = { tick() },
          onExecutionReady = {
            cancellation.set(it)
            cancellationReady.countDown()
          },
        )
      }

      assertTrue(cancellationReady.await(5, TimeUnit.SECONDS))
      assertTrue(inferenceStarted.await(5, TimeUnit.SECONDS))
      assertTrue(cancellation.get().cancel(InferenceGateway.CancellationReason.EXTERNAL))
      val result = withTimeout(5_000) { deferred.await() }

      assertEquals("cancelled", result.error)
      assertEquals(listOf("prepare", "cancel", "recover", "finish"), settlementOrder)
      assertEquals(1, nativeCancelCount.get())
      assertEquals(1, prepareCount.get())
      assertEquals(1, recoveryCount.get())
      assertTrue(!cancellation.get().cancel(InferenceGateway.CancellationReason.EXTERNAL))
      assertEquals(1, nativeCancelCount.get())
    } finally {
      threadPool.shutdownNow()
    }
  }

  @Test
  fun completedInferenceIgnoresLateExternalCancellation() = runBlocking {
    val cancellation = AtomicReference<InferenceGateway.InferenceControl>()
    val nativeCancelCount = AtomicInteger(0)

    val result = InferenceGateway.execute(
      prompt = "done",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ -> onPartial("ok", true, null) },
      cancelInference = { nativeCancelCount.incrementAndGet() },
      elapsedMs = { tick() },
      onExecutionReady = { cancellation.set(it) },
    )

    assertEquals("ok", result.output)
    assertTrue(!cancellation.get().cancel(InferenceGateway.CancellationReason.EXTERNAL))
    assertEquals(0, nativeCancelCount.get())
  }

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

  // Uses 1s real-time wait — CountDownLatch.await() can't use virtual time (Java blocking primitive).
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
