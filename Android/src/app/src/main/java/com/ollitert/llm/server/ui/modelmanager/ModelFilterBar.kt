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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.ModelCapability
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

/** Filter mode for the models list. */
enum class ModelFilter {
  ALL,
  DOWNLOADED,
  AVAILABLE,
  IMPORTED,
}

/** Capability filter for models. */
enum class CapabilityFilter(val labelResId: Int, val capability: ModelCapability) {
  VISION(R.string.capability_vision, ModelCapability.VISION),
  AUDIO(R.string.capability_audio, ModelCapability.AUDIO),
  THINKING(R.string.capability_thinking, ModelCapability.THINKING),
  TOOLS(R.string.capability_tools, ModelCapability.TOOLS),
  NPU(R.string.capability_npu, ModelCapability.NPU),
  SPECULATIVE_DECODING(R.string.capability_speculative_decoding, ModelCapability.SPECULATIVE_DECODING),
}

@Composable
fun ModelFilterChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val chipBgColor by animateColorAsState(
    targetValue = if (selected) OlliteRTPrimary
    else Color.Transparent,
    animationSpec = tween(200),
    label = "chip_bg",
  )
  val chipBorderColor by animateColorAsState(
    targetValue = if (selected) OlliteRTPrimary
    else MaterialTheme.colorScheme.outlineVariant,
    animationSpec = tween(200),
    label = "chip_border",
  )

  FilterChip(
    selected = selected,
    onClick = onClick,
    label = {
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    colors = FilterChipDefaults.filterChipColors(
      selectedContainerColor = chipBgColor,
      containerColor = chipBgColor,
    ),
    border = FilterChipDefaults.filterChipBorder(
      enabled = true,
      selected = selected,
      borderColor = chipBorderColor,
      selectedBorderColor = chipBorderColor,
    ),
    shape = if (selected) RoundedCornerShape(12.dp) else RoundedCornerShape(50),
  )
}

@Composable
fun MoreFiltersButton(
  active: Boolean,
  onClick: () -> Unit,
) {
  TooltipIconButton(
    icon = Icons.Outlined.FilterList,
    tooltip = stringResource(R.string.models_tooltip_more_filters),
    onClick = onClick,
    backgroundColor = if (active) OlliteRTPrimary else MaterialTheme.colorScheme.surfaceContainerHigh,
    tint = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
fun ModelFilterSection(
  activeFilter: ModelFilter,
  onFilterSelected: (ModelFilter) -> Unit,
  hasImportedModels: Boolean,
  showMoreFilters: Boolean,
  onToggleMoreFilters: () -> Unit,
  activeCapabilities: Set<CapabilityFilter>,
  onToggleCapability: (CapabilityFilter) -> Unit,
  availableCapabilityFilters: List<CapabilityFilter>,
  activeSort: ModelSort,
  sortAscending: Boolean,
  showSortDropdown: Boolean,
  onToggleSortDropdown: () -> Unit,
  onDismissSortDropdown: () -> Unit,
  onSortSelected: (ModelSort) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Scrollable filter chips — takes remaining space
      Row(
        modifier = Modifier
          .weight(1f)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ModelFilterChip(
          label = stringResource(R.string.filter_all),
          selected = activeFilter == ModelFilter.ALL,
          onClick = { onFilterSelected(ModelFilter.ALL) },
        )
        ModelFilterChip(
          label = stringResource(R.string.filter_downloaded),
          selected = activeFilter == ModelFilter.DOWNLOADED,
          onClick = { onFilterSelected(ModelFilter.DOWNLOADED) },
        )
        ModelFilterChip(
          label = stringResource(R.string.filter_available),
          selected = activeFilter == ModelFilter.AVAILABLE,
          onClick = { onFilterSelected(ModelFilter.AVAILABLE) },
        )
        if (hasImportedModels) {
          ModelFilterChip(
            label = stringResource(R.string.filter_imported),
            selected = activeFilter == ModelFilter.IMPORTED,
            onClick = { onFilterSelected(ModelFilter.IMPORTED) },
          )
        }
      }
      // Fixed action buttons — pinned to the right edge
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        MoreFiltersButton(
          active = showMoreFilters || activeCapabilities.isNotEmpty(),
          onClick = onToggleMoreFilters,
        )
        SortButton(
          activeSort = activeSort,
          sortAscending = sortAscending,
          showDropdown = showSortDropdown,
          onToggleDropdown = onToggleSortDropdown,
          onDismissDropdown = onDismissSortDropdown,
          onSortSelected = onSortSelected,
        )
      }
    }

    // Expandable capability filters
    AnimatedVisibility(
      visible = showMoreFilters,
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(
          stringResource(R.string.models_filter_capabilities),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          availableCapabilityFilters.forEach { cap ->
            val isSelected = cap in activeCapabilities
            ModelFilterChip(
              label = stringResource(cap.labelResId),
              selected = isSelected,
              onClick = { onToggleCapability(cap) },
            )
          }
        }
      }
    }
  }
}
