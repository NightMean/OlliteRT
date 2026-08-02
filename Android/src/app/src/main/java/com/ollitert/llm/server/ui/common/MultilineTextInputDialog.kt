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

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R

/** Reusable multiline editor dialog with paste support and validation. */
@Composable
fun MultilineTextInputDialog(
  title: String,
  label: String,
  initialValue: String,
  confirmText: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
  helperText: String? = null,
  placeholder: String? = null,
  validate: ((String) -> String?)? = null,
) {
  var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
  var error by rememberSaveable { mutableStateOf<String?>(null) }
  val clipboardManager = LocalContext.current.getSystemService(ClipboardManager::class.java)

  fun confirmIfValid() {
    val trimmed = value.trim()
    val validationError = validate?.invoke(trimmed)
    if (validationError != null) {
      error = validationError
    } else {
      onConfirm(trimmed)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        helperText?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        OutlinedTextField(
          value = value,
          onValueChange = {
            value = it
            error = null
          },
          label = { Text(label) },
          placeholder = placeholder?.let { text -> { Text(text) } },
          minLines = 6,
          maxLines = 12,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
          isError = error != null,
          supportingText = error?.let { message ->
            { Text(message, color = MaterialTheme.colorScheme.error) }
          },
          colors = olliteTextFieldColors(isError = error != null),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
          onClick = {
            clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()?.let {
              value = it
              error = null
            }
          },
        ) {
          Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.size(4.dp))
          Text(stringResource(R.string.paste))
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.cancel))
        }
        TextButton(onClick = { confirmIfValid() }) {
          Text(confirmText)
        }
      }
    },
  )
}
