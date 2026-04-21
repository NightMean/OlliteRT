/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.ui.server.settings

import android.content.Context
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.ui.server.SettingsViewModel

@Composable
internal fun SettingsDialogs(
  vm: SettingsViewModel,
  context: Context,
  serverStatus: ServerStatus,
  onRestartServer: () -> Unit,
  onStopServer: () -> Unit,
  onNavigateToModels: () -> Unit,
  onBackClick: () -> Unit,
  performSave: () -> Unit,
  forceSave: () -> Unit,
) {
  val logsClearedText = stringResource(R.string.toast_logs_cleared)
  val settingsSavedRestartManualText = stringResource(R.string.toast_settings_saved_restart_manual)
  val serverRestartingText = stringResource(R.string.toast_server_restarting)
  val settingsResetText = stringResource(R.string.toast_settings_reset)

  // Clear persisted logs confirmation dialog
  if (vm.showClearPersistedDialog) {
    AlertDialog(
      onDismissRequest = { vm.showClearPersistedDialog = false },
      title = { Text(stringResource(R.string.dialog_clear_logs_title)) },
      text = { Text(stringResource(R.string.dialog_clear_logs_body)) },
      confirmButton = {
        Button(
          onClick = {
            vm.showClearPersistedDialog = false
            RequestLogStore.clear()
            val persistenceEntryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
              context.applicationContext,
              com.ollitert.llm.server.OlliteRTApplication.PersistenceEntryPoint::class.java,
            )
            persistenceEntryPoint.requestLogPersistence().clearPersistedLogs()
            Toast.makeText(context, logsClearedText, Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text(stringResource(R.string.button_clear))
        }
      },
      dismissButton = {
        Button(
          onClick = { vm.showClearPersistedDialog = false },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
        ) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  // Trim logs confirmation
  if (vm.showTrimLogsDialog) {
    val currentCount = RequestLogStore.entries.collectAsStateWithLifecycle().value.size
    val toRemove = currentCount - vm.logMaxEntriesEntry.current
    AlertDialog(
      onDismissRequest = { vm.showTrimLogsDialog = false },
      title = { Text(stringResource(R.string.dialog_reduce_log_limit_title)) },
      text = {
        Text(stringResource(R.string.dialog_reduce_log_limit_body, currentCount, vm.logMaxEntriesEntry.current, toRemove))
      },
      confirmButton = {
        Button(
          onClick = {
            vm.showTrimLogsDialog = false
            forceSave()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text(stringResource(R.string.continue_button_label))
        }
      },
      dismissButton = {
        Button(
          onClick = { vm.showTrimLogsDialog = false },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
        ) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  // Discard unsaved changes dialog
  if (vm.showDiscardDialog) {
    AlertDialog(
      onDismissRequest = { vm.showDiscardDialog = false },
      title = { Text(stringResource(R.string.dialog_unsaved_changes_title)) },
      text = { Text(stringResource(R.string.dialog_unsaved_changes_body)) },
      confirmButton = {
        Button(onClick = {
          vm.showDiscardDialog = false
          performSave()
          onBackClick()
        }) {
          Text(stringResource(R.string.button_save))
        }
      },
      dismissButton = {
        Button(onClick = {
          vm.showDiscardDialog = false
          onBackClick()
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
        )) {
          Text(stringResource(R.string.button_discard))
        }
      },
    )
  }

  // Restart server dialog
  if (vm.showRestartDialog) {
    AlertDialog(
      onDismissRequest = {
        vm.showRestartDialog = false
        Toast.makeText(context, settingsSavedRestartManualText, Toast.LENGTH_LONG).show()
      },
      title = { Text(stringResource(R.string.dialog_restart_server_title)) },
      text = {
        Text(stringResource(R.string.dialog_restart_server_body))
      },
      confirmButton = {
        Button(onClick = {
          vm.showRestartDialog = false
          onRestartServer()
          Toast.makeText(context, serverRestartingText, Toast.LENGTH_SHORT).show()
        }) {
          Text(stringResource(R.string.button_restart))
        }
      },
      dismissButton = {
        Button(
          onClick = {
            vm.showRestartDialog = false
            Toast.makeText(context, settingsSavedRestartManualText, Toast.LENGTH_LONG).show()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
        ) {
          Text(stringResource(R.string.button_later))
        }
      },
    )
  }

  // Reset to Defaults confirmation dialog
  if (vm.showResetDialog) {
    val isServerActive = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING
    AlertDialog(
      onDismissRequest = { vm.showResetDialog = false },
      title = { Text(stringResource(R.string.dialog_reset_defaults_title)) },
      text = {
        Text(
          stringResource(
            if (isServerActive) R.string.dialog_reset_defaults_body_server_active
            else R.string.dialog_reset_defaults_body,
          ),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            vm.showResetDialog = false
            if (isServerActive) {
              onStopServer()
            }
            vm.resetToDefaults()
            val window = (context as? android.app.Activity)?.window
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Toast.makeText(context, settingsResetText, Toast.LENGTH_SHORT).show()
            onNavigateToModels()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
          ),
        ) {
          Text(stringResource(R.string.button_reset))
        }
      },
      dismissButton = {
        Button(
          onClick = { vm.showResetDialog = false },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
          ),
        ) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  // Donate dialog
  if (vm.showDonateDialog) {
    com.ollitert.llm.server.ui.common.DonateDialog(
      onDismiss = { vm.showDonateDialog = false },
    )
  }
}
