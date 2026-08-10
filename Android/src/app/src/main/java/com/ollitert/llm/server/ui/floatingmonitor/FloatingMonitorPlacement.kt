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

import kotlin.math.roundToInt

enum class FloatingMonitorGestureResult {
  Tap,
  Drag,
  Cancelled,
}

class FloatingMonitorGestureTracker(
  touchSlopPx: Float,
) {
  private val touchSlopSquared = touchSlopPx.coerceAtLeast(0f).let { it * it }
  private var startX = 0f
  private var startY = 0f
  private var active = false
  private var dragging = false
  private var cancelled = false

  fun start(rawX: Float, rawY: Float) {
    startX = rawX
    startY = rawY
    active = true
    dragging = false
    cancelled = false
  }

  fun move(rawX: Float, rawY: Float): Boolean {
    if (!active || cancelled) return false
    if (!dragging) {
      val deltaX = rawX - startX
      val deltaY = rawY - startY
      dragging = deltaX * deltaX + deltaY * deltaY > touchSlopSquared
    }
    return dragging
  }

  fun cancel() {
    cancelled = true
    active = false
  }

  fun end(rawX: Float, rawY: Float): FloatingMonitorGestureResult {
    move(rawX, rawY)
    return end()
  }

  fun end(): FloatingMonitorGestureResult {
    val result = when {
      cancelled || !active -> FloatingMonitorGestureResult.Cancelled
      dragging -> FloatingMonitorGestureResult.Drag
      else -> FloatingMonitorGestureResult.Tap
    }
    active = false
    return result
  }
}

data class FloatingMonitorPoint(
  val x: Int,
  val y: Int,
)

internal data class FloatingMonitorPendingLayout(
  val point: FloatingMonitorPoint,
  val persistAfterReplay: Boolean,
) {
  fun afterReplay(succeeded: Boolean): FloatingMonitorPendingLayout? = if (succeeded) null else this
}

data class FloatingMonitorPlacementBounds(
  val minX: Int,
  val maxX: Int,
  val minY: Int,
  val maxY: Int,
)

data class NormalizedFloatingMonitorPosition(
  val x: Float,
  val y: Float,
)

fun clampFloatingMonitorPosition(
  point: FloatingMonitorPoint,
  bounds: FloatingMonitorPlacementBounds,
): FloatingMonitorPoint {
  val effectiveMaxX = bounds.maxX.coerceAtLeast(bounds.minX)
  val effectiveMaxY = bounds.maxY.coerceAtLeast(bounds.minY)
  return FloatingMonitorPoint(
    x = point.x.coerceIn(bounds.minX, effectiveMaxX),
    y = point.y.coerceIn(bounds.minY, effectiveMaxY),
  )
}

fun normalizeFloatingMonitorPosition(
  point: FloatingMonitorPoint,
  bounds: FloatingMonitorPlacementBounds,
): NormalizedFloatingMonitorPosition {
  val clamped = clampFloatingMonitorPosition(point, bounds)
  val width = (bounds.maxX - bounds.minX).coerceAtLeast(0)
  val height = (bounds.maxY - bounds.minY).coerceAtLeast(0)
  return NormalizedFloatingMonitorPosition(
    x = if (width == 0) 0f else (clamped.x - bounds.minX).toFloat() / width,
    y = if (height == 0) 0f else (clamped.y - bounds.minY).toFloat() / height,
  )
}

fun restoreFloatingMonitorPosition(
  normalized: NormalizedFloatingMonitorPosition,
  bounds: FloatingMonitorPlacementBounds,
): FloatingMonitorPoint {
  val width = (bounds.maxX - bounds.minX).coerceAtLeast(0)
  val height = (bounds.maxY - bounds.minY).coerceAtLeast(0)
  return clampFloatingMonitorPosition(
    point = FloatingMonitorPoint(
      x = bounds.minX + (normalized.x.coerceIn(0f, 1f) * width).roundToInt(),
      y = bounds.minY + (normalized.y.coerceIn(0f, 1f) * height).roundToInt(),
    ),
    bounds = bounds,
  )
}

fun defaultFloatingMonitorPosition(
  bounds: FloatingMonitorPlacementBounds,
  rightMarginPx: Int,
  topOffsetPx: Int,
): FloatingMonitorPoint =
  clampFloatingMonitorPosition(
    point = FloatingMonitorPoint(
      x = bounds.maxX - rightMarginPx.coerceAtLeast(0),
      y = bounds.minY + topOffsetPx.coerceAtLeast(0),
    ),
    bounds = bounds,
  )
