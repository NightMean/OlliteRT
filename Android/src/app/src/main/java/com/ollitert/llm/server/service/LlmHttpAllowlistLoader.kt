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

package com.ollitert.llm.server.service

import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.data.AllowedModel
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_FILENAME
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelAllowlistJson
import java.io.File

/**
 * Loads the model allowlist from the filesystem. Resolution order:
 * 1. [externalFilesDir]/model_allowlist.json
 * 2. [assetReader] (bundled asset, optional)
 *
 * Caches the last successful load so callers always get a valid list even
 * when the external file is temporarily unavailable.
 */
class LlmHttpAllowlistLoader(
  private val externalFilesDir: File?,
  private val appVersionName: String = "",
  private val assetReader: () -> String? = { null },
) {
  private val appVersion: SemVer? = SemVer.parse(appVersionName)
  private var cached: ModelAllowlist? = null
  var lastSource: String = "unknown"
    private set
  var lastContentVersion: Int = 0
    private set

  /** Returns the current list of allowed models, falling back to cache on error. */
  fun load(): List<AllowedModel> {
    val raw = readFromFiles()
    val fresh = if (raw != null && appVersion != null) raw.filterCompatible(appVersion) else raw
    if (fresh != null && fresh.models.isNotEmpty()) {
      cached = fresh
      lastContentVersion = fresh.contentVersion
      return fresh.models
    }
    if (lastSource == "unknown") lastSource = "empty"
    return cached?.models ?: emptyList()
  }

  private fun readFromFiles(): ModelAllowlist? {
    return try {
      val file = externalFilesDir?.let { File(it, MODEL_ALLOWLIST_FILENAME) }
      // Check both exists AND non-empty — a 0-byte file can be left behind
      // when a write is interrupted by a crash (e.g. disk full during model
      // switch). Without the length check, the empty file shadows the bundled
      // asset fallback, causing "model not found" errors on restart.
      val diskCache = if (file != null && file.exists() && file.length() > 0L) {
        ModelAllowlistJson.decode(file.readText())
      } else null

      val assetText = assetReader()
      val bundled = assetText?.let { ModelAllowlistJson.decode(it) }

      // Pick the source with the higher contentVersion so a stale disk cache
      // never shadows a newer bundled asset shipped with an app update.
      when {
        diskCache != null && bundled != null -> {
          if (bundled.contentVersion > diskCache.contentVersion) {
            lastSource = "asset"
            bundled
          } else {
            lastSource = "external:${file?.absolutePath ?: "unknown"}"
            diskCache
          }
        }
        diskCache != null -> {
          lastSource = "external:${file?.absolutePath ?: "unknown"}"
          diskCache
        }
        bundled != null -> {
          lastSource = "asset"
          bundled
        }
        else -> null
      }
    } catch (e: Exception) {
      lastSource = "error"
      null
    }
  }
}
