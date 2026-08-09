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

package com.ollitert.llm.server.ui.floatingmonitor

import com.ollitert.llm.server.common.ServerStatus

private const val MAX_EXACT_COUNT = 99_999L
private const val MAX_EXACT_ELAPSED_SECONDS = 999L
private const val LAST_LATENCY_CAP_MS = 1_000_000L
private const val LAST_LATENCY_TENTHS_MAX_SECONDS = 99L

data class FloatingMonitorDurationText(
  val value: String,
  val inlineUnit: String?,
)

data class FloatingMonitorLatencyText(
  val value: String,
  val unit: String?,
  val inlineUnit: String? = unit,
)

data class FloatingMonitorRenderModel(
  val visualState: FloatingMonitorVisualState,
  val requestValue: String,
  val secondaryValue: String,
  val secondaryLabel: String,
  val secondaryUnit: String? = null,
  val lastLatency: FloatingMonitorLatencyText? = null,
)

fun formatFloatingMonitorCount(count: Long): String {
  val nonNegativeCount = count.coerceAtLeast(0)
  if (nonNegativeCount > MAX_EXACT_COUNT) return "99,999+"
  return nonNegativeCount
    .toString()
    .reversed()
    .chunked(3)
    .joinToString(",")
    .reversed()
}

fun formatProcessingElapsed(elapsedMillis: Long): FloatingMonitorDurationText {
  val nonNegativeMillis = elapsedMillis.coerceAtLeast(0)
  val totalSeconds = nonNegativeMillis / 1_000
  if (totalSeconds > MAX_EXACT_ELAPSED_SECONDS) {
    return FloatingMonitorDurationText(value = "999+", inlineUnit = null)
  }
  return FloatingMonitorDurationText(value = totalSeconds.toString(), inlineUnit = "s")
}

fun formatLastLatency(latencyMs: Long): FloatingMonitorLatencyText {
  val nonNegativeLatencyMs = latencyMs.coerceAtLeast(0)
  if (nonNegativeLatencyMs == 0L) {
    return FloatingMonitorLatencyText(value = "—", unit = null)
  }
  if (nonNegativeLatencyMs >= LAST_LATENCY_CAP_MS) {
    return FloatingMonitorLatencyText(value = "999+", unit = "s", inlineUnit = null)
  }

  val wholeSeconds = nonNegativeLatencyMs / 1_000L
  val tenths = (nonNegativeLatencyMs % 1_000L) / 100L
  if (wholeSeconds > LAST_LATENCY_TENTHS_MAX_SECONDS) {
    return FloatingMonitorLatencyText(value = wholeSeconds.toString(), unit = "s")
  }
  return FloatingMonitorLatencyText(value = "$wholeSeconds.$tenths", unit = "s")
}

fun deriveFloatingMonitorRenderModel(
  visualState: FloatingMonitorVisualState,
  requestCount: Long,
  errorCount: Long,
  processingElapsedMillis: Long?,
  lastLatencyMs: Long = 0L,
): FloatingMonitorRenderModel? {
  if (visualState == FloatingMonitorVisualState.Hidden) return null
  val processingElapsed = formatProcessingElapsed(processingElapsedMillis ?: 0)

  return FloatingMonitorRenderModel(
    visualState = visualState,
    requestValue = formatFloatingMonitorCount(requestCount),
    secondaryValue = when (visualState) {
      FloatingMonitorVisualState.Running -> formatFloatingMonitorCount(errorCount)
      FloatingMonitorVisualState.Processing -> processingElapsed.value
      FloatingMonitorVisualState.Hidden -> error("Hidden was handled above")
    },
    secondaryLabel = when (visualState) {
      FloatingMonitorVisualState.Running -> "err"
      FloatingMonitorVisualState.Processing -> "proc"
      FloatingMonitorVisualState.Hidden -> error("Hidden was handled above")
    },
    secondaryUnit = when (visualState) {
      FloatingMonitorVisualState.Running -> null
      FloatingMonitorVisualState.Processing -> processingElapsed.inlineUnit
      FloatingMonitorVisualState.Hidden -> error("Hidden was handled above")
    },
    lastLatency = when (visualState) {
      FloatingMonitorVisualState.Running -> null
      FloatingMonitorVisualState.Processing -> formatLastLatency(lastLatencyMs)
      FloatingMonitorVisualState.Hidden -> error("Hidden was handled above")
    },
  )
}

fun floatingMonitorContentDescription(model: FloatingMonitorRenderModel): String =
  when (model.visualState) {
    FloatingMonitorVisualState.Running ->
      "Running, requests ${model.requestValue}, errors ${model.secondaryValue}"
    FloatingMonitorVisualState.Processing -> {
      val lastLatency = model.lastLatency
      val lastDescription = when (lastLatency?.unit) {
        "ms" -> "${lastLatency.value} milliseconds"
        "s" -> "${lastLatency.value} seconds"
        else -> "unavailable"
      }
      "Processing, requests ${model.requestValue}, " +
        "current processing ${model.secondaryValue} seconds, " +
        "last successful latency $lastDescription"
    }
    FloatingMonitorVisualState.Hidden -> error("Hidden render models are not created")
  }

enum class FloatingMonitorVisualState {
  Hidden,
  Running,
  Processing,
}

fun deriveFloatingMonitorVisualState(
  status: ServerStatus,
  isInferring: Boolean,
): FloatingMonitorVisualState =
  when {
    status != ServerStatus.RUNNING -> FloatingMonitorVisualState.Hidden
    isInferring -> FloatingMonitorVisualState.Processing
    else -> FloatingMonitorVisualState.Running
  }

fun shouldShowFloatingMonitor(
  settingEnabled: Boolean,
  overlayPermissionGranted: Boolean,
  permissionFlowInProgress: Boolean,
  appIsForeground: Boolean,
  launchSuppressionActive: Boolean,
  serviceIsAlive: Boolean,
  visualState: FloatingMonitorVisualState,
): Boolean =
  settingEnabled &&
    overlayPermissionGranted &&
    !permissionFlowInProgress &&
    !appIsForeground &&
    !launchSuppressionActive &&
    serviceIsAlive &&
    visualState != FloatingMonitorVisualState.Hidden
