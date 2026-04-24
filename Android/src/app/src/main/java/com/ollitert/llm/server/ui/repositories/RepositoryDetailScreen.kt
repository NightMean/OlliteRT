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
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.ui.common.SCREEN_CONTENT_MAX_WIDTH

@Composable
fun RepositoryDetailScreen(
  viewModel: RepositoryViewModel,
  repoId: String,
  onBackClick: (hasChanges: Boolean) -> Unit,
  downloadingModelRepoIds: Map<String, String> = emptyMap(),
  onCancelDownload: (modelName: String) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var hasChanges by rememberSaveable { mutableStateOf(false) }
  var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var downloadingBlockModelNames by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

  LaunchedEffect(repoId) {
    viewModel.loadRepoDetail(repoId)
  }

  BackHandler { onBackClick(hasChanges) }

  val repo = uiState.selectedRepo

  if (repo == null) {
    if (!uiState.isLoading) {
      Text(
        text = stringResource(R.string.repo_not_found),
        modifier = modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  } else {
    LazyColumn(
      modifier = modifier
        .fillMaxSize()
        .widthIn(max = SCREEN_CONTENT_MAX_WIDTH),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Info card
      item(key = "info") {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (repo.description.isNotEmpty()) {
            DetailRow(
              label = stringResource(R.string.repo_detail_description),
              value = repo.description,
            )
          }

          val uriHandler = LocalUriHandler.current
          Column {
            Text(
              text = stringResource(R.string.repo_detail_url),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (repo.url.isNotEmpty()) {
              Text(
                text = repo.url,
                style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uriHandler.openUri(repo.url) },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
            } else {
              Text(
                text = stringResource(R.string.repo_detail_imported_locally),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          DetailRow(
            label = stringResource(R.string.repo_detail_models),
            value = repo.modelCount?.toString() ?: "—",
          )
          DetailRow(
            label = stringResource(R.string.repo_detail_version),
            value = "v${repo.contentVersion}",
          )
          if (repo.lastError.isNotEmpty()) {
            DetailRow(
              label = stringResource(R.string.repo_detail_last_error),
              value = repo.lastError,
              isError = true,
            )
          }
        }
      }

      // Share + Delete buttons (third-party repos only)
      if (!repo.isBuiltIn) {
        item(key = "actions") {
          val context = LocalContext.current
          Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (repo.url.isNotEmpty()) {
              OutlinedButton(
                onClick = {
                  val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_TEXT, repo.url)
                    type = "text/plain"
                  }
                  context.startActivity(Intent.createChooser(sendIntent, null))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
              ) {
                Icon(
                  imageVector = Icons.Outlined.Share,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = stringResource(R.string.repo_detail_share),
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
            Button(
              onClick = {
                val downloading = viewModel.getDownloadingModelNamesForRepo(repo.id, downloadingModelRepoIds)
                if (downloading.isNotEmpty()) {
                  downloadingBlockModelNames = downloading
                } else {
                  showDeleteDialog = true
                }
              },
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(50),
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
              ),
            ) {
              Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.repo_detail_delete),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      }

      // Models section header
      if (uiState.detailModels.isNotEmpty()) {
        item(key = "models_header") {
          Text(
            text = stringResource(R.string.repo_detail_models_section),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp),
          )
        }

        itemsIndexed(uiState.detailModels, key = { index, model -> "${index}_${model.name}" }) { _, model ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = model.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              if (model.description.isNotEmpty()) {
                Text(
                  text = model.description,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
            if (model.sizeInBytes > 0) {
              Text(
                text = model.sizeInBytes.humanReadableSize(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(16.dp)) }
    }
  }

  // Active download guard
  if (downloadingBlockModelNames.isNotEmpty() && repo != null) {
    AlertDialog(
      onDismissRequest = { downloadingBlockModelNames = emptyList() },
      title = { Text(stringResource(R.string.repo_disable_downloading_title)) },
      text = {
        Text(stringResource(R.string.repo_delete_downloading_message, downloadingBlockModelNames.size))
      },
      confirmButton = {
        TextButton(onClick = {
          for (name in downloadingBlockModelNames) {
            onCancelDownload(name)
          }
          viewModel.deleteRepo(repo.id)
          downloadingBlockModelNames = emptyList()
          onBackClick(true)
        }) {
          Text(stringResource(R.string.repo_delete_downloading_cancel_and_delete), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { downloadingBlockModelNames = emptyList() }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  // Delete confirmation
  if (showDeleteDialog && repo != null) {
    AlertDialog(
      onDismissRequest = { showDeleteDialog = false },
      title = { Text(stringResource(R.string.repo_delete_title)) },
      text = { Text(stringResource(R.string.repo_delete_message, repo.name.ifEmpty { repo.url })) },
      confirmButton = {
        TextButton(onClick = {
          viewModel.deleteRepo(repo.id)
          showDeleteDialog = false
          onBackClick(true)
        }) {
          Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}

@Composable
private fun DetailRow(
  label: String,
  value: String,
  isError: Boolean = false,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
  }
}
