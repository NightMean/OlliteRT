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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.MODEL_INFO_ICON_SIZE
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.ui.theme.customColors

/** Composable function to display an icon representing the download status of a model. */
@Composable
fun StatusIcon(
  model: Model,
  downloadStatus: ModelDownloadStatus?,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = modifier,
  ) {
    val color = MaterialTheme.colorScheme.primary
    if (model.localFileRelativeDirPathOverride.isNotEmpty()) {
      Icon(
        Icons.Filled.DownloadForOffline,
        tint = color,
        contentDescription = stringResource(R.string.cd_downloaded_icon),
        modifier = Modifier.size(MODEL_INFO_ICON_SIZE),
      )
    } else {
      when (downloadStatus?.status) {
        ModelDownloadStatusType.NOT_DOWNLOADED ->
          Icon(
            Icons.AutoMirrored.Outlined.HelpOutline,
            tint = MaterialTheme.customColors.modelInfoIconColor,
            contentDescription = stringResource(R.string.cd_not_downloaded_icon),
            modifier = Modifier.size(MODEL_INFO_ICON_SIZE),
          )

        ModelDownloadStatusType.SUCCEEDED -> {
          Icon(
            Icons.Filled.DownloadForOffline,
            tint = color,
            contentDescription = stringResource(R.string.cd_downloaded_icon),
            modifier = Modifier.size(MODEL_INFO_ICON_SIZE),
          )
        }

        ModelDownloadStatusType.FAILED ->
          Icon(
            Icons.Rounded.Error,
            tint = Color(0xFFAA0000),
            contentDescription = stringResource(R.string.cd_download_failed_icon),
            modifier = Modifier.size(MODEL_INFO_ICON_SIZE),
          )

        ModelDownloadStatusType.IN_PROGRESS ->
          Icon(
            Icons.Rounded.Downloading,
            contentDescription = stringResource(R.string.cd_downloading_icon),
            modifier = Modifier.size(MODEL_INFO_ICON_SIZE),
          )

        else -> {}
      }
    }
  }
}

