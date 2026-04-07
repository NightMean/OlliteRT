/*
 * Copyright 2025 Google LLC
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

package com.ollitert.llm.server.ui.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

private const val TAG = "OlliteRTMemoryWarning"
private const val BYTES_IN_GB = 1024f * 1024 * 1024
private const val PREFS_NAME = "memory_warning_prefs"
private const val KEY_PREFIX = "suppress_"

/** Composable function to display a memory warning alert dialog with "Don't ask again" option. */
@Composable
fun MemoryWarningAlert(
  modelName: String,
  onProceeded: (dontAskAgain: Boolean) -> Unit,
  onDismissed: () -> Unit,
) {
  var dontAskAgain by remember { mutableStateOf(false) }

  AlertDialog(
    title = { Text(stringResource(R.string.memory_warning_title)) },
    text = {
      Column {
        Text(stringResource(R.string.memory_warning_content))
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .clickable { dontAskAgain = !dontAskAgain }
            .padding(vertical = 8.dp),
        ) {
          Checkbox(
            checked = dontAskAgain,
            onCheckedChange = { dontAskAgain = it },
            colors = CheckboxDefaults.colors(checkedColor = OlliteRTPrimary),
          )
          Text(
            text = "Don't ask again for this model",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    onDismissRequest = onDismissed,
    confirmButton = {
      TextButton(onClick = { onProceeded(dontAskAgain) }) {
        Text(stringResource(R.string.memory_warning_proceed_anyway))
      }
    },
    dismissButton = { TextButton(onClick = onDismissed) { Text(stringResource(R.string.cancel)) } },
  )
}

/** Check if memory warning is suppressed for this model. */
fun isMemoryWarningSuppressed(context: Context, modelName: String): Boolean =
  context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    .getBoolean("$KEY_PREFIX$modelName", false)

/** Suppress memory warning for this model. */
fun suppressMemoryWarning(context: Context, modelName: String) {
  context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    .edit()
    .putBoolean("$KEY_PREFIX$modelName", true)
    .apply()
}

/** Checks if the device's memory is lower than the required minimum for the given model. */
fun isMemoryLow(context: Context, model: Model): Boolean {
  val activityManager =
    context.getSystemService(android.app.Activity.ACTIVITY_SERVICE) as? ActivityManager
  val minDeviceMemoryInGb = model.minDeviceMemoryInGb
  return if (activityManager != null && minDeviceMemoryInGb != null) {
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    var deviceMemInGb = memoryInfo.totalMem / BYTES_IN_GB
    // API 34+ uses advertisedMem instead of totalMem for better accuracy.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      deviceMemInGb = memoryInfo.advertisedMem / BYTES_IN_GB
    }
    Log.d(
      TAG,
      "Device memory (GB): $deviceMemInGb. " +
        "Model's required min device memory (GB): $minDeviceMemoryInGb.",
    )
    deviceMemInGb < minDeviceMemoryInGb
  } else {
    false
  }
}
