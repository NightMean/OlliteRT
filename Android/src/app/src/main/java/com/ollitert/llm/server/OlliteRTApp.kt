package com.ollitert.llm.server

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

  // Auto-load default model on app launch (if configured and server isn't already running).
  // Reads status snapshot once — no ongoing collection that would recompose the root.
  val context = LocalContext.current
  LaunchedEffect(Unit) {
    if (startDestination == OlliteRTRoutes.MODELS) {
      val defaultModel = LlmHttpPrefs.getDefaultModelName(context)
      if (!defaultModel.isNullOrBlank() && serverViewModel.status.value == ServerStatus.STOPPED) {
        serverViewModel.startServer(modelName = defaultModel)
      }
    }
  }

  // ── Server error dialog ──────────────────────────────────────────────────
  // Collected here because the dialog must overlay all screens. These flows only
  // emit on status transitions (rare), not per-token, so recomposition cost is minimal.
  val serverStatus by serverViewModel.status.collectAsStateWithLifecycle()
  val lastError by serverViewModel.lastError.collectAsStateWithLifecycle()
  var showErrorDialog by remember { mutableStateOf(false) }
  var errorDialogMessage by remember { mutableStateOf("") }

  LaunchedEffect(serverStatus, lastError) {
    if (serverStatus == ServerStatus.ERROR && !lastError.isNullOrBlank()) {
      errorDialogMessage = lastError ?: ""
      showErrorDialog = true
    }
  }

  if (showErrorDialog) {
    AlertDialog(
      onDismissRequest = { showErrorDialog = false },
      shape = RoundedCornerShape(32.dp),
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      title = {
        Text(
          text = "Server Error",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.error,
        )
      },
      text = {
        Text(
          text = errorDialogMessage,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
      },
      confirmButton = {
        Button(
          onClick = { showErrorDialog = false },
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
          ),
        ) {
          Text("OK")
        }
      },
    )
  }

  // Top bar trailing content (e.g. save button on Settings screen)
  var topBarTrailingContent: (@Composable () -> Unit)? by remember { mutableStateOf(null) }

  // Determine which screens show the bottom nav and top bar
  val showNav = currentRoute in listOf(
    OlliteRTRoutes.MODELS,
    OlliteRTRoutes.STATUS,
    OlliteRTRoutes.LOGS,
  )
  val showTopBar = currentRoute != null && currentRoute !in listOf(
    OlliteRTRoutes.GETTING_STARTED,
    OlliteRTRoutes.BENCHMARK,
  )

  // Shared tab navigation lambda
  val onTabSelected: (OlliteRTTab) -> Unit = { tab ->
    navController.navigate(tab.route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

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
      if (showNav) {
        // Only collect the storage trigger — not the full uiState — to avoid
        // recomposing the entire bottom bar on every model download progress update.
        val storageTrigger by remember {
          modelManagerViewModel.uiState.map { it.storageUpdateTrigger }
        }.collectAsStateWithLifecycle(initialValue = 0L)
        OlliteRTBottomNavBar(
          currentRoute = currentRoute,
          onTabSelected = onTabSelected,
          storageUpdateTrigger = storageTrigger,
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
