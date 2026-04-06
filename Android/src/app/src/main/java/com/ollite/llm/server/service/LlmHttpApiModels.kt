package com.ollite.llm.server.service

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

@Serializable data class Usage(val prompt_tokens: Int, val completion_tokens: Int)

@Serializable data class ResponsesRequest(
  val model: String? = null,
  val input: List<InputMsg>? = null,
  val messages: List<InputMsg>? = null,
  val stream: Boolean? = null,
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

@Serializable data class ChatRequest(
  val model: String? = null,
  val messages: List<ChatMessage> = emptyList(),
  val stream: Boolean? = null,
  val tools: List<ToolSpec>? = null,
  val tool_choice: String? = null,
)

@Serializable data class ChatChoice(
  val index: Int,
  val message: ChatMessage,
  val finish_reason: String,
)

@Serializable data class ChatResponse(
  val id: String,
  val created: Long,
  val model: String,
  val choices: List<ChatChoice>,
  val usage: Usage,
)

@Serializable data class GenReq(val prompt: String)
