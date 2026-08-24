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

package com.ollitert.llm.server.ui.modelmanager

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.Accelerator
import com.ollitert.llm.server.data.prefs.ConfigKeys
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.prefs.NumberSliderConfig
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.prefs.configSpeculativeDecodingEnabled
import com.ollitert.llm.server.data.prefs.configTemperature
import com.ollitert.llm.server.data.prefs.configThinkingEnabled
import com.ollitert.llm.server.data.prefs.configTopK
import com.ollitert.llm.server.data.prefs.configTopP
import com.ollitert.llm.server.data.prefs.maxTokensInt
import com.ollitert.llm.server.runtime.GpuAvailability
import com.ollitert.llm.server.ui.common.GpuUnavailableDialog
import com.ollitert.llm.server.ui.common.SHEET_MAX_WIDTH
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InferenceSettingsSheet(
  model: Model,
  onDismiss: () -> Unit,
  onApply: (configValues: Map<String, Any>, systemPrompt: String, isReset: Boolean) -> Unit,
  onEditDefaults: (() -> Unit)? = null,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val customPromptsEnabled = remember { ServerPrefs.isCustomPromptsEnabled(context) }
  var advancedExpanded by remember { mutableStateOf(false) }
  var systemPrompt by remember(model.prefsKey) {
    mutableStateOf(ServerPrefs.getSystemPrompt(context, model.prefsKey))
  }

  val configValues = model.configValues

  var temperature by remember {
    mutableFloatStateOf(configValues.configTemperature() ?: 1.0f)
  }
  var maxTokens by remember {
    mutableIntStateOf(configValues.maxTokensInt() ?: 1024)
  }
  var topK by remember {
    mutableIntStateOf(configValues.configTopK() ?: 40)
  }
  var topP by remember {
    mutableFloatStateOf(configValues.configTopP() ?: 0.95f)
  }
  var enableThinking by remember {
    mutableStateOf(configValues.configThinkingEnabled() ?: false)
  }
  var enableSpeculativeDecoding by remember {
    mutableStateOf(configValues.configSpeculativeDecodingEnabled() ?: false)
  }

  val availableAccelerators = model.accelerators.ifEmpty { listOf(Accelerator.GPU) }
  val gpuAccessible = GpuAvailability.isOpenClAccessible
  var selectedAccelerator by remember {
    val current = configValues[ConfigKeys.ACCELERATOR.id]?.toString() ?: ""
    val matched = availableAccelerators.find { it.label.equals(current, ignoreCase = true) }
    val resolved = matched ?: availableAccelerators.first()
    val effective = if (resolved == Accelerator.GPU && !gpuAccessible) {
      availableAccelerators.find { it == Accelerator.CPU } ?: resolved
    } else {
      resolved
    }
    mutableStateOf(effective)
  }
  var showGpuInfoDialog by remember { mutableStateOf(false) }

  val limits = remember(model) {
    fun range(key: com.ollitert.llm.server.data.prefs.ConfigKey): Pair<Float, Float>? {
      val c = model.configs.find { it.key == key }
      return if (c is NumberSliderConfig) c.sliderMin to c.sliderMax else null
    }
    mapOf(
      "temp" to (range(ConfigKeys.TEMPERATURE) ?: (0f to 2f)),
      "maxTokens" to (range(ConfigKeys.MAX_TOKENS) ?: (1f to 4096f)),
      "topK" to (range(ConfigKeys.TOPK) ?: (1f to 100f)),
      "topP" to (range(ConfigKeys.TOPP) ?: (0f to 1f)),
    )
  }
  val tempRange = limits.getValue("temp")
  val maxTokensRange = limits.getValue("maxTokens")
  val topKRange = limits.getValue("topK")
  val topPRange = limits.getValue("topP")

  val defaults = remember(model) {
    model.configs.associate { it.key.id to it.defaultValue }
  }

  var showResetDialog by remember { mutableStateOf(false) }

  var tempError by remember { mutableStateOf(false) }
  var maxTokensError by remember { mutableStateOf(false) }
  var topKError by remember { mutableStateOf(false) }
  var topPError by remember { mutableStateOf(false) }
  var tempForceError by remember { mutableStateOf(false) }
  var maxTokensForceError by remember { mutableStateOf(false) }
  var topKForceError by remember { mutableStateOf(false) }
  var topPForceError by remember { mutableStateOf(false) }
  val outOfRangeMessage = stringResource(R.string.inference_settings_error_out_of_range)

  if (showGpuInfoDialog) {
    GpuUnavailableDialog(onDismiss = { showGpuInfoDialog = false })
  }

  if (showResetDialog) {
    InferenceResetConfirmDialog(
      defaults = defaults,
      configValues = configValues,
      availableAccelerators = availableAccelerators,
      gpuAccessible = gpuAccessible,
      onDismiss = { showResetDialog = false },
      onResetConfirmed = { defTemp, defMaxTokens, defTopK, defTopP, defThinking, defSpecDec, effectiveAcc, newValues ->
        showResetDialog = false
        temperature = defTemp
        maxTokens = defMaxTokens
        topK = defTopK
        topP = defTopP
        enableThinking = defThinking
        enableSpeculativeDecoding = defSpecDec
        selectedAccelerator = effectiveAcc
        systemPrompt = ""
        onApply(newValues, "", true)
      },
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    sheetMaxWidth = SHEET_MAX_WIDTH,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .padding(bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.inference_settings_title),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (onEditDefaults != null) {
            TooltipIconButton(
              icon = Icons.Outlined.Edit,
              tooltip = stringResource(R.string.inference_settings_tooltip_edit_defaults),
              onClick = { onEditDefaults() },
            )
          }
          TooltipIconButton(
            icon = Icons.Outlined.RestartAlt,
            tooltip = stringResource(R.string.inference_settings_tooltip_reset),
            onClick = { showResetDialog = true },
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Temperature & Max Tokens row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ParameterInputBox(
          label = stringResource(R.string.inference_settings_label_temperature),
          value = String.format(Locale.US, "%.2f", temperature).trimEnd('0').trimEnd('.'),
          onValueChange = { temperature = it.toFloat(); tempForceError = false },
          min = tempRange.first,
          max = tempRange.second,
          isFloat = true,
          keyboardType = KeyboardType.Decimal,
          modifier = Modifier.weight(1f),
          forceError = tempForceError,
          onErrorStateChange = { tempError = it },
        )
        ParameterInputBox(
          label = stringResource(R.string.inference_settings_label_max_tokens),
          value = maxTokens.toString(),
          onValueChange = { maxTokens = it.toInt(); maxTokensForceError = false },
          min = maxTokensRange.first,
          max = maxTokensRange.second,
          isFloat = false,
          keyboardType = KeyboardType.Number,
          modifier = Modifier.weight(1f),
          forceError = maxTokensForceError,
          onErrorStateChange = { maxTokensError = it },
        )
      }

      // Top-K & Top-P row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ParameterInputBox(
          label = stringResource(R.string.inference_settings_label_top_k),
          value = topK.toString(),
          onValueChange = { topK = it.toInt(); topKForceError = false },
          min = topKRange.first,
          max = topKRange.second,
          isFloat = false,
          keyboardType = KeyboardType.Number,
          modifier = Modifier.weight(1f),
          forceError = topKForceError,
          onErrorStateChange = { topKError = it },
        )
        ParameterInputBox(
          label = stringResource(R.string.inference_settings_label_top_p),
          value = String.format(Locale.US, "%.2f", topP),
          onValueChange = { topP = it.toFloat(); topPForceError = false },
          min = topPRange.first,
          max = topPRange.second,
          isFloat = true,
          keyboardType = KeyboardType.Decimal,
          modifier = Modifier.weight(1f),
          forceError = topPForceError,
          onErrorStateChange = { topPError = it },
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Capability toggles
      ThinkingToggleCard(
        model = model,
        enableThinking = enableThinking,
        onCheckedChange = { enableThinking = it },
      )

      SpeculativeDecodingToggleCard(
        model = model,
        enableSpeculativeDecoding = enableSpeculativeDecoding,
        onCheckedChange = { enableSpeculativeDecoding = it },
      )

      AcceleratorSelectionCard(
        availableAccelerators = availableAccelerators,
        selectedAccelerator = selectedAccelerator,
        gpuAccessible = gpuAccessible,
        onSelect = { selectedAccelerator = it },
        onShowGpuInfo = { showGpuInfoDialog = true },
      )

      // Advanced section — custom system prompt
      if (customPromptsEnabled) {
        Spacer(modifier = Modifier.height(4.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { advancedExpanded = !advancedExpanded }
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            Icons.Outlined.Terminal,
            contentDescription = null,
            tint = OlliteRTPrimary,
            modifier = Modifier.size(24.dp),
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.inference_settings_custom_system_prompt),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.inference_settings_system_prompt_description),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Icon(
            if (advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = if (advancedExpanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
          )
        }

        AnimatedVisibility(
          visible = advancedExpanded,
          enter = expandVertically(),
          exit = shrinkVertically(),
        ) {
          Column(modifier = Modifier.padding(top = 12.dp)) {
            PromptTextArea(
              label = stringResource(R.string.inference_settings_label_system_prompt),
              hint = stringResource(R.string.inference_settings_system_prompt_description),
              value = systemPrompt,
              onValueChange = { systemPrompt = it },
              placeholder = stringResource(R.string.inference_settings_system_prompt_placeholder),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = stringResource(R.string.inference_settings_reload_warning),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
      )

      // Apply button
      Button(
        onClick = {
          tempForceError = temperature < tempRange.first || temperature > tempRange.second
          maxTokensForceError = maxTokens < maxTokensRange.first.toInt() || maxTokens > maxTokensRange.second.toInt()
          topKForceError = topK < topKRange.first.toInt() || topK > topKRange.second.toInt()
          topPForceError = topP < topPRange.first || topP > topPRange.second
          val hasValidationError = tempForceError || maxTokensForceError || topKForceError || topPForceError
            || tempError || maxTokensError || topKError || topPError
          if (hasValidationError) {
            Toast.makeText(context, outOfRangeMessage, Toast.LENGTH_SHORT).show()
            return@Button
          }
          focusManager.clearFocus()
          val newValues = mutableMapOf<String, Any>()
          newValues.putAll(configValues)
          newValues[ConfigKeys.TEMPERATURE.id] = temperature
          newValues[ConfigKeys.MAX_TOKENS.id] = maxTokens
          newValues[ConfigKeys.TOPK.id] = topK
          newValues[ConfigKeys.TOPP.id] = topP
          newValues[ConfigKeys.ENABLE_THINKING.id] = enableThinking
          newValues[ConfigKeys.ENABLE_SPECULATIVE_DECODING.id] = enableSpeculativeDecoding
          newValues[ConfigKeys.ACCELERATOR.id] = selectedAccelerator.label
          onApply(newValues, systemPrompt, false)
        },
        modifier = Modifier
          .fillMaxWidth()
          .defaultMinSize(minHeight = 52.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = OlliteRTPrimary),
      ) {
        Text(
          text = stringResource(R.string.inference_settings_save_apply),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
