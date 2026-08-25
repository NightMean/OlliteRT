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

// -- Keys: boot & lifecycle -------------------------------------------------
private const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
private const val KEY_CLEAR_LOGS_ON_STOP = "clear_logs_on_stop"
private const val KEY_CONFIRM_CLEAR_LOGS = "confirm_clear_logs"

// -- Keys: developer / debug ------------------------------------------------
private const val KEY_VERBOSE_DEBUG_ENABLED = "verbose_debug_enabled"
private const val KEY_IGNORE_CLIENT_SAMPLER_PARAMS = "ignore_client_sampler_params"

// -- Keys: request queueing -------------------------------------------------
private const val KEY_REJECT_WHEN_BUSY = "reject_when_busy"

// -- Keys: GPU availability -------------------------------------------------
private const val KEY_GPU_UNAVAILABLE_DIALOG_SHOWN = "gpu_unavailable_dialog_shown"
private const val KEY_GPU_UNAVAILABLE_SERVER_START_DISMISSED = "gpu_unavailable_server_start_dismissed"

// -- Pref declarations -------------------------------------------------------

internal val AUTO_START_ON_BOOT = BoolPref(KEY_AUTO_START_ON_BOOT, false)
internal val CLEAR_LOGS_ON_STOP = BoolPref(KEY_CLEAR_LOGS_ON_STOP, false)
internal val CONFIRM_CLEAR_LOGS = BoolPref(KEY_CONFIRM_CLEAR_LOGS, true)
internal val VERBOSE_DEBUG_ENABLED = BoolPref(KEY_VERBOSE_DEBUG_ENABLED, false)
internal val IGNORE_CLIENT_SAMPLER_PARAMS = BoolPref(KEY_IGNORE_CLIENT_SAMPLER_PARAMS, false)
internal val REJECT_WHEN_BUSY = BoolPref(KEY_REJECT_WHEN_BUSY, false)
internal val GPU_UNAVAILABLE_DIALOG_SHOWN = BoolPref(KEY_GPU_UNAVAILABLE_DIALOG_SHOWN, false)
internal val GPU_UNAVAILABLE_SERVER_START_DISMISSED = BoolPref(KEY_GPU_UNAVAILABLE_SERVER_START_DISMISSED, false)

  // ══════════════════════════════════════════════════════════════════════════
  // § Boot & Lifecycle
  // ══════════════════════════════════════════════════════════════════════════

fun ServerPrefs.isAutoStartOnBoot(context: Context): Boolean = get(context, AUTO_START_ON_BOOT)
fun ServerPrefs.setAutoStartOnBoot(context: Context, enabled: Boolean) = set(context, AUTO_START_ON_BOOT, enabled)

fun ServerPrefs.isClearLogsOnStop(context: Context): Boolean = get(context, CLEAR_LOGS_ON_STOP)
fun ServerPrefs.setClearLogsOnStop(context: Context, enabled: Boolean) = set(context, CLEAR_LOGS_ON_STOP, enabled)

fun ServerPrefs.isConfirmClearLogs(context: Context): Boolean = get(context, CONFIRM_CLEAR_LOGS)
fun ServerPrefs.setConfirmClearLogs(context: Context, enabled: Boolean) = set(context, CONFIRM_CLEAR_LOGS, enabled)

  // ══════════════════════════════════════════════════════════════════════════
  // § Developer / Debug
  // ══════════════════════════════════════════════════════════════════════════

fun ServerPrefs.isVerboseDebugEnabled(context: Context): Boolean = get(context, VERBOSE_DEBUG_ENABLED)
fun ServerPrefs.setVerboseDebugEnabled(context: Context, enabled: Boolean) = set(context, VERBOSE_DEBUG_ENABLED, enabled)

fun ServerPrefs.isIgnoreClientSamplerParams(context: Context): Boolean = get(context, IGNORE_CLIENT_SAMPLER_PARAMS)
fun ServerPrefs.setIgnoreClientSamplerParams(context: Context, enabled: Boolean) = set(context, IGNORE_CLIENT_SAMPLER_PARAMS, enabled)

  // ══════════════════════════════════════════════════════════════════════════
  // § Request Queueing
  // ══════════════════════════════════════════════════════════════════════════

fun ServerPrefs.isRejectWhenBusy(context: Context): Boolean = get(context, REJECT_WHEN_BUSY)
fun ServerPrefs.setRejectWhenBusy(context: Context, enabled: Boolean) = set(context, REJECT_WHEN_BUSY, enabled)

  // ══════════════════════════════════════════════════════════════════════════
  // § GPU Availability
  // ══════════════════════════════════════════════════════════════════════════

fun ServerPrefs.isGpuUnavailableDialogShown(context: Context): Boolean = get(context, GPU_UNAVAILABLE_DIALOG_SHOWN)
fun ServerPrefs.setGpuUnavailableDialogShown(context: Context, shown: Boolean) = set(context, GPU_UNAVAILABLE_DIALOG_SHOWN, shown)

fun ServerPrefs.isGpuUnavailableServerStartDismissed(context: Context): Boolean = get(context, GPU_UNAVAILABLE_SERVER_START_DISMISSED)
fun ServerPrefs.setGpuUnavailableServerStartDismissed(context: Context, dismissed: Boolean) = set(context, GPU_UNAVAILABLE_SERVER_START_DISMISSED, dismissed)
