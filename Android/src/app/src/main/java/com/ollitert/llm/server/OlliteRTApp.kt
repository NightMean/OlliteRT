package com.ollitert.llm.server

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.ollitert.llm.server.data.LlmHttpPrefs
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.ui.navigation.OlliteRTBottomNavBar
import com.ollitert.llm.server.ui.navigation.OlliteRTNavHost
import com.ollitert.llm.server.ui.navigation.OlliteRTRoutes
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.ui.navigation.OlliteRTTab
import com.ollitert.llm.server.ui.navigation.OlliteRTTopBar
import com.ollitert.llm.server.ui.server.ServerViewModel

/** Root composable for the OlliteRT app. */
@Composable
fun OlliteRTApp(
  modelManagerViewModel: ModelManagerViewModel,
  serverViewModel: ServerViewModel,
  navController: NavHostController = rememberNavController(),
) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  // Determine start destination based on onboarding state
  val startDestination = if (modelManagerViewModel.dataStoreRepository.isOnboardingCompleted()) {
    OlliteRTRoutes.MODELS
  } else {
    OlliteRTRoutes.GETTING_STARTED
  }

  // Auto-load default model on app launch (if configured and server isn't already running)
  val context = LocalContext.current
  val serverStatus by serverViewModel.status.collectAsState()
  LaunchedEffect(Unit) {
    if (startDestination == OlliteRTRoutes.MODELS) {
      val defaultModel = LlmHttpPrefs.getDefaultModelName(context)
      if (!defaultModel.isNullOrBlank() && serverStatus == ServerStatus.STOPPED) {
        serverViewModel.startServer(modelName = defaultModel)
      }
    }
  }

  // Top bar trailing content (e.g. save button on Settings screen)
  var topBarTrailingContent: (@Composable () -> Unit)? by remember { mutableStateOf(null) }

  // Determine which screens show the bottom nav and top bar
  val showBottomBar = currentRoute in listOf(
    OlliteRTRoutes.MODELS,
    OlliteRTRoutes.STATUS,
    OlliteRTRoutes.LOGS,
  )
  val showTopBar = currentRoute != null && currentRoute !in listOf(
    OlliteRTRoutes.GETTING_STARTED,
    OlliteRTRoutes.BENCHMARK,
  )

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.surface,
    topBar = {
      if (showTopBar) {
        val isSettings = currentRoute == OlliteRTRoutes.SETTINGS
        OlliteRTTopBar(
          serverStatus = serverStatus,
          onSettingsClick = {
            navController.navigate(OlliteRTRoutes.SETTINGS) {
              launchSingleTop = true
            }
          },
          onBackClick = if (isSettings) {
            {
              // Dispatch back press so BackHandler in child screens can intercept
              // (e.g. SettingsScreen unsaved changes guard)
              val dispatcher = navController.context as? androidx.activity.OnBackPressedDispatcherOwner
              dispatcher?.onBackPressedDispatcher?.onBackPressed()
                ?: navController.navigateUp()
            }
          } else {
            null
          },
          trailingContent = if (isSettings) topBarTrailingContent else null,
        )
      }
    },
    bottomBar = {
      if (showBottomBar) {
        val mmUiState by modelManagerViewModel.uiState.collectAsState()
        OlliteRTBottomNavBar(
          currentRoute = currentRoute,
          onTabSelected = { tab ->
            navController.navigate(tab.route) {
              popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
              }
              launchSingleTop = true
              restoreState = true
            }
          },
          storageUpdateTrigger = mmUiState.storageUpdateTrigger,
        )
      }
    },
  ) { innerPadding ->
    OlliteRTNavHost(
      navController = navController,
      modelManagerViewModel = modelManagerViewModel,
      serverViewModel = serverViewModel,
      startDestination = startDestination,
      modifier = Modifier.padding(innerPadding),
      onSetTopBarTrailingContent = { topBarTrailingContent = it },
    )
  }
}
