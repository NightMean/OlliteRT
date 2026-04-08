package com.ollitert.llm.server.service

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
  val created: Long = System.currentTimeMillis() / 1000,
  val owned_by: String = "ollitert",
  val capabilities: LlmHttpModelCapabilities = LlmHttpModelCapabilities(),
)

@Serializable
data class LlmHttpModelList(val `object`: String = "list", val data: List<LlmHttpModelItem>)

object LlmHttpResponseRenderer {
  fun renderJsonError(error: String): String {
    val escaped = LlmHttpBridgeUtils.escapeSseText(error)
    val typeSlug = error.replace(' ', '_').replace(Regex("[^a-zA-Z0-9_]"), "").take(40)
    return """{"error":{"message":"$escaped","type":"${typeSlug}_error","code":null}}"""
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
    val now = System.currentTimeMillis() / 1000
    val respId = "resp-${java.util.UUID.randomUUID()}"
    val msgId = "msg-${java.util.UUID.randomUUID()}"
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

  /** Usage chunk sent before [DONE] when stream_options.include_usage = true. */
  fun buildChatStreamUsageChunk(
    chatId: String, modelId: String, now: Long,
    promptTokens: Int, completionTokens: Int,
  ): String {
    val total = promptTokens + completionTokens
    return """data: {"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[],"usage":{"prompt_tokens":$promptTokens,"completion_tokens":$completionTokens,"total_tokens":$total}}""" + "\n\n"
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
   * Builds streaming SSE chunks for a tool call in chat.completion.chunk format.
   * Emits: (1) role + tool_calls header, (2) arguments delta, (3) finish_reason chunk.
   * Does NOT include [DONE] — caller should emit SSE_DONE separately.
   */
  fun buildChatStreamToolCallChunks(chatId: String, modelId: String, now: Long, toolCall: ToolCall): String {
    val escapedName = LlmHttpBridgeUtils.escapeSseText(toolCall.function.name)
    val escapedArgs = LlmHttpBridgeUtils.escapeSseText(toolCall.function.arguments)
    val callId = toolCall.id
    return buildString {
      // Chunk 1: role + tool_calls with name and empty arguments
      append("""data: {"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[{"index":0,"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"$callId","type":"function","function":{"name":"$escapedName","arguments":""}}]},"finish_reason":null}]}""")
      append("\n\n")
      // Chunk 2: arguments delta
      append("""data: {"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"$escapedArgs"}}]},"finish_reason":null}]}""")
      append("\n\n")
      // Chunk 3: finish_reason
      append("""data: {"id":"$chatId","object":"chat.completion.chunk","created":$now,"model":"$modelId","choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""")
      append("\n\n")
    }
  }

  fun buildToolCallSsePayload(modelId: String, toolCall: ToolCall): String {
    val now = System.currentTimeMillis() / 1000
    val respId = "resp-${java.util.UUID.randomUUID()}"
    val fcId = "fc-${java.util.UUID.randomUUID()}"
    val callId = toolCall.id
    val name = toolCall.function.name
    val escapedArgs = LlmHttpBridgeUtils.escapeSseText(toolCall.function.arguments)

    return buildString {
      append(emitSseEvent("response.created", """{"type":"response.created","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))
      append(emitSseEvent("response.in_progress", """{"type":"response.in_progress","response":{"id":"$respId","object":"response","created_at":$now,"status":"in_progress","model":"$modelId","output":[]}}"""))
      append(emitSseEvent("response.output_item.added", """{"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","id":"$fcId","call_id":"$callId","name":"$name","arguments":"","status":"in_progress"}}"""))
      append(emitSseEvent("response.function_call_arguments.delta", """{"type":"response.function_call_arguments.delta","output_index":0,"item_id":"$fcId","call_id":"$callId","delta":"$escapedArgs"}"""))
      append(emitSseEvent("response.function_call_arguments.done", """{"type":"response.function_call_arguments.done","output_index":0,"item_id":"$fcId","call_id":"$callId","arguments":"$escapedArgs"}"""))
      append(emitSseEvent("response.output_item.done", """{"type":"response.output_item.done","output_index":0,"item":{"type":"function_call","id":"$fcId","call_id":"$callId","name":"$name","arguments":"$escapedArgs","status":"completed"}}"""))
      append(emitSseEvent("response.completed", """{"type":"response.completed","response":{"id":"$respId","object":"response","created_at":$now,"status":"completed","model":"$modelId","output":[{"type":"function_call","id":"$fcId","call_id":"$callId","name":"$name","arguments":"$escapedArgs","status":"completed"}],"usage":{"input_tokens":0,"output_tokens":0,"total_tokens":0}}}"""))
      append("data: [DONE]\n\n")
    }
  }
}
