package com.ollite.llm.server.service

import java.nio.charset.StandardCharsets

data class LlmHttpParsedBody(
  val body: String,
  val bodyBytes: Int,
)

object LlmHttpBodyParser {
  fun parse(postData: String?, maxBodyBytes: Int): LlmHttpParsedBody? {
    val body = postData ?: return null
    val bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size
    if (bodyBytes > maxBodyBytes) {
      return null
    }
    return LlmHttpParsedBody(body = body, bodyBytes = bodyBytes)
  }

  fun bodySizeBytes(body: String): Int {
    return body.toByteArray(StandardCharsets.UTF_8).size
  }
}
