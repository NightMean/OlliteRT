package com.ollite.llm.server

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ollite.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollite.llm.server.ui.navigation.OlliteBottomNavBar
import com.ollite.llm.server.ui.navigation.OlliteNavHost
import com.ollite.llm.server.ui.navigation.OlliteRoutes
import com.ollite.llm.server.ui.navigation.OlliteTab
import com.ollite.llm.server.ui.navigation.OlliteTopBar
import com.ollite.llm.server.ui.navigation.ServerStatus

/** Root composable for the Ollite app. */
@Composable
fun OlliteApp(
  modelManagerViewModel: ModelManagerViewModel,
  navController: NavHostController = rememberNavController(),
) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  // Determine start destination based on onboarding state
  val startDestination = if (modelManagerViewModel.dataStoreRepository.isOnboardingCompleted()) {
    OlliteRoutes.MODELS
  } else {
    OlliteRoutes.GETTING_STARTED
  }

  // Determine which screens show the bottom nav and top bar
  val showBottomBar = currentRoute in listOf(
    OlliteRoutes.MODELS,
    OlliteRoutes.STATUS,
    OlliteRoutes.LOGS,
  )
  val showTopBar = currentRoute != null && currentRoute !in listOf(
    OlliteRoutes.GETTING_STARTED,
    OlliteRoutes.BENCHMARK,
  )

  // TODO: Wire to actual server state
  val serverStatus = ServerStatus.STOPPED

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      if (showTopBar) {
        OlliteTopBar(
          serverStatus = serverStatus,
          onSettingsClick = {
            navController.navigate(OlliteRoutes.SETTINGS) {
              launchSingleTop = true
            }
          },
        )
      }
    },
    bottomBar = {
      if (showBottomBar) {
        OlliteBottomNavBar(
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
    OlliteNavHost(
      navController = navController,
      modelManagerViewModel = modelManagerViewModel,
      startDestination = startDestination,
      modifier = Modifier.padding(innerPadding),
    )
  }
}
