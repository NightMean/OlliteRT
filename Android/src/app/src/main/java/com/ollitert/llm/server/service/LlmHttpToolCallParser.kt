package com.ollitert.llm.server.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses tool/function call patterns from raw model text output.
 *
 * When tools are injected into the prompt, the model may respond with a JSON
 * tool call instead of plain text. This parser attempts to detect and extract
 * such calls. If no tool call pattern is found, returns null (treat output as
 * regular text).
 *
 * Recognized patterns (in priority order):
 * 1. `{"tool_call": {"name": "...", "arguments": {...}}}`
 * 2. `<tool_call>{"name": "...", "arguments": {...}}</tool_call>`
 * 3. `<|tool_call>call:FunctionName{...}<tool_call|>` — native Gemma/LiteRT format
 * 4. `{"name": "...", "arguments": {...}}` — bare function call (validated against tool list)
 * 5. `{"function": {"name": "...", "arguments": {...}}}` — alternative wrapper
 */
object LlmHttpToolCallParser {

  private val json = Json { ignoreUnknownKeys = true }

  /** Regex to extract JSON between `<tool_call>` XML tags.
   *  Note: `\}` must be escaped — Android's ICU regex engine (unlike standard Java) treats
   *  unescaped `}` as a syntax error (PatternSyntaxException). */
  private val xmlToolCallRegex = Regex("""<tool_call>\s*(\{.*?\})\s*</tool_call>""", RegexOption.DOT_MATCHES_ALL)

  /** Regex for native Gemma/LiteRT tool call format: `<|tool_call>call:FunctionName{args}<tool_call|>`
   *  Gemma models trained with native tool calling emit this format instead of JSON wrappers.
   *  Group 1 = function name, Group 2 = JSON arguments (may be empty `{}`).
   *  Note: `\|` escapes the pipe, `\}` escapes the brace for ICU regex. */
  private val nativeToolCallRegex = Regex("""<\|tool_call>call:(\w+)(\{.*?\})<tool_call\|>""", RegexOption.DOT_MATCHES_ALL)

  /**
   * Attempts to parse a tool call from the model's raw text output.
   * Returns null if no tool call pattern is detected or the function name
   * doesn't match any of the available tools.
   */
  fun parse(output: String, availableTools: List<ToolSpec>): ToolCall? {
    val trimmed = output.trim()
    val toolNames = availableTools.map { it.function.name }.toSet()

    // Try each pattern in priority order
    return tryToolCallWrapper(trimmed, toolNames)
      ?: tryXmlWrapped(trimmed, toolNames)
      ?: tryNativeGemmaCall(trimmed, toolNames)
      ?: tryFunctionWrapper(trimmed, toolNames)
      ?: tryBareCall(trimmed, toolNames)
  }

  /** Pattern 1: `{"tool_call": {"name": "fn", "arguments": {...}}}` */
  private fun tryToolCallWrapper(text: String, toolNames: Set<String>): ToolCall? {
    val jsonStr = extractFirstJsonObject(text) ?: return null
    val obj = parseJsonObjectSafe(jsonStr) ?: return null
    val inner = obj["tool_call"]?.takeIf { it is JsonObject }?.jsonObject ?: return null
    return extractCall(inner, toolNames)
  }

  /** Pattern 2: `<tool_call>{"name": "fn", "arguments": {...}}</tool_call>` */
  private fun tryXmlWrapped(text: String, toolNames: Set<String>): ToolCall? {
    val match = xmlToolCallRegex.find(text) ?: return null
    val innerJson = match.groupValues[1]
    val obj = parseJsonObjectSafe(innerJson) ?: return null
    return extractCall(obj, toolNames)
  }

  /** Pattern 3: `<|tool_call>call:FunctionName{args}<tool_call|>` — native Gemma/LiteRT format */
  private fun tryNativeGemmaCall(text: String, toolNames: Set<String>): ToolCall? {
    val match = nativeToolCallRegex.find(text) ?: return null
    val name = match.groupValues[1]
    if (name !in toolNames) return null
    val argsStr = match.groupValues[2]
    // Validate the args JSON is parseable (or accept empty {})
    if (argsStr != "{}") {
      parseJsonObjectSafe(argsStr) ?: return null
    }
    return ToolCall(
      id = "call_${java.util.UUID.randomUUID().toString().replace("-", "").take(24)}",
      function = ToolCallFunction(name = name, arguments = argsStr),
    )
  }

  /** Pattern 5: `{"function": {"name": "fn", "arguments": {...}}}` */
  private fun tryFunctionWrapper(text: String, toolNames: Set<String>): ToolCall? {
    val jsonStr = extractFirstJsonObject(text) ?: return null
    val obj = parseJsonObjectSafe(jsonStr) ?: return null
    val inner = obj["function"]?.takeIf { it is JsonObject }?.jsonObject ?: return null
    return extractCall(inner, toolNames)
  }

  /** Pattern 4 (renumbered): `{"name": "fn", "arguments": {...}}` — bare call, validated against tool list */
  private fun tryBareCall(text: String, toolNames: Set<String>): ToolCall? {
    val jsonStr = extractFirstJsonObject(text) ?: return null
    val obj = parseJsonObjectSafe(jsonStr) ?: return null
    // Must have both "name" and "arguments" to be treated as a tool call
    if ("name" !in obj || "arguments" !in obj) return null
    return extractCall(obj, toolNames)
  }

  /**
   * Extracts name + arguments from a JSON object and validates the function
   * name against the available tool list.
   */
  private fun extractCall(obj: JsonObject, toolNames: Set<String>): ToolCall? {
    val name = obj["name"]?.jsonPrimitive?.content ?: return null
    if (name !in toolNames) return null
    val arguments = obj["arguments"] ?: return null
    val argsStr = if (arguments is JsonObject) json.encodeToString(JsonObject.serializer(), arguments)
    else arguments.toString()
    return ToolCall(
      id = "call_${java.util.UUID.randomUUID().toString().replace("-", "").take(24)}",
      function = ToolCallFunction(name = name, arguments = argsStr),
    )
  }

  /**
   * Finds the first balanced JSON object `{...}` in the text.
   * Handles nested braces correctly.
   */
  private fun extractFirstJsonObject(text: String): String? {
    val start = text.indexOf('{')
    if (start == -1) return null
    var depth = 0
    var inString = false
    var escape = false
    for (i in start until text.length) {
      val c = text[i]
      if (escape) { escape = false; continue }
      if (c == '\\' && inString) { escape = true; continue }
      if (c == '"') { inString = !inString; continue }
      if (inString) continue
      when (c) {
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) return text.substring(start, i + 1)
        }
      }
    }
    return null // Unbalanced braces
  }

  private fun parseJsonObjectSafe(str: String): JsonObject? =
    try { json.parseToJsonElement(str).jsonObject } catch (_: Exception) { null }
}
