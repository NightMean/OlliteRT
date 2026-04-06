/*
 * Copyright 2026 Google LLC
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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.RuntimeType
import com.ollitert.llm.server.data.Task
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.ui.common.ShimmerModelCard
import com.ollitert.llm.server.ui.common.modelitem.ModelItem
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.ui.theme.OlliteRTDeepBlue
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import kotlin.text.endsWith
import kotlin.text.lowercase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "OlliteRTGlobalMM"

/** Filter mode for the models list. */
enum class ModelFilter {
  ALL,
  DOWNLOADED,
  AVAILABLE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalModelManager(
  viewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  onModelSelected: (Task, Model) -> Unit,
  onBenchmarkClicked: (Model) -> Unit,
  onNavigateToSettings: () -> Unit = {},
  modifier: Modifier = Modifier,
  serverStatus: ServerStatus = ServerStatus.STOPPED,
  activeModelName: String? = null,
  onStopServer: () -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsState()
  val builtInModels = remember { mutableStateListOf<Model>() }
  val importedModels = remember { mutableStateListOf<Model>() }
  var showImportModelSheet by remember { mutableStateOf(false) }
  var showUnsupportedFileTypeDialog by remember { mutableStateOf(false) }
  var showUnsupportedWebModelDialog by remember { mutableStateOf(false) }
  val selectedLocalModelFileUri = remember { mutableStateOf<Uri?>(null) }
  val selectedImportedModelInfo = remember { mutableStateOf<ImportedModel?>(null) }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var showImportDialog by remember { mutableStateOf(false) }
  var showImportingDialog by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val modelItemExpandedStates = remember { mutableStateMapOf<String, Boolean>() }

  // Search and filter state
  var searchQuery by remember { mutableStateOf("") }
  var activeFilter by remember { mutableStateOf(ModelFilter.ALL) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current

  val filePickerLauncher: ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          val fileName = getFileName(context = context, uri = uri)
          Log.d(TAG, "Selected file: $fileName")
          if (fileName != null && !fileName.endsWith(".task") && !fileName.endsWith(".litertlm")) {
            showUnsupportedFileTypeDialog = true
          } else if (fileName != null && fileName.lowercase().contains("-web")) {
            showUnsupportedWebModelDialog = true
          } else {
            selectedLocalModelFileUri.value = uri
            showImportDialog = true
          }
        } ?: run { Log.d(TAG, "No file selected or URI is null.") }
      } else {
        Log.d(TAG, "File picking cancelled.")
      }
    }

  val totalModelCount = uiState.tasks.sumOf { it.models.size }
  LaunchedEffect(uiState.modelImportingUpdateTrigger, uiState.loadingModelAllowlist, totalModelCount) {
    val allModelsSet = mutableSetOf<Model>()
    for (task in uiState.tasks) {
      for (model in task.models) {
        allModelsSet.add(model)
      }
    }
    // Sort priority: Downloaded → Gemma 4 (Best Overall) → Available, then alphabetical within each group
    val sortedModels = allModelsSet.toList().sortedWith(
      compareBy<Model> { model ->
        val name = model.displayName.ifEmpty { model.name }
        val isGemma4 = name.contains("gemma-4", ignoreCase = true) || name.contains("gemma 4", ignoreCase = true)
        val isDownloaded = uiState.modelDownloadStatus[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
        when {
          isDownloaded -> 0
          isGemma4 -> 1
          else -> 2
        }
      }.thenBy { it.displayName.ifEmpty { it.name } }
    )
    builtInModels.clear()
    builtInModels.addAll(sortedModels.filter { !it.imported })
    importedModels.clear()
    importedModels.addAll(sortedModels.filter { it.imported })
  }

  // Filtered models based on search query and active filter
  val filteredBuiltInModels by remember(searchQuery, activeFilter, builtInModels.toList()) {
    derivedStateOf {
      builtInModels.filter { model ->
        val matchesSearch = searchQuery.isEmpty() ||
          model.displayName.contains(searchQuery, ignoreCase = true) ||
          model.name.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (activeFilter) {
          ModelFilter.ALL -> true
          ModelFilter.DOWNLOADED -> uiState.modelDownloadStatus[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
          ModelFilter.AVAILABLE -> uiState.modelDownloadStatus[model.name]?.status != ModelDownloadStatusType.SUCCEEDED
        }
        matchesSearch && matchesFilter
      }
    }
  }

  val filteredImportedModels by remember(searchQuery, activeFilter, importedModels.toList()) {
    derivedStateOf {
      importedModels.filter { model ->
        val matchesSearch = searchQuery.isEmpty() ||
          model.displayName.contains(searchQuery, ignoreCase = true) ||
          model.name.contains(searchQuery, ignoreCase = true)
        // Imported models are always downloaded
        activeFilter != ModelFilter.AVAILABLE
      }
    }
  }

  val handleClickModel: (Model) -> Unit = { model ->
    val tasks = viewModel.uiState.value.tasks
    val tasksForModel = tasks.filter { task -> task.models.any { it.name == model.name } }
    // Auto-select first task — skip the task selector bottom sheet entirely.
    // In OlliteRT, tapping "Start Server" should directly start the server with the model.
    if (tasksForModel.isNotEmpty()) {
      onModelSelected(tasksForModel[0], model)
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier
        .background(MaterialTheme.colorScheme.surface)
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
    ) {
      // Search bar
      item(key = "search_bar") {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
          placeholder = {
            Text(
              stringResource(R.string.search_models),
              style = MaterialTheme.typography.bodyLarge,
            )
          },
          leadingIcon = {
            Icon(
              Icons.Outlined.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(
                  Icons.Outlined.Close,
                  contentDescription = "Clear search",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = OlliteRTPrimary,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = OlliteRTPrimary,
          ),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(
            onSearch = {
              keyboardController?.hide()
              focusManager.clearFocus()
            },
          ),
        )
      }

      // Filter chips
      item(key = "filter_chips") {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(bottom = 8.dp),
        ) {
          ModelFilterChip(
            label = stringResource(R.string.filter_all),
            selected = activeFilter == ModelFilter.ALL,
            onClick = { activeFilter = ModelFilter.ALL },
          )
          ModelFilterChip(
            label = stringResource(R.string.filter_downloaded),
            selected = activeFilter == ModelFilter.DOWNLOADED,
            onClick = { activeFilter = ModelFilter.DOWNLOADED },
          )
          ModelFilterChip(
            label = stringResource(R.string.filter_available),
            selected = activeFilter == ModelFilter.AVAILABLE,
            onClick = { activeFilter = ModelFilter.AVAILABLE },
          )
        }
      }

      // Shimmer loading placeholders
      if (uiState.loadingModelAllowlist && builtInModels.isEmpty()) {
        items(3, key = { "shimmer_$it" }) {
          ShimmerModelCard()
        }
      }

      // Built-in models
      items(filteredBuiltInModels, key = { "builtin_${it.name}" }) { model ->
        val expanded = modelItemExpandedStates.getOrDefault(model.name, true)
        ModelItem(
          model = model,
          task = null,
          modelManagerViewModel = viewModel,
          onModelClicked = handleClickModel,
          onBenchmarkClicked = onBenchmarkClicked,
          expanded = expanded,
          showBenchmarkButton = model.runtimeType == RuntimeType.LITERT_LM,
          onExpanded = { modelItemExpandedStates[model.name] = it },
          serverStatus = serverStatus,
          activeModelName = activeModelName,
          onStopServer = onStopServer,
          onNavigateToSettings = onNavigateToSettings,
        )
      }

      // Imported models section
      if (filteredImportedModels.isNotEmpty()) {
        item(key = "imported_models_label") {
          Text(
            stringResource(R.string.model_list_imported_models_title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
              .padding(horizontal = 16.dp)
              .padding(top = 32.dp, bottom = 8.dp),
          )
        }
      }
      items(filteredImportedModels, key = { "imported_${it.name}" }) { model ->
        ModelItem(
          model = model,
          task = null,
          modelManagerViewModel = viewModel,
          onModelClicked = handleClickModel,
          onBenchmarkClicked = onBenchmarkClicked,
          expanded = true,
          showBenchmarkButton = model.runtimeType == RuntimeType.LITERT_LM,
          serverStatus = serverStatus,
          activeModelName = activeModelName,
          onStopServer = onStopServer,
          onNavigateToSettings = onNavigateToSettings,
        )
      }

      // Empty state when filters yield no results
      if (filteredBuiltInModels.isEmpty() && filteredImportedModels.isEmpty() && !uiState.loadingModelAllowlist) {
        item(key = "empty_state") {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 64.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.no_models_match_search),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }

    // Import FAB
    val cdImportModelFab = stringResource(R.string.cd_import_model_button)
    FloatingActionButton(
      onClick = { showImportModelSheet = true },
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.secondary,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 16.dp)
        .semantics { contentDescription = cdImportModelFab },
    ) {
      Icon(Icons.Filled.Add, contentDescription = null)
    }

    // Snackbar
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(alignment = Alignment.BottomCenter)
        .padding(bottom = 32.dp),
    )

    // Gradient overlay at the bottom
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .background(
          Brush.verticalGradient(
            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface),
          ),
        )
        .align(Alignment.BottomCenter),
    )
  }

  // Import model bottom sheet
  if (showImportModelSheet) {
    ModalBottomSheet(onDismissRequest = { showImportModelSheet = false }, sheetState = sheetState) {
      Text(
        "Import model",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp),
      )
      val cbImportFromLocalFile = stringResource(R.string.cd_import_model_from_local_file_button)
      Box(
        modifier = Modifier
          .clickable {
            scope.launch {
              delay(200)
              showImportModelSheet = false
              val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
              }
              filePickerLauncher.launch(intent)
            }
          }
          .semantics {
            role = Role.Button
            contentDescription = cbImportFromLocalFile
          },
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        ) {
          Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = null)
          Text("From local model file", modifier = Modifier.clearAndSetSemantics {})
        }
      }
    }
  }

  // Import dialog
  if (showImportDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      ModelImportDialog(
        uri = uri,
        onDismiss = { showImportDialog = false },
        onDone = { info ->
          selectedImportedModelInfo.value = info
          showImportDialog = false
          showImportingDialog = true
        },
      )
    }
  }

  // Importing in progress dialog
  if (showImportingDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      selectedImportedModelInfo.value?.let { info ->
        ModelImportingDialog(
          uri = uri,
          info = info,
          onDismiss = { showImportingDialog = false },
          onDone = {
            viewModel.addImportedLlmModel(info = it)
            showImportingDialog = false
            scope.launch { snackbarHostState.showSnackbar("Model imported successfully") }
          },
        )
      }
    }
  }

  // Alert dialog for unsupported file type
  if (showUnsupportedFileTypeDialog) {
    AlertDialog(
      icon = {
        Icon(
          Icons.Rounded.Error,
          contentDescription = stringResource(R.string.cd_error),
          tint = MaterialTheme.colorScheme.error,
        )
      },
      onDismissRequest = { showUnsupportedFileTypeDialog = false },
      title = { Text("Unsupported file type") },
      text = { Text("Only \".task\" or \".litertlm\" file type is supported.") },
      confirmButton = {
        Button(onClick = { showUnsupportedFileTypeDialog = false }) {
          Text(stringResource(R.string.ok))
        }
      },
    )
  }

  // Alert dialog for unsupported web model
  if (showUnsupportedWebModelDialog) {
    AlertDialog(
      icon = {
        Icon(
          Icons.Rounded.Error,
          contentDescription = stringResource(R.string.cd_error),
          tint = MaterialTheme.colorScheme.error,
        )
      },
      onDismissRequest = { showUnsupportedWebModelDialog = false },
      title = { Text("Unsupported model type") },
      text = { Text("Looks like the model is a web-only model and is not supported by the app.") },
      confirmButton = {
        Button(onClick = { showUnsupportedWebModelDialog = false }) {
          Text(stringResource(R.string.ok))
        }
      },
    )
  }
}

@Composable
private fun ModelFilterChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val chipBgColor by animateColorAsState(
    targetValue = if (selected) OlliteRTPrimary.copy(alpha = 0.15f)
    else Color.Transparent,
    animationSpec = tween(200),
    label = "chip_bg",
  )
  val chipBorderColor by animateColorAsState(
    targetValue = if (selected) OlliteRTPrimary
    else MaterialTheme.colorScheme.outlineVariant,
    animationSpec = tween(200),
    label = "chip_border",
  )

  FilterChip(
    selected = selected,
    onClick = onClick,
    label = {
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) OlliteRTPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    colors = FilterChipDefaults.filterChipColors(
      selectedContainerColor = chipBgColor,
      containerColor = chipBgColor,
    ),
    border = FilterChipDefaults.filterChipBorder(
      enabled = true,
      selected = selected,
      borderColor = chipBorderColor,
      selectedBorderColor = chipBorderColor,
    ),
    shape = RoundedCornerShape(12.dp),
  )
}

// Helper function to get the file name from a URI
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
