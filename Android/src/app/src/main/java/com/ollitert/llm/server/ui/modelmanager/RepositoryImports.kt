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

import com.ollitert.llm.server.data.allowlist.DiscoveredModel
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.allowlist.huggingFaceFileUrl
import com.ollitert.llm.server.data.allowlist.modelScopeFileUrl
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.repository.DiscoveredCatalogSnapshot

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

internal fun repositoryImportsState(snapshot: DiscoveredCatalogSnapshot, state: ModelManagerUiState): RepositoryImportsState {
  if (snapshot.failure != null) return RepositoryImportsState(error = true)
  val models = state.models.associateBy { it.name }
  return RepositoryImportsState(ready = true, items = snapshot.entries.map { (record, allowed) ->
    RepositoryImport(record, models[allowed.name], state.modelDownloadStatus[allowed.name]?.status)
  })
}
