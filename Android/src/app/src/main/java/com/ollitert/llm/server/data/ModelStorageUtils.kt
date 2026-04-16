/*
 * Copyright 2025 Google LLC
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

import android.util.Log
import java.io.File

private const val TAG = "ModelStorageUtils"

// Minimum free storage (in bytes) required before attempting to load a model via LiteRT Engine.
// LiteRT needs scratch space for memory-mapping, temp files, and GPU buffer allocation.
const val MIN_ENGINE_STORAGE_BYTES = 500L * 1024 * 1024

/**
 * Deletes stale .tmp files left by interrupted or crashed model imports to reclaim storage.
 * Safe to call on every app start — no-ops if the imports directory doesn't exist.
 */
fun cleanupStaleImportTmpFiles(externalFilesDir: File?) {
  try {
    val importsDir = File(externalFilesDir ?: return, IMPORTS_DIR)
    if (!importsDir.exists()) return
    val tmpFiles = importsDir.listFiles { _, name -> name.endsWith(".tmp") } ?: return
    for (file in tmpFiles) {
      Log.i(TAG, "Cleaning up stale import temp file: ${file.name} (${file.length() / (1024 * 1024)}MB)")
      file.delete()
    }
  } catch (e: Exception) {
    Log.w(TAG, "Failed to clean up stale import temp files: ${e.message}")
  }
}
