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

package com.ollitert.llm.server.ui.server.logs

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.ollitert.llm.server.ui.theme.OlliteRTJsonBoolNull
import com.ollitert.llm.server.ui.theme.OlliteRTJsonBrace
import com.ollitert.llm.server.ui.theme.OlliteRTJsonKey
import com.ollitert.llm.server.ui.theme.OlliteRTJsonNumber
import com.ollitert.llm.server.ui.theme.OlliteRTJsonString
import com.ollitert.llm.server.ui.theme.OlliteRTSubtleGrey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Attempt to parse text as JSON. Returns a JsonPrimitive of the original string on failure. */
internal fun tryParseJson(text: String): JsonElement {
  val trimmed = text.trim()
  return try {
    Json.parseToJsonElement(trimmed)
  } catch (_: Exception) {
    JsonPrimitive(text)
  }
}

internal val JsonKeyColor = OlliteRTJsonKey
internal val JsonStringColor = OlliteRTJsonString
internal val JsonNumberColor = OlliteRTJsonNumber
internal val JsonBoolNullColor = OlliteRTJsonBoolNull
internal val JsonBraceColor = OlliteRTJsonBrace

internal val jsonTokenRegex = Regex(
  """("(?:[^"\\]|\\.)*")\s*:|("(?:[^"\\]|\\.)*")|([-+]?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|(\btrue\b|\bfalse\b|\bnull\b)|([{}\[\]:,])""",
)

internal fun highlightJson(text: String): AnnotatedString = buildAnnotatedString {
  val fallbackColor = OlliteRTSubtleGrey
  var lastIndex = 0
  for (match in jsonTokenRegex.findAll(text)) {
    // Append any text between tokens (whitespace, newlines) in fallback color
    if (match.range.first > lastIndex) {
      withStyle(SpanStyle(color = fallbackColor)) {
        append(text.substring(lastIndex, match.range.first))
      }
    }
    val (key, string, number, boolNull, brace) = match.destructured
    when {
      key.isNotEmpty() -> {
        withStyle(SpanStyle(color = JsonKeyColor)) { append(key) }
        // Append the colon (and any whitespace) that follows the key
        val keyEnd = match.groupValues[1].let { match.range.first + it.length }
        val trailing = text.substring(keyEnd, match.range.last + 1) // e.g. ": "
        withStyle(SpanStyle(color = JsonBraceColor)) { append(trailing) }
      }
      string.isNotEmpty() -> withStyle(SpanStyle(color = JsonStringColor)) { append(string) }
      number.isNotEmpty() -> withStyle(SpanStyle(color = JsonNumberColor)) { append(number) }
      boolNull.isNotEmpty() -> withStyle(SpanStyle(color = JsonBoolNullColor, fontWeight = FontWeight.SemiBold)) { append(boolNull) }
      brace.isNotEmpty() -> withStyle(SpanStyle(color = JsonBraceColor)) { append(brace) }
    }
    lastIndex = match.range.last + 1
  }
  // Append any trailing text
  if (lastIndex < text.length) {
    withStyle(SpanStyle(color = fallbackColor)) {
      append(text.substring(lastIndex))
    }
  }
}

private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

/** Try to pretty-print JSON; return the original string if it's not valid JSON. */
internal fun prettyPrintJson(raw: String): String = try {
  val element = Json.parseToJsonElement(raw.trimStart())
  prettyJson.encodeToString(JsonElement.serializer(), element)
} catch (_: Exception) {
  raw
}
