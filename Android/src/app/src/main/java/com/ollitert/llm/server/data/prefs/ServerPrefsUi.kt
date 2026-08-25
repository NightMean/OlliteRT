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

import android.content.Context
import androidx.core.content.edit

// -- Keys: UI preferences --------------------------------------------------
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

// -- Keys: log persistence --------------------------------------------------
private const val KEY_LOG_PERSISTENCE_ENABLED = "log_persistence_enabled"
private const val KEY_LOG_MAX_ENTRIES = "log_max_entries"
private const val KEY_LOG_AUTO_DELETE_MINUTES = "log_auto_delete_minutes"
private const val DEFAULT_LOG_PERSISTENCE_ENABLED = false
private const val DEFAULT_LOG_MAX_ENTRIES = 500
private const val DEFAULT_LOG_AUTO_DELETE_MINUTES = 7 * 24 * 60 // 7 days

// -- Pref declarations -------------------------------------------------------

internal val KEEP_SCREEN_ON = BoolPref(KEY_KEEP_SCREEN_ON, true)
internal val AUTO_EXPAND_LOGS = BoolPref(KEY_AUTO_EXPAND_LOGS, false)
internal val WRAP_LOG_TEXT = BoolPref(KEY_WRAP_LOG_TEXT, true)
internal val STREAM_LOGS_PREVIEW = BoolPref(KEY_STREAM_LOGS_PREVIEW, true)
internal val NOTIF_SHOW_REQUEST_COUNT = BoolPref(KEY_NOTIF_SHOW_REQUEST_COUNT, false)
internal val SHOW_REQUEST_TYPES = BoolPref(KEY_SHOW_REQUEST_TYPES, false)
internal val SHOW_ADVANCED_METRICS = BoolPref(KEY_SHOW_ADVANCED_METRICS, false)
internal val FORCE_STREAM_USAGE = BoolPref(KEY_FORCE_STREAM_USAGE, true)
internal val COMPACT_IMAGE_DATA = BoolPref(KEY_COMPACT_IMAGE_DATA, DEFAULT_COMPACT_IMAGE_DATA)
internal val RESOLVE_CLIENT_HOSTNAMES = BoolPref(KEY_RESOLVE_CLIENT_HOSTNAMES, DEFAULT_RESOLVE_CLIENT_HOSTNAMES)
internal val HIDE_HEALTH_LOGS = BoolPref(KEY_HIDE_HEALTH_LOGS, DEFAULT_HIDE_HEALTH_LOGS)
internal val LOG_PERSISTENCE_ENABLED = BoolPref(KEY_LOG_PERSISTENCE_ENABLED, DEFAULT_LOG_PERSISTENCE_ENABLED)
internal val LOG_MAX_ENTRIES = IntPref(KEY_LOG_MAX_ENTRIES, DEFAULT_LOG_MAX_ENTRIES)
internal val LOG_AUTO_DELETE_MINUTES = LongPref(KEY_LOG_AUTO_DELETE_MINUTES, DEFAULT_LOG_AUTO_DELETE_MINUTES.toLong())

  // ══════════════════════════════════════════════════════════════════════════
  // § UI Preferences
  // ══════════════════════════════════════════════════════════════════════════

fun ServerPrefs.isKeepScreenOn(context: Context): Boolean = get(context, KEEP_SCREEN_ON)
fun ServerPrefs.setKeepScreenOn(context: Context, enabled: Boolean) = set(context, KEEP_SCREEN_ON, enabled)

fun ServerPrefs.isAutoExpandLogs(context: Context): Boolean = get(context, AUTO_EXPAND_LOGS)
fun ServerPrefs.setAutoExpandLogs(context: Context, enabled: Boolean) = set(context, AUTO_EXPAND_LOGS, enabled)

fun ServerPrefs.isWrapLogText(context: Context): Boolean = get(context, WRAP_LOG_TEXT)
fun ServerPrefs.setWrapLogText(context: Context, enabled: Boolean) = set(context, WRAP_LOG_TEXT, enabled)

fun ServerPrefs.isStreamLogsPreview(context: Context): Boolean = get(context, STREAM_LOGS_PREVIEW)
fun ServerPrefs.setStreamLogsPreview(context: Context, enabled: Boolean) = set(context, STREAM_LOGS_PREVIEW, enabled)

fun ServerPrefs.isNotifShowRequestCount(context: Context): Boolean = get(context, NOTIF_SHOW_REQUEST_COUNT)
fun ServerPrefs.setNotifShowRequestCount(context: Context, enabled: Boolean) = set(context, NOTIF_SHOW_REQUEST_COUNT, enabled)

fun ServerPrefs.isShowRequestTypes(context: Context): Boolean = get(context, SHOW_REQUEST_TYPES)
fun ServerPrefs.setShowRequestTypes(context: Context, enabled: Boolean) = set(context, SHOW_REQUEST_TYPES, enabled)

fun ServerPrefs.isShowAdvancedMetrics(context: Context): Boolean = get(context, SHOW_ADVANCED_METRICS)
fun ServerPrefs.setShowAdvancedMetrics(context: Context, enabled: Boolean) = set(context, SHOW_ADVANCED_METRICS, enabled)

fun ServerPrefs.isForceStreamUsage(context: Context): Boolean = get(context, FORCE_STREAM_USAGE)
fun ServerPrefs.setForceStreamUsage(context: Context, enabled: Boolean) = set(context, FORCE_STREAM_USAGE, enabled)

fun ServerPrefs.isCompactImageData(context: Context): Boolean = get(context, COMPACT_IMAGE_DATA)
fun ServerPrefs.setCompactImageData(context: Context, enabled: Boolean) = set(context, COMPACT_IMAGE_DATA, enabled)

fun ServerPrefs.isResolveClientHostnames(context: Context): Boolean = get(context, RESOLVE_CLIENT_HOSTNAMES)
fun ServerPrefs.setResolveClientHostnames(context: Context, enabled: Boolean) = set(context, RESOLVE_CLIENT_HOSTNAMES, enabled)

fun ServerPrefs.isHideHealthLogs(context: Context): Boolean = get(context, HIDE_HEALTH_LOGS)
fun ServerPrefs.setHideHealthLogs(context: Context, enabled: Boolean) = set(context, HIDE_HEALTH_LOGS, enabled)

  // ══════════════════════════════════════════════════════════════════════════
  // § Log Persistence
  // ══════════════════════════════════════════════════════════════════════════

fun ServerPrefs.isLogPersistenceEnabled(context: Context): Boolean = get(context, LOG_PERSISTENCE_ENABLED)
fun ServerPrefs.setLogPersistenceEnabled(context: Context, enabled: Boolean) = set(context, LOG_PERSISTENCE_ENABLED, enabled)

fun ServerPrefs.getLogMaxEntries(context: Context): Int = get(context, LOG_MAX_ENTRIES)
fun ServerPrefs.setLogMaxEntries(context: Context, maxEntries: Int) = set(context, LOG_MAX_ENTRIES, maxEntries.coerceAtLeast(0))

fun ServerPrefs.getLogAutoDeleteMinutes(context: Context): Long = get(context, LOG_AUTO_DELETE_MINUTES)
fun ServerPrefs.setLogAutoDeleteMinutes(context: Context, minutes: Long) = set(context, LOG_AUTO_DELETE_MINUTES, minutes.coerceAtLeast(0L))
