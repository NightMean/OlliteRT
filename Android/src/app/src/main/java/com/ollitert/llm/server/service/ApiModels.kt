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

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ── Model list data classes (used by ResponseRenderer and PayloadBuilders) ──

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
  val created: Long = BridgeUtils.epochSeconds(),
  val owned_by: String = "ollitert",
  val capabilities: LlmHttpModelCapabilities = LlmHttpModelCapabilities(),
  val update_available: Boolean = false,
)

@Serializable
data class LlmHttpModelList(val `object`: String = "list", val data: List<LlmHttpModelItem>)

/**
 * Token usage reported in API responses.
 *
 * **LiteRT SDK limitation:** The LiteRT LM SDK (litertlm) does not expose a standalone tokenizer
 * API — tokenization happens inside the native inference call and the token count is not returned.
 * All token counts are **estimated** using `charLength / 4`, which is reasonably accurate for
 * English text (~3.5–4 chars/token for Gemma/GPT tokenizers) but drifts for code (~2.5 chars/token)
 * and multilingual text. There is no `countTokens()` method available. Bundling a separate
 * SentencePiece tokenizer per model family would add binary size and complexity.
 * Monitor future LiteRT releases for a tokenizer API.
 */
@Serializable data class Usage(
  val prompt_tokens: Int,
  val completion_tokens: Int,
  val total_tokens: Int = prompt_tokens + completion_tokens,
)

/**
 * Performance timings included in API responses as a non-standard `timings` field.
 *
 * **Not part of the OpenAI API spec.** This is a widely adopted extension used by local LLM
 * servers and clients. Open WebUI, for example, reads these fields and displays them as
 * per-message performance stats in its chat UI.
 *
 * Field names intentionally match the common convention used by popular local LLM tooling
 * (e.g. llama.cpp, Ollama) so that compatible clients work out of the box.
 *
 * Token counts are estimates (charLen / 4) — see [Usage] doc for LiteRT SDK limitations.
 */
@Serializable data class InferenceTimings(
  val prompt_n: Int,              // Number of prompt (input) tokens
  val prompt_ms: Double,          // Time spent processing prompt (ms) — approximated by TTFB
  val prompt_per_token_ms: Double, // Average ms per prompt token
  val prompt_per_second: Double,  // Prompt tokens processed per second
  val predicted_n: Int,           // Number of predicted (output) tokens
  val predicted_ms: Double,       // Time spent generating output (ms)
  val predicted_per_token_ms: Double, // Average ms per output token (inter-token latency)
  val predicted_per_second: Double, // Output tokens generated per second (decode speed)
)

@Serializable data class ResponsesRequest(
  val model: String? = null,
  @Serializable(with = InputListSerializer::class)
  val input: List<InputMsg>? = null,
  val messages: List<InputMsg>? = null,
  val stream: Boolean? = null,
  val temperature: Double? = null,
  val top_p: Double? = null,
  val top_k: Int? = null,
  val max_output_tokens: Int? = null,      // Responses API uses max_output_tokens
  val tools: List<ToolSpec>? = null,
  val tool_choice: JsonElement? = null,    // String or Object, same as ChatRequest
)

@Serializable data class InputMsg(
  val role: String,
  @Serializable(with = InputContentListSerializer::class)
  val content: List<InputContent>,
)

@Serializable data class InputContent(
  val type: String,
  val text: String,
)

/**
 * Token usage for Responses API — uses `input_tokens`/`output_tokens` per spec,
 * unlike Chat Completions API which uses `prompt_tokens`/`completion_tokens`.
 */
@Serializable data class ResponsesUsage(
  val input_tokens: Int,
  val output_tokens: Int,
  val total_tokens: Int = input_tokens + output_tokens,
)

@Serializable data class ResponsesResponse(
  val id: String,
  val `object`: String = "response",
  @SerialName("created_at") val created: Long,
  val model: String,
  val output: List<RespOutputItem>,
  val usage: ResponsesUsage,
  val status: String = "completed",
)

@Serializable
sealed interface RespOutputItem

@Serializable @SerialName("message")
data class RespMessage(
  val id: String = BridgeUtils.generateMessageId(),
  val role: String = "assistant",
  val content: List<RespContent>,
  val status: String = "completed",
) : RespOutputItem

@Serializable @SerialName("function_call")
data class RespFunctionCall(
  val id: String = BridgeUtils.generateFunctionCallId(),
  val call_id: String,
  val name: String,
  val arguments: String,
  val status: String = "completed",
) : RespOutputItem

@Serializable data class RespContent(
  val type: String = "text",
  val text: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable data class GenRes(
  val text: String,
  val usage: Usage,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val timings: InferenceTimings? = null,
)

/**
 * Represents the content of a multimodal message part.
 */
@Serializable data class ContentPart(
  val type: String, // "text", "image_url", or "input_audio"
  val text: String? = null,
  val image_url: ImageUrl? = null,
  val input_audio: InputAudio? = null,
)

@Serializable data class ImageUrl(val url: String)

/**
 * Audio payload for `"input_audio"` content parts.
 *
 * [data] is the base64-encoded audio bytes. [format] is an optional hint (e.g. "wav", "mp3");
 * clients may omit it, so it defaults to null.
 */
@Serializable data class InputAudio(
  val data: String,
  val format: String? = null,
)

/**
 * Holds parsed message content that may be a plain string or an array of multimodal parts.
 */
data class ChatContent(
  val text: String,
  val parts: List<ContentPart> = emptyList(),
)

/**
 * Custom serializer for the Responses API `input` field which can be a plain string
 * or an array of [InputMsg] objects. When a string is received, it is wrapped as a
 * single user message: `[InputMsg(role = "user", content = [InputContent(type = "text", text = ...)])]`.
 */
object InputListSerializer : KSerializer<List<InputMsg>?> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InputList")

  override fun deserialize(decoder: Decoder): List<InputMsg>? {
    val jsonDecoder = decoder as JsonDecoder
    return when (val element = jsonDecoder.decodeJsonElement()) {
      is JsonNull -> null
      is JsonPrimitive -> listOf(
        InputMsg(role = "user", content = listOf(InputContent(type = "text", text = element.content)))
      )
      is JsonArray -> jsonDecoder.json.decodeFromJsonElement(
        kotlinx.serialization.builtins.ListSerializer(InputMsg.serializer()), element
      )
      else -> null
    }
  }

  override fun serialize(encoder: Encoder, value: List<InputMsg>?) {
    val jsonEncoder = encoder as JsonEncoder
    if (value == null) {
      jsonEncoder.encodeJsonElement(JsonNull)
    } else {
      jsonEncoder.encodeSerializableValue(
        kotlinx.serialization.builtins.ListSerializer(InputMsg.serializer()), value
      )
    }
  }
}

/**
 * Custom serializer for [InputMsg.content] which can be a plain string or an array
 * of [InputContent] objects. When a string is received, it is wrapped as
 * `[InputContent(type = "text", text = ...)]`.
 */
object InputContentListSerializer : KSerializer<List<InputContent>> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InputContentList")

  override fun deserialize(decoder: Decoder): List<InputContent> {
    val jsonDecoder = decoder as JsonDecoder
    return when (val element = jsonDecoder.decodeJsonElement()) {
      is JsonNull -> emptyList()
      is JsonPrimitive -> listOf(InputContent(type = "text", text = element.content))
      is JsonArray -> jsonDecoder.json.decodeFromJsonElement(
        kotlinx.serialization.builtins.ListSerializer(InputContent.serializer()), element
      )
      else -> emptyList()
    }
  }

  override fun serialize(encoder: Encoder, value: List<InputContent>) {
    val jsonEncoder = encoder as JsonEncoder
    jsonEncoder.encodeSerializableValue(
      kotlinx.serialization.builtins.ListSerializer(InputContent.serializer()), value
    )
  }
}

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
      is JsonNull -> ChatContent(text = "")
      is JsonPrimitive -> ChatContent(text = element.content)
      is JsonArray -> {
        val parts = element.jsonArray.map { partElement ->
          val obj = partElement.jsonObject
          val type = obj["type"]?.jsonPrimitive?.content ?: "text"
          val text = obj["text"]?.jsonPrimitive?.content
          val imageUrl = obj["image_url"]?.jsonObject?.let { imgObj ->
            ImageUrl(url = imgObj["url"]?.jsonPrimitive?.content ?: "")
          }
          // JsonNull is a subtype of JsonPrimitive, so check for JsonNull first before
          // calling .jsonObject — accessing .jsonObject on a JsonNull throws at runtime.
          val audioElement = obj["input_audio"]
          val inputAudio = if (audioElement != null && audioElement !is JsonNull) {
            audioElement.jsonObject.let { audioObj ->
              InputAudio(
                data = audioObj["data"]?.jsonPrimitive?.content ?: "",
                format = audioObj["format"]?.let { if (it is JsonNull) null else it.jsonPrimitive.content },
              )
            }
          } else {
            null
          }
          ContentPart(type = type, text = text, image_url = imageUrl, input_audio = inputAudio)
        }
        val combinedText = parts.filter { it.type == "text" }.mapNotNull { it.text }.joinToString(" ")
        ChatContent(text = combinedText, parts = parts)
      }
      else -> ChatContent(text = "")
    }
  }

  override fun serialize(encoder: Encoder, value: ChatContent) {
    val jsonEncoder = encoder as JsonEncoder
    if (value.text.isEmpty() && value.parts.isEmpty()) {
      jsonEncoder.encodeJsonElement(JsonNull)
    } else {
      jsonEncoder.encodeJsonElement(JsonPrimitive(value.text))
    }
  }
}

@Serializable data class ChatMessage(
  val role: String,
  @Serializable(with = ChatContentSerializer::class)
  val content: ChatContent = ChatContent(""),
  val tool_calls: List<ToolCall>? = null,
  val tool_call_id: String? = null,   // For role="tool" messages: references the tool call this result is for
  val name: String? = null,           // Function name for role="tool" or deprecated role="function" messages
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
      is JsonNull -> emptyList()
      is JsonPrimitive -> if (element.content.isNotBlank()) listOf(element.content) else emptyList()
      is JsonArray -> element.mapNotNull { el -> if (el is JsonNull) null else el.jsonPrimitive.content.takeIf { it.isNotBlank() } }
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
  val tool_choice: JsonElement? = null,    // String ("auto"/"none"/"required") or Object {"type":"function","function":{"name":"..."}}
  val parallel_tool_calls: Boolean? = null, // Accepted, ignored (sequential inference only)
  val user: String? = null,                // Accepted, ignored
  val n: Int? = null,                      // Rejected if > 1 (parallel completions unsupported)
  val logprobs: Boolean? = null,           // Accepted, ignored
  val top_logprobs: Int? = null,           // Accepted, ignored
)

@Serializable data class ChatChoice(
  val index: Int,
  val message: ChatMessage,
  val logprobs: JsonElement? = null,
  val finish_reason: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable data class ChatResponse(
  val id: String,
  val `object`: String = "chat.completion",
  val created: Long,
  val model: String,
  val choices: List<ChatChoice>,
  val usage: Usage,
  val system_fingerprint: String? = null,
  // Non-standard performance timings — see InferenceTimings doc.
  // @EncodeDefault(NEVER) prevents serializing as "timings":null when not set,
  // since our Json instance uses encodeDefaults = true.
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val timings: InferenceTimings? = null,
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
  val best_of: Int? = null,                // Rejected if > 1 (unsupported)
  val n: Int? = null,                      // Rejected if > 1 (parallel completions unsupported)
)

@Serializable data class CompletionChoice(
  val text: String,
  val index: Int,
  val logprobs: JsonElement? = null,
  val finish_reason: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable data class CompletionResponse(
  val id: String,
  val `object`: String = "text_completion",
  val created: Long,
  val model: String,
  val choices: List<CompletionChoice>,
  val usage: Usage,
  val system_fingerprint: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val timings: InferenceTimings? = null,
)
