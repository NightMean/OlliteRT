package com.ollitert.llm.server.service

/**
 * CORS (Cross-Origin Resource Sharing) helper for the HTTP server.
 *
 * Computes the correct CORS response headers based on the configured allowed origins
 * and the request's Origin header. Supports three modes:
 * - Wildcard ("*"): All origins allowed — returns Access-Control-Allow-Origin: *
 * - Specific origins (comma-separated): Only matching origins get CORS headers,
 *   with Vary: Origin to ensure correct caching per-origin.
 * - Disabled (blank/empty): No CORS headers added — browser same-origin policy applies.
 */
object LlmHttpCorsHelper {

  /**
   * Headers to add to every response (including preflight).
   * Returns an empty map if CORS is disabled or the origin is not allowed.
   *
   * @param allowedOrigins The configured allowed origins string from settings.
   *   "*" = allow all, comma-separated = specific origins, blank = disabled.
   * @param requestOrigin The value of the Origin header from the incoming request, or null.
   */
  fun buildCorsHeaders(
    allowedOrigins: String,
    requestOrigin: String?,
  ): Map<String, String> {
    val trimmed = allowedOrigins.trim()
    if (trimmed.isEmpty()) return emptyMap() // CORS disabled

    val headers = mutableMapOf<String, String>()

    if (trimmed == "*") {
      // Wildcard mode: allow all origins
      headers["Access-Control-Allow-Origin"] = "*"
    } else {
      // Specific origins mode: only allow if the request Origin matches one of the configured origins
      val allowed = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
      if (requestOrigin != null && allowed.any { it.equals(requestOrigin, ignoreCase = true) }) {
        headers["Access-Control-Allow-Origin"] = requestOrigin
        // Vary: Origin is required when the response varies based on the request Origin,
        // so intermediate caches don't serve a response with the wrong origin.
        headers["Vary"] = "Origin"
      } else {
        // Origin not in the allowed list — no CORS headers
        return emptyMap()
      }
    }

    headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    // Matches headers commonly sent by OpenAI-compatible clients and web apps.
    // Ollama allows the same set plus x-stainless-* (OpenAI SDK internals).
    headers["Access-Control-Allow-Headers"] =
      "Content-Type, Authorization, User-Agent, Accept, X-Requested-With"
    // Cache preflight response for 24 hours
    headers["Access-Control-Max-Age"] = "86400"
    return headers
  }

  /**
   * Whether a preflight (OPTIONS) request should get a 200 OK response.
   * Returns false if CORS is disabled or the origin is not allowed
   * (in which case the server should still respond, but without CORS headers,
   * letting the browser block the cross-origin request).
   */
  fun shouldAllowPreflight(
    allowedOrigins: String,
    requestOrigin: String?,
  ): Boolean {
    return buildCorsHeaders(allowedOrigins, requestOrigin).isNotEmpty()
  }
}
