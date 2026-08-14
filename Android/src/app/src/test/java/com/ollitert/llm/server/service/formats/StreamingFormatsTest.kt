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

package com.ollitert.llm.server.service.formats

import com.ollitert.llm.server.data.ErrorKind
import com.ollitert.llm.server.service.KtorSseWriterImpl
import com.ollitert.llm.server.service.ToolCall
import com.ollitert.llm.server.service.ToolSpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringWriter

class StreamingFormatsTest {

  private val json = Json { ignoreUnknownKeys = true }

  private fun createWriter(): Pair<StringWriter, KtorSseWriterImpl> {
    val sw = StringWriter()
    return sw to KtorSseWriterImpl(sw)
  }

  // ── ChatCompletionsFormat ───────────────────────────────────────────────

  @Test
  fun chatCompletionsFormat_emitHeaderAndDeltas() = runBlocking {
    val (sw, writer) = createWriter()
    val format = ChatCompletionsFormat(
      modelName = "test-model",
      now = 1234567890L,
      stopSequences = null,
      tools = null,
      json = json,
      includeUsage = true,
    )

    format.emitHeader(writer)
    format.emitContentDelta(writer, "Hello ")
    format.emitThinkingDelta(writer, "Thinking ")
    format.emitThinkingClose(writer)

    val output = sw.toString()
    assertTrue(output.contains("chatcmpl-"))
    assertTrue(output.contains("test-model"))
    assertTrue(output.contains("Hello "))
    assertTrue(output.contains("Thinking "))
    assertTrue(output.contains("</think>"))
  }

  @Test
  fun chatCompletionsFormat_emitCompletionWithUsage() = runBlocking {
    val (sw, writer) = createWriter()
    val format = ChatCompletionsFormat(
      modelName = "test-model",
      now = 1234567890L,
      stopSequences = null,
      tools = null,
      json = json,
      includeUsage = true,
    )

    val toolCalls = format.emitCompletion(
      writer = writer,
      fullText = "Complete text",
      fullThinking = "",
      promptTokens = 10,
      completionTokens = 5,
      ttfbMs = 100L,
      totalLatencyMs = 250L,
      maxTokens = 100,
    )

    assertTrue(toolCalls.isEmpty())
    val output = sw.toString()
    assertTrue(output.contains("\"finish_reason\":\"stop\""))
    assertTrue(output.contains("\"prompt_tokens\":10"))
    assertTrue(output.contains("\"completion_tokens\":5"))
    assertTrue(output.contains("data: [DONE]"))
  }

  @Test
  fun chatCompletionsFormat_buildAssistantTextIncludesThinking() {
    val format = ChatCompletionsFormat(
      modelName = "test-model",
      now = 1234567890L,
      stopSequences = null,
      tools = null,
      json = json,
      includeUsage = false,
    )

    val text = format.buildAssistantText("Hello", "Let me think")
    assertEquals("<think>Let me think</think>Hello", text)
  }

  // ── ResponsesApiFormat ──────────────────────────────────────────────────

  @Test
  fun responsesApiFormat_emitHeaderAndFooter() = runBlocking {
    val (sw, writer) = createWriter()
    val format = ResponsesApiFormat(
      modelName = "test-model",
      now = 1234567890L,
      json = json,
      tools = null,
    )

    assertFalse(format.bufferAllTokens)
    format.emitHeader(writer)
    format.emitContentDelta(writer, "Hello world")
    format.emitCompletion(
      writer = writer,
      fullText = "Hello world",
      fullThinking = "",
      promptTokens = 5,
      completionTokens = 2,
      ttfbMs = 50L,
      totalLatencyMs = 120L,
      maxTokens = 50,
    )

    val output = sw.toString()
    assertTrue(output.contains("response.created"))
    assertTrue(output.contains("response.output_item.added"))
    assertTrue(output.contains("response.completed"))
  }

  // ── CompletionsFormat ───────────────────────────────────────────────────

  @Test
  fun completionsFormat_emitDeltasAndCompletion() = runBlocking {
    val (sw, writer) = createWriter()
    val format = CompletionsFormat(
      modelName = "test-model",
      now = 1234567890L,
      stopSequences = null,
      json = json,
      includeUsage = true,
    )

    format.emitHeader(writer) // No-op
    format.emitContentDelta(writer, "Line 1")
    format.emitCompletion(
      writer = writer,
      fullText = "Line 1",
      fullThinking = "",
      promptTokens = 8,
      completionTokens = 3,
      ttfbMs = 40L,
      totalLatencyMs = 90L,
      maxTokens = 50,
    )

    val output = sw.toString()
    assertTrue(output.contains("cmpl-"))
    assertTrue(output.contains("Line 1"))
    assertTrue(output.contains("data: [DONE]"))
  }

  // ── AnthropicMessagesFormat ─────────────────────────────────────────────

  @Test
  fun anthropicMessagesFormat_emitsHeaderEarlyAndPing() = runBlocking {
    val (sw, writer) = createWriter()
    val format = AnthropicMessagesFormat(
      modelName = "claude-3-opus",
      requestModelId = "claude-3-opus-20240229",
      stopSequences = null,
      tools = null,
      hasSchemaInjection = false,
    )

    assertTrue(format.emitsHeaderEarly)
    format.emitHeader(writer)
    format.emitPing(writer)

    val output = sw.toString()
    assertTrue(output.contains("event: message_start"))
    assertTrue(output.contains("claude-3-opus-20240229"))
    assertTrue(output.contains("event: ping"))
  }

  @Test
  fun anthropicMessagesFormat_transitionsThinkingToTextBlock() = runBlocking {
    val (sw, writer) = createWriter()
    val format = AnthropicMessagesFormat(
      modelName = "claude-3-opus",
      requestModelId = "claude-3-opus-20240229",
      stopSequences = null,
      tools = null,
      hasSchemaInjection = false,
    )

    format.emitThinkingDelta(writer, "<think>Let me reason")
    format.emitContentDelta(writer, "</think>Here is the answer")

    val output = sw.toString()
    assertTrue(output.contains("content_block_start"))
    assertTrue(output.contains("\"type\":\"thinking\""))
    assertTrue(output.contains("thinking_delta"))
    assertTrue(output.contains("content_block_stop"))
    assertTrue(output.contains("\"type\":\"text\""))
    assertTrue(output.contains("text_delta"))
  }

  @Test
  fun anthropicMessagesFormat_emitErrorFormat() = runBlocking {
    val (sw, writer) = createWriter()
    val format = AnthropicMessagesFormat(
      modelName = "claude-3-opus",
      requestModelId = "claude-3-opus-20240229",
      stopSequences = null,
      tools = null,
      hasSchemaInjection = false,
    )

    format.emitError(
      writer = writer,
      enrichedMessage = "Context length exceeded",
      suggestion = "Reduce tokens",
      kind = ErrorKind.CONTEXT_OVERFLOW,
      oaiErrorJson = "{}",
      headerWritten = true,
    )

    val output = sw.toString()
    assertTrue(output.contains("event: error"))
    assertTrue(output.contains("invalid_request_error"))
    assertTrue(output.contains("Context length exceeded"))
  }

  @Test
  fun anthropicMessagesFormat_buildAssistantTextExcludesThinking() {
    val format = AnthropicMessagesFormat(
      modelName = "claude-3-opus",
      requestModelId = "claude-3-opus-20240229",
      stopSequences = null,
      tools = null,
      hasSchemaInjection = false,
    )

    val text = format.buildAssistantText("Visible reply", "Hidden internal reasoning")
    assertEquals("Visible reply", text)
  }
}
