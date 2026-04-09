package com.ollitert.llm.server.service

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
    return authorizationHeader == "Bearer $expectedToken"
  }

  fun escapeSseText(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
