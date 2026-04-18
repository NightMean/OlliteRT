package com.ollitert.llm.server.ui.server.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.server.SettingsViewModel

@Composable
internal fun ContextManagementCard(vm: SettingsViewModel) {
  SettingsCard(
    icon = Icons.Outlined.Compress,
    title = stringResource(R.string.settings_card_context_management),
    searchQuery = vm.searchQuery,
  ) {
    ToggleCardContent(cardId = CardId.CONTEXT_MANAGEMENT, vm = vm)
  }
}
