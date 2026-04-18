package com.ollitert.llm.server.ui.server.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.copyToClipboard
import com.ollitert.llm.server.ui.server.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
internal fun ServerConfigCard(vm: SettingsViewModel, context: Context) {
  val tokenRegeneratedText = stringResource(R.string.toast_token_regenerated)

  SettingsCard(
    icon = Icons.Outlined.Tune,
    title = stringResource(R.string.settings_card_server_config),
    searchQuery = vm.searchQuery,
  ) {
    if (vm.settingVisible("host_port")) {
      Text(
        text = highlightSearchMatches(stringResource(R.string.settings_host_port_label), vm.searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = vm.portText,
        onValueChange = { input ->
          vm.portText = input.filter { it.isDigit() }.take(5)
          vm.clearError("host_port")
        },
        singleLine = true,
        isError = vm.hasError("host_port"),
        placeholder = {
          Text(
            stringResource(R.string.settings_host_port_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (vm.hasError("host_port")) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (vm.hasError("host_port")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.settings_host_port_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (vm.settingVisible("host_port") && vm.settingVisible("bearer_token")) {
      SettingDivider()
    }

    if (vm.settingVisible("bearer_token")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_bearer_token),
        description = stringResource(R.string.settings_bearer_token_desc),
        checked = vm.bearerEnabledEntry.current,
        onCheckedChange = { enabled ->
          vm.bearerEnabledEntry.update(enabled)
          if (enabled && vm.bearerTokenEntry.current.isBlank()) {
            vm.bearerTokenEntry.update(java.util.UUID.randomUUID().toString().replace("-", ""))
          }
        },
        searchQuery = vm.searchQuery,
      )
      if (vm.bearerEnabledEntry.current && vm.settingVisible("bearer_token")) {
        SettingDivider()

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = vm.bearerTokenEntry.current,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.width(8.dp))

          TooltipIconButton(
            icon = Icons.Outlined.ContentCopy,
            tooltip = stringResource(R.string.settings_bearer_copy_tooltip),
            onClick = {
              copyToClipboard(context, "OlliteRT Bearer Token", vm.bearerTokenEntry.current)
            },
          )

          Spacer(modifier = Modifier.width(4.dp))

          TooltipIconButton(
            icon = Icons.Outlined.Refresh,
            tooltip = stringResource(R.string.settings_bearer_regenerate_tooltip),
            onClick = {
              vm.bearerTokenEntry.update(java.util.UUID.randomUUID().toString().replace("-", ""))
              Toast.makeText(context, tokenRegeneratedText, Toast.LENGTH_SHORT).show()
            },
          )
        }
      }
    }

    if (vm.settingVisible("bearer_token") && vm.settingVisible("cors_origins")) {
      SettingDivider()
    }

    if (vm.settingVisible("cors_origins")) {
      Text(
        text = highlightSearchMatches(stringResource(R.string.settings_cors_label), vm.searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = vm.corsAllowedOriginsEntry.current,
        onValueChange = {
          vm.corsAllowedOriginsEntry.update(it)
          if (vm.hasError("cors_origins")) vm.clearError("cors_origins")
        },
        singleLine = true,
        isError = vm.hasError("cors_origins"),
        placeholder = {
          Text(
            stringResource(R.string.settings_cors_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        trailingIcon = {
          if (vm.corsAllowedOriginsEntry.current.isNotBlank()) {
            IconButton(onClick = {
              vm.corsAllowedOriginsEntry.update("")
              if (vm.hasError("cors_origins")) vm.clearError("cors_origins")
            }) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.settings_cors_clear),
                tint = if (vm.hasError("cors_origins")) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (vm.hasError("cors_origins")) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (vm.hasError("cors_origins")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.settings_cors_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
