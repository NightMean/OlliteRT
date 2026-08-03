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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FloatingMonitorTapCoordinatorTest {

  @Test
  fun `reported successful launch reconciles after bounded suppression`() = runTest {
    val events = mutableListOf<String>()
    val coordinator = FloatingMonitorTapCoordinator(
      scope = this,
      timeoutMillis = 3_000L,
      detach = { events += "detach" },
      launch = {
        events += "launch"
        true
      },
      reconcile = { events += "reconcile" },
    )

    coordinator.handleTap()

    assertEquals(listOf("detach", "launch"), events)
    assertTrue(coordinator.suppressionActive.value)

    advanceTimeBy(2_999L)
    runCurrent()
    assertEquals(listOf("detach", "launch"), events)
    assertTrue(coordinator.suppressionActive.value)

    advanceTimeBy(1L)
    runCurrent()
    assertEquals(listOf("detach", "launch", "reconcile"), events)
    assertFalse(coordinator.suppressionActive.value)
  }

  @Test
  fun `immediate total launch failure reconciles without a ticker`() = runTest {
    val events = mutableListOf<String>()
    val coordinator = FloatingMonitorTapCoordinator(
      scope = this,
      timeoutMillis = 3_000L,
      detach = { events += "detach" },
      launch = {
        events += "launch"
        false
      },
      reconcile = { events += "reconcile" },
      activateSuppressionInput = { events += "activate" },
      onLaunchFailed = { events += "launch_failed" },
    )

    coordinator.handleTap()

    assertEquals(
      listOf("activate", "detach", "launch", "launch_failed", "reconcile"),
      events,
    )
    assertFalse(coordinator.suppressionActive.value)
  }

  @Test
  fun `foreground confirmation releases suppression early`() = runTest {
    var reconciliations = 0
    val coordinator = FloatingMonitorTapCoordinator(
      scope = this,
      timeoutMillis = 3_000L,
      detach = {},
      launch = { true },
      reconcile = { reconciliations += 1 },
    )

    coordinator.handleTap()
    coordinator.onAppForegrounded()

    assertFalse(coordinator.suppressionActive.value)
    assertEquals(1, reconciliations)

    advanceTimeBy(3_000L)
    runCurrent()
    assertEquals(1, reconciliations)
  }

  @Test
  fun `dispose cancels pending suppression recovery`() = runTest {
    var reconciliations = 0
    val coordinator = FloatingMonitorTapCoordinator(
      scope = this,
      timeoutMillis = 3_000L,
      detach = {},
      launch = { true },
      reconcile = { reconciliations += 1 },
    )

    coordinator.handleTap()
    coordinator.dispose()

    assertFalse(coordinator.suppressionActive.value)

    advanceTimeBy(3_000L)
    runCurrent()
    assertEquals(0, reconciliations)
  }
}
