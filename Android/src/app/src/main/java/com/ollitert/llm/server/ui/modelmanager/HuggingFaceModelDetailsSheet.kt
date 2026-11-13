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

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.data.allowlist.HuggingFaceFile
import com.ollitert.llm.server.data.allowlist.HuggingFaceModel
import com.ollitert.llm.server.data.allowlist.huggingFaceFileUrl
import com.ollitert.llm.server.data.allowlist.isValidHuggingFaceModelId
import com.ollitert.llm.server.ui.common.SHEET_MAX_WIDTH
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.buildTrackableUrlAnnotatedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HuggingFaceModelDetailsSheet(
  model: HuggingFaceModel,
  selectedPath: String?,
  loading: Boolean,
  error: Boolean,
  alreadyAdded: Boolean,
  onSelect: (String) -> Unit,
  onDismiss: () -> Unit,
  onContinue: () -> Unit,
) {
  val currentLoading by rememberUpdatedState(loading)
  val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true,
    confirmValueChange = { !currentLoading || it != SheetValue.Hidden },
  )
  val context = LocalContext.current
  val totalMemory = remember(context) {
    context.getSystemService(ActivityManager::class.java)?.let { manager ->
      ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }.totalMem
    }
  }
  val device = remember { "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL} ${Build.BOARD} ${Build.HARDWARE}" }
  val files = model.modelFiles
  val (possibleFiles, otherFiles) = remember(files, totalMemory, device) {
    files.partition { mayWorkOnDevice(it.path, it.sizeBytes, totalMemory, device) }
  }
  LaunchedEffect(model.id, selectedPath, possibleFiles, otherFiles) {
    if (selectedPath == null) (possibleFiles.firstOrNull() ?: otherFiles.firstOrNull())?.let { onSelect(it.path) }
  }
  val selected = files.firstOrNull { it.path == selectedPath }
  val validRevision = model.sha?.matches(Regex("[a-fA-F0-9]{40}")) == true
  ModalBottomSheet(
    onDismissRequest = { if (!loading) onDismiss() },
    sheetState = sheetState,
    sheetMaxWidth = SHEET_MAX_WIDTH,
  ) {
    Column(Modifier.fillMaxWidth()) {
      Column(
        Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            buildTrackableUrlAnnotatedString("${GitHubConfig.HUGGINGFACE_BASE_URL}/${model.id}", model.id.substringAfter('/')),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleLarge,
          )
          Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(model.id.substringBefore('/'), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        HuggingFaceModelStats(model)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (possibleFiles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(stringResource(R.string.hf_files_possible), style = MaterialTheme.typography.titleMedium)
              Text(stringResource(R.string.hf_files_estimate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            possibleFiles.forEach { file ->
              HuggingFaceFileRow(model, file, selectedPath == file.path, loading, validRevision) { onSelect(file.path) }
            }
          }
          if (otherFiles.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(stringResource(R.string.hf_files_other), style = MaterialTheme.typography.titleMedium)
              Text(stringResource(R.string.hf_files_other_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            otherFiles.forEach { file ->
              HuggingFaceFileRow(model, file, selectedPath == file.path, loading, validRevision) { onSelect(file.path) }
            }
          }
        }
        if (files.isEmpty()) {
          Text(stringResource(R.string.hf_sheet_no_files), color = MaterialTheme.colorScheme.error)
        } else if (selected?.sizeBytes == null || !validRevision) {
          Text(stringResource(R.string.hf_explore_metadata_unavailable))
        }
        if (error) Text(stringResource(R.string.hf_explore_save_error), color = MaterialTheme.colorScheme.error)
        if (alreadyAdded) Text(stringResource(R.string.hf_explore_already_added))
      }
      HorizontalDivider(Modifier.padding(top = 12.dp))
      Button(
        enabled = !loading && selected?.sizeBytes != null && validRevision,
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
      ) { Text(stringResource(R.string.hf_sheet_import)) }
    }
  }
}

@Composable
private fun HuggingFaceFileRow(
  model: HuggingFaceModel,
  file: HuggingFaceFile,
  selected: Boolean,
  loading: Boolean,
  validRevision: Boolean,
  onSelect: () -> Unit,
) {
  val context = LocalContext.current
  val copied = stringResource(R.string.toast_copied_to_clipboard)
  val copyLabel = stringResource(R.string.hf_sheet_copy_link)
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = !loading, role = Role.RadioButton, onClick = onSelect),
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      RadioButton(selected = selected, onClick = null, enabled = !loading)
      Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
        Text(file.path, style = MaterialTheme.typography.bodyMedium)
        Text(file.sizeBytes?.humanReadableSize() ?: stringResource(R.string.hf_explore_unknown_size),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      TooltipIconButton(
        icon = Icons.Outlined.ContentCopy,
        tooltip = copyLabel,
        enabled = !loading && validRevision && isValidHuggingFaceModelId(model.id),
        onClick = {
          val clipboard = context.getSystemService(ClipboardManager::class.java)
          val revision = model.sha
          if (clipboard != null && revision != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("OlliteRT Model URL", huggingFaceFileUrl(model.id, revision, file.path)))
            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
          }
        },
      )
    }
  }
}
