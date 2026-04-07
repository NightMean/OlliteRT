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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel

private val DeleteRedTint = Color(0xFFE57373)

/** Composable function to display a button for deleting the downloaded model. */
@Composable
fun DeleteModelButton(
  model: Model,
  modelManagerViewModel: ModelManagerViewModel,
  downloadStatus: ModelDownloadStatus?,
  modifier: Modifier = Modifier,
  showDeleteButton: Boolean = true,
) {
  var showConfirmDeleteDialog by remember { mutableStateOf(false) }

  if (downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED && showDeleteButton) {
    Box(
      modifier = modifier
        .size(40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(DeleteRedTint.copy(alpha = 0.12f))
        .clickable { showConfirmDeleteDialog = true },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Outlined.Delete,
        contentDescription = stringResource(R.string.cd_delete_icon),
        tint = DeleteRedTint,
        modifier = Modifier.size(22.dp),
      )
    }
  }

  if (showConfirmDeleteDialog) {
    ConfirmDeleteModelDialog(
      model = model,
      onConfirm = {
        modelManagerViewModel.deleteModelAndRefreshStorage(model = model)
        showConfirmDeleteDialog = false
      },
      onDismiss = { showConfirmDeleteDialog = false },
    )
  }
}
