/*
 * Copyright 2025 Google LLC
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

package com.ollitert.llm.server.ui.navigation

import android.util.Log
import android.widget.Toast
import com.ollitert.llm.server.R
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.ui.benchmark.BenchmarkScreen
import com.ollitert.llm.server.ui.common.DonateDialog
import com.ollitert.llm.server.ui.common.EngagementPromptDialog
import com.ollitert.llm.server.ui.common.GpuUnavailableDialog
import com.ollitert.llm.server.runtime.GpuAvailability
import com.ollitert.llm.server.ui.gettingstarted.GettingStartedScreen
import com.ollitert.llm.server.ui.modelmanager.ModelManagerScreen
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.ui.server.LogsScreen
import com.ollitert.llm.server.ui.server.ServerViewModel
import com.ollitert.llm.server.ui.modelrepos.RepositoryDetailScreen
import com.ollitert.llm.server.ui.modelrepos.RepositoryListScreen
import com.ollitert.llm.server.ui.modelrepos.RepositoryViewModel
import com.ollitert.llm.server.ui.settings.SettingsScreen
import com.ollitert.llm.server.ui.settings.SettingsViewModel
import com.ollitert.llm.server.ui.server.StatusScreen

private const val TAG = "OlliteRT.Nav"
private const val TRANSITION_DURATION_MS = 350

private fun enterTween(): FiniteAnimationSpec<IntOffset> =
  tween(TRANSITION_DURATION_MS, easing = EaseOutExpo)

private fun exitTween(): FiniteAnimationSpec<IntOffset> =
  tween(TRANSITION_DURATION_MS, easing = EaseOutExpo)

private fun AnimatedContentTransitionScope<*>.slideInLeft(): EnterTransition =
  slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, enterTween())

private fun AnimatedContentTransitionScope<*>.slideOutRight(): ExitTransition =
  slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, exitTween())

private fun activeDownloadsByRepoId(
  models: List<Model>,
  statuses: Map<String, ModelDownloadStatus>,
): Map<String, String> {
  val activeStatuses = setOf(ModelDownloadStatusType.IN_PROGRESS, ModelDownloadStatusType.UNZIPPING)
  val activeNames = statuses
    .filter { (_, status) -> status.status in activeStatuses }
    .keys
  return models
    .filter { it.name in activeNames }
    .associate { it.name to it.sourceRepositoryId }
}

@Composable
fun OlliteRTNavHost(
  navController: NavHostController,
  modelManagerViewModel: ModelManagerViewModel,
  serverViewModel: ServerViewModel,
  modifier: Modifier = Modifier,
  startDestination: OlliteRTRoute = OlliteRTRoute.Models,
  onSetTopBarTrailingContent: ((@Composable () -> Unit)?) -> Unit = {},
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current

  // Track app foreground state
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME ->
          modelManagerViewModel.setAppInForeground(foreground = true)
        Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE ->
          modelManagerViewModel.setAppInForeground(foreground = false)
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  // --- Engagement prompt (donation/support) ---
  // State lives at the NavHost level so it persists across tab navigation.
  // The server may reach RUNNING after the user navigates away from the Models screen.
  val engagementServerStatus by serverViewModel.status.collectAsStateWithLifecycle()
  var manualStartPending by remember { mutableStateOf(false) }
  var showEngagementPrompt by remember { mutableStateOf(false) }
  var showDonateFromEngagement by remember { mutableStateOf(false) }
  var showGpuServerStartDialog by remember { mutableStateOf(false) }

  LaunchedEffect(engagementServerStatus) {
    if (engagementServerStatus == ServerStatus.RUNNING && manualStartPending) {
      manualStartPending = false
      if (serverViewModel.shouldShowEngagementPrompt()) {
        serverViewModel.incrementEngagementPromptShowCount()
        showEngagementPrompt = true
      }
    } else if (engagementServerStatus == ServerStatus.ERROR || engagementServerStatus == ServerStatus.STOPPED) {
      manualStartPending = false
    }
  }

  // Engagement prompt dialog — shown after N successful manual server starts
  if (showEngagementPrompt && !showDonateFromEngagement) {
    EngagementPromptDialog(
      onSupportDevelopment = {
        // Open the donate dialog; engagement prompt will reappear when donate dialog closes
        showDonateFromEngagement = true
      },
      onStarOnGitHub = {
        showEngagementPrompt = false
        serverViewModel.setEngagementPromptPermanentlyDismissed()
        uriHandler.openUri(GitHubConfig.REPO_URL)
      },
      onDismiss = { permanentlyDismiss ->
        showEngagementPrompt = false
        if (permanentlyDismiss) {
          serverViewModel.setEngagementPromptPermanentlyDismissed()
        }
      },
    )
  }

  // Donate dialog — opened from the engagement prompt's "Support Development" button.
  // When closed, the engagement prompt reappears so the user can still tap "Star on GitHub".
  if (showDonateFromEngagement) {
    DonateDialog(onDismiss = { showDonateFromEngagement = false })
  }

  if (showGpuServerStartDialog) {
    GpuUnavailableDialog(
      onDismiss = { showGpuServerStartDialog = false },
      showDontShowAgain = true,
      onDontShowAgainChecked = { checked ->
        if (checked) {
          serverViewModel.setGpuUnavailableServerStartDismissed(true)
        }
      },
    )
  }

  NavHost(
    navController = navController,
    startDestination = startDestination,
    modifier = modifier,
    enterTransition = { fadeIn(tween(200)) },
    exitTransition = { fadeOut(tween(200)) },
  ) {
    // Models tab (main screen, ModelManagerScreen)
    composable<OlliteRTRoute.Models> { backStackEntry ->
      val reposChanged = backStackEntry.savedStateHandle
        .getStateFlow("reposChanged", false)
        .collectAsStateWithLifecycle()
      LaunchedEffect(reposChanged.value) {
        if (reposChanged.value) {
          modelManagerViewModel.loadModelAllowlist()
          backStackEntry.savedStateHandle["reposChanged"] = false
        }
      }
      val serverStatus by serverViewModel.status.collectAsStateWithLifecycle()
      val activeModelName by serverViewModel.activeModelName.collectAsStateWithLifecycle()
      val lastError by serverViewModel.lastError.collectAsStateWithLifecycle()
      ModelManagerScreen(
        viewModel = modelManagerViewModel,
        navigateUp = { navController.navigateUp() },
        onModelSelected = { model ->
          // Track manual server starts for the engagement prompt (counter lives in prefs,
          // observer lives at NavHost level so it fires regardless of which screen is active)
          serverViewModel.incrementManualStartCount()
          manualStartPending = true
          serverViewModel.startServer(modelName = model.name)
          if (!GpuAvailability.isOpenClAccessible &&
              !serverViewModel.isGpuUnavailableServerStartDismissed()) {
            showGpuServerStartDialog = true
          }
        },
        onBenchmarkClicked = { model ->
          navController.navigate(OlliteRTRoute.Benchmark(model.name)) { launchSingleTop = true }
        },
        serverStatus = serverStatus,
        activeModelName = activeModelName,
        lastError = lastError,
        onStopServer = { serverViewModel.stopServer() },
        onSwitchModel = { modelName ->
          // Track model switches the same way as fresh starts for the engagement prompt
          serverViewModel.incrementManualStartCount()
          manualStartPending = true
          serverViewModel.switchModel(modelName)
        },
        onNavigateToSettings = { navController.navigate(OlliteRTRoute.Settings) { launchSingleTop = true } },
        onNavigateToRepositories = { navController.navigate(OlliteRTRoute.Repositories) { launchSingleTop = true } },
      )
    }

    // Status tab
    composable<OlliteRTRoute.Status> {
      StatusScreen(
        serverViewModel = serverViewModel,
        onReloadModel = {
          // Reload the model atomically within the service (clean up old model, then re-init)
          serverViewModel.reloadServer()
        },
      )
    }

    // Logs tab
    composable<OlliteRTRoute.Logs> {
      LogsScreen()
    }

    // Settings screen
    composable<OlliteRTRoute.Settings>(
      enterTransition = { slideInLeft() },
      exitTransition = { slideOutRight() },
    ) { settingsBackStackEntry ->
      val reposChanged = settingsBackStackEntry.savedStateHandle
        .getStateFlow("reposChanged", false)
        .collectAsStateWithLifecycle()
      val settingsViewModel: SettingsViewModel = hiltViewModel()
      LaunchedEffect(reposChanged.value) {
        if (reposChanged.value) {
          settingsViewModel.refreshRepositoryCounts()
          settingsBackStackEntry.savedStateHandle["reposChanged"] = false
          navController.previousBackStackEntry?.savedStateHandle
            ?.set("reposChanged", true)
        }
      }
      val settingsServerStatus by serverViewModel.status.collectAsStateWithLifecycle()
      val downloadedModelNames = modelManagerViewModel.getAllDownloadedModels().map { it.name }
      val toastReloadPending = stringResource(R.string.toast_settings_saved_reload_pending)
      val toastRestarting = stringResource(R.string.toast_server_restarting)
      SettingsScreen(
        onBackClick = { navController.navigateUp() },
        serverStatus = settingsServerStatus,
        onRestartServer = {
          // Snapshot the status before issuing the reload so the toast reflects the
          // path the ViewModel will pick (queue when LOADING, immediate otherwise).
          val isLoading = settingsServerStatus == ServerStatus.LOADING
          serverViewModel.reloadServer()
          val msg = if (isLoading) toastReloadPending else toastRestarting
          Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        },
        onStopServer = { serverViewModel.stopServer() },
        onNavigateToModels = {
          navController.navigate(OlliteRTRoute.Models) {
            launchSingleTop = true
            popUpTo<OlliteRTRoute.Settings> { inclusive = true }
          }
        },
        downloadedModelNames = downloadedModelNames,
        onSetTopBarTrailingContent = onSetTopBarTrailingContent,
        onSettingsSaved = { modelManagerViewModel.refreshShowModelRecommendations() },
        onNavigateToRepositories = { navController.navigate(OlliteRTRoute.Repositories) { launchSingleTop = true } },
      )
    }

    // Repository list
    composable<OlliteRTRoute.Repositories>(
      enterTransition = { slideInLeft() },
      exitTransition = { slideOutRight() },
    ) { repoListBackStackEntry ->
      val detailChanged = repoListBackStackEntry.savedStateHandle
        .getStateFlow("detailReposChanged", false)
        .collectAsStateWithLifecycle()
      val repoViewModel: RepositoryViewModel = hiltViewModel()
      var detailMadeChanges by rememberSaveable { mutableStateOf(false) }
      LaunchedEffect(detailChanged.value) {
        if (detailChanged.value) {
          detailMadeChanges = true
          repoViewModel.loadRepositories()
          repoListBackStackEntry.savedStateHandle["detailReposChanged"] = false
        }
      }

      val modelManagerState by modelManagerViewModel.uiState.collectAsStateWithLifecycle()
      val downloadedModelRepoIds by remember {
        derivedStateOf<Map<String, String>> {
          modelManagerState.models
            .filter { it.isLlm && modelManagerState.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED }
            .associate { it.name to it.sourceRepositoryId }
        }
      }
      val downloadingModelRepoIds by remember {
        derivedStateOf { activeDownloadsByRepoId(modelManagerState.models, modelManagerState.modelDownloadStatus) }
      }
      RepositoryListScreen(
        viewModel = repoViewModel,
        onBackClick = { reposChanged ->
          if (reposChanged || detailMadeChanges) {
            navController.previousBackStackEntry?.savedStateHandle
              ?.set("reposChanged", true)
          }
          navController.popBackStack()
        },
        onRepoClick = { repoId ->
          navController.navigate(OlliteRTRoute.RepositoryDetail(repoId)) { launchSingleTop = true }
        },
        downloadedModelRepoIds = downloadedModelRepoIds,
        downloadingModelRepoIds = downloadingModelRepoIds,
        onCancelDownload = { modelName ->
          modelManagerViewModel.cancelModelDownloadByName(modelName)
        },
        onSetTopBarTrailingContent = onSetTopBarTrailingContent,
      )
    }

    // Repository detail
    composable<OlliteRTRoute.RepositoryDetail>(
      enterTransition = { slideInLeft() },
      exitTransition = { slideOutRight() },
    ) { detailBackStackEntry ->
      val args = detailBackStackEntry.toRoute<OlliteRTRoute.RepositoryDetail>()
      val repoId = args.repoId
      val detailViewModel: RepositoryViewModel = hiltViewModel()
      val detailModelManagerState by modelManagerViewModel.uiState.collectAsStateWithLifecycle()
      val detailDownloadingRepoIds by remember {
        derivedStateOf { activeDownloadsByRepoId(detailModelManagerState.models, detailModelManagerState.modelDownloadStatus) }
      }
      RepositoryDetailScreen(
        viewModel = detailViewModel,
        repoId = repoId,
        onBackClick = { reposChanged ->
          navController.previousBackStackEntry?.savedStateHandle
            ?.set("detailReposChanged", reposChanged)
          navController.popBackStack()
        },
        downloadingModelRepoIds = detailDownloadingRepoIds,
        onCancelDownload = { modelName ->
          modelManagerViewModel.cancelModelDownloadByName(modelName)
        },
      )
    }

    // Getting Started (onboarding)
    composable<OlliteRTRoute.GettingStarted> {
      GettingStartedScreen(
        onGetStartedClick = {
          modelManagerViewModel.completeOnboarding()
          navController.navigate(OlliteRTRoute.Models) {
            popUpTo<OlliteRTRoute.GettingStarted> { inclusive = true }
          }
        },
        shouldShowGpuDialog = { modelManagerViewModel.shouldShowGpuUnavailableDialog() },
        onGpuDialogConfirm = { modelManagerViewModel.onGpuUnavailableDialogConfirmed() },
      )
    }

    // Benchmark screen
    composable<OlliteRTRoute.Benchmark>(
      enterTransition = { slideInLeft() },
      exitTransition = { slideOutRight() },
    ) { backStackEntry ->
      val args = backStackEntry.toRoute<OlliteRTRoute.Benchmark>()
      val modelName = args.modelName
      // Collect the model list as snapshot state: after process death the route is
      // restored before the ViewModel finishes loading, and a plain uiState.value
      // read here would create no recomposition trigger — leaving the screen blank
      // forever even once the models arrive.
      val uiState by modelManagerViewModel.uiState.collectAsStateWithLifecycle()
      uiState.models.find { it.name == modelName }?.let { model ->
        BenchmarkScreen(
          initialModel = model,
          modelManagerViewModel = modelManagerViewModel,
          onBackClicked = { navController.navigateUp() },
        )
      }
    }
  }

  // Handle incoming deep links. Must run as a side effect, not in composition —
  // the composable body can execute many times per frame batch.
  val activity = androidx.activity.compose.LocalActivity.current
  LaunchedEffect(activity) {
    val data = activity?.intent?.data ?: return@LaunchedEffect
    activity.intent.data = null
    Log.d(TAG, "deep link: $data")
    if (data.toString() == "com.ollitert.llm.server://global_model_manager") {
      navController.navigate(OlliteRTRoute.Models) { launchSingleTop = true }
    }
  }
}
