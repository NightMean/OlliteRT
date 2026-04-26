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
  fun multiplePartialsAccumulate() {
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
  fun errorFromInferenceIsReported() {
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
  fun exceptionDuringInferenceIsCaught() {
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
    assertTrue(result.error!!.contains("reset failed"))
  }

  @Test
  fun cancelInferenceCalledOnError() {
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
  fun emptyPartialDoesNotCountAsTtfb() {
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
  fun totalMsIsTracked() {
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
  fun thinkingContentAccumulates() {
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
  fun noThinkingReturnsNull() {
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

  // ── executeStreaming tests ────────────────────────────────────────────────

  private fun streaming(
    runInference: InferenceFn,
    cancelInference: () -> Unit = {},
    onToken: (String, Boolean, String?) -> Unit,
    onError: (String) -> Unit = { fail("unexpected error: $it") },
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
    assertTrue(errorMsg!!.contains("crash"))
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
}
