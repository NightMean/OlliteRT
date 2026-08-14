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

private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
private const val KEY_AUTO_EXPAND_LOGS = "auto_expand_logs"
private const val KEY_WRAP_LOG_TEXT = "wrap_log_text"
private const val KEY_STREAM_LOGS_PREVIEW = "stream_logs_preview"
private const val KEY_NOTIF_SHOW_REQUEST_COUNT = "notif_show_request_count"
private const val KEY_SHOW_REQUEST_TYPES = "show_request_types"
private const val KEY_SHOW_ADVANCED_METRICS = "show_advanced_metrics"
private const val KEY_FORCE_STREAM_USAGE = "force_stream_usage"
private const val KEY_COMPACT_IMAGE_DATA = "compact_image_data"
private const val DEFAULT_COMPACT_IMAGE_DATA = true
private const val KEY_RESOLVE_CLIENT_HOSTNAMES = "resolve_client_hostnames"
private const val DEFAULT_RESOLVE_CLIENT_HOSTNAMES = false
private const val KEY_HIDE_HEALTH_LOGS = "hide_health_logs"
private const val DEFAULT_HIDE_HEALTH_LOGS = false

// ═══════════════════════════════════════════════════════════════════════════
// § Log Persistence — enabled, max entries, auto delete
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_LOG_PERSISTENCE_ENABLED = "log_persistence_enabled"
private const val KEY_LOG_MAX_ENTRIES = "log_max_entries"
private const val KEY_LOG_AUTO_DELETE_MINUTES = "log_auto_delete_minutes"
private const val DEFAULT_LOG_PERSISTENCE_ENABLED = false
private const val DEFAULT_LOG_MAX_ENTRIES = 500
private const val DEFAULT_LOG_AUTO_DELETE_MINUTES = 7 * 24 * 60 // 7 days

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

private const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
private const val KEY_CLEAR_LOGS_ON_STOP = "clear_logs_on_stop"
private const val KEY_CONFIRM_CLEAR_LOGS = "confirm_clear_logs"

// ═══════════════════════════════════════════════════════════════════════════
// § Developer / Debug — verbose debug, ignore client sampler params
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_VERBOSE_DEBUG_ENABLED = "verbose_debug_enabled"
private const val KEY_IGNORE_CLIENT_SAMPLER_PARAMS = "ignore_client_sampler_params"

// ═══════════════════════════════════════════════════════════════════════════
// § Request Queueing — reject concurrent requests instead of queueing
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_REJECT_WHEN_BUSY = "reject_when_busy"

// ═══════════════════════════════════════════════════════════════════════════
// § Home Assistant / STT — HA integration, STT transcription prompt
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_HA_INTEGRATION_ENABLED = "ha_integration_enabled"
private const val KEY_STT_TRANSCRIPTION_PROMPT = "stt_transcription_prompt"
private const val DEFAULT_STT_TRANSCRIPTION_PROMPT = true
private const val KEY_STT_TRANSCRIPTION_PROMPT_TEXT = "stt_transcription_prompt_text"
internal const val DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT =
  "Transcribe the audio exactly as spoken. Output only the transcribed text, nothing else."

// ═══════════════════════════════════════════════════════════════════════════
// § Update Check — enabled, interval, cached state, consecutive failures
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_UPDATE_CHECK_ENABLED = "update_check_enabled"
private const val KEY_UPDATE_CHECK_INTERVAL_HOURS = "update_check_interval_hours"
private const val KEY_LAST_DISMISSED_UPDATE_VERSION = "last_dismissed_update_version"
private const val KEY_CACHED_LATEST_VERSION = "cached_latest_version"
private const val KEY_CACHED_RELEASE_HTML_URL = "cached_release_html_url"
private const val KEY_CACHED_RELEASE_ETAG = "cached_release_etag"
private const val KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES = "update_check_consecutive_failures"
private const val KEY_CROSS_CHANNEL_NOTIFY_ENABLED = "cross_channel_notify_enabled"
private const val KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION = "last_dismissed_cross_channel_version"
private const val KEY_CACHED_CROSS_CHANNEL_VERSION = "cached_cross_channel_version"
private const val DEFAULT_UPDATE_CHECK_ENABLED = true
private const val DEFAULT_UPDATE_CHECK_INTERVAL_HOURS = 24

// ═══════════════════════════════════════════════════════════════════════════
// § Engagement Prompt — manual start count, show count, dismissed
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_MANUAL_START_COUNT = "manual_start_count"
private const val KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED = "engagement_prompt_permanently_dismissed"
private const val KEY_ENGAGEMENT_PROMPT_SHOW_COUNT = "engagement_prompt_show_count"
/** Maximum number of times the engagement prompt is shown before being auto-suppressed. */
private const val ENGAGEMENT_PROMPT_MAX_SHOWS = 2
/** Manual start count threshold for showing the engagement prompt the first time. */
private const val ENGAGEMENT_PROMPT_FIRST_THRESHOLD = 3
/** Manual start count threshold for showing the engagement prompt the second time. */
private const val ENGAGEMENT_PROMPT_SECOND_THRESHOLD = 13

// ═══════════════════════════════════════════════════════════════════════════
// § GPU Availability — one-time dialog shown, server start dialog dismissed
// ═══════════════════════════════════════════════════════════════════════════

private const val KEY_GPU_UNAVAILABLE_DIALOG_SHOWN = "gpu_unavailable_dialog_shown"
private const val KEY_GPU_UNAVAILABLE_SERVER_START_DISMISSED = "gpu_unavailable_server_start_dismissed"

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

object ServerPrefs {

  /**
   * Cached SharedPreferences instance. Android's Context.getSharedPreferences() does its own
   * internal caching, but it still requires a synchronized map lookup + string hash on every call.
   * Caching here avoids ~59 redundant lookups per settings-read cycle, and more importantly avoids
   * the disk I/O on the very first call from any thread (Android loads the XML file synchronously
   * on first access to a given prefs name).
   */
  @Volatile private var cachedPrefs: android.content.SharedPreferences? = null

  private fun prefs(context: Context): android.content.SharedPreferences =
    cachedPrefs ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also { cachedPrefs = it }

  // ── Typed pref accessors ──────────────────────────────────────────────
  private sealed class Pref<T>(val key: String, val default: T) {
    abstract fun read(prefs: android.content.SharedPreferences): T
    abstract fun write(editor: android.content.SharedPreferences.Editor, value: T): android.content.SharedPreferences.Editor
  }

  private class BoolPref(key: String, default: Boolean) : Pref<Boolean>(key, default) {
    override fun read(prefs: android.content.SharedPreferences) = prefs.getBoolean(key, default)
    override fun write(editor: android.content.SharedPreferences.Editor, value: Boolean) = editor.putBoolean(key, value)
  }

  private class IntPref(key: String, default: Int) : Pref<Int>(key, default) {
    override fun read(prefs: android.content.SharedPreferences) = prefs.getInt(key, default)
    override fun write(editor: android.content.SharedPreferences.Editor, value: Int) = editor.putInt(key, value)
  }

  private class LongPref(key: String, default: Long) : Pref<Long>(key, default) {
    override fun read(prefs: android.content.SharedPreferences) = prefs.getLong(key, default)
    override fun write(editor: android.content.SharedPreferences.Editor, value: Long) = editor.putLong(key, value)
  }

  private class StringPref(key: String, default: String) : Pref<String>(key, default) {
    override fun read(prefs: android.content.SharedPreferences) = prefs.getString(key, default) ?: default
    override fun write(editor: android.content.SharedPreferences.Editor, value: String) = editor.putString(key, value)
  }

  private fun <T> get(context: Context, pref: Pref<T>): T = pref.read(prefs(context))

  private fun <T> set(context: Context, pref: Pref<T>, value: T) {
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
  private val KEEP_SCREEN_ON = BoolPref(KEY_KEEP_SCREEN_ON, true)
  private val AUTO_EXPAND_LOGS = BoolPref(KEY_AUTO_EXPAND_LOGS, false)
  private val WRAP_LOG_TEXT = BoolPref(KEY_WRAP_LOG_TEXT, true)
  private val STREAM_LOGS_PREVIEW = BoolPref(KEY_STREAM_LOGS_PREVIEW, true)
  private val NOTIF_SHOW_REQUEST_COUNT = BoolPref(KEY_NOTIF_SHOW_REQUEST_COUNT, false)
  private val SHOW_REQUEST_TYPES = BoolPref(KEY_SHOW_REQUEST_TYPES, false)
  private val SHOW_ADVANCED_METRICS = BoolPref(KEY_SHOW_ADVANCED_METRICS, false)
  private val FORCE_STREAM_USAGE = BoolPref(KEY_FORCE_STREAM_USAGE, true)
  private val COMPACT_IMAGE_DATA = BoolPref(KEY_COMPACT_IMAGE_DATA, DEFAULT_COMPACT_IMAGE_DATA)
  private val RESOLVE_CLIENT_HOSTNAMES = BoolPref(KEY_RESOLVE_CLIENT_HOSTNAMES, DEFAULT_RESOLVE_CLIENT_HOSTNAMES)
  private val HIDE_HEALTH_LOGS = BoolPref(KEY_HIDE_HEALTH_LOGS, DEFAULT_HIDE_HEALTH_LOGS)

  // Log Persistence
  private val LOG_PERSISTENCE_ENABLED = BoolPref(KEY_LOG_PERSISTENCE_ENABLED, DEFAULT_LOG_PERSISTENCE_ENABLED)
  private val LOG_MAX_ENTRIES = IntPref(KEY_LOG_MAX_ENTRIES, DEFAULT_LOG_MAX_ENTRIES)
  private val LOG_AUTO_DELETE_MINUTES = LongPref(KEY_LOG_AUTO_DELETE_MINUTES, DEFAULT_LOG_AUTO_DELETE_MINUTES.toLong())

  // Keep Alive
  private val KEEP_ALIVE_ENABLED = BoolPref(KEY_KEEP_ALIVE_ENABLED, DEFAULT_KEEP_ALIVE_ENABLED)
  private val KEEP_ALIVE_MINUTES = IntPref(KEY_KEEP_ALIVE_MINUTES, DEFAULT_KEEP_ALIVE_MINUTES)

  // Boot & Lifecycle
  private val AUTO_START_ON_BOOT = BoolPref(KEY_AUTO_START_ON_BOOT, false)
  private val CLEAR_LOGS_ON_STOP = BoolPref(KEY_CLEAR_LOGS_ON_STOP, false)
  private val CONFIRM_CLEAR_LOGS = BoolPref(KEY_CONFIRM_CLEAR_LOGS, true)

  // Developer / Debug
  private val VERBOSE_DEBUG_ENABLED = BoolPref(KEY_VERBOSE_DEBUG_ENABLED, false)
  private val IGNORE_CLIENT_SAMPLER_PARAMS = BoolPref(KEY_IGNORE_CLIENT_SAMPLER_PARAMS, false)

  // Request Queueing
  private val REJECT_WHEN_BUSY = BoolPref(KEY_REJECT_WHEN_BUSY, false)

  // Home Assistant / STT
  private val HA_INTEGRATION_ENABLED = BoolPref(KEY_HA_INTEGRATION_ENABLED, false)
  private val STT_TRANSCRIPTION_PROMPT = BoolPref(KEY_STT_TRANSCRIPTION_PROMPT, DEFAULT_STT_TRANSCRIPTION_PROMPT)

  // GPU Availability
  private val GPU_UNAVAILABLE_DIALOG_SHOWN = BoolPref(KEY_GPU_UNAVAILABLE_DIALOG_SHOWN, false)
  private val GPU_UNAVAILABLE_SERVER_START_DISMISSED = BoolPref(KEY_GPU_UNAVAILABLE_SERVER_START_DISMISSED, false)

  // Update Check
  private val UPDATE_CHECK_ENABLED = BoolPref(KEY_UPDATE_CHECK_ENABLED, DEFAULT_UPDATE_CHECK_ENABLED)
  private val UPDATE_CHECK_INTERVAL_HOURS = IntPref(KEY_UPDATE_CHECK_INTERVAL_HOURS, DEFAULT_UPDATE_CHECK_INTERVAL_HOURS)
  private val UPDATE_CHECK_CONSECUTIVE_FAILURES = IntPref(KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES, 0)
  private val CROSS_CHANNEL_NOTIFY_ENABLED = BoolPref(KEY_CROSS_CHANNEL_NOTIFY_ENABLED, BuildConfig.UPDATE_CHANNEL != "stable")

  // Engagement Prompt
  private val MANUAL_START_COUNT = IntPref(KEY_MANUAL_START_COUNT, 0)
  private val ENGAGEMENT_PROMPT_SHOW_COUNT = IntPref(KEY_ENGAGEMENT_PROMPT_SHOW_COUNT, 0)

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
  // § UI Preferences
  // ══════════════════════════════════════════════════════════════════════════

  fun isKeepScreenOn(context: Context): Boolean = get(context, KEEP_SCREEN_ON)
  fun setKeepScreenOn(context: Context, enabled: Boolean) = set(context, KEEP_SCREEN_ON, enabled)

  fun isAutoExpandLogs(context: Context): Boolean = get(context, AUTO_EXPAND_LOGS)
  fun setAutoExpandLogs(context: Context, enabled: Boolean) = set(context, AUTO_EXPAND_LOGS, enabled)

  fun isWrapLogText(context: Context): Boolean = get(context, WRAP_LOG_TEXT)
  fun setWrapLogText(context: Context, enabled: Boolean) = set(context, WRAP_LOG_TEXT, enabled)

  fun isStreamLogsPreview(context: Context): Boolean = get(context, STREAM_LOGS_PREVIEW)
  fun setStreamLogsPreview(context: Context, enabled: Boolean) = set(context, STREAM_LOGS_PREVIEW, enabled)

  fun isNotifShowRequestCount(context: Context): Boolean = get(context, NOTIF_SHOW_REQUEST_COUNT)
  fun setNotifShowRequestCount(context: Context, enabled: Boolean) = set(context, NOTIF_SHOW_REQUEST_COUNT, enabled)

  fun isShowRequestTypes(context: Context): Boolean = get(context, SHOW_REQUEST_TYPES)
  fun setShowRequestTypes(context: Context, enabled: Boolean) = set(context, SHOW_REQUEST_TYPES, enabled)

  fun isShowAdvancedMetrics(context: Context): Boolean = get(context, SHOW_ADVANCED_METRICS)
  fun setShowAdvancedMetrics(context: Context, enabled: Boolean) = set(context, SHOW_ADVANCED_METRICS, enabled)

  fun isForceStreamUsage(context: Context): Boolean = get(context, FORCE_STREAM_USAGE)
  fun setForceStreamUsage(context: Context, enabled: Boolean) = set(context, FORCE_STREAM_USAGE, enabled)

  fun isCompactImageData(context: Context): Boolean = get(context, COMPACT_IMAGE_DATA)
  fun setCompactImageData(context: Context, enabled: Boolean) = set(context, COMPACT_IMAGE_DATA, enabled)

  fun isResolveClientHostnames(context: Context): Boolean = get(context, RESOLVE_CLIENT_HOSTNAMES)
  fun setResolveClientHostnames(context: Context, enabled: Boolean) = set(context, RESOLVE_CLIENT_HOSTNAMES, enabled)

  fun isHideHealthLogs(context: Context): Boolean = get(context, HIDE_HEALTH_LOGS)
  fun setHideHealthLogs(context: Context, enabled: Boolean) = set(context, HIDE_HEALTH_LOGS, enabled)

  // ══════════════════════════════════════════════════════════════════════════
  // § Log Persistence
  // ══════════════════════════════════════════════════════════════════════════

  fun isLogPersistenceEnabled(context: Context): Boolean = get(context, LOG_PERSISTENCE_ENABLED)
  fun setLogPersistenceEnabled(context: Context, enabled: Boolean) = set(context, LOG_PERSISTENCE_ENABLED, enabled)

  fun getLogMaxEntries(context: Context): Int = get(context, LOG_MAX_ENTRIES)
  fun setLogMaxEntries(context: Context, maxEntries: Int) = set(context, LOG_MAX_ENTRIES, maxEntries.coerceAtLeast(0))

  fun getLogAutoDeleteMinutes(context: Context): Long = get(context, LOG_AUTO_DELETE_MINUTES)
  fun setLogAutoDeleteMinutes(context: Context, minutes: Long) = set(context, LOG_AUTO_DELETE_MINUTES, minutes.coerceAtLeast(0L))

  // ══════════════════════════════════════════════════════════════════════════
  // § Keep Alive
  // ══════════════════════════════════════════════════════════════════════════

  fun isKeepAliveEnabled(context: Context): Boolean = get(context, KEEP_ALIVE_ENABLED)
  fun setKeepAliveEnabled(context: Context, enabled: Boolean) = set(context, KEEP_ALIVE_ENABLED, enabled)

  fun getKeepAliveMinutes(context: Context): Int = get(context, KEEP_ALIVE_MINUTES)
  fun setKeepAliveMinutes(context: Context, minutes: Int) = set(context, KEEP_ALIVE_MINUTES, minutes.coerceAtLeast(0))

  // ══════════════════════════════════════════════════════════════════════════
  // § Boot & Lifecycle
  // ══════════════════════════════════════════════════════════════════════════

  fun isAutoStartOnBoot(context: Context): Boolean = get(context, AUTO_START_ON_BOOT)
  fun setAutoStartOnBoot(context: Context, enabled: Boolean) = set(context, AUTO_START_ON_BOOT, enabled)

  fun isClearLogsOnStop(context: Context): Boolean = get(context, CLEAR_LOGS_ON_STOP)
  fun setClearLogsOnStop(context: Context, enabled: Boolean) = set(context, CLEAR_LOGS_ON_STOP, enabled)

  fun isConfirmClearLogs(context: Context): Boolean = get(context, CONFIRM_CLEAR_LOGS)
  fun setConfirmClearLogs(context: Context, enabled: Boolean) = set(context, CONFIRM_CLEAR_LOGS, enabled)

  // ══════════════════════════════════════════════════════════════════════════
  // § Developer / Debug
  // ══════════════════════════════════════════════════════════════════════════

  fun isVerboseDebugEnabled(context: Context): Boolean = get(context, VERBOSE_DEBUG_ENABLED)
  fun setVerboseDebugEnabled(context: Context, enabled: Boolean) = set(context, VERBOSE_DEBUG_ENABLED, enabled)

  fun isIgnoreClientSamplerParams(context: Context): Boolean = get(context, IGNORE_CLIENT_SAMPLER_PARAMS)
  fun setIgnoreClientSamplerParams(context: Context, enabled: Boolean) = set(context, IGNORE_CLIENT_SAMPLER_PARAMS, enabled)

  // ══════════════════════════════════════════════════════════════════════════
  // § Request Queueing
  // ══════════════════════════════════════════════════════════════════════════

  fun isRejectWhenBusy(context: Context): Boolean = get(context, REJECT_WHEN_BUSY)
  fun setRejectWhenBusy(context: Context, enabled: Boolean) = set(context, REJECT_WHEN_BUSY, enabled)

  // ══════════════════════════════════════════════════════════════════════════
  // § GPU Availability
  // ══════════════════════════════════════════════════════════════════════════

  fun isGpuUnavailableDialogShown(context: Context): Boolean = get(context, GPU_UNAVAILABLE_DIALOG_SHOWN)
  fun setGpuUnavailableDialogShown(context: Context, shown: Boolean) = set(context, GPU_UNAVAILABLE_DIALOG_SHOWN, shown)

  fun isGpuUnavailableServerStartDismissed(context: Context): Boolean = get(context, GPU_UNAVAILABLE_SERVER_START_DISMISSED)
  fun setGpuUnavailableServerStartDismissed(context: Context, dismissed: Boolean) = set(context, GPU_UNAVAILABLE_SERVER_START_DISMISSED, dismissed)

  // ══════════════════════════════════════════════════════════════════════════
  // § Home Assistant / STT
  // ══════════════════════════════════════════════════════════════════════════

  fun isHaIntegrationEnabled(context: Context): Boolean = get(context, HA_INTEGRATION_ENABLED)
  fun setHaIntegrationEnabled(context: Context, enabled: Boolean) = set(context, HA_INTEGRATION_ENABLED, enabled)

  fun isSttTranscriptionPromptEnabled(context: Context): Boolean = get(context, STT_TRANSCRIPTION_PROMPT)
  fun setSttTranscriptionPromptEnabled(context: Context, enabled: Boolean) = set(context, STT_TRANSCRIPTION_PROMPT, enabled)

  fun getSttTranscriptionPromptText(context: Context): String =
    prefs(context).getString(KEY_STT_TRANSCRIPTION_PROMPT_TEXT, DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT)
      ?: DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT

  fun setSttTranscriptionPromptText(context: Context, text: String) {
    prefs(context).edit { putString(KEY_STT_TRANSCRIPTION_PROMPT_TEXT, text) }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // § Update Check
  // ══════════════════════════════════════════════════════════════════════════

  fun isUpdateCheckEnabled(context: Context): Boolean = get(context, UPDATE_CHECK_ENABLED)
  fun setUpdateCheckEnabled(context: Context, enabled: Boolean) = set(context, UPDATE_CHECK_ENABLED, enabled)

  fun getUpdateCheckIntervalHours(context: Context): Int = get(context, UPDATE_CHECK_INTERVAL_HOURS)
  fun setUpdateCheckIntervalHours(context: Context, hours: Int) = set(context, UPDATE_CHECK_INTERVAL_HOURS, hours.coerceIn(1, 720))

  fun getLastDismissedUpdateVersion(context: Context): String? =
    prefs(context).getString(KEY_LAST_DISMISSED_UPDATE_VERSION, null)

  fun setLastDismissedUpdateVersion(context: Context, version: String?) {
    prefs(context).edit {
      if (version != null) putString(KEY_LAST_DISMISSED_UPDATE_VERSION, version)
      else remove(KEY_LAST_DISMISSED_UPDATE_VERSION)
    }
  }

  fun getCachedLatestVersion(context: Context): String? =
    prefs(context).getString(KEY_CACHED_LATEST_VERSION, null)

  fun getCachedReleaseHtmlUrl(context: Context): String? =
    prefs(context).getString(KEY_CACHED_RELEASE_HTML_URL, null)

  fun getCachedReleaseETag(context: Context): String? =
    prefs(context).getString(KEY_CACHED_RELEASE_ETAG, null)

  fun setCachedUpdateInfo(context: Context, version: String?, htmlUrl: String?, etag: String?) {
    prefs(context).edit {
      if (version != null) putString(KEY_CACHED_LATEST_VERSION, version) else remove(KEY_CACHED_LATEST_VERSION)
      if (htmlUrl != null) putString(KEY_CACHED_RELEASE_HTML_URL, htmlUrl) else remove(KEY_CACHED_RELEASE_HTML_URL)
      if (etag != null) putString(KEY_CACHED_RELEASE_ETAG, etag) else remove(KEY_CACHED_RELEASE_ETAG)
    }
  }

  fun getUpdateCheckConsecutiveFailures(context: Context): Int = get(context, UPDATE_CHECK_CONSECUTIVE_FAILURES)
  fun setUpdateCheckConsecutiveFailures(context: Context, count: Int) = set(context, UPDATE_CHECK_CONSECUTIVE_FAILURES, count)

  /** Clear all cached update state (version, URL, ETag, dismiss). Called after a successful app update. */
  fun clearUpdateState(context: Context) {
    prefs(context).edit {
      remove(KEY_CACHED_LATEST_VERSION)
      remove(KEY_CACHED_RELEASE_HTML_URL)
      remove(KEY_CACHED_RELEASE_ETAG)
      remove(KEY_LAST_DISMISSED_UPDATE_VERSION)
      remove(KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES)
      remove(KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION)
      remove(KEY_CACHED_CROSS_CHANNEL_VERSION)
    }
  }

  fun isCrossChannelNotifyEnabled(context: Context): Boolean = get(context, CROSS_CHANNEL_NOTIFY_ENABLED)
  fun setCrossChannelNotifyEnabled(context: Context, enabled: Boolean) = set(context, CROSS_CHANNEL_NOTIFY_ENABLED, enabled)

  fun getLastDismissedCrossChannelVersion(context: Context): String? =
    prefs(context).getString(KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION, null)

  fun setLastDismissedCrossChannelVersion(context: Context, version: String?) {
    prefs(context).edit {
      if (version != null) putString(KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION, version)
      else remove(KEY_LAST_DISMISSED_CROSS_CHANNEL_VERSION)
    }
  }

  fun getCachedCrossChannelVersion(context: Context): String? =
    prefs(context).getString(KEY_CACHED_CROSS_CHANNEL_VERSION, null)

  fun setCachedCrossChannelVersion(context: Context, version: String?) {
    prefs(context).edit {
      if (version != null) putString(KEY_CACHED_CROSS_CHANNEL_VERSION, version)
      else remove(KEY_CACHED_CROSS_CHANNEL_VERSION)
    }
  }

  // ══════════════════════════════════════════════════════════════════════════
  // § Engagement Prompt
  // ══════════════════════════════════════════════════════════════════════════

  /** Number of times the user has manually pressed "Start Server" (excludes auto-start on boot). */
  fun getManualStartCount(context: Context): Int = get(context, MANUAL_START_COUNT)

  fun incrementManualStartCount(context: Context): Int {
    val newCount = get(context, MANUAL_START_COUNT) + 1
    set(context, MANUAL_START_COUNT, newCount)
    return newCount
  }

  /** True if the user checked "Don't show this again" or tapped a positive action (Support/Star). */
  fun isEngagementPromptPermanentlyDismissed(context: Context): Boolean =
    prefs(context).getBoolean(KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED, false)

  fun setEngagementPromptPermanentlyDismissed(context: Context) {
    prefs(context).edit { putBoolean(KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED, true) }
  }

  /** How many times the engagement prompt has been shown (max 2 lifetime). */
  fun getEngagementPromptShowCount(context: Context): Int = get(context, ENGAGEMENT_PROMPT_SHOW_COUNT)

  fun incrementEngagementPromptShowCount(context: Context): Int {
    val newCount = get(context, ENGAGEMENT_PROMPT_SHOW_COUNT) + 1
    set(context, ENGAGEMENT_PROMPT_SHOW_COUNT, newCount)
    return newCount
  }

  /**
   * Whether the engagement prompt should be shown right now.
   * Criteria: not permanently dismissed, shown fewer than 2 times, and manual start count
   * hits a threshold (3 for first show, 13 for second show — i.e. 10 additional starts).
   */
  fun shouldShowEngagementPrompt(context: Context): Boolean {
    if (isEngagementPromptPermanentlyDismissed(context)) return false
    val showCount = getEngagementPromptShowCount(context)
    if (showCount >= ENGAGEMENT_PROMPT_MAX_SHOWS) return false
    val startCount = getManualStartCount(context)
    return when (showCount) {
      0 -> startCount >= ENGAGEMENT_PROMPT_FIRST_THRESHOLD
      1 -> startCount >= ENGAGEMENT_PROMPT_SECOND_THRESHOLD
      else -> false
    }
  }

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
