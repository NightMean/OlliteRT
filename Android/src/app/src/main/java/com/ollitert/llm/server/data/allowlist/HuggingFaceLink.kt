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

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import com.ollitert.llm.server.common.GitHubConfig

internal data class HuggingFaceLink(val repository: String, val revision: String? = null, val path: String? = null)

internal fun huggingFaceFileUrl(repository: String, revision: String, path: String): String {
  require(isValidHuggingFaceModelId(repository))
  require(revision.matches(Regex("[a-fA-F0-9]{40}")))
  require(isSupportedHuggingFaceFile(path))
  val encodedPath = path.split('/').joinToString("/") {
    URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
  }
  return "${GitHubConfig.HUGGINGFACE_BASE_URL}/$repository/resolve/$revision/$encodedPath?download=true"
}

internal fun parseHuggingFaceLink(input: String): HuggingFaceLink? = try {
  val text = input.trim()
  if (isValidHuggingFaceModelId(text)) HuggingFaceLink(text) else {
    val uri = URI(text)
    require(uri.scheme.equals("https", true) && uri.host.equals("huggingface.co", true))
    require(uri.userInfo == null && (uri.port == -1 || uri.port == 443))
    val parts = uri.rawPath.trim('/').split('/').map {
      URLDecoder.decode(it.replace("+", "%2B"), Charsets.UTF_8.name())
    }
    require(parts.size >= 2 && parts.take(2).none { '/' in it })
    val repository = parts.take(2).joinToString("/")
    require(isValidHuggingFaceModelId(repository))
    if (parts.size == 2) HuggingFaceLink(repository) else {
      require(parts.size >= 5 && parts[2] in setOf("resolve", "blob"))
      val specialRef = parts.size >= 7 && parts[3] == "refs" && parts[4] == "pr" && parts[5].all { it.isDigit() }
      require(parts[3] != "refs" || specialRef)
      val revision = if (specialRef) parts.subList(3, 6).joinToString("/") else parts[3]
      val fileParts = parts.drop(if (specialRef) 6 else 4)
      require(revision.isNotBlank() && revision.none { it.isISOControl() } && revision.split('/').none { it.isBlank() || it == "." || it == ".." })
      require(fileParts.none { '/' in it })
      val path = fileParts.joinToString("/")
      require(isSupportedHuggingFaceFile(path))
      HuggingFaceLink(repository, revision, path)
    }
  }
} catch (_: Exception) { null }
