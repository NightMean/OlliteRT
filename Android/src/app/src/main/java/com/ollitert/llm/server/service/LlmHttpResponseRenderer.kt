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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LlmHttpModelCapabilities(
  val image: Boolean = false,
  val audio: Boolean = false,
  val thinking: Boolean = false,
)

@Serializable
data class LlmHttpModelItem(
  val id: String,
  val `object`: String = "model",
  val created: Long = LlmHttpBridgeUtils.epochSeconds(),
  val owned_by: String = "ollitert",
  val capabilities: LlmHttpModelCapabilities = LlmHttpModelCapabilities(),
)

@Serializable
data class LlmHttpModelList(val `object`: String = "list", val data: List<LlmHttpModelItem>)

object LlmHttpResponseRenderer {
  // Strips non-slug chars from error messages to generate OpenAI-compatible error type slugs
  private val NON_SLUG_REGEX = Regex("[^a-zA-Z0-9_]")

  /**
   * Render an OpenAI-compatible JSON error response.
   *
   * @param suggestion optional recovery hint — rendered as a separate `"suggestion"` field
   *   so API clients can distinguish the raw error from the guidance.
   * @param category when provided, maps to an OpenAI-compatible `"type"` value
   *   (e.g. `server_error`, `invalid_request_error`). Falls back to a slug derived
   *   from the error message when null.
   */
  fun renderJsonError(
    error: String,
    suggestion: String? = null,
    category: ErrorCategory? = null,
  ): String {
    val escaped = LlmHttpBridgeUtils.escapeSseText(error)
    val type = if (category != null) {
      LlmHttpErrorSuggestions.openAiErrorType(category, error)
    } else {
      error.replace(' ', '_').replace(NON_SLUG_REGEX, "").take(40) + "_error"
    }
    val suggestionField = if (suggestion != null) {
      ""","suggestion":"${LlmHttpBridgeUtils.escapeSseText(suggestion)}""""
    } else ""
    return """{"error":{"message":"$escaped","type":"$type","code":null$suggestionField}}"""
  }

  fun renderModelListPayload(json: Json, modelIds: List<String>, fallbackId: String): String {
    val ids = if (modelIds.isEmpty()) listOf(fallbackId) else modelIds
    return json.encodeToString(LlmHttpModelList(data = ids.map { id -> LlmHttpModelItem(id = id) }))
  }

  fun renderModelListWithCapabilities(
    json: Json,
    models: List<com.ollitert.llm.server.data.AllowedModel>,
    fallbackId: String,
  ): String {
    if (models.isEmpty()) {
      return json.encodeToString(LlmHttpModelList(data = listOf(LlmHttpModelItem(id = fallbackId))))
    }
    val items = models.map { m ->
      LlmHttpModelItem(
        id = m.name,
        capabilities = LlmHttpModelCapabilities(
          image = m.llmSupportImage == true,
          audio = m.llmSupportAudio == true,
          thinking = m.llmSupportThinking == true,
        ),
      )
    }
    return json.encodeToString(LlmHttpModelList(data = items))
  }

  fun emitSseEvent(event: String, payload: String): String = "event: $event\n" + "data: $payload\n\n"

  fun buildTextSsePayload(modelId: String, text: String, inputTokens: Int = 0, outputTokens: Int = 0): String {
    val now = LlmHttpBridgeUtils.epochSeconds()
    val respId = LlmHttpBridgeUtils.generateResponseId()
    val msgId = LlmHttpBridgeUtils.generateMessageId()
    val esc = LlmHttpBridgeUtils.escapeSseText(text)
    val totalTokens = inputTokens + outputTokens

    return buildString {
      append(emitSseEvent("response.created", """{"type":"response.created","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))
      append(emitSseEvent("response.in_progress", """{"type":"response.in_progress","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))
      append(emitSseEvent("response.output_item.added", """{"type":"response.output_item.added","item":{"id":"$msgId","type":"message","status":"in_progress","content":[],"role":"assistant"},"output_index":0,"sequence_number":0}"""))
      append(emitSseEvent("response.content_part.added", """{"type":"response.content_part.added","content_index":0,"item_id":"$msgId","output_index":0,"part":{"type":"output_text","annotations":[],"logprobs":[],"text":""}}"""))
      append(emitSseEvent("response.output_text.delta", """{"type":"response.output_text.delta","content_index":0,"delta":"$esc","item_id":"$msgId","output_index":0}"""))
      append(emitSseEvent("response.output_text.done", """{"type":"response.output_text.done","content_index":0,"item_id":"$msgId","output_index":0,"text":"$esc"}"""))
      append(emitSseEvent("response.content_part.done", """{"type":"response.content_part.done","content_index":0,"item_id":"$msgId","output_index":0,"part":{"type":"output_text","annotations":[],"logprobs":[],"text":"$esc"}}"""))
      append(emitSseEvent("response.output_item.done", """{"type":"response.output_item.done","item":{"id":"$msgId","type":"message","status":"completed","content":[{"type":"output_text","annotations":[],"logprobs":[],"text":"$esc"}],"role":"assistant"},"output_index":0}"""))
      append(emitSseEvent("response.completed", """{"type":"response.completed","response":{"id":"$respId","object":"response","created_at":$now,"status":"completed","model":"$modelId","output":[{"id":"$msgId","type":"message","status":"completed","content":[{"type":"output_text","annotations":[],"logprobs":[],"text":"$esc"}],"role":"assistant"}],"usage":{"input_tokens":$inputTokens,"output_tokens":$outputTokens,"total_tokens":$totalTokens}}}"""))
      append("data: [DONE]\n\n")
    }
  }

  // ── Per-token streaming SSE builders ─────────────────────────────────────

  /** Emits the opening events before any delta tokens. */
  fun buildStreamingHeader(modelId: String, respId: String, msgId: String, now: Long): String = buildString {
    append(emitSseEvent("response.created", """{"type":"response.created","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))
    append(emitSseEvent("response.in_progress", """{"type":"response.in_progress","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))
    append(emitSseEvent("response.output_item.added", """{"type":"response.output_item.added","item":{"id":"$msgId","type":"message","status":"in_progress","content":[],"role":"assistant"},"output_index":0,"sequence_number":0}"""))
    append(emitSseEvent("response.content_part.added", """{"type":"response.content_part.added","content_index":0,"item_id":"$msgId","output_index":0,"part":{"type":"output_text","annotations":[],"logprobs":[],"text":""}}"""))
  }

  /** Emits a single token delta event. [escapedDelta] must already be SSE-safe. */
  fun buildTextDeltaSseEvent(msgId: String, escapedDelta: String): String =
    emitSseEvent("response.output_text.delta", """{"type":"response.output_text.delta","content_index":0,"delta":"$escapedDelta","item_id":"$msgId","output_index":0}""")

  /** Emits the closing events after all delta tokens. [escapedFullText] must already be SSE-safe. */
  fun buildStreamingFooter(modelId: String, respId: String, msgId: String, now: Long, escapedFullText: String, inputTokens: Int = 0, outputTokens: Int = 0): String = buildString {
    val totalTokens = inputTokens + outputTokens
    append(emitSseEvent("response.output_text.done", """{"type":"response.output_text.done","content_index":0,"item_id":"$msgId","output_index":0,"text":"$escapedFullText"}"""))
    append(emitSseEvent("response.content_part.done", """{"type":"response.content_part.done","content_index":0,"item_id":"$msgId","output_index":0,"part":{"type":"output_text","annotations":[],"logprobs":[],"text":"$escapedFullText"}}"""))
    append(emitSseEvent("response.output_item.done", """{"type":"response.output_item.done","item":{"id":"$msgId","type":"message","status":"completed","content":[{"type":"output_text","annotations":[],"logprobs":[],"text":"$escapedFullText"}],"role":"assistant"},"output_index":0}"""))
    append(emitSseEvent("response.completed", """{"type":"response.completed","response":{"id":"$respId","object":"response","created_at":$now,"status":"completed","model":"$modelId","output":[{"id":"$msgId","type":"message","status":"completed","content":[{"type":"output_text","annotations":[],"logprobs":[],"text":"$escapedFullText"}],"role":"assistant"}],"usage":{"input_tokens":$inputTokens,"output_tokens":$outputTokens,"total_tokens":$totalTokens}}}"""))
    append("data: [DONE]\n\n")
  }

  // ── OpenAI Chat Completions SSE builders (chat.completion.chunk format) ───

  const val SSE_DONE = "data: [DONE]\n\n"

  /** First chunk: role declaration with empty content (OpenAI sends content="" in first chunk). */
  fun buildChatStreamFirstChunk(chatId: String, modelId: String, now: Long): String =
    "data: ${buildChatChunkJson(chatId, modelId, now, deltaRole = "assistant", deltaContent = "", finishReason = null)}\n\n"

  /** Token delta chunk. */
  fun buildChatStreamDeltaChunk(chatId: String, modelId: String, now: Long, token: String): String =
    "data: ${buildChatChunkJson(chatId, modelId, now, deltaRole = null, deltaContent = token, finishReason = null)}\n\n"

  /** Final chunk with finish_reason (does NOT include [DONE] — emit SSE_DONE separately). */
  fun buildChatStreamFinalChunk(chatId: String, modelId: String, now: Long, finishReason: String = "stop"): String =
    "data: ${buildChatChunkJson(chatId, modelId, now, deltaRole = null, deltaContent = null, finishReason = finishReason)}\n\n"

  /**
   * Usage chunk sent before [DONE] when stream_options.include_usage = true.
   * Optionally includes non-standard `timings` object (widely used by local LLM tooling
   * like Open WebUI for per-message performance display).
   */
  fun buildChatStreamUsageChunk(
    chatId: String, modelId: String, now: Long,
    promptTokens: Int, completionTokens: Int,
    timingsJson: String? = null,
  ): String {
    val total = promptTokens + completionTokens
    val timingsSuffix = if (timingsJson != null) ""","timings":$timingsJson""" else ""
    return """data: {"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[],"usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens,"total_tokens":$total}$timingsSuffix}""" + "\n\n"
  }

  private fun buildChatChunkJson(
    chatId: String,
    modelId: String,
    now: Long,
    deltaRole: String?,
    deltaContent: String?,  // null = omit field, "" = include as empty string
    finishReason: String?,
  ): String {
    val deltaFields = buildString {
      var first = true
      if (deltaRole != null) { append("\"role\":\"$deltaRole\""); first = false }
      if (deltaContent != null) {
        if (!first) append(",")
        append("\"content\":\"${LlmHttpBridgeUtils.escapeSseText(deltaContent)}\"")
      }
    }
    val fr = if (finishReason != null) "\"$finishReason\"" else "null"
    return """{"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[{"index":0,"delta":{$deltaFields},"finish_reason":$fr}]}"""
  }

  /**
   * Builds streaming SSE chunks for one or more tool calls in chat.completion.chunk format.
   * Each tool call gets its own indexed entry in the `tool_calls` array, matching the
   * OpenAI streaming spec that HA integrations (extended_openai_conversation, local_openai) parse.
   *
   * Emits per tool call: (1) header with id+name+empty args, (2) arguments delta.
   * Then a single finish_reason chunk at the end.
   * Does NOT include [DONE] — caller should emit SSE_DONE separately.
   */
  fun buildChatStreamToolCallChunks(chatId: String, modelId: String, now: Long, toolCalls: List<ToolCall>): String {
    return buildString {
      for ((index, toolCall) in toolCalls.withIndex()) {
        val escapedName = LlmHttpBridgeUtils.escapeSseText(toolCall.function.name)
        val escapedArgs = LlmHttpBridgeUtils.escapeSseText(toolCall.function.arguments)
        val callId = toolCall.id

        // Chunk: role (first call only) + tool_calls entry with name and empty arguments
        val roleField = if (index == 0) """"role":"assistant","content":null,""" else ""
        append("""data: {"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[{"index":0,"delta":{${roleField}"tool_calls":[{"index":$index,"id":"$callId","type":"function","function":{"name":"$escapedName","arguments":""}}]},"finish_reason":null}]}""")
        append("\n\n")
        // Chunk: arguments delta for this tool call
        append("""data: {"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[{"index":0,"delta":{"tool_calls":[{"index":$index,"function":{"arguments":"$escapedArgs"}}]},"finish_reason":null}]}""")
        append("\n\n")
      }
      // Final chunk: finish_reason
      append("""data: {"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""")
      append("\n\n")
    }
  }

  fun buildToolCallSsePayload(modelId: String, toolCall: ToolCall, inputTokens: Int = 0, outputTokens: Int = 0): String {
    val now = LlmHttpBridgeUtils.epochSeconds()
    val respId = LlmHttpBridgeUtils.generateResponseId()
    val fcId = LlmHttpBridgeUtils.generateFunctionCallId()
    val callId = toolCall.id
    val name = toolCall.function.name
    val escapedArgs = LlmHttpBridgeUtils.escapeSseText(toolCall.function.arguments)
    val totalTokens = inputTokens + outputTokens

    return buildString {
      append(emitSseEvent("response.created", """{"type":"response.created","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))
      append(emitSseEvent("response.in_progress", """{"type":"response.in_progress","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))
      append(emitSseEvent("response.output_item.added", """{"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","id":"$fcId","call_id":"$callId","name":"$name","arguments":"","status":"in_progress"}}"""))
      append(emitSseEvent("response.function_call_arguments.delta", """{"type":"response.function_call_arguments.delta","output_index":0,"item_id":"$fcId","call_id":"$callId","delta":"$escapedArgs"}"""))
      append(emitSseEvent("response.function_call_arguments.done", """{"type":"response.function_call_arguments.done","output_index":0,"item_id":"$fcId","call_id":"$callId","arguments":"$escapedArgs"}"""))
      append(emitSseEvent("response.output_item.done", """{"type":"response.output_item.done","output_index":0,"item":{"type":"function_call","id":"$fcId","call_id":"$callId","name":"$name","arguments":"$escapedArgs","status":"completed"}}"""))
      append(emitSseEvent("response.completed", """{"type":"response.completed","response":{"id":"$respId","object":"response","created_at":$now,"status":"completed","model":"$modelId","output":[{"type":"function_call","id":"$fcId","call_id":"$callId","name":"$name","arguments":"$escapedArgs","status":"completed"}],"usage":{"input_tokens":$inputTokens,"output_tokens":$outputTokens,"total_tokens":$totalTokens}}}"""))
      append("data: [DONE]\n\n")
    }
  }
}
