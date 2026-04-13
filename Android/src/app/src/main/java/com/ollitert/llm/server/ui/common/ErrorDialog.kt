package com.ollitert.llm.server.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R

/**
 * Standard error dialog used across the app: AlertDialog with a red error icon,
 * a title, a text body, and a single dismiss button.
 *
 * For dialogs that need multiple action buttons or custom content (e.g. the
 * storage warning with "Download Anyway"), use a regular AlertDialog directly.
 */
@Composable
fun ErrorAlertDialog(
  title: String,
  text: String,
  onDismiss: () -> Unit,
  confirmLabel: String = stringResource(R.string.ok),
) {
  AlertDialog(
    icon = {
      Icon(
        Icons.Rounded.Error,
        contentDescription = stringResource(R.string.cd_error),
        tint = MaterialTheme.colorScheme.error,
      )
    },
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(text) },
    confirmButton = {
      Button(onClick = onDismiss) { Text(confirmLabel) }
    },
  )
}
