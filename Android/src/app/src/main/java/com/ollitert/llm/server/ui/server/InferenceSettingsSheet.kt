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

package com.ollitert.llm.server.ui.server

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.Accelerator
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.NumberSliderConfig
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.ui.common.SHEET_MAX_WIDTH
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InferenceSettingsSheet(
  model: Model,
  onDismiss: () -> Unit,
  onApply: (configValues: Map<String, Any>, systemPrompt: String) -> Unit,
  /** Called when the user taps the edit-defaults pencil button (imported models only). */
  onEditDefaults: (() -> Unit)? = null,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val configValues = model.configValues
  val focusManager = LocalFocusManager.current
  val context = LocalContext.current

  val customPromptsEnabled = remember { LlmHttpPrefs.isCustomPromptsEnabled(context) }

  var systemPrompt by remember {
    mutableStateOf(LlmHttpPrefs.getSystemPrompt(context, model.name))
  }
  var advancedExpanded by remember { mutableStateOf(false) }

  var temperature by remember {
    mutableFloatStateOf(configValues[ConfigKeys.TEMPERATURE.label].toFloatSafe() ?: 1.0f)
  }
  var maxTokens by remember {
    mutableIntStateOf(configValues[ConfigKeys.MAX_TOKENS.label].toIntSafe() ?: 1024)
  }
  var topK by remember {
    mutableIntStateOf(configValues[ConfigKeys.TOPK.label].toIntSafe() ?: 40)
  }
  var topP by remember {
    mutableFloatStateOf(configValues[ConfigKeys.TOPP.label].toFloatSafe() ?: 0.95f)
  }
  var enableThinking by remember {
    mutableStateOf(
      (configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean) ?: false
    )
  }
  // NPU/TPU availability is driven entirely by the model allowlist — there is no runtime API in
  // LiteRT LM SDK (0.10.0) to detect whether the device actually has an NPU/TPU. If a model's
  // allowlist entry includes "npu", we show it here; otherwise it stays hidden.
  val availableAccelerators = model.accelerators.ifEmpty { listOf(Accelerator.GPU) }
  var selectedAccelerator by remember {
    val current = configValues[ConfigKeys.ACCELERATOR.label]?.toString() ?: ""
    val matched = availableAccelerators.find { it.label.equals(current, ignoreCase = true) }
    mutableStateOf(matched ?: availableAccelerators.first())
  }

  // Extract per-model min/max limits from NumberSliderConfig objects
  val limits = remember(model) {
    fun range(key: com.ollitert.llm.server.data.ConfigKey): Pair<Float, Float>? {
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

  // Build default values map from model's config definitions
  val defaults = remember(model) {
    model.configs.associate { it.key.label to it.defaultValue }
  }

  var showResetDialog by remember { mutableStateOf(false) }

  // Reset confirmation dialog
  val modelSettingsResetText = stringResource(R.string.toast_model_settings_reset)
  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text(stringResource(R.string.dialog_reset_inference_title)) },
      text = { Text(stringResource(R.string.dialog_reset_inference_body)) },
      confirmButton = {
        Button(onClick = {
          showResetDialog = false
          temperature = defaults[ConfigKeys.TEMPERATURE.label].toFloatSafe() ?: 1.0f
          maxTokens = defaults[ConfigKeys.MAX_TOKENS.label].toIntSafe() ?: 1024
          topK = defaults[ConfigKeys.TOPK.label].toIntSafe() ?: 40
          topP = defaults[ConfigKeys.TOPP.label].toFloatSafe() ?: 0.95f
          enableThinking = (defaults[ConfigKeys.ENABLE_THINKING.label] as? Boolean) ?: false
          val defaultAcc = defaults[ConfigKeys.ACCELERATOR.label]?.toString() ?: ""
          selectedAccelerator = availableAccelerators.find { it.label.equals(defaultAcc, ignoreCase = true) }
            ?: availableAccelerators.first()
          systemPrompt = ""
          Toast.makeText(context, modelSettingsResetText, Toast.LENGTH_SHORT).show()
        }) {
          Text(stringResource(R.string.button_reset))
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetDialog = false }) {
          Text(stringResource(R.string.cancel))
        }
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
          value = "%.1f".format(temperature),
          onValueChange = { temperature = it.toFloat() },
          min = tempRange.first,
          max = tempRange.second,
          isFloat = true,
          keyboardType = KeyboardType.Decimal,
          modifier = Modifier.weight(1f),
        )
        ParameterInputBox(
          label = stringResource(R.string.inference_settings_label_max_tokens),
          value = maxTokens.toString(),
          onValueChange = { maxTokens = it.toInt() },
          min = maxTokensRange.first,
          max = maxTokensRange.second,
          isFloat = false,
          keyboardType = KeyboardType.Number,
          modifier = Modifier.weight(1f),
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
          onValueChange = { topK = it.toInt() },
          min = topKRange.first,
          max = topKRange.second,
          isFloat = false,
          keyboardType = KeyboardType.Number,
          modifier = Modifier.weight(1f),
        )
        ParameterInputBox(
          label = stringResource(R.string.inference_settings_label_top_p),
          value = "%.2f".format(topP),
          onValueChange = { topP = it.toFloat() },
          min = topPRange.first,
          max = topPRange.second,
          isFloat = true,
          keyboardType = KeyboardType.Decimal,
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Enable Thinking toggle in a container
      val supportsThinking = model.llmSupportThinking
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(
            if (supportsThinking) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
          )
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Outlined.Psychology,
          contentDescription = null,
          tint = if (supportsThinking) OlliteRTPrimary else MaterialTheme.colorScheme.outline,
          modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.inference_settings_allow_thinking),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (supportsThinking) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
          )
          Text(
            text = if (supportsThinking) stringResource(R.string.inference_settings_thinking_supported)
                   else stringResource(R.string.inference_settings_thinking_unsupported),
            style = MaterialTheme.typography.bodySmall,
            color = if (supportsThinking) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
          )
        }
        Switch(
          checked = enableThinking && supportsThinking,
          onCheckedChange = { enableThinking = it },
          enabled = supportsThinking,
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }

      // Accelerator toggle in a container
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
          .padding(horizontal = 16.dp, vertical = 14.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.inference_settings_accelerator),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          AcceleratorToggle(
            options = availableAccelerators,
            selected = selectedAccelerator,
            onSelect = { selectedAccelerator = it },
          )
        }
        if (availableAccelerators.size == 1) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = stringResource(
              R.string.inference_settings_accelerator_only,
              availableAccelerators.first().label,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Advanced section — custom system prompt (gated by Settings toggle)
      if (customPromptsEnabled) {
        Spacer(modifier = Modifier.height(4.dp))

        // Collapsible header
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
          Column(
            modifier = Modifier.padding(top = 12.dp),
          ) {
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
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
      )

      // Apply button
      Button(
        onClick = {
          focusManager.clearFocus()
          // Clamp all values to valid ranges before saving — can't rely on blur/commitValue
          // completing synchronously before this handler reads the state vars.
          val clampedTemp = temperature.coerceIn(tempRange.first, tempRange.second)
          val clampedMaxTokens = maxTokens.coerceIn(maxTokensRange.first.toInt(), maxTokensRange.second.toInt())
          val clampedTopK = topK.coerceIn(topKRange.first.toInt(), topKRange.second.toInt())
          val clampedTopP = topP.coerceIn(topPRange.first, topPRange.second)
          temperature = clampedTemp
          maxTokens = clampedMaxTokens
          topK = clampedTopK
          topP = clampedTopP
          val newValues = mutableMapOf<String, Any>()
          newValues.putAll(configValues)
          newValues[ConfigKeys.TEMPERATURE.label] = clampedTemp
          newValues[ConfigKeys.MAX_TOKENS.label] = clampedMaxTokens
          newValues[ConfigKeys.TOPK.label] = clampedTopK
          newValues[ConfigKeys.TOPP.label] = clampedTopP
          newValues[ConfigKeys.ENABLE_THINKING.label] = enableThinking
          newValues[ConfigKeys.ACCELERATOR.label] = selectedAccelerator.label
          onApply(newValues, systemPrompt)
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

@Composable
private fun ParameterInputBox(
  label: String,
  value: String,
  onValueChange: (Number) -> Unit,
  min: Float,
  max: Float,
  isFloat: Boolean,
  keyboardType: KeyboardType,
  modifier: Modifier = Modifier,
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  var textValue by remember { mutableStateOf(value) }
  // Track focus state to avoid resetting the text field while the user is typing.
  // When focused, the text field owns its content. When not focused, sync from parent
  // (e.g. after reset to defaults).
  var isFocused by remember { mutableStateOf(false) }
  if (!isFocused && textValue != value) {
    textValue = value
  }

  // Clamp and commit the current text value — called on blur and keyboard Done.
  // Clamping only happens here, NOT during typing, so the user can freely type
  // without seeing numbers snap to min/max mid-keystroke.
  fun commitValue() {
    val raw = textValue
    if (isFloat) {
      val parsed = raw.toFloatOrNull() ?: return
      val clamped = parsed.coerceIn(min, max)
      val formatted = if (clamped == clamped.toInt().toFloat()) clamped.toInt().toString()
        else "%.2f".format(clamped).trimEnd('0').trimEnd('.')
      textValue = formatted
      onValueChange(clamped)
    } else {
      val parsed = raw.toLongOrNull() ?: return
      val clamped = parsed.coerceIn(min.toLong(), max.toLong()).toInt()
      textValue = clamped.toString()
      onValueChange(clamped)
    }
  }

  val hint = if (isFloat) {
    "${if (min == min.toInt().toFloat()) min.toInt() else min}–${if (max == max.toInt().toFloat()) max.toInt() else max}"
  } else {
    "${min.toInt()}–${max.toInt()}"
  }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // Label + range hint — FlowRow wraps the hint below the label at large font scaling
    // instead of overlapping or truncating.
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = OlliteRTPrimary,
        letterSpacing = 1.sp,
      )
      Text(
        text = hint,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .clickable { focusRequester.requestFocus() }
        .padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BasicTextField(
        value = textValue,
        onValueChange = { raw ->
          // Strip non-numeric characters (keep digits, dot, comma, minus).
          // Normalize comma to dot (some locales use comma as decimal separator).
          // No clamping here — let the user type freely. Clamping happens on commit (blur/done).
          val allowed = if (isFloat) {
            val normalized = raw.replace(',', '.')
            val filtered = normalized.filter { it.isDigit() || it == '.' || it == '-' }
            // Enforce single decimal point — keep only the first dot
            val dotIndex = filtered.indexOf('.')
            if (dotIndex >= 0) {
              filtered.substring(0, dotIndex + 1) + filtered.substring(dotIndex + 1).replace(".", "")
            } else filtered
          } else {
            raw.filter { it.isDigit() }
          }
          textValue = allowed
          // Push unclamped value to parent so state stays in sync for intermediate display,
          // but only if it's a valid number (incomplete inputs like "" or "." are ignored)
          if (isFloat) {
            allowed.toFloatOrNull()?.let { onValueChange(it) }
          } else {
            allowed.toIntOrNull()?.let { onValueChange(it) }
          }
        },
        singleLine = true,
        textStyle = TextStyle(
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          fontFamily = SpaceGroteskFontFamily,
        ),
        cursorBrush = SolidColor(OlliteRTPrimary),
        keyboardOptions = KeyboardOptions(
          keyboardType = keyboardType,
          imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
          onDone = {
            commitValue()
            focusManager.clearFocus()
          },
        ),
        modifier = Modifier
          .weight(1f)
          .focusRequester(focusRequester)
          .onFocusChanged { state ->
            isFocused = state.isFocused
            if (!state.isFocused) commitValue()
          },
      )
      Icon(
        Icons.Outlined.Edit,
        contentDescription = stringResource(R.string.cd_edit_field, label),
        tint = OlliteRTPrimary,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
private fun AcceleratorToggle(
  options: List<Accelerator>,
  selected: Accelerator,
  onSelect: (Accelerator) -> Unit,
) {
  val segmentWidth = 70.dp
  val toggleWidth = segmentWidth * options.size
  val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
  val singleOption = options.size == 1

  val offsetX by animateDpAsState(
    targetValue = segmentWidth * selectedIndex,
    animationSpec = tween(200),
    label = "toggle_offset",
  )

  Box(
    modifier = Modifier
      .width(toggleWidth)
      .height(36.dp)
      .clip(RoundedCornerShape(50))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest),
  ) {
    // Sliding indicator
    Box(
      modifier = Modifier
        .offset(x = offsetX)
        .width(segmentWidth)
        .height(36.dp)
        .clip(RoundedCornerShape(50))
        .background(OlliteRTPrimary),
    )

    // Labels
    Row(modifier = Modifier.matchParentSize()) {
      options.forEach { accelerator ->
        val isSelected = accelerator == selected
        Box(
          modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .then(
              if (singleOption) Modifier
              else Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
              ) { onSelect(accelerator) }
            ),
          contentAlignment = Alignment.Center,
        ) {
          val textColor by animateColorAsState(
            targetValue = if (isSelected) Color.Black
              else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(200),
            label = "accel_text_${accelerator.label}",
          )
          Text(
            text = accelerator.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor,
          )
        }
      }
    }
  }
}

@Composable
private fun PromptTextArea(
  label: String,
  hint: String,
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = OlliteRTPrimary,
      letterSpacing = 1.sp,
    )
    Text(
      text = hint,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      textStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        fontFamily = SpaceGroteskFontFamily,
      ),
      cursorBrush = SolidColor(OlliteRTPrimary),
      modifier = Modifier
        .fillMaxWidth()
        .height(100.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(12.dp),
      decorationBox = { innerTextField ->
        Box {
          if (value.isEmpty()) {
            Text(
              text = placeholder,
              style = TextStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontFamily = SpaceGroteskFontFamily,
              ),
            )
          }
          innerTextField()
          // Clear button inside the text box — no container background to blend in
          if (value.isNotEmpty()) {
            IconButton(
              onClick = { onValueChange("") },
              modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp),
            ) {
              Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      },
    )
  }
}

/** Safely extract an Int from a config value that may be Number or String. */
private fun Any?.toIntSafe(): Int? = when (this) {
  is Number -> toInt()
  is String -> toIntOrNull()
  else -> null
}

/** Safely extract a Float from a config value that may be Number or String. */
private fun Any?.toFloatSafe(): Float? = when (this) {
  is Number -> toFloat()
  is String -> toFloatOrNull()
  else -> null
}
