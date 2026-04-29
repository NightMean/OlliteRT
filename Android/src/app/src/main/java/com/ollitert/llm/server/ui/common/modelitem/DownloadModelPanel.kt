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

package com.ollitert.llm.server.ui.common.modelitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.RuntimeType
import com.ollitert.llm.server.ui.common.DownloadAndTryButton
import com.ollitert.llm.server.ui.common.LoadingBlockingOverlay
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel

@Composable
fun DownloadModelPanel(
  model: Model,
  modelManagerViewModel: ModelManagerViewModel,
  downloadStatus: ModelDownloadStatus?,
  onTryItClicked: () -> Unit,
  onBenchmarkClicked: () -> Unit,
  modifier: Modifier = Modifier,
  onNavigateToSettings: () -> Unit = {},
  showBenchmarkButton: Boolean = false,
  serverStatus: ServerStatus = ServerStatus.STOPPED,
  activeModelName: String? = null,
  onStopServer: () -> Unit = {},
) {
  val downloadSucceeded = downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.End,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showBenchmarkButton && downloadSucceeded) {
      // Benchmark button.
      val isServerActive = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING
      Box(modifier = Modifier.weight(1f)) {
        Button(
          modifier = Modifier.height(42.dp).fillMaxWidth(),
          enabled = !isServerActive,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
          contentPadding = PaddingValues(horizontal = 12.dp),
          onClick = { onBenchmarkClicked() },
        ) {
          val textColor = if (isServerActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else MaterialTheme.colorScheme.onSecondaryContainer
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(Icons.Rounded.BarChart, contentDescription = null, tint = textColor)
            Text(
              stringResource(R.string.benchmark),
              color = textColor,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              autoSize =
                TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp, stepSize = 1.sp),
            )
          }
        }
        // Invisible overlay to show toast when disabled
        if (isServerActive) {
          LoadingBlockingOverlay(stringResource(R.string.model_stop_server_for_benchmark))
        }
      }

      Spacer(modifier = Modifier.width(8.dp))
    }

    // Update button — shown when a newer version is available in the allowlist.
    if (model.updatable && downloadSucceeded) {
      Button(
        modifier = Modifier.height(42.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        onClick = {
          model.latestModelFile?.let {
            model.version = it.commitHash
            model.downloadFileName = it.fileName
          }
          model.updatable = false
          modelManagerViewModel.downloadModel(model)
        },
      ) {
        val textColor = MaterialTheme.colorScheme.onSecondaryContainer
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(Icons.Outlined.Update, contentDescription = null, tint = textColor)
          Text(
            stringResource(R.string.update),
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp, stepSize = 1.sp),
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))
    }

    fun isDownloadButtonEnabled(downloadStatus: ModelDownloadStatus?, model: Model): Boolean {
      val downloadFailed = downloadStatus?.status == ModelDownloadStatusType.FAILED
      val isLitertLm = model.runtimeType == RuntimeType.LITERT_LM
      return !downloadFailed || isLitertLm
    }

    DownloadAndTryButton(
      model = model,
      downloadStatus = downloadStatus,
      enabled = isDownloadButtonEnabled(downloadStatus, model),
      modelManagerViewModel = modelManagerViewModel,
      onClicked = onTryItClicked,
      onNavigateToSettings = onNavigateToSettings,
      compact = false,
      modifier = Modifier,
      modifierWhenExpanded = Modifier.weight(1f),
      serverStatus = serverStatus,
      activeModelName = activeModelName,
      onStopServer = onStopServer,
    )
  }
}
