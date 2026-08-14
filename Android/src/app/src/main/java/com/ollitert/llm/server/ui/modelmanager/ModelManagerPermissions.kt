/*
 * Copyright 2026 Google LLC
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

package com.ollitert.llm.server.ui.modelmanager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.theme.OlliteRTWarningContainer
import com.ollitert.llm.server.ui.theme.OlliteRTWarningText

/**
 * State representing missing permissions required for background foreground server execution.
 */
data class ModelManagerPermissionState(
  val missingNotifPermission: Boolean,
  val missingBatteryExemption: Boolean,
) {
  val hasMissingPermissions: Boolean get() = missingNotifPermission || missingBatteryExemption
}

/**
 * Remembers and re-evaluates permissions on every ON_RESUME lifecycle event.
 */
@Composable
fun rememberModelManagerPermissionState(context: Context): ModelManagerPermissionState {
  val lifecycleOwner = LocalLifecycleOwner.current
  var resumeCount by remember { mutableIntStateOf(0) }
  androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        resumeCount++
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val missingNotifPermission by remember(resumeCount) {
    mutableStateOf(
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
      } else false
    )
  }
  val missingBatteryExemption by remember(resumeCount) {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    mutableStateOf(pm?.let { !it.isIgnoringBatteryOptimizations(context.packageName) } ?: true)
  }

  return ModelManagerPermissionState(
    missingNotifPermission = missingNotifPermission,
    missingBatteryExemption = missingBatteryExemption,
  )
}

/**
 * Warning banner shown when notification or battery optimization permissions are missing.
 */
@Composable
fun PermissionWarningBanner(
  permissionState: ModelManagerPermissionState,
  context: Context,
  modifier: Modifier = Modifier,
) {
  val notifLabel = stringResource(R.string.models_permission_notification)
  val batteryLabel = stringResource(R.string.models_permission_battery)
  val issues = buildList {
    if (permissionState.missingNotifPermission) add(notifLabel)
    if (permissionState.missingBatteryExemption) add(batteryLabel)
  }
  val issueText = issues.joinToString(" and ")

  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
      .padding(bottom = 12.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(OlliteRTWarningContainer)
      .clickable {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
          data = "package:${context.packageName}".toUri()
        }
        context.startActivity(intent)
      }
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.Warning,
      contentDescription = null,
      tint = OlliteRTWarningText,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = stringResource(R.string.models_permission_warning, issueText),
      style = MaterialTheme.typography.bodySmall,
      color = OlliteRTWarningText,
      modifier = Modifier.weight(1f),
    )
  }
}
