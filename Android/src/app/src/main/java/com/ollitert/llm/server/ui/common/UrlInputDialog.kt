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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R

@Composable
fun UrlInputDialog(
  title: String,
  label: String,
  confirmText: String,
  loadingText: String,
  isLoading: Boolean,
  error: String?,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
  helperText: String? = null,
) {
  var url by rememberSaveable { mutableStateOf("") }
  val clipboardManager = LocalContext.current.getSystemService(android.content.ClipboardManager::class.java)

  AlertDialog(
    onDismissRequest = { if (!isLoading) onDismiss() },
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (helperText != null) {
          Text(
            helperText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text(label) },
          placeholder = { Text("https://...") },
          singleLine = true,
          enabled = !isLoading,
          modifier = Modifier.fillMaxWidth(),
          isError = error != null,
          supportingText = if (error != null) {
            { Text(error, color = MaterialTheme.colorScheme.error) }
          } else null,
        )
        if (url.trim().startsWith("http://", ignoreCase = true)) {
          Text(
            stringResource(R.string.http_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
        if (isLoading) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
              loadingText,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    },
    confirmButton = {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
          onClick = {
            clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()?.let { url = it }
          },
          enabled = !isLoading,
        ) {
          Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(4.dp))
          Text(stringResource(R.string.paste))
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss, enabled = !isLoading) {
          Text(stringResource(R.string.cancel))
        }
        TextButton(
          onClick = { onConfirm(url.trim()) },
          enabled = url.isNotBlank() && !isLoading,
        ) {
          Text(confirmText)
        }
      }
    },
  )
}
