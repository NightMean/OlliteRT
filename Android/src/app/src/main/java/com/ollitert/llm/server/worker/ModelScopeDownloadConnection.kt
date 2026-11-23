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

package com.ollitert.llm.server.worker

import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.allowlist.modelScopeSiteForUrl
import com.ollitert.llm.server.data.allowlist.setModelScopeCredentials
import com.ollitert.llm.server.data.allowlist.ModelScopeRateLimitException
import com.ollitert.llm.server.data.allowlist.hasModelScopeLegacyRateLimit
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class ModelScopeDownloadAccessException(val site: ModelScopeSite) : IOException("ModelScope denied access")

/** Follows HTTPS redirects without forwarding a site's token to another site or storage host. */
internal fun openModelScopeDownloadConnection(
  url: URL, token: String?, offset: Long,
  open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
): HttpURLConnection {
  val site = requireNotNull(modelScopeSiteForUrl(url.toString()))
  var current = url
  var redirects = 0
  var useCredentials = !token.isNullOrBlank()
  var rejectedCredentials = false
  while (true) {
    val connection = open(current)
    try {
      connection.configureDownloadRequest(offset)
      connection.instanceFollowRedirects = false
      val onOriginalSite = modelScopeSiteForUrl(current.toString()) == site
      if (useCredentials && onOriginalSite) setModelScopeCredentials(connection, token)
      connection.connect()
      val status = connection.responseCode
      if (onOriginalSite && status in listOf(401, 403, 429)) {
        if (status == 429 || connection.hasModelScopeLegacyRateLimit()) throw ModelScopeRateLimitException()
        if (useCredentials) {
          // Match the official client's anonymous retry for public files after auth rejection.
          connection.disconnect()
          useCredentials = false
          rejectedCredentials = true
          continue
        }
        throw ModelScopeDownloadAccessException(site)
      }
      if (rejectedCredentials && onOriginalSite && status == HttpURLConnection.HTTP_NOT_FOUND) throw ModelScopeDownloadAccessException(site)
      if (status !in 300..399) return connection
      if (++redirects > 5) throw IOException("Too many ModelScope download redirects")
      val location = connection.getHeaderField("Location") ?: throw IOException("ModelScope download redirect has no location")
      val next = URL(current, location)
      if (!next.protocol.equals("https", true) || next.userInfo != null) throw IOException("Unsafe ModelScope download redirect")
      current = next
    } catch (e: Exception) {
      connection.disconnect()
      throw e
    }
    connection.disconnect()
  }
}
