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
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.ui.benchmark.BenchmarkScreen
import com.ollitert.llm.server.ui.common.DonateDialog
import com.ollitert.llm.server.ui.common.EngagementPromptDialog
import com.ollitert.llm.server.ui.gettingstarted.GettingStartedScreen
import com.ollitert.llm.server.ui.modelmanager.GlobalModelManager
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.ui.server.LogsScreen
import com.ollitert.llm.server.ui.server.ServerViewModel
import com.ollitert.llm.server.ui.repositories.RepositoryDetailScreen
import com.ollitert.llm.server.ui.repositories.RepositoryListScreen
import com.ollitert.llm.server.ui.repositories.RepositoryViewModel
import com.ollitert.llm.server.ui.server.SettingsScreen
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
  startDestination: String = OlliteRTRoutes.MODELS,
  modifier: Modifier = Modifier,
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

  LaunchedEffect(engagementServerStatus) {
    if (engagementServerStatus == ServerStatus.RUNNING && manualStartPending) {
      manualStartPending = false
      if (ServerPrefs.shouldShowEngagementPrompt(context)) {
        ServerPrefs.incrementEngagementPromptShowCount(context)
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
        ServerPrefs.setEngagementPromptPermanentlyDismissed(context)
        uriHandler.openUri(GitHubConfig.REPO_URL)
      },
      onDismiss = { permanentlyDismiss ->
        showEngagementPrompt = false
        if (permanentlyDismiss) {
          ServerPrefs.setEngagementPromptPermanentlyDismissed(context)
        }
      },
    )
  }

  // Donate dialog — opened from the engagement prompt's "Support Development" button.
  // When closed, the engagement prompt reappears so the user can still tap "Star on GitHub".
  if (showDonateFromEngagement) {
    DonateDialog(onDismiss = { showDonateFromEngagement = false })
  }

  NavHost(
    navController = navController,
    startDestination = startDestination,
    modifier = modifier,
    enterTransition = { fadeIn(tween(200)) },
    exitTransition = { fadeOut(tween(200)) },
  ) {
    // Models tab (main screen, reusing GlobalModelManager)
    composable(OlliteRTRoutes.MODELS) { backStackEntry ->
      val reposChanged = backStackEntry.savedStateHandle
        .getStateFlow("reposChanged", false)
        .collectAsStateWithLifecycle()
      LaunchedEffect(reposChanged.value) {
        if (reposChanged.value) {
          modelManagerViewModel.loadModelAllowlist()
          backStackEntry.savedStateHandle["reposChanged"] = false
        }
      }
      val modelsLifecycleOwner = LocalLifecycleOwner.current
      LaunchedEffect(modelsLifecycleOwner) {
        modelsLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
          modelManagerViewModel.loadModelAllowlist()
        }
      }
      val modelsContext = LocalContext.current
      val serverStatus by serverViewModel.status.collectAsStateWithLifecycle()
      val activeModelName by serverViewModel.activeModelName.collectAsStateWithLifecycle()
      val lastError by serverViewModel.lastError.collectAsStateWithLifecycle()
      GlobalModelManager(
        viewModel = modelManagerViewModel,
        navigateUp = { navController.navigateUp() },
        onModelSelected = { model ->
          // Track manual server starts for the engagement prompt (counter lives in prefs,
          // observer lives at NavHost level so it fires regardless of which screen is active)
          ServerPrefs.incrementManualStartCount(modelsContext)
          manualStartPending = true
          serverViewModel.startServer(modelName = model.name)
        },
        onBenchmarkClicked = { model ->
          navController.navigate(OlliteRTRoutes.benchmark(model.name)) { launchSingleTop = true }
        },
        serverStatus = serverStatus,
        activeModelName = activeModelName,
        lastError = lastError,
        onStopServer = { serverViewModel.stopServer() },
        onSwitchModel = { modelName ->
          // Track model switches the same way as fresh starts for the engagement prompt
          ServerPrefs.incrementManualStartCount(modelsContext)
          manualStartPending = true
          serverViewModel.switchModel(modelName)
        },
        onNavigateToSettings = { navController.navigate(OlliteRTRoutes.SETTINGS) { launchSingleTop = true } },
        onNavigateToRepositories = { navController.navigate(OlliteRTRoutes.REPOSITORIES) { launchSingleTop = true } },
      )
    }

    // Status tab
    composable(OlliteRTRoutes.STATUS) {
      StatusScreen(
        serverViewModel = serverViewModel,
        onReloadModel = {
          // Reload the model atomically within the service (clean up old model, then re-init)
          serverViewModel.reloadServer()
        },
      )
    }

    // Logs tab
    composable(OlliteRTRoutes.LOGS) {
      LogsScreen()
    }

    // Settings screen
    composable(
      OlliteRTRoutes.SETTINGS,
      enterTransition = { slideInLeft() },
      exitTransition = { slideOutRight() },
    ) { settingsBackStackEntry ->
      val reposChanged = settingsBackStackEntry.savedStateHandle
        .getStateFlow("reposChanged", false)
        .collectAsStateWithLifecycle()
      val settingsViewModel: com.ollitert.llm.server.ui.server.SettingsViewModel = hiltViewModel()
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
      SettingsScreen(
        onBackClick = { navController.navigateUp() },
        serverStatus = settingsServerStatus,
        onRestartServer = { serverViewModel.reloadServer() },
        onStopServer = { serverViewModel.stopServer() },
        onNavigateToModels = {
          navController.navigate(OlliteRTRoutes.MODELS) {
            launchSingleTop = true
            popUpTo(OlliteRTRoutes.SETTINGS) { inclusive = true }
          }
        },
        downloadedModelNames = downloadedModelNames,
        onSetTopBarTrailingContent = onSetTopBarTrailingContent,
        onSettingsSaved = { modelManagerViewModel.refreshShowModelRecommendations() },
        onNavigateToRepositories = { navController.navigate(OlliteRTRoutes.REPOSITORIES) { launchSingleTop = true } },
      )
    }

    // Repository list
    composable(
      OlliteRTRoutes.REPOSITORIES,
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
          navController.navigate(OlliteRTRoutes.repositoryDetail(repoId)) { launchSingleTop = true }
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
    composable(
      route = OlliteRTRoutes.REPOSITORY_DETAIL,
      arguments = listOf(navArgument("repoId") { type = NavType.StringType }),
      enterTransition = { slideInLeft() },
      exitTransition = { slideOutRight() },
    ) { detailBackStackEntry ->
      val repoId = detailBackStackEntry.arguments?.getString("repoId") ?: return@composable
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
    composable(OlliteRTRoutes.GETTING_STARTED) {
      GettingStartedScreen(
        onGetStartedClick = {
          modelManagerViewModel.completeOnboarding()
          navController.navigate(OlliteRTRoutes.MODELS) {
            popUpTo(OlliteRTRoutes.GETTING_STARTED) { inclusive = true }
          }
        },
      )
    }

    // Benchmark screen (existing)
    composable(
      route = OlliteRTRoutes.BENCHMARK,
      arguments = listOf(navArgument("modelName") { type = NavType.StringType }),
      enterTransition = { slideInLeft() },
      exitTransition = { slideOutRight() },
    ) { backStackEntry ->
      val modelName = backStackEntry.arguments?.getString("modelName") ?: ""
      modelManagerViewModel.getModelByName(name = modelName)?.let { model ->
        BenchmarkScreen(
          initialModel = model,
          modelManagerViewModel = modelManagerViewModel,
          onBackClicked = { navController.navigateUp() },
        )
      }
    }
  }

  // Handle incoming deep links
  val intent = androidx.activity.compose.LocalActivity.current?.intent
  val data = intent?.data
  if (data != null) {
    intent.data = null
    Log.d(TAG, "deep link: $data")
    if (data.toString() == "com.ollitert.llm.server://global_model_manager") {
      navController.navigate(OlliteRTRoutes.MODELS) { launchSingleTop = true }
    }
  }
}
