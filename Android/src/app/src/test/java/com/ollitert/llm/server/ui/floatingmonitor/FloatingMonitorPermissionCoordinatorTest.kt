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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMonitorPermissionCoordinatorTest {

  @Test
  fun `permission flow state is observable from begin until end`() {
    val coordinator = FloatingMonitorPermissionCoordinator()

    assertFalse(coordinator.permissionFlowInProgress.value)

    coordinator.beginPermissionFlow()
    assertTrue(coordinator.permissionFlowInProgress.value)

    coordinator.endPermissionFlow()
    assertFalse(coordinator.permissionFlowInProgress.value)
  }

  @Test
  fun `observed overlay permission is independent from user enable intent`() {
    val coordinator = FloatingMonitorPermissionCoordinator()

    assertFalse(coordinator.overlayPermissionGranted.value)
    coordinator.updateObservedPermission(granted = true)
    assertTrue(coordinator.overlayPermissionGranted.value)
    coordinator.updateObservedPermission(granted = false)
    assertFalse(coordinator.overlayPermissionGranted.value)
  }
}
