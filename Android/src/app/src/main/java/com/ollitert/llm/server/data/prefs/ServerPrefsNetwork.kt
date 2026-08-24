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

import android.content.SharedPreferences
import androidx.core.content.edit

// ═══════════════════════════════════════════════════════════════════════════
// § Server Network Config — port, bind mode, client IP rules, tokens, CORS
// ═══════════════════════════════════════════════════════════════════════════

internal const val KEY_PORT = "port"
internal const val KEY_SERVER_BIND_MODE = "server_bind_mode"
internal const val KEY_CUSTOM_BIND_ADDRESS = "custom_bind_address"
internal const val KEY_CLIENT_IP_POLICY_MODE = "client_ip_policy_mode"
internal const val KEY_CLIENT_IP_RULES = "client_ip_rules"
internal const val KEY_BEARER_TOKEN = "bearer_token"
internal const val KEY_HF_TOKEN = "hf_token"
internal const val KEY_CORS_ALLOWED_ORIGINS = "cors_allowed_origins"
internal const val DEFAULT_CORS_ALLOWED_ORIGINS = "*"

internal object ServerPrefsNetwork {

  fun getPort(prefs: SharedPreferences): Int =
    prefs.getInt(KEY_PORT, DEFAULT_PORT)

  fun savePort(prefs: SharedPreferences, port: Int) {
    prefs.edit { putInt(KEY_PORT, port.coerceIn(1, 65535)) }
  }

  fun getServerBindConfig(prefs: SharedPreferences): ServerBindConfig = ServerBindConfig(
    mode = ServerBindMode.fromPreference(
      prefs.getString(KEY_SERVER_BIND_MODE, ServerBindMode.ALL_INTERFACES.preferenceValue)
        ?: ServerBindMode.ALL_INTERFACES.preferenceValue
    ),
    customAddress = prefs.getString(KEY_CUSTOM_BIND_ADDRESS, "") ?: "",
  )

  /** Writes both listener fields in one editor transaction so startup never sees a mixed config. */
  fun setServerBindConfig(prefs: SharedPreferences, config: ServerBindConfig) {
    prefs.edit {
      putString(KEY_SERVER_BIND_MODE, config.mode.preferenceValue)
      putString(KEY_CUSTOM_BIND_ADDRESS, config.customAddress.trim())
    }
  }

  fun getClientIpPolicyConfig(prefs: SharedPreferences): ClientIpPolicyConfig = ClientIpPolicyConfig(
    mode = ClientIpPolicyMode.fromPreference(
      prefs.getString(KEY_CLIENT_IP_POLICY_MODE, ClientIpPolicyMode.ALLOW_ALL.preferenceValue)
        ?: ClientIpPolicyMode.ALLOW_ALL.preferenceValue
    ),
    rulesText = prefs.getString(KEY_CLIENT_IP_RULES, "") ?: "",
  )

  /** Writes the policy and its rules atomically before the running server receives the compiled policy. */
  fun setClientIpPolicyConfig(prefs: SharedPreferences, config: ClientIpPolicyConfig) {
    prefs.edit {
      putString(KEY_CLIENT_IP_POLICY_MODE, config.mode.preferenceValue)
      putString(KEY_CLIENT_IP_RULES, config.rulesText.trim())
    }
  }

  fun getBearerToken(prefs: SharedPreferences): String =
    prefs.getString(KEY_BEARER_TOKEN, "") ?: ""

  fun setBearerToken(prefs: SharedPreferences, token: String) {
    prefs.edit { putString(KEY_BEARER_TOKEN, token.trim()) }
  }

  fun getHfToken(prefs: SharedPreferences): String =
    prefs.getString(KEY_HF_TOKEN, "") ?: ""

  fun setHfToken(prefs: SharedPreferences, token: String) {
    prefs.edit { putString(KEY_HF_TOKEN, token.trim()) }
  }

  fun getCorsAllowedOrigins(prefs: SharedPreferences): String =
    prefs.getString(KEY_CORS_ALLOWED_ORIGINS, DEFAULT_CORS_ALLOWED_ORIGINS)
      ?: DEFAULT_CORS_ALLOWED_ORIGINS

  fun setCorsAllowedOrigins(prefs: SharedPreferences, origins: String) {
    prefs.edit { putString(KEY_CORS_ALLOWED_ORIGINS, origins) }
  }
}
