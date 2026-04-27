/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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
import android.util.Log
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_FILENAME
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_TEST_FILENAME
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelAllowlistJson
import java.io.File

private const val TAG = "OlliteRT.AllowLoad"

/**
 * Loads the model allowlist from disk cache or bundled assets.
 * Pure I/O layer — does not touch ViewModel state or coroutine scopes.
 * The ViewModel calls these methods from its own coroutine scope.
 *
 * Loading chain: test file → disk cache → bundled asset.
 * Network fetching is handled by [RepositoryManager], not this class.
 */
class ModelAllowlistLoader(
  private val context: Context,
  private val externalFilesDir: File?,
) : AllowlistLoader {

  /** Try to load the test allowlist from /data/local/tmp. */
  override fun readTestAllowlist(): ModelAllowlist? {
    return readFromDisk(fileName = MODEL_ALLOWLIST_TEST_FILENAME)
  }

  /** Save allowlist JSON to disk for future offline use. */
  override fun saveToDisk(content: String, filename: String) {
    try {
      Log.d(TAG, "Saving model allowlist to disk: $filename")
      val file = File(externalFilesDir, filename)
      val tmpFile = File(externalFilesDir, "$filename.tmp")
      tmpFile.writeText(content)
      if (!tmpFile.renameTo(file)) {
        Log.e(TAG, "Failed to rename tmp file to $filename")
        tmpFile.delete()
      }
      Log.d(TAG, "Done: saving model allowlist to disk.")
    } catch (e: Exception) {
      Log.e(TAG, "failed to write model allowlist to disk", e)
    }
  }

  /** Read allowlist from disk cache. */
  override fun readFromDiskCache(filename: String): ModelAllowlist? {
    return readFromDisk(filename)
  }

  /** Read allowlist from APK's bundled assets (fallback for fresh install). */
  override fun readFromAssets(): ModelAllowlist? {
    return try {
      val content = context.assets.open(MODEL_ALLOWLIST_FILENAME).bufferedReader().use { it.readText() }
      Log.d(TAG, "Loaded bundled model allowlist from assets")
      ModelAllowlistJson.decode(content)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to read bundled model allowlist from assets", e)
      null
    }
  }

  private fun readFromDisk(fileName: String): ModelAllowlist? {
    try {
      Log.d(TAG, "Reading model allowlist from disk: $fileName")
      val baseDir =
        if (fileName == MODEL_ALLOWLIST_TEST_FILENAME) File("/data/local/tmp") else externalFilesDir
      val file = File(baseDir, fileName)
      if (file.exists()) {
        val content = file.readText()
        return ModelAllowlistJson.decode(content)
      }
    } catch (e: Exception) {
      Log.e(TAG, "failed to read model allowlist from disk", e)
      return null
    }
    return null
  }
}
