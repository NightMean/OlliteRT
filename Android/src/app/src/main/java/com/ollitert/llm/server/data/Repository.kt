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

import com.ollitert.llm.server.proto.RepositoryEntry as RepositoryEntryProto

fun repoCacheFilename(id: String): String = "${MODEL_ALLOWLIST_CACHE_PREFIX}$id.json"

data class Repository(
  val id: String,
  val url: String,
  val enabled: Boolean,
  val isBuiltIn: Boolean,
  val contentVersion: Int,
  val lastRefreshMs: Long,
  val lastError: String,
  val name: String = "",
  val description: String = "",
  val iconUrl: String = "",
  val modelCount: Int? = null,
  val hiddenModelCount: Int = 0,
) {
  val cacheFilename: String
    get() = repoCacheFilename(id)

  fun toProto(): RepositoryEntryProto =
    RepositoryEntryProto.newBuilder()
      .setId(id)
      .setUrl(url)
      .setEnabled(enabled)
      .setIsBuiltIn(isBuiltIn)
      .setContentVersion(contentVersion)
      .setLastRefreshMs(lastRefreshMs)
      .setLastError(lastError)
      .build()

  companion object {
    fun fromProto(proto: RepositoryEntryProto): Repository =
      Repository(
        id = proto.id,
        url = proto.url,
        enabled = proto.enabled,
        isBuiltIn = proto.isBuiltIn,
        contentVersion = proto.contentVersion,
        lastRefreshMs = proto.lastRefreshMs,
        lastError = proto.lastError,
      )
  }
}
