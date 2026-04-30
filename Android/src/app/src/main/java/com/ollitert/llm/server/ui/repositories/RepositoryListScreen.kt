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

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
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
import com.ollitert.llm.server.ui.common.SHEET_MAX_WIDTH
import com.ollitert.llm.server.ui.common.UrlInputDialog
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryListScreen(
  viewModel: RepositoryViewModel,
  onBackClick: (hasChanges: Boolean) -> Unit,
  onRepoClick: (repoId: String) -> Unit,
  modifier: Modifier = Modifier,
  downloadedModelRepoIds: Map<String, String> = emptyMap(),
  downloadingModelRepoIds: Map<String, String> = emptyMap(),
  onCancelDownload: (modelName: String) -> Unit = {},
  onSetTopBarTrailingContent: ((@Composable () -> Unit)?) -> Unit = {},
) {
  LaunchedEffect(Unit) { viewModel.loadRepositories() }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var hasChanges by rememberSaveable { mutableStateOf(false) }
  var showAddSheet by rememberSaveable { mutableStateOf(false) }
  var showAddUrlDialog by rememberSaveable { mutableStateOf(false) }
  var disableInfoRepoId by rememberSaveable { mutableStateOf<String?>(null) }
  var downloadingBlockRepoId by rememberSaveable { mutableStateOf<String?>(null) }
  var downloadingBlockModelNames by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
  var showInfoDialog by rememberSaveable { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val repoAddedText = stringResource(R.string.repo_added_success)
  val jsonPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      result.data?.data?.let { uri ->
        viewModel.addRepositoryFromFile(uri) { addResult ->
          when (addResult) {
            is AddRepoResult.Success -> {
              hasChanges = true
              Toast.makeText(context, repoAddedText, Toast.LENGTH_SHORT).show()
            }
            is AddRepoResult.Error -> {
              Toast.makeText(context, addResult.message, Toast.LENGTH_LONG).show()
            }
          }
        }
      }
    }
  }

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
          onClick = { showAddSheet = true },
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

  // Add source bottom sheet
  if (showAddSheet) {
    ModalBottomSheet(
      onDismissRequest = { showAddSheet = false },
      sheetState = sheetState,
      sheetMaxWidth = SHEET_MAX_WIDTH,
    ) {
      Text(
        stringResource(R.string.repo_add),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
      )
      AddSourceSheetRow(
        icon = Icons.Outlined.Dns,
        labelRes = R.string.label_import_from_model_list,
        onClick = {
          scope.launch {
            delay(200)
            showAddSheet = false
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
              addCategory(Intent.CATEGORY_OPENABLE)
              type = "application/json"
              putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }
            jsonPickerLauncher.launch(intent)
          }
        },
      )
      AddSourceSheetRow(
        icon = Icons.Outlined.Link,
        labelRes = R.string.label_import_from_url,
        onClick = {
          showAddSheet = false
          showAddUrlDialog = true
        },
      )
    }
  }

  // Add from URL dialog
  if (showAddUrlDialog) {
    AddRepositoryDialog(
      isAdding = uiState.isAdding,
      error = uiState.addDialogError,
      onDismiss = { showAddUrlDialog = false },
      onAdd = { url ->
        viewModel.addRepository(url) { result ->
          when (result) {
            is AddRepoResult.Success -> {
              showAddUrlDialog = false
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
      val subtitle = when {
        repo.modelCount == null -> stringResource(R.string.repo_model_count_none)
        repo.hiddenModelCount > 0 -> stringResource(
          R.string.repo_model_count_with_hidden, repo.modelCount, repo.hiddenModelCount
        )
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
  UrlInputDialog(
    title = stringResource(R.string.repo_add_title),
    label = stringResource(R.string.repo_add_url_label),
    confirmText = stringResource(R.string.add),
    loadingText = stringResource(R.string.repo_add_loading),
    helperText = stringResource(R.string.repo_add_helper),
    isLoading = isAdding,
    error = error,
    onDismiss = onDismiss,
    onConfirm = onAdd,
  )
}

@Composable
private fun AddSourceSheetRow(
  icon: ImageVector,
  @androidx.annotation.StringRes labelRes: Int,
  onClick: () -> Unit,
) {
  val cd = stringResource(labelRes)
  Box(
    modifier = Modifier
      .clickable(onClick = onClick)
      .semantics {
        role = Role.Button
        contentDescription = cd
      },
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      Icon(icon, contentDescription = null)
      Text(stringResource(labelRes), modifier = Modifier.clearAndSetSemantics {})
    }
  }
}
