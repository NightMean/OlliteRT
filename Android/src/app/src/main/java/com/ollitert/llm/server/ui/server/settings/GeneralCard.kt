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
    ToggleCardContent(
      keys = listOf("keep_screen_awake", "auto_expand_logs", "stream_response_preview", "compact_image_data", "hide_health_logs", "clear_logs_on_stop", "confirm_clear_logs", "keep_partial_response"),
      vm = vm,
    )
  }
}
