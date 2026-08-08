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

class FloatingMonitorFormatterTest {

  @Test
  fun `counts stay exact through five digits and cap honestly`() {
    assertEquals("0", formatFloatingMonitorCount(0))
    assertEquals("999", formatFloatingMonitorCount(999))
    assertEquals("1,000", formatFloatingMonitorCount(1_000))
    assertEquals("12,345", formatFloatingMonitorCount(12_345))
    assertEquals("99,999", formatFloatingMonitorCount(99_999))
    assertEquals("99,999+", formatFloatingMonitorCount(100_000))
    assertEquals("99,999+", formatFloatingMonitorCount(Long.MAX_VALUE))
  }

  @Test
  fun `processing elapsed always uses seconds without an inline suffix`() {
    assertEquals("0", formatProcessingElapsed(0))
    assertEquals("59", formatProcessingElapsed(59_999))
    assertEquals("60", formatProcessingElapsed(60_000))
    assertEquals("3599", formatProcessingElapsed(3_599_000))
    assertEquals("9999", formatProcessingElapsed(9_999_999))
    assertEquals("9999+", formatProcessingElapsed(10_000_000))
    assertEquals("9999+", formatProcessingElapsed(Long.MAX_VALUE))
  }

  @Test
  fun `previous successful latency always uses one decimal seconds`() {
    assertEquals(FloatingMonitorLatencyText(value = "—", unit = null), formatPreviousSuccessfulLatency(0))
    assertEquals(FloatingMonitorLatencyText(value = "0.8", unit = "s"), formatPreviousSuccessfulLatency(842))
    assertEquals(FloatingMonitorLatencyText(value = "9.9", unit = "s"), formatPreviousSuccessfulLatency(9_999))
    assertEquals(FloatingMonitorLatencyText(value = "10.0", unit = "s"), formatPreviousSuccessfulLatency(10_000))
    assertEquals(FloatingMonitorLatencyText(value = "12.4", unit = "s"), formatPreviousSuccessfulLatency(12_449))
    assertEquals(FloatingMonitorLatencyText(value = "999.9", unit = "s"), formatPreviousSuccessfulLatency(999_999))
    assertEquals(FloatingMonitorLatencyText(value = "999+", unit = "s"), formatPreviousSuccessfulLatency(1_000_000))
    assertEquals(FloatingMonitorLatencyText(value = "999+", unit = "s"), formatPreviousSuccessfulLatency(Long.MAX_VALUE))
  }
}
