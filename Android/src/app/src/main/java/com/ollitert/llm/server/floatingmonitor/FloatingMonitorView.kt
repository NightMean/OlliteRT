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
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ollitert.llm.server.common.FloatingInferenceSettings
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.ui.navigation.StatusPill
import com.ollitert.llm.server.ui.server.StatusCapabilityChips
import com.ollitert.llm.server.ui.theme.OlliteRTTheme
import kotlin.math.abs

internal class FloatingMonitorView(
  context: Context,
  private val onPlacementChanged: (Int, Int) -> Unit,
  private val onPositionChanged: (Int, Int) -> Unit,
  private val onSizeChanged: () -> Unit,
  private val onOpenApp: () -> Unit,
) : FrameLayout(context), LifecycleOwner {
  private val density = resources.displayMetrics.density
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
  private val overlayViewTreeOwner = OverlayViewTreeOwner()
  private var snapshot = FloatingMonitorSnapshot()
  private var headerSnapshot by mutableStateOf(snapshot)
  private var nowMs = 0L
  private var expanded = false
  private var downRawX = 0f
  private var downRawY = 0f
  private var startWindowX = 0
  private var startWindowY = 0
  private var dragging = false

  val desiredWidthPx = dp(248)
  val desiredHeightPx get() = dp(if (expanded) expandedFooterYDp() + 16 else 188)

  init {
    setLayerType(LAYER_TYPE_SOFTWARE, null)
    setWillNotDraw(false)
    setViewTreeLifecycleOwner(overlayViewTreeOwner)
    setViewTreeSavedStateRegistryOwner(overlayViewTreeOwner)
    addView(
      ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
          OlliteRTTheme {
            FloatingMonitorHeader(snapshot = headerSnapshot)
          }
        }
      },
      LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)),
    )
  }

  override val lifecycle: Lifecycle get() = overlayViewTreeOwner.lifecycle

  fun render(snapshot: FloatingMonitorSnapshot, nowMs: Long) {
    this.snapshot = snapshot
    headerSnapshot = snapshot
    this.nowMs = nowMs
    invalidate()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(
      MeasureSpec.makeMeasureSpec(desiredWidthPx, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(desiredHeightPx, MeasureSpec.EXACTLY),
    )
    setMeasuredDimension(desiredWidthPx, desiredHeightPx)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    overlayViewTreeOwner.resume()
  }

  override fun onDetachedFromWindow() {
    overlayViewTreeOwner.destroy()
    super.onDetachedFromWindow()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val width = width.toFloat()
    val height = height.toFloat()
    paint.color = Color.rgb(28, 27, 28)
    paint.setShadowLayer(dp(12).toFloat(), 0f, dp(5).toFloat(), 0x55000000)
    canvas.drawRoundRect(RectF(0f, 0f, width, height), dp(22).toFloat(), dp(22).toFloat(), paint)
    paint.clearShadowLayer()
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = dp(1).toFloat()
    paint.color = Color.rgb(68, 71, 70)
    canvas.drawRoundRect(RectF(.5f, .5f, width - .5f, height - .5f), dp(22).toFloat(), dp(22).toFloat(), paint)
    paint.style = Paint.Style.FILL

    drawMetrics(canvas)
    if (expanded) drawAdditionalMetrics(canvas)
    drawFooter(canvas)
  }

  private fun drawMetrics(canvas: Canvas) {
    val gap = dp(6)
    val left = dp(12)
    val top = dp(48)
    val cellWidth = (width - left * 2 - gap * 2) / 3f
    val cellHeight = dp(52).toFloat()
    metric(canvas, left.toFloat(), top.toFloat(), cellWidth, cellHeight, snapshot.requestCount.toString(), "REQUESTS")
    metric(canvas, left + cellWidth + gap, top.toFloat(), cellWidth, cellHeight, formatMonitorSpeed(snapshot.decodeSpeed), "DECODE SPEED")
    metric(
      canvas,
      left + (cellWidth + gap) * 2,
      top.toFloat(),
      cellWidth,
      cellHeight,
      formatCompactUptime(snapshot.startedAtMs, nowMs),
      "UPTIME",
    )
    val errorWidth = dp(70).toFloat()
    metric(canvas, left.toFloat(), top + dp(58).toFloat(), errorWidth, cellHeight, snapshot.errorCount.toString(), "ERRORS")
    metric(
      canvas,
      left + errorWidth + gap,
      top + dp(58).toFloat(),
      width - left * 2 - errorWidth - gap,
      cellHeight,
      if (snapshot.ttfbMs > 0) "${snapshot.ttfbMs}ms" else "—",
      "LAST TTFB",
    )
  }

  private fun metric(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, value: String, label: String) {
    paint.color = Color.rgb(40, 42, 44)
    canvas.drawRoundRect(RectF(x, y, x + width, y + height), dp(16).toFloat(), dp(16).toFloat(), paint)
    paint.color = Color.rgb(229, 226, 227)
    val contentLeft = x + dp(10)
    val contentRight = x + width - dp(10)
    val fittedValue = fitMonitorMetricText(
      value = value,
      availableWidthPx = contentRight - contentLeft,
      measureWidth = { text, sizeDp ->
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textSize = dp(sizeDp).toFloat()
        paint.measureText(text)
      },
    )
    canvas.save()
    canvas.clipRect(contentLeft, y + dp(4), contentRight, y + dp(29))
    drawText(canvas, fittedValue.value, contentLeft, y + dp(23), fittedValue.sizeDp, true)
    canvas.restore()
    paint.color = Color.rgb(194, 198, 216)
    drawText(canvas, label, x + dp(10), y + dp(40), 7, false)
  }

  private fun drawAdditionalMetrics(canvas: Canvas) {
    val values = floatingMonitorDetails(snapshot.inferenceSettings)
    val gap = dp(6)
    val left = dp(12).toFloat()
    val top = dp(170).toFloat()
    val cellWidth = (width - dp(24) - gap * 2) / 3f
    var row = 0
    var column = 0
    values.forEach { metric ->
      if (column + metric.columnSpan > 3) {
        row++
        column = 0
      }
      val x = left + column * (cellWidth + gap)
      val y = top + row * dp(54)
      val metricWidth = cellWidth * metric.columnSpan + gap * (metric.columnSpan - 1)
      metric(canvas, x, y, metricWidth, dp(48).toFloat(), metric.value, metric.label)
      column += metric.columnSpan
      if (column == 3) {
        row++
        column = 0
      }
    }
  }

  private fun drawFooter(canvas: Canvas) {
    val footerY = if (expanded) dp(expandedFooterYDp()) else dp(172)
    paint.color = Color.rgb(194, 198, 216)
    drawDisclosureChevron(canvas, dp(14).toFloat(), footerY - dp(3), expanded)
    drawText(canvas, "MODEL DETAILS", dp(21).toFloat(), footerY.toFloat(), 8, true)
    val model = snapshot.modelName ?: "No model"
    paint.textSize = dp(8).toFloat()
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    val modelX = width - dp(14) - paint.measureText(model)
    paint.color = Color.rgb(229, 226, 227)
    drawText(canvas, model, modelX, footerY, 8, true)
  }

  private fun expandedFooterYDp(): Int {
    val rows = additionalMetricRowCount(floatingMonitorDetails(snapshot.inferenceSettings))
    return 170 + rows * 54 + 16
  }

  private fun drawText(canvas: Canvas, text: String, x: Float, baseline: Int, size: Int, bold: Boolean) =
    drawText(canvas, text, x, baseline.toFloat(), size, bold)

  private fun drawText(canvas: Canvas, text: String, x: Float, baseline: Float, size: Int, bold: Boolean) {
    paint.textSize = dp(size).toFloat()
    paint.typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    canvas.drawText(text, x, baseline, paint)
  }

  private fun drawDisclosureChevron(canvas: Canvas, x: Float, centerY: Int, expanded: Boolean) {
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeWidth = dp(1).toFloat()
    val halfWidth = dp(3).toFloat()
    val halfHeight = dp(2).toFloat()
    val pointY = if (expanded) centerY - halfHeight else centerY + halfHeight
    val outerY = if (expanded) centerY + halfHeight else centerY - halfHeight
    canvas.drawLine(x - halfWidth, outerY, x, pointY, paint)
    canvas.drawLine(x, pointY, x + halfWidth, outerY, paint)
    paint.strokeCap = Paint.Cap.BUTT
    paint.style = Paint.Style.FILL
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
        if (dragging) {
          persistPlacement(params)
        } else if (isFloatingMonitorFooterTap(
            tapY = event.y,
            monitorHeightPx = height,
            footerHeightPx = dp(38),
          )) {
          expanded = !expanded
          requestLayout()
          onSizeChanged()
        } else {
          onOpenApp()
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

internal data class AdditionalMonitorDetail(
  val label: String,
  val value: String,
  val columnSpan: Int = 1,
)

private fun additionalMetricRowCount(metrics: List<AdditionalMonitorDetail>): Int {
  if (metrics.isEmpty()) return 1
  var rows = 0
  var columnsUsed = 0
  metrics.forEach { metric ->
    if (columnsUsed + metric.columnSpan > 3) {
      rows++
      columnsUsed = 0
    }
    columnsUsed += metric.columnSpan
    if (columnsUsed == 3) {
      rows++
      columnsUsed = 0
    }
  }
  return if (columnsUsed == 0) rows else rows + 1
}

internal fun floatingMonitorDetails(settings: FloatingInferenceSettings): List<AdditionalMonitorDetail> =
  listOfNotNull(
    settings.temperature?.let { AdditionalMonitorDetail("TEMPERATURE", "%.2f".format(java.util.Locale.US, it)) },
    settings.topP?.let { AdditionalMonitorDetail("TOP P", "%.2f".format(java.util.Locale.US, it)) },
    settings.topK?.let { AdditionalMonitorDetail("TOP K", it.toString()) },
    settings.maxTokens?.let { AdditionalMonitorDetail("MAX TOKENS", it.toString()) },
    settings.thinkingBudget?.let { AdditionalMonitorDetail("THINKING BUDGET", it.toString(), columnSpan = 2) },
  )

/** The bottom footer is the model-details control, preserving its broad original tap target. */
internal fun isFloatingMonitorFooterTap(
  tapY: Float,
  monitorHeightPx: Int,
  footerHeightPx: Int,
): Boolean = tapY >= monitorHeightPx - footerHeightPx

/**
 * An application overlay is not attached to an Activity, so it must provide the
 * lifecycle and saved-state owners that Compose normally receives from that host.
 * The monitor has no restorable UI state; its snapshot is supplied by the controller.
 */
private class OverlayViewTreeOwner : LifecycleOwner, SavedStateRegistryOwner {
  private val lifecycleRegistry = LifecycleRegistry(this)
  private val savedStateController = SavedStateRegistryController.create(this)

  override val lifecycle: Lifecycle = lifecycleRegistry
  override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

  init {
    savedStateController.performAttach()
    savedStateController.performRestore(null)
    lifecycleRegistry.currentState = Lifecycle.State.CREATED
  }

  fun resume() {
    lifecycleRegistry.currentState = Lifecycle.State.RESUMED
  }

  fun destroy() {
    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
  }
}

@Composable
private fun FloatingMonitorHeader(snapshot: FloatingMonitorSnapshot) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .fillMaxSize()
      .padding(horizontal = 12.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    StatusPill(
      serverStatus = snapshot.status,
      isInferring = snapshot.isInferring,
      modelLoadPhase = snapshot.modelLoadPhase,
    )
    if (snapshot.status != ServerStatus.STOPPED && snapshot.status != ServerStatus.LOADING) {
      StatusCapabilityChips(
        accelerator = snapshot.accelerator,
        thinkingEnabled = snapshot.thinkingEnabled,
        mtpEnabled = snapshot.mtpEnabled,
      )
    }
  }
}
