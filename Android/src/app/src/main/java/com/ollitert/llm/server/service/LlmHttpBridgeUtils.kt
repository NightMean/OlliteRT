package com.ollitert.llm.server.service

import com.ollitert.llm.server.data.BASE64_COMPACT_THRESHOLD_CHARS
import com.ollitert.llm.server.ui.common.formatByteSize

object LlmHttpBridgeUtils {
  private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]")

  fun normalizeModelKey(value: String): String =
    value.lowercase().replace(NON_ALPHANUMERIC_REGEX, "")

  fun resolveRequestedModelId(requested: String?): String {
    if (requested.isNullOrBlank()) return "local"
    return requested.trim()
  }

  fun isBearerAuthorized(expectedToken: String, authorizationHeader: String?): Boolean {
    if (expectedToken.isBlank()) return true
    val expected = "Bearer $expectedToken".toByteArray(Charsets.UTF_8)
    val actual = (authorizationHeader ?: "").toByteArray(Charsets.UTF_8)
    return java.security.MessageDigest.isEqual(expected, actual)
  }

  fun escapeSseText(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

  // ── ID generation ──────────────────────────────────────────────────────
  // OpenAI-compatible IDs use specific prefixes per object type.

  fun generateCompletionId(): String = "cmpl-${java.util.UUID.randomUUID()}"
  fun generateChatCompletionId(): String = "chatcmpl-${java.util.UUID.randomUUID()}"
  fun generateResponseId(): String = "resp-${java.util.UUID.randomUUID()}"
  fun generateMessageId(): String = "msg-${java.util.UUID.randomUUID()}"
  fun generateFunctionCallId(): String = "fc-${java.util.UUID.randomUUID()}"
  fun generateToolCallId(): String = "call_${java.util.UUID.randomUUID().toString().replace("-", "").take(24)}"
  fun generateBearerToken(): String = java.util.UUID.randomUUID().toString().replace("-", "")
  fun epochSeconds(): Long = System.currentTimeMillis() / 1000

  // ── Compact Image Data ──────────────────────────────────────────────────
  // Replaces inline base64 data URIs (e.g. from multimodal image requests) with a
  // human-readable size placeholder. A 4K image encodes to ~5-10 MB of base64 text,
  // which causes UI freezes when Compose tries to measure/render it in the Logs tab.
  // The placeholder preserves the MIME type prefix and shows the original data size.

  /**
   * Matches `data:<mime>;base64,<payload>` where the base64 payload is at least 1 KB
   * (1365 base64 chars ≈ 1024 bytes). Shorter payloads (thumbnails, icons) are left as-is
   * since they don't cause rendering issues.
   *
   * The character class includes `\` to handle JSON-escaped slashes (`\/`) — some JSON
   * serializers escape `/` as `\/` per RFC 8259 §7, so base64 payloads in JSON strings
   * may contain `\/` instead of `/`. The backslash chars are excluded from the byte size
   * calculation since they're JSON escaping, not actual base64 data.
   */
  private val BASE64_DATA_URI_REGEX = Regex(
    """data:([^;]+);base64,([A-Za-z0-9+/=\\]{$BASE64_COMPACT_THRESHOLD_CHARS,})"""
  )

  /**
   * Replaces long base64 data URIs with a placeholder showing the MIME type and decoded size.
   *
   * Example:
   * ```
   * "data:image/png;base64,iVBORw0KGgo..." (5 MB of base64)
   * → "data:image/png;base64,▌ PLACEHOLDER — 3.2 MB image data ▌"
   * ```
   */
  fun compactBase64DataUris(body: String): String =
    BASE64_DATA_URI_REGEX.replace(body) { match ->
      val mimeType = match.groupValues[1]       // e.g. "image/png" or "image\/png"
      val base64Payload = match.groupValues[2]
      // Base64 encodes 3 bytes per 4 characters (padding '=' chars don't add data).
      // Exclude backslash chars from count — they're JSON escape characters, not base64 data.
      val base64Chars = base64Payload.count { it != '=' && it != '\\' }
      val decodedBytes = (base64Chars * 3L) / 4L
      val sizeLabel = formatByteSize(decodedBytes)
      // Extract the media category from the MIME type (e.g. "image" from "image/png").
      // Strip JSON-escaped slashes for clean display.
      val cleanMime = mimeType.replace("\\/", "/")
      val category = cleanMime.substringBefore('/')
      "data:$cleanMime;base64,▌ PLACEHOLDER — $sizeLabel $category data ▌"
    }

}
