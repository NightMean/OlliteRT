package com.ollite.llm.server.service

object LlmHttpBridgeUtils {
  fun normalizeModelKey(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9]"), "")

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
