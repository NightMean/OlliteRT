package com.ollitert.llm.server.service

import java.nio.charset.StandardCharsets

data class LlmHttpParsedBody(
  val body: String,
  val bodyBytes: Int,
)

/**
 * Parses and measures HTTP request bodies.
 *
 * No body size limit is enforced — multimodal requests carry base64-encoded images
 * that routinely exceed 1 MB. The original 512 KB limit from Google's reference
 * Gallery app was designed for text-only requests and blocked legitimate vision payloads.
 */
object LlmHttpBodyParser {
  fun parse(postData: String?): LlmHttpParsedBody? {
    val body = postData ?: return null
    val bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size
    return LlmHttpParsedBody(body = body, bodyBytes = bodyBytes)
  }

  fun bodySizeBytes(body: String): Int {
    return body.toByteArray(StandardCharsets.UTF_8).size
  }
}
