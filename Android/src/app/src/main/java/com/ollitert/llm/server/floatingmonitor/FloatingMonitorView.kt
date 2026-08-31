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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

internal class FloatingMonitorView(
  context: Context,
  private val onPlacementChanged: (Int, Int) -> Unit,
  private val onPositionChanged: (Int, Int) -> Unit,
  private val onSizeChanged: () -> Unit,
) : View(context) {
  private val density = resources.displayMetrics.density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private var snapshot = FloatingMonitorSnapshot()
  private var nowMs = 0L
  private var expanded = false
  private var downRawX = 0f
  private var downRawY = 0f
  private var startWindowX = 0
  private var startWindowY = 0
  private var dragging = false

  val desiredWidthPx = dp(248)
  val desiredHeightPx get() = dp(if (expanded) 278 else 206)

  init {
    setLayerType(LAYER_TYPE_SOFTWARE, null)
  }

  fun render(snapshot: FloatingMonitorSnapshot, nowMs: Long) {
    this.snapshot = snapshot
    this.nowMs = nowMs
    invalidate()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    setMeasuredDimension(desiredWidthPx, desiredHeightPx)
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val width = width.toFloat()
    val height = height.toFloat()
    paint.color = Color.rgb(25, 26, 28)
    paint.setShadowLayer(dp(12).toFloat(), 0f, dp(5).toFloat(), 0x55000000)
    canvas.drawRoundRect(RectF(0f, 0f, width, height), dp(22).toFloat(), dp(22).toFloat(), paint)
    paint.clearShadowLayer()
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = dp(1).toFloat()
    paint.color = Color.rgb(79, 82, 86)
    canvas.drawRoundRect(RectF(.5f, .5f, width - .5f, height - .5f), dp(22).toFloat(), dp(22).toFloat(), paint)
    paint.style = Paint.Style.FILL

    drawHeader(canvas)
    drawMetrics(canvas)
    if (expanded) drawSettings(canvas)
    drawFooter(canvas)
  }

  private fun drawHeader(canvas: Canvas) {
    val dot = when (floatingMonitorDot(snapshot.status, snapshot.isInferring, snapshot.modelLoadPhase)) {
      FloatingMonitorDot.PROCESSING -> Color.rgb(103, 183, 255)
      FloatingMonitorDot.RETRYING_CPU -> Color.rgb(255, 183, 77)
      FloatingMonitorDot.LOADING -> Color.rgb(174, 176, 181)
      FloatingMonitorDot.RUNNING -> Color.rgb(96, 211, 133)
      FloatingMonitorDot.STOPPED, FloatingMonitorDot.ERROR -> Color.rgb(239, 91, 91)
    }
    paint.color = dot
    canvas.drawCircle(dp(18).toFloat(), dp(18).toFloat(), dp(6).toFloat(), paint)
    val badges = buildList {
      snapshot.accelerator?.takeIf { it.isNotBlank() }?.let(::add)
      if (snapshot.thinkingEnabled) add("THINK")
      if (snapshot.mtpEnabled) add("MTP")
    }
    var right = width - dp(14).toFloat()
    for (badge in badges.asReversed()) {
      paint.textSize = dp(9).toFloat()
      paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
      val badgeWidth = paint.measureText(badge) + dp(14)
      val left = right - badgeWidth
      paint.color = Color.rgb(48, 50, 54)
      canvas.drawRoundRect(RectF(left.toFloat(), dp(9).toFloat(), right.toFloat(), dp(27).toFloat()), dp(6).toFloat(), dp(6).toFloat(), paint)
      paint.color = Color.rgb(141, 196, 255)
      drawText(canvas, badge, left + dp(7), dp(21), 9, true)
      right = left - dp(5)
    }
  }

  private fun drawMetrics(canvas: Canvas) {
    val gap = dp(6)
    val left = dp(12)
    val top = dp(38)
    val cellWidth = (width - left * 2 - gap * 2) / 3f
    val cellHeight = dp(52).toFloat()
    metric(canvas, left.toFloat(), top.toFloat(), cellWidth, cellHeight, snapshot.requestCount.toString(), "REQ")
    metric(canvas, left + cellWidth + gap, top.toFloat(), cellWidth, cellHeight, formatMonitorSpeed(snapshot.decodeSpeed), "T/S")
    metric(canvas, left + (cellWidth + gap) * 2, top.toFloat(), cellWidth, cellHeight, if (snapshot.ttfbMs > 0) "${snapshot.ttfbMs}" else "—", "TTFB")
    metric(canvas, left.toFloat(), top + dp(58).toFloat(), cellWidth, cellHeight, snapshot.errorCount.toString(), "ERR")
    metric(canvas, left + cellWidth + gap, top + dp(58).toFloat(), cellWidth * 2 + gap, cellHeight, formatCompactUptime(snapshot.startedAtMs, nowMs), "UP")
  }

  private fun metric(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, value: String, label: String) {
    paint.color = Color.rgb(45, 47, 50)
    canvas.drawRoundRect(RectF(x, y, x + width, y + height), dp(10).toFloat(), dp(10).toFloat(), paint)
    paint.color = Color.rgb(230, 231, 234)
    drawText(canvas, value, x + dp(10), y + dp(23), 16, true)
    paint.color = Color.rgb(165, 167, 172)
    drawText(canvas, label, x + dp(10), y + dp(40), 8, true)
  }

  private fun drawSettings(canvas: Canvas) {
    val settings = snapshot.inferenceSettings
    val values = listOf(
      "TEMP" to settings.temperature?.let { "%.2f".format(java.util.Locale.US, it) },
      "MAX" to settings.maxTokens?.toString(),
      "TOP-K" to settings.topK?.toString(),
      "TOP-P" to settings.topP?.let { "%.2f".format(java.util.Locale.US, it) },
      "THINK" to settings.thinkingEnabled?.let { if (it) "ON" else "OFF" },
      "BUDGET" to settings.thinkingBudget?.toString(),
    ).filter { it.second != null }
    var x = dp(14).toFloat()
    var y = dp(158).toFloat()
    values.forEachIndexed { index, (label, value) ->
      if (index == 3) { x = dp(14).toFloat(); y += dp(28) }
      paint.color = Color.rgb(166, 168, 173)
      drawText(canvas, "$label ${value ?: "—"}", x, y, 9, false)
      x += dp(74)
    }
  }

  private fun drawFooter(canvas: Canvas) {
    val footerY = if (expanded) dp(263) else dp(190)
    paint.color = Color.rgb(157, 159, 164)
    drawText(canvas, if (expanded) "⌃  SETTINGS" else "⌄  SETTINGS", dp(14).toFloat(), footerY.toFloat(), 9, true)
    val model = snapshot.modelName ?: "No model"
    paint.textSize = dp(10).toFloat()
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    val modelX = width - dp(14) - paint.measureText(model)
    paint.color = Color.rgb(193, 195, 199)
    drawText(canvas, model, modelX, footerY, 10, true)
  }

  private fun drawText(canvas: Canvas, text: String, x: Float, baseline: Int, size: Int, bold: Boolean) =
    drawText(canvas, text, x, baseline.toFloat(), size, bold)

  private fun drawText(canvas: Canvas, text: String, x: Float, baseline: Float, size: Int, bold: Boolean) {
    paint.textSize = dp(size).toFloat()
    paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    canvas.drawText(text, x, baseline, paint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val params = layoutParams as? WindowManager.LayoutParams ?: return true
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        downRawX = event.rawX; downRawY = event.rawY
        startWindowX = params.x; startWindowY = params.y
        dragging = false
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = event.rawX - downRawX; val dy = event.rawY - downRawY
        if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
        if (dragging) {
          onPositionChanged(startWindowX + dx.toInt(), startWindowY + dy.toInt())
        }
        return true
      }
      MotionEvent.ACTION_UP -> {
        if (dragging) persistPlacement(params) else if (event.y >= height - dp(38)) {
          expanded = !expanded
          requestLayout(); onSizeChanged()
        }
        return true
      }
    }
    return true
  }

  private fun persistPlacement(params: WindowManager.LayoutParams) {
    onPlacementChanged(params.x, params.y)
  }

  private fun dp(value: Int): Int = (value * density).toInt()
}
