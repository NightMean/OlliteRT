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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private const val PREFS_NAME = "llm_http_prefs"
private const val KEY_PORT = "port"
private const val KEY_BEARER_TOKEN = "bearer_token"
private const val KEY_HF_TOKEN = "hf_token"
private const val KEY_DEFAULT_MODEL_NAME = "default_model_name"
private const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
private const val KEY_AUTO_EXPAND_LOGS = "auto_expand_logs"
private const val KEY_NOTIF_SHOW_REQUEST_COUNT = "notif_show_request_count"
private const val KEY_WARMUP_ENABLED = "warmup_enabled"
private const val KEY_STREAM_LOGS_PREVIEW = "stream_logs_preview"
private const val KEY_KEEP_PARTIAL_RESPONSE = "keep_partial_response"
private const val KEY_EAGER_VISION_INIT = "eager_vision_init"
private const val KEY_CUSTOM_PROMPTS_ENABLED = "custom_prompts_enabled"
private const val KEY_COMPACT_TOOL_SCHEMAS = "compact_tool_schemas"
private const val KEY_AUTO_TRUNCATE_HISTORY = "auto_truncate_history"
private const val KEY_AUTO_TRIM_PROMPTS = "auto_trim_prompts"
private const val KEY_CLEAR_LOGS_ON_STOP = "clear_logs_on_stop"
private const val KEY_CONFIRM_CLEAR_LOGS = "confirm_clear_logs"
private const val KEY_SHOW_REQUEST_TYPES = "show_request_types"
private const val KEY_SHOW_ADVANCED_METRICS = "show_advanced_metrics"
private const val KEY_CORS_ALLOWED_ORIGINS = "cors_allowed_origins"
private const val DEFAULT_CORS_ALLOWED_ORIGINS = "*"
private const val KEY_PREFIX_SYSTEM_PROMPT = "system_prompt_"
private const val KEY_PREFIX_INFERENCE_CONFIG = "inference_config_"
// TODO: Remove after 1.0.0 — migration from 0.9.0-beta keys (model.name → model.prefsKey).
private const val KEY_PREFS_KEY_MIGRATION_DONE = "prefs_key_migration_v1"

// --- Developer / Debug ---
private const val KEY_VERBOSE_DEBUG_ENABLED = "verbose_debug_enabled"
private const val KEY_IGNORE_CLIENT_SAMPLER_PARAMS = "ignore_client_sampler_params"

// --- Home Assistant Integration (UI convenience — shows copy-config button in Settings) ---
private const val KEY_HA_INTEGRATION_ENABLED = "ha_integration_enabled"
private const val KEY_STT_TRANSCRIPTION_PROMPT = "stt_transcription_prompt"
private const val DEFAULT_STT_TRANSCRIPTION_PROMPT = false
private const val KEY_STT_TRANSCRIPTION_PROMPT_TEXT = "stt_transcription_prompt_text"
// TODO: Remove after 1.0.0 — migration from 0.9.0 keys (ha_stt_* → stt_*).
private const val KEY_STT_KEY_MIGRATION_DONE = "stt_key_migration_v1"
internal const val DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT =
  "Transcribe the audio exactly as spoken. Output only the transcribed text, nothing else."


// --- Keep Alive (auto-unload model after idle timeout to free RAM) ---
private const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
private const val KEY_KEEP_ALIVE_MINUTES = "keep_alive_minutes"
private const val DEFAULT_KEEP_ALIVE_ENABLED = false
private const val DEFAULT_KEEP_ALIVE_MINUTES = 5

// --- Log Persistence ---
private const val KEY_LOG_PERSISTENCE_ENABLED = "log_persistence_enabled"
private const val KEY_LOG_MAX_ENTRIES = "log_max_entries"
private const val KEY_LOG_AUTO_DELETE_MINUTES = "log_auto_delete_minutes"
private const val DEFAULT_LOG_PERSISTENCE_ENABLED = false
private const val DEFAULT_LOG_MAX_ENTRIES = 500
private const val DEFAULT_LOG_AUTO_DELETE_MINUTES = 7 * 24 * 60 // 7 days

// --- Compact Image Data (replace base64 image payloads with size placeholders in logs) ---
private const val KEY_COMPACT_IMAGE_DATA = "compact_image_data"
private const val DEFAULT_COMPACT_IMAGE_DATA = true

// --- Resolve Client Hostnames (show hostname instead of IP in Logs) ---
private const val KEY_RESOLVE_CLIENT_HOSTNAMES = "resolve_client_hostnames"
private const val DEFAULT_RESOLVE_CLIENT_HOSTNAMES = false

// --- Hide Health Logs (suppress /health endpoint entries from the Logs tab) ---
private const val KEY_HIDE_HEALTH_LOGS = "hide_health_logs"
private const val DEFAULT_HIDE_HEALTH_LOGS = false

// --- Engagement Prompt (donation/support prompt shown after N manual server starts) ---
private const val KEY_MANUAL_START_COUNT = "manual_start_count"
private const val KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED = "engagement_prompt_permanently_dismissed"
private const val KEY_ENGAGEMENT_PROMPT_SHOW_COUNT = "engagement_prompt_show_count"
/** Maximum number of times the engagement prompt is shown before being auto-suppressed. */
private const val ENGAGEMENT_PROMPT_MAX_SHOWS = 2
/** Manual start count threshold for showing the engagement prompt the first time. */
private const val ENGAGEMENT_PROMPT_FIRST_THRESHOLD = 3
/** Manual start count threshold for showing the engagement prompt the second time. */
private const val ENGAGEMENT_PROMPT_SECOND_THRESHOLD = 13

// --- Update Check ---
private const val KEY_SHOW_MODEL_RECOMMENDATIONS = "show_model_recommendations"
private const val KEY_UPDATE_CHECK_ENABLED = "update_check_enabled"
private const val KEY_UPDATE_CHECK_INTERVAL_HOURS = "update_check_interval_hours"
private const val KEY_LAST_DISMISSED_UPDATE_VERSION = "last_dismissed_update_version"
private const val KEY_CACHED_LATEST_VERSION = "cached_latest_version"
private const val KEY_CACHED_RELEASE_HTML_URL = "cached_release_html_url"
private const val KEY_CACHED_RELEASE_ETAG = "cached_release_etag"
private const val KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES = "update_check_consecutive_failures"
private const val DEFAULT_UPDATE_CHECK_ENABLED = true
private const val DEFAULT_UPDATE_CHECK_INTERVAL_HOURS = 24

// --- Model Update Detection ---
private const val KEY_ALLOWLIST_CONTENT_VERSION = "allowlist_content_version"
private const val KEY_IGNORED_MODEL_UPDATES = "ignored_model_updates"

// --- DataStore Corruption Recovery ---
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
    pref.write(prefs(context).edit(), value).apply()
  }

  // ── Pref declarations ─────────────────────────────────────────────────
  // Boolean prefs — General
  private val AUTO_START_ON_BOOT = BoolPref(KEY_AUTO_START_ON_BOOT, false)
  private val KEEP_SCREEN_ON = BoolPref(KEY_KEEP_SCREEN_ON, true)
  private val AUTO_EXPAND_LOGS = BoolPref(KEY_AUTO_EXPAND_LOGS, false)
  private val STREAM_LOGS_PREVIEW = BoolPref(KEY_STREAM_LOGS_PREVIEW, true)
  private val KEEP_PARTIAL_RESPONSE = BoolPref(KEY_KEEP_PARTIAL_RESPONSE, false)
  private val NOTIF_SHOW_REQUEST_COUNT = BoolPref(KEY_NOTIF_SHOW_REQUEST_COUNT, false)
  private val WARMUP_ENABLED = BoolPref(KEY_WARMUP_ENABLED, true)
  private val EAGER_VISION_INIT = BoolPref(KEY_EAGER_VISION_INIT, false)
  private val CUSTOM_PROMPTS_ENABLED = BoolPref(KEY_CUSTOM_PROMPTS_ENABLED, false)
  private val COMPACT_TOOL_SCHEMAS = BoolPref(KEY_COMPACT_TOOL_SCHEMAS, false)
  private val AUTO_TRUNCATE_HISTORY = BoolPref(KEY_AUTO_TRUNCATE_HISTORY, false)
  private val AUTO_TRIM_PROMPTS = BoolPref(KEY_AUTO_TRIM_PROMPTS, false)
  private val CLEAR_LOGS_ON_STOP = BoolPref(KEY_CLEAR_LOGS_ON_STOP, false)
  private val CONFIRM_CLEAR_LOGS = BoolPref(KEY_CONFIRM_CLEAR_LOGS, true)
  private val SHOW_REQUEST_TYPES = BoolPref(KEY_SHOW_REQUEST_TYPES, false)
  private val SHOW_ADVANCED_METRICS = BoolPref(KEY_SHOW_ADVANCED_METRICS, false)
  // Boolean prefs — Developer / Debug
  private val VERBOSE_DEBUG_ENABLED = BoolPref(KEY_VERBOSE_DEBUG_ENABLED, false)
  private val IGNORE_CLIENT_SAMPLER_PARAMS = BoolPref(KEY_IGNORE_CLIENT_SAMPLER_PARAMS, false)
  private val SHOW_MODEL_RECOMMENDATIONS = BoolPref(KEY_SHOW_MODEL_RECOMMENDATIONS, true)
  // Boolean prefs — Home Assistant
  private val HA_INTEGRATION_ENABLED = BoolPref(KEY_HA_INTEGRATION_ENABLED, false)
  private val STT_TRANSCRIPTION_PROMPT = BoolPref(KEY_STT_TRANSCRIPTION_PROMPT, DEFAULT_STT_TRANSCRIPTION_PROMPT)
  // Boolean prefs — Keep Alive
  private val KEEP_ALIVE_ENABLED = BoolPref(KEY_KEEP_ALIVE_ENABLED, DEFAULT_KEEP_ALIVE_ENABLED)
  // Boolean prefs — Log Persistence
  private val LOG_PERSISTENCE_ENABLED = BoolPref(KEY_LOG_PERSISTENCE_ENABLED, DEFAULT_LOG_PERSISTENCE_ENABLED)
  // Boolean prefs — Misc
  private val COMPACT_IMAGE_DATA = BoolPref(KEY_COMPACT_IMAGE_DATA, DEFAULT_COMPACT_IMAGE_DATA)
  private val RESOLVE_CLIENT_HOSTNAMES = BoolPref(KEY_RESOLVE_CLIENT_HOSTNAMES, DEFAULT_RESOLVE_CLIENT_HOSTNAMES)
  private val HIDE_HEALTH_LOGS = BoolPref(KEY_HIDE_HEALTH_LOGS, DEFAULT_HIDE_HEALTH_LOGS)
  private val UPDATE_CHECK_ENABLED = BoolPref(KEY_UPDATE_CHECK_ENABLED, DEFAULT_UPDATE_CHECK_ENABLED)
  // Numeric prefs
  private val PORT = IntPref(KEY_PORT, DEFAULT_PORT)
  private val KEEP_ALIVE_MINUTES = IntPref(KEY_KEEP_ALIVE_MINUTES, DEFAULT_KEEP_ALIVE_MINUTES)
  private val LOG_MAX_ENTRIES = IntPref(KEY_LOG_MAX_ENTRIES, DEFAULT_LOG_MAX_ENTRIES)
  private val LOG_AUTO_DELETE_MINUTES = LongPref(KEY_LOG_AUTO_DELETE_MINUTES, DEFAULT_LOG_AUTO_DELETE_MINUTES.toLong())
  private val UPDATE_CHECK_INTERVAL_HOURS = IntPref(KEY_UPDATE_CHECK_INTERVAL_HOURS, DEFAULT_UPDATE_CHECK_INTERVAL_HOURS)
  private val UPDATE_CHECK_CONSECUTIVE_FAILURES = IntPref(KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES, 0)
  private val MANUAL_START_COUNT = IntPref(KEY_MANUAL_START_COUNT, 0)
  private val ENGAGEMENT_PROMPT_SHOW_COUNT = IntPref(KEY_ENGAGEMENT_PROMPT_SHOW_COUNT, 0)
  private val ALLOWLIST_CONTENT_VERSION = IntPref(KEY_ALLOWLIST_CONTENT_VERSION, 0)

  // ── Public accessors ──────────────────────────────────────────────────

  fun getPort(context: Context): Int = get(context, PORT)

  fun getHfToken(context: Context): String =
    prefs(context).getString(KEY_HF_TOKEN, "")
      ?: ""

  fun setHfToken(context: Context, token: String) {
    prefs(context)
      .edit()
      .putString(KEY_HF_TOKEN, token.trim())
      .apply()
  }

  fun getBearerToken(context: Context): String =
    prefs(context).getString(KEY_BEARER_TOKEN, "")
      ?: ""

  fun setBearerToken(context: Context, token: String) {
    prefs(context)
      .edit()
      .putString(KEY_BEARER_TOKEN, token.trim())
      .apply()
  }

  fun getDefaultModelName(context: Context): String? =
    prefs(context)
      .getString(KEY_DEFAULT_MODEL_NAME, null)

  fun setDefaultModelName(context: Context, modelName: String?) {
    prefs(context)
      .edit()
      .apply {
        if (modelName != null) putString(KEY_DEFAULT_MODEL_NAME, modelName)
        else remove(KEY_DEFAULT_MODEL_NAME)
      }
      .apply()
  }

  fun isAutoStartOnBoot(context: Context): Boolean = get(context, AUTO_START_ON_BOOT)
  fun setAutoStartOnBoot(context: Context, enabled: Boolean) = set(context, AUTO_START_ON_BOOT, enabled)

  fun isKeepScreenOn(context: Context): Boolean = get(context, KEEP_SCREEN_ON)
  fun setKeepScreenOn(context: Context, enabled: Boolean) = set(context, KEEP_SCREEN_ON, enabled)

  fun isAutoExpandLogs(context: Context): Boolean = get(context, AUTO_EXPAND_LOGS)
  fun setAutoExpandLogs(context: Context, enabled: Boolean) = set(context, AUTO_EXPAND_LOGS, enabled)

  fun isStreamLogsPreview(context: Context): Boolean = get(context, STREAM_LOGS_PREVIEW)
  fun setStreamLogsPreview(context: Context, enabled: Boolean) = set(context, STREAM_LOGS_PREVIEW, enabled)

  fun isKeepPartialResponse(context: Context): Boolean = get(context, KEEP_PARTIAL_RESPONSE)
  fun setKeepPartialResponse(context: Context, enabled: Boolean) = set(context, KEEP_PARTIAL_RESPONSE, enabled)

  fun isNotifShowRequestCount(context: Context): Boolean = get(context, NOTIF_SHOW_REQUEST_COUNT)
  fun setNotifShowRequestCount(context: Context, enabled: Boolean) = set(context, NOTIF_SHOW_REQUEST_COUNT, enabled)

  fun isWarmupEnabled(context: Context): Boolean = get(context, WARMUP_ENABLED)
  fun setWarmupEnabled(context: Context, enabled: Boolean) = set(context, WARMUP_ENABLED, enabled)

  fun isEagerVisionInit(context: Context): Boolean = get(context, EAGER_VISION_INIT)
  fun setEagerVisionInit(context: Context, enabled: Boolean) = set(context, EAGER_VISION_INIT, enabled)

  fun isCustomPromptsEnabled(context: Context): Boolean = get(context, CUSTOM_PROMPTS_ENABLED)
  fun setCustomPromptsEnabled(context: Context, enabled: Boolean) = set(context, CUSTOM_PROMPTS_ENABLED, enabled)

  fun isCompactToolSchemas(context: Context): Boolean = get(context, COMPACT_TOOL_SCHEMAS)
  fun setCompactToolSchemas(context: Context, enabled: Boolean) = set(context, COMPACT_TOOL_SCHEMAS, enabled)

  fun isAutoTruncateHistory(context: Context): Boolean = get(context, AUTO_TRUNCATE_HISTORY)
  fun setAutoTruncateHistory(context: Context, enabled: Boolean) = set(context, AUTO_TRUNCATE_HISTORY, enabled)

  fun isAutoTrimPrompts(context: Context): Boolean = get(context, AUTO_TRIM_PROMPTS)
  fun setAutoTrimPrompts(context: Context, enabled: Boolean) = set(context, AUTO_TRIM_PROMPTS, enabled)

  fun isClearLogsOnStop(context: Context): Boolean = get(context, CLEAR_LOGS_ON_STOP)
  fun setClearLogsOnStop(context: Context, enabled: Boolean) = set(context, CLEAR_LOGS_ON_STOP, enabled)

  fun isConfirmClearLogs(context: Context): Boolean = get(context, CONFIRM_CLEAR_LOGS)
  fun setConfirmClearLogs(context: Context, enabled: Boolean) = set(context, CONFIRM_CLEAR_LOGS, enabled)

  fun isShowRequestTypes(context: Context): Boolean = get(context, SHOW_REQUEST_TYPES)
  fun setShowRequestTypes(context: Context, enabled: Boolean) = set(context, SHOW_REQUEST_TYPES, enabled)

  fun isShowAdvancedMetrics(context: Context): Boolean = get(context, SHOW_ADVANCED_METRICS)
  fun setShowAdvancedMetrics(context: Context, enabled: Boolean) = set(context, SHOW_ADVANCED_METRICS, enabled)

  fun getSystemPrompt(context: Context, modelName: String): String =
    prefs(context)
      .getString(KEY_PREFIX_SYSTEM_PROMPT + modelName, "") ?: ""

  fun setSystemPrompt(context: Context, modelName: String, prompt: String) {
    prefs(context)
      .edit()
      .putString(KEY_PREFIX_SYSTEM_PROMPT + modelName, prompt)
      .apply()
  }

  /**
   * Persist inference config values (temperature, max tokens, etc.) for a specific model.
   * Stored as a JSON string so it survives app restarts. Values are keyed by ConfigKey label.
   */
  fun setInferenceConfig(context: Context, modelName: String, configValues: Map<String, Any>) {
    prefs(context)
      .edit()
      .putString(KEY_PREFIX_INFERENCE_CONFIG + modelName, encodeInferenceConfig(configValues))
      .apply()
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
    prefs(context)
      .edit()
      .remove(KEY_PREFIX_INFERENCE_CONFIG + modelName)
      .apply()
  }

  fun getCorsAllowedOrigins(context: Context): String =
    prefs(context)
      .getString(KEY_CORS_ALLOWED_ORIGINS, DEFAULT_CORS_ALLOWED_ORIGINS)
      ?: DEFAULT_CORS_ALLOWED_ORIGINS

  fun setCorsAllowedOrigins(context: Context, origins: String) {
    prefs(context)
      .edit()
      .putString(KEY_CORS_ALLOWED_ORIGINS, origins)
      .apply()
  }

  // --- Developer / Debug ---

  fun isVerboseDebugEnabled(context: Context): Boolean = get(context, VERBOSE_DEBUG_ENABLED)
  fun setVerboseDebugEnabled(context: Context, enabled: Boolean) = set(context, VERBOSE_DEBUG_ENABLED, enabled)

  fun isIgnoreClientSamplerParams(context: Context): Boolean = get(context, IGNORE_CLIENT_SAMPLER_PARAMS)
  fun setIgnoreClientSamplerParams(context: Context, enabled: Boolean) = set(context, IGNORE_CLIENT_SAMPLER_PARAMS, enabled)

  fun isShowModelRecommendations(context: Context): Boolean = get(context, SHOW_MODEL_RECOMMENDATIONS)
  fun setShowModelRecommendations(context: Context, enabled: Boolean) = set(context, SHOW_MODEL_RECOMMENDATIONS, enabled)

  // --- Home Assistant Integration ---

  fun isHaIntegrationEnabled(context: Context): Boolean = get(context, HA_INTEGRATION_ENABLED)
  fun setHaIntegrationEnabled(context: Context, enabled: Boolean) = set(context, HA_INTEGRATION_ENABLED, enabled)

  fun isSttTranscriptionPromptEnabled(context: Context): Boolean = get(context, STT_TRANSCRIPTION_PROMPT)
  fun setSttTranscriptionPromptEnabled(context: Context, enabled: Boolean) = set(context, STT_TRANSCRIPTION_PROMPT, enabled)

  fun getSttTranscriptionPromptText(context: Context): String =
    prefs(context).getString(KEY_STT_TRANSCRIPTION_PROMPT_TEXT, DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT)
      ?: DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT

  fun setSttTranscriptionPromptText(context: Context, text: String) {
    prefs(context).edit().putString(KEY_STT_TRANSCRIPTION_PROMPT_TEXT, text).apply()
  }

  // --- Keep Alive ---

  fun isKeepAliveEnabled(context: Context): Boolean = get(context, KEEP_ALIVE_ENABLED)
  fun setKeepAliveEnabled(context: Context, enabled: Boolean) = set(context, KEEP_ALIVE_ENABLED, enabled)

  fun getKeepAliveMinutes(context: Context): Int = get(context, KEEP_ALIVE_MINUTES)
  fun setKeepAliveMinutes(context: Context, minutes: Int) = set(context, KEEP_ALIVE_MINUTES, minutes)

  // --- Log Persistence ---

  fun isLogPersistenceEnabled(context: Context): Boolean = get(context, LOG_PERSISTENCE_ENABLED)
  fun setLogPersistenceEnabled(context: Context, enabled: Boolean) = set(context, LOG_PERSISTENCE_ENABLED, enabled)

  fun getLogMaxEntries(context: Context): Int = get(context, LOG_MAX_ENTRIES)
  fun setLogMaxEntries(context: Context, maxEntries: Int) = set(context, LOG_MAX_ENTRIES, maxEntries)

  fun getLogAutoDeleteMinutes(context: Context): Long = get(context, LOG_AUTO_DELETE_MINUTES)
  fun setLogAutoDeleteMinutes(context: Context, minutes: Long) = set(context, LOG_AUTO_DELETE_MINUTES, minutes)

  // --- Compact Image Data ---

  fun isCompactImageData(context: Context): Boolean = get(context, COMPACT_IMAGE_DATA)
  fun setCompactImageData(context: Context, enabled: Boolean) = set(context, COMPACT_IMAGE_DATA, enabled)

  fun isResolveClientHostnames(context: Context): Boolean = get(context, RESOLVE_CLIENT_HOSTNAMES)
  fun setResolveClientHostnames(context: Context, enabled: Boolean) = set(context, RESOLVE_CLIENT_HOSTNAMES, enabled)

  fun isHideHealthLogs(context: Context): Boolean = get(context, HIDE_HEALTH_LOGS)
  fun setHideHealthLogs(context: Context, enabled: Boolean) = set(context, HIDE_HEALTH_LOGS, enabled)

  // --- Update Check ---

  fun isUpdateCheckEnabled(context: Context): Boolean = get(context, UPDATE_CHECK_ENABLED)
  fun setUpdateCheckEnabled(context: Context, enabled: Boolean) = set(context, UPDATE_CHECK_ENABLED, enabled)

  fun getUpdateCheckIntervalHours(context: Context): Int = get(context, UPDATE_CHECK_INTERVAL_HOURS)
  fun setUpdateCheckIntervalHours(context: Context, hours: Int) = set(context, UPDATE_CHECK_INTERVAL_HOURS, hours)

  fun getLastDismissedUpdateVersion(context: Context): String? =
    prefs(context).getString(KEY_LAST_DISMISSED_UPDATE_VERSION, null)

  fun setLastDismissedUpdateVersion(context: Context, version: String?) {
    prefs(context).edit().apply {
      if (version != null) putString(KEY_LAST_DISMISSED_UPDATE_VERSION, version)
      else remove(KEY_LAST_DISMISSED_UPDATE_VERSION)
    }.apply()
  }

  fun getCachedLatestVersion(context: Context): String? =
    prefs(context).getString(KEY_CACHED_LATEST_VERSION, null)

  fun getCachedReleaseHtmlUrl(context: Context): String? =
    prefs(context).getString(KEY_CACHED_RELEASE_HTML_URL, null)

  fun getCachedReleaseETag(context: Context): String? =
    prefs(context).getString(KEY_CACHED_RELEASE_ETAG, null)

  fun setCachedUpdateInfo(context: Context, version: String?, htmlUrl: String?, etag: String?) {
    prefs(context).edit().apply {
      if (version != null) putString(KEY_CACHED_LATEST_VERSION, version) else remove(KEY_CACHED_LATEST_VERSION)
      if (htmlUrl != null) putString(KEY_CACHED_RELEASE_HTML_URL, htmlUrl) else remove(KEY_CACHED_RELEASE_HTML_URL)
      if (etag != null) putString(KEY_CACHED_RELEASE_ETAG, etag) else remove(KEY_CACHED_RELEASE_ETAG)
    }.apply()
  }

  fun getUpdateCheckConsecutiveFailures(context: Context): Int = get(context, UPDATE_CHECK_CONSECUTIVE_FAILURES)
  fun setUpdateCheckConsecutiveFailures(context: Context, count: Int) = set(context, UPDATE_CHECK_CONSECUTIVE_FAILURES, count)

  // ---------------------------------------------------------------------------
  // Engagement Prompt
  // ---------------------------------------------------------------------------

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
    prefs(context).edit().putBoolean(KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED, true).apply()
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

  /** Clear all cached update state (version, URL, ETag, dismiss). Called after a successful app update. */
  fun clearUpdateState(context: Context) {
    prefs(context).edit()
      .remove(KEY_CACHED_LATEST_VERSION)
      .remove(KEY_CACHED_RELEASE_HTML_URL)
      .remove(KEY_CACHED_RELEASE_ETAG)
      .remove(KEY_LAST_DISMISSED_UPDATE_VERSION)
      .remove(KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES)
      .apply()
  }

  fun save(context: Context, port: Int) {
    prefs(context)
      .edit()
      .putInt(KEY_PORT, port)
      .apply()
  }

  // -- DataStore Corruption Recovery --

  fun getCorruptedDataStores(context: Context): Set<String> =
    prefs(context).getStringSet(KEY_CORRUPTED_DATASTORES, emptySet()) ?: emptySet()

  fun addCorruptedDataStore(context: Context, name: String) {
    val current = getCorruptedDataStores(context).toMutableSet()
    current.add(name)
    prefs(context).edit().putStringSet(KEY_CORRUPTED_DATASTORES, current).apply()
  }

  fun clearCorruptedDataStores(context: Context) {
    prefs(context).edit().remove(KEY_CORRUPTED_DATASTORES).apply()
  }

  // --- Model Update Detection ---

  fun getAllowlistContentVersion(context: Context): Int = get(context, ALLOWLIST_CONTENT_VERSION)
  fun setAllowlistContentVersion(context: Context, version: Int) = set(context, ALLOWLIST_CONTENT_VERSION, version)

  fun getIgnoredModelUpdates(context: Context): Set<String> =
    prefs(context).getStringSet(KEY_IGNORED_MODEL_UPDATES, emptySet()) ?: emptySet()

  fun addIgnoredModelUpdate(context: Context, nameVersion: String) {
    val current = getIgnoredModelUpdates(context).toMutableSet()
    current.add(nameVersion)
    prefs(context).edit().putStringSet(KEY_IGNORED_MODEL_UPDATES, current).apply()
  }

  fun removeIgnoredModelUpdate(context: Context, nameVersion: String) {
    val current = getIgnoredModelUpdates(context).toMutableSet()
    current.remove(nameVersion)
    prefs(context).edit().putStringSet(KEY_IGNORED_MODEL_UPDATES, current).apply()
  }

  /**
   * Clear all settings and restore defaults. Wipes the entire SharedPreferences store,
   * including per-model inference configs and system prompts.
   * The cached prefs instance is invalidated so the next access picks up the cleared state.
   */
  fun resetToDefaults(context: Context) {
    prefs(context).edit().clear().apply()
    cachedPrefs = null
  }

  // TODO: Remove after 1.0.0 — one-time migration introduced in 0.9.0-beta.1 to move
  // per-model prefs from old keys (model.name) to stable keys (model.downloadFileName).
  fun migratePerModelKeys(context: Context, modelNameToDownloadFileName: Map<String, String>) {
    val p = prefs(context)
    if (p.getBoolean(KEY_PREFS_KEY_MIGRATION_DONE, false)) return

    val editor = p.edit()
    var migrated = 0

    for ((oldName, newKey) in modelNameToDownloadFileName) {
      if (oldName == newKey) continue

      val oldPromptKey = KEY_PREFIX_SYSTEM_PROMPT + oldName
      val newPromptKey = KEY_PREFIX_SYSTEM_PROMPT + newKey
      val prompt = p.getString(oldPromptKey, null)
      if (prompt != null && !p.contains(newPromptKey)) {
        editor.putString(newPromptKey, prompt)
        editor.remove(oldPromptKey)
        migrated++
      }

      val oldConfigKey = KEY_PREFIX_INFERENCE_CONFIG + oldName
      val newConfigKey = KEY_PREFIX_INFERENCE_CONFIG + newKey
      val config = p.getString(oldConfigKey, null)
      if (config != null && !p.contains(newConfigKey)) {
        editor.putString(newConfigKey, config)
        editor.remove(oldConfigKey)
        migrated++
      }
    }

    editor.putBoolean(KEY_PREFS_KEY_MIGRATION_DONE, true)
    editor.apply()

    if (migrated > 0) {
      Log.i(TAG, "Migrated $migrated per-model prefs key(s) to stable format")
    }
  }

  // TODO: Remove after 1.0.0 — one-time migration introduced in 0.9.0 to rename
  // ha_stt_transcription_prompt → stt_transcription_prompt (setting is not HA-specific).
  fun migrateSttKeys(context: Context) {
    val p = prefs(context)
    if (p.getBoolean(KEY_STT_KEY_MIGRATION_DONE, false)) return

    val editor = p.edit()
    var migrated = 0

    val oldToggle = "ha_stt_transcription_prompt"
    if (p.contains(oldToggle) && !p.contains(KEY_STT_TRANSCRIPTION_PROMPT)) {
      editor.putBoolean(KEY_STT_TRANSCRIPTION_PROMPT, p.getBoolean(oldToggle, DEFAULT_STT_TRANSCRIPTION_PROMPT))
      editor.remove(oldToggle)
      migrated++
    }

    val oldText = "ha_stt_transcription_prompt_text"
    if (p.contains(oldText) && !p.contains(KEY_STT_TRANSCRIPTION_PROMPT_TEXT)) {
      editor.putString(KEY_STT_TRANSCRIPTION_PROMPT_TEXT, p.getString(oldText, DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT))
      editor.remove(oldText)
      migrated++
    }

    editor.putBoolean(KEY_STT_KEY_MIGRATION_DONE, true)
    editor.apply()

    if (migrated > 0) {
      Log.i(TAG, "Migrated $migrated STT prefs key(s) from ha_stt_* to stt_*")
    }
  }

  private val SENSITIVE_KEYS = setOf(KEY_BEARER_TOKEN, KEY_HF_TOKEN)

  fun dumpToLogcat(context: Context) {
    Log.i(TAG, "=== Active Settings Snapshot ===")
    for ((key, value) in prefs(context).all.toSortedMap()) {
      val display = if (key in SENSITIVE_KEYS) {
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
      compactToolSchemas = isCompactToolSchemas(context),
      ignoreClientSamplerParams = isIgnoreClientSamplerParams(context),
      eagerVisionInit = isEagerVisionInit(context),
      streamLogsPreview = isStreamLogsPreview(context),
      keepPartialResponse = isKeepPartialResponse(context),
      compactImageData = isCompactImageData(context),
      resolveClientHostnames = isResolveClientHostnames(context),
      hideHealthLogs = isHideHealthLogs(context),
      verboseDebug = isVerboseDebugEnabled(context),
      sttTranscriptionPromptEnabled = isSttTranscriptionPromptEnabled(context),
      sttTranscriptionPromptText = getSttTranscriptionPromptText(context),
    )
}

data class RequestPrefsSnapshot(
  val autoTruncateHistory: Boolean = false,
  val autoTrimPrompts: Boolean = false,
  val compactToolSchemas: Boolean = false,
  val ignoreClientSamplerParams: Boolean = false,
  val eagerVisionInit: Boolean = false,
  val streamLogsPreview: Boolean = false,
  val keepPartialResponse: Boolean = false,
  val compactImageData: Boolean = false,
  val resolveClientHostnames: Boolean = false,
  val hideHealthLogs: Boolean = false,
  val verboseDebug: Boolean = false,
  val sttTranscriptionPromptEnabled: Boolean = false,
  val sttTranscriptionPromptText: String = "",
)

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
    result
  } catch (_: Exception) {
    null
  }
}
