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

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.isPixel10
import com.ollitert.llm.server.data.Accelerator
import com.ollitert.llm.server.data.Config
import com.ollitert.llm.server.data.ConfigKey
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.DEFAULT_MAX_TOKEN
import com.ollitert.llm.server.data.DEFAULT_TEMPERATURE
import com.ollitert.llm.server.data.DEFAULT_TOPK
import com.ollitert.llm.server.data.DEFAULT_TOPP
import com.ollitert.llm.server.data.EditableTextConfig
import com.ollitert.llm.server.data.LabelConfig
import com.ollitert.llm.server.data.MAX_MAX_TOKENS
import com.ollitert.llm.server.data.MAX_TEMPERATURE
import com.ollitert.llm.server.data.MAX_TOPK
import com.ollitert.llm.server.data.MAX_TOPP
import com.ollitert.llm.server.data.MIN_MAX_TOKENS
import com.ollitert.llm.server.data.MIN_TEMPERATURE
import com.ollitert.llm.server.data.MIN_TOPK
import com.ollitert.llm.server.data.MIN_TOPP
import com.ollitert.llm.server.data.NumberSliderConfig
import com.ollitert.llm.server.data.SegmentedButtonConfig
import com.ollitert.llm.server.data.ValueType
import com.ollitert.llm.server.data.convertValueToTargetType

internal inline fun <reified T> safeConfigValue(
  values: Map<String, Any>,
  key: ConfigKey,
  valueType: ValueType,
  default: T,
): T {
  val raw = values[key.id] ?: return default
  val converted = convertValueToTargetType(raw, valueType)
  return converted as? T ?: default
}

internal val SUPPORTED_ACCELERATORS: List<Accelerator> =
  if (isPixel10()) {
    listOf(Accelerator.CPU, Accelerator.NPU)
  } else {
    listOf(Accelerator.CPU, Accelerator.GPU, Accelerator.NPU)
  }

/**
 * Builds the import config list with the file extension passed to the editable name field,
 * so the extension is shown as a read-only suffix next to the text input.
 */
internal fun buildImportConfigsLlm(context: Context, fileExtension: String): List<Config> =
  listOf(
    EditableTextConfig(key = ConfigKeys.NAME, suffix = fileExtension),
    LabelConfig(key = ConfigKeys.MODEL_TYPE),
    NumberSliderConfig(
      key = ConfigKeys.DEFAULT_MAX_TOKENS,
      sliderMin = MIN_MAX_TOKENS.toFloat(),
      sliderMax = MAX_MAX_TOKENS.toFloat(),
      defaultValue = DEFAULT_MAX_TOKEN.toFloat(),
      valueType = ValueType.INT,
      allowAboveMax = true,
    ),
    NumberSliderConfig(
      key = ConfigKeys.DEFAULT_TOPK,
      sliderMin = MIN_TOPK.toFloat(),
      sliderMax = MAX_TOPK.toFloat(),
      defaultValue = DEFAULT_TOPK.toFloat(),
      valueType = ValueType.INT,
    ),
    NumberSliderConfig(
      key = ConfigKeys.DEFAULT_TOPP,
      sliderMin = MIN_TOPP,
      sliderMax = MAX_TOPP,
      defaultValue = DEFAULT_TOPP,
      valueType = ValueType.FLOAT,
    ),
    NumberSliderConfig(
      key = ConfigKeys.DEFAULT_TEMPERATURE,
      sliderMin = MIN_TEMPERATURE,
      sliderMax = MAX_TEMPERATURE,
      defaultValue = DEFAULT_TEMPERATURE,
      valueType = ValueType.FLOAT,
    ),
    SegmentedButtonConfig(
      key = ConfigKeys.COMPATIBLE_ACCELERATORS,
      defaultValue = SUPPORTED_ACCELERATORS[0].label,
      options = SUPPORTED_ACCELERATORS.map { it.label },
      allowMultiple = true,
      description = context.getString(R.string.import_compatible_accelerators_description),
    ),
  )

@Composable
internal fun CompactToggle(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
