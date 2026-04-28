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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseRendererTest {
  private val json = Json { encodeDefaults = true }

  @Test
  fun rendersJsonErrorPayload() {
    val result = ResponseRenderer.renderJsonError("not_found")
    assertTrue(result.contains("\"message\":\"not_found\""))
    assertTrue(result.contains("\"type\":\"not_found_error\""))
  }

  @Test
  fun rendersJsonErrorIncludesParamField() {
    val result = ResponseRenderer.renderJsonError("something broke")
    assertTrue(result.contains("\"param\":null"))
  }

  @Test
  fun rendersJsonErrorIncludesParamFieldWithKind() {
    val result = ResponseRenderer.renderJsonError("overflow", kind = ErrorKind.CONTEXT_OVERFLOW)
    assertTrue(result.contains("\"param\":null"))
  }

  @Test
  fun rendersJsonErrorWithSuggestion() {
    val result = ResponseRenderer.renderJsonError("context overflow", suggestion = "Try shorter input")
    assertTrue(result.contains("\"suggestion\":\"Try shorter input\""))
    assertTrue(result.contains("\"message\":\"context overflow\""))
  }

  @Test
  fun rendersJsonErrorWithKind() {
    val result = ResponseRenderer.renderJsonError("model failed", kind = ErrorKind.MODEL_FILES_MISSING)
    assertTrue(result.contains("\"type\":\"server_error\""))
    assertTrue(result.contains("\"code\":null"))
  }

  @Test
  fun rendersJsonErrorWithKindAndSuggestion() {
    val result = ResponseRenderer.renderJsonError(
      "out of memory",
      suggestion = "Try a smaller model",
      kind = ErrorKind.OOM,
    )
    assertTrue(result.contains("\"type\":\"server_error\""))
    assertTrue(result.contains("\"suggestion\":\"Try a smaller model\""))
    assertTrue(result.contains("\"message\":\"out of memory\""))
  }

  @Test
  fun rendersJsonErrorContextOverflowMapsToInvalidRequestWithCode() {
    val result = ResponseRenderer.renderJsonError("tokens exceed limit", kind = ErrorKind.CONTEXT_OVERFLOW)
    assertTrue(result.contains("\"type\":\"invalid_request_error\""))
    assertTrue(result.contains("\"code\":\"context_length_exceeded\""))
  }

  @Test
  fun rendersJsonErrorModelNotFoundMapsToNotFoundError() {
    val result = ResponseRenderer.renderJsonError("model not found", kind = ErrorKind.MODEL_NOT_FOUND)
    assertTrue(result.contains("\"type\":\"not_found_error\""))
  }

  @Test
  fun rendersJsonErrorWithoutSuggestionOmitsField() {
    val result = ResponseRenderer.renderJsonError("generic error")
    assertTrue(!result.contains("\"suggestion\""))
  }

  @Test
  fun rendersJsonErrorStructurallyValid() {
    val result = ResponseRenderer.renderJsonError("test error", suggestion = "try again", kind = ErrorKind.OOM)
    val parsed = Json.parseToJsonElement(result).jsonObject
    val error = parsed["error"]!!.jsonObject
    assertEquals("test error", error["message"]!!.jsonPrimitive.content)
    assertEquals("server_error", error["type"]!!.jsonPrimitive.content)
    assertEquals(JsonNull, error["param"])
    assertEquals("try again", error["suggestion"]!!.jsonPrimitive.content)
  }

  @Test
  fun emitsSseEventFrame() {
    assertEquals("event: ping\ndata: {\"ok\":true}\n\n", ResponseRenderer.emitSseEvent("ping", "{\"ok\":true}"))
  }

  @Test
  fun textSsePayloadContainsRequiredEvents() {
    val payload = ResponseRenderer.buildTextSsePayload("test-model", "hello world")
    assertTrue(payload.contains("event: response.created"))
    assertTrue(payload.contains("event: response.in_progress"))
    assertTrue(payload.contains("event: response.output_text.delta"))
    assertTrue(payload.contains("event: response.completed"))
    assertTrue(payload.contains("data: [DONE]"))
    assertTrue(payload.contains("\"model\":\"test-model\""))
    assertTrue(payload.contains("hello world"))
  }

  @Test
  fun textSsePayloadEscapesSpecialChars() {
    val payload = ResponseRenderer.buildTextSsePayload("m", "line1\nline2 \"quoted\"")
    assertTrue(payload.contains("line1\\nline2"))
    assertTrue(payload.contains("\\\"quoted\\\""))
  }

  @Test
  fun emptySsePayloadUsesEmptyText() {
    val payload = ResponseRenderer.buildTextSsePayload("m", "")
    assertTrue(payload.contains("\"delta\":\"\""))
    assertTrue(payload.contains("event: response.completed"))
  }

  // ── Streaming builder tests ───────────────────────────────────────────────

  @Test
  fun streamingHeaderContainsOpeningEvents() {
    val header = ResponseRenderer.buildStreamingHeader("my-model", "resp-1", "msg-1", 1000L)
    assertTrue(header.contains("event: response.created"))
    assertTrue(header.contains("event: response.in_progress"))
    assertTrue(header.contains("event: response.output_item.added"))
    assertTrue(header.contains("event: response.content_part.added"))
    assertTrue(header.contains("\"model\":\"my-model\""))
    assertTrue(header.contains("\"id\":\"resp-1\""))
    assertTrue(header.contains("\"id\":\"msg-1\""))
  }

  @Test
  fun streamingHeaderDoesNotContainDeltaOrDone() {
    val header = ResponseRenderer.buildStreamingHeader("m", "r", "g", 1000L)
    assertTrue(!header.contains("output_text.delta"))
    assertTrue(!header.contains("response.completed"))
    assertTrue(!header.contains("[DONE]"))
  }

  @Test
  fun textDeltaSseEventFormat() {
    val event = ResponseRenderer.buildTextDeltaSseEvent("msg-42", "hello")
    assertEquals("event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\"hello\",\"item_id\":\"msg-42\",\"output_index\":0}\n\n", event)
  }

  @Test
  fun streamingFooterContainsClosingEventsAndDone() {
    val footer = ResponseRenderer.buildStreamingFooter("my-model", "resp-1", "msg-1", 1000L, "full text")
    assertTrue(footer.contains("event: response.output_text.done"))
    assertTrue(footer.contains("event: response.content_part.done"))
    assertTrue(footer.contains("event: response.output_item.done"))
    assertTrue(footer.contains("event: response.completed"))
    assertTrue(footer.contains("data: [DONE]"))
    assertTrue(footer.contains("full text"))
    assertTrue(footer.contains("\"model\":\"my-model\""))
  }

  @Test
  fun streamingFooterDoesNotContainOpeningEvents() {
    val footer = ResponseRenderer.buildStreamingFooter("m", "r", "g", 1000L, "")
    assertTrue(!footer.contains("response.created"))
    assertTrue(!footer.contains("response.in_progress"))
    assertTrue(!footer.contains("output_item.added"))
  }

  // ── Model list enrichment──────────────────────────────────

  @Test
  fun modelItemHasCreatedAndOwnedBy() {
    val item = LlmHttpModelItem(id = "test-model")
    assertEquals("ollitert", item.owned_by)
    assertTrue("created should be a recent epoch", item.created > 0)
  }

  @Test
  fun modelItemSerializesCreatedAndOwnedBy() {
    val item = LlmHttpModelItem(id = "test-model")
    val serialized = json.encodeToString(LlmHttpModelItem.serializer(), item)
    assertTrue(serialized.contains("\"owned_by\":\"ollitert\""))
    assertTrue(serialized.contains("\"created\":"))
  }

  // ── Chat stream first chunk sends content=""───────────────

  @Test
  fun chatStreamFirstChunkHasEmptyContent() {
    val chunk = ResponseRenderer.buildChatStreamFirstChunk("c1", "m", 1000L)
    // Should have content:"" (empty string), not omit content entirely
    assertTrue("First chunk should have content:\"\"", chunk.contains("\"content\":\"\""))
    assertTrue("First chunk should have role:assistant", chunk.contains("\"role\":\"assistant\""))
  }

  // ── [DONE] separated from final chunk──────────────────────

  @Test
  fun chatStreamFinalChunkDoesNotContainDone() {
    val chunk = ResponseRenderer.buildChatStreamFinalChunk("c1", "m", 1000L)
    assertTrue("Final chunk should NOT contain [DONE]", !chunk.contains("[DONE]"))
    assertTrue("Final chunk should have finish_reason:stop", chunk.contains("\"finish_reason\":\"stop\""))
  }

  @Test
  fun sseDoneConstant() {
    assertEquals("data: [DONE]\n\n", ResponseRenderer.SSE_DONE)
  }

  // ── Dynamic finish_reason──────────────────────────────────

  @Test
  fun chatStreamFinalChunkAcceptsCustomFinishReason() {
    val chunk = ResponseRenderer.buildChatStreamFinalChunk("c1", "m", 1000L, finishReason = "length")
    assertTrue(chunk.contains("\"finish_reason\":\"length\""))
    assertTrue(!chunk.contains("\"finish_reason\":\"stop\""))
  }

  @Test
  fun chatStreamFinalChunkDefaultsToStop() {
    val chunk = ResponseRenderer.buildChatStreamFinalChunk("c1", "m", 1000L)
    assertTrue(chunk.contains("\"finish_reason\":\"stop\""))
  }

  @Test
  fun chatStreamFinalChunkWithLengthFinishReason() {
    val chunk = ResponseRenderer.buildChatStreamFinalChunk("c1", "m", 1000L, finishReason = FinishReason.LENGTH)
    assertTrue(chunk.contains("\"finish_reason\":\"length\""))
    assertFalse(chunk.contains("\"finish_reason\":\"stop\""))
  }

  // ── Usage chunk for streaming──────────────────────────────

  @Test
  fun chatStreamUsageChunkFormat() {
    val chunk = ResponseRenderer.buildChatStreamUsageChunk("c1", "m", 1000L, 10, 20)
    assertTrue("Should start with data:", chunk.startsWith("data: "))
    assertTrue("Should have empty choices", chunk.contains("\"choices\":[]"))
    assertTrue("Should have prompt_tokens", chunk.contains("\"prompt_tokens\":10"))
    assertTrue("Should have completion_tokens", chunk.contains("\"completion_tokens\":20"))
    assertTrue("Should have total_tokens", chunk.contains("\"total_tokens\":30"))
    assertTrue("Should have chat.completion.chunk object", chunk.contains("\"object\":\"chat.completion.chunk\""))
  }

  @Test
  fun chatStreamUsageChunkPreservesIds() {
    val chunk = ResponseRenderer.buildChatStreamUsageChunk("my-chat-id", "my-model", 999L, 5, 10)
    assertTrue(chunk.contains("\"id\":\"my-chat-id\""))
    assertTrue(chunk.contains("\"model\":\"my-model\""))
    assertTrue(chunk.contains("\"created\":999"))
  }

  // ── Streaming footer token counts────────────────────────────────

  @Test
  fun streamingFooterIncludesTokenCounts() {
    val footer = ResponseRenderer.buildStreamingFooter("m", "r", "g", 1000L, "text", inputTokens = 8, outputTokens = 12)
    assertTrue("Should have input_tokens", footer.contains("\"input_tokens\":8"))
    assertTrue("Should have output_tokens", footer.contains("\"output_tokens\":12"))
    assertTrue("Should have total_tokens", footer.contains("\"total_tokens\":20"))
  }

  @Test
  fun streamingFooterDefaultsToZeroTokens() {
    val footer = ResponseRenderer.buildStreamingFooter("m", "r", "g", 1000L, "text")
    assertTrue(footer.contains("\"input_tokens\":0"))
    assertTrue(footer.contains("\"output_tokens\":0"))
    assertTrue(footer.contains("\"total_tokens\":0"))
  }

  // ── Text SSE payload token counts────────────────────────────────

  @Test
  fun textSsePayloadIncludesTokenCounts() {
    val payload = ResponseRenderer.buildTextSsePayload("m", "hello", inputTokens = 5, outputTokens = 3)
    assertTrue(payload.contains("\"input_tokens\":5"))
    assertTrue(payload.contains("\"output_tokens\":3"))
    assertTrue(payload.contains("\"total_tokens\":8"))
  }

  // ── UUID-based IDs in SSE payloads─────────────────────────

  @Test
  fun textSsePayloadUsesUuidIds() {
    val payload = ResponseRenderer.buildTextSsePayload("m", "hello")
    // Should NOT contain timestamp-based IDs like "resp-12345"
    assertTrue("Should have UUID-based resp ID", payload.contains("\"id\":\"resp-"))
    // UUID format: 8-4-4-4-12 hex chars = 36 chars
    val respIdMatch = Regex("\"id\":\"resp-([a-f0-9\\-]{36})\"").find(payload)
    assertNotNull("resp ID should be UUID format", respIdMatch)
  }

  // ── Full streaming sequence test ──────────────────────────────────────────

  @Test
  fun fullChatStreamingSequenceIsCorrect() {
    val chatId = "chatcmpl-test"
    val model = "test-model"
    val now = 1000L

    // 1. First chunk: role + empty content
    val first = ResponseRenderer.buildChatStreamFirstChunk(chatId, model, now)
    assertTrue(first.contains("\"role\":\"assistant\""))
    assertTrue(first.contains("\"content\":\"\""))
    assertTrue(first.contains("\"finish_reason\":null"))

    // 2. Content chunks: content with tokens
    val delta = ResponseRenderer.buildChatStreamDeltaChunk(chatId, model, now, "Hello")
    assertTrue(delta.contains("\"content\":\"Hello\""))
    assertTrue(delta.contains("\"finish_reason\":null"))
    assertTrue(!delta.contains("\"role\""))

    // 3. Final chunk: finish_reason, no content
    val final_ = ResponseRenderer.buildChatStreamFinalChunk(chatId, model, now)
    assertTrue(final_.contains("\"finish_reason\":\"stop\""))
    assertTrue(!final_.contains("[DONE]"))

    // 4. Usage chunk (optional)
    val usage = ResponseRenderer.buildChatStreamUsageChunk(chatId, model, now, 10, 5)
    assertTrue(usage.contains("\"choices\":[]"))
    assertTrue(usage.contains("\"total_tokens\":15"))

    // 5. [DONE]
    assertEquals("data: [DONE]\n\n", ResponseRenderer.SSE_DONE)
  }

  // ── buildChatStreamUsageChunk with timings ───────────────────────────────

  @Test
  fun chatStreamUsageChunkWithTimingsIncludesTimingsJson() {
    val timings = """{"prompt_ms":200,"predicted_ms":1000}"""
    val result = ResponseRenderer.buildChatStreamUsageChunk(
      "chat-1", "model-1", 100L, 10, 5, timings,
    )
    assertTrue("should contain timings field", result.contains(""""timings":{"prompt_ms":200,"predicted_ms":1000}"""))
    assertTrue("should contain usage", result.contains(""""total_tokens":15"""))
  }

  @Test
  fun chatStreamUsageChunkWithNullTimingsOmitsField() {
    val result = ResponseRenderer.buildChatStreamUsageChunk(
      "chat-1", "model-1", 100L, 10, 5, null,
    )
    assertTrue("should contain usage", result.contains(""""total_tokens":15"""))
    assertTrue("should not contain timings", !result.contains("timings"))
  }

  // ── buildResponsesStreamToolCallEvents ───────────────────────────────────

  @Test
  fun responsesStreamToolCallEventsSingleCallContainsAllEvents() {
    val toolCalls = listOf(
      ToolCall(id = "call-1", function = ToolCallFunction(name = "turn_on", arguments = "{\"device\":\"light\"}")),
    )
    val result = ResponseRenderer.buildResponsesStreamToolCallEvents("resp-1", "m", 1000L, toolCalls)
    assertTrue(result.contains("event: response.created"))
    assertTrue(result.contains("event: response.in_progress"))
    assertTrue(result.contains("event: response.output_item.added"))
    assertTrue(result.contains("event: response.function_call_arguments.delta"))
    assertTrue(result.contains("event: response.function_call_arguments.done"))
    assertTrue(result.contains("event: response.output_item.done"))
    assertTrue(result.contains("event: response.completed"))
    assertTrue(result.contains("data: [DONE]"))
    assertTrue(result.contains("\"type\":\"function_call\""))
    assertTrue(result.contains("\"call_id\":\"call-1\""))
    assertTrue(result.contains("\"name\":\"turn_on\""))
  }

  @Test
  fun responsesStreamToolCallEventsMultipleCallsIncrementOutputIndex() {
    val toolCalls = listOf(
      ToolCall(id = "c1", function = ToolCallFunction(name = "fn1", arguments = "{}")),
      ToolCall(id = "c2", function = ToolCallFunction(name = "fn2", arguments = "{}")),
    )
    val result = ResponseRenderer.buildResponsesStreamToolCallEvents("r", "m", 1L, toolCalls)
    assertTrue(result.contains("\"output_index\":0"))
    assertTrue(result.contains("\"output_index\":1"))
  }

  @Test
  fun responsesStreamToolCallEventsEmptyListEmitsOnlyEnvelope() {
    val result = ResponseRenderer.buildResponsesStreamToolCallEvents("r", "m", 1L, emptyList())
    assertTrue(result.contains("event: response.created"))
    assertTrue(result.contains("event: response.completed"))
    assertTrue(result.contains("data: [DONE]"))
    assertTrue("should not contain function_call events", !result.contains("function_call_arguments"))
  }

  @Test
  fun responsesStreamToolCallEventsIncludesSequenceNumber() {
    val toolCalls = listOf(
      ToolCall(id = "c1", function = ToolCallFunction(name = "fn", arguments = "{}")),
    )
    val result = ResponseRenderer.buildResponsesStreamToolCallEvents("r", "m", 1L, toolCalls)
    assertTrue(result.contains("\"sequence_number\":0"))
  }

  @Test
  fun responsesStreamToolCallEventsIncludesUsage() {
    val result = ResponseRenderer.buildResponsesStreamToolCallEvents("r", "m", 1L, emptyList(), inputTokens = 10, outputTokens = 5)
    assertTrue(result.contains("\"input_tokens\":10"))
    assertTrue(result.contains("\"output_tokens\":5"))
    assertTrue(result.contains("\"total_tokens\":15"))
  }

  // ── Completions streaming builders ──────────────────────────────────────

  @Test
  fun completionStreamChunkContainsTextAndObjectType() {
    val result = ResponseRenderer.buildCompletionStreamChunk("cmpl-abc", "test-model", 1000L, "Hello")
    assertTrue(result.startsWith("data: "))
    assertTrue(result.contains("\"object\":\"text_completion\""))
    assertTrue(result.contains("\"text\":\"Hello\""))
    assertTrue(result.contains("\"finish_reason\":null"))
    assertTrue(result.endsWith("\n\n"))
  }

  @Test
  fun completionStreamChunkEscapesSpecialCharacters() {
    val result = ResponseRenderer.buildCompletionStreamChunk("cmpl-abc", "test-model", 1000L, "line1\nline2\"quote")
    assertTrue(result.contains("\"text\":\"line1\\nline2\\\"quote\""))
  }

  @Test
  fun completionStreamFinalChunkHasFinishReason() {
    val result = ResponseRenderer.buildCompletionStreamFinalChunk("cmpl-abc", "test-model", 1000L, "stop")
    assertTrue(result.contains("\"text\":\"\""))
    assertTrue(result.contains("\"finish_reason\":\"stop\""))
    assertTrue(result.contains("\"object\":\"text_completion\""))
  }

  @Test
  fun completionStreamFinalChunkSupportsLengthReason() {
    val result = ResponseRenderer.buildCompletionStreamFinalChunk("cmpl-abc", "test-model", 1000L, "length")
    assertTrue(result.contains("\"finish_reason\":\"length\""))
  }

  @Test
  fun completionStreamUsageChunkHasEmptyChoicesAndUsage() {
    val result = ResponseRenderer.buildCompletionStreamUsageChunk("cmpl-abc", "test-model", 1000L, 10, 5, null)
    assertTrue(result.contains("\"choices\":[]"))
    assertTrue(result.contains("\"prompt_tokens\":10"))
    assertTrue(result.contains("\"completion_tokens\":5"))
    assertTrue(result.contains("\"total_tokens\":15"))
    assertFalse(result.contains("\"timings\""))
  }

  @Test
  fun completionStreamUsageChunkIncludesTimingsWhenProvided() {
    val result = ResponseRenderer.buildCompletionStreamUsageChunk("cmpl-abc", "test-model", 1000L, 10, 5, """{"predicted_per_second":12.5}""")
    assertTrue(result.contains("\"timings\":{\"predicted_per_second\":12.5}"))
  }

  // ── buildChatStreamToolCallChunks with empty list ────────────────────────

  // ── API-05/API-08: logprobs field in choices ────────────────────────────────

  @Test
  fun chatChoiceSerializesLogprobsNull() {
    val choice = ChatChoice(index = 0, message = ChatMessage("assistant", ChatContent("hi")), finish_reason = "stop")
    val serialized = json.encodeToString(ChatChoice.serializer(), choice)
    assertTrue("ChatChoice should serialize logprobs:null", serialized.contains("\"logprobs\":null"))
  }

  @Test
  fun chatStreamFirstChunkHasLogprobsNull() {
    val chunk = ResponseRenderer.buildChatStreamFirstChunk("c1", "m", 1000L)
    assertTrue("First chunk choice should have logprobs:null", chunk.contains("\"logprobs\":null"))
  }

  @Test
  fun chatStreamDeltaChunkHasLogprobsNull() {
    val chunk = ResponseRenderer.buildChatStreamDeltaChunk("c1", "m", 1000L, "hello")
    assertTrue("Delta chunk choice should have logprobs:null", chunk.contains("\"logprobs\":null"))
  }

  @Test
  fun chatStreamFinalChunkHasLogprobsNull() {
    val chunk = ResponseRenderer.buildChatStreamFinalChunk("c1", "m", 1000L)
    assertTrue("Final chunk choice should have logprobs:null", chunk.contains("\"logprobs\":null"))
  }

  @Test
  fun completionStreamChunkHasLogprobsNull() {
    val chunk = ResponseRenderer.buildCompletionStreamChunk("cmpl-1", "m", 1000L, "hello")
    assertTrue("Completion chunk choice should have logprobs:null", chunk.contains("\"logprobs\":null"))
  }

  @Test
  fun completionStreamFinalChunkHasLogprobsNull() {
    val chunk = ResponseRenderer.buildCompletionStreamFinalChunk("cmpl-1", "m", 1000L)
    assertTrue("Completion final chunk choice should have logprobs:null", chunk.contains("\"logprobs\":null"))
  }

  @Test
  fun chatStreamToolCallChunksHaveLogprobsNull() {
    val toolCalls = listOf(
      ToolCall(id = "call-1", function = ToolCallFunction(name = "fn", arguments = "{}")),
    )
    val result = ResponseRenderer.buildChatStreamToolCallChunks("c1", "m", 1000L, toolCalls)
    val lines = result.split("\n\n").filter { it.startsWith("data: ") }
    for (line in lines) {
      assertTrue("Every tool-call chunk choice should have logprobs:null: $line", line.contains("\"logprobs\":null"))
    }
  }

  @Test
  fun chatStreamToolCallChunksEmptyListReturnsOnlyFinalChunk() {
    val result = ResponseRenderer.buildChatStreamToolCallChunks(
      "chat-1", "model-1", 100L, emptyList(),
    )
    // With empty tool calls, only the final finish_reason chunk should be emitted
    assertTrue("should contain finish_reason", result.contains(""""finish_reason":"tool_calls""""))
    // Should not contain function name/arguments chunks (those come from the for loop)
    assertTrue("should not contain function field", !result.contains(""""function":{"name":"""))
  }

}
