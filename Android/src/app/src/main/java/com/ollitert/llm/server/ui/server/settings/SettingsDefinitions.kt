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

package com.ollitert.llm.server.ui.server.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.DEFAULT_PORT
import com.ollitert.llm.server.data.DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.MAX_VALID_PORT
import com.ollitert.llm.server.data.MIN_VALID_PORT

// ─── General Card ───────────────────────────────────────────────────

val KEEP_SCREEN_AWAKE = SettingDef.Toggle(
  key = "keep_screen_awake",
  labelRes = R.string.settings_keep_screen_awake,
  descriptionRes = R.string.settings_keep_screen_awake_desc,
  card = CardId.GENERAL,
  default = true,
  resetDefault = false,
  prefsKey = "keep_screen_on",
  read = { ServerPrefs.isKeepScreenOn(it) },
  write = { ctx, v -> ServerPrefs.setKeepScreenOn(ctx, v) },
)

val AUTO_EXPAND_LOGS = SettingDef.Toggle(
  key = "auto_expand_logs",
  labelRes = R.string.settings_auto_expand_logs,
  descriptionRes = R.string.settings_auto_expand_logs_desc,
  card = CardId.GENERAL,
  default = false,
  prefsKey = "auto_expand_logs",
  read = { ServerPrefs.isAutoExpandLogs(it) },
  write = { ctx, v -> ServerPrefs.setAutoExpandLogs(ctx, v) },
)

val WRAP_LOG_TEXT = SettingDef.Toggle(
  key = "wrap_log_text",
  labelRes = R.string.settings_wrap_log_text,
  descriptionRes = R.string.settings_wrap_log_text_desc,
  card = CardId.GENERAL,
  default = true,
  prefsKey = "wrap_log_text",
  read = { ServerPrefs.isWrapLogText(it) },
  write = { ctx, v -> ServerPrefs.setWrapLogText(ctx, v) },
)

val STREAM_RESPONSE_PREVIEW = SettingDef.Toggle(
  key = "stream_response_preview",
  labelRes = R.string.settings_stream_response_preview,
  descriptionRes = R.string.settings_stream_response_preview_desc,
  card = CardId.GENERAL,
  default = true,
  prefsKey = "stream_logs_preview",
  read = { ServerPrefs.isStreamLogsPreview(it) },
  write = { ctx, v -> ServerPrefs.setStreamLogsPreview(ctx, v) },
)

val COMPACT_IMAGE_DATA = SettingDef.Toggle(
  key = "compact_image_data",
  labelRes = R.string.settings_compact_image_data,
  descriptionRes = R.string.settings_compact_image_data_desc,
  card = CardId.GENERAL,
  default = true,
  prefsKey = "compact_image_data",
  read = { ServerPrefs.isCompactImageData(it) },
  write = { ctx, v -> ServerPrefs.setCompactImageData(ctx, v) },
)

val RESOLVE_CLIENT_HOSTNAMES = SettingDef.Toggle(
  key = "resolve_client_hostnames",
  labelRes = R.string.settings_resolve_client_hostnames,
  descriptionRes = R.string.settings_resolve_client_hostnames_desc,
  card = CardId.GENERAL,
  default = false,
  prefsKey = "resolve_client_hostnames",
  read = { ServerPrefs.isResolveClientHostnames(it) },
  write = { ctx, v -> ServerPrefs.setResolveClientHostnames(ctx, v) },
)

val HIDE_HEALTH_LOGS = SettingDef.Toggle(
  key = "hide_health_logs",
  labelRes = R.string.settings_hide_health_logs,
  descriptionRes = R.string.settings_hide_health_logs_desc,
  card = CardId.GENERAL,
  default = false,
  prefsKey = "hide_health_logs",
  read = { ServerPrefs.isHideHealthLogs(it) },
  write = { ctx, v -> ServerPrefs.setHideHealthLogs(ctx, v) },
)

val CLEAR_LOGS_ON_STOP = SettingDef.Toggle(
  key = "clear_logs_on_stop",
  labelRes = R.string.settings_clear_logs_on_stop,
  descriptionRes = R.string.settings_clear_logs_on_stop_desc,
  card = CardId.GENERAL,
  default = false,
  prefsKey = "clear_logs_on_stop",
  read = { ServerPrefs.isClearLogsOnStop(it) },
  write = { ctx, v -> ServerPrefs.setClearLogsOnStop(ctx, v) },
)

val CONFIRM_CLEAR_LOGS = SettingDef.Toggle(
  key = "confirm_clear_logs",
  labelRes = R.string.settings_confirm_clear_logs,
  descriptionRes = R.string.settings_confirm_clear_logs_desc,
  card = CardId.GENERAL,
  default = true,
  prefsKey = "confirm_clear_logs",
  read = { ServerPrefs.isConfirmClearLogs(it) },
  write = { ctx, v -> ServerPrefs.setConfirmClearLogs(ctx, v) },
)

val KEEP_PARTIAL_RESPONSE = SettingDef.Toggle(
  key = "keep_partial_response",
  labelRes = R.string.settings_keep_partial_response,
  descriptionRes = R.string.settings_keep_partial_response_desc,
  card = CardId.GENERAL,
  default = false,
  prefsKey = "keep_partial_response",
  read = { ServerPrefs.isKeepPartialResponse(it) },
  write = { ctx, v -> ServerPrefs.setKeepPartialResponse(ctx, v) },
)

val REJECT_WHEN_BUSY = SettingDef.Toggle(
  key = "reject_when_busy",
  labelRes = R.string.settings_reject_when_busy,
  descriptionRes = R.string.settings_reject_when_busy_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = false,
  prefsKey = "reject_when_busy",
  read = { ServerPrefs.isRejectWhenBusy(it) },
  write = { ctx, v -> ServerPrefs.setRejectWhenBusy(ctx, v) },
)

// ─── HF Token Card ────────────────────────────────────────────────────

val HF_TOKEN = SettingDef.TextInput(
  key = "hf_token",
  labelRes = R.string.settings_card_hf_token,
  descriptionRes = R.string.settings_hf_token_desc,
  card = CardId.HF_TOKEN,
  default = "",
  prefsKey = "hf_token",
  isPassword = true,
  read = { ServerPrefs.getHfToken(it) },
  write = { ctx, v -> ServerPrefs.setHfToken(ctx, v) },
)

// ─── Server Configuration Card ────────────────────────────────────

val HOST_PORT = SettingDef.NumericInput(
  key = "host_port",
  labelRes = R.string.settings_host_port_label,
  descriptionRes = R.string.settings_host_port_desc,
  card = CardId.SERVER_CONFIG,
  default = DEFAULT_PORT,
  prefsKey = "port",
  min = MIN_VALID_PORT,
  max = MAX_VALID_PORT,
  read = { ServerPrefs.getPort(it) },
  write = { ctx, v -> ServerPrefs.save(ctx, v) },
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
  read = { ServerPrefs.getCorsAllowedOrigins(it) },
  write = { ctx, v -> ServerPrefs.setCorsAllowedOrigins(ctx, v) },
)

val NOTIF_REQUEST_COUNT = SettingDef.Toggle(
  key = "notif_request_count",
  labelRes = R.string.settings_notif_request_count,
  descriptionRes = R.string.settings_notif_request_count_desc,
  card = CardId.METRICS,
  default = false,
  prefsKey = "notif_show_request_count",
  read = { ServerPrefs.isNotifShowRequestCount(it) },
  write = { ctx, v -> ServerPrefs.setNotifShowRequestCount(ctx, v) },
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
  read = { ServerPrefs.getDefaultModelName(it) },
  write = { ctx, v -> ServerPrefs.setDefaultModelName(ctx, v) },
)

val START_ON_BOOT = SettingDef.Toggle(
  key = "start_on_boot",
  labelRes = R.string.settings_start_on_boot,
  descriptionRes = R.string.settings_start_on_boot_desc,
  card = CardId.AUTO_LAUNCH,
  default = false,
  prefsKey = "auto_start_on_boot",
  read = { ServerPrefs.isAutoStartOnBoot(it) },
  write = { ctx, v -> ServerPrefs.setAutoStartOnBoot(ctx, v) },
)

val KEEP_ALIVE = SettingDef.Toggle(
  key = "keep_alive",
  labelRes = R.string.settings_keep_alive,
  descriptionRes = R.string.settings_keep_alive_desc,
  card = CardId.AUTO_LAUNCH,
  default = false,
  prefsKey = "keep_alive_enabled",
  read = { ServerPrefs.isKeepAliveEnabled(it) },
  write = { ctx, v -> ServerPrefs.setKeepAliveEnabled(ctx, v) },
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
  read = { ServerPrefs.getKeepAliveMinutes(it).toLong() },
  write = { ctx, v -> ServerPrefs.setKeepAliveMinutes(ctx, v.toInt()) },
)

val DONTKILLMYAPP = SettingDef.Custom(
  key = "dontkillmyapp",
  labelRes = R.string.settings_dontkillmyapp_title,
  descriptionRes = R.string.settings_dontkillmyapp_desc,
  card = CardId.AUTO_LAUNCH,
)

// ─── Updates Card ─────────────────────────────────────────────

val AUTO_UPDATE_CHECK = SettingDef.Toggle(
  key = "auto_update_check",
  labelRes = R.string.settings_auto_update_check,
  descriptionRes = R.string.settings_auto_update_check_desc,
  card = CardId.UPDATES,
  default = true,
  prefsKey = "update_check_enabled",
  read = { ServerPrefs.isUpdateCheckEnabled(it) },
  write = { ctx, v -> ServerPrefs.setUpdateCheckEnabled(ctx, v) },
)

val CHECK_FREQUENCY = SettingDef.NumericWithUnit(
  key = "check_frequency",
  labelRes = R.string.settings_check_frequency_label,
  descriptionRes = R.string.settings_check_frequency_desc,
  card = CardId.UPDATES,
  defaultValue = 24L,
  defaultUnit = "hours",
  prefsKey = "update_check_interval_hours",
  unitOptions = listOf("hours", "days"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "days" -> value * 24
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base > 0 && base % 24 == 0L -> Pair(base / 24, "days")
      else -> Pair(base, "hours")
    }
  },
  min = 1,
  max = 720,
  baseUnitLabel = "hours",
  read = { ServerPrefs.getUpdateCheckIntervalHours(it).toLong() },
  write = { ctx, v -> ServerPrefs.setUpdateCheckIntervalHours(ctx, v.toInt()) },
)

val CHECK_FOR_UPDATES = SettingDef.Custom(
  key = "check_for_updates",
  labelRes = R.string.settings_check_for_updates,
  descriptionRes = R.string.settings_check_for_updates_desc,
  card = CardId.UPDATES,
)

val CROSS_CHANNEL_NOTIFY = SettingDef.Toggle(
  key = "cross_channel_notify",
  labelRes = R.string.settings_cross_channel_notify,
  descriptionRes = R.string.settings_cross_channel_notify_desc,
  card = CardId.UPDATES,
  default = false,
  prefsKey = "cross_channel_notify_enabled",
  read = { ServerPrefs.isCrossChannelNotifyEnabled(it) },
  write = { ctx, v -> ServerPrefs.setCrossChannelNotifyEnabled(ctx, v) },
)

val NOTIFICATION_SETTINGS = SettingDef.Custom(
  key = "notification_settings",
  labelRes = R.string.settings_notification_settings,
  descriptionRes = R.string.settings_notification_settings_desc,
  card = CardId.UPDATES,
)

// ─── Metrics Card ─────────────────────────────────────────────────

val SHOW_REQUEST_TYPES = SettingDef.Toggle(
  key = "show_request_types",
  labelRes = R.string.settings_show_request_types,
  descriptionRes = R.string.settings_show_request_types_desc,
  card = CardId.METRICS,
  default = false,
  prefsKey = "show_request_types",
  read = { ServerPrefs.isShowRequestTypes(it) },
  write = { ctx, v -> ServerPrefs.setShowRequestTypes(ctx, v) },
)

val SHOW_ADVANCED_METRICS = SettingDef.Toggle(
  key = "show_advanced_metrics",
  labelRes = R.string.settings_show_advanced_metrics,
  descriptionRes = R.string.settings_show_advanced_metrics_desc,
  card = CardId.METRICS,
  default = false,
  prefsKey = "show_advanced_metrics",
  read = { ServerPrefs.isShowAdvancedMetrics(it) },
  write = { ctx, v -> ServerPrefs.setShowAdvancedMetrics(ctx, v) },
)

// ─── Log Persistence Card ─────────────────────────────────────────

val LOG_PERSISTENCE_ENABLED = SettingDef.Toggle(
  key = "log_persistence_enabled",
  labelRes = R.string.settings_persist_logs,
  descriptionRes = R.string.settings_persist_logs_desc,
  card = CardId.LOG_PERSISTENCE,
  default = false,
  prefsKey = "log_persistence_enabled",
  read = { ServerPrefs.isLogPersistenceEnabled(it) },
  write = { ctx, v -> ServerPrefs.setLogPersistenceEnabled(ctx, v) },
)

val LOG_MAX_ENTRIES = SettingDef.NumericPlain(
  key = "log_max_entries",
  labelRes = R.string.settings_max_log_entries_label,
  descriptionRes = R.string.settings_max_log_entries_desc,
  card = CardId.LOG_PERSISTENCE,
  default = 500,
  prefsKey = "log_max_entries",
  min = 0,
  max = 10000,
  read = { ServerPrefs.getLogMaxEntries(it) },
  write = { ctx, v -> ServerPrefs.setLogMaxEntries(ctx, v) },
)

val LOG_AUTO_DELETE = SettingDef.NumericWithUnit(
  key = "log_auto_delete",
  labelRes = R.string.settings_auto_delete_label,
  descriptionRes = R.string.settings_auto_delete_desc,
  card = CardId.LOG_PERSISTENCE,
  defaultValue = 10080L,
  defaultUnit = "days",
  prefsKey = "log_auto_delete_minutes",
  unitOptions = listOf("minutes", "hours", "days"),
  toBaseUnit = { value, unit ->
    when (unit) {
      "hours" -> value * 60
      "days" -> value * 24 * 60
      else -> value
    }
  },
  fromBaseUnit = { base ->
    when {
      base == 0L -> Pair(0L, "minutes")
      base % (24 * 60) == 0L -> Pair(base / (24 * 60), "days")
      base % 60 == 0L -> Pair(base / 60, "hours")
      else -> Pair(base, "minutes")
    }
  },
  min = 0,
  max = 525600,
  baseUnitLabel = "minutes",
  read = { ServerPrefs.getLogAutoDeleteMinutes(it) },
  write = { ctx, v -> ServerPrefs.setLogAutoDeleteMinutes(ctx, v) },
)

val CLEAR_ALL_LOGS = SettingDef.Custom(
  key = "clear_all_logs",
  labelRes = R.string.settings_clear_all_logs_button,
  descriptionRes = R.string.settings_clear_all_logs_desc,
  card = CardId.LOG_PERSISTENCE,
)

// ─── Home Assistant Card ──────────────────────────────────────────────

val HA_INTEGRATION = SettingDef.Custom(
  key = "ha_integration",
  labelRes = R.string.settings_ha_rest_api,
  descriptionRes = R.string.settings_ha_rest_api_desc,
  card = CardId.HOME_ASSISTANT,
)

val STT_TRANSCRIPTION_PROMPT = SettingDef.Toggle(
  key = "stt_transcription_prompt",
  labelRes = R.string.settings_stt_transcription_prompt,
  descriptionRes = R.string.settings_stt_transcription_prompt_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = true,
  prefsKey = "stt_transcription_prompt",
  read = { ServerPrefs.isSttTranscriptionPromptEnabled(it) },
  write = { ctx, v -> ServerPrefs.setSttTranscriptionPromptEnabled(ctx, v) },
)

val STT_TRANSCRIPTION_PROMPT_TEXT = SettingDef.TextInput(
  key = "stt_transcription_prompt_text",
  labelRes = R.string.settings_stt_transcription_prompt_text,
  descriptionRes = R.string.settings_stt_transcription_prompt_text_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT,
  prefsKey = "stt_transcription_prompt_text",
  read = { ServerPrefs.getSttTranscriptionPromptText(it).ifBlank { DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT } },
  write = { ctx, v ->
    ServerPrefs.setSttTranscriptionPromptText(
      ctx, v.ifBlank { DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT },
    )
  },
)

// ─── Context Management Card ───────────────────────────────────────────────

val TRUNCATE_HISTORY = SettingDef.Toggle(
  key = "truncate_history",
  labelRes = R.string.settings_truncate_history,
  descriptionRes = R.string.settings_truncate_history_desc,
  card = CardId.CONTEXT_MANAGEMENT,
  default = false,
  resetDefault = true,
  prefsKey = "auto_truncate_history",
  read = { ServerPrefs.isAutoTruncateHistory(it) },
  write = { ctx, v -> ServerPrefs.setAutoTruncateHistory(ctx, v) },
)

val COMPACT_TOOL_SCHEMAS = SettingDef.Toggle(
  key = "compact_tool_schemas",
  labelRes = R.string.settings_compact_tool_schemas,
  descriptionRes = R.string.settings_compact_tool_schemas_desc,
  card = CardId.CONTEXT_MANAGEMENT,
  default = false,
  resetDefault = true,
  prefsKey = "compact_tool_schemas",
  read = { ServerPrefs.isCompactToolSchemas(it) },
  write = { ctx, v -> ServerPrefs.setCompactToolSchemas(ctx, v) },
)

val TRIM_PROMPT = SettingDef.Toggle(
  key = "trim_prompt",
  labelRes = R.string.settings_trim_prompt,
  descriptionRes = R.string.settings_trim_prompt_desc,
  card = CardId.CONTEXT_MANAGEMENT,
  default = false,
  prefsKey = "auto_trim_prompts",
  read = { ServerPrefs.isAutoTrimPrompts(it) },
  write = { ctx, v -> ServerPrefs.setAutoTrimPrompts(ctx, v) },
)

// ─── Model Behaviour Card ─────────────────────────────────────────────────

val WARMUP_MESSAGE = SettingDef.Toggle(
  key = "warmup_message",
  labelRes = R.string.settings_warmup_message,
  descriptionRes = R.string.settings_warmup_message_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = true,
  prefsKey = "warmup_enabled",
  read = { ServerPrefs.isWarmupEnabled(it) },
  write = { ctx, v -> ServerPrefs.setWarmupEnabled(ctx, v) },
)

val PRE_INIT_VISION = SettingDef.Toggle(
  key = "pre_init_vision",
  labelRes = R.string.settings_pre_init_vision,
  descriptionRes = R.string.settings_pre_init_vision_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = false,
  prefsKey = "eager_vision_init",
  requiresRestart = true,
  read = { ServerPrefs.isEagerVisionInit(it) },
  write = { ctx, v -> ServerPrefs.setEagerVisionInit(ctx, v) },
)

val CUSTOM_PROMPTS = SettingDef.Toggle(
  key = "custom_prompts",
  labelRes = R.string.settings_custom_prompts,
  descriptionRes = R.string.settings_custom_prompts_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = false,
  prefsKey = "custom_prompts_enabled",
  read = { ServerPrefs.isCustomPromptsEnabled(it) },
  write = { ctx, v -> ServerPrefs.setCustomPromptsEnabled(ctx, v) },
)

val IGNORE_CLIENT_PARAMS = SettingDef.Toggle(
  key = "ignore_client_params",
  labelRes = R.string.settings_ignore_client_params,
  descriptionRes = R.string.settings_ignore_client_params_desc,
  card = CardId.MODEL_BEHAVIOUR,
  default = false,
  prefsKey = "ignore_client_sampler_params",
  read = { ServerPrefs.isIgnoreClientSamplerParams(it) },
  write = { ctx, v -> ServerPrefs.setIgnoreClientSamplerParams(ctx, v) },
)

val SHOW_MODEL_RECOMMENDATIONS = SettingDef.Toggle(
  key = "show_model_recommendations",
  labelRes = R.string.settings_show_model_recommendations,
  descriptionRes = R.string.settings_show_model_recommendations_desc,
  card = CardId.GENERAL,
  default = true,
  prefsKey = "show_model_recommendations",
  read = { ServerPrefs.isShowModelRecommendations(it) },
  write = { ctx, v -> ServerPrefs.setShowModelRecommendations(ctx, v) },
)

// ─── Developer Card ───────────────────────────────────────────────

val VERBOSE_DEBUG = SettingDef.Toggle(
  key = "verbose_debug",
  labelRes = R.string.settings_verbose_debug,
  descriptionRes = R.string.settings_verbose_debug_desc,
  card = CardId.DEVELOPER,
  default = false,
  prefsKey = "verbose_debug_enabled",
  read = { ServerPrefs.isVerboseDebugEnabled(it) },
  write = { ctx, v -> ServerPrefs.setVerboseDebugEnabled(ctx, v) },
)

val EXPORT_LOGCAT = SettingDef.Custom(
  key = "export_logcat",
  labelRes = R.string.settings_export_logcat,
  descriptionRes = R.string.settings_export_logcat_desc,
  card = CardId.DEVELOPER,
)

// ─── Reset Section ────────────────────────────────────────────────────

val RESET_TO_DEFAULTS = SettingDef.Custom(
  key = "reset",
  labelRes = R.string.settings_reset_to_defaults,
  descriptionRes = R.string.settings_reset_to_defaults,
  card = CardId.RESET,
)

val REPOSITORIES_NAV = SettingDef.Custom(
  key = "repositories_nav",
  labelRes = R.string.settings_card_repositories,
  descriptionRes = R.string.settings_repositories_description,
  card = CardId.REPOSITORIES,
)

// ─── All Setting Definitions (ordered) ──────────────────────────────────────
// Order must match: CardId enum, allCardDefs, and SettingsScreen.kt rendering.

val allSettingDefs: List<SettingDef> = listOf(
  // Repositories
  REPOSITORIES_NAV,
  // HF Token
  HF_TOKEN,
  // General
  KEEP_SCREEN_AWAKE, SHOW_MODEL_RECOMMENDATIONS, RESOLVE_CLIENT_HOSTNAMES,
  WRAP_LOG_TEXT, AUTO_EXPAND_LOGS, STREAM_RESPONSE_PREVIEW, KEEP_PARTIAL_RESPONSE, COMPACT_IMAGE_DATA,
  HIDE_HEALTH_LOGS, CLEAR_LOGS_ON_STOP, CONFIRM_CLEAR_LOGS,
  // Server Config
  HOST_PORT, BEARER_TOKEN, CORS_ORIGINS,
  // Auto-Launch
  DEFAULT_MODEL, START_ON_BOOT, KEEP_ALIVE, KEEP_ALIVE_TIMEOUT, DONTKILLMYAPP,
  // Model Behaviour
  CUSTOM_PROMPTS, REJECT_WHEN_BUSY, WARMUP_MESSAGE, PRE_INIT_VISION, IGNORE_CLIENT_PARAMS,
  STT_TRANSCRIPTION_PROMPT, STT_TRANSCRIPTION_PROMPT_TEXT,
  // Context Management
  TRUNCATE_HISTORY, COMPACT_TOOL_SCHEMAS, TRIM_PROMPT,
  // Metrics
  SHOW_REQUEST_TYPES, SHOW_ADVANCED_METRICS, NOTIF_REQUEST_COUNT,
  // Log Persistence
  LOG_PERSISTENCE_ENABLED, LOG_MAX_ENTRIES, LOG_AUTO_DELETE, CLEAR_ALL_LOGS,
  // Home Assistant
  HA_INTEGRATION,
  // Updates
  AUTO_UPDATE_CHECK, CHECK_FREQUENCY, CROSS_CHANNEL_NOTIFY, CHECK_FOR_UPDATES, NOTIFICATION_SETTINGS,
  // Developer
  VERBOSE_DEBUG, EXPORT_LOGCAT,
  // Reset
  RESET_TO_DEFAULTS,
)

/** Lookup table: setting key → SettingDef. */
val settingDefsByKey: Map<String, SettingDef> = allSettingDefs.associateBy { it.key }

// ─── Card Definitions ───────────────────────────────────────────────────────
// Order must match: CardId enum, allSettingDefs sections, and SettingsScreen.kt rendering.

val allCardDefs: List<CardDef> = listOf(
  CardDef(
    id = CardId.REPOSITORIES,
    titleRes = R.string.settings_card_repositories,
    icon = CardIcon.Vector(Icons.Outlined.Inventory2),
    settings = listOf(REPOSITORIES_NAV),
  ),
  CardDef(
    id = CardId.HF_TOKEN,
    titleRes = R.string.settings_card_hf_token,
    icon = CardIcon.Vector(Icons.Outlined.Key),
    settings = listOf(HF_TOKEN),
  ),
  CardDef(
    id = CardId.GENERAL,
    titleRes = R.string.settings_card_general,
    icon = CardIcon.Vector(Icons.Outlined.PhoneAndroid),
    settings = listOf(
      KEEP_SCREEN_AWAKE, SHOW_MODEL_RECOMMENDATIONS, RESOLVE_CLIENT_HOSTNAMES,
      WRAP_LOG_TEXT, AUTO_EXPAND_LOGS, STREAM_RESPONSE_PREVIEW, KEEP_PARTIAL_RESPONSE, COMPACT_IMAGE_DATA,
      HIDE_HEALTH_LOGS, CLEAR_LOGS_ON_STOP, CONFIRM_CLEAR_LOGS,
    ),
  ),
  CardDef(
    id = CardId.SERVER_CONFIG,
    titleRes = R.string.settings_card_server_config,
    icon = CardIcon.Vector(Icons.Outlined.Tune),
    settings = listOf(HOST_PORT, BEARER_TOKEN, CORS_ORIGINS),
  ),
  CardDef(
    id = CardId.AUTO_LAUNCH,
    titleRes = R.string.settings_card_auto_launch,
    icon = CardIcon.Vector(Icons.Outlined.PlayArrow),
    settings = listOf(
      DEFAULT_MODEL, START_ON_BOOT, KEEP_ALIVE, KEEP_ALIVE_TIMEOUT, DONTKILLMYAPP,
    ),
  ),
  CardDef(
    id = CardId.MODEL_BEHAVIOUR,
    titleRes = R.string.settings_card_model_behaviour,
    icon = CardIcon.Vector(Icons.Outlined.Token),
    settings = listOf(
      CUSTOM_PROMPTS, REJECT_WHEN_BUSY, WARMUP_MESSAGE, PRE_INIT_VISION, IGNORE_CLIENT_PARAMS,
      STT_TRANSCRIPTION_PROMPT,
    ),
  ),
  CardDef(
    id = CardId.CONTEXT_MANAGEMENT,
    titleRes = R.string.settings_card_context_management,
    icon = CardIcon.Vector(Icons.Outlined.Compress),
    settings = listOf(TRUNCATE_HISTORY, COMPACT_TOOL_SCHEMAS, TRIM_PROMPT),
  ),
  CardDef(
    id = CardId.METRICS,
    titleRes = R.string.settings_card_metrics,
    icon = CardIcon.Vector(Icons.Outlined.BarChart),
    settings = listOf(SHOW_REQUEST_TYPES, SHOW_ADVANCED_METRICS, NOTIF_REQUEST_COUNT),
  ),
  CardDef(
    id = CardId.LOG_PERSISTENCE,
    titleRes = R.string.settings_card_log_persistence,
    icon = CardIcon.Vector(Icons.Outlined.Storage),
    settings = listOf(LOG_PERSISTENCE_ENABLED, LOG_MAX_ENTRIES, LOG_AUTO_DELETE, CLEAR_ALL_LOGS),
  ),
  CardDef(
    id = CardId.HOME_ASSISTANT,
    titleRes = R.string.settings_card_home_assistant,
    icon = CardIcon.Resource(R.drawable.ic_home_assistant),
    settings = listOf(HA_INTEGRATION),
  ),
  CardDef(
    id = CardId.UPDATES,
    titleRes = R.string.settings_card_updates,
    icon = CardIcon.Vector(Icons.Outlined.SystemUpdate),
    settings = listOf(AUTO_UPDATE_CHECK, CHECK_FREQUENCY, CROSS_CHANNEL_NOTIFY, CHECK_FOR_UPDATES, NOTIFICATION_SETTINGS),
  ),
  CardDef(
    id = CardId.DEVELOPER,
    titleRes = R.string.settings_card_developer,
    icon = CardIcon.Vector(Icons.Outlined.BugReport),
    settings = listOf(VERBOSE_DEBUG, EXPORT_LOGCAT),
  ),
  CardDef(
    id = CardId.RESET,
    titleRes = R.string.settings_reset_to_defaults,
    icon = CardIcon.Vector(Icons.Outlined.RestartAlt),
    settings = listOf(RESET_TO_DEFAULTS),
  ),
)

/** Lookup table: CardId → CardDef. */
val cardDefsById: Map<CardId, CardDef> = allCardDefs.associateBy { it.id }
