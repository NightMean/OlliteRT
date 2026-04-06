package com.ollite.llm.server.ui.navigation

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
import com.ollite.llm.server.ui.benchmark.BenchmarkScreen
import com.ollite.llm.server.ui.modelmanager.GlobalModelManager
import com.ollite.llm.server.ui.modelmanager.ModelManagerViewModel

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
fun OlliteNavHost(
  navController: NavHostController,
  modelManagerViewModel: ModelManagerViewModel,
  modifier: Modifier = Modifier,
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
    startDestination = OlliteRoutes.MODELS,
    modifier = modifier,
    enterTransition = { fadeIn(tween(200)) },
    exitTransition = { fadeOut(tween(200)) },
  ) {
    // Models tab (main screen, reusing GlobalModelManager)
    composable(OlliteRoutes.MODELS) {
      GlobalModelManager(
        viewModel = modelManagerViewModel,
        navigateUp = { navController.navigateUp() },
        onModelSelected = { _, _ -> },
        onBenchmarkClicked = { model ->
          navController.navigate(OlliteRoutes.benchmark(model.name))
        },
      )
    }

    // Status tab (placeholder)
    composable(OlliteRoutes.STATUS) {
      PlaceholderScreen("Status", "No active server.\nStart a model from the Models tab.")
    }

    // Logs tab (placeholder)
    composable(OlliteRoutes.LOGS) {
      PlaceholderScreen("Logs", "No requests yet.\nAPI traffic will appear here.")
    }

    // Settings screen (placeholder)
    composable(
      OlliteRoutes.SETTINGS,
      enterTransition = { slideInLeft() },
      exitTransition = { slideOutRight() },
    ) {
      PlaceholderScreen("Settings", "Global settings coming soon.")
    }

    // Getting Started (placeholder)
    composable(OlliteRoutes.GETTING_STARTED) {
      PlaceholderScreen("Getting Started", "Welcome to Ollite!")
    }

    // Benchmark screen (existing)
    composable(
      route = OlliteRoutes.BENCHMARK,
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
    Log.d("OlliteNavGraph", "deep link: $data")
    if (data.toString() == "com.ollite.llm.server://global_model_manager") {
      navController.navigate(OlliteRoutes.MODELS)
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
