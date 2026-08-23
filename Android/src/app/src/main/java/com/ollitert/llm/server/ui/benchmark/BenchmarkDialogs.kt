/*
 * Copyright 2026 Google LLC
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

package com.ollitert.llm.server.ui.benchmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.common.MarkdownText
import com.ollitert.llm.server.ui.common.SHEET_MAX_WIDTH
import com.ollitert.llm.server.ui.common.SMALL_BUTTON_CONTENT_PADDING

@Composable
internal fun BenchmarkDeleteConfirmDialog(
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.delete_benchmark_result_dialog_title)) },
    text = { Text(stringResource(R.string.delete_benchmark_result_dialog_content)) },
    confirmButton = {
      Button(
        onClick = onConfirm,
        contentPadding = SMALL_BUTTON_CONTENT_PADDING,
      ) {
        Text(stringResource(R.string.delete))
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onDismiss,
        contentPadding = SMALL_BUTTON_CONTENT_PADDING,
      ) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BenchmarkComparisonHelpBottomSheet(
  sheetState: SheetState,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    sheetMaxWidth = SHEET_MAX_WIDTH,
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
        Text(
          stringResource(R.string.benchmark_comparison_help_title),
          style = MaterialTheme.typography.titleMedium,
        )
      }
      MarkdownText(
        text = stringResource(R.string.benchmark_comparison_help_content),
        smallFontSize = true,
      )
      OutlinedButton(
        onClick = onDismiss,
        contentPadding = SMALL_BUTTON_CONTENT_PADDING,
        modifier = Modifier.align(alignment = Alignment.End),
      ) {
        Text(stringResource(R.string.dismiss))
      }
    }
  }
}
