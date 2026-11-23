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
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

internal const val MODELSCOPE_BASE_URL = "https://modelscope.cn"
@Serializable
internal enum class ModelScopeSite(val baseUrl: String) {
  INTERNATIONAL("https://www.modelscope.ai"), CHINA(MODELSCOPE_BASE_URL),
}

internal fun modelScopeSiteForUrl(url: String): ModelScopeSite? {
  val uri = try { java.net.URI(url) } catch (_: java.net.URISyntaxException) { return null }
  if (!uri.scheme.equals("https", true) || uri.userInfo != null || uri.port !in listOf(-1, 443)) return null
  return when (uri.host?.lowercase(java.util.Locale.ROOT)) {
    "modelscope.ai", "www.modelscope.ai" -> ModelScopeSite.INTERNATIONAL
    "modelscope.cn", "www.modelscope.cn" -> ModelScopeSite.CHINA
    else -> null
  }
}

internal fun setModelScopeCredentials(connection: HttpURLConnection, token: String?) {
  val value = token?.trim()?.takeIf { it.isNotEmpty() } ?: return
  require(value.all { it.code in 0x21..0x7e && it !in ";,\\\"" }) { "Invalid ModelScope token" }
  connection.setRequestProperty("Authorization", "Bearer $value")
  connection.setRequestProperty("Cookie", "m_session_id=$value")
}
private const val TREE_LIMIT = 3000
private const val PAGE_SIZE = 50
private const val MAX_PAGE = 3000 / PAGE_SIZE
internal const val MODELSCOPE_RATE_LIMIT_CODE = 10013201001L

internal fun isModelScopeLegacyRateLimit(body: String?): Boolean {
  if (body == null) return false
  val response = try {
    Json.parseToJsonElement(body) as? JsonObject
  } catch (_: IllegalArgumentException) {
    null
  }
  return (response?.get("Code") as? JsonPrimitive)?.longOrNull == MODELSCOPE_RATE_LIMIT_CODE
}

internal fun HttpURLConnection.hasModelScopeLegacyRateLimit(): Boolean =
  isModelScopeLegacyRateLimit(readErrorPrefix())

internal class ModelScopeRateLimitException(val retryAfterMillis: Long? = null) : IOException("ModelScope rate limit exceeded")
internal class ModelScopeAccessException : IOException("ModelScope denied access")
private class ModelScopeNotFoundException : IOException("ModelScope repository not found")

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
@Serializable private data class OpenModelDetailResponse(val success: Boolean, val data: ModelScopeModel? = null)
@Serializable private data class ModelDetailResponse(
  @SerialName("Success") val success: Boolean,
  @SerialName("Data") val data: ModelDetailData? = null,
)
@Serializable private data class ModelDetailData(
  @SerialName("Path") val owner: String,
  @SerialName("Name") val name: String,
  @SerialName("Downloads") val downloads: Long = 0,
  @SerialName("Stars") val likes: Long = 0,
  @SerialName("LastUpdatedTime") val lastUpdated: Long? = null,
)
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
  val encoded = URLEncoder.encode(path, Charsets.UTF_8.name()).replace("+", "%20")
  return "${site.baseUrl}/api/v1/models/$repository/repo?Revision=$revision&FilePath=$encoded"
}

/** Reads public repositories without forwarding Hugging Face credentials. */
internal class ModelScopeCatalog(
  private val openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection },
  private val endpoint: String = MODELSCOPE_BASE_URL,
  private val token: String? = null,
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
    try {
      val response = json.decodeFromString<OpenModelDetailResponse>(get("$endpoint/openapi/v1/models/$repository"))
      if (!response.success || response.data == null || response.data.id != repository) throw IOException("ModelScope repository request failed")
      return response.data
    } catch (_: ModelScopeNotFoundException) {
      // OpenAPI can hide private repositories that the legacy endpoint can access.
    } catch (_: ModelScopeAccessException) {
      // Legacy metadata may grant access when the OpenAPI endpoint does not.
    }
    val response = json.decodeFromString<ModelDetailResponse>(get("$endpoint/api/v1/models/$repository"))
    val detail = response.data
    if (!response.success || detail == null || "${detail.owner}/${detail.name}" != repository) throw IOException("ModelScope repository request failed")
    return ModelScopeModel(repository, detail.downloads, detail.likes,
      detail.lastUpdated?.let { java.time.Instant.ofEpochSecond(it).toString() })
  }

  fun files(repository: String, revision: String = "master"): List<ModelScopeFile> {
    require(isValidHuggingFaceModelId(repository)) { "Invalid model repository ID" }
    require(revision.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) && revision != "." && revision != "..") { "Invalid model revision" }
    val result = getResponse("$endpoint/api/v1/models/$repository/repo/files?Revision=$revision&Recursive=True")
    val response = json.decodeFromString<FileListResponse>(result.body)
    if (response.code == MODELSCOPE_RATE_LIMIT_CODE) throw ModelScopeRateLimitException(result.retryAfterMillis)
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

  private data class Response(val body: String, val retryAfterMillis: Long?)

  private fun get(url: String): String = getResponse(url).body

  private fun getResponse(url: String, withCredentials: Boolean = true): Response {
    val connection = openConnection(url)
    try {
      connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
      connection.readTimeout = HTTP_READ_TIMEOUT_MS
      connection.instanceFollowRedirects = false
      connection.setRequestProperty("User-Agent", "OlliteRT")
      if (withCredentials && modelScopeSiteForUrl(url) != null && modelScopeSiteForUrl(url) == modelScopeSiteForUrl(endpoint)) {
        setModelScopeCredentials(connection, token)
      }
      val status = connection.responseCode
      if (status != HttpURLConnection.HTTP_OK) {
        if (status == 429) throw ModelScopeRateLimitException(parseRetryAfter(connection.getHeaderField("Retry-After")))
        // The legacy API reports rate limits as HTTP 403 with a provider-specific code.
        if (connection.hasModelScopeLegacyRateLimit()) {
          throw ModelScopeRateLimitException(parseRetryAfter(connection.getHeaderField("Retry-After")))
        }
        if (status == HttpURLConnection.HTTP_NOT_FOUND) throw ModelScopeNotFoundException()
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
          // An invalid token must not hide repositories available anonymously.
          if (withCredentials && !token.isNullOrBlank()) {
            return try { getResponse(url, withCredentials = false) }
            catch (_: ModelScopeNotFoundException) { throw ModelScopeAccessException() }
          }
          throw ModelScopeAccessException()
        }
        throw IOException("ModelScope returned HTTP $status")
      }
      val body = connection.inputStream.bufferedReader().use { it.readBoundedText(MAX_ALLOWLIST_RESPONSE_BYTES.toInt()) }
        ?: throw IOException("ModelScope response is too large")
      return Response(body, parseRetryAfter(connection.getHeaderField("Retry-After")))
    } finally {
      connection.disconnect()
    }
  }
}

/** Unknown or overflowing cooldowns disable automatic retry rather than retrying too early. */
internal fun parseRetryAfter(value: String?, now: Instant = Instant.now()): Long? {
  val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  if (text.all { it in '0'..'9' }) {
    val seconds = text.toLongOrNull() ?: return Long.MAX_VALUE
    return if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
  }
  return try {
    val target = ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    java.time.Duration.between(now, target).toMillis().coerceAtLeast(0)
  } catch (_: java.time.DateTimeException) { Long.MAX_VALUE }
    catch (_: ArithmeticException) { Long.MAX_VALUE }
}
