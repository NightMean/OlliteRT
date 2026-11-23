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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.allowlist.ModelScopeSort
import com.ollitert.llm.server.data.allowlist.ModelScopeModel
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.allowlist.modelScopeFileUrl
import com.ollitert.llm.server.ui.modelmanager.components.ModelScopeAccessDialog
import com.ollitert.llm.server.ui.common.OlliteSearchBar
import com.ollitert.llm.server.ui.common.UrlInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelScopeExploreScreen(onBack: () -> Unit, onNavigateToProviderTokens: () -> Unit, modelManagerViewModel: ModelManagerViewModel) {
  val viewModel: ModelScopeExploreViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val modelError = if (state.showModelError) stringResource(
    if (state.rateLimited) R.string.modelscope_rate_limited else if (state.accessDenied) R.string.modelscope_access_denied else R.string.modelscope_files_error
  ) else null
  val errorSnackbar = rememberRepositoryErrorSnackbar(modelError, viewModel::retryError, viewModel::dismissModelError)
  var importsRetry by remember { mutableStateOf(0) }
  val importsState = rememberRepositoryImports(modelManagerViewModel, DiscoveredProvider.MODELSCOPE, importsRetry)
  val imports = importsState.items
  val importedGroups = matchingImportedRepositories(imports.filter { it.record.modelScopeSite == state.site }, state.query)
  var showLink by rememberSaveable { mutableStateOf(false) }
  val listState = rememberLazyListState()
  LaunchedEffect(state.query, state.sort, state.site) { listState.scrollToItem(0) }
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
      title = stringResource(R.string.modelscope_explore_title),
      linkLabel = stringResource(R.string.modelscope_link_title),
      linkEnabled = !state.saving && importsState.ready,
      backEnabled = !state.saving,
      onOpenLink = { viewModel.beginLink(); showLink = true },
      onBack = onBack,
    )
  }) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
      OlliteSearchBar(query = state.query, onQueryChange = viewModel::search,
        placeholderRes = R.string.modelscope_explore_search, clearContentDescriptionRes = R.string.models_clear_search)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ModelScopeSite.INTERNATIONAL to R.string.modelscope_site_international,
          ModelScopeSite.CHINA to R.string.modelscope_site_china).forEach { (site, label) ->
          ModelFilterChip(label = stringResource(label), selected = state.site == site, onClick = { viewModel.selectSite(site) })
        }
      }
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ModelScopeSort.DEFAULT to R.string.modelscope_sort_default,
          ModelScopeSort.DOWNLOADS to R.string.hf_explore_downloads,
          ModelScopeSort.LIKES to R.string.hf_explore_likes,
          ModelScopeSort.RECENT to R.string.hf_explore_recent).forEach { (sort, label) ->
          ModelFilterChip(label = stringResource(label), selected = state.sort == sort, onClick = { viewModel.sort(sort) })
        }
      }
      if (importsState.error) ModelListError(title = stringResource(R.string.models_failed_to_load),
        message = stringResource(R.string.repository_imports_error), onRetry = { importsRetry++ }, verticalPadding = 16.dp)
      RepositoryLoadingIndicator((!importsState.ready && !importsState.error) || state.loading)
      if (state.error && state.details == null && state.failedModel == null) {
        ModelListError(
          title = stringResource(if (state.failedModel == null) R.string.models_failed_to_load else R.string.modelscope_files_failed),
          message = stringResource(if (state.rateLimited) R.string.modelscope_rate_limited else if (state.accessDenied) R.string.modelscope_access_denied else if (state.failedModel == null) R.string.modelscope_explore_error else R.string.modelscope_files_error),
          onRetry = viewModel::retryError,
          verticalPadding = if (state.models.isEmpty()) 48.dp else 16.dp,
        )
      }
      if (importsState.ready) {
        RepositoryExploreList(
          listState = listState,
          remoteModels = remember(state.models) { state.models.map { it.toRepositorySummary() } },
          importedIds = importedGroups.keys,
          complete = !state.hasMore && !state.loading && !state.loadingMore && !state.searchLimitReached &&
            !state.paginationError && !state.error,
          openingModelId = state.openingModelId,
          loadingMore = state.loadingMore,
          mayLoadMore = state.page > 0 && state.hasMore && !state.loading && !state.loadingMore &&
            state.openingModelId == null && !state.paginationError && !state.error && state.details == null &&
            !state.saving && !showLink,
          pageKey = listOf(state.site, state.query, state.sort, state.page),
          emptyMessage = if (!state.error && !state.loading && !state.loadingMore && !state.hasMore && !state.paginationError) {
            stringResource(if (state.query.isNotBlank()) R.string.no_models_match_search else R.string.hf_explore_empty)
          } else null,
          onLoadMore = viewModel::loadMore,
          onOpen = { id, imported ->
            val remote = state.models.firstOrNull { it.id == id }
            if (imported) viewModel.open(remote ?: ModelScopeModel(id, downloads = -1), hasLocalImports = true)
            else remote?.let { viewModel.open(it) }
          },
          modifier = Modifier.weight(1f),
          loadingMoreDescription = stringResource(R.string.modelscope_loading_more),
        ) {
          if (state.paginationError) item(key = "page_error") {
            ModelListError(title = stringResource(if (state.paginationStalled) R.string.modelscope_page_empty else R.string.modelscope_more_failed),
              message = stringResource(if (state.paginationStalled) R.string.modelscope_page_continue else if (state.rateLimited) R.string.modelscope_rate_limited else if (state.accessDenied) R.string.modelscope_access_denied else R.string.modelscope_more_error),
              onRetry = viewModel::retryLoadMore, verticalPadding = 16.dp,
              retryLabel = stringResource(if (state.paginationStalled) R.string.modelscope_continue else R.string.retry))
          }
          if (state.searchLimitReached) item { Text(stringResource(R.string.modelscope_search_limit)) }
        }
      }
    }
  }
  if (showLink) {
    UrlInputDialog(title = stringResource(R.string.modelscope_link_title), label = stringResource(R.string.modelscope_link_label),
      helperText = stringResource(R.string.modelscope_link_help), confirmText = stringResource(R.string.hf_link_open),
      loadingText = stringResource(R.string.hf_link_loading), isLoading = state.linkLoading,
      error = if (state.linkError) stringResource(if (state.rateLimited) R.string.modelscope_rate_limited else if (state.accessDenied) R.string.modelscope_access_denied else R.string.modelscope_link_error) else null,
      onDismiss = { showLink = false; viewModel.closeLink() }, onConfirm = viewModel::openLink)
  }
  state.details?.takeIf { state.addedModelName == null }?.let { details ->
    RepositoryModelDialog(
      model = details.toRepositorySummary(state.files.size.takeUnless { state.detailsOffline }),
      remoteFiles = state.files.map { RepositoryFileVariant(it.path, it.size) },
      managementOnly = state.detailsOffline,
      initialPath = state.selectedPath,
      loading = state.saving,
      error = state.error,
      alreadyAdded = state.alreadyAdded,
      onDismiss = viewModel::closeDetails,
      onAdd = { path, info -> state.files.firstOrNull { it.path == path }?.let { viewModel.add(it, info) } },
      manager = modelManagerViewModel,
      imports = imports.filter { it.record.repository == details.id && it.record.modelScopeSite == state.detailsSite },
      importsReady = importsState.ready,
      importsError = importsState.error,
      onRetryImports = { importsRetry++ },
      repositoryUrl = "${state.detailsSite.baseUrl}/models/${details.id}",
      fileUrl = { path -> importedFileUrl(imports, details.id, path, state.detailsSite)
        ?: state.files.firstOrNull { it.path == path }?.let { modelScopeFileUrl(details.id, it.revision, path, state.detailsSite) } },
    )
  }
  if (state.showAccessDialog) {
    ModelScopeAccessDialog(
      site = state.accessDeniedSite,
      tokenMissing = !viewModel.hasToken(state.accessDeniedSite),
      onNavigateToProviderTokens = onNavigateToProviderTokens,
      onDismiss = viewModel::acknowledgeAccessDenied,
    )
  }
}

private fun ModelScopeModel.toRepositorySummary(variantCount: Int? = null) =
  RepositoryModelSummary(id, downloads, likes, lastModified, variantCount)
