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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMonitorStateTest {

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
    val nonRunningStates =
      listOf(ServerStatus.STOPPED, ServerStatus.LOADING, ServerStatus.ERROR)

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

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `reported successful launch without foreground does not hide monitor indefinitely`() = runTest {
    val suppression = FloatingMonitorTapSuppression(
      scope = this,
      timeoutMillis = 3_000L,
    )

    suppression.suppress()
    assertFalse(showMonitor(launchSuppressionActive = suppression.active.value))

    advanceTimeBy(2_999L)
    runCurrent()
    assertFalse(showMonitor(launchSuppressionActive = suppression.active.value))

    advanceTimeBy(1L)
    runCurrent()
    assertTrue(showMonitor(launchSuppressionActive = suppression.active.value))
  }

  private fun showMonitor(
    settingEnabled: Boolean = true,
    overlayPermissionGranted: Boolean = true,
    permissionFlowInProgress: Boolean = false,
    appIsForeground: Boolean = false,
    launchSuppressionActive: Boolean = false,
    serviceIsAlive: Boolean = true,
    visualState: FloatingMonitorVisualState = FloatingMonitorVisualState.Running,
  ): Boolean =
    shouldShowFloatingMonitor(
      settingEnabled = settingEnabled,
      overlayPermissionGranted = overlayPermissionGranted,
      permissionFlowInProgress = permissionFlowInProgress,
      appIsForeground = appIsForeground,
      launchSuppressionActive = launchSuppressionActive,
      serviceIsAlive = serviceIsAlive,
      visualState = visualState,
    )
}
