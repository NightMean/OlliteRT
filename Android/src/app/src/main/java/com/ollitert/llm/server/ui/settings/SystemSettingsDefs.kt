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
import com.ollitert.llm.server.data.prefs.ServerPrefs

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

// ─── Metrics Card ─────────────────────────────────────────────────

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

val FORCE_STREAM_USAGE = SettingDef.Toggle(
  key = "force_stream_usage",
  labelRes = R.string.settings_force_stream_usage,
  descriptionRes = R.string.settings_force_stream_usage_desc,
  card = CardId.METRICS,
  default = true,
  prefsKey = "force_stream_usage",
  read = { ServerPrefs.isForceStreamUsage(it) },
  write = { ctx, v -> ServerPrefs.setForceStreamUsage(ctx, v) },
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
