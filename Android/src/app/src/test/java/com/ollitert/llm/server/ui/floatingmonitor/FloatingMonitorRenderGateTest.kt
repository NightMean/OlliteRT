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

class FloatingMonitorRenderGateTest {

  @Test
  fun `equal model is skipped until the gate is reset`() {
    val gate = FloatingMonitorRenderGate()
    val running = model(FloatingMonitorVisualState.Running)
    val processing = model(FloatingMonitorVisualState.Processing)

    assertTrue(gate.renderIfChanged(running) {})
    assertFalse(gate.renderIfChanged(running) {})
    assertTrue(gate.renderIfChanged(processing) {})

    gate.reset()

    assertTrue(gate.renderIfChanged(processing) {})
  }

  @Test
  fun `failed render is not cached`() {
    val gate = FloatingMonitorRenderGate()
    val running = model(FloatingMonitorVisualState.Running)
    var attempts = 0

    runCatching {
      gate.renderIfChanged(running) {
        attempts += 1
        error("render")
      }
    }
    val rendered = gate.renderIfChanged(running) {
      attempts += 1
    }

    assertTrue(rendered)
    assertEquals(2, attempts)
  }

  private fun model(state: FloatingMonitorVisualState) =
    FloatingMonitorRenderModel(
      visualState = state,
      requestValue = "1",
      secondaryValue = "0",
      secondaryLabel = if (state == FloatingMonitorVisualState.Running) "err" else "proc",
    )
}
