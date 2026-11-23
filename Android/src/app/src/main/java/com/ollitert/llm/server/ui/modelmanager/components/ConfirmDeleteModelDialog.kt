/*
 * Copyright 2025 Google LLC
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

package com.ollitert.llm.server.ui.modelmanager.components

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel

/** Composable function to display a confirmation dialog for deleting a model. */
@Composable
fun ConfirmDeleteModelDialog(model: Model, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.confirm_delete_model_dialog_title)) },
    text = {
      Text(stringResource(R.string.confirm_delete_model_dialog_content, model.name))
    },
    confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.ok)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
internal fun DiscoveredModelRemovalDialog(model: Model, manager: ModelManagerViewModel, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val unavailable = stringResource(R.string.repository_remove_unavailable)
  val failed = stringResource(R.string.repository_remove_failed)
  var inspectedStatus by remember(model.name) { mutableStateOf<ModelDownloadStatusType?>(null) }
  LaunchedEffect(model.name) {
    val status = manager.inspectDiscoveredModelRemoval(model)
    if (status == null) {
      Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
      onDismiss()
    } else {
      inspectedStatus = status
    }
  }
  val status = inspectedStatus ?: return
  val message = when (status) {
    ModelDownloadStatusType.SUCCEEDED -> R.string.repository_remove_downloaded
    ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> R.string.repository_remove_partial
    else -> R.string.repository_remove_imported
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.repository_remove_title)) },
    text = { Text(stringResource(message)) },
    confirmButton = {
      Button(
        onClick = {
          onDismiss()
          manager.removeDiscoveredModel(model, status) { removed ->
            if (!removed) Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
      ) { Text(stringResource(R.string.model_tooltip_delete)) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}
