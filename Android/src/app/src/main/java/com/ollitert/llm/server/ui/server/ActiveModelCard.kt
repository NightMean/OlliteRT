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

package com.ollitert.llm.server.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ErrorSuggestions
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.formatModelError
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTWarningYellow
import java.util.Locale

@Composable
internal fun ActiveModelCard(
  status: ServerStatus,
  modelName: String?,
  modelSizeBytes: Long,
  activeAccelerator: String?,
  thinkingEnabled: Boolean,
  speculativeDecodingEnabled: Boolean,
  modelLoadTimeMs: Long,
  isIdleUnloaded: Boolean,
  loadingElapsedSeconds: Long,
  lastError: String?,
  onReloadClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val isStopped = status == ServerStatus.STOPPED
  val isLoading = status == ServerStatus.LOADING
  val context = LocalContext.current

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(20.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Model icon box
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.ViewInAr,
          contentDescription = null,
          tint = if (isStopped) MaterialTheme.colorScheme.onSurfaceVariant else OlliteRTPrimary,
          modifier = Modifier.size(26.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = if (isStopped) stringResource(R.string.status_no_model_loaded) else (modelName ?: stringResource(R.string.status_model_loading)),
            style = MaterialTheme.typography.titleMedium,
            color = if (isStopped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (!isStopped && !isLoading) {
            StatusCapabilityChips(
              accelerator = activeAccelerator,
              thinkingEnabled = thinkingEnabled,
              mtpEnabled = speculativeDecodingEnabled,
            )
          }
        }
        Spacer(modifier = Modifier.height(2.dp))
        if (status == ServerStatus.ERROR) {
          val errorText = formatModelError(context, lastError)
          Text(
            text = errorText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
          )
          if (!lastError.isNullOrBlank()) {
            val suggestion = remember(lastError) {
              val kind = ErrorSuggestions.classifyFromString(lastError)
              ErrorSuggestions.suggest(kind, context)
            }
            if (suggestion != null) {
              Text(
                text = suggestion,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
              )
            }
          }
        } else if (isLoading) {
          Text(
            text = if (modelSizeBytes > 0) {
              stringResource(R.string.status_loading_elapsed_with_size, modelSizeBytes.humanReadableSize(), loadingElapsedSeconds)
            } else {
              stringResource(R.string.status_loading_elapsed, loadingElapsedSeconds)
            },
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTPrimary.copy(alpha = 0.7f),
          )
        } else if (!isStopped && isIdleUnloaded) {
          Text(
            text = stringResource(R.string.status_idle_unloaded),
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTWarningYellow.copy(alpha = 0.8f),
          )
        } else if (!isStopped && modelLoadTimeMs > 0) {
          Text(
            text = if (modelSizeBytes > 0) {
              stringResource(R.string.status_loaded_in_with_size, modelSizeBytes.humanReadableSize(), formatLoadTime(modelLoadTimeMs))
            } else {
              stringResource(R.string.status_loaded_in, formatLoadTime(modelLoadTimeMs))
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          )
        } else if (isStopped) {
          Text(
            text = stringResource(R.string.status_start_model_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        }
      }
      if (!isStopped) {
        if (isLoading) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = OlliteRTPrimary,
              strokeWidth = 2.dp,
            )
          }
        } else {
          TooltipIconButton(
            icon = Icons.Outlined.Refresh,
            tooltip = stringResource(R.string.status_reload_model_tooltip),
            onClick = onReloadClick,
            tint = OlliteRTPrimary,
          )
        }
      }
    }
  }
}

@Composable
internal fun ReloadModelConfirmDialog(
  modelName: String?,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.status_dialog_reload_title),
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Text(
        text = stringResource(R.string.status_dialog_reload_body, modelName ?: stringResource(R.string.status_model_loading)),
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Button(onClick = onConfirm) {
        Text(stringResource(R.string.status_dialog_reload_confirm))
      }
    },
    dismissButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ),
      ) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

internal fun formatLoadTime(ms: Long): String {
  return when {
    ms < 1000 -> "${ms}ms"
    ms < 60_000 -> String.format(Locale.US, "%.1fs", ms / 1000.0)
    else -> String.format(Locale.US, "%dm %ds", ms / 60_000, (ms % 60_000) / 1000)
  }
}
