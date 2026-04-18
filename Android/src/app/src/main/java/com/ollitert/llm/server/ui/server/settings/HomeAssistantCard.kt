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

package com.ollitert.llm.server.ui.server.settings

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.getWifiIpAddress
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.ui.common.copyToClipboard
import com.ollitert.llm.server.ui.server.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
internal fun HomeAssistantCard(vm: SettingsViewModel, context: Context) {
  var haIntegrationEnabled by remember { mutableStateOf(LlmHttpPrefs.isHaIntegrationEnabled(context)) }

  SettingsCard(
    iconRes = R.drawable.ic_home_assistant,
    title = stringResource(R.string.settings_card_home_assistant),
    searchQuery = vm.searchQuery,
  ) {
    ToggleSettingRow(
      label = stringResource(R.string.settings_ha_rest_api),
      description = stringResource(R.string.settings_ha_rest_api_desc),
      checked = haIntegrationEnabled,
      onCheckedChange = {
        haIntegrationEnabled = it
        LlmHttpPrefs.setHaIntegrationEnabled(context, it)
      },
      searchQuery = vm.searchQuery,
    )

    if (haIntegrationEnabled) {
      SettingDivider()

      val currentPort = vm.portText.toIntOrNull() ?: LlmHttpPrefs.getPort(context)
      val currentIp = remember { getWifiIpAddress(context) ?: "<YOUR_DEVICE_IP>" }
      val currentToken = if (vm.bearerEnabledEntry.current) vm.bearerTokenEntry.current else ""
      val baseUrl = "http://$currentIp:$currentPort"

      val authYaml = if (currentToken.isNotBlank()) "    headers:\n      Authorization: \"Bearer $currentToken\"\n" else ""

      val haConfig = buildString {
        appendLine("# OlliteRT — Home Assistant REST Integration")
        appendLine("# Add this to your configuration.yaml")
        appendLine("# Docs: GET /health?metrics=true for sensors, POST /v1/server/* for commands")
        appendLine()

        appendLine("rest:")
        appendLine("  - resource: \"$baseUrl/health?metrics=true\"")
        appendLine("    scan_interval: 30")
        if (currentToken.isNotBlank()) {
          append(authYaml)
        }
        appendLine("    sensor:")
        appendLine("      - name: \"OlliteRT Status\"")
        appendLine("        value_template: \"{{ value_json.status }}\"")
        appendLine("      - name: \"OlliteRT Model\"")
        appendLine("        value_template: \"{{ value_json.model | default('none') }}\"")
        appendLine("      - name: \"OlliteRT Uptime\"")
        appendLine("        value_template: \"{{ value_json.uptime_seconds | default(0) }}\"")
        appendLine("        unit_of_measurement: \"s\"")
        appendLine("      - name: \"OlliteRT Thinking\"")
        appendLine("        value_template: \"{{ value_json.thinking_enabled | default(false) }}\"")
        appendLine("      - name: \"OlliteRT Accelerator\"")
        appendLine("        value_template: \"{{ value_json.accelerator | default('unknown') }}\"")
        appendLine("      - name: \"OlliteRT Idle\"")
        appendLine("        value_template: \"{{ value_json.is_idle_unloaded | default(false) }}\"")
        appendLine("      - name: \"OlliteRT Requests\"")
        appendLine("        value_template: \"{{ value_json.metrics.requests_total | default(0) }}\"")
        appendLine("      - name: \"OlliteRT Errors\"")
        appendLine("        value_template: \"{{ value_json.metrics.errors_total | default(0) }}\"")
        appendLine("      - name: \"OlliteRT TTFB\"")
        appendLine("        value_template: \"{{ value_json.metrics.ttfb_avg_ms | default(0) }}\"")
        appendLine("        unit_of_measurement: \"ms\"")
        appendLine("      - name: \"OlliteRT Decode Speed\"")
        appendLine("        value_template: \"{{ value_json.metrics.decode_tokens_per_second | default(0) | round(1) }}\"")
        appendLine("        unit_of_measurement: \"t/s\"")
        appendLine("      - name: \"OlliteRT Context Usage\"")
        appendLine("        value_template: \"{{ value_json.metrics.context_utilization_percent | default(0) | round(1) }}\"")
        appendLine("        unit_of_measurement: \"%\"")
        appendLine()

        appendLine("rest_command:")

        appendLine("  ollitert_stop:")
        appendLine("    url: \"$baseUrl/v1/server/stop\"")
        appendLine("    method: POST")
        if (currentToken.isNotBlank()) append(authYaml)
        appendLine("    content_type: \"application/json\"")

        appendLine("  ollitert_reload:")
        appendLine("    url: \"$baseUrl/v1/server/reload\"")
        appendLine("    method: POST")
        if (currentToken.isNotBlank()) append(authYaml)
        appendLine("    content_type: \"application/json\"")

        appendLine("  ollitert_thinking:")
        appendLine("    url: \"$baseUrl/v1/server/thinking\"")
        appendLine("    method: POST")
        if (currentToken.isNotBlank()) append(authYaml)
        appendLine("    content_type: \"application/json\"")
        appendLine("    payload: '{\"enabled\": {{ enabled }}}'")

        appendLine("  ollitert_config:")
        appendLine("    url: \"$baseUrl/v1/server/config\"")
        appendLine("    method: POST")
        if (currentToken.isNotBlank()) append(authYaml)
        appendLine("    content_type: \"application/json\"")
        appendLine("    payload: '{{ payload }}'")
      }

      Text(
        text = stringResource(R.string.settings_ha_config_preview, currentIp, currentPort, if (currentToken.isNotBlank()) stringResource(R.string.settings_ha_config_preview_token_suffix) else ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = {
          copyToClipboard(context, "OlliteRT HA Config", haConfig)
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = OlliteRTPrimary),
      ) {
        Icon(
          imageVector = Icons.Outlined.ContentCopy,
          contentDescription = null,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.settings_ha_copy_config),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}
