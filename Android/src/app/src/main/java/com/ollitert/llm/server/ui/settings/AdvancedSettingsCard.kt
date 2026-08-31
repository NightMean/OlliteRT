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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.settings.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.common.olliteTextFieldColors
import com.ollitert.llm.server.ui.theme.customColors
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
internal fun AdvancedSettingsCard(vm: SettingsViewModel) {
  val uriHandler = LocalUriHandler.current
  CollapsibleSettingsCard(
    icon = Icons.Outlined.Science,
    title = stringResource(R.string.settings_card_advanced_settings),
    expanded = vm.advancedSettingsExpanded || vm.shouldAutoExpandAdvanced,
    onExpandedChange = { vm.advancedSettingsExpanded = it },
    searchQuery = vm.searchQuery,
  ) {
    Column {
      SectionHeader(
        icon = Icons.Outlined.Tune,
        title = stringResource(R.string.settings_advanced_sampling_section),
      )
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = stringResource(R.string.settings_default_seed_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = vm.defaultSeedEntry.current,
        onValueChange = { value -> vm.defaultSeedEntry.update(value.filter { it.isDigit() }) },
        placeholder = { Text(stringResource(R.string.settings_default_seed_placeholder)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = vm.hasError(DEFAULT_SEED.key),
        colors = olliteTextFieldColors(isError = vm.hasError(DEFAULT_SEED.key)),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = buildAnnotatedString {
          append(stringResource(R.string.settings_default_seed_desc_prefix))
          append(" ")
          withLink(
            link = LinkAnnotation.Url(
              url = LITERT_LM_SAMPLER_ISSUE_URL,
              styles = TextLinkStyles(
                style = SpanStyle(
                  color = MaterialTheme.customColors.linkColor,
                  textDecoration = TextDecoration.Underline,
                ),
              ),
              linkInteractionListener = { uriHandler.openUri(LITERT_LM_SAMPLER_ISSUE_URL) },
            ),
          ) { append(stringResource(R.string.settings_default_seed_issue_link)) }
          append(stringResource(R.string.settings_default_seed_desc_suffix))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(12.dp))
      SectionHeader(
        icon = Icons.Outlined.Timer,
        title = stringResource(R.string.settings_advanced_timeouts_section),
      )
      Spacer(modifier = Modifier.height(12.dp))

      NumericWithUnitRow(
        def = TIMEOUT_CHAT_COMPLETIONS,
        baseValue = vm.timeoutChatCompletionsEntry.current,
        savedBaseValue = vm.timeoutChatCompletionsEntry.saved,
        onBaseValueChange = { vm.timeoutChatCompletionsEntry.update(it) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError(TIMEOUT_CHAT_COMPLETIONS.key),
        onErrorClear = { vm.clearError(TIMEOUT_CHAT_COMPLETIONS.key) },
      )

      SettingDivider()

      NumericWithUnitRow(
        def = TIMEOUT_RESPONSES,
        baseValue = vm.timeoutResponsesEntry.current,
        savedBaseValue = vm.timeoutResponsesEntry.saved,
        onBaseValueChange = { vm.timeoutResponsesEntry.update(it) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError(TIMEOUT_RESPONSES.key),
        onErrorClear = { vm.clearError(TIMEOUT_RESPONSES.key) },
      )

      SettingDivider()

      NumericWithUnitRow(
        def = TIMEOUT_STREAMING,
        baseValue = vm.timeoutStreamingEntry.current,
        savedBaseValue = vm.timeoutStreamingEntry.saved,
        onBaseValueChange = { vm.timeoutStreamingEntry.update(it) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError(TIMEOUT_STREAMING.key),
        onErrorClear = { vm.clearError(TIMEOUT_STREAMING.key) },
      )

      SettingDivider()

      NumericWithUnitRow(
        def = TIMEOUT_BLOCKING,
        baseValue = vm.timeoutBlockingEntry.current,
        savedBaseValue = vm.timeoutBlockingEntry.saved,
        onBaseValueChange = { vm.timeoutBlockingEntry.update(it) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError(TIMEOUT_BLOCKING.key),
        onErrorClear = { vm.clearError(TIMEOUT_BLOCKING.key) },
      )

      SettingDivider()

      NumericWithUnitRow(
        def = TIMEOUT_WARMUP,
        baseValue = vm.timeoutWarmupEntry.current,
        savedBaseValue = vm.timeoutWarmupEntry.saved,
        onBaseValueChange = { vm.timeoutWarmupEntry.update(it) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError(TIMEOUT_WARMUP.key),
        onErrorClear = { vm.clearError(TIMEOUT_WARMUP.key) },
      )

      SettingDivider()

      NumericWithUnitRow(
        def = TIMEOUT_KEEP_ALIVE_RECHECK,
        baseValue = vm.timeoutKeepAliveRecheckEntry.current,
        savedBaseValue = vm.timeoutKeepAliveRecheckEntry.saved,
        onBaseValueChange = { vm.timeoutKeepAliveRecheckEntry.update(it) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError(TIMEOUT_KEEP_ALIVE_RECHECK.key),
        onErrorClear = { vm.clearError(TIMEOUT_KEEP_ALIVE_RECHECK.key) },
      )

      SettingDivider()

      NumericWithUnitRow(
        def = TIMEOUT_CLEANUP_AWAIT,
        baseValue = vm.timeoutCleanupAwaitEntry.current,
        savedBaseValue = vm.timeoutCleanupAwaitEntry.saved,
        onBaseValueChange = { vm.timeoutCleanupAwaitEntry.update(it) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError(TIMEOUT_CLEANUP_AWAIT.key),
        onErrorClear = { vm.clearError(TIMEOUT_CLEANUP_AWAIT.key) },
      )
    }
  }
}

private const val LITERT_LM_SAMPLER_ISSUE_URL =
  "https://github.com/google-ai-edge/LiteRT-LM/issues/2080"

@Composable
private fun SectionHeader(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = OlliteRTPrimary,
      modifier = Modifier.size(16.dp),
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
  Spacer(modifier = Modifier.height(6.dp))
  HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
