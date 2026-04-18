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

package com.ollitert.llm.server.ui.common.modelitem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.MODEL_INFO_ICON_SIZE
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.RuntimeType
import com.ollitert.llm.server.data.Task
import com.ollitert.llm.server.ui.common.ClickableLink
import com.ollitert.llm.server.ui.common.highlightSearchMatches
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import androidx.compose.ui.platform.LocalContext
import com.ollitert.llm.server.ui.common.humanReadableSize
import com.ollitert.llm.server.ui.theme.customColors
import com.ollitert.llm.server.ui.theme.labelSmallNarrow

/**
 * Composable function to display the model name and its download status information.
 *
 * This function renders the model's name and its current download status, including:
 * - Model name.
 * - Failure message (if download failed).
 * - "Unzipping..." status for unzipping processes.
 * - Model size for successful downloads.
 */
@Composable
fun ModelNameAndStatus(
  model: Model,
  task: Task?,
  downloadStatus: ModelDownloadStatus?,
  searchQuery: String = "",
  modifier: Modifier = Modifier,
) {
  val inProgress = downloadStatus?.status == ModelDownloadStatusType.IN_PROGRESS
  val isPartiallyDownloaded = downloadStatus?.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED
  var curDownloadProgress = 0f

  val context = LocalContext.current
  Column(modifier = modifier) {
    // Show "Best Overall" badge only for Gemma 4 models.
    val isGemma4 = (model.displayName.ifEmpty { model.name }).contains("gemma-4", ignoreCase = true)
        || (model.displayName.ifEmpty { model.name }).contains("gemma 4", ignoreCase = true)
    val showBestBadge = isGemma4 && model.bestForTaskIds.isNotEmpty()
    if (showBestBadge) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 6.dp),
      ) {
        Icon(
          Icons.Filled.Star,
          tint = Color(0xFFFCC934),
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Text(
          stringResource(R.string.best_overall),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.alpha(0.6f),
        )
      }
    }

    // Model name — end padding reserves space for the overlaid action icons
    // (delete 40dp + settings 40dp + 8dp gap + 12dp card padding = 100dp worst case).
    Text(
      text = highlightSearchMatches(
        model.displayName.ifEmpty { model.name },
        searchQuery,
        OlliteRTPrimary,
      ),
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.padding(end = 100.dp),
    )

    // Capability chips (Text, Vision, Audio, Thinking)
    if (model.isLlm) {
      CapabilityChips(
        model = model,
        modifier = Modifier.padding(top = 2.dp),
        searchQuery = searchQuery,
      )
    }

    // Status icon + size + download progress details.
    var showDownloadStatusUI = true

    if (showDownloadStatusUI) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        // Status icon.
        StatusIcon(
          task = task,
          model = model,
          downloadStatus = downloadStatus,
          modifier = Modifier.padding(end = 4.dp),
        )

        // Failure message.
        if (downloadStatus != null && downloadStatus.status == ModelDownloadStatusType.FAILED) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              downloadStatus.errorMessage,
              color = MaterialTheme.colorScheme.error,
              style = labelSmallNarrow,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        // Status label
        else {
          var sizeLabel = model.totalBytes.humanReadableSize()
          if (model.localFileRelativeDirPathOverride.isNotEmpty()) {
            sizeLabel = "{ext_files_dir}/${model.localFileRelativeDirPathOverride}"
          }

          // Populate the status label.
          if (downloadStatus != null) {
            // For in-progress model, show {receivedSize} / {totalSize} - {rate} - {remainingTime}
            if (inProgress || isPartiallyDownloaded) {
              var totalSize = downloadStatus.totalBytes
              if (totalSize == 0L) {
                totalSize = model.totalBytes
              }
              sizeLabel =
                "${downloadStatus.receivedBytes.humanReadableSize(extraDecimalForGbAndAbove = true)} of ${totalSize.humanReadableSize()}"
              if (downloadStatus.bytesPerSecond > 0) {
                sizeLabel = "$sizeLabel · ${downloadStatus.bytesPerSecond.humanReadableSize()} / s"
                // if (downloadStatus.remainingMs >= 0) {
                //   sizeLabel =
                //     "$sizeLabel\n${downloadStatus.remainingMs.formatToHourMinSecond()} left"
                // }
              }
              if (isPartiallyDownloaded) {
                sizeLabel = "$sizeLabel${stringResource(R.string.model_status_resuming_suffix)}"
              }
              curDownloadProgress =
                downloadStatus.receivedBytes.toFloat() / downloadStatus.totalBytes.toFloat()
              if (curDownloadProgress.isNaN()) {
                curDownloadProgress = 0f
              }
            }
            // Status for unzipping.
            else if (downloadStatus.status == ModelDownloadStatusType.UNZIPPING) {
              sizeLabel = stringResource(R.string.model_status_unzipping)
            }
          }

          Column(
            horizontalAlignment = Alignment.Start
          ) {
            for ((index, line) in sizeLabel.split("\n").withIndex()) {
              Text(
                line,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                style =
                  MaterialTheme.typography.bodyMedium.copy(
                    // This stops numbers from "jumping around" when being updated.
                    fontFeatureSettings = "tnum"
                  ),
                overflow = TextOverflow.Visible,
                modifier = Modifier.offset(y = if (index == 0) 0.dp else (-1).dp),
              )
            }
          }
        }
      }
    }

    // Learn more url.
    if (!model.imported && model.learnMoreUrl.isNotEmpty()) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.AutoMirrored.Outlined.OpenInNew,
          tint = MaterialTheme.customColors.modelInfoIconColor,
          contentDescription = null,
          modifier = Modifier.size(MODEL_INFO_ICON_SIZE).offset(y = 1.dp),
        )
        ClickableLink(
          model.learnMoreUrl,
          linkText = stringResource(R.string.learn_more),
          textAlign = TextAlign.Left,
        )
      }
    }
  }
}
