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
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.copyToClipboard
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.ui.common.PlainTooltipIconButton
import com.ollitert.llm.server.ui.common.SHEET_MAX_WIDTH
import com.ollitert.llm.server.ui.modelmanager.components.ModelFeatureChip
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.buildTrackableUrlAnnotatedString
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.storage.canInspectDiscoveredRemoval
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTWarningText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RepositoryModelDetailsSheet(
  model: RepositoryModelSummary,
  files: List<RepositoryFileVariant>,
  managementOnly: Boolean,
  selectedPath: String?,
  loading: Boolean,
  error: Boolean,
  alreadyAdded: Boolean,
  onSelect: (String?) -> Unit,
  onDismiss: () -> Unit,
  onContinue: () -> Unit,
  imports: List<RepositoryImport> = emptyList(),
  importsReady: Boolean = true,
  importsError: Boolean = false,
  onRetryImports: () -> Unit = {},
  onDelete: (RepositoryImport) -> Unit = {},
  repositoryUrl: String,
  fileUrl: (String) -> String?,
) {
  var compatibilityInfo by remember { mutableStateOf<Boolean?>(null) }
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
  val (possibleFiles, otherFiles) = remember(files, totalMemory, device) {
    files.partition { mayWorkOnDevice(it.path, it.sizeBytes, totalMemory, device) }
  }
  LaunchedEffect(model.id, selectedPath, possibleFiles, otherFiles, imports, importsReady) {
    if (importsReady && !managementOnly && selectedPath == null) {
      val next = selectRepositoryPath(selectedPath, (possibleFiles + otherFiles).map { it.path }, imports)
      if (selectedPath != next) onSelect(next)
    }
  }
  val selected = files.firstOrNull { it.path == selectedPath }
  val validRevision = selected?.path?.let(fileUrl) != null
  ModalBottomSheet(
    onDismissRequest = { if (!loading) onDismiss() },
    // Reserve the top inset before Material measures the drag anchors. Padding that
    // depends on the drag offset can move the expanded anchor during nested scroll.
    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
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
            buildTrackableUrlAnnotatedString(repositoryUrl, model.id.substringAfter('/')),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleLarge,
          )
          Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(model.id.substringBefore('/'), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        if (model.downloads >= 0) RepositoryModelStats(model)
        if (managementOnly) Text(stringResource(R.string.repository_offline_details))
        if (importsError) {
          Text(stringResource(R.string.repository_imports_error), color = MaterialTheme.colorScheme.error)
          TextButton(onClick = onRetryImports) { Text(stringResource(R.string.retry)) }
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (possibleFiles.isNotEmpty()) {
            RepositoryFileSection(
              title = stringResource(R.string.hf_files_possible),
              description = stringResource(R.string.hf_files_estimate),
              warning = false,
              onShowInfo = { compatibilityInfo = false },
            ) {
              possibleFiles.forEach { file ->
                RepositoryFileRow(file, selectedPath == file.path, loading || !importsReady, managementOnly, fileUrl(file.path), imports.firstOrNull { it.record.path == file.path }, onDelete) { onSelect(file.path) }
              }
            }
          }
          if (otherFiles.isNotEmpty()) {
            RepositoryFileSection(
              title = stringResource(R.string.hf_files_other),
              description = stringResource(R.string.hf_files_other_hint),
              warning = true,
              onShowInfo = { compatibilityInfo = true },
            ) {
              otherFiles.forEach { file ->
                RepositoryFileRow(file, selectedPath == file.path, loading || !importsReady, managementOnly, fileUrl(file.path), imports.firstOrNull { it.record.path == file.path }, onDelete) { onSelect(file.path) }
              }
            }
          }
        }
        if (files.isEmpty() && importsReady && !managementOnly) {
          Text(stringResource(R.string.hf_sheet_no_files), color = MaterialTheme.colorScheme.error)
        } else if (!managementOnly && selected != null && (selected.sizeBytes == null || !validRevision)) {
          Text(stringResource(R.string.hf_explore_metadata_unavailable))
        }
        if (error) Text(stringResource(R.string.hf_explore_save_error), color = MaterialTheme.colorScheme.error)
        if (alreadyAdded) Text(stringResource(R.string.hf_explore_already_added))
      }
      if (!managementOnly) {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Button(
          enabled = !loading && importsReady && selected?.sizeBytes != null && validRevision && imports.none { it.record.path == selected.path },
          onClick = onContinue,
          modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        ) { Text(stringResource(R.string.hf_sheet_import)) }
      }
    }
  }
  compatibilityInfo?.let { warning ->
    AlertDialog(
      onDismissRequest = { compatibilityInfo = null },
      title = { Text(stringResource(R.string.hf_compatibility_info_title)) },
      text = { Text(stringResource(if (warning) R.string.hf_compatibility_info_body else R.string.hf_compatible_info_body)) },
      confirmButton = { TextButton(onClick = { compatibilityInfo = null }) { Text(stringResource(R.string.ok)) } },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepositoryFileSection(
  title: String,
  description: String,
  warning: Boolean,
  onShowInfo: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val accent = if (warning) OlliteRTWarningText else OlliteRTPrimary
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(Modifier.width(3.dp).height(24.dp).background(accent, RoundedCornerShape(2.dp)))
      ModelFeatureChip(
        label = title,
        modifier = Modifier.weight(1f, fill = false),
        textColor = accent,
        textStyle = MaterialTheme.typography.titleMedium,
        maxLines = 2,
      )
      if (onShowInfo != null) {
        PlainTooltipIconButton(
          icon = Icons.Outlined.Info,
          tooltip = stringResource(R.string.hf_compatibility_info_tooltip),
          onClick = onShowInfo,
          modifier = Modifier.size(32.dp),
          iconSize = 20.dp,
        )
      }
    }
    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    content()
  }
}

@Composable
private fun RepositoryFileRow(
  file: RepositoryFileVariant,
  selected: Boolean,
  loading: Boolean,
  managementOnly: Boolean,
  url: String?,
  imported: RepositoryImport?,
  onDelete: (RepositoryImport) -> Unit,
  onSelect: () -> Unit,
) {
  val context = LocalContext.current
  val copyLabel = stringResource(R.string.hf_sheet_copy_link)
  Surface(
    shape = RoundedCornerShape(24.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (imported != null) 0.4f else 1f),
    modifier = Modifier.fillMaxWidth().selectable(selected = selected, enabled = !loading && !managementOnly && imported == null, role = Role.RadioButton, onClick = onSelect),
  ) {
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      RadioButton(selected = selected, onClick = null, enabled = !loading && !managementOnly && imported == null)
      Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
        val alpha = if (imported != null) 0.4f else 1f
        Text(file.path, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        Text(file.sizeBytes?.humanReadableSize() ?: stringResource(R.string.hf_explore_unknown_size),
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        if (imported != null) Text(stringResource(if (imported.status == ModelDownloadStatusType.SUCCEEDED) R.string.repository_already_downloaded else R.string.repository_already_imported), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      if (imported != null) {
        val tint = if (imported.status == ModelDownloadStatusType.SUCCEEDED || imported.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED) OlliteRTDeleteRed else OlliteRTPrimary
        TooltipIconButton(icon = Icons.Outlined.Delete, tooltip = stringResource(R.string.model_tooltip_delete),
          modifier = Modifier.padding(end = 8.dp), tint = tint, backgroundColor = tint.copy(alpha = 0.12f),
          enabled = !loading && imported.model != null && canInspectDiscoveredRemoval(imported.status),
          onClick = { onDelete(imported) })
      }
      TooltipIconButton(
        icon = Icons.Outlined.ContentCopy,
        tooltip = copyLabel,
        enabled = !loading && url != null,
        onClick = { if (url != null) copyToClipboard(context, "OlliteRT Model URL", url) },
      )
    }
  }
}
