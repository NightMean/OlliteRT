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

object ResponseRenderer {
  // Strips non-slug chars from error messages to generate OpenAI-compatible error type slugs
  private val NON_SLUG_REGEX = Regex("[^a-zA-Z0-9_]")

  /**
   * Render an OpenAI-compatible JSON error response.
   *
   * @param suggestion optional recovery hint — rendered as a separate `"suggestion"` field
   *   so API clients can distinguish the raw error from the guidance.
   * @param kind when provided, maps to an OpenAI-compatible `"type"` and `"code"` value
   *   (e.g. `server_error`, `invalid_request_error` / `context_length_exceeded`).
   *   Falls back to a slug derived from the error message when null.
   */
  fun renderJsonError(
    error: String,
    suggestion: String? = null,
    kind: ErrorKind? = null,
  ): String {
    val escaped = BridgeUtils.escapeSseText(error)
    val type = if (kind != null) {
      ErrorSuggestions.openAiErrorType(kind)
    } else {
      error.replace(' ', '_').replace(NON_SLUG_REGEX, "").take(40) + "_error"
    }
    val code = ErrorSuggestions.openAiErrorCode(kind)
    val codeJson = if (code != null) "\"$code\"" else "null"
    val suggestionField = if (suggestion != null) {
      ""","suggestion":"${BridgeUtils.escapeSseText(suggestion)}""""
    } else ""
    return """{"error":{"message":"$escaped","type":"$type","param":null,"code":$codeJson$suggestionField}}"""
  }

  fun emitSseEvent(event: String, payload: String): String = "event: $event\n" + "data: $payload\n\n"

  fun buildTextSsePayload(modelId: String, text: String, inputTokens: Int = 0, outputTokens: Int = 0): String {
    val now = BridgeUtils.epochSeconds()
    val respId = BridgeUtils.generateResponseId()
    val msgId = BridgeUtils.generateMessageId()
    val esc = BridgeUtils.escapeSseText(text)
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
  fun buildChatStreamFinalChunk(chatId: String, modelId: String, now: Long, finishReason: String = FinishReason.STOP): String =
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

  // ── OpenAI Completions SSE builders (text_completion format) ────────────

  fun buildCompletionStreamChunk(cmplId: String, modelId: String, now: Long, token: String): String =
    "data: ${buildCompletionChunkJson(cmplId, modelId, now, BridgeUtils.escapeSseText(token), null)}\n\n"

  fun buildCompletionStreamFinalChunk(cmplId: String, modelId: String, now: Long, finishReason: String = FinishReason.STOP): String =
    "data: ${buildCompletionChunkJson(cmplId, modelId, now, "", finishReason)}\n\n"

  fun buildCompletionStreamUsageChunk(
    cmplId: String, modelId: String, now: Long,
    promptTokens: Int, completionTokens: Int,
    timingsJson: String? = null,
  ): String {
    val total = promptTokens + completionTokens
    val timingsSuffix = if (timingsJson != null) ""","timings":$timingsJson""" else ""
    return """data: {"id":"$cmplId","object":"text_completion","created":$now,"model":"$modelId","choices":[],"usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens,"total_tokens":$total}$timingsSuffix}""" + "\n\n"
  }

  private fun buildCompletionChunkJson(
    cmplId: String,
    modelId: String,
    now: Long,
    text: String,
    finishReason: String?,
  ): String {
    val fr = if (finishReason != null) "\"$finishReason\"" else "null"
    return """{"id":"$cmplId","object":"text_completion","created":$now,"model":"$modelId","choices":[{"text":"$text","index":0,"finish_reason":$fr}]}"""
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
        append("\"content\":\"${BridgeUtils.escapeSseText(deltaContent)}\"")
      }
    }
    val fr = if (finishReason != null) "\"$finishReason\"" else "null"
    return """{"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[{"index":0,"delta":{$deltaFields},"logprobs":null,"finish_reason":$fr}]}"""
  }

  /**
   * Builds streaming SSE events for tool calls in Responses API format.
   * Each tool call gets: output_item.added → function_call_arguments.delta →
   * function_call_arguments.done → output_item.done.
   * Then a response.completed event wrapping all tool calls.
   */
  fun buildResponsesStreamToolCallEvents(
    respId: String,
    modelId: String,
    now: Long,
    toolCalls: List<ToolCall>,
    inputTokens: Int = 0,
    outputTokens: Int = 0,
  ): String = buildString {
    val totalTokens = inputTokens + outputTokens

    append(emitSseEvent("response.created", """{"type":"response.created","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))
    append(emitSseEvent("response.in_progress", """{"type":"response.in_progress","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))

    data class FcData(val fcId: String, val callId: String, val escapedName: String, val escapedArgs: String)
    val fcDataList = toolCalls.map { tc ->
      FcData(
        fcId = BridgeUtils.generateFunctionCallId(),
        callId = tc.id,
        escapedName = BridgeUtils.escapeSseText(tc.function.name),
        escapedArgs = BridgeUtils.escapeSseText(tc.function.arguments),
      )
    }

    var seqNum = 0
    for ((index, fc) in fcDataList.withIndex()) {
      append(emitSseEvent("response.output_item.added",
        """{"type":"response.output_item.added","output_index":$index,"item":{"id":"${fc.fcId}","type":"function_call","call_id":"${fc.callId}","name":"${fc.escapedName}","arguments":"","status":"in_progress"},"sequence_number":${seqNum++}}"""))

      append(emitSseEvent("response.function_call_arguments.delta",
        """{"type":"response.function_call_arguments.delta","output_index":$index,"item_id":"${fc.fcId}","delta":"${fc.escapedArgs}"}"""))

      append(emitSseEvent("response.function_call_arguments.done",
        """{"type":"response.function_call_arguments.done","output_index":$index,"item_id":"${fc.fcId}","arguments":"${fc.escapedArgs}"}"""))

      append(emitSseEvent("response.output_item.done",
        """{"type":"response.output_item.done","output_index":$index,"item":{"id":"${fc.fcId}","type":"function_call","call_id":"${fc.callId}","name":"${fc.escapedName}","arguments":"${fc.escapedArgs}","status":"completed"}}"""))
    }

    val outputItemsJson = fcDataList.withIndex().joinToString(",") { (index, fc) ->
      """{"id":"${fc.fcId}","type":"function_call","call_id":"${fc.callId}","name":"${fc.escapedName}","arguments":"${fc.escapedArgs}","status":"completed"}"""
    }
    append(emitSseEvent("response.completed",
      """{"type":"response.completed","response":{"id":"$respId","object":"response","created_at":$now,"status":"completed","model":"$modelId","output":[$outputItemsJson],"usage":{"input_tokens":$inputTokens,"output_tokens":$outputTokens,"total_tokens":$totalTokens}}}"""))

    append("data: [DONE]\n\n")
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
        val escapedName = BridgeUtils.escapeSseText(toolCall.function.name)
        val escapedArgs = BridgeUtils.escapeSseText(toolCall.function.arguments)
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

}
