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

import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.data.BASE64_COMPACT_THRESHOLD_CHARS

object BridgeUtils {
  private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]")

  fun normalizeModelKey(value: String): String =
    value.lowercase().replace(NON_ALPHANUMERIC_REGEX, "")

  fun resolveRequestedModelId(requested: String?): String {
    if (requested.isNullOrBlank()) return "local"
    return requested.trim()
  }

  fun isBearerAuthorized(expectedToken: String, authorizationHeader: String?): Boolean {
    if (expectedToken.isBlank()) return true
    // Constant-time comparison prevents timing attacks that could reveal the token byte-by-byte.
    val expected = "Bearer $expectedToken".toByteArray(Charsets.UTF_8)
    val actual = (authorizationHeader ?: "").toByteArray(Charsets.UTF_8)
    return java.security.MessageDigest.isEqual(expected, actual)
  }

  fun escapeSseText(value: String): String = buildString(value.length) {
    for (ch in value) {
      when (ch) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        '\b' -> append("\\b")
        '' -> append("\\f")
        else -> if (ch.code in 0x00..0x1F) {
          append("\\u")
          append(ch.code.toString(16).padStart(4, '0'))
        } else {
          append(ch)
        }
      }
    }
  }

  // ── ID generation ──────────────────────────────────────────────────────
  // OpenAI-compatible IDs use specific prefixes per object type.

  fun generateCompletionId(): String = "cmpl-${java.util.UUID.randomUUID()}"
  fun generateChatCompletionId(): String = "chatcmpl-${java.util.UUID.randomUUID()}"
  fun generateResponseId(): String = "resp-${java.util.UUID.randomUUID()}"
  fun generateMessageId(): String = "msg-${java.util.UUID.randomUUID()}"
  fun generateToolCallId(): String = "call_${java.util.UUID.randomUUID().toString().replace("-", "").take(24)}"
  fun generateFunctionCallId(): String = "fc-${java.util.UUID.randomUUID()}"
  fun generateBearerToken(): String = java.util.UUID.randomUUID().toString().replace("-", "")
  fun epochSeconds(): Long = System.currentTimeMillis() / 1000

  // ── Compact Image Data ──────────────────────────────────────────────────

  private const val MARKER = ";base64,"

  /**
   * Replaces long base64 data URIs with a placeholder showing the MIME type and decoded size.
   *
   * Uses manual string scanning instead of regex because Android's java.util.regex engine
   * stack-overflows on quantifiers matching 100K+ characters (typical for image payloads).
   *
   * Example:
   * ```
   * "data:image/png;base64,iVBORw0KGgo..." (5 MB of base64)
   * → "data:image/png;base64,▌ PLACEHOLDER — 3.2 MB image data ▌"
   * ```
   */
  fun compactBase64DataUris(body: String): String {
    if (!body.contains(MARKER)) return body

    val sb = StringBuilder(body.length / 4)
    var cursor = 0

    while (cursor < body.length) {
      val markerIdx = body.indexOf(MARKER, cursor)
      if (markerIdx < 0) {
        sb.append(body, cursor, body.length)
        break
      }

      // Walk backward from marker to find "data:" prefix and extract MIME type
      val dataIdx = body.lastIndexOf("data:", markerIdx)
      if (dataIdx < 0 || dataIdx < cursor) {
        // No valid data: prefix — copy up to and including marker, continue
        sb.append(body, cursor, markerIdx + MARKER.length)
        cursor = markerIdx + MARKER.length
        continue
      }

      // Extract MIME type between "data:" and ";base64,"
      val mimeType = body.substring(dataIdx + 5, markerIdx)
      if (mimeType.contains('"') || mimeType.length > 50) {
        // Invalid MIME (crossed a JSON boundary) — skip
        sb.append(body, cursor, markerIdx + MARKER.length)
        cursor = markerIdx + MARKER.length
        continue
      }

      // Scan forward to find end of base64 payload
      val payloadStart = markerIdx + MARKER.length
      var payloadEnd = payloadStart
      while (payloadEnd < body.length) {
        val ch = body[payloadEnd]
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '+' || ch == '/' || ch == '=' || ch == '\\') {
          payloadEnd++
        } else {
          break
        }
      }

      val payloadLen = payloadEnd - payloadStart
      if (payloadLen < BASE64_COMPACT_THRESHOLD_CHARS) {
        // Below threshold — copy as-is
        sb.append(body, cursor, payloadEnd)
        cursor = payloadEnd
        continue
      }

      // Replace with placeholder
      val base64Chars = body.substring(payloadStart, payloadEnd).count { it != '=' && it != '\\' }
      val decodedBytes = (base64Chars * 3L) / 4L
      val sizeLabel = decodedBytes.humanReadableSize()
      val cleanMime = mimeType.replace("\\/", "/")
      val category = cleanMime.substringBefore('/')

      sb.append(body, cursor, dataIdx)
      sb.append("data:$cleanMime;base64,▌ PLACEHOLDER — $sizeLabel $category data ▌")
      cursor = payloadEnd
    }

    return sb.toString()
  }

}
