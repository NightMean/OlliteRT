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

private const val PREFS_NAME = "llm_http_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_PORT = "port"
private const val KEY_PAYLOAD_LOGGING_ENABLED = "payload_logging_enabled"
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
private const val DEFAULT_PAYLOAD_LOGGING_ENABLED = false

// --- Developer / Debug ---
private const val KEY_VERBOSE_DEBUG_ENABLED = "verbose_debug_enabled"
private const val KEY_IGNORE_CLIENT_SAMPLER_PARAMS = "ignore_client_sampler_params"

// --- Home Assistant Integration (UI convenience — shows copy-config button in Settings) ---
private const val KEY_HA_INTEGRATION_ENABLED = "ha_integration_enabled"
private const val KEY_HA_STT_TRANSCRIPTION_PROMPT = "ha_stt_transcription_prompt"
private const val DEFAULT_HA_STT_TRANSCRIPTION_PROMPT = true
private const val KEY_HA_STT_TRANSCRIPTION_PROMPT_TEXT = "ha_stt_transcription_prompt_text"
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

  fun isEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_ENABLED, false)

  fun getPort(context: Context): Int =
    prefs(context).getInt(KEY_PORT, DEFAULT_PORT)

  fun isPayloadLoggingEnabled(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_PAYLOAD_LOGGING_ENABLED, DEFAULT_PAYLOAD_LOGGING_ENABLED)

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

  fun isAutoStartOnBoot(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_AUTO_START_ON_BOOT, false)

  fun setAutoStartOnBoot(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_AUTO_START_ON_BOOT, enabled)
      .apply()
  }

  fun isKeepScreenOn(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_KEEP_SCREEN_ON, true)

  fun setKeepScreenOn(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_KEEP_SCREEN_ON, enabled)
      .apply()
  }

  fun isAutoExpandLogs(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_AUTO_EXPAND_LOGS, false)

  fun setAutoExpandLogs(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_AUTO_EXPAND_LOGS, enabled)
      .apply()
  }

  fun isStreamLogsPreview(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_STREAM_LOGS_PREVIEW, true)

  fun setStreamLogsPreview(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_STREAM_LOGS_PREVIEW, enabled)
      .apply()
  }

  fun isKeepPartialResponse(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_KEEP_PARTIAL_RESPONSE, false)

  fun setKeepPartialResponse(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_KEEP_PARTIAL_RESPONSE, enabled)
      .apply()
  }

  fun isNotifShowRequestCount(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_NOTIF_SHOW_REQUEST_COUNT, false)

  fun setNotifShowRequestCount(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_NOTIF_SHOW_REQUEST_COUNT, enabled)
      .apply()
  }

  fun isWarmupEnabled(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_WARMUP_ENABLED, true)

  fun setWarmupEnabled(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_WARMUP_ENABLED, enabled)
      .apply()
  }

  fun isEagerVisionInit(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_EAGER_VISION_INIT, false)

  fun setEagerVisionInit(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_EAGER_VISION_INIT, enabled)
      .apply()
  }

  fun isCustomPromptsEnabled(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_CUSTOM_PROMPTS_ENABLED, false)

  fun setCustomPromptsEnabled(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_CUSTOM_PROMPTS_ENABLED, enabled)
      .apply()
  }

  fun isCompactToolSchemas(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_COMPACT_TOOL_SCHEMAS, false)

  fun setCompactToolSchemas(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_COMPACT_TOOL_SCHEMAS, enabled)
      .apply()
  }

  fun isAutoTruncateHistory(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_AUTO_TRUNCATE_HISTORY, false)

  fun setAutoTruncateHistory(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_AUTO_TRUNCATE_HISTORY, enabled)
      .apply()
  }

  fun isAutoTrimPrompts(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_AUTO_TRIM_PROMPTS, false)

  fun setAutoTrimPrompts(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_AUTO_TRIM_PROMPTS, enabled)
      .apply()
  }

  fun isClearLogsOnStop(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_CLEAR_LOGS_ON_STOP, false)

  fun setClearLogsOnStop(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_CLEAR_LOGS_ON_STOP, enabled)
      .apply()
  }

  fun isConfirmClearLogs(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_CONFIRM_CLEAR_LOGS, true)

  fun setConfirmClearLogs(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_CONFIRM_CLEAR_LOGS, enabled)
      .apply()
  }

  fun isShowRequestTypes(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_SHOW_REQUEST_TYPES, false)

  fun setShowRequestTypes(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_SHOW_REQUEST_TYPES, enabled)
      .apply()
  }

  fun isShowAdvancedMetrics(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_SHOW_ADVANCED_METRICS, false)

  fun setShowAdvancedMetrics(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_SHOW_ADVANCED_METRICS, enabled)
      .apply()
  }

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
    val json = org.json.JSONObject()
    for ((key, value) in configValues) {
      when (value) {
        is Boolean -> json.put(key, value)
        is Int -> json.put(key, value)
        is Long -> json.put(key, value)
        is Float -> json.put(key, value.toDouble())
        is Double -> json.put(key, value)
        is String -> json.put(key, value)
        else -> json.put(key, value.toString())
      }
    }
    prefs(context)
      .edit()
      .putString(KEY_PREFIX_INFERENCE_CONFIG + modelName, json.toString())
      .apply()
  }

  /**
   * Load saved inference config values for a model. Returns null if no config was saved.
   * Values are returned as their JSON-native types (Int, Double, Boolean, String).
   */
  fun getInferenceConfig(context: Context, modelName: String): Map<String, Any>? {
    val jsonStr = prefs(context)
      .getString(KEY_PREFIX_INFERENCE_CONFIG + modelName, null) ?: return null
    return try {
      val json = org.json.JSONObject(jsonStr)
      val result = mutableMapOf<String, Any>()
      for (key in json.keys()) {
        val value = json.get(key)
        // JSONObject returns Integer for small ints, Long for large — normalize
        result[key] = value
      }
      result
    } catch (e: Exception) {
      android.util.Log.w("ServerPrefs", "Failed to parse inference config JSON", e)
      null
    }
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

  fun isVerboseDebugEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_VERBOSE_DEBUG_ENABLED, false)

  fun setVerboseDebugEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_VERBOSE_DEBUG_ENABLED, enabled).apply()
  }

  fun isIgnoreClientSamplerParams(context: Context): Boolean =
    prefs(context).getBoolean(KEY_IGNORE_CLIENT_SAMPLER_PARAMS, false)

  fun setIgnoreClientSamplerParams(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_IGNORE_CLIENT_SAMPLER_PARAMS, enabled).apply()
  }

  fun isShowModelRecommendations(context: Context): Boolean =
    prefs(context).getBoolean(KEY_SHOW_MODEL_RECOMMENDATIONS, true)

  fun setShowModelRecommendations(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_SHOW_MODEL_RECOMMENDATIONS, enabled).apply()
  }

  // --- Home Assistant Integration ---

  fun isHaIntegrationEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_HA_INTEGRATION_ENABLED, false)

  fun setHaIntegrationEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_HA_INTEGRATION_ENABLED, enabled).apply()
  }

  fun isSttTranscriptionPromptEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_HA_STT_TRANSCRIPTION_PROMPT, DEFAULT_HA_STT_TRANSCRIPTION_PROMPT)

  fun setSttTranscriptionPromptEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_HA_STT_TRANSCRIPTION_PROMPT, enabled).apply()
  }

  fun getSttTranscriptionPromptText(context: Context): String =
    prefs(context).getString(KEY_HA_STT_TRANSCRIPTION_PROMPT_TEXT, DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT)
      ?: DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT

  fun setSttTranscriptionPromptText(context: Context, text: String) {
    prefs(context).edit().putString(KEY_HA_STT_TRANSCRIPTION_PROMPT_TEXT, text).apply()
  }

  // --- Keep Alive ---

  fun isKeepAliveEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_KEEP_ALIVE_ENABLED, DEFAULT_KEEP_ALIVE_ENABLED)

  fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled).apply()
  }

  fun getKeepAliveMinutes(context: Context): Int =
    prefs(context).getInt(KEY_KEEP_ALIVE_MINUTES, DEFAULT_KEEP_ALIVE_MINUTES)

  fun setKeepAliveMinutes(context: Context, minutes: Int) {
    prefs(context).edit().putInt(KEY_KEEP_ALIVE_MINUTES, minutes).apply()
  }

  // --- Log Persistence ---

  fun isLogPersistenceEnabled(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_LOG_PERSISTENCE_ENABLED, DEFAULT_LOG_PERSISTENCE_ENABLED)

  fun setLogPersistenceEnabled(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_LOG_PERSISTENCE_ENABLED, enabled)
      .apply()
  }

  fun getLogMaxEntries(context: Context): Int =
    prefs(context)
      .getInt(KEY_LOG_MAX_ENTRIES, DEFAULT_LOG_MAX_ENTRIES)

  fun setLogMaxEntries(context: Context, maxEntries: Int) {
    prefs(context)
      .edit()
      .putInt(KEY_LOG_MAX_ENTRIES, maxEntries)
      .apply()
  }

  fun getLogAutoDeleteMinutes(context: Context): Long =
    prefs(context)
      .getLong(KEY_LOG_AUTO_DELETE_MINUTES, DEFAULT_LOG_AUTO_DELETE_MINUTES.toLong())

  fun setLogAutoDeleteMinutes(context: Context, minutes: Long) {
    prefs(context)
      .edit()
      .putLong(KEY_LOG_AUTO_DELETE_MINUTES, minutes)
      .apply()
  }

  // --- Compact Image Data ---

  fun isCompactImageData(context: Context): Boolean =
    prefs(context).getBoolean(KEY_COMPACT_IMAGE_DATA, DEFAULT_COMPACT_IMAGE_DATA)

  fun setCompactImageData(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_COMPACT_IMAGE_DATA, enabled).apply()
  }

  fun isResolveClientHostnames(context: Context): Boolean =
    prefs(context).getBoolean(KEY_RESOLVE_CLIENT_HOSTNAMES, DEFAULT_RESOLVE_CLIENT_HOSTNAMES)

  fun setResolveClientHostnames(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_RESOLVE_CLIENT_HOSTNAMES, enabled).apply()
  }

  fun isHideHealthLogs(context: Context): Boolean =
    prefs(context).getBoolean(KEY_HIDE_HEALTH_LOGS, DEFAULT_HIDE_HEALTH_LOGS)

  fun setHideHealthLogs(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_HIDE_HEALTH_LOGS, enabled).apply()
  }

  // --- Update Check ---

  fun isUpdateCheckEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_UPDATE_CHECK_ENABLED, DEFAULT_UPDATE_CHECK_ENABLED)

  fun setUpdateCheckEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_UPDATE_CHECK_ENABLED, enabled).apply()
  }

  fun getUpdateCheckIntervalHours(context: Context): Int =
    prefs(context).getInt(KEY_UPDATE_CHECK_INTERVAL_HOURS, DEFAULT_UPDATE_CHECK_INTERVAL_HOURS)

  fun setUpdateCheckIntervalHours(context: Context, hours: Int) {
    prefs(context).edit().putInt(KEY_UPDATE_CHECK_INTERVAL_HOURS, hours).apply()
  }

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

  fun getUpdateCheckConsecutiveFailures(context: Context): Int =
    prefs(context).getInt(KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES, 0)

  fun setUpdateCheckConsecutiveFailures(context: Context, count: Int) {
    prefs(context).edit().putInt(KEY_UPDATE_CHECK_CONSECUTIVE_FAILURES, count).apply()
  }

  // ---------------------------------------------------------------------------
  // Engagement Prompt
  // ---------------------------------------------------------------------------

  /** Number of times the user has manually pressed "Start Server" (excludes auto-start on boot). */
  fun getManualStartCount(context: Context): Int =
    prefs(context).getInt(KEY_MANUAL_START_COUNT, 0)

  fun incrementManualStartCount(context: Context): Int {
    val newCount = getManualStartCount(context) + 1
    prefs(context).edit().putInt(KEY_MANUAL_START_COUNT, newCount).apply()
    return newCount
  }

  /** True if the user checked "Don't show this again" or tapped a positive action (Support/Star). */
  fun isEngagementPromptPermanentlyDismissed(context: Context): Boolean =
    prefs(context).getBoolean(KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED, false)

  fun setEngagementPromptPermanentlyDismissed(context: Context) {
    prefs(context).edit().putBoolean(KEY_ENGAGEMENT_PROMPT_PERMANENTLY_DISMISSED, true).apply()
  }

  /** How many times the engagement prompt has been shown (max 2 lifetime). */
  fun getEngagementPromptShowCount(context: Context): Int =
    prefs(context).getInt(KEY_ENGAGEMENT_PROMPT_SHOW_COUNT, 0)

  fun incrementEngagementPromptShowCount(context: Context): Int {
    val newCount = getEngagementPromptShowCount(context) + 1
    prefs(context).edit().putInt(KEY_ENGAGEMENT_PROMPT_SHOW_COUNT, newCount).apply()
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

  fun save(context: Context, enabled: Boolean, port: Int) {
    prefs(context)
      .edit()
      .putBoolean(KEY_ENABLED, enabled)
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

  fun getAllowlistContentVersion(context: Context): Int =
    prefs(context).getInt(KEY_ALLOWLIST_CONTENT_VERSION, 0)

  fun setAllowlistContentVersion(context: Context, version: Int) {
    prefs(context).edit().putInt(KEY_ALLOWLIST_CONTENT_VERSION, version).apply()
  }

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

  private val SENSITIVE_KEYS = setOf(KEY_BEARER_TOKEN, KEY_HF_TOKEN)

  fun dumpToLogcat(context: Context) {
    val tag = "OlliteRT.Settings"
    Log.i(tag, "=== Active Settings Snapshot ===")
    for ((key, value) in prefs(context).all.toSortedMap()) {
      val display = if (key in SENSITIVE_KEYS) {
        if (value.toString().isBlank()) "not set" else "configured (redacted)"
      } else {
        value.toString()
      }
      Log.i(tag, "$key = $display")
    }
    Log.i(tag, "================================")
  }
}
