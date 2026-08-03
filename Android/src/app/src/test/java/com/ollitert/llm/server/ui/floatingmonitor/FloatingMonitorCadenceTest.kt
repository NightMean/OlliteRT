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
import org.junit.Test

class FloatingMonitorCadenceTest {
  @Test
  fun `first visible frame renders immediately`() {
    assertEquals(
      0L,
      floatingMonitorVisibleRenderDelayMillis(
        wasVisible = false,
        lastRenderAtMillis = Long.MIN_VALUE,
        nowMillis = 100L,
      ),
    )
  }

  @Test
  fun `visible state emissions share one second cadence`() {
    assertEquals(
      900L,
      floatingMonitorVisibleRenderDelayMillis(
        wasVisible = true,
        lastRenderAtMillis = 1_000L,
        nowMillis = 1_100L,
      ),
    )
    assertEquals(
      0L,
      floatingMonitorVisibleRenderDelayMillis(
        wasVisible = true,
        lastRenderAtMillis = 1_000L,
        nowMillis = 2_000L,
      ),
    )
  }

  @Test
  fun `clock rollback never creates a negative delay`() {
    assertEquals(
      FLOATING_MONITOR_REFRESH_MILLIS,
      floatingMonitorVisibleRenderDelayMillis(
        wasVisible = true,
        lastRenderAtMillis = 2_000L,
        nowMillis = 1_000L,
      ),
    )
  }
}
