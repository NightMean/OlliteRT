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

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.ui.modelmanager.components.DiscoveredModelRemovalDialog

@Composable
internal fun RepositoryModelDialog(
  model: RepositoryModelSummary,
  remoteFiles: List<RepositoryFileVariant>,
  managementOnly: Boolean,
  initialPath: String?,
  loading: Boolean,
  error: Boolean,
  alreadyAdded: Boolean,
  onDismiss: () -> Unit,
  onAdd: (String, ImportedModel) -> Unit,
  manager: ModelManagerViewModel,
  imports: List<RepositoryImport>,
  importsReady: Boolean,
  importsError: Boolean,
  onRetryImports: () -> Unit,
  repositoryUrl: String,
  fileUrl: (String) -> String?,
) {
  val context = LocalContext.current
  var pendingRemoval by remember(repositoryUrl) { mutableStateOf<Model?>(null) }
  val unavailable = stringResource(R.string.repository_remove_unavailable)
  val files = remoteFiles + imports.filter { item -> remoteFiles.none { it.path == item.record.path } }
    .map { RepositoryFileVariant(it.record.path, it.record.size) }
  var selectedPath by remember(repositoryUrl, initialPath, managementOnly) { mutableStateOf(initialPath) }
  var configuringPath by remember(repositoryUrl, managementOnly) { mutableStateOf<String?>(null) }
  val selected = files.firstOrNull { it.path == selectedPath }
  val configuringFile = remoteFiles.firstOrNull { it.path == configuringPath && it.sizeBytes != null }
    ?.takeUnless { file -> managementOnly || imports.any { it.record.path == file.path } }
  if (configuringFile == null) {
    RepositoryModelDetailsSheet(
      model = model, files = files, managementOnly = managementOnly, selectedPath = selectedPath, loading = loading, error = error,
      alreadyAdded = alreadyAdded, onSelect = { selectedPath = it }, onDismiss = onDismiss,
      onContinue = { configuringPath = selected?.path },
      imports = imports, importsReady = importsReady, importsError = importsError, onRetryImports = onRetryImports, onDelete = { item ->
        val importedModel = item.model
        if (importedModel == null) Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
        else pendingRemoval = importedModel
      },
      repositoryUrl = repositoryUrl, fileUrl = fileUrl,
    )
  }
  pendingRemoval?.let {
    DiscoveredModelRemovalDialog(it, manager, onDismiss = { pendingRemoval = null })
  }
  configuringFile?.let { file ->
    val size = file.sizeBytes ?: return@let
    ModelImportSettingsDialog(
      sourceFileName = file.path.substringAfterLast('/'),
      sourceFileSize = size,
      onDismiss = { configuringPath = null },
      onDone = { info ->
        configuringPath = null
        onAdd(file.path, info)
      },
      remote = true,
    )
  }
}
