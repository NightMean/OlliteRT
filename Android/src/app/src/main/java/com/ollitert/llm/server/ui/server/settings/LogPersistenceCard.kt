package com.ollitert.llm.server.ui.server.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.server.SettingsViewModel

@Composable
internal fun LogPersistenceCard(vm: SettingsViewModel) {
  SettingsCard(
    icon = Icons.Outlined.Storage,
    title = stringResource(R.string.settings_card_log_persistence),
    searchQuery = vm.searchQuery,
  ) {
    ToggleSettingRow(
      label = stringResource(R.string.settings_persist_logs),
      description = stringResource(R.string.settings_persist_logs_desc),
      checked = vm.logPersistenceEnabledEntry.current,
      onCheckedChange = { vm.logPersistenceEnabledEntry.update(it) },
      searchQuery = vm.searchQuery,
    )

    val childAlpha = vm.settingAlpha("log_max_entries")

    SettingDivider(verticalPadding = 8)

    var maxEntriesText by remember { mutableStateOf(vm.logMaxEntriesEntry.current.toString()) }
    NumericInputRow(
      label = stringResource(R.string.settings_max_log_entries_label),
      description = stringResource(R.string.settings_max_log_entries_desc),
      value = maxEntriesText,
      onValueChange = { text ->
        maxEntriesText = text
        text.toIntOrNull()?.let { vm.logMaxEntriesEntry.update(it) }
      },
      searchQuery = vm.searchQuery,
      enabled = vm.isSettingEnabled("log_max_entries"),
      modifier = Modifier.alpha(childAlpha),
    )

    SettingDivider(verticalPadding = 8)

    NumericWithUnitRow(
      def = LOG_AUTO_DELETE,
      baseValue = vm.logAutoDeleteMinutesEntry.current,
      savedBaseValue = vm.logAutoDeleteMinutesEntry.saved,
      onBaseValueChange = { vm.logAutoDeleteMinutesEntry.update(it) },
      searchQuery = vm.searchQuery,
      enabled = vm.isSettingEnabled("log_auto_delete"),
      modifier = Modifier.alpha(childAlpha),
    )

    SettingDivider(verticalPadding = 8)

    Column(modifier = Modifier.alpha(childAlpha)) {
      Button(
        onClick = { vm.showClearPersistedDialog = true },
        enabled = vm.isSettingEnabled("clear_all_logs"),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
        ),
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(stringResource(R.string.settings_clear_all_logs_button), fontWeight = FontWeight.Bold)
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.settings_clear_all_logs_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
