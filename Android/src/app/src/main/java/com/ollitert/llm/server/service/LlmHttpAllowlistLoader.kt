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
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_CACHE_PREFIX
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_FILENAME
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_OFFICIAL_FILENAME
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
  private val enabledCacheFilenames: () -> Set<String>? = { null },
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
    if (externalFilesDir == null) {
      val assetContent = assetReader()
      if (assetContent != null) {
        try {
          val decoded = ModelAllowlistJson.decode(assetContent)
          lastSource = "asset"
          lastContentVersion = decoded.contentVersion
          return decoded
        } catch (_: Exception) { /* fall through */ }
      }
      lastSource = "empty"
      return null
    }

    // Migrate legacy single-file cache to per-repo naming.
    // Must run here too — service may start before the ViewModel.
    val officialFile = File(externalFilesDir, MODEL_ALLOWLIST_OFFICIAL_FILENAME)
    val legacyFile = File(externalFilesDir, MODEL_ALLOWLIST_FILENAME)
    if (!officialFile.exists() && legacyFile.exists()) {
      if (!legacyFile.renameTo(officialFile)) {
        legacyFile.copyTo(officialFile, overwrite = true)
        legacyFile.delete()
      }
    }

    val allModels = mutableListOf<AllowedModel>()
    var bestContentVersion = 0

    // Process Official first (hardcoded filename), then remaining files alphabetically.
    // When enabledCacheFilenames is provided, only load cache files for enabled repos.
    val enabled = enabledCacheFilenames()
    val otherFiles = externalFilesDir.listFiles { _, name ->
      name.startsWith(MODEL_ALLOWLIST_CACHE_PREFIX) && name.endsWith(".json") && name != MODEL_ALLOWLIST_OFFICIAL_FILENAME
          && (enabled == null || name in enabled)
    }?.sorted() ?: emptyList()

    val filesToProcess = buildList {
      if (officialFile.exists() && officialFile.length() > 0L) add(officialFile)
      addAll(otherFiles.filter { it.length() > 0L })
    }

    // If no disk files but we have a bundled asset, use it
    if (filesToProcess.isEmpty()) {
      val assetContent = assetReader()
      if (assetContent != null) {
        try {
          val decoded = ModelAllowlistJson.decode(assetContent)
          lastSource = "asset"
          lastContentVersion = decoded.contentVersion
          return decoded
        } catch (_: Exception) {
          lastSource = "error"
          return null
        }
      }
      lastSource = "empty"
      return null
    }

    // Also check bundled asset for Official (regression guard)
    var bundledAllowlist: ModelAllowlist? = null
    try {
      val assetContent = assetReader()
      if (assetContent != null) bundledAllowlist = ModelAllowlistJson.decode(assetContent)
    } catch (_: Exception) { }

    val seenModelIds = mutableSetOf<String>()
    for (file in filesToProcess) {
      try {
        var decoded = ModelAllowlistJson.decode(file.readText())

        // For Official file: use bundled asset if it has higher contentVersion
        if (file.name == MODEL_ALLOWLIST_OFFICIAL_FILENAME && bundledAllowlist != null) {
          if (bundledAllowlist.contentVersion > decoded.contentVersion) {
            decoded = bundledAllowlist
          }
        }

        if (decoded.contentVersion > bestContentVersion) {
          bestContentVersion = decoded.contentVersion
        }

        for (model in decoded.models) {
          if (model.modelId !in seenModelIds) {
            seenModelIds.add(model.modelId)
            allModels.add(model)
          }
        }
      } catch (_: Exception) {
        // Skip malformed files — no android.util.Log in this JVM-testable class
      }
    }

    lastSource = "external:${filesToProcess.first().absolutePath}"
    lastContentVersion = bestContentVersion
    return ModelAllowlist(
      schemaVersion = 1,
      contentVersion = bestContentVersion,
      models = allModels,
    )
  }
}
