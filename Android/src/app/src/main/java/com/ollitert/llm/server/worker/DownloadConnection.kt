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

import androidx.work.Data
import com.ollitert.llm.server.data.allowlist.hasSameOriginAs
import com.ollitert.llm.server.data.allowlist.isHuggingFaceUrl
import com.ollitert.llm.server.data.prefs.DOWNLOAD_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.prefs.DOWNLOAD_READ_TIMEOUT_MS
import com.ollitert.llm.server.data.storage.HuggingFaceDownloadAccess
import com.ollitert.llm.server.data.storage.KEY_HUGGING_FACE_DOWNLOAD_ACCESS
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_ACCESS_TOKEN
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal fun huggingFaceDownloadAccess(input: Data): HuggingFaceDownloadAccess {
  val mode = input.getString(KEY_HUGGING_FACE_DOWNLOAD_ACCESS)
  return HuggingFaceDownloadAccess.entries.firstOrNull { it.name == mode }
    // Legacy scheduled work may carry an obsolete token. Use only the fact that it needed auth.
    ?: if (input.getString(KEY_MODEL_DOWNLOAD_ACCESS_TOKEN) != null) HuggingFaceDownloadAccess.IF_REQUIRED
       else HuggingFaceDownloadAccess.ANONYMOUS
}

internal fun HttpURLConnection.configureDownloadRequest(offset: Long) {
  connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
  readTimeout = DOWNLOAD_READ_TIMEOUT_MS
  if (offset > 0) {
    setRequestProperty("Range", "bytes=$offset-")
    setRequestProperty("Accept-Encoding", "identity")
  }
}

/** Resume public files anonymously, and retry a direct authentication denial with the current token. */
internal fun openHuggingFaceDownloadConnection(
  url: URL,
  offset: Long,
  access: HuggingFaceDownloadAccess,
  getToken: () -> String?,
  open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
): HttpURLConnection {
  require(isHuggingFaceUrl(url.toString()))
  var current = url
  var redirects = 0
  var authenticatedOrigin = true
  var token = if (access == HuggingFaceDownloadAccess.AUTHENTICATED) getToken() else null
  while (true) {
    val connection = open(current)
    try {
      connection.configureDownloadRequest(offset)
      connection.instanceFollowRedirects = false
      if (authenticatedOrigin && token != null) connection.setRequestProperty("Authorization", "Bearer $token")
      val status = connection.responseCode
      if (authenticatedOrigin && token == null && access == HuggingFaceDownloadAccess.IF_REQUIRED &&
        (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN)
      ) {
        token = getToken()
        if (token != null) {
          connection.disconnect()
          continue
        }
      }
      if (status !in 300..399) return connection
      if (++redirects > 5) throw IOException("Too many Hugging Face download redirects")
      val location = connection.getHeaderField("Location")
        ?: throw IOException("Hugging Face download redirect has no location")
      val next = URL(current, location)
      if (next.protocol != "https" || next.userInfo != null) throw IOException("Unsafe Hugging Face download redirect")
      // Signed storage links have their own credentials. Send ours only on the original origin.
      authenticatedOrigin = authenticatedOrigin && next.hasSameOriginAs(url)
      current = next
    } catch (e: Exception) {
      connection.disconnect()
      throw e
    }
    connection.disconnect()
  }
}
