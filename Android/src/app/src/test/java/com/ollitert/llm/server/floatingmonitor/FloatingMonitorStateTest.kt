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

  @Test fun `dot prioritizes processing and cpu retry`() {
    assertEquals(FloatingMonitorDot.PROCESSING, floatingMonitorDot(ServerStatus.RUNNING, true, ModelLoadPhase.STARTING))
    assertEquals(FloatingMonitorDot.RETRYING_CPU, floatingMonitorDot(ServerStatus.LOADING, false, ModelLoadPhase.RETRYING_CPU))
    assertEquals(FloatingMonitorDot.RUNNING, floatingMonitorDot(ServerStatus.RUNNING, false, ModelLoadPhase.STARTING))
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
}
