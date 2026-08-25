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

package com.ollitert.llm.server.ui.server.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.data.repository.RequestLogStore

/** Confirmation dialog for clearing logs, aware of whether a filter narrows the visible set. */
@Composable
internal fun ClearLogsConfirmDialog(
  viewModel: com.ollitert.llm.server.ui.server.LogsViewModel,
  entries: List<RequestLogEntry>,
  displayedEntries: List<RequestLogEntry>,
  filter: LogFilter,
) {
  val totalCount = entries.size
  val filteredCount = displayedEntries.size
  val isFiltered = filter.isActive && filteredCount != totalCount
  AlertDialog(
    onDismissRequest = { viewModel.setShowClearConfirmDialog(false) },
    title = {
      Text(
        text = stringResource(R.string.logs_dialog_clear_title),
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Text(
        text = if (isFiltered) {
          stringResource(R.string.logs_dialog_clear_body_filtered, totalCount, filteredCount)
        } else {
          stringResource(R.string.logs_dialog_clear_body, totalCount)
        },
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Button(
        onClick = {
          viewModel.clearLogs()
          viewModel.clearAllFilters()
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
        ),
      ) {
        Text(stringResource(R.string.logs_dialog_clear_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = { viewModel.setShowClearConfirmDialog(false) }) {
        Text(stringResource(R.string.logs_dialog_clear_cancel))
      }
    },
  )
}

/** Clear dialog variant shown while requests are still generating (Cancel | Yes | Stop). */
@Composable
internal fun ClearActiveLogsDialog(
  viewModel: com.ollitert.llm.server.ui.server.LogsViewModel,
  entries: List<RequestLogEntry>,
) {
  val pendingCount = entries.count { it.isPending }
  AlertDialog(
    onDismissRequest = { viewModel.setShowClearActiveDialog(false) },
    title = {
      Text(
        text = stringResource(R.string.logs_dialog_clear_active_title),
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Text(
        text = pluralStringResource(R.plurals.logs_dialog_clear_active_body, pendingCount, pendingCount),
        style = MaterialTheme.typography.bodyMedium,
      )
    },
    confirmButton = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = {
            viewModel.clearLogs()
            viewModel.clearAllFilters()
            viewModel.setShowClearActiveDialog(false)
          },
        ) {
          Text(stringResource(R.string.logs_dialog_clear_active_clear))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = {
              RequestLogStore.cancelAllPending()
              viewModel.clearLogs()
              viewModel.clearAllFilters()
              viewModel.setShowClearActiveDialog(false)
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
            ),
          ) {
            Text(stringResource(R.string.logs_dialog_clear_active_stop))
          }
          TextButton(onClick = { viewModel.setShowClearActiveDialog(false) }) {
            Text(stringResource(R.string.logs_dialog_clear_cancel))
          }
        }
      }
    },
  )
}
