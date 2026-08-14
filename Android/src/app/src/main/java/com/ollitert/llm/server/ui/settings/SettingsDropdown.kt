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

package com.ollitert.llm.server.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

internal data class SettingsDropdownOption<T>(
  val value: T,
  val label: String,
  val dividerAfter: Boolean = false,
)

/** Shared boxed selector used throughout Settings. */
@Composable
internal fun <T> SettingsDropdown(
  selectedValue: T,
  options: List<SettingsDropdownOption<T>>,
  onSelected: (T) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onOpen: () -> Unit = {},
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
    ?: options.firstOrNull()?.label.orEmpty()
  val arrowRotation by animateFloatAsState(
    targetValue = if (expanded) 180f else 0f,
    label = "SettingsDropdownArrowRotation",
  )

  BoxWithConstraints(modifier = modifier) {
    OutlinedTextField(
      value = selectedLabel,
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      enabled = false,
      trailingIcon = {
        Icon(
          imageVector = Icons.Outlined.ExpandMore,
          contentDescription = stringResource(
            if (expanded) R.string.cd_collapse else R.string.cd_expand,
          ),
          modifier = Modifier
            .size(24.dp)
            .rotate(arrowRotation),
        )
      },
      colors = OutlinedTextFieldDefaults.colors(
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        disabledBorderColor = if (expanded) OlliteRTPrimary else MaterialTheme.colorScheme.outline,
        disabledTrailingIconColor = if (expanded) OlliteRTPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .clickable(enabled = enabled) {
          val willExpand = !expanded
          if (willExpand) onOpen()
          expanded = willExpand
        },
    )
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      modifier = Modifier.width(maxWidth),
    ) {
      options.forEachIndexed { index, option ->
        val selected = option.value == selectedValue
        DropdownMenuItem(
          text = {
            Text(
              text = option.label,
              color = if (selected) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          trailingIcon = if (selected) {
            {
              Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = OlliteRTPrimary,
                modifier = Modifier.size(20.dp),
              )
            }
          } else {
            null
          },
          onClick = {
            onSelected(option.value)
            expanded = false
          },
          modifier = Modifier.background(
            if (selected) OlliteRTPrimary.copy(alpha = 0.12f) else Color.Transparent,
          ),
        )
        if (option.dividerAfter && index < options.lastIndex) HorizontalDivider()
      }
    }
  }
}
