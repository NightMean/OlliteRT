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

package com.ollitert.llm.server.ui.modelmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

/** Sort mode for the models list. */
enum class ModelSort(val labelResId: Int) {
  DEFAULT(R.string.models_sort_default),
  ALPHABETICAL(R.string.models_sort_name),
  SIZE(R.string.models_sort_size),
}

@Composable
fun SortButton(
  activeSort: ModelSort,
  sortAscending: Boolean,
  showDropdown: Boolean,
  onToggleDropdown: () -> Unit,
  onDismissDropdown: () -> Unit,
  onSortSelected: (ModelSort) -> Unit,
) {
  Box {
    TooltipIconButton(
      icon = Icons.AutoMirrored.Outlined.Sort,
      tooltip = stringResource(R.string.models_tooltip_sort),
      onClick = onToggleDropdown,
      backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    DropdownMenu(
      expanded = showDropdown,
      onDismissRequest = onDismissDropdown,
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = RoundedCornerShape(12.dp),
    ) {
      ModelSort.entries.forEach { sort ->
        val isActive = activeSort == sort
        DropdownMenuItem(
          text = {
            Text(
              stringResource(sort.labelResId),
              color = if (isActive) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
            )
          },
          onClick = { onSortSelected(sort) },
          trailingIcon = {
            if (isActive && sort != ModelSort.DEFAULT) {
              Icon(
                if (sortAscending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                contentDescription = null,
                tint = OlliteRTPrimary,
                modifier = Modifier.size(18.dp),
              )
            } else if (isActive) {
              Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = OlliteRTPrimary,
                modifier = Modifier.size(18.dp),
              )
            }
          },
        )
      }
    }
  }
}
