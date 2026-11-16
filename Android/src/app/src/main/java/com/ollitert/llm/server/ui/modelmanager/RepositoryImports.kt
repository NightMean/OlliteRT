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

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.data.allowlist.DiscoveredModel
import com.ollitert.llm.server.data.allowlist.DiscoveredModels
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.allowlist.huggingFaceFileUrl
import com.ollitert.llm.server.data.allowlist.modelScopeFileUrl
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

internal data class RepositoryImport(val record: DiscoveredModel, val model: Model?, val status: ModelDownloadStatusType?)
internal data class RepositoryImportsState(val items: List<RepositoryImport> = emptyList(), val ready: Boolean = false, val error: Boolean = false)

internal fun matchingImportedRepositories(imports: List<RepositoryImport>, query: String): Map<String, List<RepositoryImport>> =
  imports.filter { it.record.repository.contains(query.trim(), ignoreCase = true) }.groupBy { it.record.repository }

internal fun importedFileUrl(imports: List<RepositoryImport>, repository: String, path: String, site: ModelScopeSite? = null): String? {
  val record = imports.firstOrNull { it.record.repository == repository && it.record.path == path &&
    (site == null || it.record.modelScopeSite == site) }?.record ?: return null
  return when (record.provider) {
    DiscoveredProvider.HUGGING_FACE -> huggingFaceFileUrl(repository, record.revision, path)
    DiscoveredProvider.MODELSCOPE -> modelScopeFileUrl(repository, record.revision, path, record.modelScopeSite)
  }
}

internal fun selectRepositoryPath(current: String?, paths: List<String>, imports: List<RepositoryImport>): String? {
  // An explicit selection belongs to the user even if the file is already imported or has gone away.
  if (current != null) return current
  val unavailable = imports.mapTo(mutableSetOf()) { it.record.path }
  return paths.firstOrNull { it !in unavailable }
}

internal fun shouldLoadRepositoryPage(lastVisible: Int, importedCount: Int, availableCount: Int): Boolean {
  if (availableCount == 0) return true
  val firstAvailable = (if (importedCount > 0) importedCount + 1 else 0) + 1
  return lastVisible in firstAvailable until firstAvailable + availableCount &&
    lastVisible >= firstAvailable + (availableCount - 3).coerceAtLeast(0)
}

@Composable
internal fun rememberRepositoryImports(manager: ModelManagerViewModel, provider: DiscoveredProvider, retry: Int = 0): RepositoryImportsState {
  val context = LocalContext.current
  val state by manager.uiState.collectAsStateWithLifecycle()
  val records by produceState<Result<List<DiscoveredModel>>?>(null, context, provider, state.models, state.storageUpdateTrigger, retry) {
    value = null
    value = withContext(Dispatchers.IO) {
      try {
        Result.success(context.getExternalFilesDir(null)?.let { DiscoveredModels(it, provider).read() } ?: error("Model storage unavailable"))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e("OlliteRT.Repository", "Could not read imported repositories", e)
        Result.failure(e)
      }
    }
  }
  val models = state.models.associateBy { it.name }
  val current = records ?: return RepositoryImportsState()
  val items = current.getOrNull() ?: return RepositoryImportsState(error = true)
  return RepositoryImportsState(ready = true, items = items.map { record ->
    val name = record.toAllowedModel().name
    RepositoryImport(record, models[name], state.modelDownloadStatus[name]?.status)
  })
}
