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

package com.ollitert.llm.server.ui.settings

import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.prefs.BindAddressResult
import com.ollitert.llm.server.data.prefs.ClientIpPolicyMode
import com.ollitert.llm.server.data.prefs.DEFAULT_PORT
import com.ollitert.llm.server.data.prefs.MAX_VALID_PORT
import com.ollitert.llm.server.data.prefs.MIN_VALID_PORT
import com.ollitert.llm.server.data.prefs.ServerBindConfig
import com.ollitert.llm.server.data.prefs.ServerBindMode
import com.ollitert.llm.server.data.prefs.resolveHost

// ─── Server Configuration Card ────────────────────────────────────

val SERVER_BIND_MODE = SettingDef.Dropdown(
  key = "server_bind_mode",
  labelRes = R.string.settings_bind_mode_label,
  descriptionRes = R.string.settings_bind_mode_desc,
  card = CardId.SERVER_CONFIG,
  default = ServerBindMode.ALL_INTERFACES.preferenceValue,
  prefsKey = "server_bind_mode",
  read = { it.getServerBindConfig().mode.preferenceValue },
  write = { repo, value ->
    val current = repo.getServerBindConfig()
    repo.setServerBindConfig(current.copy(mode = ServerBindMode.fromPreference(value)))
  },
)

val CUSTOM_BIND_ADDRESS = SettingDef.TextInput(
  key = "custom_bind_address",
  labelRes = R.string.settings_custom_bind_address_label,
  descriptionRes = R.string.settings_custom_bind_address_desc,
  card = CardId.SERVER_CONFIG,
  default = "",
  prefsKey = "custom_bind_address",
  validate = { input, ctx ->
    if (ServerBindConfig(ServerBindMode.CUSTOM, input).resolveHost() is BindAddressResult.Invalid)
      ctx.getString(R.string.validation_bind_address_invalid)
    else null
  },
  read = { it.getServerBindConfig().customAddress },
  write = { repo, value ->
    val current = repo.getServerBindConfig()
    repo.setServerBindConfig(current.copy(customAddress = value.trim()))
  },
)

val HOST_PORT = SettingDef.NumericInput(
  key = "host_port",
  labelRes = R.string.settings_host_port_label,
  descriptionRes = R.string.settings_host_port_desc,
  card = CardId.SERVER_CONFIG,
  default = DEFAULT_PORT,
  prefsKey = "port",
  min = MIN_VALID_PORT,
  max = MAX_VALID_PORT,
  read = { it.getPort() },
  write = { repo, v -> repo.savePort(v) },
)

val CLIENT_IP_POLICY_MODE = SettingDef.Dropdown(
  key = "client_ip_policy_mode",
  labelRes = R.string.settings_client_ip_policy_label,
  descriptionRes = R.string.settings_client_ip_policy_desc,
  card = CardId.SERVER_CONFIG,
  default = ClientIpPolicyMode.ALLOW_ALL.preferenceValue,
  prefsKey = "client_ip_policy_mode",
  read = { it.getClientIpPolicyConfig().mode.preferenceValue },
  write = { repo, value ->
    val current = repo.getClientIpPolicyConfig()
    repo.setClientIpPolicyConfig(current.copy(mode = ClientIpPolicyMode.fromPreference(value)))
  },
)

val CLIENT_IP_RULES = SettingDef.TextInput(
  key = "client_ip_rules",
  labelRes = R.string.settings_client_ip_rules_label,
  descriptionRes = R.string.settings_client_ip_rules_desc,
  card = CardId.SERVER_CONFIG,
  default = "",
  prefsKey = "client_ip_rules",
  read = { it.getClientIpPolicyConfig().rulesText },
  write = { repo, value ->
    val current = repo.getClientIpPolicyConfig()
    repo.setClientIpPolicyConfig(current.copy(rulesText = value.trim()))
  },
)

val BEARER_TOKEN = SettingDef.Custom(
  key = "bearer_token",
  labelRes = R.string.settings_bearer_token,
  descriptionRes = R.string.settings_bearer_token_desc,
  card = CardId.SERVER_CONFIG,
)

val CORS_ORIGINS = SettingDef.TextInput(
  key = "cors_origins",
  labelRes = R.string.settings_cors_label,
  descriptionRes = R.string.settings_cors_desc,
  card = CardId.SERVER_CONFIG,
  default = "*",
  resetDefault = "",
  prefsKey = "cors_allowed_origins",
  validate = { input, ctx ->
    if (!isValidCorsOrigins(input))
      ctx.getString(R.string.validation_cors_invalid)
    else null
  },
  read = { it.getCorsAllowedOrigins() },
  write = { repo, v -> repo.setCorsAllowedOrigins(v) },
)

// ─── Auto-Launch & Behaviour Card ─────────────────────────────────

val DEFAULT_MODEL = SettingDef.Dropdown(
  key = "default_model",
  labelRes = R.string.settings_default_model_label,
  descriptionRes = R.string.settings_default_model_desc,
  card = CardId.AUTO_LAUNCH,
  default = null,
  resetDefault = "",
  prefsKey = "default_model_name",
  read = { it.getDefaultModelName() },
  write = { repo, v -> repo.setDefaultModelName(v) },
)

val START_ON_BOOT = SettingDef.Toggle(
  key = "start_on_boot",
  labelRes = R.string.settings_start_on_boot,
  descriptionRes = R.string.settings_start_on_boot_desc,
  card = CardId.AUTO_LAUNCH,
  default = false,
  prefsKey = "auto_start_on_boot",
  read = { it.isAutoStartOnBoot() },
  write = { repo, v -> repo.setAutoStartOnBoot(v) },
)

val KEEP_ALIVE = SettingDef.Toggle(
  key = "keep_alive",
  labelRes = R.string.settings_keep_alive,
  descriptionRes = R.string.settings_keep_alive_desc,
  card = CardId.AUTO_LAUNCH,
  default = false,
  prefsKey = "keep_alive_enabled",
  read = { it.isKeepAliveEnabled() },
  write = { repo, v -> repo.setKeepAliveEnabled(v) },
)

val KEEP_ALIVE_TIMEOUT = SettingDef.NumericWithUnit(
  key = "keep_alive_timeout",
  labelRes = R.string.settings_idle_timeout_label,
  descriptionRes = R.string.settings_idle_timeout_desc,
  card = CardId.AUTO_LAUNCH,
  defaultValue = 5L,
  defaultUnit = "minutes",
  prefsKey = "keep_alive_minutes",
  unitOptions = listOf("minutes", "hours"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "hours" -> value * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 60 == 0L -> Pair(base / 60, "hours")
      else -> Pair(base, "minutes")
    }
  },
  min = 1,
  max = 7200,
  baseUnitLabel = "minutes",
  read = { it.getKeepAliveMinutes().toLong() },
  write = { repo, v -> repo.setKeepAliveMinutes(v.toInt()) },
)

val DONTKILLMYAPP = SettingDef.Custom(
  key = "dontkillmyapp",
  labelRes = R.string.settings_dontkillmyapp_title,
  descriptionRes = R.string.settings_dontkillmyapp_desc,
  card = CardId.AUTO_LAUNCH,
)

// ─── Advanced Timeouts Card ──────────────────────────────────────────────

val DEFAULT_SEED = SettingDef.TextInput(
  key = "default_seed",
  labelRes = R.string.settings_default_seed_label,
  descriptionRes = R.string.settings_default_seed_desc_prefix,
  card = CardId.ADVANCED_SETTINGS,
  default = "",
  prefsKey = "default_seed",
  validate = { input, ctx ->
    if (input.isNotBlank() && (input.toIntOrNull() == null || input.toInt() < 0)) {
      ctx.getString(R.string.validation_default_seed_invalid)
    } else null
  },
  read = { it.getDefaultSeed()?.toString() ?: "" },
  write = { repo, value -> repo.setDefaultSeed(value.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()) },
)

val TIMEOUT_CHAT_COMPLETIONS = SettingDef.NumericWithUnit(
  key = "timeout_chat_completions",
  labelRes = R.string.settings_timeout_chat_completions_label,
  descriptionRes = R.string.settings_timeout_chat_completions_desc,
  card = CardId.ADVANCED_SETTINGS,
  defaultValue = 120L,
  defaultUnit = "seconds",
  prefsKey = "timeout_chat_completions_seconds",
  unitOptions = listOf("seconds", "minutes"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "minutes" -> value * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 60 == 0L -> Pair(base / 60, "minutes")
      else -> Pair(base, "seconds")
    }
  },
  min = 10,
  max = 3600,
  baseUnitLabel = "seconds",
  read = { it.getTimeoutChatCompletions() },
  write = { repo, v -> repo.setTimeoutChatCompletions(v) },
)

val TIMEOUT_RESPONSES = SettingDef.NumericWithUnit(
  key = "timeout_responses",
  labelRes = R.string.settings_timeout_responses_label,
  descriptionRes = R.string.settings_timeout_responses_desc,
  card = CardId.ADVANCED_SETTINGS,
  defaultValue = 90L,
  defaultUnit = "seconds",
  prefsKey = "timeout_responses_seconds",
  unitOptions = listOf("seconds", "minutes"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "minutes" -> value * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 60 == 0L -> Pair(base / 60, "minutes")
      else -> Pair(base, "seconds")
    }
  },
  min = 10,
  max = 3600,
  baseUnitLabel = "seconds",
  read = { it.getTimeoutResponses() },
  write = { repo, v -> repo.setTimeoutResponses(v) },
)

val TIMEOUT_STREAMING = SettingDef.NumericWithUnit(
  key = "timeout_streaming",
  labelRes = R.string.settings_timeout_streaming_label,
  descriptionRes = R.string.settings_timeout_streaming_desc,
  card = CardId.ADVANCED_SETTINGS,
  defaultValue = 90L,
  defaultUnit = "seconds",
  prefsKey = "timeout_streaming_seconds",
  unitOptions = listOf("seconds", "minutes"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "minutes" -> value * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 60 == 0L -> Pair(base / 60, "minutes")
      else -> Pair(base, "seconds")
    }
  },
  min = 10,
  max = 3600,
  baseUnitLabel = "seconds",
  read = { it.getTimeoutStreaming() },
  write = { repo, v -> repo.setTimeoutStreaming(v) },
)

val TIMEOUT_BLOCKING = SettingDef.NumericWithUnit(
  key = "timeout_blocking",
  labelRes = R.string.settings_timeout_blocking_label,
  descriptionRes = R.string.settings_timeout_blocking_desc,
  card = CardId.ADVANCED_SETTINGS,
  defaultValue = 30L,
  defaultUnit = "seconds",
  prefsKey = "timeout_blocking_seconds",
  unitOptions = listOf("seconds", "minutes"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "minutes" -> value * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 60 == 0L -> Pair(base / 60, "minutes")
      else -> Pair(base, "seconds")
    }
  },
  min = 10,
  max = 3600,
  baseUnitLabel = "seconds",
  read = { it.getTimeoutBlocking() },
  write = { repo, v -> repo.setTimeoutBlocking(v) },
)

val TIMEOUT_WARMUP = SettingDef.NumericWithUnit(
  key = "timeout_warmup",
  labelRes = R.string.settings_timeout_warmup_label,
  descriptionRes = R.string.settings_timeout_warmup_desc,
  card = CardId.ADVANCED_SETTINGS,
  defaultValue = 10L,
  defaultUnit = "seconds",
  prefsKey = "timeout_warmup_seconds",
  unitOptions = listOf("seconds", "minutes"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "minutes" -> value * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 60 == 0L -> Pair(base / 60, "minutes")
      else -> Pair(base, "seconds")
    }
  },
  min = 5,
  max = 300,
  baseUnitLabel = "seconds",
  read = { it.getTimeoutWarmup() },
  write = { repo, v -> repo.setTimeoutWarmup(v) },
)

val TIMEOUT_KEEP_ALIVE_RECHECK = SettingDef.NumericWithUnit(
  key = "timeout_keep_alive_recheck",
  labelRes = R.string.settings_timeout_keep_alive_recheck_label,
  descriptionRes = R.string.settings_timeout_keep_alive_recheck_desc,
  card = CardId.ADVANCED_SETTINGS,
  defaultValue = 30L,
  defaultUnit = "seconds",
  prefsKey = "timeout_keep_alive_recheck_seconds",
  unitOptions = listOf("seconds", "minutes"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "minutes" -> value * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 60 == 0L -> Pair(base / 60, "minutes")
      else -> Pair(base, "seconds")
    }
  },
  min = 10,
  max = 300,
  baseUnitLabel = "seconds",
  read = { it.getTimeoutKeepAliveRecheckSeconds() },
  write = { repo, v -> repo.setTimeoutKeepAliveRecheckSeconds(v) },
)

val TIMEOUT_CLEANUP_AWAIT = SettingDef.NumericWithUnit(
  key = "timeout_cleanup_await",
  labelRes = R.string.settings_timeout_cleanup_await_label,
  descriptionRes = R.string.settings_timeout_cleanup_await_desc,
  card = CardId.ADVANCED_SETTINGS,
  defaultValue = 15L,
  defaultUnit = "seconds",
  prefsKey = "timeout_cleanup_await_seconds",
  unitOptions = listOf("seconds", "minutes"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "minutes" -> value * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 60 == 0L -> Pair(base / 60, "minutes")
      else -> Pair(base, "seconds")
    }
  },
  min = 5,
  max = 120,
  baseUnitLabel = "seconds",
  read = { it.getTimeoutCleanupAwait() },
  write = { repo, v -> repo.setTimeoutCleanupAwait(v) },
)
