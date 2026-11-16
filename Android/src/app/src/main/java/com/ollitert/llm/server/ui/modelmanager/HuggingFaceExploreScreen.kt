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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.data.allowlist.huggingFaceFileUrl
import com.ollitert.llm.server.ui.common.UrlInputDialog
import com.ollitert.llm.server.ui.common.OlliteSearchBar
import com.ollitert.llm.server.data.allowlist.HuggingFaceModel
import com.ollitert.llm.server.data.allowlist.HuggingFaceSort
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HuggingFaceExploreScreen(onBack: () -> Unit, modelManagerViewModel: ModelManagerViewModel) {
  val viewModel: HuggingFaceExploreViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val modelError = if (state.error && state.details == null && state.failedModel != null) stringResource(R.string.hf_model_files_error) else null
  val errorSnackbar = rememberRepositoryErrorSnackbar(modelError, viewModel::retryError, viewModel::dismissModelError)
  var importsRetry by remember { mutableStateOf(0) }
  val importsState = rememberRepositoryImports(modelManagerViewModel, DiscoveredProvider.HUGGING_FACE, importsRetry)
  val imports = importsState.items
  val importedGroups = matchingImportedRepositories(imports, state.query)
  val available = state.models.filterNot { it.id in importedGroups }
  val listState = rememberLazyListState()
  var showLink by remember { mutableStateOf(false) }
  LaunchedEffect(state.query, state.sort) { listState.scrollToItem(0) }
  LaunchedEffect(listState, state.query, state.sort, available.size, importedGroups.size, importsState.ready,
    state.searchComplete, state.searchError, state.error, state.loading, state.loadingMore, state.openingModelId, state.details, state.saving, showLink) {
    if (importsState.ready && !state.searchComplete && !state.searchError && !state.error && !state.loading && !state.loadingMore &&
      state.openingModelId == null && state.details == null && !state.saving && !showLink) {
      if (available.isEmpty() && importedGroups.isEmpty()) viewModel.loadMore()
      else if (available.isEmpty()) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.any { it.index == importedGroups.size } }
          .distinctUntilChanged().collect { if (it) viewModel.loadMore() }
      } else {
        val first = (if (importedGroups.isNotEmpty()) importedGroups.size + 1 else 0) + 1
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull { it.index in first until first + available.size }?.index ?: -1 }
          .distinctUntilChanged().collect { if (shouldLoadRepositoryPage(it, importedGroups.size, available.size)) viewModel.loadMore() }
      }
    }
  }
  LaunchedEffect(state.details) { if (state.details != null) showLink = false }
  BackHandler(enabled = state.saving) { }
  LaunchedEffect(state.addedModelName) {
    if (state.addedModelName != null) {
      modelManagerViewModel.loadModelAllowlist()
      onBack()
      viewModel.consumeImportedModel()
    }
  }
  Scaffold(snackbarHost = { SnackbarHost(errorSnackbar) }, topBar = {
    RepositoryExploreTopBar(
      title = stringResource(R.string.hf_explore_title),
      linkLabel = stringResource(R.string.hf_link_title),
      linkEnabled = !state.saving && importsState.ready,
      backEnabled = !state.saving,
      onOpenLink = { viewModel.beginLink(); showLink = true },
      onBack = onBack,
    )
  }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
      OlliteSearchBar(
        query = state.query,
        onQueryChange = viewModel::search,
        placeholderRes = R.string.hf_explore_search,
        clearContentDescriptionRes = R.string.models_clear_search,
      )
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = listOf(
          HuggingFaceSort.TRENDING to R.string.hf_explore_trending,
          HuggingFaceSort.DOWNLOADS to R.string.hf_explore_downloads,
          HuggingFaceSort.LIKES to R.string.hf_explore_likes,
          HuggingFaceSort.RECENT to R.string.hf_explore_recent,
        )
        options.forEach { (sort, label) ->
          ModelFilterChip(label = stringResource(label), selected = state.sort == sort, onClick = { viewModel.sort(sort) })
        }
      }
      if (importsState.error) ModelListError(title = stringResource(R.string.models_failed_to_load),
        message = stringResource(R.string.repository_imports_error), onRetry = { importsRetry++ }, verticalPadding = 16.dp)
      RepositoryLoadingIndicator((!importsState.ready && !importsState.error) || state.loading)
      if (state.searchError) {
        Text(stringResource(if (state.searchLimitReached) R.string.hf_search_limit else if (state.searchInvalidPagination) R.string.hf_search_invalid_pagination else R.string.hf_explore_error), color = MaterialTheme.colorScheme.error)
        if (!state.searchLimitReached) TextButton(onClick = if (state.searchInvalidPagination) viewModel::refresh else viewModel::retrySearch) {
          Text(stringResource(if (state.searchInvalidPagination) R.string.hf_search_refresh else R.string.hf_explore_retry))
        }
      }
      if (importsState.ready) LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (importedGroups.isNotEmpty()) {
          item(key = "imported_header") { ModelSectionHeader(stringResource(R.string.repository_imported), importedGroups.size, Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp)) }
          items(importedGroups.keys.toList(), key = { "imported:$it" }) { id ->
            val remote = state.models.firstOrNull { it.id == id }
            val fallback = HuggingFaceModel(id = id, downloads = -1)
            RepositoryModelCard((remote ?: fallback).toRepositorySummary(), showStats = remote != null,
              loading = state.openingModelId == id, onClick = { viewModel.open(remote ?: fallback, fallback) })
          }
        }
        item(key = "available_header") { ModelSectionHeader(stringResource(R.string.repository_available), available.size,
          Modifier.padding(horizontal = 16.dp).padding(top = if (importedGroups.isEmpty()) 8.dp else 24.dp, bottom = 8.dp), complete = state.searchComplete) }
        if (state.searchComplete && !state.searchError && !state.error && !state.loading && state.models.isEmpty() && importedGroups.isEmpty()) item(key = "empty") {
          ModelListEmpty(stringResource(if (state.query.isNotBlank()) R.string.no_models_match_search else R.string.hf_explore_empty))
        }
        items(available, key = { "available:${it.id}" }) { model ->
          RepositoryModelCard(model = model.toRepositorySummary(), loading = state.openingModelId == model.id, onClick = { viewModel.open(model) })
        }
        if (state.loadingMore) item(key = "loading_more") {
          Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
      }
    }
  }
  if (showLink) {
    UrlInputDialog(
      title = stringResource(R.string.hf_link_title),
      label = stringResource(R.string.hf_link_label),
      helperText = stringResource(R.string.hf_link_help),
      confirmText = stringResource(R.string.hf_link_open),
      loadingText = stringResource(R.string.hf_link_loading),
      isLoading = state.linkLoading,
      error = if (state.linkError) stringResource(R.string.hf_link_error) else null,
      onDismiss = { showLink = false; viewModel.closeLink() },
      onConfirm = viewModel::openLink,
    )
  }
  state.details?.takeIf { state.addedModelName == null }?.let { model ->
    val files = if (state.detailsOffline) emptyList() else model.modelFiles
    val revision = model.sha?.takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }
    RepositoryModelDialog(
      model = model.toRepositorySummary(),
      remoteFiles = files.map { RepositoryFileVariant(it.path, it.sizeBytes) },
      managementOnly = state.detailsOffline,
      initialPath = state.selectedPath,
      loading = state.saving,
      error = state.error,
      alreadyAdded = state.alreadyAdded,
      onDismiss = viewModel::closeDetails,
      onAdd = { path, info -> files.firstOrNull { it.path == path }?.let { viewModel.add(it, info) } },
      manager = modelManagerViewModel,
      imports = imports.filter { it.record.repository == model.id },
      importsReady = importsState.ready,
      importsError = importsState.error,
      onRetryImports = { importsRetry++ },
      repositoryUrl = "${GitHubConfig.HUGGINGFACE_BASE_URL}/${model.id}",
      fileUrl = { path ->
        importedFileUrl(imports, model.id, path)
          ?: revision?.takeIf { files.any { file -> file.path == path } }?.let { huggingFaceFileUrl(model.id, it, path) }
      },
    )
  }
}

private fun HuggingFaceModel.toRepositorySummary() =
  RepositoryModelSummary(id, downloads, likes, lastModified, modelFiles.size)
