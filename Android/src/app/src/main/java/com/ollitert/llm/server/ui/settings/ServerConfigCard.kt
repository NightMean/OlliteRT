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

package com.ollitert.llm.server.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.copyToClipboard
import com.ollitert.llm.server.data.prefs.ClientIpAccessPolicy
import com.ollitert.llm.server.data.prefs.ClientIpPolicyCompileResult
import com.ollitert.llm.server.data.prefs.ClientIpPolicyConfig
import com.ollitert.llm.server.data.prefs.ClientIpPolicyMode
import com.ollitert.llm.server.data.prefs.ServerBindMode
import com.ollitert.llm.server.service.inference.BridgeUtils
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.highlightSearchMatches
import com.ollitert.llm.server.ui.common.MultilineTextInputDialog
import com.ollitert.llm.server.ui.common.olliteTextFieldColors
import com.ollitert.llm.server.ui.settings.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
internal fun ServerConfigCard(vm: SettingsViewModel, context: Context) {
  val tokenRegeneratedText = stringResource(R.string.toast_token_regenerated)
  var showClientIpRulesDialog by rememberSaveable { mutableStateOf(false) }
  val bindMode = ServerBindMode.fromPreference(vm.serverBindModeEntry.current)
  val policyMode = ClientIpPolicyMode.fromPreference(vm.clientIpPolicyModeEntry.current)
  val showBindMode = vm.settingVisible(SERVER_BIND_MODE.key)
  val showCustomBind = vm.settingVisible(CUSTOM_BIND_ADDRESS.key) &&
    (bindMode == ServerBindMode.CUSTOM || vm.searchQuery.isNotBlank())
  val showPort = vm.settingVisible(HOST_PORT.key)
  val showPolicyMode = vm.settingVisible(CLIENT_IP_POLICY_MODE.key)
  val showIpRules = vm.settingVisible(CLIENT_IP_RULES.key) &&
    (policyMode != ClientIpPolicyMode.ALLOW_ALL || vm.searchQuery.isNotBlank())
  val showBearer = vm.settingVisible(BEARER_TOKEN.key)
  val showCors = vm.settingVisible(CORS_ORIGINS.key)

  SettingsCard(
    icon = Icons.Outlined.Tune,
    title = stringResource(R.string.settings_card_server_config),
    searchQuery = vm.searchQuery,
  ) {
    if (showBindMode) {
      ServerConfigDropdown(
        label = stringResource(R.string.settings_bind_mode_label),
        description = stringResource(R.string.settings_bind_mode_desc),
        selectedValue = vm.serverBindModeEntry.current,
        options = listOf(
          ServerBindMode.ALL_INTERFACES.preferenceValue to stringResource(R.string.settings_bind_mode_all_interfaces),
          ServerBindMode.LOOPBACK.preferenceValue to stringResource(R.string.settings_bind_mode_loopback),
          ServerBindMode.CUSTOM.preferenceValue to stringResource(R.string.settings_bind_mode_custom),
        ),
        searchQuery = vm.searchQuery,
        onSelected = { vm.serverBindModeEntry.update(it) },
      )
    }

    if (showCustomBind && showBindMode) SettingDivider()

    if (showCustomBind) {
      Text(
        text = highlightSearchMatches(stringResource(R.string.settings_custom_bind_address_label), vm.searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = vm.customBindAddressEntry.current,
        onValueChange = {
          vm.customBindAddressEntry.update(it)
          vm.clearError(CUSTOM_BIND_ADDRESS.key)
        },
        enabled = vm.isSettingEnabled(CUSTOM_BIND_ADDRESS.key),
        singleLine = true,
        isError = vm.hasError(CUSTOM_BIND_ADDRESS.key),
        placeholder = { Text(stringResource(R.string.settings_custom_bind_address_placeholder)) },
        supportingText = vm.validationErrors[CUSTOM_BIND_ADDRESS.key]?.let { error ->
          { Text(error, color = MaterialTheme.colorScheme.error) }
        },
        colors = olliteTextFieldColors(isError = vm.hasError(CUSTOM_BIND_ADDRESS.key)),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.settings_custom_bind_address_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (showPort && (showBindMode || showCustomBind)) SettingDivider()

    if (showPort) {
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
          vm.clearError(HOST_PORT.key)
        },
        singleLine = true,
        isError = vm.hasError(HOST_PORT.key),
        placeholder = {
          Text(
            stringResource(R.string.settings_host_port_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = olliteTextFieldColors(isError = vm.hasError(HOST_PORT.key)),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.settings_host_port_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (showPolicyMode && (showBindMode || showCustomBind || showPort)) SettingDivider()

    if (showPolicyMode) {
      ServerConfigDropdown(
        label = stringResource(R.string.settings_client_ip_policy_label),
        description = stringResource(R.string.settings_client_ip_policy_desc),
        selectedValue = vm.clientIpPolicyModeEntry.current,
        options = listOf(
          ClientIpPolicyMode.ALLOW_ALL.preferenceValue to stringResource(R.string.settings_client_ip_policy_allow_all),
          ClientIpPolicyMode.ALLOW_ONLY.preferenceValue to stringResource(R.string.settings_client_ip_policy_allow_only),
          ClientIpPolicyMode.BLOCK_LISTED.preferenceValue to stringResource(R.string.settings_client_ip_policy_block_listed),
        ),
        searchQuery = vm.searchQuery,
        onSelected = {
          vm.clientIpPolicyModeEntry.update(it)
          vm.clearError(CLIENT_IP_RULES.key)
        },
      )
    }

    if (showIpRules && (showBindMode || showCustomBind || showPort || showPolicyMode)) SettingDivider()

    if (showIpRules) {
      val ruleCount = countIpRuleEntries(vm.clientIpRulesEntry.current)
      val rulesError = vm.validationErrors[CLIENT_IP_RULES.key]
      Text(
        text = highlightSearchMatches(
          stringResource(R.string.settings_client_ip_rules_label),
          vm.searchQuery,
          OlliteRTPrimary,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = if (ruleCount == 0) {
          stringResource(R.string.settings_client_ip_rules_none)
        } else {
          pluralStringResource(R.plurals.settings_client_ip_rules_count, ruleCount, ruleCount)
        },
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        enabled = false,
        isError = rulesError != null,
        trailingIcon = {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = stringResource(R.string.settings_client_ip_rules_edit),
            modifier = Modifier.size(20.dp),
          )
        },
        supportingText = rulesError?.let { error ->
          { Text(error, color = MaterialTheme.colorScheme.error) }
        },
        colors = OutlinedTextFieldDefaults.colors(
          disabledTextColor = MaterialTheme.colorScheme.onSurface,
          disabledBorderColor = if (rulesError != null) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.outline
          },
          disabledTrailingIconColor = OlliteRTPrimary,
          disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .clickable(enabled = vm.isSettingEnabled(CLIENT_IP_RULES.key)) {
            showClientIpRulesDialog = true
          },
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = stringResource(R.string.settings_client_ip_rules_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (showBearer && (showBindMode || showCustomBind || showPort || showPolicyMode || showIpRules)) {
      SettingDivider()
    }

    if (showBearer) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_bearer_token),
        description = stringResource(R.string.settings_bearer_token_desc),
        checked = vm.bearerEnabledEntry.current,
        onCheckedChange = { enabled ->
          vm.bearerEnabledEntry.update(enabled)
          if (enabled && vm.bearerTokenEntry.current.isBlank()) {
            vm.bearerTokenEntry.update(BridgeUtils.generateBearerToken())
          }
        },
        searchQuery = vm.searchQuery,
      )
      if (vm.bearerEnabledEntry.current && vm.settingVisible(BEARER_TOKEN.key)) {
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
              vm.bearerTokenEntry.update(BridgeUtils.generateBearerToken())
              Toast.makeText(context, tokenRegeneratedText, Toast.LENGTH_SHORT).show()
            },
          )
        }
      }
    }

    if (showCors && (showBindMode || showCustomBind || showPort || showPolicyMode || showIpRules || showBearer)) {
      SettingDivider()
    }

    if (showCors) {
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
          if (vm.hasError(CORS_ORIGINS.key)) vm.clearError(CORS_ORIGINS.key)
        },
        singleLine = true,
        isError = vm.hasError(CORS_ORIGINS.key),
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
              if (vm.hasError(CORS_ORIGINS.key)) vm.clearError(CORS_ORIGINS.key)
            }) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.settings_cors_clear),
                tint = if (vm.hasError(CORS_ORIGINS.key)) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        colors = olliteTextFieldColors(isError = vm.hasError(CORS_ORIGINS.key)),
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

  if (showClientIpRulesDialog) {
    val activePolicyMode = ClientIpPolicyMode.fromPreference(vm.clientIpPolicyModeEntry.current)
    MultilineTextInputDialog(
      title = stringResource(R.string.settings_client_ip_rules_dialog_title),
      label = stringResource(R.string.settings_client_ip_rules_label),
      initialValue = vm.clientIpRulesEntry.current,
      confirmText = stringResource(R.string.settings_client_ip_rules_apply),
      helperText = stringResource(R.string.settings_client_ip_rules_dialog_help),
      placeholder = stringResource(R.string.settings_client_ip_rules_placeholder),
      validate = { input -> clientIpRulesValidationError(activePolicyMode, input, context) },
      onDismiss = { showClientIpRulesDialog = false },
      onConfirm = { input ->
        val result = ClientIpAccessPolicy.compile(ClientIpPolicyConfig(activePolicyMode, input))
        if (result is ClientIpPolicyCompileResult.Success) {
          vm.clientIpRulesEntry.update(result.normalizedRulesText)
          vm.clearError(CLIENT_IP_RULES.key)
          showClientIpRulesDialog = false
        }
      },
    )
  }
}

private fun countIpRuleEntries(rulesText: String): Int = rulesText
  .split(Regex("[,\\r\\n]+"))
  .count { it.isNotBlank() }

private fun clientIpRulesValidationError(
  mode: ClientIpPolicyMode,
  rulesText: String,
  context: Context,
): String? = when (val result = ClientIpAccessPolicy.compile(ClientIpPolicyConfig(mode, rulesText))) {
  ClientIpPolicyCompileResult.EmptyRules -> context.getString(R.string.validation_client_ip_rules_required)
  is ClientIpPolicyCompileResult.InvalidRule -> context.getString(
    R.string.validation_client_ip_rule_invalid,
    result.position,
    result.input,
  )
  is ClientIpPolicyCompileResult.Success -> null
}

@Composable
private fun ServerConfigDropdown(
  label: String,
  description: String,
  selectedValue: String?,
  options: List<Pair<String, String>>,
  searchQuery: String,
  onSelected: (String) -> Unit,
) {
  Text(
    text = highlightSearchMatches(label, searchQuery, OlliteRTPrimary),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  Spacer(modifier = Modifier.height(4.dp))
  SettingsDropdown(
    selectedValue = selectedValue ?: options.first().first,
    options = options.map { (value, optionLabel) ->
      SettingsDropdownOption(value = value, label = optionLabel)
    },
    onSelected = onSelected,
    modifier = Modifier.fillMaxWidth(),
  )
  Spacer(modifier = Modifier.height(4.dp))
  Text(
    text = description,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
