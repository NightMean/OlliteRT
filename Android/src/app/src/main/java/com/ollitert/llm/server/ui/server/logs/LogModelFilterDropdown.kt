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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

/**
 * Dropdown filter for the Logs screen: "All models" plus one entry per currently
 * loaded model (primary, then secondary). Stays in sync with server state, so a
 * model that is unloaded disappears from the options (and a stale selection is
 * harmless — it simply matches nothing until cleared).
 */
@Composable
internal fun LogModelFilterDropdown(
  options: List<String>,
  selected: String?,
  onSelected: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val labelStyle = MaterialTheme.typography.labelSmall.copy(
    fontFamily = SpaceGroteskFontFamily,
    fontWeight = FontWeight.Medium,
  )
  Box(modifier = modifier) {
    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(12.dp))
        .background(
          if (selected != null) OlliteRTPrimary.copy(alpha = 0.15f)
          else MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        .clickable { expanded = true }
        .padding(horizontal = 12.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.ModelTraining,
        contentDescription = null,
        tint = if (selected != null) OlliteRTPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = selected ?: "All models",
        style = labelStyle,
        color = if (selected != null) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Icon(
        imageVector = Icons.Rounded.ArrowDropDown,
        contentDescription = "Filter by model",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(14.dp),
      )
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = RoundedCornerShape(12.dp),
    ) {
      DropdownMenuItem(
        text = {
          Text(
            "All models",
            color = if (selected == null) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
          )
        },
        onClick = { onSelected(null); expanded = false },
        trailingIcon = {
          if (selected == null) {
            Icon(
              Icons.Outlined.Check,
              contentDescription = null,
              tint = OlliteRTPrimary,
              modifier = Modifier.size(18.dp),
            )
          }
        },
      )
      options.forEach { name ->
        DropdownMenuItem(
          text = {
            Text(
              name,
              color = if (selected == name) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
            )
          },
          onClick = { onSelected(name); expanded = false },
          trailingIcon = {
            if (selected == name) {
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
