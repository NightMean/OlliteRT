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

import java.util.Locale

/**
 * Formats transcription text into OpenAI Whisper API response formats.
 *
 * LiteRT returns raw text without word-level timing, so verbose_json returns a
 * single segment. srt/vtt require word-level timing and are not supported.
 */
object TranscriptionFormatter {

  val VALID_FORMATS = setOf("json", "text", "verbose_json")
  val UNSUPPORTED_FORMATS = setOf("srt", "vtt")

  fun toText(text: String): String = text

  fun toJson(text: String): String =
    """{"text":${escapeJsonString(text)}}"""

  fun toVerboseJson(
    text: String,
    language: String?,
    durationSeconds: Double,
  ): String {
    val escapedText = escapeJsonString(text)
    val lang = escapeJsonString(language ?: "")
    val dur = formatDouble(durationSeconds)
    return buildString {
      append("""{"task":"transcribe","language":""")
      append(lang)
      append(""","duration":""")
      append(dur)
      append(""","text":""")
      append(escapedText)
      append(""","segments":[{"id":0,"seek":0,"start":0.0,"end":""")
      append(dur)
      append(""","text":""")
      append(escapedText)
      append("""}]}""")
    }
  }

  private fun formatDouble(value: Double): String {
    val formatted = String.format(Locale.US, "%.3f", value)
    return formatted.trimEnd('0').let { if (it.endsWith('.')) "${it}0" else it }
  }

  private fun escapeJsonString(value: String): String {
    val sb = StringBuilder(value.length + 2)
    sb.append('"')
    for (ch in value) {
      when (ch) {
        '"' -> sb.append("\\\"")
        '\\' -> sb.append("\\\\")
        '\n' -> sb.append("\\n")
        '\r' -> sb.append("\\r")
        '\t' -> sb.append("\\t")
        '\b' -> sb.append("\\b")
        '' -> sb.append("\\f")
        else -> {
          if (ch.code < 0x20) {
            sb.append("\\u")
            sb.append(String.format(Locale.US, "%04x", ch.code))
          } else {
            sb.append(ch)
          }
        }
      }
    }
    sb.append('"')
    return sb.toString()
  }
}
