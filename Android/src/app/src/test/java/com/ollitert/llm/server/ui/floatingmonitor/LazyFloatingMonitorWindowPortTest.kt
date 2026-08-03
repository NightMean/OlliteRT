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

class LazyFloatingMonitorWindowPortTest {

  @Test
  fun `hidden disabled path does not allocate platform window`() {
    var factoryCalls = 0
    val port = LazyFloatingMonitorWindowPort {
      factoryCalls += 1
      RecordingWindowPort()
    }

    assertFalse(port.isAttached)
    port.detach()
    port.deactivate()

    assertEquals(0, factoryCalls)
  }

  @Test
  fun `first visible attach allocates once and delegates lifecycle`() {
    var factoryCalls = 0
    val delegate = RecordingWindowPort()
    val port = LazyFloatingMonitorWindowPort {
      factoryCalls += 1
      delegate
    }
    val model = FloatingMonitorRenderModel(
      visualState = FloatingMonitorVisualState.Running,
      requestValue = "1",
      secondaryValue = "0",
      secondaryLabel = "err",
    )

    port.attach(model)
    port.update(model.copy(requestValue = "2"))
    port.detach()
    port.deactivate()

    assertEquals(1, factoryCalls)
    assertEquals(1, delegate.attachCalls)
    assertEquals(1, delegate.updateCalls)
    assertEquals(1, delegate.detachCalls)
    assertEquals(1, delegate.deactivateCalls)
    assertFalse(port.isAttached)
  }

  private class RecordingWindowPort : FloatingMonitorWindowPort {
    override var isAttached: Boolean = false
      private set
    var attachCalls = 0
    var updateCalls = 0
    var detachCalls = 0
    var deactivateCalls = 0

    override fun attach(model: FloatingMonitorRenderModel) {
      attachCalls += 1
      isAttached = true
    }

    override fun update(model: FloatingMonitorRenderModel) {
      updateCalls += 1
    }

    override fun detach() {
      detachCalls += 1
      isAttached = false
    }

    override fun deactivate() {
      deactivateCalls += 1
    }
  }
}
