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

package com.ollitert.llm.server.ui.common.modelitem

import android.content.Intent
import android.os.Environment
import android.os.StatFs
import androidx.activity.result.ActivityResultLauncher
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.bytesToGb
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

private val SHEET_MAX_WIDTH = 480.dp

/**
 * Modal bottom sheet displaying user agreement instructions for a gated HuggingFace model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GatedAgreementSheet(
  model: Model,
  sheetState: SheetState,
  agreementAckLauncher: ActivityResultLauncher<Intent>,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    sheetMaxWidth = SHEET_MAX_WIDTH,
    modifier = Modifier.wrapContentHeight(),
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(horizontal = 16.dp),
    ) {
      Text(stringResource(R.string.dialog_user_agreement_title), style = MaterialTheme.typography.titleLarge)
      Text(
        stringResource(R.string.dialog_user_agreement_body),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 16.dp),
      )
      Button(
        onClick = {
          val index = model.url.indexOf("/resolve/")
          if (index >= 0) {
            val agreementUrl = model.url.substring(0, index)
            val customTabsIntent = CustomTabsIntent.Builder().build()
            customTabsIntent.intent.setData(agreementUrl.toUri())
            agreementAckLauncher.launch(customTabsIntent.intent)
          }
          onDismiss()
        }
      ) {
        Text(stringResource(R.string.button_open_user_agreement))
      }
    }
  }
}

/**
 * Dialog prompting user to configure or fix their HuggingFace API token in Settings.
 */
@Composable
internal fun HfTokenRequiredDialog(
  reason: HfTokenDialogReason,
  onNavigateToSettings: () -> Unit,
  onDismiss: () -> Unit,
) {
  val isInvalid = reason == HfTokenDialogReason.INVALID
  val titleRes = if (isInvalid) R.string.dialog_hf_token_invalid_title else R.string.dialog_hf_token_required_title
  val bodyRes = if (isInvalid) R.string.dialog_hf_token_invalid_body else R.string.dialog_hf_token_required_body
  val icon = if (isInvalid) Icons.Outlined.ErrorOutline else Icons.Outlined.Key
  val iconTint = if (isInvalid) MaterialTheme.colorScheme.error else OlliteRTPrimary

  AlertDialog(
    icon = { Icon(icon, contentDescription = null, tint = iconTint) },
    title = { Text(stringResource(titleRes)) },
    text = { Text(stringResource(bodyRes)) },
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = { onDismiss(); onNavigateToSettings() }) {
        Text(stringResource(R.string.button_go_to_settings))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
  )
}

/**
 * Storage warning dialog detailing why a download is blocked due to the 3 GB system reserve.
 */
@Composable
internal fun StorageWarningDialog(
  model: Model,
  onProceedAnyway: () -> Unit,
  onDismiss: () -> Unit,
) {
  val modelSizeGb = model.totalBytes.bytesToGb()
  val reserveGb = SYSTEM_RESERVED_STORAGE_IN_BYTES.bytesToGb()
  val totalRequiredGb = modelSizeGb + reserveGb
  val availableBytes = try {
    val stat = StatFs(Environment.getDataDirectory().path)
    stat.availableBlocksLong * stat.blockSizeLong
  } catch (_: Exception) { 0L }
  val availableGb = availableBytes.bytesToGb()

  AlertDialog(
    icon = {
      Icon(
        Icons.Rounded.Error,
        contentDescription = stringResource(R.string.cd_error),
        tint = MaterialTheme.colorScheme.error,
      )
    },
    title = { Text(stringResource(R.string.dialog_storage_warning_title)) },
    text = {
      Text(
        stringResource(
          R.string.dialog_storage_warning_body,
          totalRequiredGb,
          modelSizeGb,
          reserveGb,
          availableGb,
          (totalRequiredGb - availableGb).coerceAtLeast(0f),
        )
      )
    },
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
    },
    dismissButton = {
      TextButton(onClick = {
        onDismiss()
        onProceedAnyway()
      }) { Text(stringResource(R.string.button_download_anyway)) }
    },
  )
}

/**
 * Dialog prompting confirmation before stopping the server when requests are actively in-flight.
 */
@Composable
internal fun StopActiveRequestsDialog(
  onConfirmStop: () -> Unit,
  onDismiss: () -> Unit,
) {
  val entries by RequestLogStore.entries.collectAsStateWithLifecycle()
  val pendingCount = entries.count { it.isPending }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.logs_dialog_stop_active_title),
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Text(
        text = pluralStringResource(R.plurals.logs_dialog_stop_active_body, pendingCount, pendingCount),
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Button(
        onClick = {
          onDismiss()
          onConfirmStop()
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
        ),
      ) {
        Text(stringResource(R.string.logs_dialog_clear_active_stop))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.logs_dialog_clear_cancel))
      }
    },
  )
}
