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
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.service.ServerMetrics
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.highlightSearchMatches
import com.ollitert.llm.server.ui.server.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.worker.UpdateCheckWorker

@Composable
internal fun UpdatesCard(vm: SettingsViewModel, context: Context) {
  val updateCheckTimeoutText = stringResource(R.string.settings_update_check_timeout)
  val checkingForUpdatesText = stringResource(R.string.settings_checking_for_updates)

  SettingsCard(
    icon = Icons.Outlined.SystemUpdate,
    title = stringResource(R.string.settings_card_updates),
    searchQuery = vm.searchQuery,
  ) {
    if (vm.settingVisible(AUTO_UPDATE_CHECK.key)) {
      val notifPermissionGranted = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
      val updateChannelMuted = UpdateCheckWorker.isUpdateChannelMuted(context)
      val updateControlsEnabled = notifPermissionGranted && !updateChannelMuted

      ToggleSettingRow(
        label = stringResource(R.string.settings_auto_update_check),
        description = stringResource(R.string.settings_auto_update_check_desc),
        checked = vm.updateCheckEnabledEntry.current,
        onCheckedChange = { vm.updateCheckEnabledEntry.update(it) },
        searchQuery = vm.searchQuery,
        enabled = updateControlsEnabled,
      )

      if (!notifPermissionGranted) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.settings_notif_permission_warning),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      } else if (updateChannelMuted) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.settings_notif_channel_muted),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.clickable {
            val intent = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
              putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
              putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, UpdateCheckWorker.UPDATE_CHANNEL_ID)
            }
            context.startActivity(intent)
          },
        )
      }

      SettingDivider(verticalPadding = 8)

      NumericWithUnitRow(
        def = CHECK_FREQUENCY,
        baseValue = vm.updateCheckIntervalHoursEntry.current,
        savedBaseValue = vm.updateCheckIntervalHoursEntry.saved,
        onBaseValueChange = { vm.updateCheckIntervalHoursEntry.update(it) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError(CHECK_FREQUENCY.key),
        enabled = vm.updateCheckEnabledEntry.current && updateControlsEnabled,
        modifier = Modifier.alpha(if (vm.updateCheckEnabledEntry.current && updateControlsEnabled) 1f else 0.4f),
        onErrorClear = { vm.clearError(CHECK_FREQUENCY.key) },
      )
    }

    if (vm.settingVisible(AUTO_UPDATE_CHECK.key) && vm.settingVisible(CHECK_FOR_UPDATES.key)) {
      SettingDivider()
    }

    if (vm.settingVisible(CHECK_FOR_UPDATES.key)) {
      val availableVersion by ServerMetrics.availableUpdateVersion.collectAsStateWithLifecycle()
      val availableUrl by ServerMetrics.availableUpdateUrl.collectAsStateWithLifecycle()
      val hasUpdate = availableVersion != null

      var checkWorkId by remember { mutableStateOf<java.util.UUID?>(null) }
      val workManager = remember { WorkManager.getInstance(context) }

      checkWorkId?.let { id ->
        val workInfo by workManager.getWorkInfoByIdFlow(id).collectAsStateWithLifecycle(initialValue = null)
        LaunchedEffect(workInfo?.state) {
          val info = workInfo ?: return@LaunchedEffect
          if (info.state.isFinished) {
            val message = info.outputData.getString(UpdateCheckWorker.KEY_MESSAGE)
            if (message != null) {
              Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            checkWorkId = null
          }
        }
        LaunchedEffect(id) {
          kotlinx.coroutines.delay(15_000)
          if (checkWorkId == id) {
            Toast.makeText(context, updateCheckTimeoutText, Toast.LENGTH_SHORT).show()
            workManager.cancelWorkById(id)
            checkWorkId = null
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
          SettingLabel(text = stringResource(R.string.settings_check_for_updates), searchQuery = vm.searchQuery)
          Text(
            text = stringResource(R.string.settings_check_for_updates_desc, BuildConfig.UPDATE_CHANNEL),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (hasUpdate && !availableUrl.isNullOrBlank()) {
          val uriHandler = LocalUriHandler.current
          val url = availableUrl ?: ""
          TooltipIconButton(
            icon = Icons.Outlined.FileDownload,
            tooltip = stringResource(R.string.settings_download_version, availableVersion ?: ""),
            onClick = {
              val intent = UpdateCheckWorker.buildUpdateIntent(context, url)
              intent.data?.let { uri -> uriHandler.openUri(uri.toString()) }
            },
          )
        } else {
          TooltipIconButton(
            icon = Icons.Outlined.Refresh,
            tooltip = stringResource(R.string.settings_check_now_tooltip),
            onClick = {
              Toast.makeText(context, checkingForUpdatesText, Toast.LENGTH_SHORT).show()
              checkWorkId = UpdateCheckWorker.checkNow(context)
            },
          )
        }
      }
    }

    if (vm.settingVisible(CROSS_CHANNEL_NOTIFY.key)) {
      if (vm.settingVisible(CHECK_FOR_UPDATES.key)) {
        SettingDivider()
      }

      val notifPermissionGranted = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
      val crossChannelMuted = UpdateCheckWorker.areCrossChannelChannelsMuted(context)
      val crossChannelEnabled = notifPermissionGranted && !crossChannelMuted
      val isDevBuild = BuildConfig.UPDATE_CHANNEL == "dev"

      ToggleSettingRow(
        label = stringResource(R.string.settings_cross_channel_notify),
        description = if (isDevBuild) {
          stringResource(R.string.settings_cross_channel_notify_desc_dev)
        } else {
          stringResource(R.string.settings_cross_channel_notify_desc)
        },
        checked = vm.crossChannelNotifyEntry.current,
        onCheckedChange = { vm.crossChannelNotifyEntry.update(it) },
        searchQuery = vm.searchQuery,
        enabled = crossChannelEnabled && !isDevBuild,
      )

      if (!notifPermissionGranted) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.settings_notif_permission_warning),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      } else if (crossChannelMuted) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.settings_cross_channel_muted),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.clickable {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
              putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
          },
        )
      }
    }

    if (vm.settingVisible(NOTIFICATION_SETTINGS.key)) {
      if (vm.settingVisible(CROSS_CHANNEL_NOTIFY.key) || vm.settingVisible(CHECK_FOR_UPDATES.key)) {
        SettingDivider()
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
              putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            context.startActivity(intent)
          }
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = highlightSearchMatches(stringResource(R.string.settings_notification_settings), vm.searchQuery, OlliteRTPrimary),
            style = MaterialTheme.typography.bodyMedium,
            color = OlliteRTPrimary,
          )
          Text(
            text = stringResource(R.string.settings_notification_settings_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = stringResource(R.string.settings_notification_settings),
          tint = OlliteRTPrimary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
