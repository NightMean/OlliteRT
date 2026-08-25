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

package com.ollitert.llm.server.data.prefs
import com.ollitert.llm.server.data.model.Model

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.BuildConfig
import androidx.core.content.edit

private const val PREFS_NAME = "llm_http_prefs"

// ═══════════════════════════════════════════════════════════════════════════
// § Server Config — port, CORS, bearer token
// ═══════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════
// § Model Config — default model, inference config, system prompts, recommendations
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_DEFAULT_MODEL_NAME = "default_model_name"
private const val KEY_PREFIX_SYSTEM_PROMPT = "system_prompt_"
private const val KEY_PREFIX_INFERENCE_CONFIG = "inference_config_"
private const val KEY_SHOW_MODEL_RECOMMENDATIONS = "show_model_recommendations"
private const val KEY_WARMUP_ENABLED = "warmup_enabled"
private const val KEY_EAGER_VISION_INIT = "eager_vision_init"
private const val KEY_CUSTOM_PROMPTS_ENABLED = "custom_prompts_enabled"
private const val KEY_AUTO_TRUNCATE_HISTORY = "auto_truncate_history"
private const val KEY_AUTO_TRIM_PROMPTS = "auto_trim_prompts"
private const val KEY_KEEP_PARTIAL_RESPONSE = "keep_partial_response"
private const val KEY_SCHEMA_INJECTION_TOOL_CALLING = "schema_injection_tool_calling"

// ═══════════════════════════════════════════════════════════════════════════
// § UI Preferences — keep screen on, log display, stream preview, metrics
// ═══════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════
// § Keep Alive — auto-unload model after idle timeout to free RAM
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
private const val KEY_KEEP_ALIVE_MINUTES = "keep_alive_minutes"
private const val DEFAULT_KEEP_ALIVE_ENABLED = false
private const val DEFAULT_KEEP_ALIVE_MINUTES = 5

// ═══════════════════════════════════════════════════════════════════════════
// § Boot & Lifecycle — auto start on boot, clear logs on stop
// ═══════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════
// § Model Update Detection — allowlist version, ignored updates
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_ALLOWLIST_CONTENT_VERSION = "allowlist_content_version"
private const val KEY_IGNORED_MODEL_UPDATES = "ignored_model_updates"

// ═══════════════════════════════════════════════════════════════════════════
// § DataStore Corruption Recovery
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_CORRUPTED_DATASTORES = "corrupted_datastores"

private const val TAG = "OlliteRT.Prefs"

  // ── Typed pref accessors ──────────────────────────────────────────────
internal sealed class Pref<T>(val key: String, val default: T) {
  abstract fun read(prefs: android.content.SharedPreferences): T
  abstract fun write(editor: android.content.SharedPreferences.Editor, value: T): android.content.SharedPreferences.Editor
  }

internal class BoolPref(key: String, default: Boolean) : Pref<Boolean>(key, default) {
  override fun read(prefs: android.content.SharedPreferences) = prefs.getBoolean(key, default)
  override fun write(editor: android.content.SharedPreferences.Editor, value: Boolean) = editor.putBoolean(key, value)
  }

internal class IntPref(key: String, default: Int) : Pref<Int>(key, default) {
  override fun read(prefs: android.content.SharedPreferences) = prefs.getInt(key, default)
  override fun write(editor: android.content.SharedPreferences.Editor, value: Int) = editor.putInt(key, value)
  }

internal class LongPref(key: String, default: Long) : Pref<Long>(key, default) {
  override fun read(prefs: android.content.SharedPreferences) = prefs.getLong(key, default)
  override fun write(editor: android.content.SharedPreferences.Editor, value: Long) = editor.putLong(key, value)
  }

internal class StringPref(key: String, default: String) : Pref<String>(key, default) {
  override fun read(prefs: android.content.SharedPreferences) = prefs.getString(key, default) ?: default
  override fun write(editor: android.content.SharedPreferences.Editor, value: String) = editor.putString(key, value)
  }

object ServerPrefs {

  /**
   * Cached SharedPreferences instance. Android's Context.getSharedPreferences() does its own
   * internal caching, but it still requires a synchronized map lookup + string hash on every call.
   * Caching here avoids ~59 redundant lookups per settings-read cycle, and more importantly avoids
   * the disk I/O on the very first call from any thread (Android loads the XML file synchronously
   * on first access to a given prefs name).
   */
  @Volatile private var cachedPrefs: android.content.SharedPreferences? = null

  internal fun prefs(context: Context): android.content.SharedPreferences =
    cachedPrefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { cachedPrefs = it }


  internal fun <T> get(context: Context, pref: Pref<T>): T = pref.read(prefs(context))

  internal fun <T> set(context: Context, pref: Pref<T>, value: T) {
    prefs(context).edit { pref.write(this, value) }
  }

  // ── Pref declarations (grouped by concern) ─────────────────────────────

  // Model Config
  private val WARMUP_ENABLED = BoolPref(KEY_WARMUP_ENABLED, true)
  private val EAGER_VISION_INIT = BoolPref(KEY_EAGER_VISION_INIT, false)
  private val CUSTOM_PROMPTS_ENABLED = BoolPref(KEY_CUSTOM_PROMPTS_ENABLED, false)
  private val AUTO_TRUNCATE_HISTORY = BoolPref(KEY_AUTO_TRUNCATE_HISTORY, false)
  private val AUTO_TRIM_PROMPTS = BoolPref(KEY_AUTO_TRIM_PROMPTS, false)
  private val KEEP_PARTIAL_RESPONSE = BoolPref(KEY_KEEP_PARTIAL_RESPONSE, false)
  private val SCHEMA_INJECTION_TOOL_CALLING = BoolPref(KEY_SCHEMA_INJECTION_TOOL_CALLING, true)
  private val SHOW_MODEL_RECOMMENDATIONS = BoolPref(KEY_SHOW_MODEL_RECOMMENDATIONS, true)

  // UI Preferences

  // Log Persistence

  // Keep Alive
  private val KEEP_ALIVE_ENABLED = BoolPref(KEY_KEEP_ALIVE_ENABLED, DEFAULT_KEEP_ALIVE_ENABLED)
  private val KEEP_ALIVE_MINUTES = IntPref(KEY_KEEP_ALIVE_MINUTES, DEFAULT_KEEP_ALIVE_MINUTES)

  // Boot & Lifecycle

  // Developer / Debug

  // Request Queueing

  // Home Assistant / STT

  // GPU Availability

  // Update Check

  // Engagement Prompt

  // Model Update Detection
  private val ALLOWLIST_CONTENT_VERSION = IntPref(KEY_ALLOWLIST_CONTENT_VERSION, 0)

  // ══════════════════════════════════════════════════════════════════════════
  // § Server Network Config
  // ══════════════════════════════════════════════════════════════════════════

  fun getPort(context: Context): Int = ServerPrefsNetwork.getPort(prefs(context))

  fun save(context: Context, port: Int) {
    ServerPrefsNetwork.savePort(prefs(context), port)
  }

  fun getServerBindConfig(context: Context): ServerBindConfig =
    ServerPrefsNetwork.getServerBindConfig(prefs(context))

  /** Writes both listener fields in one editor transaction so startup never sees a mixed config. */
  fun setServerBindConfig(context: Context, config: ServerBindConfig) {
    ServerPrefsNetwork.setServerBindConfig(prefs(context), config)
  }

  fun getClientIpPolicyConfig(context: Context): ClientIpPolicyConfig =
    ServerPrefsNetwork.getClientIpPolicyConfig(prefs(context))

  /** Writes the policy and its rules atomically before the running server receives the compiled policy. */
  fun setClientIpPolicyConfig(context: Context, config: ClientIpPolicyConfig) {
    ServerPrefsNetwork.setClientIpPolicyConfig(prefs(context), config)
  }

  fun getBearerToken(context: Context): String =
    ServerPrefsNetwork.getBearerToken(prefs(context))

  fun setBearerToken(context: Context, token: String) {
    ServerPrefsNetwork.setBearerToken(prefs(context), token)
  }

  fun getHfToken(context: Context): String =
    ServerPrefsNetwork.getHfToken(prefs(context))

  fun setHfToken(context: Context, token: String) {
    ServerPrefsNetwork.setHfToken(prefs(context), token)
  }

  fun getCorsAllowedOrigins(context: Context): String =
    ServerPrefsNetwork.getCorsAllowedOrigins(prefs(context))

  fun setCorsAllowedOrigins(context: Context, origins: String) {
    ServerPrefsNetwork.setCorsAllowedOrigins(prefs(context), origins)
  }

  // ══════════════════════════════════════════════════════════════════════════
  // § Model Config
  // ══════════════════════════════════════════════════════════════════════════

  fun getDefaultModelName(context: Context): String? =
    prefs(context)
      .getString(KEY_DEFAULT_MODEL_NAME, null)

  fun setDefaultModelName(context: Context, modelName: String?) {
    prefs(context).edit {
      if (modelName != null) putString(KEY_DEFAULT_MODEL_NAME, modelName)
      else remove(KEY_DEFAULT_MODEL_NAME)
    }
  }

  fun isWarmupEnabled(context: Context): Boolean = get(context, WARMUP_ENABLED)
  fun setWarmupEnabled(context: Context, enabled: Boolean) = set(context, WARMUP_ENABLED, enabled)

  fun isEagerVisionInit(context: Context): Boolean = get(context, EAGER_VISION_INIT)
  fun setEagerVisionInit(context: Context, enabled: Boolean) = set(context, EAGER_VISION_INIT, enabled)

  fun isCustomPromptsEnabled(context: Context): Boolean = get(context, CUSTOM_PROMPTS_ENABLED)
  fun setCustomPromptsEnabled(context: Context, enabled: Boolean) = set(context, CUSTOM_PROMPTS_ENABLED, enabled)

  fun isAutoTruncateHistory(context: Context): Boolean = get(context, AUTO_TRUNCATE_HISTORY)
  fun setAutoTruncateHistory(context: Context, enabled: Boolean) = set(context, AUTO_TRUNCATE_HISTORY, enabled)

  fun isAutoTrimPrompts(context: Context): Boolean = get(context, AUTO_TRIM_PROMPTS)
  fun setAutoTrimPrompts(context: Context, enabled: Boolean) = set(context, AUTO_TRIM_PROMPTS, enabled)

  fun isKeepPartialResponse(context: Context): Boolean = get(context, KEEP_PARTIAL_RESPONSE)
  fun setKeepPartialResponse(context: Context, enabled: Boolean) = set(context, KEEP_PARTIAL_RESPONSE, enabled)

  fun isSchemaInjectionToolCalling(context: Context): Boolean = get(context, SCHEMA_INJECTION_TOOL_CALLING)
  fun setSchemaInjectionToolCalling(context: Context, enabled: Boolean) = set(context, SCHEMA_INJECTION_TOOL_CALLING, enabled)

  fun isShowModelRecommendations(context: Context): Boolean = get(context, SHOW_MODEL_RECOMMENDATIONS)
  fun setShowModelRecommendations(context: Context, enabled: Boolean) = set(context, SHOW_MODEL_RECOMMENDATIONS, enabled)

  fun getSystemPrompt(context: Context, modelName: String): String =
    prefs(context)
      .getString(KEY_PREFIX_SYSTEM_PROMPT + modelName, "") ?: ""

  fun setSystemPrompt(context: Context, modelName: String, prompt: String) {
    prefs(context).edit { putString(KEY_PREFIX_SYSTEM_PROMPT + modelName, prompt) }
  }

  /**
   * Persist inference config values (temperature, max tokens, etc.) for a specific model.
   * Stored as a JSON string so it survives app restarts. Values are keyed by ConfigKey label.
   */
  fun setInferenceConfig(context: Context, modelName: String, configValues: Map<String, Any>) {
    prefs(context).edit { putString(KEY_PREFIX_INFERENCE_CONFIG + modelName, encodeInferenceConfig(configValues)) }
  }

  /**
   * Load saved inference config values for a model. Returns null if no config was saved.
   * Values are returned as their JSON-native types (Int, Double, Boolean, String).
   */
  fun getInferenceConfig(context: Context, modelName: String): Map<String, Any>? {
    val jsonStr = prefs(context)
      .getString(KEY_PREFIX_INFERENCE_CONFIG + modelName, null) ?: return null
    return decodeInferenceConfig(jsonStr)
  }

  /** Removes any saved inference config overrides for a model, reverting it to defaults. */
  fun clearInferenceConfig(context: Context, modelName: String) {
    prefs(context).edit { remove(KEY_PREFIX_INFERENCE_CONFIG + modelName) }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // § Keep Alive
  // ══════════════════════════════════════════════════════════════════════════

  fun isKeepAliveEnabled(context: Context): Boolean = get(context, KEEP_ALIVE_ENABLED)
  fun setKeepAliveEnabled(context: Context, enabled: Boolean) = set(context, KEEP_ALIVE_ENABLED, enabled)

  fun getKeepAliveMinutes(context: Context): Int = get(context, KEEP_ALIVE_MINUTES)
  fun setKeepAliveMinutes(context: Context, minutes: Int) = set(context, KEEP_ALIVE_MINUTES, minutes.coerceAtLeast(0))

  // ══════════════════════════════════════════════════════════════════════════
  // § Model Update Detection
  // ══════════════════════════════════════════════════════════════════════════

  fun getAllowlistContentVersion(context: Context): Int = get(context, ALLOWLIST_CONTENT_VERSION)
  fun setAllowlistContentVersion(context: Context, version: Int) = set(context, ALLOWLIST_CONTENT_VERSION, version)

  fun getIgnoredModelUpdates(context: Context): Set<String> =
    prefs(context).getStringSet(KEY_IGNORED_MODEL_UPDATES, emptySet()) ?: emptySet()

  fun addIgnoredModelUpdate(context: Context, nameVersion: String) {
    val current = getIgnoredModelUpdates(context).toMutableSet()
    current.add(nameVersion)
    prefs(context).edit { putStringSet(KEY_IGNORED_MODEL_UPDATES, current) }
  }

  fun removeIgnoredModelUpdate(context: Context, nameVersion: String) {
    val current = getIgnoredModelUpdates(context).toMutableSet()
    current.remove(nameVersion)
    prefs(context).edit { putStringSet(KEY_IGNORED_MODEL_UPDATES, current) }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // § DataStore Corruption Recovery
  // ══════════════════════════════════════════════════════════════════════════

  fun getCorruptedDataStores(context: Context): Set<String> =
    prefs(context).getStringSet(KEY_CORRUPTED_DATASTORES, emptySet()) ?: emptySet()

  fun addCorruptedDataStore(context: Context, name: String) {
    val current = getCorruptedDataStores(context).toMutableSet()
    current.add(name)
    prefs(context).edit { putStringSet(KEY_CORRUPTED_DATASTORES, current) }
  }

  fun clearCorruptedDataStores(context: Context) {
    prefs(context).edit { remove(KEY_CORRUPTED_DATASTORES) }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // § Advanced Timeouts
  // ══════════════════════════════════════════════════════════════════════════

  fun getTimeoutChatCompletions(context: Context): Long =
    ServerPrefsTimeouts.getTimeoutChatCompletions(prefs(context))

  fun setTimeoutChatCompletions(context: Context, seconds: Long) {
    ServerPrefsTimeouts.setTimeoutChatCompletions(prefs(context), seconds)
  }

  fun getTimeoutResponses(context: Context): Long =
    ServerPrefsTimeouts.getTimeoutResponses(prefs(context))

  fun setTimeoutResponses(context: Context, seconds: Long) {
    ServerPrefsTimeouts.setTimeoutResponses(prefs(context), seconds)
  }

  fun getTimeoutStreaming(context: Context): Long =
    ServerPrefsTimeouts.getTimeoutStreaming(prefs(context))

  fun setTimeoutStreaming(context: Context, seconds: Long) {
    ServerPrefsTimeouts.setTimeoutStreaming(prefs(context), seconds)
  }

  fun getTimeoutBlocking(context: Context): Long =
    ServerPrefsTimeouts.getTimeoutBlocking(prefs(context))

  fun setTimeoutBlocking(context: Context, seconds: Long) {
    ServerPrefsTimeouts.setTimeoutBlocking(prefs(context), seconds)
  }

  fun getTimeoutWarmup(context: Context): Long =
    ServerPrefsTimeouts.getTimeoutWarmup(prefs(context))

  fun setTimeoutWarmup(context: Context, seconds: Long) {
    ServerPrefsTimeouts.setTimeoutWarmup(prefs(context), seconds)
  }

  fun getTimeoutKeepAliveRecheckSeconds(context: Context): Long =
    ServerPrefsTimeouts.getTimeoutKeepAliveRecheckSeconds(prefs(context))

  fun setTimeoutKeepAliveRecheckSeconds(context: Context, seconds: Long) {
    ServerPrefsTimeouts.setTimeoutKeepAliveRecheckSeconds(prefs(context), seconds)
  }

  fun getTimeoutCleanupAwait(context: Context): Long =
    ServerPrefsTimeouts.getTimeoutCleanupAwait(prefs(context))

  fun setTimeoutCleanupAwait(context: Context, seconds: Long) {
    ServerPrefsTimeouts.setTimeoutCleanupAwait(prefs(context), seconds)
  }

  // ══════════════════════════════════════════════════════════════════════════
  // § Migrations
  // ══════════════════════════════════════════════════════════════════════════

  // TODO: Remove after 1.0.0 — one-time migration introduced in 0.9.0-beta.1 to move
  // per-model prefs from old keys (model.name) to stable keys (model.downloadFileName).
  fun migratePerModelKeys(context: Context, modelNameToDownloadFileName: Map<String, String>) {
    ServerPrefsMigrations.migratePerModelKeys(prefs(context), modelNameToDownloadFileName)
  }

  fun renameModelPrefsKey(context: Context, oldKey: String, newKey: String) {
    ServerPrefsMigrations.renameModelPrefsKey(prefs(context), oldKey, newKey)
  }

  // TODO: Remove after 1.0.0 — one-time migration introduced in 0.9.0 to rename
  // ha_stt_transcription_prompt → stt_transcription_prompt (setting is not HA-specific).
  fun migrateSttKeys(context: Context) {
    ServerPrefsMigrations.migrateSttKeys(prefs(context))
  }

  // ══════════════════════════════════════════════════════════════════════════
  // § Reset & Diagnostics
  // ══════════════════════════════════════════════════════════════════════════

  /**
   * Clear all settings and restore defaults. Wipes the entire SharedPreferences store,
   * including per-model inference configs and system prompts.
   * The cached prefs instance is invalidated so the next access picks up the cleared state.
   */
  fun resetToDefaults(context: Context) {
    prefs(context).edit { clear() }
    cachedPrefs = null
  }

  private val SENSITIVE_KEYS = setOf(KEY_BEARER_TOKEN, KEY_HF_TOKEN, KEY_CLIENT_IP_RULES)
  private val SENSITIVE_PREFIXES = listOf(KEY_PREFIX_SYSTEM_PROMPT, KEY_PREFIX_INFERENCE_CONFIG)

  private fun isSensitiveKey(key: String): Boolean =
    key in SENSITIVE_KEYS || SENSITIVE_PREFIXES.any { key.startsWith(it) }

  fun dumpToLogcat(context: Context) {
    Log.i(TAG, "=== Active Settings Snapshot ===")
    for ((key, value) in prefs(context).all.toSortedMap()) {
      val display = if (isSensitiveKey(key)) {
        if (value.toString().isBlank()) "not set" else "configured (redacted)"
      } else {
        value.toString()
      }
      Log.i(TAG, "$key = $display")
    }
    Log.i(TAG, "================================")
  }

  // ── Per-request snapshot ──────────────────────────────────────────────────

  fun captureRequestSnapshot(context: Context): RequestPrefsSnapshot =
    RequestPrefsSnapshot(
      autoTruncateHistory = isAutoTruncateHistory(context),
      autoTrimPrompts = isAutoTrimPrompts(context),
      ignoreClientSamplerParams = isIgnoreClientSamplerParams(context),
      eagerVisionInit = isEagerVisionInit(context),
      streamLogsPreview = isStreamLogsPreview(context),
      keepPartialResponse = isKeepPartialResponse(context),
      compactImageData = isCompactImageData(context),
      resolveClientHostnames = isResolveClientHostnames(context),
      hideHealthLogs = isHideHealthLogs(context),
      verboseDebug = isVerboseDebugEnabled(context),
      rejectWhenBusy = isRejectWhenBusy(context),
      sttTranscriptionPromptEnabled = isSttTranscriptionPromptEnabled(context),
      sttTranscriptionPromptText = getSttTranscriptionPromptText(context),
      schemaInjectionToolCalling = isSchemaInjectionToolCalling(context),
    )
}
