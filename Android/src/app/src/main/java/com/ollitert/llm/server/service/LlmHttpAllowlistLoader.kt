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

import com.ollitert.llm.server.data.AllowedModel
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
  private val packageName: String,
  private val assetReader: () -> String? = { null },
) {
  private var cached: ModelAllowlist? = null
  var lastSource: String = "unknown"
    private set

  /** Returns the current list of allowed models, falling back to cache on error. */
  fun load(): List<AllowedModel> {
    val fresh = readFromFiles()
    if (fresh != null && fresh.models.isNotEmpty()) {
      cached = fresh
      return fresh.models
    }
    if (lastSource == "unknown") lastSource = "empty"
    return cached?.models ?: emptyList()
  }

  private fun readFromFiles(): ModelAllowlist? {
    return try {
      var file = externalFilesDir?.let { File(it, "model_allowlist.json") }
      // Check both exists AND non-empty — a 0-byte file can be left behind
      // when a write is interrupted by a crash (e.g. disk full during model
      // switch). Without the length check, the empty file shadows the bundled
      // asset fallback, causing "model not found" errors on restart.
      if (file != null && file.exists() && file.length() > 0L) {
        val allowlist = ModelAllowlistJson.decode(file.readText())
        lastSource = "external:${file.absolutePath}"
        return allowlist
      }
      val assetText = assetReader() ?: return null
      val allowlist = ModelAllowlistJson.decode(assetText)
      lastSource = "asset"
      allowlist
    } catch (e: Exception) {
      lastSource = "error"
      null
    }
  }
}
