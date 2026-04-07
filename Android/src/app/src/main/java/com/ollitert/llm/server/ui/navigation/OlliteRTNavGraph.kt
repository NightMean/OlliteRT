package com.ollitert.llm.server.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutExpo
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ollitert.llm.server.ui.benchmark.BenchmarkScreen
import com.ollitert.llm.server.ui.gettingstarted.GettingStartedScreen
import com.ollitert.llm.server.ui.modelmanager.GlobalModelManager
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.ui.server.LogsScreen
import com.ollitert.llm.server.ui.server.ServerViewModel
import com.ollitert.llm.server.ui.server.SettingsScreen
import com.ollitert.llm.server.ui.server.StatusScreen

private const val TRANSITION_DURATION_MS = 350

private fun enterTween(): FiniteAnimationSpec<IntOffset> =
  tween(TRANSITION_DURATION_MS, easing = EaseOutExpo)

private fun exitTween(): FiniteAnimationSpec<IntOffset> =
  tween(TRANSITION_DURATION_MS, easing = EaseOutExpo)

private fun AnimatedContentTransitionScope<*>.slideInLeft(): EnterTransition =
  slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, enterTween())

private fun AnimatedContentTransitionScope<*>.slideOutRight(): ExitTransition =
  slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, exitTween())

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

  NavHost(
    navController = navController,
    startDestination = startDestination,
    modifier = modifier,
    enterTransition = { fadeIn(tween(200)) },
    exitTransition = { fadeOut(tween(200)) },
  ) {
    // Models tab (main screen, reusing GlobalModelManager)
    composable(OlliteRTRoutes.MODELS) {
      val serverStatus by serverViewModel.status.collectAsState()
      val activeModelName by serverViewModel.activeModelName.collectAsState()
      val lastError by serverViewModel.lastError.collectAsState()
      GlobalModelManager(
        viewModel = modelManagerViewModel,
        navigateUp = { navController.navigateUp() },
        onModelSelected = { _, model ->
          // Start the server with the selected model
          serverViewModel.startServer(modelName = model.name)
        },
        onBenchmarkClicked = { model ->
          navController.navigate(OlliteRTRoutes.benchmark(model.name))
        },
        serverStatus = serverStatus,
        activeModelName = activeModelName,
        lastError = lastError,
        onStopServer = { serverViewModel.stopServer() },
        onNavigateToSettings = { navController.navigate(OlliteRTRoutes.SETTINGS) },
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
    ) {
      val settingsServerStatus by serverViewModel.status.collectAsState()
      val settingsActiveModel by serverViewModel.activeModelName.collectAsState()
      val downloadedModelNames = modelManagerViewModel.getAllDownloadedModels().map { it.name }
      SettingsScreen(
        onBackClick = { navController.navigateUp() },
        serverStatus = settingsServerStatus,
        onRestartServer = {
          val currentModel = settingsActiveModel
          serverViewModel.stopServer()
          serverViewModel.startServer(modelName = currentModel)
        },
        downloadedModelNames = downloadedModelNames,
        onSetTopBarTrailingContent = onSetTopBarTrailingContent,
      )
    }

    // Getting Started (onboarding)
    composable(OlliteRTRoutes.GETTING_STARTED) {
      GettingStartedScreen(
        onGetStartedClick = {
          modelManagerViewModel.dataStoreRepository.setOnboardingCompleted()
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
    Log.d("OlliteRTNavGraph", "deep link: $data")
    if (data.toString() == "com.ollitert.llm.server://global_model_manager") {
      navController.navigate(OlliteRTRoutes.MODELS)
    }
  }
}

@Composable
private fun PlaceholderScreen(title: String, message: String) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = message,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
