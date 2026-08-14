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

package com.ollitert.llm.server.data

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private const val TAG = "OlliteRT.PrefsJson"

internal fun encodeInferenceConfig(configValues: Map<String, Any>): String = buildJsonObject {
  for ((key, value) in configValues) {
    when (value) {
      is Boolean -> put(key, JsonPrimitive(value))
      is Int -> put(key, JsonPrimitive(value))
      is Long -> put(key, JsonPrimitive(value))
      is Float -> put(key, JsonPrimitive(value.toDouble()))
      is Double -> put(key, JsonPrimitive(value))
      is String -> put(key, JsonPrimitive(value))
      else -> put(key, JsonPrimitive(value.toString()))
    }
  }
}.toString()

private val LABEL_TO_ID_MIGRATION: Map<String, String> = mapOf(
  "Max tokens" to "max_tokens",
  "TopK" to "topk",
  "TopP" to "topp",
  "Temperature" to "temperature",
  "Default max tokens" to "default_max_tokens",
  "Default TopK" to "default_topk",
  "Default TopP" to "default_topp",
  "Default temperature" to "default_temperature",
  "Support image" to "support_image",
  "Support audio" to "support_audio",
  "Support thinking" to "support_thinking",
  "Enable thinking" to "enable_thinking",
  "Accelerator" to "accelerator",
  "Vision accelerator" to "vision_accelerator",
  "Compatible accelerators" to "compatible_accelerators",
  "Name" to "name",
  "Model type" to "model_type",
  "Prefill tokens" to "prefill_tokens",
  "Decode tokens" to "decode_tokens",
  "Number of runs" to "number_of_runs",
)

internal fun migrateConfigKeys(config: Map<String, Any>): Map<String, Any> {
  var needsMigration = false
  for (key in config.keys) {
    if (key in LABEL_TO_ID_MIGRATION) { needsMigration = true; break }
  }
  if (!needsMigration) return config
  val result = mutableMapOf<String, Any>()
  for ((key, value) in config) {
    result[LABEL_TO_ID_MIGRATION[key] ?: key] = value
  }
  return result
}

internal fun decodeInferenceConfig(jsonStr: String?): Map<String, Any>? {
  if (jsonStr == null) return null
  return try {
    val json = Json.parseToJsonElement(jsonStr).jsonObject
    val result = mutableMapOf<String, Any>()
    for ((key, element) in json) {
      val prim = element.jsonPrimitive
      result[key] = when {
        prim.isString -> prim.content
        prim.booleanOrNull != null -> prim.boolean
        prim.content.contains('.') || prim.content.contains('e', ignoreCase = true) ->
          prim.double
        else -> {
          val longVal = prim.long
          if (longVal in Int.MIN_VALUE..Int.MAX_VALUE) longVal.toInt() else longVal
        }
      }
    }
    migrateConfigKeys(result)
  } catch (e: Exception) {
    Log.w(TAG, "decodeInferenceConfig: malformed JSON, falling back to null", e)
    null
  }
}
