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

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.allowlist.OFFICIAL_REPO_ID
import com.ollitert.llm.server.data.model.RuntimeType
import com.ollitert.llm.server.data.allowlist.UNKNOWN_REPO_LABEL
import com.ollitert.llm.server.ui.common.OlliteSearchBar
import com.ollitert.llm.server.ui.common.SCREEN_CONTENT_MAX_WIDTH
import com.ollitert.llm.server.ui.common.ShimmerModelCard
import com.ollitert.llm.server.ui.common.matchesSearchQuery
import com.ollitert.llm.server.ui.modelmanager.components.ModelItem
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
  viewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  onModelSelected: (Model) -> Unit,
  onBenchmarkClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
  onNavigateToSettings: () -> Unit = {},
  onNavigateToRepositories: () -> Unit = {},
  serverStatus: ServerStatus = ServerStatus.STOPPED,
  activeModelName: String? = null,
  lastError: String? = null,
  onStopServer: () -> Unit = {},
  onSwitchModel: (String) -> Unit = {},
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val showRecommendations by viewModel.showModelRecommendations.collectAsStateWithLifecycle()
  val dialogState = rememberModelManagerDialogState()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }

  // Show a toast when a manual retry fails to reach the model server
  LaunchedEffect(viewModel) {
    viewModel.toastErrorEvents.collect { message ->
      Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
  }

  // Pull-to-refresh: separate from initial load so the indicator only appears on swipe,
  // with a minimum visible duration so the spinner doesn't flash and vanish.
  var isManualRefreshing by remember { mutableStateOf(false) }
  LaunchedEffect(uiState.loadingModelAllowlist, isManualRefreshing) {
    if (isManualRefreshing && !uiState.loadingModelAllowlist) {
      delay(500)
      isManualRefreshing = false
    }
  }

  // Permission state — re-evaluated on every resume so the banner disappears
  // after the user grants permissions in system settings and returns to the app.
  val permissionState = rememberModelManagerPermissionState(context)

  // Search, filter, and sort state — rememberSaveable so the user's filters
  // survive rotation and process death (they represent explicit intent).
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var activeFilter by rememberSaveable { mutableStateOf(ModelFilter.ALL) }
  var activeCapabilities by rememberSaveable(stateSaver = capabilitySetSaver) {
    mutableStateOf(emptySet<CapabilityFilter>())
  }
  var showMoreFilters by rememberSaveable { mutableStateOf(false) }
  var activeSort by rememberSaveable { mutableStateOf(ModelSort.DEFAULT) }
  var sortAscending by rememberSaveable { mutableStateOf(true) }
  var showSortDropdown by rememberSaveable { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current

  // Derive model lists reactively from uiState — any change to the flat model list or download
  // status automatically propagates.
  val sortedAllModels by remember {
    derivedStateOf {
      val downloadStatus = uiState.modelDownloadStatus
      val allModels = uiState.models
      allModels.sortedWith(
        compareBy<Model> { model ->
          val isDownloaded = downloadStatus[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
          when {
            isDownloaded -> 0
            model.pinned && showRecommendations && model.incompatibilityReason == null -> 1
            else -> 2
          }
        }.thenBy { it.displayName.ifEmpty { it.name } }
      )
    }
  }
  val builtInModels by remember { derivedStateOf { sortedAllModels.filter { !it.imported } } }
  val importedModels by remember { derivedStateOf { sortedAllModels.filter { it.imported } } }
  val availableCapabilityFilters by remember {
    derivedStateOf {
      val allCaps = sortedAllModels.flatMapTo(mutableSetOf()) { it.capabilities }
      CapabilityFilter.entries.filter { it.capability in allCaps }
    }
  }

  // Reset to ALL if the Imported filter is active but all imported models have been deleted
  LaunchedEffect(importedModels.size) {
    if (activeFilter == ModelFilter.IMPORTED && importedModels.isEmpty()) {
      activeFilter = ModelFilter.ALL
    }
  }

  // Filtered and sorted models
  val filteredBuiltInModels by remember(searchQuery, activeFilter, activeCapabilities, activeSort, sortAscending, builtInModels) {
    derivedStateOf {
      filterAndSortModels(
        models = builtInModels,
        searchQuery = searchQuery,
        matchesFilter = { model ->
          // Built-in models are hidden when Imported filter is active
          when (activeFilter) {
            ModelFilter.ALL -> true
            ModelFilter.DOWNLOADED -> uiState.modelDownloadStatus[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
            ModelFilter.AVAILABLE -> uiState.modelDownloadStatus[model.name]?.status != ModelDownloadStatusType.SUCCEEDED
            ModelFilter.IMPORTED -> false
          }
        },
        activeCapabilities = activeCapabilities,
        activeSort = activeSort,
        sortAscending = sortAscending,
      )
    }
  }

  val filteredImportedModels by remember(searchQuery, activeFilter, activeCapabilities, activeSort, sortAscending, importedModels) {
    derivedStateOf {
      filterAndSortModels(
        models = importedModels,
        searchQuery = searchQuery,
        matchesFilter = {
          // Imported models are always downloaded — hide only for "Available" filter
          activeFilter != ModelFilter.AVAILABLE
        },
        activeCapabilities = activeCapabilities,
        activeSort = activeSort,
        sortAscending = sortAscending,
      )
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
      val isServerActive = serverStatus == ServerStatus.RUNNING
      val isDifferentModel = activeModelName != null && !activeModelName.equals(model.name, ignoreCase = true)
      if (isServerActive && isDifferentModel) {
        // Ask user to confirm switching models
        dialogState.pendingSwitchModel = model
        dialogState.showSwitchModelDialog = true
      } else {
        onModelSelected(model)
      }
    }
  }

  PullToRefreshBox(
    isRefreshing = isManualRefreshing,
    onRefresh = {
      isManualRefreshing = true
      viewModel.loadModelAllowlist(isManualRetry = true)
    },
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
        ModelFilterSection(
          activeFilter = activeFilter,
          onFilterSelected = { activeFilter = it },
          hasImportedModels = importedModels.isNotEmpty(),
          showMoreFilters = showMoreFilters,
          onToggleMoreFilters = { showMoreFilters = !showMoreFilters },
          activeCapabilities = activeCapabilities,
          onToggleCapability = { cap ->
            activeCapabilities = if (cap in activeCapabilities) activeCapabilities - cap else activeCapabilities + cap
          },
          availableCapabilityFilters = availableCapabilityFilters,
          activeSort = activeSort,
          sortAscending = sortAscending,
          showSortDropdown = showSortDropdown,
          onToggleSortDropdown = { showSortDropdown = !showSortDropdown },
          onDismissSortDropdown = { showSortDropdown = false },
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

      // All repos disabled — no downloaded models: centered empty state
      if (uiState.allReposDisabled && uiState.models.isEmpty()) {
        item(key = "all_repos_disabled") {
          Box(
            modifier = Modifier
              .fillParentMaxHeight(0.7f)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = stringResource(R.string.no_repos_enabled),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )
              Spacer(modifier = Modifier.height(16.dp))
              Button(onClick = onNavigateToRepositories) {
                Text(stringResource(R.string.button_go_to_model_sources))
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
      if (permissionState.hasMissingPermissions && !uiState.loadingModelAllowlist) {
        item(key = "permission_warning_banner") {
          PermissionWarningBanner(permissionState = permissionState, context = context)
        }
      }

      // Info banner when some/all repos are offline — the app works fully offline,
      // this just lets the user know the list may not include newer models.
      if (uiState.loadingModelAllowlistError.isNotEmpty() && uiState.models.isNotEmpty() && !uiState.loadingModelAllowlist) {
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
              text = uiState.loadingModelAllowlistError,
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
          showRecommendations = showRecommendations,
        )
      }

      // Built-in models grouped by source repository (built-in repo always first)
      val groupedByRepoId = filteredBuiltInModels.groupBy { it.sourceRepositoryId.ifEmpty { "unknown" } }
      val sortedRepoIds = groupedByRepoId.keys.sortedWith(
        compareByDescending<String> { it == OFFICIAL_REPO_ID }.thenBy { it }
      )
      val needsHeaders = sortedRepoIds.size > 1 || filteredImportedModels.isNotEmpty()
      for ((index, repoId) in sortedRepoIds.withIndex()) {
        val repoModels = groupedByRepoId[repoId] ?: continue
        if (needsHeaders) {
          val repoDisplayName = repoModels.first().sourceRepository.ifEmpty { UNKNOWN_REPO_LABEL }
          val isFirst = index == 0 && filteredImportedModels.isEmpty()
          item(key = "repo_header_$repoId") {
            Text(
              repoDisplayName,
              color = MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = if (isFirst) 8.dp else 24.dp, bottom = 8.dp),
            )
          }
        }
        items(repoModels, key = { "builtin_${it.name}" }) { model ->
          ModelItem(
            model = model,
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
            showRecommendations = showRecommendations,
          )
        }
      }

      // All repos disabled banner — shown below downloaded models
      if (uiState.allReposDisabled && uiState.models.isNotEmpty()) {
        item(key = "all_repos_disabled_banner") {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = stringResource(R.string.no_repos_enabled),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onNavigateToRepositories) {
              Text(stringResource(R.string.button_go_to_model_sources))
            }
          }
        }
      }

      // Footer: some models hidden by version compatibility
      val hasVisibleModels = filteredBuiltInModels.isNotEmpty() || filteredImportedModels.isNotEmpty()
      if (hasVisibleModels && uiState.droppedByVersionFilter > 0 && !uiState.loadingModelAllowlist) {
        item(key = "version_filter_footer") {
          val count = uiState.droppedByVersionFilter
          val resources = LocalResources.current
          val modelsText = resources.getQuantityString(R.plurals.models_count, count, count)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 16.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = stringResource(R.string.models_hidden_by_version, modelsText),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      // Empty state — distinguish between filter mismatch and allowlist load failure
      if (filteredBuiltInModels.isEmpty() && filteredImportedModels.isEmpty() && !uiState.loadingModelAllowlist && !uiState.allReposDisabled) {
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
            val hasActiveSearchOrFilter = searchQuery.isNotEmpty() ||
              activeFilter != ModelFilter.ALL ||
              activeCapabilities.isNotEmpty()
            val requiredVersion = uiState.requiredVersion
            val emptyText = when {
              hasActiveSearchOrFilter -> stringResource(R.string.no_models_match_search)
              uiState.emptyReason == ModelEmptyReason.VERSION_TOO_OLD ->
                stringResource(R.string.no_models_version_too_old, requiredVersion ?: "?")
              uiState.totalBeforeFilters == 0 ->
                stringResource(R.string.no_models_empty_source)
              else -> stringResource(R.string.no_models_not_compatible)
            }
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 64.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = emptyText,
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
          onClick = { dialogState.showImportModelSheet = true },
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

  // Name lookups for dialog validation — derived once per model-list change
  // instead of rebuilding sets on every recomposition.
  val importedModelNames = remember(importedModels) { importedModels.map { it.name }.toSet() }
  val allowlistModelNames =
    remember(uiState.models) { uiState.models.filter { !it.imported }.map { it.name }.toSet() }

  ModelManagerDialogs(
    state = dialogState,
    viewModel = viewModel,
    importedModelNames = importedModelNames,
    allowlistModelNames = allowlistModelNames,
    snackbarHostState = snackbarHostState,
    serverStatus = serverStatus,
    activeModelName = activeModelName,
    onSwitchModel = onSwitchModel,
  )
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

/** Persists capability-filter selections across rotation and process death. */
private val capabilitySetSaver =
  listSaver<Set<CapabilityFilter>, String>(
    save = { set -> set.map { filter -> filter.name } },
    restore = { saved ->
      saved.mapNotNull { name -> CapabilityFilter.entries.firstOrNull { it.name == name } }.toSet()
    },
  )

/** Shared search + capability-filter + sort pipeline for built-in and imported model lists. */
private fun filterAndSortModels(
  models: List<Model>,
  searchQuery: String,
  matchesFilter: (Model) -> Boolean,
  activeCapabilities: Set<CapabilityFilter>,
  activeSort: ModelSort,
  sortAscending: Boolean,
): List<Model> =
  models
    .filter { model ->
      matchesSearchQuery(buildModelSearchableText(model), searchQuery) &&
        matchesFilter(model) &&
        modelMatchesCapabilityFilters(model, activeCapabilities)
    }
    .let { filtered ->
      when (activeSort) {
        ModelSort.DEFAULT -> filtered // preserve original order
        ModelSort.ALPHABETICAL ->
          if (sortAscending) filtered.sortedBy { it.displayName.ifEmpty { it.name }.lowercase() }
          else filtered.sortedByDescending { it.displayName.ifEmpty { it.name }.lowercase() }
        ModelSort.SIZE ->
          if (sortAscending) filtered.sortedBy { it.totalBytes }
          else filtered.sortedByDescending { it.totalBytes }
      }
    }
