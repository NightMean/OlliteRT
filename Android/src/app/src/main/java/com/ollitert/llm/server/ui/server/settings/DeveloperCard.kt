package com.ollitert.llm.server.ui.server.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.server.SettingsViewModel
import com.ollitert.llm.server.ui.server.logs.exportLogcat
import kotlinx.coroutines.launch

@Composable
internal fun DeveloperCard(vm: SettingsViewModel, context: Context) {
  SettingsCard(
    icon = Icons.Outlined.BugReport,
    title = stringResource(R.string.settings_card_developer),
    searchQuery = vm.searchQuery,
  ) {
    ToggleSettingRow(
      label = stringResource(R.string.settings_verbose_debug),
      description = stringResource(R.string.settings_verbose_debug_desc),
      checked = vm.verboseDebugEnabledEntry.current,
      onCheckedChange = { vm.verboseDebugEnabledEntry.update(it) },
      searchQuery = vm.searchQuery,
    )

    AnimatedVisibility(
      visible = vm.verboseDebugEnabledEntry.current,
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
      val logcatScope = rememberCoroutineScope()
      Column {
        SettingDivider(verticalPadding = 12)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            SettingLabel(text = stringResource(R.string.settings_export_logcat), searchQuery = vm.searchQuery)
            Text(
              text = stringResource(R.string.settings_export_logcat_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          TooltipIconButton(
            onClick = { logcatScope.launch { exportLogcat(context) } },
            icon = Icons.Outlined.Share,
            tooltip = stringResource(R.string.settings_export_logcat),
          )
        }
      }
    }
  }
}
