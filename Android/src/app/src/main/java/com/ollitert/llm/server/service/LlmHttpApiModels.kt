package com.ollitert.llm.server.service

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable data class Usage(
  val prompt_tokens: Int,
  val completion_tokens: Int,
  val total_tokens: Int = prompt_tokens + completion_tokens,
)

@Serializable data class ResponsesRequest(
  val model: String? = null,
  val input: List<InputMsg>? = null,
  val messages: List<InputMsg>? = null,
  val stream: Boolean? = null,
  val temperature: Double? = null,
  val top_p: Double? = null,
  val top_k: Int? = null,
  val max_output_tokens: Int? = null,      // Responses API uses max_output_tokens
  val tools: List<ToolSpec>? = null,
  val tool_choice: String? = null,
)

@Serializable data class InputMsg(
  val role: String,
  val content: List<InputContent>,
)

@Serializable data class InputContent(
  val type: String,
  val text: String,
)

@Serializable data class ResponsesResponse(
  val id: String,
  val `object`: String = "response",
  val created: Long,
  val model: String,
  val output: List<RespMessage>,
  val usage: Usage,
)

@Serializable data class RespMessage(
  val role: String = "assistant",
  val content: List<RespContent>,
  val finish_reason: String = "stop",
)

@Serializable data class RespContent(
  val type: String = "text",
  val text: String,
)

@Serializable data class GenRes(val text: String, val usage: Usage)

/**
 * Represents the content of a multimodal message part.
 */
@Serializable data class ContentPart(
  val type: String, // "text" or "image_url"
  val text: String? = null,
  val image_url: ImageUrl? = null,
)

@Serializable data class ImageUrl(val url: String)

/**
 * Holds parsed message content that may be a plain string or an array of multimodal parts.
 */
data class ChatContent(
  val text: String,
  val parts: List<ContentPart> = emptyList(),
)

/**
 * Custom serializer for ChatContent that handles both:
 * - `"content": "hello"` (plain string)
 * - `"content": [{"type":"text","text":"hello"},{"type":"image_url",...}]` (multimodal array)
 */
object ChatContentSerializer : KSerializer<ChatContent> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ChatContent")

  override fun deserialize(decoder: Decoder): ChatContent {
    val jsonDecoder = decoder as JsonDecoder
    return when (val element = jsonDecoder.decodeJsonElement()) {
      is JsonPrimitive -> ChatContent(text = element.content)
      is JsonArray -> {
        val parts = element.jsonArray.map { partElement ->
          val obj = partElement.jsonObject
          val type = obj["type"]?.jsonPrimitive?.content ?: "text"
          val text = obj["text"]?.jsonPrimitive?.content
          val imageUrl = obj["image_url"]?.jsonObject?.let { imgObj ->
            ImageUrl(url = imgObj["url"]?.jsonPrimitive?.content ?: "")
          }
          ContentPart(type = type, text = text, image_url = imageUrl)
        }
        val combinedText = parts.filter { it.type == "text" }.mapNotNull { it.text }.joinToString(" ")
        ChatContent(text = combinedText, parts = parts)
      }
      else -> ChatContent(text = "")
    }
  }

  override fun serialize(encoder: Encoder, value: ChatContent) {
    val jsonEncoder = encoder as JsonEncoder
    jsonEncoder.encodeJsonElement(JsonPrimitive(value.text))
  }
}

@Serializable data class ChatMessage(
  val role: String,
  @Serializable(with = ChatContentSerializer::class)
  val content: ChatContent = ChatContent(""),
  val tool_calls: List<ToolCall>? = null,
)

@Serializable data class ToolCallFunction(val name: String, val arguments: String)

@Serializable data class ToolCall(
  val id: String,
  val type: String = "function",
  val function: ToolCallFunction,
)

@Serializable data class ToolFunctionDef(
  val name: String,
  val description: String? = null,
  val parameters: JsonElement? = null,
)

@Serializable data class ToolSpec(val type: String = "function", val function: ToolFunctionDef)

@Serializable data class StreamOptions(
  val include_usage: Boolean = false,
)

@Serializable data class ResponseFormat(
  val type: String = "text", // "text", "json_object", or "json_schema"
)

/**
 * Custom serializer for the `stop` field which can be a single string or an array of strings.
 * OpenAI API allows both `"stop": "\n"` and `"stop": ["\n", "###"]`.
 */
object StopDeserializer : KSerializer<List<String>> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Stop")

  override fun deserialize(decoder: Decoder): List<String> {
    val jsonDecoder = decoder as JsonDecoder
    return when (val element = jsonDecoder.decodeJsonElement()) {
      is JsonPrimitive -> if (element.content.isNotBlank()) listOf(element.content) else emptyList()
      is JsonArray -> element.map { it.jsonPrimitive.content }
      else -> emptyList()
    }
  }

  override fun serialize(encoder: Encoder, value: List<String>) {
    val jsonEncoder = encoder as JsonEncoder
    jsonEncoder.encodeJsonElement(JsonArray(value.map { JsonPrimitive(it) }))
  }
}

@Serializable data class ChatRequest(
  val model: String? = null,
  val messages: List<ChatMessage> = emptyList(),
  val stream: Boolean? = null,
  val stream_options: StreamOptions? = null,
  val temperature: Double? = null,
  val top_p: Double? = null,
  val top_k: Int? = null,                  // Non-standard but widely used (Ollama, llama.cpp)
  val max_tokens: Int? = null,             // Deprecated but still sent by most clients
  val max_completion_tokens: Int? = null,  // Newer replacement for max_tokens
  @Serializable(with = StopDeserializer::class)
  val stop: List<String> = emptyList(),
  val seed: Int? = null,
  val frequency_penalty: Double? = null,   // Accepted, silently ignored (LiteRT limitation)
  val presence_penalty: Double? = null,    // Accepted, silently ignored (LiteRT limitation)
  val response_format: ResponseFormat? = null,
  val tools: List<ToolSpec>? = null,
  val tool_choice: String? = null,
  val user: String? = null,                // Accepted, ignored
  val n: Int? = null,                      // Accepted, ignored (always 1)
  val logprobs: Boolean? = null,           // Accepted, ignored
  val top_logprobs: Int? = null,           // Accepted, ignored
)

@Serializable data class ChatChoice(
  val index: Int,
  val message: ChatMessage,
  val finish_reason: String,
)

@Serializable data class ChatResponse(
  val id: String,
  val `object`: String = "chat.completion",
  val created: Long,
  val model: String,
  val choices: List<ChatChoice>,
  val usage: Usage,
  val system_fingerprint: String? = null,
)

@Serializable data class GenReq(val prompt: String)

// ── Legacy /v1/completions endpoint models ───────────────────────────────────

@Serializable data class CompletionRequest(
  val model: String? = null,
  val prompt: String = "",
  val max_tokens: Int? = null,
  val temperature: Double? = null,
  val top_p: Double? = null,
  val stream: Boolean? = null,
  val stream_options: StreamOptions? = null,
  val stop: JsonElement? = null,  // String or List<String>, handled at runtime
  val suffix: String? = null,
  val echo: Boolean? = null,
  val seed: Int? = null,
  val user: String? = null,
  val frequency_penalty: Double? = null,
  val presence_penalty: Double? = null,
  val logit_bias: JsonElement? = null,
  val logprobs: Int? = null,
  val best_of: Int? = null,
  val n: Int? = null,
)

@Serializable data class CompletionChoice(
  val text: String,
  val index: Int,
  val logprobs: JsonElement? = null,
  val finish_reason: String,
)

@Serializable data class CompletionResponse(
  val id: String,
  val `object`: String = "text_completion",
  val created: Long,
  val model: String,
  val choices: List<CompletionChoice>,
  val usage: Usage,
  val system_fingerprint: String? = null,
)
