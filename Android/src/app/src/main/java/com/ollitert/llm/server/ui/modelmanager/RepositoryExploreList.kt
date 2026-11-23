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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R

private const val AVAILABLE_HEADER_KEY = "available_header"
private const val PAGINATION_LOOKAHEAD_ROWS = 3

private fun availableKey(id: String) = "available:$id"

internal fun repositoryPageTriggerKeys(importedCount: Int, availableIds: List<String>): Set<String> =
  if (availableIds.isEmpty() && importedCount > 0) setOf(AVAILABLE_HEADER_KEY)
  else availableIds.takeLast(PAGINATION_LOOKAHEAD_ROWS).mapTo(mutableSetOf(), ::availableKey)

@Composable
internal fun RepositoryExploreList(
  listState: LazyListState,
  remoteModels: List<RepositoryModelSummary>,
  importedIds: Set<String>,
  complete: Boolean,
  openingModelId: String?,
  loadingMore: Boolean,
  mayLoadMore: Boolean,
  pageKey: Any,
  emptyMessage: String?,
  onLoadMore: () -> Unit,
  onOpen: (id: String, imported: Boolean) -> Unit,
  modifier: Modifier = Modifier,
  loadingMoreDescription: String? = null,
  footer: LazyListScope.() -> Unit = {},
) {
  val remoteById = remember(remoteModels) { remoteModels.associateBy { it.id } }
  val available = remember(remoteModels, importedIds) { remoteModels.filterNot { it.id in importedIds } }
  val triggerKeys = remember(available, importedIds.size) {
    repositoryPageTriggerKeys(importedIds.size, available.map { it.id })
  }
  val loadMore by rememberUpdatedState(onLoadMore)
  LaunchedEffect(listState, mayLoadMore, pageKey, triggerKeys) {
    if (!mayLoadMore) return@LaunchedEffect
    if (triggerKeys.isEmpty()) {
      loadMore()
    } else {
      // Match row keys so inserting a header, error, or status row cannot start the wrong page.
      snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { item -> triggerKeys.any { it == item.key } } }
        .collect { nearEnd -> if (nearEnd) loadMore() }
    }
  }

  LazyColumn(state = listState, modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (importedIds.isNotEmpty()) {
      item(key = "imported_header") {
        ModelSectionHeader(
          stringResource(R.string.repository_imported), importedIds.size,
          Modifier.padding(horizontal = 16.dp).padding(vertical = 8.dp),
        )
      }
      items(importedIds.toList(), key = { "imported:$it" }) { id ->
        val remote = remoteById[id]
        RepositoryModelCard(
          model = remote ?: RepositoryModelSummary(id, downloads = -1, likes = 0, lastModified = null),
          showStats = remote != null,
          loading = openingModelId == id,
          onClick = { onOpen(id, true) },
        )
      }
    }
    item(key = AVAILABLE_HEADER_KEY) {
      ModelSectionHeader(
        stringResource(R.string.repository_available), available.size,
        Modifier.padding(horizontal = 16.dp).padding(top = if (importedIds.isEmpty()) 8.dp else 24.dp, bottom = 8.dp),
        complete = complete,
      )
    }
    if (emptyMessage != null && remoteModels.isEmpty() && importedIds.isEmpty()) {
      item(key = "empty") { ModelListEmpty(emptyMessage) }
    }
    items(available, key = { availableKey(it.id) }) { model ->
      RepositoryModelCard(model, loading = openingModelId == model.id, onClick = { onOpen(model.id, false) })
    }
    if (loadingMore) {
      item(key = "loading_more") {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(
            if (loadingMoreDescription == null) Modifier
            else Modifier.semantics { contentDescription = loadingMoreDescription },
          )
        }
      }
    }
    footer()
  }
}
