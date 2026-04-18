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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.json.JSONArray
import org.json.JSONObject

/** Attempt to parse text as JSON (object or array). Returns the original string on failure. */
internal fun tryParseJson(text: String): Any {
  val trimmed = text.trim()
  return try {
    if (trimmed.startsWith("{")) JSONObject(trimmed)
    else if (trimmed.startsWith("[")) JSONArray(trimmed)
    else text
  } catch (_: Exception) {
    text
  }
}

// JSON syntax highlighting colors
internal val JsonKeyColor = Color(0xFF82AAFF)      // blue — object keys
internal val JsonStringColor = Color(0xFFC3E88D)   // green — string values
internal val JsonNumberColor = Color(0xFFF78C6C)   // orange — numbers
internal val JsonBoolNullColor = Color(0xFFFF5370)  // red/pink — true, false, null
internal val JsonBraceColor = Color(0xFF89DDFF)     // cyan — brackets, braces, colons, commas

internal val jsonTokenRegex = Regex(
  """("(?:[^"\\]|\\.)*")\s*:|("(?:[^"\\]|\\.)*")|([-+]?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|(\btrue\b|\bfalse\b|\bnull\b)|([{}\[\]:,])""",
)

internal fun highlightJson(text: String): AnnotatedString = buildAnnotatedString {
  val fallbackColor = Color(0xFFBDBDBD) // light grey for non-JSON / whitespace
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

/** Try to pretty-print JSON; return the original string if it's not valid JSON. */
internal fun prettyPrintJson(raw: String): String = try {
  val trimmed = raw.trimStart()
  if (trimmed.startsWith("{")) {
    JSONObject(trimmed).toString(2)
  } else if (trimmed.startsWith("[")) {
    JSONArray(trimmed).toString(2)
  } else {
    raw
  }
} catch (_: Exception) {
  raw
}
