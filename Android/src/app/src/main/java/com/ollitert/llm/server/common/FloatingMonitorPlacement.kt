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

package com.ollitert.llm.server.common

/** Normalized, durable overlay placement and conversions for its display bounds. */
internal data class FloatingMonitorPlacement(
  val xFraction: Float,
  val yFraction: Float,
) {
  fun clamped(): FloatingMonitorPlacement = FloatingMonitorPlacement(
    xFraction = xFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: DEFAULT.xFraction,
    yFraction = yFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: DEFAULT.yFraction,
  )

  fun toWindowPosition(bounds: FloatingMonitorBounds, monitorWidthPx: Int, monitorHeightPx: Int): Pair<Int, Int> {
    val normalized = clamped()
    return (
      (bounds.widthPx - monitorWidthPx).coerceAtLeast(1) * normalized.xFraction
      ).toInt() to (
      (bounds.heightPx - monitorHeightPx).coerceAtLeast(1) * normalized.yFraction
      ).toInt()
  }

  companion object {
    val DEFAULT = FloatingMonitorPlacement(xFraction = 0.96f, yFraction = 0.35f)

    fun fromWindowPosition(
      xPx: Int,
      yPx: Int,
      bounds: FloatingMonitorBounds,
      monitorWidthPx: Int,
      monitorHeightPx: Int,
    ): FloatingMonitorPlacement = FloatingMonitorPlacement(
      xFraction = xPx.toFloat() / (bounds.widthPx - monitorWidthPx).coerceAtLeast(1),
      yFraction = yPx.toFloat() / (bounds.heightPx - monitorHeightPx).coerceAtLeast(1),
    ).clamped()
  }
}

/** Physical display bounds supplied by the WindowManager that owns the overlay. */
internal data class FloatingMonitorBounds(val widthPx: Int, val heightPx: Int)
