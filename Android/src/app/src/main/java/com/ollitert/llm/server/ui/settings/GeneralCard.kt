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

import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.ollitert.llm.server.R
import com.ollitert.llm.server.floatingmonitor.FloatingMonitorPermissionCoordinator
import com.ollitert.llm.server.ui.settings.SettingsViewModel

@Composable
internal fun GeneralCard(vm: SettingsViewModel) {
  val context = LocalContext.current
  SettingsCard(
    icon = Icons.Outlined.PhoneAndroid,
    title = stringResource(R.string.settings_card_general),
    searchQuery = vm.searchQuery,
  ) {
    ToggleCardContent(
      cardId = CardId.GENERAL,
      vm = vm,
      onToggleChanged = { key, enabled ->
        if (key == "floating_monitor" && enabled && !Settings.canDrawOverlays(context)) {
          FloatingMonitorPermissionCoordinator.requestOverlayPermission(context)
        }
      },
    )
  }
}
