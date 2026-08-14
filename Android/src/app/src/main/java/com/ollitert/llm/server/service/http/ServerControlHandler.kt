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

package com.ollitert.llm.server.service.http

import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.service.inference.*

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.EventCategory
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.configTemperature
import com.ollitert.llm.server.data.configThinkingEnabled
import com.ollitert.llm.server.data.configTopK
import com.ollitert.llm.server.data.configTopP
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.data.maxTokensInt
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val TAG = "OlliteRT.ServerControl"

/**
 * Handles REST API requests for server control, lifecycle, and runtime configuration
 * (/v1/server/stop, /v1/server/reload, /v1/server/thinking, /v1/server/config).
 */
class ServerControlHandler(
  private val serviceContext: Context,
  private val modelLifecycle: ModelLifecycle,
  private val inferenceLock: Any,
) {

  private val behaviorToggles = listOf(
    BooleanToggle("auto_truncate_history", "Auto Truncate History",
      ServerPrefs::isAutoTruncateHistory, ServerPrefs::setAutoTruncateHistory),
    BooleanToggle("auto_trim_prompts", "Auto Trim Prompts",
      ServerPrefs::isAutoTrimPrompts, ServerPrefs::setAutoTrimPrompts),
    BooleanToggle("warmup_enabled", "Warmup",
      ServerPrefs::isWarmupEnabled, ServerPrefs::setWarmupEnabled),
    BooleanToggle("keep_alive_enabled", "Keep Alive",
      ServerPrefs::isKeepAliveEnabled, ServerPrefs::setKeepAliveEnabled,
      onChanged = { v -> if (v) modelLifecycle.resetKeepAliveTimer() else modelLifecycle.cancelKeepAliveTimer() }),
    BooleanToggle("custom_prompts_enabled", "Custom Prompts",
      ServerPrefs::isCustomPromptsEnabled, ServerPrefs::setCustomPromptsEnabled),
  )

  /**
   * Handles POST /v1/server/stop — triggers graceful shutdown via the service Stop action.
   */
  fun handleServerStop(): HttpResponse {
    val stopIntent = Intent(serviceContext, ServerService::class.java).apply {
      action = ServerService.ACTION_STOP
    }
    return try {
      serviceContext.startService(stopIntent)
      httpOkJson("""{"success":true,"message":"Server stopping"}""")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to send stop intent", e)
      httpInternalError("Failed to stop server")
    }
  }

  /**
   * Handles POST /v1/server/reload — triggers a model reload.
   */
  fun handleServerReload(defaultModel: Model?, keepAliveUnloadedModelName: String?): HttpResponse {
    val modelName = defaultModel?.name ?: keepAliveUnloadedModelName
      ?: return httpBadRequest("No model loaded")
    val reloadPort = ServerMetrics.port.value
    ServerService.reload(serviceContext, reloadPort, modelName)
    val result = buildJsonObject {
      put("success", true)
      put("message", "Model reloading")
      put("model", modelName)
    }
    return httpOkJson(result.toString())
  }

  private data class ModelContext(
    val model: Model?,
    val isIdle: Boolean,
    val modelName: String,
    val modelPrefsKey: String,
  )

  private fun resolveModelContext(defaultModel: Model?, keepAliveUnloadedModelName: String?): ModelContext? {
    val model = defaultModel
    val isIdle = ServerMetrics.isIdleUnloaded.value
    val modelName = model?.name ?: keepAliveUnloadedModelName ?: return null
    val modelPrefsKey = model?.prefsKey ?: modelLifecycle.keepAliveUnloadedModelPrefsKey ?: return null
    return ModelContext(model, isIdle, modelName, modelPrefsKey)
  }

  /**
   * Handles POST /v1/server/thinking — toggle thinking mode on/off.
   */
  fun handleServerThinking(body: String, defaultModel: Model?, keepAliveUnloadedModelName: String?): HttpResponse {
    val ctx = resolveModelContext(defaultModel, keepAliveUnloadedModelName) ?: return httpBadRequest("No model loaded")
    val (model, isIdle, modelName, modelPrefsKey) = ctx
    if (model != null && !model.llmSupportThinking) {
      return httpBadRequest("Model does not support thinking")
    }
    val currentState: Boolean
    val requestedState: Boolean
    val updatedConfig: Map<String, Any>
    if (model != null) {
      synchronized(inferenceLock) {
        val config = model.configValues
        currentState = config.configThinkingEnabled() ?: false
        requestedState = parseThinkingRequestedState(body, currentState)
          ?: return httpBadRequest("Invalid JSON in request body")
        updatedConfig = config + (ConfigKeys.ENABLE_THINKING.id to requestedState)
        model.configValues = updatedConfig
      }
    } else {
      val config = ServerPrefs.getInferenceConfig(serviceContext, modelPrefsKey)
      currentState = config?.configThinkingEnabled() ?: false
      requestedState = parseThinkingRequestedState(body, currentState)
        ?: return httpBadRequest("Invalid JSON in request body")
      updatedConfig = (config ?: emptyMap()) + (ConfigKeys.ENABLE_THINKING.id to requestedState)
    }
    ServerPrefs.setInferenceConfig(serviceContext, modelPrefsKey, updatedConfig)
    ServerMetrics.setThinkingEnabled(requestedState)
    val oldLabel = if (currentState) "enabled" else "disabled"
    val newLabel = if (requestedState) "enabled" else "disabled"
    RequestLogStore.addEvent(
      "Config via REST API (1 change)",
      modelName = modelName,
      category = EventCategory.SETTINGS,
      body = "Thinking: $oldLabel → $newLabel",
    )
    val result = buildJsonObject {
      put("success", true)
      put("thinking_enabled", requestedState)
      put("model", modelName)
      put("model_loaded", !isIdle)
      if (isIdle) put("warning", "Model is idle-unloaded; thinking support cannot be verified until reload")
    }
    return httpOkJson(result.toString())
  }

  /**
   * Handles POST /v1/server/config — update inference settings.
   */
  fun handleServerConfig(body: String, defaultModel: Model?, keepAliveUnloadedModelName: String?): HttpResponse {
    val ctx = resolveModelContext(defaultModel, keepAliveUnloadedModelName) ?: return httpBadRequest("No model loaded")
    val (model, isIdle, modelName, modelPrefsKey) = ctx
    if (body.isBlank()) {
      val currentConfig = if (model != null) {
        synchronized(inferenceLock) { model.configValues }
      } else {
        ServerPrefs.getInferenceConfig(serviceContext, modelPrefsKey) ?: emptyMap()
      }
      return httpOkJson(
        PayloadBuilders.serverConfig(currentConfig, modelName, !isIdle, modelPrefsKey, serviceContext),
      )
    }
    val obj = try {
      Json.parseToJsonElement(body).jsonObject
    } catch (e: Exception) {
      return httpBadRequest("Invalid JSON body: ${e.message?.take(200) ?: "parse error"}")
    }
    return try {
      val reqTemperature = parseConfigDouble(obj, "temperature")
      val reqMaxTokens = parseConfigInt(obj, "max_tokens")
      val reqTopK = parseConfigInt(obj, "top_k")
      val reqTopP = parseConfigDouble(obj, "top_p")
      val reqThinking = parseConfigBool(obj, "thinking_enabled")
      val updated: Map<String, Any>
      val configChanges: MutableList<String>
      if (model != null) {
        synchronized(inferenceLock) {
          val result = mergeInferenceConfig(
            model.configValues, model, reqTemperature, reqMaxTokens, reqTopK, reqTopP, reqThinking,
          )
          updated = result.first
          configChanges = result.second
          if (configChanges.isNotEmpty()) model.configValues = updated
        }
      } else {
        val base = ServerPrefs.getInferenceConfig(serviceContext, modelPrefsKey) ?: emptyMap()
        val result = mergeInferenceConfig(
          base, null, reqTemperature, reqMaxTokens, reqTopK, reqTopP, reqThinking,
        )
        updated = result.first
        configChanges = result.second
      }
      val changes = configChanges.toMutableList()
      applyBehaviorToggles(obj, changes)
      val specialFieldError = applySpecialFields(obj, modelPrefsKey, changes)
      if (specialFieldError != null) return specialFieldError
      if (changes.isEmpty()) {
        httpBadRequest("No recognized config fields")
      } else {
        ServerPrefs.setInferenceConfig(serviceContext, modelPrefsKey, updated)
        RequestLogStore.addEvent(
          "Config via REST API (${changes.size} ${if (changes.size == 1) "change" else "changes"})",
          modelName = modelName,
          category = EventCategory.SETTINGS,
          body = changes.joinToString("\n"),
        )
        httpOkJson(
          PayloadBuilders.serverConfig(updated, modelName, !isIdle, modelPrefsKey, serviceContext, success = true),
        )
      }
    } catch (e: ConfigFieldException) {
      httpBadRequest(e.message ?: "Invalid config field '${e.fieldName}'")
    } catch (e: Exception) {
      httpBadRequest("Invalid request body: ${e.message?.take(200) ?: "unknown error"}")
    }
  }

  private fun mergeInferenceConfig(
    currentConfig: Map<String, Any>,
    model: Model?,
    reqTemperature: Double?,
    reqMaxTokens: Int?,
    reqTopK: Int?,
    reqTopP: Double?,
    reqThinking: Boolean?,
  ): Pair<Map<String, Any>, MutableList<String>> {
    val updated = currentConfig.toMutableMap()
    val changes = mutableListOf<String>()
    reqTemperature?.let { raw ->
      val old = currentConfig.configTemperature()
      val v = clampTemperature(raw)
      updated[ConfigKeys.TEMPERATURE.id] = v
      changes.add("Temperature: ${old ?: "unset"} → $v")
    }
    reqMaxTokens?.let { raw ->
      val old = currentConfig.maxTokensInt()
      val v = clampMaxTokens(raw)
      updated[ConfigKeys.MAX_TOKENS.id] = v
      changes.add("Max Tokens: ${old ?: "unset"} → $v")
    }
    reqTopK?.let { raw ->
      val old = currentConfig.configTopK()
      val v = clampTopK(raw)
      updated[ConfigKeys.TOPK.id] = v
      changes.add("Top-K: ${old ?: "unset"} → $v")
    }
    reqTopP?.let { raw ->
      val old = currentConfig.configTopP()
      val v = clampTopP(raw)
      updated[ConfigKeys.TOPP.id] = v
      changes.add("Top-P: ${old ?: "unset"} → $v")
    }
    reqThinking?.let { v ->
      if (model == null || model.llmSupportThinking) {
        val old = currentConfig.configThinkingEnabled() ?: false
        updated[ConfigKeys.ENABLE_THINKING.id] = v
        ServerMetrics.setThinkingEnabled(v)
        changes.add("Thinking: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
    }
    return updated.toMap() to changes
  }

  private fun parseThinkingRequestedState(body: String, currentState: Boolean): Boolean? {
    if (body.isNotBlank()) {
      val obj = try {
        Json.parseToJsonElement(body).jsonObject
      } catch (_: Exception) {
        return null
      }
      return obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: !currentState
    }
    return !currentState
  }

  private fun applyBehaviorToggles(obj: JsonObject, changes: MutableList<String>) {
    for (toggle in behaviorToggles) {
      parseConfigBool(obj, toggle.jsonKey)?.let { v ->
        val old = toggle.read(serviceContext)
        toggle.write(serviceContext, v)
        toggle.onChanged?.invoke(v)
        changes.add("${toggle.displayName}: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
    }
  }

  private fun applySpecialFields(
    obj: JsonObject,
    modelPrefsKey: String,
    changes: MutableList<String>,
  ): HttpResponse? {
    parseConfigInt(obj, "keep_alive_minutes")?.let { v ->
      if (v < 1 || v > 7200) {
        return httpBadRequest("keep_alive_minutes out of range (1–7200)")
      }
      val old = ServerPrefs.getKeepAliveMinutes(serviceContext)
      ServerPrefs.setKeepAliveMinutes(serviceContext, v)
      if (ServerPrefs.isKeepAliveEnabled(serviceContext)) modelLifecycle.resetKeepAliveTimer()
      changes.add("Keep Alive Minutes: $old → $v")
    }
    parseConfigString(obj, "system_prompt")?.let { v ->
      val old = ServerPrefs.getSystemPrompt(serviceContext, modelPrefsKey)
      ServerPrefs.setSystemPrompt(serviceContext, modelPrefsKey, v)
      val oldDisplay = if (old.isBlank()) "(empty)" else "\"${old.take(40)}${if (old.length > 40) "…" else ""}\""
      val newDisplay = if (v.isBlank()) "(empty)" else "\"${v.take(40)}${if (v.length > 40) "…" else ""}\""
      changes.add("System Prompt: $oldDisplay → $newDisplay")
    }
    return null
  }
}

private sealed interface BehaviorSetting {
  val jsonKey: String
  val displayName: String
}

private class BooleanToggle(
  override val jsonKey: String,
  override val displayName: String,
  val read: (Context) -> Boolean,
  val write: (Context, Boolean) -> Unit,
  val onChanged: ((Boolean) -> Unit)? = null,
) : BehaviorSetting

class ConfigFieldException(
  val fieldName: String,
  expectedType: String,
  cause: Throwable? = null,
) : IllegalArgumentException("Invalid value for '$fieldName': expected $expectedType", cause)

fun parseConfigDouble(obj: JsonObject, field: String): Double? {
  if (!obj.containsKey(field)) return null
  return try {
    obj.getValue(field).jsonPrimitive.double
  } catch (e: Exception) {
    throw ConfigFieldException(field, "number", e)
  }
}

fun parseConfigInt(obj: JsonObject, field: String): Int? {
  if (!obj.containsKey(field)) return null
  return try {
    obj.getValue(field).jsonPrimitive.int
  } catch (e: Exception) {
    throw ConfigFieldException(field, "integer", e)
  }
}

fun parseConfigBool(obj: JsonObject, field: String): Boolean? {
  if (!obj.containsKey(field)) return null
  return try {
    obj.getValue(field).jsonPrimitive.boolean
  } catch (e: Exception) {
    throw ConfigFieldException(field, "boolean", e)
  }
}

fun parseConfigString(obj: JsonObject, field: String): String? {
  if (!obj.containsKey(field)) return null
  return try {
    obj.getValue(field).jsonPrimitive.content
  } catch (e: Exception) {
    throw ConfigFieldException(field, "string", e)
  }
}

fun readBytesWithLimit(source: kotlinx.io.Source, maxBytes: Long): ByteArray {
  val buffer = kotlinx.io.Buffer()
  var totalRead = 0L
  while (true) {
    val chunk = source.readAtMostTo(buffer, minOf(8192L, maxBytes + 1 - totalRead))
    if (chunk == -1L) break
    totalRead += chunk
    if (totalRead > maxBytes) {
      throw java.io.IOException("File exceeds $maxBytes byte limit")
    }
  }
  return buffer.readByteArray()
}
