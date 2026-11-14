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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.prefs.ServerPrefs

/**
 * Pre-start dialog asking how many instances of the selected model to load.
 * One instance (default) behaves exactly like before; extra instances let
 * multiple clients generate at the same time at the cost of more RAM.
 */
@Composable
fun InstanceCountDialog(
  onConfirm: (count: Int) -> Unit,
  onDismiss: () -> Unit,
  modelName: String? = null,
) {
  val context = LocalContext.current
  var selectedCount by remember { mutableStateOf(ServerPrefs.getModelInstanceCount(context)) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.instance_count_title)) },
    text = {
      Column {
        Text(stringResource(R.string.instance_count_body))
        Spacer(Modifier.height(12.dp))
        listOf(1, 2, 3).forEach { n ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clickable { selectedCount = n }
              .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = selectedCount == n, onClick = { selectedCount = n })
            Spacer(Modifier.width(12.dp))
            Text(
              if (n == 1) stringResource(R.string.instance_count_one)
              else stringResource(R.string.instance_count_many, n),
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(selectedCount) }) {
        Text(stringResource(R.string.instance_count_start))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
