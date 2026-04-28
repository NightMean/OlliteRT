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

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [PayloadBuilders] — pure response factory functions.
 *
 * Functions that depend on android.util.Log or BuildConfig (serverInfo, health,
 * modelDetail, modelsList) are excluded — they require Robolectric or instrumented tests.
 * This file covers the response builders and timing calculations that are pure JVM logic.
 */
class PayloadBuildersTest {

  private val json = Json { encodeDefaults = true }

  // ── buildTimingsFromValues() ──────────────────────────────────────────────

  @Test
  fun buildTimingsFromValuesReturnsNullWhenTtfbZero() {
    assertNull(PayloadBuilders.buildTimingsFromValues(10, 5, ttfbMs = 0, totalMs = 100))
  }

  @Test
  fun buildTimingsFromValuesReturnsNullWhenTotalMsZero() {
    assertNull(PayloadBuilders.buildTimingsFromValues(10, 5, ttfbMs = 50, totalMs = 0))
  }

  @Test
  fun buildTimingsFromValuesReturnsNullWhenBothZero() {
    assertNull(PayloadBuilders.buildTimingsFromValues(10, 5, ttfbMs = 0, totalMs = 0))
  }

  @Test
  fun buildTimingsFromValuesReturnsNullWhenTtfbNegative() {
    assertNull(PayloadBuilders.buildTimingsFromValues(10, 5, ttfbMs = -1, totalMs = 100))
  }

  @Test
  fun buildTimingsFromValuesCalculatesCorrectly() {
    // ttfb=200ms, total=1200ms → predictedMs = 1000ms
    val timings = PayloadBuilders.buildTimingsFromValues(
      promptTokens = 100, completionTokens = 50, ttfbMs = 200, totalMs = 1200,
    )
    assertNotNull(timings)
    timings!!

    assertEquals(100, timings.prompt_n)
    assertEquals(200.0, timings.prompt_ms, 0.001)
    // prompt_per_token_ms = 200.0 / 100 = 2.0
    assertEquals(2.0, timings.prompt_per_token_ms, 0.001)
    // prompt_per_second = 100 * 1000.0 / 200.0 = 500.0
    assertEquals(500.0, timings.prompt_per_second, 0.001)

    assertEquals(50, timings.predicted_n)
    assertEquals(1000.0, timings.predicted_ms, 0.001)
    // predicted_per_token_ms = 1000.0 / 50 = 20.0
    assertEquals(20.0, timings.predicted_per_token_ms, 0.001)
    // predicted_per_second = 50 * 1000.0 / 1000.0 = 50.0
    assertEquals(50.0, timings.predicted_per_second, 0.001)
  }

  @Test
  fun buildTimingsFromValuesZeroPromptTokens() {
    val timings = PayloadBuilders.buildTimingsFromValues(
      promptTokens = 0, completionTokens = 10, ttfbMs = 100, totalMs = 500,
    )
    assertNotNull(timings)
    timings!!
    assertEquals(0.0, timings.prompt_per_token_ms, 0.001)
    assertEquals(0.0, timings.prompt_per_second, 0.001)
  }

  @Test
  fun buildTimingsFromValuesZeroCompletionTokens() {
    val timings = PayloadBuilders.buildTimingsFromValues(
      promptTokens = 10, completionTokens = 0, ttfbMs = 100, totalMs = 500,
    )
    assertNotNull(timings)
    timings!!
    assertEquals(0.0, timings.predicted_per_token_ms, 0.001)
    assertEquals(0.0, timings.predicted_per_second, 0.001)
  }

  @Test
  fun buildTimingsFromValuesTtfbEqualsTotalMs() {
    // predictedMs = 0 → predicted_per_second = 0
    val timings = PayloadBuilders.buildTimingsFromValues(
      promptTokens = 10, completionTokens = 5, ttfbMs = 200, totalMs = 200,
    )
    assertNotNull(timings)
    timings!!
    assertEquals(0.0, timings.predicted_ms, 0.001)
    assertEquals(0.0, timings.predicted_per_second, 0.001)
  }

  // ── emptyChatResponse() ───────────────────────────────────────────────────

  @Test
  fun emptyChatResponseHasCorrectStructure() {
    val resp = PayloadBuilders.emptyChatResponse("test-model")
    assertEquals("test-model", resp.model)
    assertEquals(1, resp.choices.size)
    assertEquals("assistant", resp.choices[0].message.role)
    assertEquals("", resp.choices[0].message.content.text)
    assertEquals("stop", resp.choices[0].finish_reason)
    assertEquals(0, resp.usage.prompt_tokens)
    assertEquals(0, resp.usage.completion_tokens)
  }

  @Test
  fun emptyChatResponseHasUuidId() {
    val resp = PayloadBuilders.emptyChatResponse("m")
    assertTrue("should start with chatcmpl-", resp.id.startsWith("chatcmpl-"))
    // UUID part: 8-4-4-4-12 = 36 chars
    assertEquals(36, resp.id.removePrefix("chatcmpl-").length)
  }

  @Test
  fun emptyChatResponseCreatedIsRecentEpoch() {
    val before = System.currentTimeMillis() / 1000
    val resp = PayloadBuilders.emptyChatResponse("m")
    val after = System.currentTimeMillis() / 1000
    assertTrue("created should be recent", resp.created in before..after)
  }

  // ── chatResponseWithText() ────────────────────────────────────────────────

  @Test
  fun chatResponseWithTextHasCorrectContent() {
    val resp = PayloadBuilders.chatResponseWithText("my-model", "Hello world")
    assertEquals("my-model", resp.model)
    assertEquals("Hello world", resp.choices[0].message.content.text)
    assertEquals("assistant", resp.choices[0].message.role)
    assertEquals("stop", resp.choices[0].finish_reason)
  }

  @Test
  fun chatResponseWithTextDefaultFinishReasonIsStop() {
    val resp = PayloadBuilders.chatResponseWithText("m", "text")
    assertEquals("stop", resp.choices[0].finish_reason)
  }

  @Test
  fun chatResponseWithTextCustomFinishReason() {
    val resp = PayloadBuilders.chatResponseWithText("m", "text", finishReason = "length")
    assertEquals("length", resp.choices[0].finish_reason)
  }

  @Test
  fun chatResponseWithTextLengthFinishReason() {
    val resp = PayloadBuilders.chatResponseWithText("m", "a".repeat(400), finishReason = FinishReason.LENGTH)
    assertEquals("length", resp.choices[0].finish_reason)
    assertEquals("chat.completion", resp.`object`)
  }

  @Test
  fun chatResponseWithTextEstimatesTokens() {
    // "Hello World!" = 12 chars → 12/4 = 3 completion tokens
    // promptLen = 100 → 100/4 = 25 prompt tokens
    val resp = PayloadBuilders.chatResponseWithText("m", "Hello World!", promptLen = 100)
    assertEquals(25, resp.usage.prompt_tokens)
    assertEquals(3, resp.usage.completion_tokens)
  }

  @Test
  fun chatResponseWithTextEmptyTextZeroCompletionTokens() {
    val resp = PayloadBuilders.chatResponseWithText("m", "")
    assertEquals(0, resp.usage.completion_tokens)
  }

  @Test
  fun chatResponseWithTextShortTextMinOneToken() {
    // "Hi" = 2 chars → 2/4 = 0, but coerceAtLeast(1) for non-empty
    val resp = PayloadBuilders.chatResponseWithText("m", "Hi")
    assertEquals(1, resp.usage.completion_tokens)
  }

  @Test
  fun chatResponseWithTextZeroPromptLenZeroPromptTokens() {
    val resp = PayloadBuilders.chatResponseWithText("m", "text", promptLen = 0)
    assertEquals(0, resp.usage.prompt_tokens)
  }

  @Test
  fun chatResponseWithTextShortPromptMinOneToken() {
    // promptLen=3 → 3/4 = 0, but coerceAtLeast(1) for promptLen > 0
    val resp = PayloadBuilders.chatResponseWithText("m", "text", promptLen = 3)
    assertEquals(1, resp.usage.prompt_tokens)
  }

  @Test
  fun chatResponseWithTextIncludesTimings() {
    val timings = InferenceTimings(
      prompt_n = 10, prompt_ms = 100.0, prompt_per_token_ms = 10.0, prompt_per_second = 100.0,
      predicted_n = 5, predicted_ms = 500.0, predicted_per_token_ms = 100.0, predicted_per_second = 10.0,
    )
    val resp = PayloadBuilders.chatResponseWithText("m", "text", timings = timings)
    assertNotNull(resp.timings)
    assertEquals(10, resp.timings!!.prompt_n)
  }

  @Test
  fun chatResponseWithTextNullTimingsDefault() {
    val resp = PayloadBuilders.chatResponseWithText("m", "text")
    assertNull(resp.timings)
  }

  // ── chatResponseWithToolCalls() ───────────────────────────────────────────

  @Test
  fun chatResponseWithToolCallsHasCorrectStructure() {
    val toolCalls = listOf(
      ToolCall(id = "call-1", function = ToolCallFunction(name = "get_weather", arguments = "{\"city\":\"Boston\"}")),
    )
    val resp = PayloadBuilders.chatResponseWithToolCalls("m", toolCalls, promptLen = 40)
    assertEquals("tool_calls", resp.choices[0].finish_reason)
    assertEquals("assistant", resp.choices[0].message.role)
    assertEquals("", resp.choices[0].message.content.text)
    assertNotNull(resp.choices[0].message.tool_calls)
    assertEquals(1, resp.choices[0].message.tool_calls!!.size)
    assertEquals("get_weather", resp.choices[0].message.tool_calls!![0].function.name)
  }

  @Test
  fun chatResponseWithToolCallsEstimatesTokensFromArguments() {
    // arguments = "{\"city\":\"Boston\"}" = 18 chars → 18/4 = 4
    val toolCalls = listOf(
      ToolCall(id = "c1", function = ToolCallFunction(name = "fn", arguments = "{\"city\":\"Boston\"}")),
    )
    val resp = PayloadBuilders.chatResponseWithToolCalls("m", toolCalls)
    assertEquals(4, resp.usage.completion_tokens)
  }

  @Test
  fun chatResponseWithToolCallsSumsMultipleCallArguments() {
    // 8 + 8 = 16 chars → 16/4 = 4
    val toolCalls = listOf(
      ToolCall(id = "c1", function = ToolCallFunction(name = "fn1", arguments = "12345678")),
      ToolCall(id = "c2", function = ToolCallFunction(name = "fn2", arguments = "abcdefgh")),
    )
    val resp = PayloadBuilders.chatResponseWithToolCalls("m", toolCalls)
    assertEquals(4, resp.usage.completion_tokens)
  }

  @Test
  fun chatResponseWithToolCallsEmptyArgumentsZeroTokens() {
    // Empty arguments → 0 chars → 0 tokens (estimateTokens returns 0 for empty string)
    val toolCalls = listOf(
      ToolCall(id = "c1", function = ToolCallFunction(name = "fn", arguments = "")),
    )
    val resp = PayloadBuilders.chatResponseWithToolCalls("m", toolCalls)
    assertEquals(0, resp.usage.completion_tokens)
  }

  // ── responsesResponseWithText() ───────────────────────────────────────────

  @Test
  fun responsesResponseWithTextHasCorrectStructure() {
    val resp = PayloadBuilders.responsesResponseWithText("my-model", "Hello")
    assertEquals("my-model", resp.model)
    assertTrue("should start with resp-", resp.id.startsWith("resp-"))
    assertEquals(1, resp.output.size)
    val msg = resp.output[0] as RespMessage
    assertEquals(1, msg.content.size)
    assertEquals("Hello", msg.content[0].text)
  }

  @Test
  fun responsesResponseWithTextEstimatesTokens() {
    // "Hello World!" = 12 chars → 3 tokens; promptLen=80 → 20 tokens
    val resp = PayloadBuilders.responsesResponseWithText("m", "Hello World!", promptLen = 80)
    assertEquals(20, resp.usage.input_tokens)
    assertEquals(3, resp.usage.output_tokens)
  }

  @Test
  fun responsesResponseWithTextEmptyTextZeroTokens() {
    val resp = PayloadBuilders.responsesResponseWithText("m", "")
    assertEquals(0, resp.usage.output_tokens)
  }

  // ── responsesResponseWithToolCalls() ──────────────────────────────────────

  @Test
  fun responsesResponseWithToolCallsHasCorrectStructure() {
    val toolCalls = listOf(
      ToolCall(id = "call-1", function = ToolCallFunction(name = "turn_on_light", arguments = "{\"device\":\"kitchen\"}")),
    )
    val resp = PayloadBuilders.responsesResponseWithToolCalls("m", toolCalls)
    assertEquals(1, resp.output.size)
    val fc = resp.output[0] as RespFunctionCall
    assertEquals("turn_on_light", fc.name)
    assertEquals("call-1", fc.call_id)
    assertEquals("{\"device\":\"kitchen\"}", fc.arguments)
    assertEquals("completed", fc.status)
    assertEquals("completed", resp.status)
  }

  @Test
  fun responsesResponseWithToolCallsMultipleCallsAllPresent() {
    val toolCalls = listOf(
      ToolCall(id = "c1", function = ToolCallFunction(name = "fn1", arguments = "{}")),
      ToolCall(id = "c2", function = ToolCallFunction(name = "fn2", arguments = "{\"x\":1}")),
    )
    val resp = PayloadBuilders.responsesResponseWithToolCalls("m", toolCalls)
    assertEquals(2, resp.output.size)
    assertEquals("fn1", (resp.output[0] as RespFunctionCall).name)
    assertEquals("fn2", (resp.output[1] as RespFunctionCall).name)
  }

  @Test
  fun responsesResponseWithToolCallsEstimatesTokens() {
    // arguments = "{}" = 2 chars → 2/4 = 0, coerceAtLeast(1)
    val toolCalls = listOf(
      ToolCall(id = "c1", function = ToolCallFunction(name = "fn", arguments = "{}")),
    )
    val resp = PayloadBuilders.responsesResponseWithToolCalls("m", toolCalls)
    assertEquals(1, resp.usage.output_tokens)
  }

  @Test
  fun responsesResponseWithToolCallsWithPromptLen() {
    val toolCalls = listOf(
      ToolCall(id = "c1", function = ToolCallFunction(name = "fn", arguments = "{}")),
    )
    val resp = PayloadBuilders.responsesResponseWithToolCalls("m", toolCalls, promptLen = 40)
    assertEquals(10, resp.usage.input_tokens)
  }

  @Test
  fun responsesResponseWithToolCallsSerializesWithTypeDiscriminator() {
    val toolCalls = listOf(
      ToolCall(id = "c1", function = ToolCallFunction(name = "fn", arguments = "{}")),
    )
    val resp = PayloadBuilders.responsesResponseWithToolCalls("m", toolCalls)
    val serialized = json.encodeToString(ResponsesResponse.serializer(), resp)
    assertTrue("should contain function_call type", serialized.contains("\"type\":\"function_call\""))
    assertTrue("should contain call_id", serialized.contains("\"call_id\":\"c1\""))
  }
}
