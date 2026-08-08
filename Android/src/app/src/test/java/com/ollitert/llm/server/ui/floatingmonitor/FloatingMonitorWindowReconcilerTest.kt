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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMonitorWindowReconcilerTest {

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
  fun `visible models attach once update in place and hidden detaches once`() {
    val window = FakeWindow()
    val reconciler = FloatingMonitorWindowReconciler(window)
    val running = model(FloatingMonitorVisualState.Running)
    val processing = model(FloatingMonitorVisualState.Processing)

    reconciler.reconcile(running)
    reconciler.reconcile(processing)
    reconciler.reconcile(null)
    reconciler.reconcile(null)

    assertEquals(listOf("attach:Running", "update:Processing", "detach"), window.calls)
    assertFalse(window.isAttached)
  }

  @Test
  fun `window failures are contained and later reconcile can retry`() {
    val failures = mutableListOf<String>()
    val window = FakeWindow(failNextAttach = true)
    val reconciler = FloatingMonitorWindowReconciler(window) { failures += it.message.orEmpty() }
    val running = model(FloatingMonitorVisualState.Running)

    reconciler.reconcile(running)
    reconciler.reconcile(running)

    window.failNextUpdate = true
    reconciler.reconcile(running)
    reconciler.reconcile(running)

    assertEquals(
      listOf("attach", "attach:Running", "update", "detach", "attach:Running"),
      window.calls,
    )
    assertEquals(listOf("attach", "update"), failures)
  }

  @Test
  fun `dispose detaches and permanently suppresses later attach`() {
    val window = FakeWindow()
    val reconciler = FloatingMonitorWindowReconciler(window)

    reconciler.reconcile(model(FloatingMonitorVisualState.Running))
    reconciler.dispose()
    reconciler.reconcile(model(FloatingMonitorVisualState.Processing))

    assertEquals(listOf("attach:Running", "detach"), window.calls)
    assertFalse(window.isAttached)
  }

  @Test
  fun `attach failure is reported and later reconcile retries`() {
    val failures = mutableListOf<String>()
    val window = FakeWindow(failNextAttach = true)
    val reconciler = FloatingMonitorWindowReconciler(window) { failures += it.message.orEmpty() }

    assertFalse(reconciler.reconcile(model(FloatingMonitorVisualState.Running)))
    assertTrue(reconciler.reconcile(model(FloatingMonitorVisualState.Running)))

    assertEquals(listOf("attach", "attach:Running"), window.calls)
    assertEquals(listOf("attach"), failures)
    assertTrue(window.isAttached)
  }

  @Test
  fun `dispose retries a transient detach failure`() {
    val failures = mutableListOf<String>()
    val window = FakeWindow(detachFailuresRemaining = 1)
    val reconciler = FloatingMonitorWindowReconciler(window) { failures += it.message.orEmpty() }

    reconciler.reconcile(model(FloatingMonitorVisualState.Running))
    reconciler.dispose()

    assertEquals(listOf("attach:Running", "detach", "detach"), window.calls)
    assertEquals(listOf("detach"), failures)
    assertFalse(window.isAttached)
  }

  @Test
  fun `dispose deactivates an attached window after detach retries are exhausted`() {
    val window = FakeWindow(detachFailuresRemaining = 2)
    val reconciler = FloatingMonitorWindowReconciler(window)

    reconciler.reconcile(model(FloatingMonitorVisualState.Running))
    reconciler.dispose()

    assertEquals(listOf("attach:Running", "detach", "detach", "deactivate"), window.calls)
    assertTrue(window.isAttached)
    assertTrue(window.deactivated)
  }

  private fun model(state: FloatingMonitorVisualState) =
    FloatingMonitorRenderModel(
      visualState = state,
      requestValue = "1",
      secondaryValue = "0",
      secondaryLabel = if (state == FloatingMonitorVisualState.Running) "err" else "proc",
    )

  private class FakeWindow(
    private var failNextAttach: Boolean = false,
    private var detachFailuresRemaining: Int = 0,
  ) : FloatingMonitorWindowPort {
    override var isAttached: Boolean = false
      private set
    val calls = mutableListOf<String>()
    var failNextUpdate: Boolean = false
    var deactivated: Boolean = false
      private set

    override fun attach(model: FloatingMonitorRenderModel) {
      if (failNextAttach) {
        failNextAttach = false
        calls += "attach"
        throw IllegalStateException("attach")
      }
      isAttached = true
      calls += "attach:${model.visualState.name}"
    }

    override fun update(model: FloatingMonitorRenderModel) {
      if (failNextUpdate) {
        failNextUpdate = false
        calls += "update"
        throw IllegalStateException("update")
      }
      calls += "update:${model.visualState.name}"
    }

    override fun detach() {
      calls += "detach"
      if (detachFailuresRemaining > 0) {
        detachFailuresRemaining -= 1
        throw IllegalStateException("detach")
      }
      isAttached = false
    }

    override fun deactivate() {
      calls += "deactivate"
      deactivated = true
    }
  }
}
