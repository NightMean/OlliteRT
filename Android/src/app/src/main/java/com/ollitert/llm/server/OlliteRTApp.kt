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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.service.LlmHttpService
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.ui.navigation.OlliteRTBottomNavBar
import com.ollitert.llm.server.ui.navigation.OlliteRTNavHost
import com.ollitert.llm.server.ui.navigation.OlliteRTRoutes
import com.ollitert.llm.server.ui.navigation.OlliteRTTab
import com.ollitert.llm.server.ui.navigation.OlliteRTTopBar
import com.ollitert.llm.server.ui.server.ServerViewModel
import kotlinx.coroutines.flow.map

/** Root composable for the OlliteRT app. */
@Composable
fun OlliteRTApp(
  modelManagerViewModel: ModelManagerViewModel,
  serverViewModel: ServerViewModel,
  navController: NavHostController = rememberNavController(),
) {
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route

  val startDestination = if (modelManagerViewModel.onboardingCompleted) {
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
        serverViewModel.startServer(modelName = defaultModel, source = LlmHttpService.SOURCE_LAUNCH)
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
          text = stringResource(R.string.dialog_server_error_title),
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
          Text(stringResource(R.string.ok))
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
