package com.ollitert.llm.server.ui.server.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.server.SettingsViewModel

@Composable
internal fun GeneralCard(vm: SettingsViewModel) {
  SettingsCard(
    icon = Icons.Outlined.PhoneAndroid,
    title = stringResource(R.string.settings_card_general),
    searchQuery = vm.searchQuery,
  ) {
    ToggleCardContent(cardId = CardId.GENERAL, vm = vm)
  }
}
