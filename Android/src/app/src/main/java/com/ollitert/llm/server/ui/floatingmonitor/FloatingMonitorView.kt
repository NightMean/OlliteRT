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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.Log
import android.view.View
import kotlin.math.roundToInt

internal const val FLOATING_MONITOR_WIDTH_DP = 88f
internal const val FLOATING_MONITOR_HEIGHT_DP = 100f
internal const val FLOATING_MONITOR_BORDER_WIDTH_DP = 2f
internal const val FLOATING_MONITOR_BORDER_INSET_DP = FLOATING_MONITOR_BORDER_WIDTH_DP / 2f
internal const val FLOATING_MONITOR_CANVAS_RETRY_MILLIS = 1_000L
internal const val FLOATING_MONITOR_MAIN_VALUE_TEXT_SIZE_DP = 20f
internal const val FLOATING_MONITOR_PROCESSING_VALUE_TEXT_SIZE_DP = 18f
internal const val FLOATING_MONITOR_LABEL_TEXT_SIZE_DP = 10f
internal const val FLOATING_MONITOR_UNIT_TEXT_SIZE_DP = 10f
internal const val FLOATING_MONITOR_PROCESSING_TEXT_SCALE_X = 0.80f
internal const val FLOATING_MONITOR_LAST_DECIMAL_TEXT_SCALE_X = 0.45f
internal const val FLOATING_MONITOR_UNIT_TEXT_SCALE_X = 1f
internal const val FLOATING_MONITOR_PROCESSING_RUN_MAX_WIDTH_DP = 40f
internal const val FLOATING_MONITOR_TEXT_COLOR = 0xFF000000.toInt()
internal const val FLOATING_MONITOR_LAST_TEXT_COLOR = 0xD9000000.toInt()
internal const val FLOATING_MONITOR_DIVIDER_COLOR = 0x33000000
internal const val FLOATING_MONITOR_TOP_LABEL_BASELINE_FRACTION = 0.19f
internal const val FLOATING_MONITOR_TOP_VALUE_BASELINE_FRACTION = 0.43f
internal const val FLOATING_MONITOR_BOTTOM_VALUE_BASELINE_FRACTION = 0.70f
internal const val FLOATING_MONITOR_BOTTOM_LABEL_BASELINE_FRACTION = 0.88f
internal const val FLOATING_MONITOR_PROC_CENTER_FRACTION = 0.25f
internal const val FLOATING_MONITOR_LAST_CENTER_FRACTION = 0.75f
internal const val FLOATING_MONITOR_PROCESSING_VALUE_BASELINE_FRACTION = 0.66f
internal const val FLOATING_MONITOR_PROCESSING_LABEL_BASELINE_FRACTION = 0.78f
internal const val FLOATING_MONITOR_DIVIDER_X_FRACTION = 0.50f
internal const val FLOATING_MONITOR_DIVIDER_TOP_FRACTION = 0.54f
internal const val FLOATING_MONITOR_DIVIDER_BOTTOM_FRACTION = 0.80f
internal const val FLOATING_MONITOR_DIVIDER_WIDTH_DP = 1f

private const val COMMA_ADVANCE_FRACTION = 0.55f
private const val PROCESSING_UNIT_GAP_DP = 0.5f

internal fun floatingMonitorFillColor(state: FloatingMonitorVisualState): Int =
  when (state) {
    FloatingMonitorVisualState.Running -> 0xB34ADE80.toInt()
    FloatingMonitorVisualState.Processing -> 0xB39DCAFC.toInt()
    FloatingMonitorVisualState.Hidden -> error("Hidden monitor has no renderable fill")
  }

internal fun floatingMonitorBorderColor(state: FloatingMonitorVisualState): Int =
  when (state) {
    FloatingMonitorVisualState.Running -> 0xFF4ADE80.toInt()
    FloatingMonitorVisualState.Processing -> 0xFF9DCAFC.toInt()
    FloatingMonitorVisualState.Hidden -> error("Hidden monitor has no renderable border")
  }

internal fun floatingMonitorGroupedTextWidth(
  text: String,
  stableCharacterAdvance: Float,
  commaAdvance: Float,
): Float {
  val stableAdvance = stableCharacterAdvance.coerceAtLeast(0f)
  val narrowCommaAdvance = commaAdvance.coerceIn(0f, stableAdvance)
  return text.fold(0f) { width, character ->
    width + if (character == ',') narrowCommaAdvance else stableAdvance
  }
}

internal fun floatingMonitorCompositeRunWidth(
  valueWidth: Float,
  unitWidth: Float,
  gap: Float,
): Float {
  val safeValueWidth = valueWidth.coerceAtLeast(0f)
  val safeUnitWidth = unitWidth.coerceAtLeast(0f)
  return if (safeUnitWidth == 0f) {
    safeValueWidth
  } else {
    safeValueWidth + gap.coerceAtLeast(0f) + safeUnitWidth
  }
}

internal fun floatingMonitorCenteredTextStartX(centerX: Float, width: Float): Float =
  centerX - width.coerceAtLeast(0f) / 2f

@SuppressLint("ViewConstructor")
internal class FloatingMonitorView(
  context: Context,
  onTap: () -> Unit,
) : View(context) {
  private val density = resources.displayMetrics.density
  private val hexPath = Path()
  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeWidth = FLOATING_MONITOR_BORDER_WIDTH_DP * density
  }
  private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = FLOATING_MONITOR_DIVIDER_COLOR
    style = Paint.Style.STROKE
    strokeWidth = FLOATING_MONITOR_DIVIDER_WIDTH_DP * density
  }
  private val labelPaint = textPaint(
    color = FLOATING_MONITOR_TEXT_COLOR,
    textSizeDp = FLOATING_MONITOR_LABEL_TEXT_SIZE_DP,
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL),
    align = Paint.Align.CENTER,
  )
  private val lastLabelPaint = textPaint(
    color = FLOATING_MONITOR_LAST_TEXT_COLOR,
    textSizeDp = FLOATING_MONITOR_LABEL_TEXT_SIZE_DP,
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL),
    align = Paint.Align.CENTER,
  )
  private val mainValuePaint = textPaint(
    color = FLOATING_MONITOR_TEXT_COLOR,
    textSizeDp = FLOATING_MONITOR_MAIN_VALUE_TEXT_SIZE_DP,
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
  )
  private val commaPaint = textPaint(
    color = FLOATING_MONITOR_TEXT_COLOR,
    textSizeDp = FLOATING_MONITOR_MAIN_VALUE_TEXT_SIZE_DP,
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
  )
  private val processingValuePaint = textPaint(
    color = FLOATING_MONITOR_TEXT_COLOR,
    textSizeDp = FLOATING_MONITOR_PROCESSING_VALUE_TEXT_SIZE_DP,
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
    textScaleX = FLOATING_MONITOR_PROCESSING_TEXT_SCALE_X,
  )
  private val processingUnitPaint = textPaint(
    color = FLOATING_MONITOR_TEXT_COLOR,
    textSizeDp = FLOATING_MONITOR_UNIT_TEXT_SIZE_DP,
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
    textScaleX = FLOATING_MONITOR_UNIT_TEXT_SCALE_X,
  )
  private val lastValuePaint = textPaint(
    color = FLOATING_MONITOR_LAST_TEXT_COLOR,
    textSizeDp = FLOATING_MONITOR_PROCESSING_VALUE_TEXT_SIZE_DP,
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
    textScaleX = FLOATING_MONITOR_PROCESSING_TEXT_SCALE_X,
  )
  private val lastDecimalPaint = textPaint(
    color = FLOATING_MONITOR_LAST_TEXT_COLOR,
    textSizeDp = FLOATING_MONITOR_PROCESSING_VALUE_TEXT_SIZE_DP,
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
    textScaleX = FLOATING_MONITOR_LAST_DECIMAL_TEXT_SCALE_X,
  )
  private val lastUnitPaint = textPaint(
    color = FLOATING_MONITOR_LAST_TEXT_COLOR,
    textSizeDp = FLOATING_MONITOR_UNIT_TEXT_SIZE_DP,
    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD),
    textScaleX = FLOATING_MONITOR_UNIT_TEXT_SCALE_X,
  )

  private var model: FloatingMonitorRenderModel? = null

  init {
    isClickable = true
    isFocusable = false
    setOnClickListener { onTap() }
  }

  fun render(nextModel: FloatingMonitorRenderModel) {
    model = nextModel
    contentDescription = floatingMonitorContentDescription(nextModel)
    invalidate()
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val desiredWidth = (FLOATING_MONITOR_WIDTH_DP * density).roundToInt()
    val desiredHeight = (FLOATING_MONITOR_HEIGHT_DP * density).roundToInt()
    setMeasuredDimension(
      resolveSize(desiredWidth, widthMeasureSpec),
      resolveSize(desiredHeight, heightMeasureSpec),
    )
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val current = model ?: return
    try {
      drawMonitor(canvas, current)
    } catch (e: RuntimeException) {
      Log.w(TAG, "Floating monitor draw failed", e)
      try {
        postInvalidateDelayed(FLOATING_MONITOR_CANVAS_RETRY_MILLIS)
      } catch (_: RuntimeException) {
        // Canvas recovery remains isolated from WindowManager reconciliation and server work.
      }
    }
  }

  private fun drawMonitor(canvas: Canvas, current: FloatingMonitorRenderModel) {
    val width = width.toFloat()
    val height = height.toFloat()
    val borderInset = borderPaint.strokeWidth / 2f

    hexPath.reset()
    hexPath.moveTo(width / 2f, borderInset)
    hexPath.lineTo(width - borderInset, height * 0.25f)
    hexPath.lineTo(width - borderInset, height * 0.75f)
    hexPath.lineTo(width / 2f, height - borderInset)
    hexPath.lineTo(borderInset, height * 0.75f)
    hexPath.lineTo(borderInset, height * 0.25f)
    hexPath.close()

    fillPaint.color = floatingMonitorFillColor(current.visualState)
    borderPaint.color = floatingMonitorBorderColor(current.visualState)
    canvas.drawPath(hexPath, fillPaint)
    canvas.drawPath(hexPath, borderPaint)

    val centerX = width / 2f
    canvas.drawText(
      "req",
      centerX,
      height * FLOATING_MONITOR_TOP_LABEL_BASELINE_FRACTION,
      labelPaint,
    )
    drawGroupedCount(
      canvas = canvas,
      text = current.requestValue,
      centerX = centerX,
      baseline = height * FLOATING_MONITOR_TOP_VALUE_BASELINE_FRACTION,
    )

    if (current.visualState == FloatingMonitorVisualState.Processing) {
      drawProcessingMetrics(canvas, current, width, height)
    } else {
      drawGroupedCount(
        canvas = canvas,
        text = current.secondaryValue,
        centerX = centerX,
        baseline = height * FLOATING_MONITOR_BOTTOM_VALUE_BASELINE_FRACTION,
      )
      canvas.drawText(
        current.secondaryLabel,
        centerX,
        height * FLOATING_MONITOR_BOTTOM_LABEL_BASELINE_FRACTION,
        labelPaint,
      )
    }
  }

  private fun drawProcessingMetrics(
    canvas: Canvas,
    current: FloatingMonitorRenderModel,
    width: Float,
    height: Float,
  ) {
    val valueBaseline = height * FLOATING_MONITOR_PROCESSING_VALUE_BASELINE_FRACTION
    val labelBaseline = height * FLOATING_MONITOR_PROCESSING_LABEL_BASELINE_FRACTION
    val procCenterX = width * FLOATING_MONITOR_PROC_CENTER_FRACTION
    val lastCenterX = width * FLOATING_MONITOR_LAST_CENTER_FRACTION
    val lastLatency = current.lastLatency ?: FloatingMonitorLatencyText(value = "—", unit = null)

    canvas.drawLine(
      width * FLOATING_MONITOR_DIVIDER_X_FRACTION,
      height * FLOATING_MONITOR_DIVIDER_TOP_FRACTION,
      width * FLOATING_MONITOR_DIVIDER_X_FRACTION,
      height * FLOATING_MONITOR_DIVIDER_BOTTOM_FRACTION,
      dividerPaint,
    )
    drawCompositeMetric(
      canvas = canvas,
      value = current.secondaryValue,
      unit = current.secondaryUnit,
      centerX = procCenterX,
      baseline = valueBaseline,
      valuePaint = processingValuePaint,
      unitPaint = processingUnitPaint,
    )
    drawLastCompositeMetric(
      canvas = canvas,
      value = lastLatency.value,
      unit = lastLatency.inlineUnit,
      centerX = lastCenterX,
      baseline = valueBaseline,
    )
    canvas.drawText("proc", procCenterX, labelBaseline, labelPaint)
    canvas.drawText("last", lastCenterX, labelBaseline, lastLabelPaint)
  }

  private fun drawGroupedCount(
    canvas: Canvas,
    text: String,
    centerX: Float,
    baseline: Float,
  ) {
    val stableAdvance = mainValuePaint.measureText("0")
    val commaGlyphWidth = commaPaint.measureText(",")
    val commaAdvance = minOf(commaGlyphWidth, stableAdvance * COMMA_ADVANCE_FRACTION)
    val width = floatingMonitorGroupedTextWidth(text, stableAdvance, commaAdvance)
    var x = floatingMonitorCenteredTextStartX(centerX, width)

    for (character in text) {
      val characterText = character.toString()
      val paint = if (character == ',') commaPaint else mainValuePaint
      val advance = if (character == ',') commaAdvance else stableAdvance
      val glyphWidth = paint.measureText(characterText)
      canvas.drawText(characterText, x + (advance - glyphWidth) / 2f, baseline, paint)
      x += advance
    }
  }

  private fun drawCompositeMetric(
    canvas: Canvas,
    value: String,
    unit: String?,
    centerX: Float,
    baseline: Float,
    valuePaint: Paint,
    unitPaint: Paint,
  ) {
    val valueWidth = valuePaint.measureText(value)
    val unitWidth = unit?.let { unitPaint.measureText(it) } ?: 0f
    val gap = if (unit == null) 0f else PROCESSING_UNIT_GAP_DP * density
    val width = floatingMonitorCompositeRunWidth(valueWidth, unitWidth, gap)
    val startX = floatingMonitorCenteredTextStartX(centerX, width)

    canvas.drawText(value, startX, baseline, valuePaint)
    if (unit != null) {
      canvas.drawText(unit, startX + valueWidth + gap, baseline, unitPaint)
    }
  }

  private fun drawLastCompositeMetric(
    canvas: Canvas,
    value: String,
    unit: String?,
    centerX: Float,
    baseline: Float,
  ) {
    val decimalIndex = value.indexOf('.')
    if (decimalIndex < 0) {
      drawCompositeMetric(
        canvas = canvas,
        value = value,
        unit = unit,
        centerX = centerX,
        baseline = baseline,
        valuePaint = lastValuePaint,
        unitPaint = lastUnitPaint,
      )
      return
    }

    val wholePart = value.substring(0, decimalIndex)
    val fractionalPart = value.substring(decimalIndex + 1)
    val wholeWidth = lastValuePaint.measureText(wholePart)
    val decimalWidth = lastDecimalPaint.measureText(".")
    val fractionalWidth = lastValuePaint.measureText(fractionalPart)
    val valueWidth = wholeWidth + decimalWidth + fractionalWidth
    val unitWidth = unit?.let { lastUnitPaint.measureText(it) } ?: 0f
    val gap = if (unit == null) 0f else PROCESSING_UNIT_GAP_DP * density
    val width = floatingMonitorCompositeRunWidth(valueWidth, unitWidth, gap)
    val startX = floatingMonitorCenteredTextStartX(centerX, width)

    canvas.drawText(wholePart, startX, baseline, lastValuePaint)
    canvas.drawText(".", startX + wholeWidth, baseline, lastDecimalPaint)
    canvas.drawText(fractionalPart, startX + wholeWidth + decimalWidth, baseline, lastValuePaint)
    if (unit != null) {
      canvas.drawText(unit, startX + valueWidth + gap, baseline, lastUnitPaint)
    }
  }

  private fun textPaint(
    color: Int,
    textSizeDp: Float,
    typeface: Typeface,
    align: Paint.Align = Paint.Align.LEFT,
    textScaleX: Float = 1f,
  ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textAlign = align
    textSize = textSizeDp * density
    this.typeface = typeface
    this.textScaleX = textScaleX
  }

  private companion object {
    const val TAG = "OlliteRT.FloatView"
  }
}
