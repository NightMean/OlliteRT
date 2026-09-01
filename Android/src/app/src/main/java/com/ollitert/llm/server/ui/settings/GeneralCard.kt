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

package com.ollitert.llm.server.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ollitert.llm.server.OlliteRTApplication
import com.ollitert.llm.server.R
import com.ollitert.llm.server.floatingmonitor.FloatingMonitorPermissionCoordinator
import com.ollitert.llm.server.ui.settings.SettingsViewModel

@Composable
internal fun GeneralCard(vm: SettingsViewModel) {
  val context = LocalContext.current
  val resetFloatingMonitorPositionMessage =
    stringResource(R.string.toast_floating_monitor_position_reset)
  var isAwaitingOverlayPermission by remember { mutableStateOf(false) }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    if (isAwaitingOverlayPermission) {
      isAwaitingOverlayPermission = false
      vm.reconcileFloatingMonitorPermission(
        hasOverlayPermission = FloatingMonitorPermissionCoordinator.hasOverlayPermission(context),
        requestedEnabled = true,
      )
    }
  }

  SettingsCard(
    icon = Icons.Outlined.PhoneAndroid,
    title = stringResource(R.string.settings_card_general),
    searchQuery = vm.searchQuery,
  ) {
    ToggleCardContent(
      cardId = CardId.GENERAL,
      vm = vm,
      onToggleRequested = { key, enabled ->
        if (key == "floating_monitor" && enabled &&
          !FloatingMonitorPermissionCoordinator.hasOverlayPermission(context)
        ) {
          isAwaitingOverlayPermission = true
          FloatingMonitorPermissionCoordinator.requestOverlayPermission(context)
          false
        } else {
          true
        }
      },
      afterToggleContent = { key, enabled ->
        if (key == "floating_monitor" && enabled) {
          SettingDivider(verticalPadding = 8)
          Column {
            Button(
              onClick = {
                (context.applicationContext as? OlliteRTApplication)?.resetFloatingMonitorPosition()
                Toast.makeText(
                  context,
                  resetFloatingMonitorPositionMessage,
                  Toast.LENGTH_SHORT,
                ).show()
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
              ),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = stringResource(R.string.settings_reset_floating_monitor_position),
                fontWeight = FontWeight.Bold,
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = stringResource(R.string.settings_reset_floating_monitor_position_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      },
    )
  }
}
