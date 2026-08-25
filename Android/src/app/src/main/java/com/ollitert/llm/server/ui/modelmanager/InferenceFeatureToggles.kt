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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.Accelerator
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.llmSupportSpeculativeDecoding
import com.ollitert.llm.server.data.model.llmSupportThinking
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
internal fun ThinkingToggleCard(
  model: Model,
  enableThinking: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val supportsThinking = model.llmSupportThinking
  Row(
    modifier = modifier
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
      onCheckedChange = onCheckedChange,
      enabled = supportsThinking,
      colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
    )
  }
}

// Slider travel is logarithmic over MIN..MAX: equal thumb movement feels like an
// equal *ratio* change (128 → 250 → 500 → 1k …), so no dead zone at the top end.
// Raw positions round to 2 significant digits for readable values; the text box
// still accepts exact numbers.
private const val BUDGET_SLIDER_MIN = 128f

/** Slider position (0..1) → budget value, rounded to 2 significant digits. */
internal fun thinkingBudgetFromPosition(position: Float, min: Int, max: Int): Int {
  val clamped = position.coerceIn(0f, 1f)
  val lo = min.coerceAtLeast(1)
  val hi = max.coerceAtLeast(lo + 1)
  // Endpoints stay exact — 2-sig-digit rounding would otherwise shift them (128→130).
  if (clamped == 0f) return lo
  if (clamped == 1f) return hi
  val ratio = hi.toDouble() / lo
  val raw = lo * Math.pow(ratio, clamped.toDouble())
  val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)) - 1.0)
  val rounded = (Math.round(raw / magnitude) * magnitude).toInt()
  return rounded.coerceIn(lo, hi)
}

/** Budget value → slider position (0..1); inverse of [thinkingBudgetFromPosition]. */
internal fun thinkingBudgetToPosition(value: Float, min: Int, max: Int): Float {
  val lo = min.coerceAtLeast(1)
  val hi = max.coerceAtLeast(lo + 1)
  val clamped = value.coerceIn(lo.toFloat(), hi.toFloat())
  return (Math.log(clamped.toDouble() / lo) / Math.log(hi.toDouble() / lo)).toFloat().coerceIn(0f, 1f)
}

@Composable
internal fun ThinkingBudgetCard(
  budgetTokens: Int,
  maxTokens: Int,
  onValueChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val sliderMin = BUDGET_SLIDER_MIN.toInt()
  val sliderMax = maxTokens.coerceAtLeast(sliderMin + 1)
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(horizontal = 16.dp, vertical = 14.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        Icons.Outlined.Psychology,
        contentDescription = null,
        tint = OlliteRTPrimary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(10.dp))
      Text(
        text = stringResource(R.string.config_label_thinking_budget),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.weight(1f),
      )
      Text(
        text = stringResource(R.string.inference_settings_thinking_budget_tokens, budgetTokens),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Slider(
      value = thinkingBudgetToPosition(budgetTokens.toFloat(), sliderMin, sliderMax),
      onValueChange = { onValueChange(thinkingBudgetFromPosition(it, sliderMin, sliderMax)) },
      valueRange = 0f..1f,
      colors = SliderDefaults.colors(thumbColor = OlliteRTPrimary, activeTrackColor = OlliteRTPrimary),
    )
    ParameterInputBox(
      label = stringResource(R.string.inference_settings_thinking_budget_hint),
      value = budgetTokens.toString(),
      onValueChange = { onValueChange(it.toInt()) },
      min = sliderMin.toFloat(),
      max = sliderMax.toFloat(),
      isFloat = false,
      keyboardType = KeyboardType.Number,
      containerColor = MaterialTheme.colorScheme.surface,
      showBorder = true,
    )
  }
}

@Composable
internal fun SpeculativeDecodingToggleCard(
  model: Model,
  enableSpeculativeDecoding: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val supportsSpecDec = model.llmSupportSpeculativeDecoding
  if (!supportsSpecDec) return

  val specDecEnabled = !model.updatable
  val specDecConfig = model.configs.find { it.key == com.ollitert.llm.server.data.prefs.ConfigKeys.ENABLE_SPECULATIVE_DECODING }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(
        if (specDecEnabled) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
      )
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Outlined.Bolt,
      contentDescription = null,
      tint = if (specDecEnabled) OlliteRTPrimary else MaterialTheme.colorScheme.outline,
      modifier = Modifier.size(24.dp),
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = stringResource(R.string.inference_settings_spec_dec_label),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = if (specDecEnabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
      )
      val subtitle = specDecConfig?.subtitle
      Text(
        text = subtitle ?: stringResource(R.string.inference_settings_spec_dec_supported),
        style = MaterialTheme.typography.bodySmall,
        color = if (!specDecEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else if (subtitle != null) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(
      checked = enableSpeculativeDecoding && specDecEnabled,
      onCheckedChange = onCheckedChange,
      enabled = specDecEnabled,
      colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
    )
  }
}

@Composable
internal fun AcceleratorSelectionCard(
  availableAccelerators: List<Accelerator>,
  selectedAccelerator: Accelerator,
  gpuAccessible: Boolean,
  onSelect: (Accelerator) -> Unit,
  onShowGpuInfo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
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
        onSelect = { accelerator ->
          if (accelerator == Accelerator.GPU && !gpuAccessible) return@AcceleratorToggle
          onSelect(accelerator)
        },
        disabledOptions = if (!gpuAccessible) setOf(Accelerator.GPU) else emptySet(),
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
    if (!gpuAccessible && availableAccelerators.contains(Accelerator.GPU)) {
      Spacer(modifier = Modifier.height(8.dp))
      val captionText = buildAnnotatedString {
        append(stringResource(R.string.gpu_unavailable_caption))
        append(" ")
        withLink(
          link = LinkAnnotation.Clickable(
            tag = "learn_more",
            styles = TextLinkStyles(
              style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
              ),
            ),
            linkInteractionListener = { onShowGpuInfo() },
          ),
        ) {
          append(stringResource(R.string.gpu_unavailable_learn_more))
        }
      }
      Text(
        text = captionText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
