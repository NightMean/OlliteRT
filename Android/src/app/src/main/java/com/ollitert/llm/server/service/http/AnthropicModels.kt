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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire-format data classes for the Anthropic Messages API.
 *
 * The Anthropic shape diverges from OpenAI's Chat Completions in three places that
 * matter for serialization: content blocks are typed sums (text/image/tool_use/...),
 * `tool_choice` is always an object (not a polymorphic string-or-object), and the
 * top-level `system` field can be either a string or an array of typed blocks.
 *
 * To stay forward-compatible with Anthropic's silent additions of new fields, every
 * data class below is decoded with `ignoreUnknownKeys = true` (already configured on
 * the shared Json instance). Computer-use / document / citations blocks reach the
 * converter as raw `AnthropicContentBlock` objects and are rejected explicitly.
 *
 * Response-side data classes are written by [AnthropicConverter.toAnthropicResponse]
 * via JsonObject building rather than via these classes — the SSE event stream needs
 * to interleave serialized block fragments and is easier to express as raw objects.
 * The classes here are kept for the non-streaming JSON response path and tests.
 */

// ── Request shapes ────────────────────────────────────────────────────────────

@Serializable
data class AnthropicMessagesRequest(
  val model: String? = null,
  val messages: List<AnthropicMessage> = emptyList(),
  /** Either a plain string OR an array of [AnthropicTextBlock]-like objects. Validated at converter time. */
  val system: JsonElement? = null,
  val max_tokens: Int? = null,
  val temperature: Double? = null,
  val top_p: Double? = null,
  val top_k: Int? = null,
  val stop_sequences: List<String>? = null,
  val stream: Boolean? = null,
  val tools: List<AnthropicToolDef>? = null,
  /** Object form: `{type:"auto"|"any"|"tool", name?: "..."}` — always an object, not a string. */
  val tool_choice: AnthropicToolChoice? = null,
  val metadata: JsonElement? = null,         // Accepted, ignored
  val service_tier: String? = null,          // Accepted, ignored
  val thinking: AnthropicThinkingConfig? = null,
)

@Serializable
data class AnthropicMessage(
  val role: String,
  /** Either a plain string OR an array of typed content blocks. */
  val content: JsonElement,
)

@Serializable
data class AnthropicToolDef(
  val name: String,
  val description: String? = null,
  /** JSON Schema for the tool's input — passed straight through to the OAI `parameters` field. */
  val input_schema: JsonElement? = null,
  val cache_control: JsonElement? = null,    // Accepted silently, no caching today
)

@Serializable
data class AnthropicToolChoice(
  /** "auto", "any", "tool" — Anthropic does not have a "none" form. */
  val type: String,
  /** Required when `type == "tool"`; ignored otherwise. */
  val name: String? = null,
)

@Serializable
data class AnthropicThinkingConfig(
  /** "enabled" or "disabled". Anything else is treated as null at the converter. */
  val type: String? = null,
  /** Token budget — accepted, ignored (LiteRT does not expose a budget knob). */
  val budget_tokens: Int? = null,
)

// ── Response shapes (non-streaming) ───────────────────────────────────────────

@Serializable
data class AnthropicMessagesResponse(
  val id: String,
  val type: String = "message",
  val role: String = "assistant",
  val model: String,
  val content: List<AnthropicContentBlock>,
  val stop_reason: String,
  val stop_sequence: String? = null,
  val usage: AnthropicUsage,
)

@Serializable
data class AnthropicContentBlock(
  val type: String,
  val text: String? = null,
  val thinking: String? = null,
  val signature: String? = null,
  val id: String? = null,
  val name: String? = null,
  val input: JsonElement? = null,
)

@Serializable
data class AnthropicUsage(
  val input_tokens: Int,
  val output_tokens: Int,
  val cache_creation_input_tokens: Int = 0,
  val cache_read_input_tokens: Int = 0,
)

// ── count_tokens response ─────────────────────────────────────────────────────

@Serializable
data class AnthropicCountTokensResponse(
  val input_tokens: Int,
)

// ── Error shape ───────────────────────────────────────────────────────────────

@Serializable
data class AnthropicErrorEnvelope(
  val type: String = "error",
  val error: AnthropicErrorBody,
)

@Serializable
data class AnthropicErrorBody(
  val type: String,
  val message: String,
)

/**
 * Thrown from [AnthropicConverter] when the input cannot be translated to a valid
 * internal [ChatRequest]. The handler maps this to a 400 Anthropic-shaped error
 * response using [errorType] (e.g. "invalid_request_error") and [message].
 */
class AnthropicConversionError(
  val errorType: String,
  override val message: String,
) : RuntimeException(message)
