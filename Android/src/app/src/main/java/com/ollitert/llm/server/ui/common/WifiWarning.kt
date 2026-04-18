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

package com.ollitert.llm.server.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R

/** Alert dialog shown when the user tries to start the server without a Wi-Fi connection. */
@Composable
fun WifiWarningAlert(onStartAnyway: () -> Unit, onDismissed: () -> Unit) {
  AlertDialog(
    title = { Text(stringResource(R.string.wifi_warning_title)) },
    text = { Text(stringResource(R.string.wifi_warning_content)) },
    onDismissRequest = onDismissed,
    confirmButton = {
      TextButton(onClick = onStartAnyway) {
        Text(stringResource(R.string.wifi_warning_start_anyway))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismissed) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
