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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMonitorRetryActivationTest {
  private val base = FloatingMonitorRetryActivationKey(
    status = ServerStatus.RUNNING,
    isInferring = false,
    inferenceSequence = 0L,
    appIsForeground = false,
    settingEnabled = true,
    permissionFlowInProgress = false,
    overlayPermissionGranted = true,
    launchSuppressionActive = false,
  )

  @Test
  fun `initial and qualifying input changes reactivate budget`() {
    assertTrue(
      shouldReactivateFloatingMonitorRetryBudget(
        previous = null,
        next = base,
        ignoreLaunchFailureRelease = false,
      ),
    )
    assertTrue(
      shouldReactivateFloatingMonitorRetryBudget(
        previous = base,
        next = base.copy(launchSuppressionActive = true),
        ignoreLaunchFailureRelease = false,
      ),
    )
    assertTrue(
      shouldReactivateFloatingMonitorRetryBudget(
        previous = base,
        next = base.copy(overlayPermissionGranted = false),
        ignoreLaunchFailureRelease = false,
      ),
    )
  }

  @Test
  fun `unchanged input does not reactivate budget`() {
    assertFalse(
      shouldReactivateFloatingMonitorRetryBudget(
        previous = base,
        next = base,
        ignoreLaunchFailureRelease = false,
      ),
    )
  }

  @Test
  fun `synchronous launch failure release does not reactivate budget`() {
    val suppressed = base.copy(launchSuppressionActive = true)

    assertFalse(
      shouldReactivateFloatingMonitorRetryBudget(
        previous = suppressed,
        next = base,
        ignoreLaunchFailureRelease = true,
      ),
    )
  }

  @Test
  fun `other qualifying change still reactivates during launch failure release`() {
    val suppressed = base.copy(launchSuppressionActive = true)

    assertTrue(
      shouldReactivateFloatingMonitorRetryBudget(
        previous = suppressed,
        next = base.copy(appIsForeground = true),
        ignoreLaunchFailureRelease = true,
      ),
    )
  }
}
