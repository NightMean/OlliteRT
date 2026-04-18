package com.ollitert.llm.server.ui.server.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.server.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
internal fun AutoLaunchCard(vm: SettingsViewModel, downloadedModelNames: List<String>) {
  val uriHandler = LocalUriHandler.current

  SettingsCard(
    icon = Icons.Outlined.PlayArrow,
    title = stringResource(R.string.settings_card_auto_launch),
    searchQuery = vm.searchQuery,
  ) {
    if (vm.settingVisible("default_model")) {
      Text(
        text = highlightSearchMatches(stringResource(R.string.settings_default_model_label), vm.searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      if (downloadedModelNames.isEmpty()) {
        Text(
          text = stringResource(R.string.settings_no_downloaded_models),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      } else {
        Column {
          OutlinedTextField(
            value = vm.defaultModelEntry.current ?: stringResource(R.string.settings_none_manual_start),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { vm.showModelDropdown = true },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
              disabledTextColor = MaterialTheme.colorScheme.onSurface,
              disabledBorderColor = MaterialTheme.colorScheme.outline,
              disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
          )
          DropdownMenu(
            expanded = vm.showModelDropdown,
            onDismissRequest = { vm.showModelDropdown = false },
          ) {
            DropdownMenuItem(
              text = {
                Text(
                  stringResource(R.string.settings_none_manual_start),
                  color = if (vm.defaultModelEntry.current == null) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
                )
              },
              onClick = {
                vm.defaultModelEntry.update(null)
                vm.autoStartOnBootEntry.update(false)
                vm.showModelDropdown = false
              },
            )
            HorizontalDivider()
            downloadedModelNames.forEach { modelName ->
              DropdownMenuItem(
                text = {
                  Text(
                    modelName,
                    color = if (modelName == vm.defaultModelEntry.current) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
                  )
                },
                onClick = {
                  vm.defaultModelEntry.update(modelName)
                  vm.showModelDropdown = false
                },
              )
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.settings_default_model_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (vm.settingVisible("default_model") && vm.settingVisible("start_on_boot")) {
      SettingDivider()
    }

    if (vm.settingVisible("start_on_boot")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_start_on_boot),
        description = stringResource(
          if (vm.defaultModelEntry.current == null) R.string.settings_start_on_boot_desc_no_model
          else R.string.settings_start_on_boot_desc,
        ),
        checked = vm.autoStartOnBootEntry.current,
        onCheckedChange = { vm.autoStartOnBootEntry.update(it) },
        searchQuery = vm.searchQuery,
        enabled = vm.isSettingEnabled("start_on_boot"),
        alphaOverride = vm.settingAlpha("start_on_boot"),
      )
    }

    if (vm.settingVisible("start_on_boot") && vm.settingVisible("keep_alive")) {
      SettingDivider()
    }

    if (vm.settingVisible("keep_alive")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_keep_alive),
        description = stringResource(R.string.settings_keep_alive_desc),
        checked = vm.keepAliveEnabledEntry.current,
        onCheckedChange = { vm.keepAliveEnabledEntry.update(it) },
        searchQuery = vm.searchQuery,
      )

      SettingDivider(verticalPadding = 8)

      NumericWithUnitRow(
        def = KEEP_ALIVE_TIMEOUT,
        baseValue = vm.keepAliveMinutesEntry.current.toLong(),
        savedBaseValue = vm.keepAliveMinutesEntry.saved.toLong(),
        onBaseValueChange = { vm.keepAliveMinutesEntry.update(it.toInt()) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError("keep_alive_timeout"),
        enabled = vm.keepAliveEnabledEntry.current,
        onErrorClear = { vm.clearError("keep_alive_timeout") },
        modifier = Modifier.alpha(vm.settingAlpha("keep_alive_timeout")),
      )
    }

    if (vm.settingVisible("keep_alive") && vm.settingVisible("dontkillmyapp")) {
      SettingDivider()
    }

    if (vm.settingVisible("dontkillmyapp")) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable { uriHandler.openUri("https://dontkillmyapp.com") }
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = highlightSearchMatches(stringResource(R.string.settings_dontkillmyapp_title), vm.searchQuery, OlliteRTPrimary),
            style = MaterialTheme.typography.bodyMedium,
            color = OlliteRTPrimary,
          )
          Text(
            text = stringResource(R.string.settings_dontkillmyapp_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = stringResource(R.string.settings_dontkillmyapp_cd),
          tint = OlliteRTPrimary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}
