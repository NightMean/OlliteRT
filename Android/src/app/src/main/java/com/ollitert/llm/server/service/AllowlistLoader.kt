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
 * Loads the model allowlist from the filesystem. Merges models from the official
 * allowlist file, per-repo cache files, and the bundled asset (fallback).
 * Repos can be individually enabled/disabled via [enabledCacheFilenames].
 *
 * Caches the last successful load so callers always get a valid list even
 * when the external files are temporarily unavailable.
 */
class AllowlistLoader(
  private val externalFilesDir: File?,
  private val appVersionName: String = "",
  private val assetReader: () -> String? = { null },
  private val enabledCacheFilenames: () -> Set<String>? = { null },
  private val onError: (source: String, exception: Exception) -> Unit = { _, _ -> },
) {
  private val appVersion: SemVer? = SemVer.parse(appVersionName)
  @Volatile private var cached: ModelAllowlist? = null
  @Volatile var lastSource: String = "unknown"
    private set
  @Volatile var lastContentVersion: Int = 0
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
        } catch (e: Exception) { onError("asset", e) }
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

    // Official file is subject to the same enabled filter as third-party repos —
    // users can disable the built-in repo to hide its models from the server.
    val officialEnabled = enabled == null || MODEL_ALLOWLIST_OFFICIAL_FILENAME in enabled
    val filesToProcess = buildList {
      if (officialEnabled && officialFile.exists() && officialFile.length() > 0L) add(officialFile)
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
        } catch (e: Exception) {
          onError("asset", e)
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
    } catch (e: Exception) { onError("bundled-asset", e) }

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
      } catch (e: Exception) {
        onError(file.name, e)
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
