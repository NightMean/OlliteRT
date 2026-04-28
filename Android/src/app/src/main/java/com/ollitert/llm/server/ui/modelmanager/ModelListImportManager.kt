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

package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import android.net.Uri
import android.util.Log
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.fetchBounded
import com.ollitert.llm.server.data.MAX_ALLOWLIST_RESPONSE_BYTES
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_FILENAME
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_OFFICIAL_FILENAME
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelAllowlistJson
import com.ollitert.llm.server.data.Repository
import com.ollitert.llm.server.data.repoCacheFilename
import java.io.File
import java.util.UUID

private const val TAG = "OlliteRT.ListImport"

/**
 * Handles model list import operations: URL fetch, file read, JSON validation,
 * disk cache persistence, and repository registration.
 *
 * Separated from ModelManagerViewModel to isolate I/O-heavy import logic
 * from UI state management and download orchestration.
 */
class ModelListImportManager(
  private val context: Context,
  private val dataStoreRepository: DataStoreRepository,
  private val allowlistLoader: ModelAllowlistLoader,
) {

  /**
   * Import a model list from a URL. Returns an error message or null on success.
   * Must be called from a background thread (performs network I/O).
   */
  suspend fun importFromUrl(url: String): String? {
    val normalizedUrl = url.trim().trimEnd('/')
    val parsed = try { java.net.URL(normalizedUrl) } catch (_: Exception) {
      return context.getString(R.string.import_model_list_error_invalid_url)
    }
    if (parsed.protocol != "https" && parsed.protocol != "http") {
      return context.getString(R.string.import_model_list_error_invalid_url)
    }
    val body = try {
      fetchBounded(normalizedUrl, userAgent = "OlliteRT-ModelListImport")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to fetch model list from URL", e)
      null
    }
    if (body.isNullOrBlank()) return context.getString(R.string.import_model_list_error_fetch)
    return validateAndSave(body, repoUrl = normalizedUrl)
  }

  /**
   * Import a model list from a content URI (file picker). Returns an error message or null on success.
   * Must be called from a background thread (performs file I/O).
   */
  suspend fun importFromUri(uri: Uri): String? {
    val fileSize = try {
      context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    } catch (_: Exception) { 0L }
    if (fileSize > MAX_ALLOWLIST_RESPONSE_BYTES) {
      return context.getString(
        R.string.import_model_list_error_too_large,
        "${MAX_ALLOWLIST_RESPONSE_BYTES / 1024 / 1024}MB",
      )
    }
    val body = try {
      context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to read model list file", e)
      return context.getString(R.string.import_model_list_error_read)
    }
    if (body.isNullOrBlank()) return context.getString(R.string.import_model_list_error_empty)
    return validateAndSave(body, repoUrl = "")
  }

  /**
   * Migrate legacy single-file disk cache to per-repo filename format.
   * Safe to call multiple times — no-ops if already migrated.
   */
  fun migrateDiskCacheIfNeeded() {
    val dir = context.getExternalFilesDir(null) ?: return
    val officialFile = File(dir, MODEL_ALLOWLIST_OFFICIAL_FILENAME)
    val legacyFile = File(dir, MODEL_ALLOWLIST_FILENAME)
    if (!officialFile.exists() && legacyFile.exists()) {
      if (legacyFile.renameTo(officialFile)) {
        Log.d(TAG, "Migrated disk cache: $MODEL_ALLOWLIST_FILENAME → $MODEL_ALLOWLIST_OFFICIAL_FILENAME")
      } else {
        Log.e(TAG, "Failed to migrate disk cache")
      }
    }
  }

  private suspend fun validateAndSave(body: String, repoUrl: String): String? {
    val allowlist: ModelAllowlist
    try {
      allowlist = ModelAllowlistJson.decode(body)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse model list JSON", e)
      return context.getString(R.string.import_model_list_error_invalid_json)
    }

    if (allowlist.models.isEmpty()) return context.getString(R.string.import_model_list_error_no_models)

    if (allowlist.schemaVersion > ModelAllowlist.SUPPORTED_SCHEMA_VERSION) {
      return context.getString(R.string.import_model_list_error_newer_version)
    }

    val repoId = UUID.randomUUID().toString()
    val cacheFilename = repoCacheFilename(repoId)
    allowlistLoader.saveToDisk(body, cacheFilename)
    if (allowlistLoader.readFromDiskCache(cacheFilename) == null) {
      Log.w(TAG, "Disk cache write failed for imported model list")
      return context.getString(R.string.import_model_list_error_read)
    }
    val repoName = allowlist.sourceName.ifEmpty {
      context.getString(R.string.import_model_list_default_name)
    }
    dataStoreRepository.addRepository(
      Repository(
        id = repoId,
        url = repoUrl,
        enabled = true,
        isBuiltIn = false,
        contentVersion = allowlist.contentVersion,
        lastRefreshMs = System.currentTimeMillis(),
        lastError = "",
        name = repoName,
        description = allowlist.sourceDescription,
        iconUrl = allowlist.sourceIconUrl,
        modelCount = allowlist.models.size,
      )
    )
    Log.d(TAG, "Imported model list as repo '$repoName' ($repoId) with ${allowlist.models.size} models")
    return null
  }
}
