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

package com.ollitert.llm.server.data.allowlist
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.prefs.HTTP_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.prefs.HTTP_READ_TIMEOUT_MS

import android.util.Log
import java.io.IOException
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "OlliteRT.HttpFetcher"

/** Returns null when the response exceeds the limit, unless the caller only needs a prefix. */
internal fun Reader.readBoundedText(maxChars: Int, allowTruncation: Boolean = false): String? {
  require(maxChars >= 0)
  val text = StringBuilder()
  val buffer = CharArray(minOf(DEFAULT_BUFFER_SIZE, maxChars))
  while (text.length < maxChars) {
    val count = read(buffer, 0, minOf(buffer.size, maxChars - text.length))
    if (count < 0) return text.toString()
    text.append(buffer, 0, count)
  }
  return if (allowTruncation || read() < 0) text.toString() else null
}

sealed class FetchResult {
  data class Success(val body: String) : FetchResult()
  data class HttpError(val code: Int) : FetchResult()
  data class NetworkError(val message: String) : FetchResult()
}

fun fetchBounded(
  url: String,
  userAgent: String,
  redirectCount: Int = 0,
  openConnection: (String) -> HttpURLConnection = ::defaultOpenConnection,
): String? = when (val result = fetchBoundedResult(url, userAgent, redirectCount, openConnection)) {
  is FetchResult.Success -> result.body
  else -> null
}

fun fetchBoundedResult(
  url: String,
  userAgent: String,
  redirectCount: Int = 0,
  openConnection: (String) -> HttpURLConnection = ::defaultOpenConnection,
): FetchResult {
  if (redirectCount > MAX_REDIRECTS) {
    Log.w(TAG, "Too many redirects for $url")
    return FetchResult.NetworkError("Too many redirects")
  }
  val connection = try {
    openConnection(url)
  } catch (e: Exception) {
    Log.w(TAG, "Invalid URL: $url", e)
    return FetchResult.NetworkError("Invalid URL")
  }
  try {
    connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
    connection.readTimeout = HTTP_READ_TIMEOUT_MS
    connection.setRequestProperty("User-Agent", userAgent)
    connection.instanceFollowRedirects = false
    val code = connection.responseCode
    if (code == 301 || code == 302 || code == 307 || code == 308) {
      val location = connection.getHeaderField("Location")
      if (location == null) {
        Log.w(TAG, "Redirect with no Location header")
        return FetchResult.NetworkError("Redirect with no Location header")
      }
      val isHttps = url.startsWith("https://")
      val redirectIsHttp = location.startsWith("http://")
      if (isHttps && redirectIsHttp) {
        Log.w(TAG, "Rejecting HTTPS→HTTP redirect downgrade: $location")
        return FetchResult.NetworkError("Redirect from HTTPS to HTTP is not allowed")
      }
      if (location.startsWith("https://") || location.startsWith("http://")) {
        connection.disconnect()
        return fetchBoundedResult(location, userAgent, redirectCount + 1, openConnection)
      }
      Log.w(TAG, "Rejecting redirect to unsupported protocol: $location")
      return FetchResult.NetworkError("Redirect to unsupported protocol")
    }
    if (code !in 200..299) {
      Log.w(TAG, "Fetch failed: HTTP $code for $url")
      return FetchResult.HttpError(code)
    }
    val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
    if (contentLength != null && contentLength > MAX_ALLOWLIST_RESPONSE_BYTES) {
      return FetchResult.NetworkError("Response too large (>${MAX_ALLOWLIST_RESPONSE_BYTES / 1024 / 1024}MB)")
    }
    val body = connection.inputStream.bufferedReader().use { it.readBoundedText(MAX_ALLOWLIST_RESPONSE_BYTES.toInt()) }
      ?: return FetchResult.NetworkError("Response too large")
    return FetchResult.Success(body)
  } catch (e: IOException) {
    Log.w(TAG, "Network error fetching $url", e)
    return FetchResult.NetworkError(e.message ?: "Network error")
  } finally {
    connection.disconnect()
  }
}

// ── Model URL probing ────────────────────────────────────────────────────

sealed class ModelUrlResult {
  data class Success(val code: Int) : ModelUrlResult()
  data class Error(val message: String) : ModelUrlResult()
  data object UnexpectedResponse : ModelUrlResult()
}

internal fun probeModelUrl(
  modelUrl: String,
  accessToken: String?,
  openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
): ModelUrlResult {
  var connection: HttpURLConnection? = null
  var redirectConnection: HttpURLConnection? = null
  try {
    connection = openConnection(modelUrl)
    connection.requestMethod = "HEAD"
    connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
    connection.readTimeout = HTTP_READ_TIMEOUT_MS
    connection.instanceFollowRedirects = false
    // Scope the credential to huggingface.co — the probe URL can point anywhere.
    if (accessToken != null && isHuggingFaceUrl(modelUrl)) {
      connection.setRequestProperty("Authorization", "Bearer $accessToken")
    }
    connection.connect()

    val responseCode = connection.responseCode
    if (responseCode in 300..399) {
      val redirectUrl = connection.getHeaderField("Location")
        ?: return ModelUrlResult.UnexpectedResponse
      redirectConnection = openConnection(redirectUrl)
      redirectConnection.requestMethod = "HEAD"
      redirectConnection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
      redirectConnection.readTimeout = HTTP_READ_TIMEOUT_MS
      redirectConnection.instanceFollowRedirects = true
      redirectConnection.connect()
      val redirectCode = redirectConnection.responseCode
      // CDN or signed-link authentication errors do not establish whether the provider token works.
      if (redirectCode == HttpURLConnection.HTTP_UNAUTHORIZED || redirectCode == HttpURLConnection.HTTP_FORBIDDEN) {
        return ModelUrlResult.UnexpectedResponse
      }
      if (redirectCode !in 200..299) return ModelUrlResult.Success(redirectCode)
      val contentType = redirectConnection.contentType ?: ""
      if (contentType.contains("text/html", ignoreCase = true)) return ModelUrlResult.UnexpectedResponse
      return ModelUrlResult.Success(redirectCode)
    }
    return ModelUrlResult.Success(responseCode)
  } catch (e: Exception) {
    return ModelUrlResult.Error(e.message ?: "Unknown network error")
  } finally {
    connection?.disconnect()
    redirectConnection?.disconnect()
  }
}

// ── Token helpers ────────────────────────────────────────────────────────

/** Normalizes the single configured Hugging Face token used by downloads and resumed work. */
internal fun configuredHfTokenOrNull(rawToken: String): String? =
  rawToken.trim().takeIf { it.isNotEmpty() }

/**
 * True when it is safe to attach the user's Hugging Face bearer token to requests
 * for [url]. Allowlist entries and user-added repos can declare arbitrary download
 * URLs — a hostile repo operator must never receive the credential, so the token
 * is scoped to huggingface.co (and subdomains) only.
 */
internal fun isHuggingFaceUrl(url: String): Boolean {
  val host = try {
    URL(url).host.lowercase()
  } catch (_: Exception) {
    return false
  }
  return host == "huggingface.co" || host.endsWith(".huggingface.co")
}

private fun defaultOpenConnection(url: String): HttpURLConnection =
  URL(url).openConnection() as HttpURLConnection

