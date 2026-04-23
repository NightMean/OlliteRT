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

package com.ollitert.llm.server.data

import java.net.URI

private val GITHUB_HOSTS = setOf("github.com", "raw.githubusercontent.com")

fun deriveRepositoryName(url: String): String {
  if (url.isBlank()) return ""
  val uri = try { URI(url) } catch (_: Exception) { return url }
  val host = uri.host ?: return url
  if (host in GITHUB_HOSTS) {
    val segments = uri.path.orEmpty().trim('/').split('/')
    if (segments.size >= 2) return "${segments[0]}/${segments[1]}"
  }
  return host
}
