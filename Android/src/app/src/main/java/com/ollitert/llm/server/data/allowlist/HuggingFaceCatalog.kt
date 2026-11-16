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
import java.net.URI
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

internal data class HuggingFaceSearchProgress(
  val models: List<HuggingFaceModel>, val nextUrl: String?,
  val visited: Set<String> = emptySet(), val responseBytes: Long = 0,
)

private const val MAX_SEARCH_PAGES = 1000
internal const val MAX_SEARCH_RESPONSE_BYTES = 32L * 1024 * 1024
private const val MAX_SEARCH_MODELS = 10000
internal class HuggingFaceSearchLimitException : IOException("Hugging Face search limit reached")
internal class HuggingFacePaginationException(message: String, cause: Throwable? = null) : IOException(message, cause)

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

  fun initialSearch(query: String, sort: HuggingFaceSort): HuggingFaceSearchProgress {
    val encodedQuery = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
    val url = "${GitHubConfig.HUGGINGFACE_BASE_URL}/api/models?filter=litert-lm" +
      "&sort=${sort.apiValue}&direction=-1&limit=50&expand=siblings&expand=downloads&expand=likes&expand=lastModified" +
      if (encodedQuery.isEmpty()) "" else "&search=$encodedQuery"
    return HuggingFaceSearchProgress(emptyList(), url)
  }

  /** Fetch one opaque cursor, preserving the validated progress for a later retry. */
  fun searchPage(start: HuggingFaceSearchProgress, token: String?): HuggingFaceSearchProgress {
    val url = start.nextUrl ?: return start
    if (start.visited.size >= MAX_SEARCH_PAGES) throw HuggingFaceSearchLimitException()
    if (url in start.visited) throw HuggingFacePaginationException("Hugging Face returned a repeated pagination URL")
    val response = getResponse(url, token)
    val responseBytes = start.responseBytes + response.body.length.toLong() * 2
    if (responseBytes > MAX_SEARCH_RESPONSE_BYTES) throw HuggingFaceSearchLimitException()
    val found = start.models.associateByTo(linkedMapOf()) { it.id }
    json.decodeFromString<List<HuggingFaceModel>>(response.body)
      .filter { isValidHuggingFaceModelId(it.id) && it.modelFiles.isNotEmpty() }
      .forEach { found.putIfAbsent(it.id, it.copy(siblings = it.modelFiles)) }
    if (found.size > MAX_SEARCH_MODELS) throw HuggingFaceSearchLimitException()
    val (community, others) = found.values.partition { it.id.substringBefore('/').equals("litert-community", ignoreCase = true) }
    return HuggingFaceSearchProgress(community + others, response.next, start.visited + url, responseBytes)
  }

  fun details(id: String, token: String?, revision: String? = null): HuggingFaceModel {
    require(isValidHuggingFaceModelId(id)) { "Invalid model repository ID" }
    val revisionPath = revision?.let { "/revision/" + URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") } ?: ""
    return json.decodeFromString(get("${GitHubConfig.HUGGINGFACE_BASE_URL}/api/models/$id$revisionPath?blobs=true", token))
  }

  private fun get(url: String, token: String?): String = getResponse(url, token).body

  private data class Response(val body: String, val next: String?)

  private fun getResponse(url: String, token: String?): Response {
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
      val next = nextPageUrl(connection.getHeaderField("Link"))
      val body = connection.inputStream.bufferedReader().use { reader ->
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
      return Response(body, next)
    } finally {
      connection.disconnect()
    }
  }
}

/** Only an authenticated request to the original model-list endpoint may follow a cursor. */
internal fun nextPageUrl(header: String?): String? {
  if (header.isNullOrBlank()) return null
  val parts = mutableListOf<String>()
  var start = 0
  var inAngle = false
  var inQuote = false
  var escaped = false
  header.forEachIndexed { index, character ->
    if (escaped) escaped = false
    else if (inQuote && character == '\\') escaped = true
    else if (!inQuote && character == '<') inAngle = true
    else if (!inQuote && character == '>') inAngle = false
    else if (!inAngle && character == '"') inQuote = !inQuote
    else if (!inAngle && !inQuote && character == ',') { parts.add(header.substring(start, index)); start = index + 1 }
  }
  if (inAngle || inQuote || escaped) throw HuggingFacePaginationException("Invalid Hugging Face pagination header")
  parts.add(header.substring(start))
  var next: String? = null
  for (part in parts) {
    val match = Regex("<([^>]+)>\\s*;(.*)").matchEntire(part.trim())
      ?: throw HuggingFacePaginationException("Invalid Hugging Face pagination header")
    val relation = Regex("(?:^|;)\\s*rel\\s*=\\s*(?:\"([^\"]*)\"|([^;\\s]+))", RegexOption.IGNORE_CASE)
      .find(match.groupValues[2]) ?: throw HuggingFacePaginationException("Hugging Face link has no relation")
    val rels = (relation.groupValues[1].ifEmpty { relation.groupValues[2] }).split(Regex("\\s+"))
    if (rels.any { it.equals("next", true) }) {
      if (next != null) throw HuggingFacePaginationException("Ambiguous Hugging Face pagination header")
      next = match.groupValues[1]
    }
  }
  if (next == null) return null
  val uri = try { URI(next) } catch (e: java.net.URISyntaxException) { throw HuggingFacePaginationException("Invalid Hugging Face pagination URL", e) }
  if (!uri.scheme.equals("https", true) || !uri.host.equals("huggingface.co", true) || uri.userInfo != null ||
    uri.port !in listOf(-1, 443) || uri.path != "/api/models" || uri.rawFragment != null || uri.rawQuery == null) {
    throw HuggingFacePaginationException("Unsafe Hugging Face pagination URL")
  }
  return uri.toASCIIString()
}
