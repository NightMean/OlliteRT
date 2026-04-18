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

import com.ollitert.llm.server.data.CORS_PREFLIGHT_MAX_AGE_SECONDS

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

  private val CRLF_REGEX = Regex("[\\r\\n]")

  /**
   * Cached parsed origins list — avoids re-splitting the comma-separated string on every request.
   * Invalidated when the raw setting string changes.
   */
  @Volatile private var cachedRawOrigins: String? = null
  @Volatile private var cachedParsedOrigins: List<String> = emptyList()

  private fun parsedOrigins(trimmed: String): List<String> {
    if (trimmed == cachedRawOrigins) return cachedParsedOrigins
    val parsed = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    cachedRawOrigins = trimmed
    cachedParsedOrigins = parsed
    return parsed
  }

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

    // Defense-in-depth: strip CRLF from the origin before reflecting it into response headers.
    // FlushingSseResponse.send() writes headers as raw bytes — a CRLF in the origin could inject
    // headers. The equals() check below prevents exploitation (a tainted origin won't match),
    // but explicit sanitization eliminates the entire class of attack.
    val safeOrigin = requestOrigin?.replace(CRLF_REGEX, "")

    val headers = mutableMapOf<String, String>()

    if (trimmed == "*") {
      // Wildcard mode: allow all origins
      headers["Access-Control-Allow-Origin"] = "*"
    } else {
      // Specific origins mode: only allow if the request Origin matches one of the configured origins
      val allowed = parsedOrigins(trimmed)
      if (safeOrigin != null && allowed.any { it.equals(safeOrigin, ignoreCase = true) }) {
        headers["Access-Control-Allow-Origin"] = safeOrigin
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
    headers["Access-Control-Max-Age"] = CORS_PREFLIGHT_MAX_AGE_SECONDS
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
