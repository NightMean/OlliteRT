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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMonitorVisualSpecTest {

  @Test
  fun `monitor uses compact fixed geometry`() {
    assertEquals(88f, FLOATING_MONITOR_WIDTH_DP)
    assertEquals(100f, FLOATING_MONITOR_HEIGHT_DP)
    assertEquals(2f, FLOATING_MONITOR_BORDER_WIDTH_DP)
    assertEquals(1f, FLOATING_MONITOR_BORDER_INSET_DP)
    assertEquals(1_000L, FLOATING_MONITOR_CANVAS_RETRY_MILLIS)
    assertEquals(0.19f, FLOATING_MONITOR_TOP_LABEL_BASELINE_FRACTION)
    assertEquals(0.43f, FLOATING_MONITOR_TOP_VALUE_BASELINE_FRACTION)
    assertEquals(0.70f, FLOATING_MONITOR_BOTTOM_VALUE_BASELINE_FRACTION)
    assertEquals(0.88f, FLOATING_MONITOR_BOTTOM_LABEL_BASELINE_FRACTION)
  }

  @Test
  fun `state palette keeps alpha on fill only`() {
    assertEquals(0xCC4ADE80.toInt(), floatingMonitorFillColor(FloatingMonitorVisualState.Running))
    assertEquals(0xCCAFC6FF.toInt(), floatingMonitorFillColor(FloatingMonitorVisualState.Processing))
    assertEquals(0xFF4ADE80.toInt(), floatingMonitorBorderColor(FloatingMonitorVisualState.Running))
    assertEquals(0xFFAFC6FF.toInt(), floatingMonitorBorderColor(FloatingMonitorVisualState.Processing))
    assertEquals(0xFF000000.toInt(), FLOATING_MONITOR_TEXT_COLOR)
    assertEquals(0xD9000000.toInt(), FLOATING_MONITOR_LAST_TEXT_COLOR)
  }

  @Test
  fun `fixed type hierarchy and processing geometry match active contract`() {
    assertEquals(20f, FLOATING_MONITOR_MAIN_VALUE_TEXT_SIZE_DP)
    assertEquals(16f, FLOATING_MONITOR_PROCESSING_VALUE_TEXT_SIZE_DP)
    assertEquals(10f, FLOATING_MONITOR_LABEL_TEXT_SIZE_DP)
    assertEquals(10f, FLOATING_MONITOR_UNIT_TEXT_SIZE_DP)
    assertEquals(0.68f, FLOATING_MONITOR_PROCESSING_TEXT_SCALE_X)
    assertEquals(0.25f, FLOATING_MONITOR_PROC_CENTER_FRACTION)
    assertEquals(0.75f, FLOATING_MONITOR_LAST_CENTER_FRACTION)
    assertEquals(0.66f, FLOATING_MONITOR_PROCESSING_VALUE_BASELINE_FRACTION)
    assertEquals(0.78f, FLOATING_MONITOR_PROCESSING_LABEL_BASELINE_FRACTION)
    assertEquals(0.50f, FLOATING_MONITOR_DIVIDER_X_FRACTION)
    assertEquals(0.54f, FLOATING_MONITOR_DIVIDER_TOP_FRACTION)
    assertEquals(0.80f, FLOATING_MONITOR_DIVIDER_BOTTOM_FRACTION)
    assertEquals(1f, FLOATING_MONITOR_DIVIDER_WIDTH_DP)
    assertEquals(0x33000000, FLOATING_MONITOR_DIVIDER_COLOR)
  }

  @Test
  fun `grouped counts keep stable digits with narrower centered commas`() {
    val width = floatingMonitorGroupedTextWidth(
      text = "99,999+",
      stableCharacterAdvance = 10f,
      commaAdvance = 4f,
    )

    assertEquals(64f, width)
    assertEquals(18f, floatingMonitorCenteredTextStartX(centerX = 50f, width = width))
    assertTrue(4f < 10f)
  }

  @Test
  fun `processing composite run remains centered within forty dp budget`() {
    val width = floatingMonitorCompositeRunWidth(
      valueWidth = 34f,
      unitWidth = 5f,
      gap = 1f,
    )

    assertEquals(40f, width)
    assertEquals(2f, floatingMonitorCenteredTextStartX(centerX = 22f, width = width))
    assertTrue(width <= FLOATING_MONITOR_PROCESSING_RUN_MAX_WIDTH_DP)
  }

  @Test(expected = IllegalStateException::class)
  fun `hidden state has no renderable fill`() {
    floatingMonitorFillColor(FloatingMonitorVisualState.Hidden)
  }
}
