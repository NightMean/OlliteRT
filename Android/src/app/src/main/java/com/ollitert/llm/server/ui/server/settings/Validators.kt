package com.ollitert.llm.server.ui.server.settings

/**
 * Validates CORS allowed origins input.
 * Valid formats: blank (disabled), "*" (allow all), or comma-separated origin URLs
 * with http:// or https:// scheme.
 */
fun isValidCorsOrigins(input: String): Boolean {
  val trimmed = input.trim()
  if (trimmed.isEmpty() || trimmed == "*") return true
  return trimmed.split(",").all { entry ->
    val origin = entry.trim()
    origin.isNotEmpty() && (origin.startsWith("http://") || origin.startsWith("https://")) &&
      origin.substringAfter("://").let { host ->
        host.isNotEmpty() && !host.startsWith("/") && !host.contains(" ")
      }
  }
}
