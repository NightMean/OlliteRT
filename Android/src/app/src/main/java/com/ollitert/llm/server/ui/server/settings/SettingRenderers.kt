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

package com.ollitert.llm.server.ui.server.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.ui.common.highlightSearchMatches
import com.ollitert.llm.server.ui.server.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

/** Setting name text with search term highlighting. */
@Composable
fun SettingLabel(text: String, searchQuery: String) {
  Text(
    text = highlightSearchMatches(text, searchQuery, OlliteRTPrimary),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurface,
  )
}

/** Divider between settings within a card. */
@Composable
fun SettingDivider(verticalPadding: Int = 16) {
  Spacer(modifier = Modifier.height(verticalPadding.dp))
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
  Spacer(modifier = Modifier.height(verticalPadding.dp))
}

/**
 * Standard toggle setting row: label + description on the left, Switch on the right.
 * Replaces ~20 identical Row > Column + Switch blocks across SettingsScreen.
 */
@Composable
fun ToggleSettingRow(
  label: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  searchQuery: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  alphaOverride: Float = 1f,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .alpha(alphaOverride),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
      SettingLabel(text = label, searchQuery = searchQuery)
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
    )
  }
}

/**
 * Numeric input with unit dropdown (e.g. "5 minutes", "24 hours", "7 days").
 * Used by keep_alive_timeout, check_frequency, and log_auto_delete.
 *
 * @param def The NumericWithUnit definition containing unit options and conversion functions
 * @param baseValue The current value in base units (minutes/hours depending on the setting)
 * @param savedBaseValue The saved value used to derive initial display value + unit
 * @param onBaseValueChange Called with the new value in base units when user edits
 * @param isError Whether to show error styling
 * @param enabled Whether the input is interactive
 */
@Composable
fun NumericWithUnitRow(
  def: SettingDef.NumericWithUnit,
  baseValue: Long,
  savedBaseValue: Long,
  onBaseValueChange: (Long) -> Unit,
  searchQuery: String,
  modifier: Modifier = Modifier,
  isError: Boolean = false,
  enabled: Boolean = true,
  onErrorClear: () -> Unit = {},
) {
  val focusManager = LocalFocusManager.current
  val (initialDisplayValue, initialUnit) = remember(savedBaseValue) {
    def.fromBaseUnit(savedBaseValue)
  }
  var valueText by remember { mutableStateOf(initialDisplayValue.toString()) }
  var selectedUnit by remember { mutableStateOf(initialUnit) }
  var showUnitDropdown by remember { mutableStateOf(false) }

  fun recompute() {
    val num = valueText.toLongOrNull() ?: 0L
    onBaseValueChange(def.toBaseUnit(num, selectedUnit))
    onErrorClear()
  }

  Column(modifier = modifier) {
    Text(
      text = highlightSearchMatches(
        androidx.compose.ui.res.stringResource(def.labelRes),
        searchQuery,
        OlliteRTPrimary,
      ),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = valueText,
        onValueChange = { text ->
          val filtered = text.filter { it.isDigit() }.take(5)
          valueText = filtered
          recompute()
        },
        singleLine = true,
        isError = isError,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (isError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
          cursorColor = OlliteRTPrimary,
        ),
      )
      Column {
        OutlinedTextField(
          value = selectedUnit,
          onValueChange = {},
          readOnly = true,
          singleLine = true,
          modifier = Modifier
            .widthIn(min = 90.dp, max = 120.dp)
            .clickable(enabled = enabled) {
              focusManager.clearFocus()
              showUnitDropdown = true
            },
          enabled = false,
          colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          ),
        )
        DropdownMenu(
          expanded = showUnitDropdown,
          onDismissRequest = { showUnitDropdown = false },
        ) {
          def.unitOptions.forEach { unit ->
            DropdownMenuItem(
              text = {
                Text(
                  unit,
                  color = if (unit == selectedUnit) OlliteRTPrimary
                          else MaterialTheme.colorScheme.onSurface,
                )
              },
              onClick = {
                selectedUnit = unit
                showUnitDropdown = false
                recompute()
              },
            )
          }
        }
      }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = androidx.compose.ui.res.stringResource(def.descriptionRes),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/**
 * Renders a list of toggle settings with automatic dividers between visible items.
 * Used by cards that contain only uniform toggle rows (General, Advanced, Metrics).
 */
@Composable
internal fun ToggleCardContent(
  cardId: CardId,
  vm: SettingsViewModel,
  dividerPadding: Int = 16,
) {
  val keys = cardDefsById[cardId]?.settings?.map { it.key } ?: return
  val visible = keys.map { vm.settingVisible(it) }
  var visibleCount = 0
  keys.forEachIndexed { index, key ->
    if (!visible[index]) return@forEachIndexed
    if (visibleCount > 0) SettingDivider(verticalPadding = dividerPadding)
    visibleCount++
    val entry = vm.getToggleEntry(key) ?: return@forEachIndexed
    val def = settingDefsByKey[key] as? SettingDef.Toggle ?: return@forEachIndexed
    ToggleSettingRow(
      label = stringResource(def.labelRes),
      description = stringResource(def.descriptionRes),
      checked = entry.current,
      onCheckedChange = { entry.update(it) },
      searchQuery = vm.searchQuery,
      enabled = vm.isSettingEnabled(key),
      alphaOverride = vm.settingAlpha(key),
    )
  }
}

@Composable
internal fun SettingsCard(
  icon: ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
  content: @Composable () -> Unit,
) {
  SettingsCardLayout(
    iconContent = {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = OlliteRTPrimary,
        modifier = Modifier.size(20.dp),
      )
    },
    title = title,
    modifier = modifier,
    searchQuery = searchQuery,
    content = content,
  )
}

/** Overload for cards that use a drawable resource icon (e.g. brand icons like Home Assistant). */
@Composable
internal fun SettingsCard(
  iconRes: Int,
  title: String,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
  content: @Composable () -> Unit,
) {
  SettingsCardLayout(
    iconContent = {
      Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = OlliteRTPrimary,
        modifier = Modifier.size(20.dp),
      )
    },
    title = title,
    modifier = modifier,
    searchQuery = searchQuery,
    content = content,
  )
}

/** Overload that accepts a [CardIcon] sealed class for data-driven card rendering. */
@Composable
internal fun SettingsCard(
  cardIcon: CardIcon,
  title: String,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
  content: @Composable () -> Unit,
) {
  when (cardIcon) {
    is CardIcon.Vector ->
      SettingsCard(icon = cardIcon.icon, title = title, modifier = modifier, searchQuery = searchQuery, content = content)
    is CardIcon.Resource ->
      SettingsCard(iconRes = cardIcon.resId, title = title, modifier = modifier, searchQuery = searchQuery, content = content)
  }
}

@Composable
internal fun SettingsCardLayout(
  iconContent: @Composable () -> Unit,
  title: String,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(20.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      iconContent()
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = highlightSearchMatches(title, searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Spacer(modifier = Modifier.height(12.dp))
    content()
  }
}

/**
 * Simple numeric input field (no unit dropdown).
 * Used by log_max_entries and host_port.
 */
@Composable
fun NumericInputRow(
  label: String,
  description: String,
  value: String,
  onValueChange: (String) -> Unit,
  searchQuery: String,
  modifier: Modifier = Modifier,
  isError: Boolean = false,
  enabled: Boolean = true,
  maxLength: Int = 5,
  placeholder: String = "",
) {
  Column(modifier = modifier) {
    Text(
      text = highlightSearchMatches(label, searchQuery, OlliteRTPrimary),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    OutlinedTextField(
      value = value,
      onValueChange = { text ->
        onValueChange(text.filter { it.isDigit() }.take(maxLength))
      },
      singleLine = true,
      isError = isError,
      enabled = enabled,
      placeholder = if (placeholder.isNotEmpty()) {
        {
          Text(
            placeholder,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        }
      } else null,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = if (isError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
        unfocusedBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        cursorColor = OlliteRTPrimary,
      ),
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
