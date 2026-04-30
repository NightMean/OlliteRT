/*
 * Copyright 2026 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.ui.common.ErrorAlertDialog
import com.ollitert.llm.server.ui.common.SHEET_MAX_WIDTH
import com.ollitert.llm.server.ui.common.UrlInputDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "OlliteRT.ModelMgrDlg"

class ModelManagerDialogState internal constructor() {
  var showImportModelSheet by mutableStateOf(false)
  var showImportUrlDialog by mutableStateOf(false)
  internal var importUrlLoading by mutableStateOf(false)
  internal var importUrlError by mutableStateOf<String?>(null)
  var showUnsupportedFileTypeDialog by mutableStateOf(false)
  var showUnsupportedWebModelDialog by mutableStateOf(false)
  internal val selectedLocalModelFileUri = mutableStateOf<Uri?>(null)
  internal val selectedImportedModelInfo = mutableStateOf<ImportedModel?>(null)
  var showImportDialog by mutableStateOf(false)
  var showImportingDialog by mutableStateOf(false)
  var showSwitchModelDialog by mutableStateOf(false)
  var pendingSwitchModel by mutableStateOf<Model?>(null)
}

@Composable
fun rememberModelManagerDialogState(): ModelManagerDialogState {
  return remember { ModelManagerDialogState() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerDialogs(
  state: ModelManagerDialogState,
  viewModel: ModelManagerViewModel,
  importedModelNames: Set<String>,
  allowlistModelNames: Set<String>,
  snackbarHostState: SnackbarHostState,
  serverStatus: ServerStatus,
  activeModelName: String?,
  onSwitchModel: (String) -> Unit,
) {
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      result.data?.data?.let { uri ->
        val fileName = getFileName(context = context, uri = uri)
        Log.d(TAG, "Selected file: $fileName")
        if (fileName != null && !fileName.endsWith(".litertlm")) {
          state.showUnsupportedFileTypeDialog = true
        } else if (fileName != null && fileName.lowercase().contains("-web")) {
          state.showUnsupportedWebModelDialog = true
        } else {
          state.selectedLocalModelFileUri.value = uri
          state.showImportDialog = true
        }
      } ?: run { Log.d(TAG, "No file selected or URI is null.") }
    } else {
      Log.d(TAG, "File picking cancelled.")
    }
  }

  val importModelListSuccessText = stringResource(R.string.import_model_list_success)
  val modelListPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == Activity.RESULT_OK) {
      result.data?.data?.let { uri ->
        viewModel.importModelList(uri) { error ->
          if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
          } else {
            Toast.makeText(context, importModelListSuccessText, Toast.LENGTH_SHORT).show()
          }
        }
      }
    }
  }

  // Import model bottom sheet
  if (state.showImportModelSheet) {
    ModalBottomSheet(
      onDismissRequest = { state.showImportModelSheet = false },
      sheetState = sheetState,
      sheetMaxWidth = SHEET_MAX_WIDTH,
    ) {
      Text(
        stringResource(R.string.label_import_model),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
      )
      BottomSheetActionRow(
        icon = Icons.AutoMirrored.Outlined.NoteAdd,
        labelRes = R.string.label_import_from_local_file,
        contentDescriptionRes = R.string.cd_import_model_from_local_file_button,
        onClick = {
          scope.launch {
            delay(200)
            state.showImportModelSheet = false
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
              addCategory(Intent.CATEGORY_OPENABLE)
              type = "*/*"
              putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }
            filePickerLauncher.launch(intent)
          }
        },
      )
      BottomSheetActionRow(
        icon = Icons.Outlined.Dns,
        labelRes = R.string.label_import_from_model_list,
        contentDescriptionRes = R.string.cd_import_model_list_button,
        onClick = {
          scope.launch {
            delay(200)
            state.showImportModelSheet = false
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
              addCategory(Intent.CATEGORY_OPENABLE)
              type = "application/json"
              putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            }
            modelListPickerLauncher.launch(intent)
          }
        },
      )
      BottomSheetActionRow(
        icon = Icons.Outlined.Link,
        labelRes = R.string.label_import_from_url,
        contentDescriptionRes = R.string.cd_import_model_list_url_button,
        onClick = {
          state.showImportModelSheet = false
          state.showImportUrlDialog = true
        },
      )
    }
  }

  // Import model list from URL dialog
  if (state.showImportUrlDialog) {
    ImportModelListUrlDialog(
      isLoading = state.importUrlLoading,
      error = state.importUrlError,
      onDismiss = {
        if (!state.importUrlLoading) {
          state.showImportUrlDialog = false
          state.importUrlError = null
        }
      },
      onImport = { url ->
        state.importUrlLoading = true
        state.importUrlError = null
        viewModel.importModelListFromUrl(url) { error ->
          state.importUrlLoading = false
          if (error != null) {
            state.importUrlError = error
          } else {
            state.showImportUrlDialog = false
            state.importUrlError = null
            Toast.makeText(context, importModelListSuccessText, Toast.LENGTH_SHORT).show()
          }
        }
      },
    )
  }

  // Import dialog
  if (state.showImportDialog) {
    state.selectedLocalModelFileUri.value?.let { uri ->
      ModelImportDialog(
        uri = uri,
        onDismiss = { state.showImportDialog = false },
        onDone = { info ->
          state.selectedImportedModelInfo.value = info
          state.showImportDialog = false
          state.showImportingDialog = true
        },
        existingImportedModelNames = importedModelNames,
        allowlistModelNames = allowlistModelNames,
      )
    }
  }

  // Importing in progress dialog
  val modelImportedText = stringResource(R.string.toast_model_imported)
  if (state.showImportingDialog) {
    state.selectedLocalModelFileUri.value?.let { uri ->
      state.selectedImportedModelInfo.value?.let { info ->
        ModelImportingDialog(
          uri = uri,
          info = info,
          onDismiss = { state.showImportingDialog = false },
          onDone = {
            viewModel.addImportedLlmModel(info = it)
            state.showImportingDialog = false
            scope.launch { snackbarHostState.showSnackbar(modelImportedText) }
          },
        )
      }
    }
  }

  // Alert dialog for unsupported file type
  if (state.showUnsupportedFileTypeDialog) {
    ErrorAlertDialog(
      title = stringResource(R.string.dialog_unsupported_file_type_title),
      text = stringResource(R.string.dialog_unsupported_file_type_body),
      onDismiss = { state.showUnsupportedFileTypeDialog = false },
    )
  }

  // Alert dialog for unsupported web model
  if (state.showUnsupportedWebModelDialog) {
    ErrorAlertDialog(
      title = stringResource(R.string.dialog_unsupported_web_model_title),
      text = stringResource(R.string.dialog_unsupported_web_model_body),
      onDismiss = { state.showUnsupportedWebModelDialog = false },
    )
  }

  // Confirmation dialog when switching models while server is running
  val switchModel = state.pendingSwitchModel
  if (state.showSwitchModelDialog && switchModel != null) {
    AlertDialog(
      onDismissRequest = {
        state.showSwitchModelDialog = false
        state.pendingSwitchModel = null
      },
      title = { Text(stringResource(R.string.dialog_switch_model_title)) },
      text = {
        Text(
          stringResource(
            R.string.dialog_switch_model_body,
            activeModelName ?: stringResource(R.string.label_current_model),
            switchModel.displayName.ifEmpty { switchModel.name },
          )
        )
      },
      confirmButton = {
        Button(onClick = {
          state.showSwitchModelDialog = false
          state.pendingSwitchModel = null
          onSwitchModel(switchModel.name)
        }) {
          Text(stringResource(R.string.button_switch))
        }
      },
      dismissButton = {
        Button(
          onClick = {
            state.showSwitchModelDialog = false
            state.pendingSwitchModel = null
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
        ) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }
}

@Composable
private fun BottomSheetActionRow(
  icon: ImageVector,
  @StringRes labelRes: Int,
  @StringRes contentDescriptionRes: Int,
  onClick: () -> Unit,
) {
  val cd = stringResource(contentDescriptionRes)
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

private fun getFileName(context: Context, uri: Uri): String? {
  if (uri.scheme == "content") {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
      if (cursor.moveToFirst()) {
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1) {
          return cursor.getString(nameIndex)
        }
      }
    }
  } else if (uri.scheme == "file") {
    return uri.lastPathSegment
  }
  return null
}

@Composable
private fun ImportModelListUrlDialog(
  isLoading: Boolean,
  error: String?,
  onDismiss: () -> Unit,
  onImport: (String) -> Unit,
) {
  UrlInputDialog(
    title = stringResource(R.string.import_model_list_url_title),
    label = stringResource(R.string.import_model_list_url_label),
    confirmText = stringResource(R.string.button_import),
    loadingText = stringResource(R.string.import_model_list_url_loading),
    isLoading = isLoading,
    error = error,
    onDismiss = onDismiss,
    onConfirm = onImport,
  )
}
