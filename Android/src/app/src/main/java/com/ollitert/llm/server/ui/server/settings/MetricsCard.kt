package com.ollitert.llm.server.ui.server.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.server.SettingsViewModel

@Composable
internal fun MetricsCard(vm: SettingsViewModel) {
  SettingsCard(
    icon = Icons.Outlined.BarChart,
    title = stringResource(R.string.settings_card_metrics),
    searchQuery = vm.searchQuery,
  ) {
    ToggleCardContent(cardId = CardId.METRICS, vm = vm, dividerPadding = 8)
  }
}
