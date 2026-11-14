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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.common.UrlInputDialog
import com.ollitert.llm.server.ui.common.OlliteSearchBar
import com.ollitert.llm.server.data.allowlist.HuggingFaceFile
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.data.allowlist.HuggingFaceModel
import com.ollitert.llm.server.data.allowlist.HuggingFaceSort

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HuggingFaceExploreScreen(onBack: () -> Unit, modelManagerViewModel: ModelManagerViewModel) {
  val viewModel: HuggingFaceExploreViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  var showLink by remember { mutableStateOf(false) }
  LaunchedEffect(state.details) { if (state.details != null) showLink = false }
  BackHandler(enabled = state.saving) { }
  LaunchedEffect(state.addedModelName) {
    if (state.addedModelName != null) {
      modelManagerViewModel.loadModelAllowlist()
      onBack()
      viewModel.consumeImportedModel()
    }
  }
  Scaffold(topBar = {
    TopAppBar(
      title = { Text(stringResource(R.string.hf_explore_title)) },
      actions = {
        TooltipBox(
          positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
          tooltip = { PlainTooltip { Text(stringResource(R.string.hf_link_title)) } },
          state = rememberTooltipState(),
        ) {
          IconButton(onClick = { viewModel.beginLink(); showLink = true }, enabled = !state.saving) {
            Icon(Icons.Outlined.Link, contentDescription = stringResource(R.string.hf_link_title))
          }
        }
      },
      navigationIcon = {
        IconButton(onClick = onBack, enabled = !state.saving) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.hf_explore_back))
        }
      },
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
      if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
      if (state.error) {
        Text(stringResource(R.string.hf_explore_error), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.hf_explore_retry)) }
      } else if (!state.loading && state.models.isEmpty()) {
        Text(stringResource(R.string.hf_explore_empty), modifier = Modifier.padding(vertical = 20.dp))
      }
      LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(state.models, key = { it.id }) { model ->
          HuggingFaceModelCard(model = model, loading = state.openingModelId == model.id, onClick = { viewModel.open(model) })
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
    HuggingFaceModelDialog(model, state.selectedPath, state.loading, state.error, state.alreadyAdded, viewModel::closeDetails, viewModel::add)
  }
}

@Composable
internal fun HuggingFaceModelDialog(
  model: HuggingFaceModel,
  initialPath: String?,
  loading: Boolean,
  error: Boolean,
  alreadyAdded: Boolean,
  onDismiss: () -> Unit,
  onAdd: (HuggingFaceFile, ImportedModel) -> Unit,
  repositoryUrl: String = "${com.ollitert.llm.server.common.GitHubConfig.HUGGINGFACE_BASE_URL}/${model.id}",
  fileUrl: (HuggingFaceFile) -> String? = { file ->
    model.sha?.takeIf { it.matches(Regex("[a-fA-F0-9]{40}")) }?.let {
      com.ollitert.llm.server.data.allowlist.huggingFaceFileUrl(model.id, it, file.path)
    }
  },
) {
  var selectedPath by remember(model.id) { mutableStateOf(initialPath) }
  var configuringFile by remember(model.id) { mutableStateOf<HuggingFaceFile?>(null) }
  val selected = model.modelFiles.firstOrNull { it.path == selectedPath }
  if (configuringFile == null) {
    HuggingFaceModelDetailsSheet(
      model = model, selectedPath = selectedPath, loading = loading, error = error,
      alreadyAdded = alreadyAdded, onSelect = { selectedPath = it }, onDismiss = onDismiss,
      onContinue = { configuringFile = selected },
      repositoryUrl = repositoryUrl, fileUrl = fileUrl,
    )
  }
  configuringFile?.let { file ->
    val size = file.sizeBytes
    if (size != null) {
      ModelImportSettingsDialog(
        sourceFileName = file.path.substringAfterLast('/'), sourceFileSize = size,
        onDismiss = { configuringFile = null },
        onDone = { info -> configuringFile = null; onAdd(file, info) },
        remote = true,
      )
    }
  }

}
