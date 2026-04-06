package com.ollite.llm.server.service

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpResponseRendererTest {
  private val json = Json { encodeDefaults = true }

  @Test
  fun rendersJsonErrorPayload() {
    assertEquals("{\"error\":\"not_found\"}", LlmHttpResponseRenderer.renderJsonError("not_found"))
  }

  @Test
  fun rendersFallbackModelListWhenIdsEmpty() {
    val payload = LlmHttpResponseRenderer.renderModelListPayload(json, emptyList(), "local")
    assertTrue(payload.contains("\"id\":\"local\""))
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
  fun toolCallSsePayloadContainsToolJson() {
    val toolCall = ToolCall(id = "call-1", function = ToolCallFunction(name = "test", arguments = "{}"))
    val payload = LlmHttpResponseRenderer.buildToolCallSsePayload("m", toolCall)
    assertTrue(payload.contains("event: response.function_call_arguments.delta"))
    assertTrue(payload.contains("event: response.function_call_arguments.done"))
    assertTrue(payload.contains("\"call_id\":\"call-1\""))
    assertTrue(payload.contains("\"name\":\"test\""))
    assertTrue(payload.contains("event: response.completed"))
    assertTrue(payload.contains("data: [DONE]"))
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
}
