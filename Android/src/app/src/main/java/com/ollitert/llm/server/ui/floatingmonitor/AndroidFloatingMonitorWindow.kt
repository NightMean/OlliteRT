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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import com.ollitert.llm.server.data.ServerPrefs
import kotlin.math.roundToInt

internal class AndroidFloatingMonitorWindow(
  context: Context,
  onTap: () -> Unit,
  private val onWindowOperationFailure: (RuntimeException) -> Unit = {},
) : FloatingMonitorWindowPort {
  private val appContext = context.applicationContext
  private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  private val monitorWidth = dp(FLOATING_MONITOR_WIDTH_DP)
  private val monitorHeight = dp(FLOATING_MONITOR_HEIGHT_DP)
  private val view = FloatingMonitorView(appContext, onTap)
  private val renderGate = FloatingMonitorRenderGate()
  private val gestureTracker = FloatingMonitorGestureTracker(
    ViewConfiguration.get(appContext).scaledTouchSlop.toFloat()
  )
  private var downRawX = 0f
  private var downRawY = 0f
  private var dragStartX = 0
  private var dragStartY = 0
  private var lastBounds: FloatingMonitorPlacementBounds? = null
  private val layoutParams = WindowManager.LayoutParams(
    monitorWidth,
    monitorHeight,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT,
  ).apply {
    gravity = Gravity.TOP or Gravity.START
  }

  init {
    installTouchHandling()
  }

  override val isAttached: Boolean
    get() = view.parent != null

  override fun attach(model: FloatingMonitorRenderModel) {
    if (isAttached) {
      update(model)
      return
    }

    renderIfChanged(model)
    restorePlacementForAttach()
    windowManager.addView(view, layoutParams)
  }

  override fun update(model: FloatingMonitorRenderModel) {
    renderIfChanged(model)
    reconcileChangedBounds()
  }

  override fun detach() {
    if (!isAttached) {
      renderGate.reset()
      return
    }
    try {
      windowManager.removeViewImmediate(view)
    } finally {
      if (!isAttached) renderGate.reset()
    }
  }

  override fun deactivate() {
    view.setOnTouchListener(null)
    view.setOnClickListener(null)
    view.isClickable = false
    view.visibility = View.GONE
    renderGate.reset()
  }

  private fun renderIfChanged(model: FloatingMonitorRenderModel) {
    renderGate.renderIfChanged(model) { view.render(model) }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun installTouchHandling() {
    view.setOnTouchListener { _, event ->
      try {
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> {
            downRawX = event.rawX
            downRawY = event.rawY
            dragStartX = layoutParams.x
            dragStartY = layoutParams.y
            gestureTracker.start(event.rawX, event.rawY)
            true
          }
          MotionEvent.ACTION_MOVE -> {
            if (gestureTracker.move(event.rawX, event.rawY)) {
              val target = FloatingMonitorPoint(
                x = dragStartX + (event.rawX - downRawX).roundToInt(),
                y = dragStartY + (event.rawY - downRawY).roundToInt(),
              )
              applyPosition(clampFloatingMonitorPosition(target, currentBounds()), updateWindow = true)
            }
            true
          }
          MotionEvent.ACTION_UP -> {
            when (gestureTracker.end(event.rawX, event.rawY)) {
              FloatingMonitorGestureResult.Tap -> view.performClick()
              FloatingMonitorGestureResult.Drag -> {
                val target = FloatingMonitorPoint(
                  x = dragStartX + (event.rawX - downRawX).roundToInt(),
                  y = dragStartY + (event.rawY - downRawY).roundToInt(),
                )
                applyPosition(clampFloatingMonitorPosition(target, currentBounds()), updateWindow = true)
                persistCurrentPosition()
              }
              FloatingMonitorGestureResult.Cancelled -> Unit
            }
            true
          }
          MotionEvent.ACTION_CANCEL -> {
            gestureTracker.cancel()
            persistCurrentPosition()
            true
          }
          else -> true
        }
      } catch (exception: RuntimeException) {
        try {
          onWindowOperationFailure(exception)
        } catch (_: RuntimeException) {
          // Optional diagnostics must not escape the input-dispatch boundary.
        }
        true
      }
    }
  }

  private fun restorePlacementForAttach() {
    val bounds = currentBounds()
    val saved = readSavedPosition()
    val point = if (saved == null) {
      defaultFloatingMonitorPosition(
        bounds = bounds,
        rightMarginPx = dp(DEFAULT_RIGHT_MARGIN_DP),
        topOffsetPx = dp(DEFAULT_TOP_OFFSET_DP),
      )
    } else {
      restoreFloatingMonitorPosition(saved, bounds)
    }
    lastBounds = bounds
    applyPosition(point, updateWindow = false)
  }

  private fun reconcileChangedBounds() {
    val oldBounds = lastBounds ?: return
    val newBounds = currentBounds()
    if (newBounds == oldBounds) return

    val normalized = readSavedPosition()
      ?: normalizeFloatingMonitorPosition(
        FloatingMonitorPoint(layoutParams.x, layoutParams.y),
        oldBounds,
      )
    lastBounds = newBounds
    applyPosition(restoreFloatingMonitorPosition(normalized, newBounds), updateWindow = isAttached)
  }

  private fun persistCurrentPosition() {
    val bounds = currentBounds()
    val clamped = clampFloatingMonitorPosition(
      FloatingMonitorPoint(layoutParams.x, layoutParams.y),
      bounds,
    )
    applyPosition(clamped, updateWindow = isAttached)
    val normalized = normalizeFloatingMonitorPosition(clamped, bounds)
    try {
      ServerPrefs.setFloatingMonitorPosition(appContext, normalized.x, normalized.y)
      lastBounds = bounds
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unable to persist floating monitor position", e)
    }
  }

  private fun readSavedPosition(): NormalizedFloatingMonitorPosition? =
    try {
      ServerPrefs.getFloatingMonitorPosition(appContext)?.let { (x, y) ->
        NormalizedFloatingMonitorPosition(x, y)
      }
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unable to read floating monitor position", e)
      null
    }

  private fun applyPosition(point: FloatingMonitorPoint, updateWindow: Boolean) {
    if (layoutParams.x == point.x && layoutParams.y == point.y) return
    val previousX = layoutParams.x
    val previousY = layoutParams.y
    layoutParams.x = point.x
    layoutParams.y = point.y
    if (!updateWindow) return

    try {
      windowManager.updateViewLayout(view, layoutParams)
    } catch (e: RuntimeException) {
      layoutParams.x = previousX
      layoutParams.y = previousY
      throw e
    }
  }

  private fun currentBounds(): FloatingMonitorPlacementBounds {
    val metrics = windowManager.currentWindowMetrics
    val insetTypes = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
    val insets = metrics.windowInsets.getInsetsIgnoringVisibility(insetTypes)
    val width = metrics.bounds.width()
    val height = metrics.bounds.height()
    val minX = insets.left
    val minY = insets.top
    return FloatingMonitorPlacementBounds(
      minX = minX,
      maxX = (width - insets.right - monitorWidth).coerceAtLeast(minX),
      minY = minY,
      maxY = (height - insets.bottom - monitorHeight).coerceAtLeast(minY),
    )
  }

  private fun dp(value: Float): Int =
    (value * appContext.resources.displayMetrics.density).roundToInt()

  private companion object {
    const val TAG = "OlliteRT.FloatWindow"
    const val DEFAULT_RIGHT_MARGIN_DP = 16f
    const val DEFAULT_TOP_OFFSET_DP = 120f
  }
}
