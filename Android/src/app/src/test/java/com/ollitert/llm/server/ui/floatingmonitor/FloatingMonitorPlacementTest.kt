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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMonitorPlacementTest {

  @Test
  fun `movement must exceed touch slop before becoming drag`() {
    val tracker = FloatingMonitorGestureTracker(touchSlopPx = 10f)

    tracker.start(0f, 0f)
    assertFalse(tracker.move(6f, 8f))
    assertEquals(FloatingMonitorGestureResult.Tap, tracker.end())

    tracker.start(0f, 0f)
    assertTrue(tracker.move(6f, 8.1f))
    assertTrue(tracker.move(1f, 1f))
    assertEquals(FloatingMonitorGestureResult.Drag, tracker.end())
  }

  @Test
  fun `final up coordinate participates in drag classification`() {
    val tracker = FloatingMonitorGestureTracker(touchSlopPx = 10f)
    tracker.start(0f, 0f)

    assertEquals(FloatingMonitorGestureResult.Drag, tracker.end(11f, 0f))
  }

  @Test
  fun `cancel never becomes a tap`() {
    val tracker = FloatingMonitorGestureTracker(touchSlopPx = 10f)
    tracker.start(0f, 0f)
    tracker.cancel()

    assertEquals(FloatingMonitorGestureResult.Cancelled, tracker.end())
  }

  @Test
  fun `position clamps and round trips through normalized coordinates`() {
    val bounds = FloatingMonitorPlacementBounds(
      minX = 10,
      maxX = 110,
      minY = 20,
      maxY = 220,
    )

    assertEquals(FloatingMonitorPoint(10, 220), clampFloatingMonitorPosition(FloatingMonitorPoint(-5, 250), bounds))

    val normalized = normalizeFloatingMonitorPosition(FloatingMonitorPoint(60, 120), bounds)
    assertEquals(NormalizedFloatingMonitorPosition(0.5f, 0.5f), normalized)
    assertEquals(FloatingMonitorPoint(60, 120), restoreFloatingMonitorPosition(normalized, bounds))

    assertEquals(
      FloatingMonitorPoint(110, 20),
      restoreFloatingMonitorPosition(NormalizedFloatingMonitorPosition(2f, -1f), bounds),
    )
  }

  @Test
  fun `collapsed bounds stay finite and use their only valid point`() {
    val bounds = FloatingMonitorPlacementBounds(
      minX = 42,
      maxX = 42,
      minY = 99,
      maxY = 99,
    )

    assertEquals(
      NormalizedFloatingMonitorPosition(0f, 0f),
      normalizeFloatingMonitorPosition(FloatingMonitorPoint(500, -500), bounds),
    )
    assertEquals(
      FloatingMonitorPoint(42, 99),
      restoreFloatingMonitorPosition(NormalizedFloatingMonitorPosition(0.8f, 0.2f), bounds),
    )
  }

  @Test
  fun `default position respects right margin top offset and safe bounds`() {
    val bounds = FloatingMonitorPlacementBounds(
      minX = 10,
      maxX = 110,
      minY = 20,
      maxY = 90,
    )

    assertEquals(
      FloatingMonitorPoint(94, 90),
      defaultFloatingMonitorPosition(bounds, rightMarginPx = 16, topOffsetPx = 120),
    )
  }

  @Test
  fun `failed final drag stays pending and persists only after successful replay`() {
    val pending = FloatingMonitorPendingLayout(
      point = FloatingMonitorPoint(90, 120),
      persistAfterReplay = true,
    )

    assertEquals(pending, pending.afterReplay(succeeded = false))
    assertNull(pending.afterReplay(succeeded = true))
  }

  @Test
  fun `failed move replay never requests persistence`() {
    val pending = FloatingMonitorPendingLayout(
      point = FloatingMonitorPoint(90, 120),
      persistAfterReplay = false,
    )

    assertFalse(pending.persistAfterReplay)
    assertNull(pending.afterReplay(succeeded = true))
  }
}
