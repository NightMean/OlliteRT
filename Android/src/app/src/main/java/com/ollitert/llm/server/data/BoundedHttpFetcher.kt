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

package com.ollitert.llm.server.data

import android.util.Log
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "BoundedHttpFetcher"

fun fetchBounded(
  url: String,
  userAgent: String,
  redirectCount: Int = 0,
): String? {
  if (redirectCount > MAX_REDIRECTS) {
    Log.w(TAG, "Too many redirects for $url")
    return null
  }
  val connection = try {
    URL(url).openConnection() as HttpURLConnection
  } catch (e: Exception) {
    Log.w(TAG, "Invalid URL: $url", e)
    return null
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
        return null
      }
      val isHttps = url.startsWith("https://")
      val redirectIsHttp = location.startsWith("http://")
      if (isHttps && redirectIsHttp) {
        Log.w(TAG, "Rejecting HTTPS→HTTP redirect downgrade: $location")
        return null
      }
      if (location.startsWith("https://") || location.startsWith("http://")) {
        connection.disconnect()
        return fetchBounded(location, userAgent, redirectCount + 1)
      }
      Log.w(TAG, "Rejecting redirect to unsupported protocol: $location")
      return null
    }
    if (code !in 200..299) {
      Log.w(TAG, "Fetch failed: HTTP $code for $url")
      return null
    }
    val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
    if (contentLength != null && contentLength > MAX_ALLOWLIST_RESPONSE_BYTES) {
      throw IOException("Response Content-Length exceeds ${MAX_ALLOWLIST_RESPONSE_BYTES / 1024 / 1024}MB")
    }
    return connection.inputStream.bufferedReader().use { reader ->
      val sb = StringBuilder()
      val buffer = CharArray(8192)
      var totalRead = 0L
      var read: Int
      while (reader.read(buffer).also { read = it } != -1) {
        totalRead += read
        if (totalRead > MAX_ALLOWLIST_RESPONSE_BYTES) {
          throw IOException("Response body exceeds ${MAX_ALLOWLIST_RESPONSE_BYTES / 1024 / 1024}MB")
        }
        sb.append(buffer, 0, read)
      }
      sb.toString()
    }
  } finally {
    connection.disconnect()
  }
}
