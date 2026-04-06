package com.ollite.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.Executor

class LlmHttpInferenceGatewayTest {

  private val directExecutor = Executor { it.run() }
  private val lock = Any()
  private var clock = 0L
  private fun tick(): Long { clock += 10; return clock }

  @Test
  fun successfulInferenceReturnsOutput() {
    val result = LlmHttpInferenceGateway.execute(
      prompt = "hello",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("world", false)
        onPartial("", true)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertEquals("world", result.output)
    assertNull(result.error)
    assertTrue(result.ttfbMs >= 0)
  }

  @Test
  fun multiplePartialsAccumulate() {
    val result = LlmHttpInferenceGateway.execute(
      prompt = "hi",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("a", false)
        onPartial("b", false)
        onPartial("c", false)
        onPartial("", true)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertEquals("abc", result.output)
    assertNull(result.error)
  }

  @Test
  fun errorFromInferenceIsReported() {
    val result = LlmHttpInferenceGateway.execute(
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
  fun exceptionDuringInferenceIsCaught() {
    val result = LlmHttpInferenceGateway.execute(
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
    assertTrue(result.error!!.contains("reset failed"))
  }

  @Test
  fun cancelInferenceCalledOnError() {
    var cancelled = false
    LlmHttpInferenceGateway.execute(
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
  fun emptyPartialDoesNotCountAsTtfb() {
    val result = LlmHttpInferenceGateway.execute(
      prompt = "x",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("", false)
        onPartial("tok", false)
        onPartial("", true)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertEquals("tok", result.output)
    assertTrue(result.ttfbMs > 0)
  }

  @Test
  fun totalMsIsTracked() {
    clock = 0
    val result = LlmHttpInferenceGateway.execute(
      prompt = "x",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = { _, onPartial, _ ->
        onPartial("ok", false)
        onPartial("", true)
      },
      cancelInference = {},
      elapsedMs = { tick() },
    )
    assertTrue(result.totalMs > 0)
  }

  // ── executeStreaming tests ────────────────────────────────────────────────

  private fun streaming(
    runInference: InferenceRunner,
    cancelInference: () -> Unit = {},
    onToken: (String, Boolean) -> Unit,
    onError: (String) -> Unit = { fail("unexpected error: $it") },
  ) {
    LlmHttpInferenceGateway.executeStreaming(
      prompt = "p",
      timeoutSeconds = 5,
      executor = directExecutor,
      inferenceLock = lock,
      resetConversation = {},
      runInference = runInference,
      cancelInference = cancelInference,
      elapsedMs = { tick() },
      onToken = onToken,
      onError = onError,
    )
  }

  @Test
  fun streamingTokensAreDeliveredInOrder() {
    val tokens = mutableListOf<String>()
    var doneReceived = false
    streaming(
      runInference = { _, onPartial, _ ->
        onPartial("foo", false)
        onPartial("bar", false)
        onPartial("", true)
      },
      onToken = { partial, done ->
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
        onPartial("tok", true)
      },
      onToken = { partial, done ->
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
      onToken = { _, _ -> fail("should not receive tokens on error") },
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
      onToken = { _, _ -> fail("should not receive tokens") },
      onError = { errorMsg = it },
    )
    assertNotNull(errorMsg)
    assertTrue(errorMsg!!.contains("crash"))
  }
}
