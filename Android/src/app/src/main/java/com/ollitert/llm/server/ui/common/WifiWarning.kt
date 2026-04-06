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
