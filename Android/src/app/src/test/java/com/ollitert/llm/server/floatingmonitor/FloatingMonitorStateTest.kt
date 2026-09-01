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
import com.ollitert.llm.server.common.FloatingInferenceSettings
import com.ollitert.llm.server.common.FloatingMonitorBounds
import com.ollitert.llm.server.common.FloatingMonitorPlacement
import com.ollitert.llm.server.common.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMonitorStateTest {

  @Test fun `formats compact uptime at unit boundaries`() {
    val startedAt = 1_000L
    assertEquals("0s", formatCompactUptime(startedAt, startedAt))
    assertEquals("59s", formatCompactUptime(startedAt, startedAt + 59_000L))
    assertEquals("1m", formatCompactUptime(startedAt, startedAt + 60_000L))
    assertEquals("1h", formatCompactUptime(startedAt, startedAt + 3_600_000L))
    assertEquals("1d", formatCompactUptime(startedAt, startedAt + 86_400_000L))
    assertEquals("—", formatCompactUptime(0L, 10_000L))
  }

  @Test fun `visibility requires background permission enabled and active server`() {
    assertTrue(shouldShowFloatingMonitor(true, true, false, ServerStatus.RUNNING))
    assertTrue(shouldShowFloatingMonitor(true, true, false, ServerStatus.LOADING))
    assertFalse(shouldShowFloatingMonitor(false, true, false, ServerStatus.RUNNING))
    assertFalse(shouldShowFloatingMonitor(true, false, false, ServerStatus.RUNNING))
    assertFalse(shouldShowFloatingMonitor(true, true, true, ServerStatus.RUNNING))
    assertFalse(shouldShowFloatingMonitor(true, true, false, ServerStatus.STOPPED))
    assertFalse(shouldShowFloatingMonitor(true, true, false, ServerStatus.ERROR))
  }

  @Test fun `floating monitor enables only after overlay permission is granted`() {
    assertTrue(resolveFloatingMonitorEnabled(requestedEnabled = true, hasOverlayPermission = true))
    assertFalse(resolveFloatingMonitorEnabled(requestedEnabled = true, hasOverlayPermission = false))
    assertFalse(resolveFloatingMonitorEnabled(requestedEnabled = false, hasOverlayPermission = true))
  }

  @Test fun `dot prioritizes processing and cpu retry`() {
    assertEquals(FloatingMonitorDot.PROCESSING, floatingMonitorDot(ServerStatus.RUNNING, true, ModelLoadPhase.STARTING))
    assertEquals(FloatingMonitorDot.RETRYING_CPU, floatingMonitorDot(ServerStatus.LOADING, false, ModelLoadPhase.RETRYING_CPU))
    assertEquals(FloatingMonitorDot.RUNNING, floatingMonitorDot(ServerStatus.RUNNING, false, ModelLoadPhase.STARTING))
  }

  @Test fun `state label follows the status pill wording`() {
    assertEquals(
      com.ollitert.llm.server.R.string.status_pill_processing,
      floatingMonitorStateLabel(ServerStatus.RUNNING, true, ModelLoadPhase.STARTING),
    )
    assertEquals(
      com.ollitert.llm.server.R.string.status_pill_running,
      floatingMonitorStateLabel(ServerStatus.RUNNING, false, ModelLoadPhase.STARTING),
    )
  }

  @Test fun `placement clamps invalid and edge values`() {
    assertEquals(FloatingMonitorPlacement(0f, 1f), FloatingMonitorPlacement(-1f, 2f).clamped())
    assertEquals(FloatingMonitorPlacement.DEFAULT, FloatingMonitorPlacement(Float.NaN, Float.POSITIVE_INFINITY).clamped())
  }

  @Test fun `placement converts through one display coordinate system`() {
    val bounds = FloatingMonitorBounds(widthPx = 1080, heightPx = 2400)
    val placement = FloatingMonitorPlacement(0.5f, 0.25f)

    val (x, y) = placement.toWindowPosition(bounds, monitorWidthPx = 248, monitorHeightPx = 188)

    assertEquals(416, x)
    assertEquals(553, y)
    val restored = FloatingMonitorPlacement.fromWindowPosition(x, y, bounds, 248, 188)
    assertEquals(placement.xFraction, restored.xFraction, 0.001f)
    assertEquals(placement.yFraction, restored.yFraction, 0.001f)
  }

  @Test fun `bottom footer taps expand model details`() {
    assertFalse(isFloatingMonitorFooterTap(tapY = 149f, monitorHeightPx = 188, footerHeightPx = 38))
    assertTrue(isFloatingMonitorFooterTap(tapY = 150f, monitorHeightPx = 188, footerHeightPx = 38))
  }

  @Test fun `details exclude inference settings already shown as badges`() {
    val details = floatingMonitorDetails(
      FloatingInferenceSettings(
        temperature = 0.75,
        maxTokens = 32_000,
        topK = 40,
        topP = 0.9,
        thinkingEnabled = true,
        thinkingBudget = 128,
      ),
    )

    assertEquals(
      listOf("TEMPERATURE", "TOP P", "TOP K", "MAX TOKENS", "THINKING BUDGET"),
      details.map(AdditionalMonitorDetail::label),
    )
    assertFalse(details.any { it.label == "THINKING" })
    assertEquals(listOf(1, 1, 1, 1, 2), details.map(AdditionalMonitorDetail::columnSpan))
  }

  @Test fun `formats decode speed with its unit`() {
    assertEquals("9.8 t/s", formatMonitorSpeed(9.84))
    assertEquals("—", formatMonitorSpeed(0.0))
  }

  @Test fun `keeps metric values within the available card width`() {
    val fitted = fitMonitorMetricText(
      value = "100 t/s",
      availableWidthPx = 40f,
      measureWidth = { text, sizeDp -> text.length * sizeDp.toFloat() / 2f },
    )

    assertEquals("100 t/s", fitted.value)
    assertTrue(fitted.sizeDp < 16)
  }

  @Test fun `keeps five digit max tokens in a standard detail card`() {
    val fitted = fitMonitorMetricText(
      value = "32000",
      availableWidthPx = 40f,
      measureWidth = { text, sizeDp -> text.length * sizeDp.toFloat() / 2f },
    )

    assertEquals("32000", fitted.value)
  }

  @Test fun `shortens metric values only when the minimum size cannot fit`() {
    val fitted = fitMonitorMetricText(
      value = "123456789 ms",
      availableWidthPx = 20f,
      measureWidth = { text, sizeDp -> text.length * sizeDp.toFloat() },
    )

    assertEquals("1…", fitted.value)
    assertEquals(8, fitted.sizeDp)
  }
}
