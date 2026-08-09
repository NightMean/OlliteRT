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

import com.ollitert.llm.server.common.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
  fun `processing elapsed caps at 999 seconds and drops only the capped inline unit`() {
    assertEquals(FloatingMonitorDurationText(value = "0", inlineUnit = "s"), formatProcessingElapsed(0))
    assertEquals(FloatingMonitorDurationText(value = "59", inlineUnit = "s"), formatProcessingElapsed(59_999))
    assertEquals(FloatingMonitorDurationText(value = "999", inlineUnit = "s"), formatProcessingElapsed(999_999))
    assertEquals(FloatingMonitorDurationText(value = "999+", inlineUnit = null), formatProcessingElapsed(1_000_000))
    assertEquals(FloatingMonitorDurationText(value = "999+", inlineUnit = null), formatProcessingElapsed(Long.MAX_VALUE))
  }

  @Test
  fun `last latency uses tenths below one hundred seconds and integers through 999`() {
    assertEquals(FloatingMonitorLatencyText(value = "—", unit = null), formatLastLatency(0))
    assertEquals(FloatingMonitorLatencyText(value = "0.8", unit = "s"), formatLastLatency(842))
    assertEquals(FloatingMonitorLatencyText(value = "9.9", unit = "s"), formatLastLatency(9_999))
    assertEquals(FloatingMonitorLatencyText(value = "12.4", unit = "s"), formatLastLatency(12_449))
    assertEquals(FloatingMonitorLatencyText(value = "99.9", unit = "s"), formatLastLatency(99_999))
    assertEquals(FloatingMonitorLatencyText(value = "100", unit = "s"), formatLastLatency(100_000))
    assertEquals(FloatingMonitorLatencyText(value = "999", unit = "s"), formatLastLatency(999_999))
    assertEquals(FloatingMonitorLatencyText(value = "999+", unit = "s", inlineUnit = null), formatLastLatency(1_000_000))
    assertEquals(FloatingMonitorLatencyText(value = "999+", unit = "s", inlineUnit = null), formatLastLatency(Long.MAX_VALUE))
  }

  @Test
  fun `render model uses truthful state-specific metrics`() {
    assertNull(
      deriveFloatingMonitorRenderModel(
        visualState = FloatingMonitorVisualState.Hidden,
        requestCount = 123,
        errorCount = 4,
        processingElapsedMillis = 5_000,
      )
    )

    assertEquals(
      FloatingMonitorRenderModel(
        visualState = FloatingMonitorVisualState.Running,
        requestValue = "1,234",
        secondaryValue = "5",
        secondaryLabel = "err",
        lastLatency = null,
      ),
      deriveFloatingMonitorRenderModel(
        visualState = FloatingMonitorVisualState.Running,
        requestCount = 1_234,
        errorCount = 5,
        processingElapsedMillis = null,
        lastLatencyMs = 842,
      ),
    )

    assertEquals(
      FloatingMonitorRenderModel(
        visualState = FloatingMonitorVisualState.Processing,
        requestValue = "99,999+",
        secondaryValue = "61",
        secondaryLabel = "proc",
        secondaryUnit = "s",
        lastLatency = FloatingMonitorLatencyText(value = "0.8", unit = "s"),
      ),
      deriveFloatingMonitorRenderModel(
        visualState = FloatingMonitorVisualState.Processing,
        requestCount = 100_000,
        errorCount = 999,
        processingElapsedMillis = 61_000,
        lastLatencyMs = 842,
      ),
    )
  }

  @Test
  fun `processing render model keeps the unit only for values below the cap`() {
    val exact = requireNotNull(
      deriveFloatingMonitorRenderModel(
        visualState = FloatingMonitorVisualState.Processing,
        requestCount = 1,
        errorCount = 0,
        processingElapsedMillis = 999_999,
      )
    )
    val capped = requireNotNull(
      deriveFloatingMonitorRenderModel(
        visualState = FloatingMonitorVisualState.Processing,
        requestCount = 1,
        errorCount = 0,
        processingElapsedMillis = 1_000_000,
      )
    )

    assertEquals("999", exact.secondaryValue)
    assertEquals("s", exact.secondaryUnit)
    assertEquals("999+", capped.secondaryValue)
    assertNull(capped.secondaryUnit)
  }

  @Test
  fun `content description uses full state metric and unit names`() {
    val running = FloatingMonitorRenderModel(
      visualState = FloatingMonitorVisualState.Running,
      requestValue = "1,234",
      secondaryValue = "5",
      secondaryLabel = "err",
      lastLatency = null,
    )
    val processing = FloatingMonitorRenderModel(
      visualState = FloatingMonitorVisualState.Processing,
      requestValue = "99,999+",
      secondaryValue = "61",
      secondaryLabel = "proc",
      lastLatency = FloatingMonitorLatencyText(value = "0.8", unit = "s"),
    )
    val firstProcessing = processing.copy(
      secondaryValue = "0",
      lastLatency = FloatingMonitorLatencyText(value = "—", unit = null),
    )
    val cappedLastProcessing = processing.copy(
      lastLatency = FloatingMonitorLatencyText(value = "999+", unit = "s", inlineUnit = null),
    )

    assertEquals(
      "Running, requests 1,234, errors 5",
      floatingMonitorContentDescription(running),
    )
    assertEquals(
      "Processing, requests 99,999+, current processing 61 seconds, last successful latency 0.8 seconds",
      floatingMonitorContentDescription(processing),
    )
    assertEquals(
      "Processing, requests 99,999+, current processing 0 seconds, last successful latency unavailable",
      floatingMonitorContentDescription(firstProcessing),
    )
    assertEquals(
      "Processing, requests 99,999+, current processing 61 seconds, last successful latency 999+ seconds",
      floatingMonitorContentDescription(cappedLastProcessing),
    )
  }

  @Test
  fun `running server without inference maps to running monitor`() {
    assertEquals(
      FloatingMonitorVisualState.Running,
      deriveFloatingMonitorVisualState(
        status = ServerStatus.RUNNING,
        isInferring = false,
      ),
    )
  }

  @Test
  fun `running server with inference maps to processing monitor`() {
    assertEquals(
      FloatingMonitorVisualState.Processing,
      deriveFloatingMonitorVisualState(
        status = ServerStatus.RUNNING,
        isInferring = true,
      ),
    )
  }

  @Test
  fun `monitor is visible when every visibility gate is satisfied`() {
    assertTrue(showMonitor())
  }

  @Test
  fun `non-running server states are hidden regardless of inference flag`() {
    val nonRunningStates = listOf(ServerStatus.STOPPED, ServerStatus.LOADING, ServerStatus.ERROR)

    for (status in nonRunningStates) {
      assertEquals(
        FloatingMonitorVisualState.Hidden,
        deriveFloatingMonitorVisualState(status = status, isInferring = false),
      )
      assertEquals(
        FloatingMonitorVisualState.Hidden,
        deriveFloatingMonitorVisualState(status = status, isInferring = true),
      )
    }
  }

  @Test
  fun `monitor is hidden when any visibility gate fails`() {
    assertFalse(showMonitor(settingEnabled = false))
    assertFalse(showMonitor(overlayPermissionGranted = false))
    assertFalse(showMonitor(permissionFlowInProgress = true))
    assertFalse(showMonitor(appIsForeground = true))
    assertFalse(showMonitor(launchSuppressionActive = true))
    assertFalse(showMonitor(serviceIsAlive = false))
    assertFalse(showMonitor(visualState = FloatingMonitorVisualState.Hidden))
  }

  private fun showMonitor(
    settingEnabled: Boolean = true,
    overlayPermissionGranted: Boolean = true,
    permissionFlowInProgress: Boolean = false,
    appIsForeground: Boolean = false,
    launchSuppressionActive: Boolean = false,
    serviceIsAlive: Boolean = true,
    visualState: FloatingMonitorVisualState = FloatingMonitorVisualState.Running,
  ): Boolean = shouldShowFloatingMonitor(
    settingEnabled = settingEnabled,
    overlayPermissionGranted = overlayPermissionGranted,
    permissionFlowInProgress = permissionFlowInProgress,
    appIsForeground = appIsForeground,
    launchSuppressionActive = launchSuppressionActive,
    serviceIsAlive = serviceIsAlive,
    visualState = visualState,
  )
}
