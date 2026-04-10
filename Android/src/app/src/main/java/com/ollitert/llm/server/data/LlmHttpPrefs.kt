package com.ollitert.llm.server.data

import android.content.Context
import java.util.UUID

private const val PREFS_NAME = "llm_http_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_PORT = "port"
private const val KEY_PAYLOAD_LOGGING_ENABLED = "payload_logging_enabled"
private const val KEY_ACCELERATOR_FALLBACK_ENABLED = "accelerator_fallback_enabled"
private const val KEY_BEARER_TOKEN = "bearer_token"
private const val KEY_HF_TOKEN = "hf_token"
private const val KEY_LAST_MODEL_NAME = "last_model_name"
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
private const val KEY_CORS_ALLOWED_ORIGINS = "cors_allowed_origins"
private const val DEFAULT_CORS_ALLOWED_ORIGINS = "*"
private const val KEY_PREFIX_SYSTEM_PROMPT = "system_prompt_"
private const val KEY_PREFIX_CHAT_TEMPLATE = "chat_template_"
private const val KEY_PREFIX_INFERENCE_CONFIG = "inference_config_"
private const val DEFAULT_PORT = 8000
private const val DEFAULT_PAYLOAD_LOGGING_ENABLED = false
private const val DEFAULT_ACCELERATOR_FALLBACK_ENABLED = true

// --- Developer / Debug ---
private const val KEY_VERBOSE_DEBUG_ENABLED = "verbose_debug_enabled"
private const val KEY_IGNORE_CLIENT_SAMPLER_PARAMS = "ignore_client_sampler_params"

// --- Log Persistence ---
private const val KEY_LOG_PERSISTENCE_ENABLED = "log_persistence_enabled"
private const val KEY_LOG_MAX_ENTRIES = "log_max_entries"
private const val KEY_LOG_AUTO_DELETE_MINUTES = "log_auto_delete_minutes"
private const val DEFAULT_LOG_PERSISTENCE_ENABLED = false
private const val DEFAULT_LOG_MAX_ENTRIES = 500
private const val DEFAULT_LOG_AUTO_DELETE_MINUTES = 7 * 24 * 60 // 7 days

object LlmHttpPrefs {

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

  fun isAcceleratorFallbackEnabled(context: Context): Boolean =
    prefs(context)
      .getBoolean(KEY_ACCELERATOR_FALLBACK_ENABLED, DEFAULT_ACCELERATOR_FALLBACK_ENABLED)

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

  fun setPayloadLoggingEnabled(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_PAYLOAD_LOGGING_ENABLED, enabled)
      .apply()
  }

  fun setAcceleratorFallbackEnabled(context: Context, enabled: Boolean) {
    prefs(context)
      .edit()
      .putBoolean(KEY_ACCELERATOR_FALLBACK_ENABLED, enabled)
      .apply()
  }

  fun setBearerToken(context: Context, token: String) {
    prefs(context)
      .edit()
      .putString(KEY_BEARER_TOKEN, token.trim())
      .apply()
  }

  fun ensureBearerToken(context: Context): String {
    val current = getBearerToken(context)
    if (current.isNotBlank()) return current

    val generated = UUID.randomUUID().toString().replace("-", "")
    setBearerToken(context, generated)
    return generated
  }

  fun getLastModelName(context: Context): String? =
    prefs(context)
      .getString(KEY_LAST_MODEL_NAME, null)

  fun setLastModelName(context: Context, modelName: String?) {
    prefs(context)
      .edit()
      .apply {
        if (modelName != null) putString(KEY_LAST_MODEL_NAME, modelName)
        else remove(KEY_LAST_MODEL_NAME)
      }
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

  fun getSystemPrompt(context: Context, modelName: String): String =
    prefs(context)
      .getString(KEY_PREFIX_SYSTEM_PROMPT + modelName, "") ?: ""

  fun setSystemPrompt(context: Context, modelName: String, prompt: String) {
    prefs(context)
      .edit()
      .putString(KEY_PREFIX_SYSTEM_PROMPT + modelName, prompt)
      .apply()
  }

  fun getChatTemplate(context: Context, modelName: String): String =
    prefs(context)
      .getString(KEY_PREFIX_CHAT_TEMPLATE + modelName, "") ?: ""

  fun setChatTemplate(context: Context, modelName: String, template: String) {
    prefs(context)
      .edit()
      .putString(KEY_PREFIX_CHAT_TEMPLATE + modelName, template)
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
      android.util.Log.w("LlmHttpPrefs", "Failed to parse inference config JSON", e)
      null
    }
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

  fun save(context: Context, enabled: Boolean, port: Int) {
    prefs(context)
      .edit()
      .putBoolean(KEY_ENABLED, enabled)
      .putInt(KEY_PORT, port)
      .apply()
  }

  /**
   * Clear all settings and restore defaults. Wipes the entire SharedPreferences store,
   * including per-model inference configs, system prompts, and chat templates.
   * The cached prefs instance is invalidated so the next access picks up the cleared state.
   */
  fun resetToDefaults(context: Context) {
    prefs(context).edit().clear().apply()
    cachedPrefs = null
  }
}
