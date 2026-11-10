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

import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.data.prefs.HTTP_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.prefs.HTTP_READ_TIMEOUT_MS
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class HuggingFaceFile(
  @SerialName("rfilename") val path: String,
  val size: Long? = null,
  val lfs: HuggingFaceLfs? = null,
) {
  val sizeBytes: Long? get() = size?.takeIf { it > 0 } ?: lfs?.size?.takeIf { it > 0 }
}

@Serializable
internal data class HuggingFaceLfs(val size: Long? = null)

@Serializable
internal data class HuggingFaceModel(
  val id: String,
  val sha: String? = null,
  val downloads: Long = 0,
  val likes: Long = 0,
  val lastModified: String? = null,
  val siblings: List<HuggingFaceFile> = emptyList(),
) {
  val modelFiles: List<HuggingFaceFile>
    get() = siblings.filter { isSupportedHuggingFaceFile(it.path) }
}

internal enum class HuggingFaceSort(val apiValue: String) {
  TRENDING("trendingScore"), DOWNLOADS("downloads"), LIKES("likes"), RECENT("lastModified"),
}

internal fun isSupportedHuggingFaceFile(path: String): Boolean {
  val segments = path.split('/')
  return segments.all { it.isNotBlank() && it != "." && it != ".." && !it.contains('\\') } &&
    path.endsWith(".litertlm", ignoreCase = true) && !path.contains("-web", ignoreCase = true)
}

internal fun isValidHuggingFaceModelId(id: String): Boolean =
  id.split('/').let { parts ->
    parts.size == 2 && parts.all { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) && it != "." && it != ".." }
  }

/** API calls run on an IO dispatcher. Redirects are rejected to keep tokens on the API host. */
internal class HuggingFaceCatalog(
  private val openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
) {
  private val json = Json { ignoreUnknownKeys = true }

  fun search(query: String, sort: HuggingFaceSort, token: String?): List<HuggingFaceModel> {
    val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    val url = "${GitHubConfig.HUGGINGFACE_BASE_URL}/api/models?filter=litert-lm" +
      "&sort=${sort.apiValue}&direction=-1&limit=50&expand=siblings&expand=downloads&expand=likes&expand=lastModified" +
      if (encodedQuery.isEmpty()) "" else "&search=$encodedQuery"
    val models = json.decodeFromString<List<HuggingFaceModel>>(get(url, token)).filter { it.modelFiles.isNotEmpty() }
    val (community, others) = models.partition { it.id.substringBefore('/').equals("litert-community", ignoreCase = true) }
    return community + others
  }

  fun details(id: String, token: String?, revision: String? = null): HuggingFaceModel {
    require(isValidHuggingFaceModelId(id)) { "Invalid model repository ID" }
    val revisionPath = revision?.let { "/revision/" + URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") } ?: ""
    return json.decodeFromString(get("${GitHubConfig.HUGGINGFACE_BASE_URL}/api/models/$id$revisionPath?blobs=true", token))
  }

  private fun get(url: String, token: String?): String {
    val connection = openConnection(url)
    try {
      connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
      connection.readTimeout = HTTP_READ_TIMEOUT_MS
      connection.instanceFollowRedirects = false
      connection.setRequestProperty("User-Agent", "OlliteRT")
      token?.trim()?.takeIf { it.isNotEmpty() }?.let {
        connection.setRequestProperty("Authorization", "Bearer $it")
      }
      val status = connection.responseCode
      if (status != HttpURLConnection.HTTP_OK) throw IOException("Hugging Face returned HTTP $status")
      return connection.inputStream.bufferedReader().use { reader ->
        val result = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
          val count = reader.read(buffer)
          if (count < 0) break
          if (result.length + count > MAX_ALLOWLIST_RESPONSE_BYTES) throw IOException("Hugging Face response is too large")
          result.append(buffer, 0, count)
        }
        result.toString()
      }
    } finally {
      connection.disconnect()
    }
  }
}
