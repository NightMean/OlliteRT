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

package com.ollitert.llm.server.floatingmonitor

import com.ollitert.llm.server.common.ModelLoadPhase
import com.ollitert.llm.server.common.ServerStatus
import kotlin.math.roundToLong

internal enum class FloatingMonitorDot { PROCESSING, RETRYING_CPU, STOPPED, LOADING, RUNNING, ERROR }

internal fun floatingMonitorDot(
  status: ServerStatus,
  isInferring: Boolean,
  modelLoadPhase: ModelLoadPhase,
): FloatingMonitorDot = when {
  status == ServerStatus.RUNNING && isInferring -> FloatingMonitorDot.PROCESSING
  status == ServerStatus.LOADING && modelLoadPhase == ModelLoadPhase.RETRYING_CPU -> FloatingMonitorDot.RETRYING_CPU
  status == ServerStatus.STOPPED -> FloatingMonitorDot.STOPPED
  status == ServerStatus.LOADING -> FloatingMonitorDot.LOADING
  status == ServerStatus.RUNNING -> FloatingMonitorDot.RUNNING
  else -> FloatingMonitorDot.ERROR
}

internal fun shouldShowFloatingMonitor(
  enabled: Boolean,
  hasOverlayPermission: Boolean,
  isAppInForeground: Boolean,
  status: ServerStatus,
): Boolean = enabled && hasOverlayPermission && !isAppInForeground &&
  (status == ServerStatus.LOADING || status == ServerStatus.RUNNING)

internal fun formatCompactUptime(startedAtMs: Long, nowMs: Long): String {
  if (startedAtMs <= 0L || nowMs < startedAtMs) return "—"
  val totalSeconds = ((nowMs - startedAtMs) / 1_000L).coerceAtLeast(0L)
  return when {
    totalSeconds < 60L -> "${totalSeconds}s"
    totalSeconds < 3_600L -> "${totalSeconds / 60L}m"
    totalSeconds < 86_400L -> "${totalSeconds / 3_600L}h"
    else -> "${totalSeconds / 86_400L}d"
  }
}

internal fun formatMonitorSpeed(tokensPerSecond: Double): String =
  if (tokensPerSecond <= 0.0) "—" else "${(tokensPerSecond * 10.0).roundToLong() / 10.0}"
