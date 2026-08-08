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
