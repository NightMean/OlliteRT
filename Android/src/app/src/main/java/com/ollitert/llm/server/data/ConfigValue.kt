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

sealed class ConfigValue {
  data class IntValue(val value: Int) : ConfigValue()
  data class FloatValue(val value: Float) : ConfigValue()
  data class StringValue(val value: String) : ConfigValue()
}

fun getIntConfigValue(configValue: ConfigValue?, default: Int): Int {
  if (configValue == null) {
    return default
  }
  return when (configValue) {
    is ConfigValue.IntValue -> configValue.value
    is ConfigValue.FloatValue -> configValue.value.toInt()
    is ConfigValue.StringValue -> 0
  }
}

fun getFloatConfigValue(configValue: ConfigValue?, default: Float): Float {
  if (configValue == null) {
    return default
  }
  return when (configValue) {
    is ConfigValue.IntValue -> configValue.value.toFloat()
    is ConfigValue.FloatValue -> configValue.value
    is ConfigValue.StringValue -> 0f
  }
}

fun getStringConfigValue(configValue: ConfigValue?, default: String): String {
  if (configValue == null) {
    return default
  }
  return when (configValue) {
    is ConfigValue.IntValue -> "${configValue.value}"
    is ConfigValue.FloatValue -> "${configValue.value}"
    is ConfigValue.StringValue -> configValue.value
  }
}
