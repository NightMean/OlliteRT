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

package com.ollitert.llm.server.service.http

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

private data class WebOrigin(val scheme: String, val host: String, val port: Int)

private fun parseWebOrigin(value: String): WebOrigin? {
  val uri = try { URI(value) } catch (_: URISyntaxException) { return null }
  val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
  if (scheme != "http" && scheme != "https") return null
  val host = uri.host?.lowercase(Locale.ROOT) ?: return null
  if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
    !(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") || uri.port > 65535) return null
  val port = if (uri.port == -1) { if (scheme == "https") 443 else 80 } else uri.port
  return WebOrigin(scheme, host, port)
}

/** Rejects untrusted anonymous browser origins without changing native-client auth. */
internal fun isAllowedAnonymousInferenceOrigin(
  origins: List<String>,
  fetchSites: List<String>,
  requestOrigin: String,
  configuredOrigins: String,
): Boolean {
  if (origins.size > 1 || fetchSites.size > 1) return false
  if (origins.isEmpty()) {
    return fetchSites.isEmpty() || fetchSites.single().equals("same-origin", ignoreCase = true)
  }
  val origin = parseWebOrigin(origins.single()) ?: return false
  if (origin == parseWebOrigin(requestOrigin)) return true
  val allowed = configuredOrigins.trim()
  if (allowed.isEmpty() || allowed == "*") return false
  return allowed.split(',').any { parseWebOrigin(it.trim()) == origin }
}
