package com.ollitert.llm.server.service

data class LlmHttpParsedBody(
  val body: String,
  val bodyLength: Int,
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
    return LlmHttpParsedBody(body = body, bodyLength = body.length)
  }
}
