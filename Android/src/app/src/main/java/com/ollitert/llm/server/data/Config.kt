/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.data

import kotlin.math.abs

/** The data types of configuration values. */
enum class ValueType {
  INT,
  FLOAT,
  STRING,
  BOOLEAN,
}

// TODO: Extract ConfigKey labels to string resources for localization.
data class ConfigKey(val id: String, val label: String)

object ConfigKeys {
  val MAX_TOKENS = ConfigKey("max_tokens", "Max tokens")
  val TOPK = ConfigKey("topk", "TopK")
  val TOPP = ConfigKey("topp", "TopP")
  val TEMPERATURE = ConfigKey("temperature", "Temperature")
  val DEFAULT_MAX_TOKENS = ConfigKey("default_max_tokens", "Default max tokens")
  val DEFAULT_TOPK = ConfigKey("default_topk", "Default TopK")
  val DEFAULT_TOPP = ConfigKey("default_topp", "Default TopP")
  val DEFAULT_TEMPERATURE = ConfigKey("default_temperature", "Default temperature")
  val SUPPORT_IMAGE = ConfigKey("support_image", "Support image")
  val SUPPORT_AUDIO = ConfigKey("support_audio", "Support audio")
  val SUPPORT_THINKING = ConfigKey("support_thinking", "Support thinking")
  val ENABLE_THINKING = ConfigKey("enable_thinking", "Enable thinking")
  val ACCELERATOR = ConfigKey("accelerator", "Accelerator")
  val VISION_ACCELERATOR = ConfigKey("vision_accelerator", "Vision accelerator")
  val COMPATIBLE_ACCELERATORS = ConfigKey("compatible_accelerators", "Compatible accelerators")
  val NAME = ConfigKey("name", "Name")
  val MODEL_TYPE = ConfigKey("model_type", "Model type")
  val PREFILL_TOKENS = ConfigKey("prefill_tokens", "Prefill tokens")
  val DECODE_TOKENS = ConfigKey("decode_tokens", "Decode tokens")
  val NUMBER_OF_RUNS = ConfigKey("number_of_runs", "Number of runs")
}

/**
 * Base class for configuration settings.
 *
 * @param key The unique key for the configuration setting.
 * @param defaultValue The default value for the configuration setting.
 * @param valueType The data type of the configuration value.
 * @param needReinitialization Indicates whether the model needs to be reinitialized after changing
 *   this config.
 */
open class Config(
  open val key: ConfigKey,
  open val defaultValue: Any,
  open val valueType: ValueType,
  // Changes on any configs with this field set to true will automatically trigger a model
  // re-initialization.
  open val needReinitialization: Boolean = true,
)

/** Configuration setting for a label. */
class LabelConfig(override val key: ConfigKey, override val defaultValue: String = "") :
  Config(
    key = key,
    defaultValue = defaultValue,
    valueType = ValueType.STRING,
  )

/** Configuration setting for an editable text field. */
class EditableTextConfig(
  override val key: ConfigKey,
  override val defaultValue: String = "",
  /** Optional read-only suffix displayed after the text field (e.g. file extension). */
  val suffix: String = "",
  override val needReinitialization: Boolean = true,
) :
  Config(
    key = key,
    defaultValue = defaultValue,
    valueType = ValueType.STRING,
  )

/**
 * Configuration setting for a number slider.
 *
 * @param sliderMin The minimum value of the slider.
 * @param sliderMax The maximum value of the slider.
 */
class NumberSliderConfig(
  override val key: ConfigKey,
  val sliderMin: Float,
  val sliderMax: Float,
  override val defaultValue: Float,
  override val valueType: ValueType,
  override val needReinitialization: Boolean = true,
) :
  Config(
    key = key,
    defaultValue = defaultValue,
    valueType = valueType,
  )

/** Configuration setting for a boolean switch. */
class BooleanSwitchConfig(
  override val key: ConfigKey,
  override val defaultValue: Boolean,
  override val needReinitialization: Boolean = true,
) :
  Config(
    key = key,
    defaultValue = defaultValue,
    valueType = ValueType.BOOLEAN,
  )

/** Configuration setting for a segmented button. */
class SegmentedButtonConfig(
  override val key: ConfigKey,
  override val defaultValue: String,
  val options: List<String>,
  val allowMultiple: Boolean = false,
  val description: String? = null,
) :
  Config(
    key = key,
    defaultValue = defaultValue,
    // The emitted value will be comma-separated labels when allowMultiple=true.
    valueType = ValueType.STRING,
  )

fun convertValueToTargetType(value: Any, valueType: ValueType): Any {
  return when (valueType) {
    ValueType.INT ->
      when (value) {
        is Int -> value
        is Float -> value.toInt()
        is Double -> value.toInt()
        is String -> value.toIntOrNull() ?: 0
        is Boolean -> if (value) 1 else 0
        else -> 0
      }

    ValueType.FLOAT ->
      when (value) {
        is Int -> value.toFloat()
        is Float -> value
        is Double -> value.toFloat()
        is String -> value.toFloatOrNull() ?: 0f
        is Boolean -> if (value) 1f else 0f
        else -> 0f
      }


    ValueType.BOOLEAN ->
      when (value) {
        is Int -> value != 0
        is Boolean -> value
        is Float -> abs(value) > 1e-6  // avoid floating-point rounding treating 0.0f as true
        is Double -> abs(value) > 1e-6
        is String -> value.isNotEmpty()
        else -> false
      }

    ValueType.STRING -> value.toString()
  }
}

fun preferredAcceleratorOrder(acc: Accelerator): Int = when (acc) {
  Accelerator.NPU, Accelerator.TPU -> 0
  Accelerator.GPU -> 1
  Accelerator.CPU -> 2
}

fun createLlmChatConfigs(
  defaultMaxToken: Int = DEFAULT_MAX_TOKEN,
  defaultMaxContextLength: Int? = null,
  defaultTopK: Int = DEFAULT_TOPK,
  defaultTopP: Float = DEFAULT_TOPP,
  defaultTemperature: Float = DEFAULT_TEMPERATURE,
  accelerators: List<Accelerator> = DEFAULT_ACCELERATORS,
  supportThinking: Boolean = false,
): List<Config> {
  var maxTokensConfig: Config =
    LabelConfig(key = ConfigKeys.MAX_TOKENS, defaultValue = "$defaultMaxToken")
  if (defaultMaxContextLength != null) {
    maxTokensConfig =
      NumberSliderConfig(
        key = ConfigKeys.MAX_TOKENS,
        sliderMin = MIN_MAX_TOKENS.toFloat(),
        sliderMax = defaultMaxContextLength.toFloat(),
        defaultValue = defaultMaxToken.toFloat(),
        valueType = ValueType.INT,
      )
  }
  val configs =
    listOf(
        maxTokensConfig,
        NumberSliderConfig(
          key = ConfigKeys.TOPK,
          sliderMin = MIN_TOPK.toFloat(),
          sliderMax = MAX_TOPK.toFloat(),
          defaultValue = defaultTopK.toFloat(),
          valueType = ValueType.INT,
          needReinitialization = false, // Applied per-conversation via resetConversation()
        ),
        NumberSliderConfig(
          key = ConfigKeys.TOPP,
          sliderMin = MIN_TOPP,
          sliderMax = MAX_TOPP,
          defaultValue = defaultTopP,
          valueType = ValueType.FLOAT,
          needReinitialization = false, // Applied per-conversation via resetConversation()
        ),
        NumberSliderConfig(
          key = ConfigKeys.TEMPERATURE,
          sliderMin = MIN_TEMPERATURE,
          sliderMax = MAX_TEMPERATURE,
          defaultValue = defaultTemperature,
          valueType = ValueType.FLOAT,
          needReinitialization = false, // Applied per-conversation via resetConversation()
        ),
        SegmentedButtonConfig(
          key = ConfigKeys.ACCELERATOR,
          defaultValue = accelerators.sortedBy { preferredAcceleratorOrder(it) }.first().label,
          options = accelerators.sortedBy { preferredAcceleratorOrder(it) }.map { it.label },
        ),
      )
      .toMutableList()

  if (supportThinking) {
    configs.add(BooleanSwitchConfig(key = ConfigKeys.ENABLE_THINKING, defaultValue = false, needReinitialization = false)) // Read at request time, not during Engine init
  }
  return configs
}

/**
 * Creates the configuration settings for an LLM model that only supports NPU.
 *
 * For now NPU models don't support setting topK, topP, and temperature.
 */
fun createLlmChatConfigsForNpuModel(
  defaultMaxToken: Int = DEFAULT_MAX_TOKEN,
  accelerators: List<Accelerator> = DEFAULT_ACCELERATORS,
): List<Config> {
  return listOf(
    LabelConfig(key = ConfigKeys.MAX_TOKENS, defaultValue = "$defaultMaxToken"),
    SegmentedButtonConfig(
      key = ConfigKeys.ACCELERATOR,
      defaultValue = accelerators.sortedBy { preferredAcceleratorOrder(it) }.first().label,
      options = accelerators.sortedBy { preferredAcceleratorOrder(it) }.map { it.label },
    ),
  )
}
