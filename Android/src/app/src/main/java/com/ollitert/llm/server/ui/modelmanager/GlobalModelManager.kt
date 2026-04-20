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

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ollitert.llm.server.ui.common.OlliteSearchBar
import com.ollitert.llm.server.ui.common.matchesSearchQuery
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Warning
import com.ollitert.llm.server.ui.common.ErrorAlertDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelCapability
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.RuntimeType
import com.ollitert.llm.server.data.Task
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.ui.common.SCREEN_CONTENT_MAX_WIDTH
import com.ollitert.llm.server.ui.common.SHEET_MAX_WIDTH
import com.ollitert.llm.server.ui.common.ShimmerModelCard
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.modelitem.ModelItem
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.ui.theme.OlliteRTDeepBlue
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTWarningContainer
import com.ollitert.llm.server.ui.theme.OlliteRTWarningText
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
  IMPORTED,
}

/** Capability filter for models. */
enum class CapabilityFilter(val labelResId: Int, val capability: ModelCapability) {
  VISION(R.string.capability_vision, ModelCapability.VISION),
  AUDIO(R.string.capability_audio, ModelCapability.AUDIO),
  THINKING(R.string.capability_thinking, ModelCapability.THINKING),
}

/** Sort mode for the models list. */
enum class ModelSort(val labelResId: Int) {
  DEFAULT(R.string.models_sort_default),
  ALPHABETICAL(R.string.models_sort_name),
  SIZE(R.string.models_sort_size),
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
  lastError: String? = null,
  onStopServer: () -> Unit = {},
  onSwitchModel: (String) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

  // Show a toast when a manual retry fails to reach the model server
  LaunchedEffect(viewModel) {
    viewModel.toastErrorEvents.collect { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  // Switch model confirmation state
  var showSwitchModelDialog by remember { mutableStateOf(false) }
  var pendingSwitchModel by remember { mutableStateOf<Model?>(null) }
  var pendingSwitchTask by remember { mutableStateOf<Task?>(null) }

  // Re-check permissions when the user returns from system settings.
  // A simple counter bumped on ON_RESUME forces recomposition of permission-dependent UI.
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  var resumeCount by remember { mutableStateOf(0) }
  androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        resumeCount++
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // Permission state — re-evaluated on every resume so the banner disappears
  // after the user grants permissions in system settings and returns to the app.
  val missingNotifPermission by remember(resumeCount) {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
      } else false
    )
  }
  val missingBatteryExemption by remember(resumeCount) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    mutableStateOf(pm?.let { !it.isIgnoringBatteryOptimizations(context.packageName) } ?: true)
  }

  // Search, filter, and sort state
  var searchQuery by remember { mutableStateOf("") }
  var activeFilter by remember { mutableStateOf(ModelFilter.ALL) }
  var activeCapabilities by remember { mutableStateOf(emptySet<CapabilityFilter>()) }
  var showMoreFilters by remember { mutableStateOf(false) }
  var activeSort by remember { mutableStateOf(ModelSort.DEFAULT) }
  var sortAscending by remember { mutableStateOf(true) }
  var showSortDropdown by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current

  val filePickerLauncher: ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == android.app.Activity.RESULT_OK) {
        result.data?.data?.let { uri ->
          val fileName = getFileName(context = context, uri = uri)
          Log.d(TAG, "Selected file: $fileName")
          if (fileName != null && !fileName.endsWith(".litertlm")) {
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

  // Derive model lists reactively from uiState — any change to tasks, models, or download
  // status automatically propagates without manual trigger bumping.
  val sortedAllModels by remember {
    derivedStateOf {
      // Read modelImportingUpdateTrigger so this derivedStateOf invalidates when
      // in-place task.models mutations are signaled via trigger bump (MutableStateFlow
      // conflates structurally-equal values, so the trigger makes them differ).
      @Suppress("UNUSED_VARIABLE")
      val importTrigger = uiState.modelImportingUpdateTrigger
      val downloadStatus = uiState.modelDownloadStatus
      val allModels = uiState.tasks.flatMap { it.models }.distinctBy { it.name }
      allModels.sortedWith(
        compareBy<Model> { model ->
          val name = model.displayName.ifEmpty { model.name }
          val isGemma4 = name.contains("gemma-4", ignoreCase = true) || name.contains("gemma 4", ignoreCase = true)
          val isDownloaded = downloadStatus[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
          when {
            isDownloaded -> 0
            isGemma4 -> 1
            else -> 2
          }
        }.thenBy { it.displayName.ifEmpty { it.name } }
      )
    }
  }
  val builtInModels by remember { derivedStateOf { sortedAllModels.filter { !it.imported } } }
  val importedModels by remember { derivedStateOf { sortedAllModels.filter { it.imported } } }

  // Reset to ALL if the Imported filter is active but all imported models have been deleted
  LaunchedEffect(importedModels.size) {
    if (activeFilter == ModelFilter.IMPORTED && importedModels.isEmpty()) {
      activeFilter = ModelFilter.ALL
    }
  }

  // Filtered and sorted models
  val filteredBuiltInModels by remember(searchQuery, activeFilter, activeCapabilities, activeSort, sortAscending, builtInModels) {
    derivedStateOf {
      builtInModels.filter { model ->
        val matchesSearch = matchesSearchQuery(buildModelSearchableText(model), searchQuery)
        val matchesFilter = when (activeFilter) {
          ModelFilter.ALL -> true
          ModelFilter.DOWNLOADED -> uiState.modelDownloadStatus[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
          ModelFilter.AVAILABLE -> uiState.modelDownloadStatus[model.name]?.status != ModelDownloadStatusType.SUCCEEDED
          ModelFilter.IMPORTED -> false // built-in models are hidden when Imported filter is active
        }
        val matchesCaps = modelMatchesCapabilityFilters(model, activeCapabilities)
        matchesSearch && matchesFilter && matchesCaps
      }.let { filtered ->
        when (activeSort) {
          ModelSort.DEFAULT -> filtered // preserve original order
          ModelSort.ALPHABETICAL -> if (sortAscending) filtered.sortedBy { it.displayName.ifEmpty { it.name }.lowercase() }
            else filtered.sortedByDescending { it.displayName.ifEmpty { it.name }.lowercase() }
          ModelSort.SIZE -> if (sortAscending) filtered.sortedBy { it.totalBytes }
            else filtered.sortedByDescending { it.totalBytes }
        }
      }
    }
  }

  val filteredImportedModels by remember(searchQuery, activeFilter, activeCapabilities, activeSort, sortAscending, importedModels) {
    derivedStateOf {
      importedModels.filter { model ->
        val matchesSearch = matchesSearchQuery(buildModelSearchableText(model), searchQuery)
        val matchesCaps = modelMatchesCapabilityFilters(model, activeCapabilities)
        // Imported models are always downloaded — hide only for "Available" filter
        activeFilter != ModelFilter.AVAILABLE && matchesSearch && matchesCaps
      }.let { filtered ->
        when (activeSort) {
          ModelSort.DEFAULT -> filtered
          ModelSort.ALPHABETICAL -> if (sortAscending) filtered.sortedBy { it.displayName.ifEmpty { it.name }.lowercase() }
            else filtered.sortedByDescending { it.displayName.ifEmpty { it.name }.lowercase() }
          ModelSort.SIZE -> if (sortAscending) filtered.sortedBy { it.totalBytes }
            else filtered.sortedByDescending { it.totalBytes }
        }
      }
    }
  }

  val pleaseWaitModelLoadingText = stringResource(R.string.label_please_wait_model_loading)
  val handleClickModel: (Model) -> Unit = { model ->
    if (serverStatus == ServerStatus.LOADING) {
      // Block model selection while a model is loading to prevent OOM from concurrent warmups
      Toast.makeText(
        context,
        pleaseWaitModelLoadingText,
        Toast.LENGTH_SHORT,
      ).show()
    } else {
      val tasks = viewModel.uiState.value.tasks
      val tasksForModel = tasks.filter { task -> task.models.any { it.name == model.name } }
      if (tasksForModel.isNotEmpty()) {
        val isServerActive = serverStatus == ServerStatus.RUNNING
        val isDifferentModel = activeModelName != null && !activeModelName.equals(model.name, ignoreCase = true)
        if (isServerActive && isDifferentModel) {
          // Ask user to confirm switching models
          pendingSwitchModel = model
          pendingSwitchTask = tasksForModel[0]
          showSwitchModelDialog = true
        } else {
          onModelSelected(tasksForModel[0], model)
        }
      }
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .pointerInput(Unit) {
        detectTapGestures { focusManager.clearFocus() }
      },
  ) {
    LazyColumn(
      modifier = Modifier
        .background(MaterialTheme.colorScheme.surface)
        .widthIn(max = SCREEN_CONTENT_MAX_WIDTH)
        .fillMaxWidth()
        .align(Alignment.TopCenter)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
    ) {
      // Search bar
      item(key = "search_bar") {
        OlliteSearchBar(
          query = searchQuery,
          onQueryChange = { searchQuery = it },
          placeholderRes = R.string.search_models,
          clearContentDescriptionRes = R.string.models_clear_search,
        )
      }

      // Filter chips + sort button
      item(key = "filter_chips") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          // Outer Row: scrollable chips on the left, fixed action buttons pinned right.
          // weight() doesn't work inside a horizontalScroll Row (infinite width),
          // so the chips and buttons must be in separate siblings.
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            // Scrollable filter chips — takes remaining space
            Row(
              modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
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
              // Show "Imported" filter chip only when imported models exist
              if (importedModels.isNotEmpty()) {
                ModelFilterChip(
                  label = stringResource(R.string.filter_imported),
                  selected = activeFilter == ModelFilter.IMPORTED,
                  onClick = { activeFilter = ModelFilter.IMPORTED },
                )
              }
            }
            // Fixed action buttons — always pinned to the right edge
            Row(
              horizontalArrangement = Arrangement.spacedBy(4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              // "More Filters" toggle
              MoreFiltersButton(
                active = showMoreFilters || activeCapabilities.isNotEmpty(),
                onClick = { showMoreFilters = !showMoreFilters },
              )
              SortButton(
                activeSort = activeSort,
                sortAscending = sortAscending,
                showDropdown = showSortDropdown,
                onToggleDropdown = { showSortDropdown = !showSortDropdown },
                onDismissDropdown = { showSortDropdown = false },
                onSortSelected = { sort ->
                  if (sort == ModelSort.DEFAULT) {
                    activeSort = sort
                  } else if (activeSort == sort) {
                    sortAscending = !sortAscending
                  } else {
                    activeSort = sort
                    sortAscending = true
                  }
                  showSortDropdown = false
                },
              )
            }
          }
          // Expandable capability filters
          AnimatedVisibility(
            visible = showMoreFilters,
            enter = expandVertically(),
            exit = shrinkVertically(),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
              // Capability section
              Text(
                stringResource(R.string.models_filter_capabilities),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CapabilityFilter.entries.forEach { cap ->
                  val isSelected = cap in activeCapabilities
                  ModelFilterChip(
                    label = stringResource(cap.labelResId),
                    selected = isSelected,
                    onClick = {
                      activeCapabilities = if (isSelected) activeCapabilities - cap
                      else activeCapabilities + cap
                    },
                  )
                }
              }
            }
          }
        }
      }

      // Shimmer loading placeholders
      if (uiState.loadingModelAllowlist && builtInModels.isEmpty()) {
        items(3, key = { "shimmer_$it" }) {
          ShimmerModelCard()
        }
      }

      // Permission warning banner — shown when notification or battery optimization
      // permissions are missing, which can cause the OS to kill the server in the background.
      // State is hoisted above and re-checked on every ON_RESUME lifecycle event.
      if ((missingNotifPermission || missingBatteryExemption) && !uiState.loadingModelAllowlist) {
        item(key = "permission_warning_banner") {
          // Build a message tailored to which permissions are missing
          val notifLabel = stringResource(R.string.models_permission_notification)
          val batteryLabel = stringResource(R.string.models_permission_battery)
          val issues = buildList {
            if (missingNotifPermission) add(notifLabel)
            if (missingBatteryExemption) add(batteryLabel)
          }
          val issueText = issues.joinToString(" and ")

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .padding(bottom = 12.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(OlliteRTWarningContainer)
              .clickable {
                // Open app settings so the user can grant the missing permissions
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
              }
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Warning,
              contentDescription = null,
              tint = OlliteRTWarningText,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = stringResource(R.string.models_permission_warning, issueText),
              style = MaterialTheme.typography.bodySmall,
              color = OlliteRTWarningText,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }

      // Info banner when model list was loaded from cache/bundle instead of network.
      // The app works fully offline — this just lets the user know the list may not include
      // newer models that were published after their last successful fetch.
      if (uiState.allowlistSource != null && uiState.allowlistSource != AllowlistSource.NETWORK && !uiState.loadingModelAllowlist) {
        item(key = "offline_info_banner") {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .padding(bottom = 12.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHigh)
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.CloudOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = stringResource(
                if (uiState.allowlistSource == AllowlistSource.DISK_CACHE)
                  R.string.models_offline_cached
                else
                  R.string.models_offline_bundled
              ),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            Text(
              text = stringResource(R.string.retry),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { viewModel.loadModelAllowlist(isManualRetry = true) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        }
      }

      // Imported models section — shown first so user's own models are immediately visible
      if (filteredImportedModels.isNotEmpty()) {
        item(key = "imported_models_label") {
          Text(
            stringResource(R.string.model_list_imported_models_title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
              .padding(horizontal = 16.dp)
              .padding(top = 8.dp, bottom = 8.dp),
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
          showBenchmarkButton = model.runtimeType == RuntimeType.LITERT_LM,
          serverStatus = serverStatus,
          activeModelName = activeModelName,
          lastError = lastError,
          onStopServer = onStopServer,
          onNavigateToSettings = onNavigateToSettings,
          searchQuery = searchQuery,
        )
      }

      // Built-in models section header — only shown when imported models are also visible
      if (filteredBuiltInModels.isNotEmpty() && filteredImportedModels.isNotEmpty()) {
        item(key = "built_in_models_label") {
          Text(
            stringResource(R.string.model_list_built_in_models_title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
              .padding(horizontal = 16.dp)
              .padding(top = 24.dp, bottom = 8.dp),
          )
        }
      }

      // Built-in models
      items(filteredBuiltInModels, key = { "builtin_${it.name}" }) { model ->
        ModelItem(
          model = model,
          task = null,
          modelManagerViewModel = viewModel,
          onModelClicked = handleClickModel,
          onBenchmarkClicked = onBenchmarkClicked,
          showBenchmarkButton = model.runtimeType == RuntimeType.LITERT_LM,
          serverStatus = serverStatus,
          activeModelName = activeModelName,
          lastError = lastError,
          onStopServer = onStopServer,
          onNavigateToSettings = onNavigateToSettings,
          searchQuery = searchQuery,
        )
      }

      // Empty state — distinguish between filter mismatch and allowlist load failure
      if (filteredBuiltInModels.isEmpty() && filteredImportedModels.isEmpty() && !uiState.loadingModelAllowlist) {
        item(key = "empty_state") {
          if (uiState.loadingModelAllowlistError.isNotEmpty()) {
            // Allowlist failed to load — show error with retry
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = stringResource(R.string.models_failed_to_load),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = uiState.loadingModelAllowlistError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
              )
              Spacer(modifier = Modifier.height(16.dp))
              androidx.compose.material3.Button(
                onClick = {
                  viewModel.clearLoadModelAllowlistError()
                  viewModel.loadModelAllowlist()
                },
              ) {
                Text(stringResource(R.string.retry))
              }
            }
          } else {
            // Filters/search yielded no results
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
    }

    // Import FAB
    val cdImportModelFab = stringResource(R.string.cd_import_model_button)
    Box(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 16.dp, bottom = 16.dp),
    ) {
      TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(stringResource(R.string.label_import_model)) } },
        state = rememberTooltipState(),
      ) {
        FloatingActionButton(
          onClick = { showImportModelSheet = true },
          containerColor = OlliteRTPrimary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier
            .semantics { contentDescription = cdImportModelFab },
        ) {
          Icon(Icons.Filled.Add, contentDescription = null)
        }
      }
    }

    // Snackbar
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(alignment = Alignment.BottomCenter)
        .padding(bottom = 32.dp),
    )

  }

  // Import model bottom sheet
  if (showImportModelSheet) {
    ModalBottomSheet(onDismissRequest = { showImportModelSheet = false }, sheetState = sheetState, sheetMaxWidth = SHEET_MAX_WIDTH) {
      Text(
        stringResource(R.string.label_import_model),
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
          Text(stringResource(R.string.label_import_from_local_file), modifier = Modifier.clearAndSetSemantics {})
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
        existingImportedModelNames = importedModels.map { it.name }.toSet(),
      )
    }
  }

  // Importing in progress dialog
  val modelImportedText = stringResource(R.string.toast_model_imported)
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
            scope.launch { snackbarHostState.showSnackbar(modelImportedText) }
          },
        )
      }
    }
  }

  // Alert dialog for unsupported file type
  if (showUnsupportedFileTypeDialog) {
    ErrorAlertDialog(
      title = stringResource(R.string.dialog_unsupported_file_type_title),
      text = stringResource(R.string.dialog_unsupported_file_type_body),
      onDismiss = { showUnsupportedFileTypeDialog = false },
    )
  }

  // Alert dialog for unsupported web model
  if (showUnsupportedWebModelDialog) {
    ErrorAlertDialog(
      title = stringResource(R.string.dialog_unsupported_web_model_title),
      text = stringResource(R.string.dialog_unsupported_web_model_body),
      onDismiss = { showUnsupportedWebModelDialog = false },
    )
  }

  // Confirmation dialog when switching models while server is running
  val switchModel = pendingSwitchModel
  val switchTask = pendingSwitchTask
  if (showSwitchModelDialog && switchModel != null && switchTask != null) {
    AlertDialog(
      onDismissRequest = {
        showSwitchModelDialog = false
        pendingSwitchModel = null
        pendingSwitchTask = null
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
          showSwitchModelDialog = false
          pendingSwitchModel = null
          pendingSwitchTask = null
          onSwitchModel(switchModel.name)
        }) {
          Text(stringResource(R.string.button_switch))
        }
      },
      dismissButton = {
        Button(
          onClick = {
            showSwitchModelDialog = false
            pendingSwitchModel = null
            pendingSwitchTask = null
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
private fun ModelFilterChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val chipBgColor by animateColorAsState(
    targetValue = if (selected) OlliteRTPrimary
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
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
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
    shape = if (selected) RoundedCornerShape(12.dp) else RoundedCornerShape(50),
  )
}

@Composable
private fun MoreFiltersButton(
  active: Boolean,
  onClick: () -> Unit,
) {
  TooltipIconButton(
    icon = Icons.Outlined.FilterList,
    tooltip = stringResource(R.string.models_tooltip_more_filters),
    onClick = onClick,
    backgroundColor = if (active) OlliteRTPrimary else MaterialTheme.colorScheme.surfaceContainerHigh,
    tint = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
private fun SortButton(
  activeSort: ModelSort,
  sortAscending: Boolean,
  showDropdown: Boolean,
  onToggleDropdown: () -> Unit,
  onDismissDropdown: () -> Unit,
  onSortSelected: (ModelSort) -> Unit,
) {
  Box {
    TooltipIconButton(
      icon = Icons.AutoMirrored.Outlined.Sort,
      tooltip = stringResource(R.string.models_tooltip_sort),
      onClick = { onToggleDropdown() },
      backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    DropdownMenu(
      expanded = showDropdown,
      onDismissRequest = onDismissDropdown,
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = RoundedCornerShape(12.dp),
    ) {
      ModelSort.entries.forEach { sort ->
        val isActive = activeSort == sort
        DropdownMenuItem(
          text = {
            Text(
              stringResource(sort.labelResId),
              color = if (isActive) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
            )
          },
          onClick = { onSortSelected(sort) },
          trailingIcon = {
            if (isActive && sort != ModelSort.DEFAULT) {
              Icon(
                if (sortAscending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                tint = OlliteRTPrimary,
                modifier = Modifier.size(18.dp),
              )
            } else if (isActive) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = OlliteRTPrimary,
                modifier = Modifier.size(18.dp),
              )
            }
          },
        )
      }
    }
  }
}

/** Builds a combined searchable string from model name, display name, description, and capabilities. */
private fun buildModelSearchableText(model: Model): String = buildString {
  append(model.displayName)
  append(' ')
  append(model.name)
  if (model.info.isNotEmpty()) {
    append(' ')
    append(model.info)
  }
  if (model.isLlm) append(" text")
  for (cap in model.capabilities) {
    append(" ")
    append(cap.name.lowercase())
  }
}

/** Returns true if the model has all of the selected capability filters. */
private fun modelMatchesCapabilityFilters(model: Model, caps: Set<CapabilityFilter>): Boolean {
  if (caps.isEmpty()) return true
  return caps.all { it.capability in model.capabilities }
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
