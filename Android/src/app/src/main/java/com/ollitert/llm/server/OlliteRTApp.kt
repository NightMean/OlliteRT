package com.ollitert.llm.server

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.ui.navigation.OlliteRTBottomNavBar
import com.ollitert.llm.server.ui.navigation.OlliteRTNavHost
import com.ollitert.llm.server.ui.navigation.OlliteRTRoutes
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

  val serverStatus by serverViewModel.status.collectAsState()

  Scaffold(
    modifier = Modifier.fillMaxSize(),
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
            { navController.navigateUp() }
          } else {
            null
          },
        )
      }
    },
    bottomBar = {
      if (showBottomBar) {
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
    )
  }
}
