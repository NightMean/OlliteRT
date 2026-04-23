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

import androidx.activity.result.ActivityResult
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_OFFICIAL_FILENAME
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelDownloadStatus
import net.openid.appauth.AuthorizationService

/**
 * Contracts for the three managers extracted from ModelManagerViewModel.
 * Defined as interfaces so the ViewModel can be tested with fakes that
 * don't hit real OAuth endpoints, network, or file system.
 */

/** Manages HuggingFace OAuth token lifecycle. */
interface TokenManager {
  val authService: AuthorizationService
  var curAccessToken: String
  fun dispose()
  suspend fun getTokenStatusAndData(): TokenStatusAndData
  fun handleAuthResult(result: ActivityResult, onTokenRequested: (TokenRequestResult) -> Unit)
  fun saveAccessToken(accessToken: String, refreshToken: String, expiresAt: Long)
}

/** Loads the model allowlist from disk cache or bundled assets. */
interface AllowlistLoader {
  fun readTestAllowlist(): ModelAllowlist?
  fun saveToDisk(content: String, filename: String = MODEL_ALLOWLIST_OFFICIAL_FILENAME)
  fun readFromDiskCache(filename: String = MODEL_ALLOWLIST_OFFICIAL_FILENAME): ModelAllowlist?
  fun readFromAssets(): ModelAllowlist?
}

/** Manages model file operations on the local file system. */
interface ModelFileOps {
  fun isFileInExternalFilesDir(fileName: String): Boolean
  fun deleteFileFromExternalFilesDir(fileName: String)
  fun deleteFilesFromImportDir(fileName: String)
  fun deleteDirFromExternalFilesDir(dir: String)
  fun isModelPartiallyDownloaded(model: Model): Boolean
  fun isModelDownloaded(model: Model): Boolean
  fun getModelDownloadStatus(model: Model): ModelDownloadStatus
}
