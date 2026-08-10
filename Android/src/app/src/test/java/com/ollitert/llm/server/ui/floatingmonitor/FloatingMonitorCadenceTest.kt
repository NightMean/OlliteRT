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
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMonitorCadenceTest {
  @Test
  fun `timed refresh interval remains one second`() {
    assertEquals(
      1_000L,
      FLOATING_MONITOR_REFRESH_MILLIS,
    )
  }

  @Test
  fun `only processing or a pending window retry needs a timed refresh`() {
    assertTrue(
      needsFloatingMonitorTimedRefresh(
        isInferring = true,
        hasPendingWindowRetry = false,
        isVisible = true,
      ),
    )
    assertTrue(
      needsFloatingMonitorTimedRefresh(
        isInferring = false,
        hasPendingWindowRetry = true,
        isVisible = false,
      ),
    )
    assertFalse(
      needsFloatingMonitorTimedRefresh(
        isInferring = false,
        hasPendingWindowRetry = false,
        isVisible = true,
      ),
    )
    assertFalse(
      needsFloatingMonitorTimedRefresh(
        isInferring = true,
        hasPendingWindowRetry = false,
        isVisible = false,
      ),
    )
  }

  @Test
  fun `teardown retry remains bounded after immediate detach fails`() {
    assertTrue(shouldRetryFloatingMonitorTeardown(detached = false, delayedAttempts = 0))
    assertTrue(shouldRetryFloatingMonitorTeardown(detached = false, delayedAttempts = 1))
    assertFalse(shouldRetryFloatingMonitorTeardown(detached = false, delayedAttempts = 2))
    assertFalse(shouldRetryFloatingMonitorTeardown(detached = true, delayedAttempts = 0))
  }
}
