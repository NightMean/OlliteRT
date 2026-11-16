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
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal enum class ProviderToken { HUGGING_FACE, MODELSCOPE_AI, MODELSCOPE_CN }

internal enum class TokenCheck { TESTING, ACCEPTED, REJECTED, DENIED, RATE_LIMITED, UNAVAILABLE }

internal class ProviderTokenVerifier @Inject constructor() {
  internal var openConnection: (String) -> HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }

  fun verify(provider: ProviderToken, token: String): TokenCheck {
    val endpoint = when (provider) {
      ProviderToken.HUGGING_FACE -> "${GitHubConfig.HUGGINGFACE_BASE_URL}/api/whoami-v2"
      ProviderToken.MODELSCOPE_AI -> "${ModelScopeSite.INTERNATIONAL.baseUrl}/openapi/v1/users/me"
      ProviderToken.MODELSCOPE_CN -> "${ModelScopeSite.CHINA.baseUrl}/openapi/v1/users/me"
    }
    var connection: HttpURLConnection? = null
    return try {
      connection = openConnection(endpoint)
      connection.instanceFollowRedirects = false
      connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
      connection.readTimeout = HTTP_READ_TIMEOUT_MS
      connection.setRequestProperty("Authorization", "Bearer $token")
      when (connection.responseCode) {
        200 -> {
          // Identity responses are small; bound the read and do not trust HTTP 200 alone.
          val body = connection.inputStream.bufferedReader().use { it.readTextLimited() }
          if (body != null && hasIdentity(provider, body)) TokenCheck.ACCEPTED else TokenCheck.UNAVAILABLE
        }
        401 -> TokenCheck.REJECTED
        403 -> {
          val error = connection.errorStream?.bufferedReader()?.use { it.readTextLimited() }
          if (error?.contains("10013201001") == true) TokenCheck.RATE_LIMITED else TokenCheck.DENIED
        }
        429 -> TokenCheck.RATE_LIMITED
        else -> TokenCheck.UNAVAILABLE
      }
    } catch (_: IOException) {
      TokenCheck.UNAVAILABLE
    } catch (_: IllegalArgumentException) {
      TokenCheck.UNAVAILABLE
    } finally {
      connection?.disconnect()
    }
  }
}

private fun java.io.Reader.readTextLimited(): String? {
  val buffer = CharArray(4097)
  val result = StringBuilder()
  while (true) {
    val count = read(buffer)
    if (count < 0) return result.toString()
    result.append(buffer, 0, count)
    if (result.length > 65536) return null
  }
}

internal fun hasIdentity(provider: ProviderToken, body: String): Boolean {
  val root = try { Json.parseToJsonElement(body) as? JsonObject } catch (_: IllegalArgumentException) { null } ?: return false
  if (listOf("success", "Success").any { (root[it] as? JsonPrimitive)?.contentOrNull == "false" }) return false
  val profile = if (provider == ProviderToken.HUGGING_FACE) root else
    (root["data"] ?: root["Data"] ?: root) as? JsonObject ?: return false
  val fields = if (provider == ProviderToken.HUGGING_FACE) listOf("name") else
    listOf("Username", "username", "name", "preferred_username")
  return fields.any { key -> (profile[key] as? JsonPrimitive)?.let { it.isString && it.contentOrNull?.isNotBlank() == true } == true }
}
