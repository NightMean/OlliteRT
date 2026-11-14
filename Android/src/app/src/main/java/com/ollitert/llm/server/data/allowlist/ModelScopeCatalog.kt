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

import com.ollitert.llm.server.data.prefs.HTTP_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.prefs.HTTP_READ_TIMEOUT_MS
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal const val MODELSCOPE_BASE_URL = "https://modelscope.cn"
@Serializable
internal enum class ModelScopeSite(val baseUrl: String) {
  INTERNATIONAL("https://www.modelscope.ai"), CHINA(MODELSCOPE_BASE_URL),
}
private const val TREE_LIMIT = 3000
private const val PAGE_SIZE = 50
private const val MAX_PAGE = 3000 / PAGE_SIZE
private const val RATE_LIMIT_CODE = 10013201001L

internal class ModelScopeRateLimitException : IOException("ModelScope rate limit exceeded")

@Serializable
internal data class ModelScopeModel(
  val id: String,
  val downloads: Long = 0,
  val likes: Long = 0,
  @SerialName("last_modified") val lastModified: String? = null,
)

internal data class ModelScopeFile(
  val path: String,
  val size: Long,
  val revision: String,
  val sha256: String?,
)

@Serializable private data class FileRecord(
  @SerialName("Path") val path: String? = null,
  @SerialName("Size") val size: Long? = null,
  @SerialName("Revision") val revision: String? = null,
  @SerialName("Sha256") val sha256: String? = null,
  @SerialName("Type") val type: String? = null,
)
@Serializable private data class ModelListResponse(val success: Boolean, val data: ModelListData? = null)
@Serializable private data class ModelDetailResponse(val success: Boolean, val data: ModelScopeModel? = null)
@Serializable private data class ModelListData(
  val models: List<ModelScopeModel>,
  @SerialName("total_count") val totalCount: Int,
)
@Serializable private data class FileListResponse(
  @SerialName("Success") val success: Boolean,
  @SerialName("Code") val code: Long? = null,
  @SerialName("Data") val data: FileListData? = null,
)
@Serializable private data class FileListData(@SerialName("Files") val files: List<FileRecord>)

internal data class ModelScopePage(
  val models: List<ModelScopeModel>,
  val hasMore: Boolean,
  val searchLimitReached: Boolean,
)

internal enum class ModelScopeSort(val apiValue: String) {
  DEFAULT("default"), DOWNLOADS("downloads"), LIKES("likes"), RECENT("last_modified"),
}

internal fun modelScopeFileUrl(repository: String, revision: String, path: String, site: ModelScopeSite = ModelScopeSite.CHINA): String {
  require(isValidHuggingFaceModelId(repository))
  require(revision.matches(Regex("[a-fA-F0-9]{40}")))
  require(isSupportedHuggingFaceFile(path))
  val encoded = path.split('/').joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }
  return "${site.baseUrl}/models/$repository/resolve/$revision/$encoded"
}

/** Reads public repositories without forwarding Hugging Face credentials. */
internal class ModelScopeCatalog(
  private val openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
  private val endpoint: String = MODELSCOPE_BASE_URL,
) {
  private val json = Json { ignoreUnknownKeys = true }

  fun search(query: String, sort: ModelScopeSort, page: Int = 1): ModelScopePage {
    require(page in 1..MAX_PAGE) { "Invalid ModelScope page" }
    val search = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    val url = "$endpoint/openapi/v1/models?filter.custom_tag=litert-lm&page_number=$page&page_size=$PAGE_SIZE" +
      "&sort=${sort.apiValue}" + if (search.isEmpty()) "" else "&search=$search"
    val response = json.decodeFromString<ModelListResponse>(get(url))
    if (!response.success || response.data == null) throw IOException("ModelScope catalog request failed")
    return ModelScopePage(
      response.data.models.filter { isValidHuggingFaceModelId(it.id) },
      page < MAX_PAGE && page * PAGE_SIZE < response.data.totalCount,
      page == MAX_PAGE && page * PAGE_SIZE < response.data.totalCount,
    )
  }

  fun details(repository: String): ModelScopeModel {
    require(isValidHuggingFaceModelId(repository)) { "Invalid model repository ID" }
    val response = json.decodeFromString<ModelDetailResponse>(get("$endpoint/openapi/v1/models/$repository"))
    if (!response.success || response.data == null || response.data.id != repository) throw IOException("ModelScope repository request failed")
    return response.data
  }

  fun files(repository: String, revision: String = "master"): List<ModelScopeFile> {
    require(isValidHuggingFaceModelId(repository)) { "Invalid model repository ID" }
    require(revision.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) && revision != "." && revision != "..") { "Invalid model revision" }
    val response = json.decodeFromString<FileListResponse>(
      get("$endpoint/api/v1/models/$repository/repo/files?Revision=$revision&Recursive=True")
    )
    if (response.code == RATE_LIMIT_CODE) throw ModelScopeRateLimitException()
    if (!response.success || response.data == null) throw IOException("ModelScope file listing failed (code ${response.code})")
    // The legacy API silently truncates large trees; never present a partial list as complete.
    if (response.data.files.size >= TREE_LIMIT) throw IOException("ModelScope file listing is incomplete")
    return response.data.files.mapNotNull {
      val path = it.path ?: return@mapNotNull null
      val size = it.size ?: return@mapNotNull null
      val revision = it.revision ?: return@mapNotNull null
      if (it.type != "blob" || size <= 0 || !isSupportedHuggingFaceFile(path) ||
        !revision.matches(Regex("[a-fA-F0-9]{40}"))) return@mapNotNull null
      ModelScopeFile(path, size, revision, it.sha256)
    }
  }

  private fun get(url: String): String {
    val connection = openConnection(url)
    try {
      connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
      connection.readTimeout = HTTP_READ_TIMEOUT_MS
      connection.instanceFollowRedirects = false
      connection.setRequestProperty("User-Agent", "OlliteRT")
      val status = connection.responseCode
      if (status != HttpURLConnection.HTTP_OK) {
        // The legacy API reports rate limits as HTTP 403 with a provider-specific code.
        val error = connection.errorStream?.bufferedReader()?.use { it.readTextLimited(4096) }
        val code = error?.let { runCatching { json.parseToJsonElement(it).jsonObject["Code"]?.jsonPrimitive?.longOrNull }.getOrNull() }
        if (status == 429 || code == RATE_LIMIT_CODE) throw ModelScopeRateLimitException()
        throw IOException("ModelScope returned HTTP $status")
      }
      return connection.inputStream.bufferedReader().use { reader ->
        val result = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
          val count = reader.read(buffer)
          if (count < 0) break
          if (result.length + count > MAX_ALLOWLIST_RESPONSE_BYTES) throw IOException("ModelScope response is too large")
          result.append(buffer, 0, count)
        }
        result.toString()
      }
    } finally {
      connection.disconnect()
    }
  }
}

private fun java.io.Reader.readTextLimited(limit: Int): String {
  val chars = CharArray(limit)
  val result = StringBuilder()
  while (result.length < limit) {
    val count = read(chars, 0, limit - result.length)
    if (count < 0) break
    result.append(chars, 0, count)
  }
  return result.toString()
}
