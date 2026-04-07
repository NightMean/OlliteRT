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
private const val DEFAULT_PORT = 8000
private const val DEFAULT_PAYLOAD_LOGGING_ENABLED = false
private const val DEFAULT_ACCELERATOR_FALLBACK_ENABLED = true

object LlmHttpPrefs {
  fun isEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

  fun getPort(context: Context): Int =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_PORT, DEFAULT_PORT)

  fun isPayloadLoggingEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_PAYLOAD_LOGGING_ENABLED, DEFAULT_PAYLOAD_LOGGING_ENABLED)

  fun isAcceleratorFallbackEnabled(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_ACCELERATOR_FALLBACK_ENABLED, DEFAULT_ACCELERATOR_FALLBACK_ENABLED)

  fun getHfToken(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_HF_TOKEN, "")
      ?: ""

  fun setHfToken(context: Context, token: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_HF_TOKEN, token.trim())
      .apply()
  }

  fun getBearerToken(context: Context): String =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_BEARER_TOKEN, "")
      ?: ""

  fun setPayloadLoggingEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_PAYLOAD_LOGGING_ENABLED, enabled)
      .apply()
  }

  fun setAcceleratorFallbackEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_ACCELERATOR_FALLBACK_ENABLED, enabled)
      .apply()
  }

  fun setBearerToken(context: Context, token: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_LAST_MODEL_NAME, null)

  fun setLastModelName(context: Context, modelName: String?) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .apply {
        if (modelName != null) putString(KEY_LAST_MODEL_NAME, modelName)
        else remove(KEY_LAST_MODEL_NAME)
      }
      .apply()
  }

  fun getDefaultModelName(context: Context): String? =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_DEFAULT_MODEL_NAME, null)

  fun setDefaultModelName(context: Context, modelName: String?) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .apply {
        if (modelName != null) putString(KEY_DEFAULT_MODEL_NAME, modelName)
        else remove(KEY_DEFAULT_MODEL_NAME)
      }
      .apply()
  }

  fun isAutoStartOnBoot(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_AUTO_START_ON_BOOT, false)

  fun setAutoStartOnBoot(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_AUTO_START_ON_BOOT, enabled)
      .apply()
  }

  fun isKeepScreenOn(context: Context): Boolean =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_KEEP_SCREEN_ON, true)

  fun setKeepScreenOn(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_KEEP_SCREEN_ON, enabled)
      .apply()
  }

  fun save(context: Context, enabled: Boolean, port: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_ENABLED, enabled)
      .putInt(KEY_PORT, port)
      .apply()
  }
}
