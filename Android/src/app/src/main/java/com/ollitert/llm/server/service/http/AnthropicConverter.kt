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

package com.ollitert.llm.server.service.http

import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.service.inference.*

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Pure translation between the Anthropic Messages API request shape and the
 * server's internal [ChatRequest] / [ChatMessage] types.
 *
 * Stateless and side-effect-free — all I/O (auth, model selection, prompt
 * compaction, inference) happens in the caller. The converter only reshapes
 * data and rejects unsupported block kinds with [AnthropicConversionError].
 *
 * Unsupported features (rejected with `invalid_request_error`):
 *   - URL-source image blocks (`{type:"image", source:{type:"url", ...}}`)
 *   - Document/PDF blocks (`type == "document"`)
 *   - Computer-use tools (`computer_20241022`, `text_editor_20241022`, `bash_20241022`)
 *
 * Silently dropped (no-op):
 *   - `cache_control` on any content block
 *   - Echoed `thinking` blocks in assistant turns (the runtime regenerates them)
 *   - `metadata`, `service_tier`, `parallel_tool_calls`
 */
object AnthropicConverter {

  private val UNSUPPORTED_TOOL_TYPES = setOf(
    "computer_20241022",
    "text_editor_20241022",
    "bash_20241022",
  )

  /**
   * Parse a raw Anthropic request body into typed form. Throws
   * [AnthropicConversionError] on JSON failure (mapped to 400 by the handler).
   */
  fun parseRequest(json: Json, body: String): AnthropicMessagesRequest =
    try {
      json.decodeFromString(AnthropicMessagesRequest.serializer(), body)
    } catch (e: SerializationException) {
      throw AnthropicConversionError("invalid_request_error", "Invalid JSON: ${e.message}")
    }

  /**
   * Translate an Anthropic request into the internal [ChatRequest] used by the
   * existing chat-completion pipeline. The returned object is consumed by
   * [EndpointHandlers.runChatCompletion] which already handles sampling, prompt
   * compaction, multimodal decoding, and tool dispatch.
   */
  fun toInternalChatRequest(req: AnthropicMessagesRequest): ChatRequest {
    if (req.max_tokens == null) {
      throw AnthropicConversionError("invalid_request_error", "max_tokens: required")
    }
    if (req.messages.isEmpty()) {
      throw AnthropicConversionError("invalid_request_error", "messages: required")
    }

    val messages = buildList {
      systemMessage(req.system)?.let { add(it) }
      for (msg in req.messages) addAll(translateMessage(msg))
    }

    return ChatRequest(
      model = req.model,
      messages = messages,
      stream = req.stream,
      temperature = req.temperature,
      top_p = req.top_p,
      top_k = req.top_k,
      max_tokens = req.max_tokens,
      stop = req.stop_sequences ?: emptyList(),
      tools = req.tools?.map { translateToolDef(it) },
      tool_choice = translateToolChoice(req.tool_choice),
    )
  }

  // ── System ──────────────────────────────────────────────────────────────────

  private fun systemMessage(system: JsonElement?): ChatMessage? {
    if (system == null || system is JsonNull) return null
    val text = when (system) {
      is JsonPrimitive -> system.content
      is JsonArray -> system.jsonArray.joinToString("\n\n") { block ->
        val obj = block as? JsonObject
          ?: throw AnthropicConversionError("invalid_request_error", "system: array entries must be objects")
        when (val type = obj["type"]?.jsonPrimitive?.content) {
          "text" -> obj["text"]?.jsonPrimitive?.content.orEmpty()
          else -> throw AnthropicConversionError(
            "invalid_request_error",
            "system: unsupported block type '$type' (only 'text' is allowed)",
          )
        }
      }
      else -> throw AnthropicConversionError("invalid_request_error", "system: must be string or array of text blocks")
    }
    if (text.isBlank()) return null
    return ChatMessage(role = "system", content = ChatContent(text = text))
  }

  // ── Messages ────────────────────────────────────────────────────────────────

  private fun translateMessage(msg: AnthropicMessage): List<ChatMessage> {
    val role = msg.role
    return when (val content = msg.content) {
      is JsonNull -> listOf(ChatMessage(role = role, content = ChatContent(text = "")))
      is JsonPrimitive -> listOf(ChatMessage(role = role, content = ChatContent(text = content.content)))
      is JsonArray -> translateBlockArray(role, content)
      else -> throw AnthropicConversionError(
        "invalid_request_error",
        "messages[$role].content: must be string or array of blocks",
      )
    }
  }

  /**
   * Translate an array of typed content blocks. Tool-use and tool-result blocks
   * are split into their own [ChatMessage] entries so the existing OAI prompt
   * builder can treat them as separate turns. Text/image blocks aggregate into
   * a single message via [ChatContent.parts].
   */
  private fun translateBlockArray(role: String, array: JsonArray): List<ChatMessage> {
    val parts = mutableListOf<ContentPart>()
    val toolCalls = mutableListOf<ToolCall>()
    val toolResultMessages = mutableListOf<ChatMessage>()

    for (element in array) {
      val obj = element as? JsonObject
        ?: throw AnthropicConversionError("invalid_request_error", "content blocks must be objects")
      when (val type = obj["type"]?.jsonPrimitive?.content) {
        "text" -> parts.add(
          ContentPart(type = "text", text = obj["text"]?.jsonPrimitive?.content.orEmpty())
        )
        "image" -> parts.add(translateImageBlock(obj))
        "tool_use" -> toolCalls.add(translateToolUseBlock(obj))
        "tool_result" -> toolResultMessages.add(translateToolResultBlock(obj))
        "thinking", "redacted_thinking" -> {
          // Echoed thinking from prior assistant turns — drop. The runtime
          // regenerates thinking content on each request.
        }
        "document" -> throw AnthropicConversionError(
          "invalid_request_error",
          "Document/PDF blocks are not supported",
        )
        else -> throw AnthropicConversionError(
          "invalid_request_error",
          "Unsupported content block type: '$type'",
        )
      }
    }

    val messages = mutableListOf<ChatMessage>()
    if (parts.isNotEmpty() || toolCalls.isNotEmpty()) {
      // Build the primary message. text="" + parts so the multimodal pipeline
      // sees image_url parts; the existing serializer handles parts-only content.
      val combinedText = parts.filter { it.type == "text" }
        .mapNotNull { it.text }
        .joinToString(" ")
      val content = if (parts.isEmpty()) {
        ChatContent(text = "")
      } else {
        ChatContent(text = combinedText, parts = parts.toList())
      }
      messages.add(
        ChatMessage(
          role = role,
          content = content,
          tool_calls = toolCalls.takeIf { it.isNotEmpty() },
        )
      )
    }
    messages.addAll(toolResultMessages)
    return messages
  }

  private fun translateImageBlock(obj: JsonObject): ContentPart {
    val source = obj["source"]?.jsonObject
      ?: throw AnthropicConversionError("invalid_request_error", "image: source missing")
    return when (val sourceType = source["type"]?.jsonPrimitive?.content) {
      "base64" -> {
        val mediaType = source["media_type"]?.jsonPrimitive?.content
          ?: throw AnthropicConversionError("invalid_request_error", "image: source.media_type missing")
        val data = source["data"]?.jsonPrimitive?.content
          ?: throw AnthropicConversionError("invalid_request_error", "image: source.data missing")
        ContentPart(
          type = "image_url",
          image_url = ImageUrl(url = "data:$mediaType;base64,$data"),
        )
      }
      "url" -> throw AnthropicConversionError(
        "invalid_request_error",
        "URL image sources are not supported; provide base64-encoded image data",
      )
      else -> throw AnthropicConversionError(
        "invalid_request_error",
        "image: unsupported source type '$sourceType'",
      )
    }
  }

  private fun translateToolUseBlock(obj: JsonObject): ToolCall {
    val id = obj["id"]?.jsonPrimitive?.content
      ?: throw AnthropicConversionError("invalid_request_error", "tool_use: id missing")
    val name = obj["name"]?.jsonPrimitive?.content
      ?: throw AnthropicConversionError("invalid_request_error", "tool_use: name missing")
    val input = obj["input"] ?: JsonObject(emptyMap())
    return ToolCall(
      id = id,
      type = "function",
      function = ToolCallFunction(name = name, arguments = input.toString()),
    )
  }

  private fun translateToolResultBlock(obj: JsonObject): ChatMessage {
    val toolUseId = obj["tool_use_id"]?.jsonPrimitive?.content
      ?: throw AnthropicConversionError("invalid_request_error", "tool_result: tool_use_id missing")
    val isError = obj["is_error"]?.jsonPrimitive?.content == "true"
    val rawContent = obj["content"]
    val text = when (rawContent) {
      null, is JsonNull -> ""
      is JsonPrimitive -> rawContent.content
      is JsonArray -> rawContent.jsonArray.mapNotNull { entry ->
        val entryObj = entry as? JsonObject ?: return@mapNotNull null
        // Only text blocks contribute — image blocks inside tool_result are dropped.
        if (entryObj["type"]?.jsonPrimitive?.content == "text") {
          entryObj["text"]?.jsonPrimitive?.content
        } else null
      }.joinToString("\n")
      else -> ""
    }
    val finalText = if (isError) "[error] $text" else text
    return ChatMessage(
      role = "tool",
      content = ChatContent(text = finalText),
      tool_call_id = toolUseId,
    )
  }

  // ── Tools ───────────────────────────────────────────────────────────────────

  private fun translateToolDef(def: AnthropicToolDef): ToolSpec {
    if (def.name in UNSUPPORTED_TOOL_TYPES) {
      throw AnthropicConversionError(
        "invalid_request_error",
        "Unsupported tool type: '${def.name}' (computer-use tools are not implemented)",
      )
    }
    return ToolSpec(
      type = "function",
      function = ToolFunctionDef(
        name = def.name,
        description = def.description,
        parameters = def.input_schema,
      ),
    )
  }

  // ── Response side ───────────────────────────────────────────────────────────

  /**
   * Re-shape a serialized OpenAI [ChatResponse] body into an Anthropic
   * [AnthropicMessagesResponse] body.
   *
   * Why JSON-in / JSON-out instead of typed-in / JSON-out: the existing
   * [EndpointHandlers.runChatCompletion] core captures the OAI response as a
   * String and feeds it back into `captureResponse(...)`. The Anthropic handler
   * supplies an adapter that calls this function so the captured Anthropic body
   * matches the body actually returned to the client.
   *
   *   - `content` is built from the assistant message's `content` (text) and
   *     `tool_calls` (one `tool_use` block per call). A leading `<think>...</think>`
   *     segment becomes a separate `thinking` block.
   *   - `stop_reason` is mapped from the OAI `finish_reason`. When the caller
   *     observed a stop-sequence match, [matchedStopSequence] takes precedence
   *     and produces `stop_reason="stop_sequence"` with the matched text in
   *     the top-level `stop_sequence` field.
   *   - `id` defaults to `msg_<requestId>` when supplied so log entries cross-link.
   */
  fun toAnthropicResponse(
    json: Json,
    oaiResponseBody: String,
    requestedModelId: String,
    requestId: String? = null,
    matchedStopSequence: String? = null,
  ): String {
    val root = try {
      json.parseToJsonElement(oaiResponseBody).jsonObject
    } catch (e: Exception) {
      // Caller already returned this string; treat parse failure as opaque api_error.
      throw AnthropicConversionError("api_error", "Failed to re-shape upstream response: ${e.message}")
    }
    // Every field below is null-tolerant: kotlinx.serialization with encodeDefaults=true
    // emits "tool_calls":null, "usage":null, "logprobs":null, etc. on the wire, and
    // calling .jsonArray / .jsonObject on a JsonNull throws at runtime
    // ("Element class kotlinx.serialization.json.JsonNull is not a JsonArray").
    fun JsonElement?.asObjectOrNull(): JsonObject? = (this as? JsonObject)
    fun JsonElement?.asArrayOrNull(): JsonArray? = (this as? JsonArray)
    fun JsonElement?.asPrimitiveOrNull(): JsonPrimitive? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }

    val msgId = requestId?.let { "msg_$it" } ?: root["id"].asPrimitiveOrNull()?.content ?: "msg_unknown"
    val responseModel = root["model"].asPrimitiveOrNull()?.content ?: requestedModelId
    val choice = root["choices"].asArrayOrNull()?.firstOrNull().asObjectOrNull()
    val message = choice?.get("message").asObjectOrNull()
    val rawText = message?.get("content").asPrimitiveOrNull()?.content.orEmpty()
    val toolCalls = message?.get("tool_calls").asArrayOrNull()
    val oaiFinish = choice?.get("finish_reason").asPrimitiveOrNull()?.content ?: FinishReason.STOP

    val (thinkingText, visibleText) = splitThinkingAndText(rawText)

    val contentBlocks = buildJsonArray {
      if (thinkingText.isNotEmpty()) {
        add(buildJsonObject {
          put("type", "thinking")
          put("thinking", thinkingText)
          put("signature", "")
        })
      }
      if (visibleText.isNotEmpty()) {
        add(buildJsonObject {
          put("type", "text")
          put("text", visibleText)
        })
      }
      if (toolCalls != null) {
        for (call in toolCalls) {
          val callObj = call.asObjectOrNull() ?: continue
          val function = callObj["function"].asObjectOrNull() ?: continue
          val name = function["name"].asPrimitiveOrNull()?.content ?: continue
          val argsRaw = function["arguments"].asPrimitiveOrNull()?.content.orEmpty()
          val parsedArgs = parseArgumentsAsJson(json, argsRaw)
          add(buildJsonObject {
            put("type", "tool_use")
            put("id", callObj["id"].asPrimitiveOrNull()?.content ?: "toolu_unknown")
            put("name", name)
            put("input", parsedArgs)
          })
        }
      }
    }

    val stopReason = mapStopReason(oaiFinish, matchedStopSequence != null)
    val usage = root["usage"].asObjectOrNull()
    val inputTokens = usage?.get("prompt_tokens").asPrimitiveOrNull()?.content?.toIntOrNull() ?: 0
    val outputTokens = usage?.get("completion_tokens").asPrimitiveOrNull()?.content?.toIntOrNull() ?: 0

    val payload = buildJsonObject {
      put("id", msgId)
      put("type", "message")
      put("role", "assistant")
      put("model", responseModel)
      put("content", contentBlocks)
      put("stop_reason", stopReason)
      if (matchedStopSequence != null && stopReason == "stop_sequence") {
        put("stop_sequence", matchedStopSequence)
      } else {
        put("stop_sequence", JsonNull)
      }
      put("usage", buildJsonObject {
        put("input_tokens", inputTokens)
        put("output_tokens", outputTokens)
        put("cache_creation_input_tokens", 0)
        put("cache_read_input_tokens", 0)
      })
    }
    return payload.toString()
  }

  /**
   * Split `<think>...</think>RESPONSE` into (thinking, response). The blocking
   * inference path injects literal think tags into the OAI response text; the
   * Anthropic format wants them as a separate content block.
   */
  internal fun splitThinkingAndText(raw: String): Pair<String, String> {
    if (!raw.startsWith("<think>")) return "" to raw
    val close = raw.indexOf("</think>")
    if (close < 0) return "" to raw
    val thinking = raw.substring("<think>".length, close)
    val rest = raw.substring(close + "</think>".length)
    return thinking to rest
  }

  private fun parseArgumentsAsJson(json: Json, raw: String): JsonElement {
    if (raw.isBlank()) return JsonObject(emptyMap())
    return try {
      json.parseToJsonElement(raw)
    } catch (_: Exception) {
      JsonObject(emptyMap())
    }
  }

  private fun mapStopReason(oaiFinish: String, stopSequenceTriggered: Boolean): String = when {
    stopSequenceTriggered -> "stop_sequence"
    oaiFinish == FinishReason.LENGTH -> "max_tokens"
    oaiFinish == FinishReason.TOOL_CALLS -> "tool_use"
    else -> "end_turn"
  }

  /**
   * Translate Anthropic `tool_choice` (always an object) into an OAI-shape
   * [JsonElement]. The downstream [PromptBuilder.resolveToolChoice] then flattens
   * this into a string at request time — we deliberately stop one step short
   * here so existing call sites stay untouched.
   *
   *   {type:"auto"}             → JsonPrimitive("auto")
   *   {type:"any"}              → JsonPrimitive("required")
   *   {type:"tool", name:"X"}   → {"type":"function","function":{"name":"X"}}
   *   null                      → null  (defaults to "auto" downstream)
   */
  private fun translateToolChoice(choice: AnthropicToolChoice?): JsonElement? {
    if (choice == null) return null
    return when (choice.type) {
      "auto" -> JsonPrimitive("auto")
      "any" -> JsonPrimitive("required")
      "tool" -> {
        val name = choice.name ?: throw AnthropicConversionError(
          "invalid_request_error",
          "tool_choice: name required when type='tool'",
        )
        buildJsonObject {
          put("type", "function")
          put("function", buildJsonObject { put("name", name) })
        }
      }
      else -> throw AnthropicConversionError(
        "invalid_request_error",
        "tool_choice: unsupported type '${choice.type}'",
      )
    }
  }
}
