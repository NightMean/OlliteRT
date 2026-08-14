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

package com.ollitert.llm.server.service.inference

import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.service.http.*

import android.util.Log
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val TAG = "OlliteRT.SchemaInjectionBridge"

/**
 * Bridge for SDK-based schema injection tool calling.
 *
 * When the "Schema Injection" toggle is ON, tools are registered with the SDK
 * via ToolProvider and the SDK injects schemas into the model context natively
 * (instead of PromptBuilder injecting them as text).
 *
 * This entire file is removable once the SDK's native tool calling is fully
 * stable and the toggle is removed.
 *
 * **Known issue — Gemma-4 native tool calling is unreliable on LiteRT-LM 0.11.0
 * / 0.12.0.** When a tool argument is a string that itself contains quoted
 * content (a Bash command, an Edit `old_string`/`new_string`, a WebFetch URL,
 * any JSON-in-a-string param), Gemma-4 emits its trained `<|"|>` quote
 * delimiter for the inner quotes. The SDK's ANTLR-based function-call parser
 * does not understand `<|"|>` and throws `INVALID_ARGUMENT: Failed to parse
 * FC tool calls`, surfacing as a 500 to the API client. This affects every
 * Anthropic /v1/messages request from Claude Code (Bash/Edit/Read/etc are
 * always sent), every OAI /v1/chat/completions request with similar tools,
 * and every /v1/responses request with tools. Tracking upstream:
 *   https://github.com/google-ai-edge/LiteRT-LM/issues/2418
 *
 * Until the SDK ships a parser fix, callers should be prepared for the
 * surfaced error. Disabling this toggle (set `schema_injection_tool_calling`
 * to false in Settings) routes through PromptBuilder + [ToolCallParser] text
 * mode, which avoids the SDK parser entirely at the cost of less precise
 * tool-call detection.
 */
object SchemaInjectionBridge {

  // Flip to true once the SDK fixes Content.ToolResponse, then delete the
  // workaround path in buildLastUserInput().
  const val NATIVE_TOOL_RESPONSE_WORKS = false

  private val json = Json { ignoreUnknownKeys = true }

  // See the file-level KDoc for the LiteRT-LM #2418 known issue. Until the
  // SDK fixes its FC parser to handle Gemma-4's `<|"|>` quote tokens, every
  // ToolProvider registered here can trigger an INVALID_ARGUMENT error on
  // string args that contain quoted content. Issue:
  //   https://github.com/google-ai-edge/LiteRT-LM/issues/2418
  fun toolSpecsToProviders(specs: List<ToolSpec>): List<ToolProvider> =
    specs.map { spec ->
      val descriptionJson = buildJsonObject {
        put("name", JsonPrimitive(spec.function.name))
        put("description", JsonPrimitive(spec.function.description ?: ""))
        spec.function.parameters?.let { put("parameters", it) }
      }.toString()

      tool(object : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = descriptionJson
        override fun execute(paramsJsonString: String): String {
          throw UnsupportedOperationException("Server does not execute tools")
        }
      })
    }

  fun convertNativeToolCalls(
    nativeCalls: List<com.google.ai.edge.litertlm.ToolCall>,
  ): List<ToolCall> =
    nativeCalls.mapIndexed { index, native ->
      ToolCall(
        id = "call_${System.nanoTime()}_$index",
        type = "function",
        function = ToolCallFunction(
          name = native.name,
          arguments = mapToJsonString(native.arguments),
        ),
      )
    }

  fun buildInitialMessages(msgs: List<ChatMessage>): List<Message> {
    if (msgs.size <= 1) return emptyList()
    return msgs.dropLast(1)
      .filter { msg ->
        // System messages are handled separately via systemInstruction; drop here.
        if (msg.role == "system") return@filter false
        // Drop empty assistant turns. OpenWebUI and Claude Code both insert placeholder
        // assistant entries with empty content + no tool_calls when a prior response was
        // empty or cancelled. Feeding those into LiteRT initialMessages tells the SDK
        // "the model already finished its turn with nothing to say", which conditions
        // the next sample to emit immediate EOS. Verified to produce 0-token completions
        // on otherwise valid prompts when the dead turn was retained.
        if (msg.role == "assistant" &&
            msg.content.text.isBlank() &&
            msg.content.parts.isEmpty() &&
            msg.tool_calls.isNullOrEmpty()) {
          return@filter false
        }
        true
      }
      .mapNotNull { msg ->
        when (msg.role) {
          "user" -> Message.user(msg.content.text)
          "assistant" -> {
            val toolCalls = msg.tool_calls?.map { tc ->
              val argsMap = try {
                json.decodeFromString<JsonObject>(tc.function.arguments)
                  .let { jsonObjectToMap(it) }
              } catch (e: SerializationException) {
                Log.w(TAG, "tool_call.arguments not valid JSON; treating as empty args", e)
                emptyMap()
              }
              com.google.ai.edge.litertlm.ToolCall(name = tc.function.name, arguments = argsMap)
            } ?: emptyList()
            Message.model(Contents.of(msg.content.text.ifEmpty { "" }), toolCalls)
          }
          "tool" -> Message.tool(Contents.of(msg.content.text))
          else -> null
        }
      }
  }

  fun buildLastUserInput(msgs: List<ChatMessage>): String {
    if (msgs.isEmpty()) return ""
    val last = msgs.last()

    if (last.role != "tool" || NATIVE_TOOL_RESPONSE_WORKS) {
      return last.content.text
    }

    val toolResults = mutableListOf<ChatMessage>()
    for (i in msgs.indices.reversed()) {
      if (msgs[i].role == "tool") toolResults.add(0, msgs[i])
      else break
    }

    val assistantMsg = msgs.getOrNull(msgs.size - 1 - toolResults.size)
    val sb = StringBuilder()
    sb.appendLine("After the tool is invoked, it can now answer the user's question.")
    sb.appendLine("Below is the function's return value.")
    for (toolMsg in toolResults) {
      val callId = toolMsg.tool_call_id ?: "unknown"
      val fnName = assistantMsg?.tool_calls
        ?.firstOrNull { it.id == callId }?.function?.name ?: callId
      sb.appendLine("$callId:$fnName: ${toolMsg.content.text}")
    }
    return sb.toString().trim()
  }

  private fun mapToJsonString(map: Map<String, Any?>): String {
    if (map.isEmpty()) return "{}"
    return buildJsonObject {
      for ((key, value) in map) {
        put(key, anyToJsonElement(value))
      }
    }.toString()
  }

  private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> buildJsonObject {
      @Suppress("UNCHECKED_CAST")
      for ((k, v) in value as Map<String, Any?>) {
        put(k, anyToJsonElement(v))
      }
    }
    is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
    else -> JsonPrimitive(value.toString())
  }

  private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> =
    obj.entries.associate { (key, element) -> key to jsonElementToAny(element) }

  private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
    is JsonNull -> null
    is JsonPrimitive -> when {
      element.isString -> element.content
      element.content == "true" || element.content == "false" -> element.content.toBoolean()
      element.content.contains('.') -> element.content.toDoubleOrNull() ?: element.content
      else -> element.content.toLongOrNull() ?: element.content
    }
    is JsonObject -> jsonObjectToMap(element)
    is JsonArray -> element.map { jsonElementToAny(it) }
  }
}
