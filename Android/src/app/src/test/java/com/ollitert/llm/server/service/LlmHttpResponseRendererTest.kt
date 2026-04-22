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

import com.ollitert.llm.server.common.ErrorCategory
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpResponseRendererTest {
  private val json = Json { encodeDefaults = true }

  @Test
  fun rendersJsonErrorPayload() {
    val result = LlmHttpResponseRenderer.renderJsonError("not_found")
    assertTrue(result.contains("\"message\":\"not_found\""))
    assertTrue(result.contains("\"type\":\"not_found_error\""))
  }

  @Test
  fun rendersJsonErrorWithSuggestion() {
    val result = LlmHttpResponseRenderer.renderJsonError("context overflow", suggestion = "Try shorter input")
    assertTrue(result.contains("\"suggestion\":\"Try shorter input\""))
    assertTrue(result.contains("\"message\":\"context overflow\""))
  }

  @Test
  fun rendersJsonErrorWithCategory() {
    val result = LlmHttpResponseRenderer.renderJsonError("model failed", category = ErrorCategory.MODEL_LOAD)
    assertTrue(result.contains("\"type\":\"server_error\""))
  }

  @Test
  fun rendersJsonErrorWithCategoryAndSuggestion() {
    val result = LlmHttpResponseRenderer.renderJsonError(
      "out of memory",
      suggestion = "Try a smaller model",
      category = ErrorCategory.SYSTEM,
    )
    assertTrue(result.contains("\"type\":\"server_error\""))
    assertTrue(result.contains("\"suggestion\":\"Try a smaller model\""))
    assertTrue(result.contains("\"message\":\"out of memory\""))
  }

  @Test
  fun rendersJsonErrorNetworkCategoryMapsToInvalidRequest() {
    val result = LlmHttpResponseRenderer.renderJsonError("port in use", category = ErrorCategory.NETWORK)
    assertTrue(result.contains("\"type\":\"invalid_request_error\""))
  }

  @Test
  fun rendersJsonErrorWithoutSuggestionOmitsField() {
    val result = LlmHttpResponseRenderer.renderJsonError("generic error")
    assertTrue(!result.contains("\"suggestion\""))
  }

  @Test
  fun emitsSseEventFrame() {
    assertEquals("event: ping\ndata: {\"ok\":true}\n\n", LlmHttpResponseRenderer.emitSseEvent("ping", "{\"ok\":true}"))
  }

  @Test
  fun textSsePayloadContainsRequiredEvents() {
    val payload = LlmHttpResponseRenderer.buildTextSsePayload("test-model", "hello world")
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
    val payload = LlmHttpResponseRenderer.buildTextSsePayload("m", "line1\nline2 \"quoted\"")
    assertTrue(payload.contains("line1\\nline2"))
    assertTrue(payload.contains("\\\"quoted\\\""))
  }

  @Test
  fun emptySsePayloadUsesEmptyText() {
    val payload = LlmHttpResponseRenderer.buildTextSsePayload("m", "")
    assertTrue(payload.contains("\"delta\":\"\""))
    assertTrue(payload.contains("event: response.completed"))
  }

  // ── Streaming builder tests ───────────────────────────────────────────────

  @Test
  fun streamingHeaderContainsOpeningEvents() {
    val header = LlmHttpResponseRenderer.buildStreamingHeader("my-model", "resp-1", "msg-1", 1000L)
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
    val header = LlmHttpResponseRenderer.buildStreamingHeader("m", "r", "g", 1000L)
    assertTrue(!header.contains("output_text.delta"))
    assertTrue(!header.contains("response.completed"))
    assertTrue(!header.contains("[DONE]"))
  }

  @Test
  fun textDeltaSseEventFormat() {
    val event = LlmHttpResponseRenderer.buildTextDeltaSseEvent("msg-42", "hello")
    assertEquals("event: response.output_text.delta\ndata: {\"type\":\"response.output_text.delta\",\"content_index\":0,\"delta\":\"hello\",\"item_id\":\"msg-42\",\"output_index\":0}\n\n", event)
  }

  @Test
  fun streamingFooterContainsClosingEventsAndDone() {
    val footer = LlmHttpResponseRenderer.buildStreamingFooter("my-model", "resp-1", "msg-1", 1000L, "full text")
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
    val footer = LlmHttpResponseRenderer.buildStreamingFooter("m", "r", "g", 1000L, "")
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
    val chunk = LlmHttpResponseRenderer.buildChatStreamFirstChunk("c1", "m", 1000L)
    // Should have content:"" (empty string), not omit content entirely
    assertTrue("First chunk should have content:\"\"", chunk.contains("\"content\":\"\""))
    assertTrue("First chunk should have role:assistant", chunk.contains("\"role\":\"assistant\""))
  }

  // ── [DONE] separated from final chunk──────────────────────

  @Test
  fun chatStreamFinalChunkDoesNotContainDone() {
    val chunk = LlmHttpResponseRenderer.buildChatStreamFinalChunk("c1", "m", 1000L)
    assertTrue("Final chunk should NOT contain [DONE]", !chunk.contains("[DONE]"))
    assertTrue("Final chunk should have finish_reason:stop", chunk.contains("\"finish_reason\":\"stop\""))
  }

  @Test
  fun sseDoneConstant() {
    assertEquals("data: [DONE]\n\n", LlmHttpResponseRenderer.SSE_DONE)
  }

  // ── Dynamic finish_reason──────────────────────────────────

  @Test
  fun chatStreamFinalChunkAcceptsCustomFinishReason() {
    val chunk = LlmHttpResponseRenderer.buildChatStreamFinalChunk("c1", "m", 1000L, finishReason = "length")
    assertTrue(chunk.contains("\"finish_reason\":\"length\""))
    assertTrue(!chunk.contains("\"finish_reason\":\"stop\""))
  }

  @Test
  fun chatStreamFinalChunkDefaultsToStop() {
    val chunk = LlmHttpResponseRenderer.buildChatStreamFinalChunk("c1", "m", 1000L)
    assertTrue(chunk.contains("\"finish_reason\":\"stop\""))
  }

  // ── Usage chunk for streaming──────────────────────────────

  @Test
  fun chatStreamUsageChunkFormat() {
    val chunk = LlmHttpResponseRenderer.buildChatStreamUsageChunk("c1", "m", 1000L, 10, 20)
    assertTrue("Should start with data:", chunk.startsWith("data: "))
    assertTrue("Should have empty choices", chunk.contains("\"choices\":[]"))
    assertTrue("Should have prompt_tokens", chunk.contains("\"prompt_tokens\":10"))
    assertTrue("Should have completion_tokens", chunk.contains("\"completion_tokens\":20"))
    assertTrue("Should have total_tokens", chunk.contains("\"total_tokens\":30"))
    assertTrue("Should have chat.completion.chunk object", chunk.contains("\"object\":\"chat.completion.chunk\""))
  }

  @Test
  fun chatStreamUsageChunkPreservesIds() {
    val chunk = LlmHttpResponseRenderer.buildChatStreamUsageChunk("my-chat-id", "my-model", 999L, 5, 10)
    assertTrue(chunk.contains("\"id\":\"my-chat-id\""))
    assertTrue(chunk.contains("\"model\":\"my-model\""))
    assertTrue(chunk.contains("\"created\":999"))
  }

  // ── Streaming footer token counts────────────────────────────────

  @Test
  fun streamingFooterIncludesTokenCounts() {
    val footer = LlmHttpResponseRenderer.buildStreamingFooter("m", "r", "g", 1000L, "text", inputTokens = 8, outputTokens = 12)
    assertTrue("Should have input_tokens", footer.contains("\"input_tokens\":8"))
    assertTrue("Should have output_tokens", footer.contains("\"output_tokens\":12"))
    assertTrue("Should have total_tokens", footer.contains("\"total_tokens\":20"))
  }

  @Test
  fun streamingFooterDefaultsToZeroTokens() {
    val footer = LlmHttpResponseRenderer.buildStreamingFooter("m", "r", "g", 1000L, "text")
    assertTrue(footer.contains("\"input_tokens\":0"))
    assertTrue(footer.contains("\"output_tokens\":0"))
    assertTrue(footer.contains("\"total_tokens\":0"))
  }

  // ── Text SSE payload token counts────────────────────────────────

  @Test
  fun textSsePayloadIncludesTokenCounts() {
    val payload = LlmHttpResponseRenderer.buildTextSsePayload("m", "hello", inputTokens = 5, outputTokens = 3)
    assertTrue(payload.contains("\"input_tokens\":5"))
    assertTrue(payload.contains("\"output_tokens\":3"))
    assertTrue(payload.contains("\"total_tokens\":8"))
  }

  // ── UUID-based IDs in SSE payloads─────────────────────────

  @Test
  fun textSsePayloadUsesUuidIds() {
    val payload = LlmHttpResponseRenderer.buildTextSsePayload("m", "hello")
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
    val first = LlmHttpResponseRenderer.buildChatStreamFirstChunk(chatId, model, now)
    assertTrue(first.contains("\"role\":\"assistant\""))
    assertTrue(first.contains("\"content\":\"\""))
    assertTrue(first.contains("\"finish_reason\":null"))

    // 2. Content chunks: content with tokens
    val delta = LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model, now, "Hello")
    assertTrue(delta.contains("\"content\":\"Hello\""))
    assertTrue(delta.contains("\"finish_reason\":null"))
    assertTrue(!delta.contains("\"role\""))

    // 3. Final chunk: finish_reason, no content
    val final_ = LlmHttpResponseRenderer.buildChatStreamFinalChunk(chatId, model, now)
    assertTrue(final_.contains("\"finish_reason\":\"stop\""))
    assertTrue(!final_.contains("[DONE]"))

    // 4. Usage chunk (optional)
    val usage = LlmHttpResponseRenderer.buildChatStreamUsageChunk(chatId, model, now, 10, 5)
    assertTrue(usage.contains("\"choices\":[]"))
    assertTrue(usage.contains("\"total_tokens\":15"))

    // 5. [DONE]
    assertEquals("data: [DONE]\n\n", LlmHttpResponseRenderer.SSE_DONE)
  }

  // ── buildChatStreamUsageChunk with timings ───────────────────────────────

  @Test
  fun chatStreamUsageChunkWithTimingsIncludesTimingsJson() {
    val timings = """{"prompt_ms":200,"predicted_ms":1000}"""
    val result = LlmHttpResponseRenderer.buildChatStreamUsageChunk(
      "chat-1", "model-1", 100L, 10, 5, timings,
    )
    assertTrue("should contain timings field", result.contains(""""timings":{"prompt_ms":200,"predicted_ms":1000}"""))
    assertTrue("should contain usage", result.contains(""""total_tokens":15"""))
  }

  @Test
  fun chatStreamUsageChunkWithNullTimingsOmitsField() {
    val result = LlmHttpResponseRenderer.buildChatStreamUsageChunk(
      "chat-1", "model-1", 100L, 10, 5, null,
    )
    assertTrue("should contain usage", result.contains(""""total_tokens":15"""))
    assertTrue("should not contain timings", !result.contains("timings"))
  }

  // ── buildChatStreamToolCallChunks with empty list ────────────────────────

  @Test
  fun chatStreamToolCallChunksEmptyListReturnsOnlyFinalChunk() {
    val result = LlmHttpResponseRenderer.buildChatStreamToolCallChunks(
      "chat-1", "model-1", 100L, emptyList(),
    )
    // With empty tool calls, only the final finish_reason chunk should be emitted
    assertTrue("should contain finish_reason", result.contains(""""finish_reason":"tool_calls""""))
    // Should not contain function name/arguments chunks (those come from the for loop)
    assertTrue("should not contain function field", !result.contains(""""function":{"name":"""))
  }

}
