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
import java.net.URISyntaxException
import java.net.URLDecoder

internal data class ModelScopeLink(val repository: String, val revision: String? = null, val path: String? = null,
  val site: ModelScopeSite? = null)

internal fun parseModelScopeLink(value: String): ModelScopeLink? {
  val text = value.trim()
  if (isValidHuggingFaceModelId(text)) return ModelScopeLink(text)
  val uri = try { URI(text) } catch (_: URISyntaxException) { return null }
  val site = when (uri.host?.lowercase()) {
    "modelscope.cn", "www.modelscope.cn" -> ModelScopeSite.CHINA
    "modelscope.ai", "www.modelscope.ai" -> ModelScopeSite.INTERNATIONAL
    else -> return null
  }
  if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null ||
    uri.port !in listOf(-1, 443) || uri.rawFragment != null) return null
  val parts = try {
    uri.rawPath?.trimEnd('/')?.split('/')?.map { URLDecoder.decode(it.replace("+", "%2B"), Charsets.UTF_8.name()) }
  } catch (_: IllegalArgumentException) { return null } ?: return null
  if (parts.any { '/' in it || '\\' in it }) return null
  if (parts.size == 7 && parts.take(4) == listOf("", "api", "v1", "models") && parts[6] == "repo") {
    val repository = "${parts[4]}/${parts[5]}"
    if (!isValidHuggingFaceModelId(repository)) return null
    val query = uri.rawQuery ?: return null
    val pairs = try { query.split('&').map { part ->
      val pair = part.split('=', limit = 2)
      if (pair.size != 2) return null
      URLDecoder.decode(pair[0], Charsets.UTF_8.name()) to URLDecoder.decode(pair[1], Charsets.UTF_8.name())
    } } catch (_: IllegalArgumentException) { return null }
    if (pairs.size != 2 || pairs.map { it.first }.toSet() != setOf("Revision", "FilePath")) return null
    val values = pairs.toMap()
    val revision = values["Revision"] ?: return null
    val path = values["FilePath"] ?: return null
    if (!revision.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) || revision == "." || revision == ".." ||
      !isSupportedHuggingFaceFile(path)) return null
    return ModelScopeLink(repository, revision, path, site)
  }
  if (uri.rawQuery != null) return null
  if (parts.size < 4 || parts[0] != "" || parts[1] != "models") return null
  val repository = "${parts[2]}/${parts[3]}"
  if (!isValidHuggingFaceModelId(repository)) return null
  if (parts.size == 4 || (parts.size == 5 && parts[4] in setOf("summary", "files"))) return ModelScopeLink(repository, site = site)
  val revisionIndex = when {
    parts.size >= 7 && parts[4] == "resolve" -> 5
    parts.size >= 8 && parts[4] == "file" && parts[5] == "view" -> 6
    else -> return null
  }
  val revision = parts[revisionIndex]
  if (!revision.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) || revision == "." || revision == "..") return null
  val path = parts.drop(revisionIndex + 1).joinToString("/")
  if (!isSupportedHuggingFaceFile(path)) return null
  return ModelScopeLink(repository, revision, path, site)
}
