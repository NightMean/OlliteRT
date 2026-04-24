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

package com.ollitert.llm.server.ui.repositories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.Repository
import com.ollitert.llm.server.ui.common.SCREEN_CONTENT_MAX_WIDTH
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryListScreen(
  viewModel: RepositoryViewModel,
  onBackClick: (hasChanges: Boolean) -> Unit,
  onRepoClick: (repoId: String) -> Unit,
  downloadedModelRepoIds: Map<String, String> = emptyMap(),
  downloadingModelRepoIds: Map<String, String> = emptyMap(),
  onCancelDownload: (modelName: String) -> Unit = {},
  onSetTopBarTrailingContent: ((@Composable () -> Unit)?) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(Unit) { viewModel.loadRepositories() }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var hasChanges by rememberSaveable { mutableStateOf(false) }
  var showAddDialog by rememberSaveable { mutableStateOf(false) }
  var disableInfoRepoId by rememberSaveable { mutableStateOf<String?>(null) }
  var downloadingBlockRepoId by rememberSaveable { mutableStateOf<String?>(null) }
  var downloadingBlockModelNames by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
  var showInfoDialog by rememberSaveable { mutableStateOf(false) }

  DisposableEffect(Unit) {
    onSetTopBarTrailingContent {
      TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(stringResource(R.string.repo_info_button)) } },
        state = rememberTooltipState(),
      ) {
        IconButton(onClick = { showInfoDialog = true }) {
          Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(R.string.repo_info_button),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
    onDispose { }
  }

  BackHandler { onBackClick(hasChanges) }

  Box(modifier = modifier.fillMaxSize()) {
    if (uiState.isLoading && uiState.repositories.isEmpty()) {
      CircularProgressIndicator(
        modifier = Modifier
          .align(Alignment.Center)
          .size(40.dp),
      )
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = SCREEN_CONTENT_MAX_WIDTH),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Heading
      item(key = "heading") {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
          Text(
            text = stringResource(R.string.repo_list_heading),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = stringResource(R.string.repo_list_subheading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (uiState.repoCountWarning) {
        item(key = "limit_warning") {
          Text(
            text = stringResource(R.string.repo_limit_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
          )
        }
      }

      items(uiState.repositories, key = { it.id }) { repo ->
        RepositoryRow(
          repo = repo,
          onClick = { onRepoClick(repo.id) },
          onToggle = { enabled ->
            if (!enabled) {
              val downloading = viewModel.getDownloadingModelNamesForRepo(repo.id, downloadingModelRepoIds)
              if (downloading.isNotEmpty()) {
                downloadingBlockRepoId = repo.id
                downloadingBlockModelNames = downloading
                return@RepositoryRow
              }
              val count = viewModel.getDownloadedModelCountForRepo(repo.id, downloadedModelRepoIds)
              if (count > 0) {
                disableInfoRepoId = repo.id
                return@RepositoryRow
              }
            }
            viewModel.toggleRepo(repo.id, enabled)
            hasChanges = true
          },
        )
      }
    }

    val cdAddRepoFab = stringResource(R.string.repo_add)
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 16.dp),
    ) {
      TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(stringResource(R.string.repo_add)) } },
        state = rememberTooltipState(),
      ) {
        FloatingActionButton(
          onClick = { showAddDialog = true },
          containerColor = OlliteRTPrimary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier
            .semantics { contentDescription = cdAddRepoFab },
        ) {
          Icon(Icons.Filled.Add, contentDescription = null)
        }
      }
    }
  }

  // Add dialog
  if (showAddDialog) {
    AddRepositoryDialog(
      isAdding = uiState.isAdding,
      error = uiState.addDialogError,
      onDismiss = { showAddDialog = false },
      onAdd = { url ->
        viewModel.addRepository(url) { result ->
          when (result) {
            is AddRepoResult.Success -> {
              showAddDialog = false
              hasChanges = true
            }
            is AddRepoResult.Error -> {
              // Error is shown in the dialog via uiState.addDialogError
            }
          }
        }
      },
    )
  }

  // Info dialog when disabling a repo that has downloaded models
  disableInfoRepoId?.let { repoId ->
    val count = viewModel.getDownloadedModelCountForRepo(repoId, downloadedModelRepoIds)
    AlertDialog(
      onDismissRequest = { disableInfoRepoId = null },
      title = { Text(stringResource(R.string.repo_disable_title)) },
      text = {
        Text(stringResource(R.string.repo_disable_downloaded_info, count))
      },
      confirmButton = {
        TextButton(onClick = {
          viewModel.toggleRepo(repoId, false)
          hasChanges = true
          disableInfoRepoId = null
        }) {
          Text(stringResource(R.string.repo_disable_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { disableInfoRepoId = null }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  // Dialog when disabling a repo that has actively downloading models
  downloadingBlockRepoId?.let { repoId ->
    AlertDialog(
      onDismissRequest = { downloadingBlockRepoId = null },
      title = { Text(stringResource(R.string.repo_disable_downloading_title)) },
      text = {
        Text(stringResource(R.string.repo_disable_downloading_message, downloadingBlockModelNames.size))
      },
      confirmButton = {
        TextButton(onClick = {
          for (name in downloadingBlockModelNames) {
            onCancelDownload(name)
          }
          viewModel.toggleRepo(repoId, false)
          hasChanges = true
          downloadingBlockRepoId = null
          downloadingBlockModelNames = emptyList()
        }) {
          Text(stringResource(R.string.repo_disable_downloading_cancel_and_disable), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          downloadingBlockRepoId = null
          downloadingBlockModelNames = emptyList()
        }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  if (showInfoDialog) {
    AlertDialog(
      onDismissRequest = { showInfoDialog = false },
      title = { Text(stringResource(R.string.repo_info_title)) },
      text = { Text(stringResource(R.string.repo_info_body)) },
      confirmButton = {
        TextButton(onClick = { showInfoDialog = false }) {
          Text(stringResource(R.string.ok))
        }
      },
    )
  }
}

@Composable
private fun RepositoryRow(
  repo: Repository,
  onClick: () -> Unit,
  onToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Icon
    RepoIcon(repo = repo, modifier = Modifier.size(40.dp))

    // Name + details
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = repo.name.ifEmpty { repo.url },
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      val subtitle = when (repo.modelCount) {
        null -> stringResource(R.string.repo_model_count_none)
        else -> stringResource(R.string.repo_model_count, repo.modelCount)
      }
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (repo.lastError.isNotEmpty()) {
        Text(
          text = repo.lastError,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Enable/disable toggle
    val toggleLabel = stringResource(R.string.repo_toggle_label, repo.name.ifEmpty { repo.url })
    Switch(
      checked = repo.enabled,
      onCheckedChange = onToggle,
      modifier = Modifier.semantics { contentDescription = toggleLabel },
    )
  }
}

@Composable
private fun RepoIcon(repo: Repository, modifier: Modifier = Modifier) {
  if (repo.isBuiltIn) {
    Image(
      painter = painterResource(R.mipmap.ic_launcher_foreground),
      contentDescription = null,
      modifier = modifier.clip(CircleShape),
    )
  } else if (repo.iconUrl.isNotEmpty() && repo.iconUrl.startsWith("https://")) {
    val context = LocalContext.current
    AsyncImage(
      model = ImageRequest.Builder(context)
        .data(repo.iconUrl)
        .crossfade(true)
        .build(),
      contentDescription = null,
      modifier = modifier.clip(CircleShape),
    )
  } else {
    Box(
      modifier = modifier
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = (repo.name.firstOrNull() ?: '?').uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AddRepositoryDialog(
  isAdding: Boolean,
  error: String?,
  onDismiss: () -> Unit,
  onAdd: (String) -> Unit,
) {
  var url by rememberSaveable { mutableStateOf("") }
  val clipboardManager = LocalContext.current.getSystemService(android.content.ClipboardManager::class.java)

  AlertDialog(
    onDismissRequest = { if (!isAdding) onDismiss() },
    title = { Text(stringResource(R.string.repo_add_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          stringResource(R.string.repo_add_helper),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text(stringResource(R.string.repo_add_url_label)) },
          placeholder = { Text("https://...") },
          singleLine = true,
          enabled = !isAdding,
          modifier = Modifier.fillMaxWidth(),
          isError = error != null,
          supportingText = if (error != null) {
            { Text(error, color = MaterialTheme.colorScheme.error) }
          } else null,
        )
        if (url.trim().startsWith("http://", ignoreCase = true)) {
          Text(
            stringResource(R.string.http_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        if (isAdding) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
              stringResource(R.string.repo_add_loading),
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    },
    confirmButton = {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
          onClick = {
            clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()?.let { url = it }
          },
          enabled = !isAdding,
        ) {
          Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(4.dp))
          Text(stringResource(R.string.paste))
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss, enabled = !isAdding) {
          Text(stringResource(R.string.cancel))
        }
        TextButton(
          onClick = { onAdd(url.trim()) },
          enabled = url.isNotBlank() && !isAdding,
        ) {
          Text(stringResource(R.string.add))
        }
      }
    },
  )
}
