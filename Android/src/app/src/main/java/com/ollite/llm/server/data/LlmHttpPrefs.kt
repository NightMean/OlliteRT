package com.ollite.llm.server.data

import android.content.Context
import java.util.UUID

private const val PREFS_NAME = "llm_http_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_PORT = "port"
private const val KEY_PAYLOAD_LOGGING_ENABLED = "payload_logging_enabled"
private const val KEY_ACCELERATOR_FALLBACK_ENABLED = "accelerator_fallback_enabled"
private const val KEY_BEARER_TOKEN = "bearer_token"
private const val DEFAULT_PORT = 9006
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

  fun save(context: Context, enabled: Boolean, port: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_ENABLED, enabled)
      .putInt(KEY_PORT, port)
      .apply()
  }
}
