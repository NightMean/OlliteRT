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

import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.data.HTTP_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.HTTP_READ_TIMEOUT_MS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

internal data class ReleaseInfo(
  val tagName: String,
  val htmlUrl: String,
  val etag: String?,
)

internal sealed class GitHubResponse {
  data class Success(val body: String, val etag: String?) : GitHubResponse()
  data object NotModified : GitHubResponse()
  data class Error(val code: Int) : GitHubResponse()
}

internal class UpdateCheckException(val httpCode: Int, val url: String) : Exception("HTTP $httpCode: $url")

internal object GitHubReleaseClient {
  // Tag patterns for channel-aware filtering (internal for testability)
  val STABLE_TAG_PATTERN = Regex("^v\\d+\\.\\d+\\.\\d+$")
  val BETA_TAG_PATTERN = Regex("^v\\d+\\.\\d+\\.\\d+(-beta\\.\\d+)?$")
  val DEV_TAG_PATTERN = Regex("^v\\d+\\.\\d+\\.\\d+(-(?:dev|beta)\\.\\d+)?$")

  fun isOwnChannelTag(tag: String): Boolean {
    val ownPattern = when (BuildConfig.UPDATE_CHANNEL) {
      "stable" -> STABLE_TAG_PATTERN
      "beta" -> BETA_TAG_PATTERN
      "dev" -> DEV_TAG_PATTERN
      else -> STABLE_TAG_PATTERN
    }
    return ownPattern.matches(tag)
  }

  fun fetchGitHub(url: String, etag: String?): GitHubResponse {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
      connection.readTimeout = HTTP_READ_TIMEOUT_MS
      connection.setRequestProperty("Accept", "application/vnd.github+json")
      connection.setRequestProperty("User-Agent", "OlliteRT/${BuildConfig.VERSION_NAME}")
      if (etag != null) {
        connection.setRequestProperty("If-None-Match", etag)
      }

      val code = connection.responseCode
      return when {
        code == 304 -> GitHubResponse.NotModified
        code in 200..299 -> {
          val body = connection.inputStream.bufferedReader().use { it.readText() }
          val responseEtag = connection.getHeaderField("ETag")
          GitHubResponse.Success(body, responseEtag)
        }
        else -> GitHubResponse.Error(code)
      }
    } finally {
      connection.disconnect()
    }
  }

  fun parseRelease(json: String, etag: String?): ReleaseInfo? {
    val obj = Json.parseToJsonElement(json).jsonObject
    val tag = obj["tag_name"]?.jsonPrimitive?.content ?: ""
    val url = obj["html_url"]?.jsonPrimitive?.content ?: ""
    if (tag.isBlank() || url.isBlank()) return null
    return ReleaseInfo(tag, url, etag)
  }

  fun findBestRelease(releasesJson: String, tagPattern: Regex): ReleaseInfo? {
    return Json.parseToJsonElement(releasesJson).jsonArray
      .map { it.jsonObject }
      .firstOrNull { release ->
        release["draft"]?.jsonPrimitive?.booleanOrNull != true &&
          (release["tag_name"]?.jsonPrimitive?.content ?: "").let { it.isNotBlank() && tagPattern.matches(it) } &&
          (release["html_url"]?.jsonPrimitive?.content ?: "").isNotBlank()
      }?.let { release ->
        val tag = release["tag_name"]?.jsonPrimitive?.content ?: return@let null
        val url = release["html_url"]?.jsonPrimitive?.content ?: return@let null
        ReleaseInfo(tagName = tag, htmlUrl = url, etag = null)
      }
  }

  fun findCrossChannelRelease(releasesJson: String, ownPattern: Regex): ReleaseInfo? {
    return Json.parseToJsonElement(releasesJson).jsonArray
      .map { it.jsonObject }
      .firstOrNull { release ->
        release["draft"]?.jsonPrimitive?.booleanOrNull != true &&
          (release["tag_name"]?.jsonPrimitive?.content ?: "").let { tag ->
            tag.isNotBlank() && !ownPattern.matches(tag) && SemVer.parse(tag) != null
          } &&
          (release["html_url"]?.jsonPrimitive?.content ?: "").isNotBlank()
      }?.let { release ->
        val tag = release["tag_name"]?.jsonPrimitive?.content ?: return@let null
        val url = release["html_url"]?.jsonPrimitive?.content ?: return@let null
        ReleaseInfo(tagName = tag, htmlUrl = url, etag = null)
      }
  }
}
