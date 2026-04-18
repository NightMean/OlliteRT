package com.ollitert.llm.server.ui.server.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.server.SettingsViewModel

@Composable
internal fun AdvancedCard(vm: SettingsViewModel) {
  SettingsCard(
    icon = Icons.Outlined.Science,
    title = stringResource(R.string.settings_card_advanced),
    searchQuery = vm.searchQuery,
  ) {
    ToggleCardContent(
      keys = listOf("warmup_message", "pre_init_vision", "custom_prompts", "ignore_client_params"),
      vm = vm,
    )
  }
}
