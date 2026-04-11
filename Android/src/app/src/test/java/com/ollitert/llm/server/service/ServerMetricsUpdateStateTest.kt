package com.ollitert.llm.server.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests the update availability state in [ServerMetrics].
 * Verifies that update info persists across server stop/start cycles
 * (it's app-level state, not server-level state).
 */
class ServerMetricsUpdateStateTest {

  @Before
  fun setUp() {
    ServerMetrics.setAvailableUpdate(null, null)
  }

  @After
  fun tearDown() {
    ServerMetrics.setAvailableUpdate(null, null)
    ServerMetrics.onServerStopped()
  }

  @Test
  fun setAvailableUpdateStoresVersionAndUrl() {
    ServerMetrics.setAvailableUpdate("v1.3.0", "https://github.com/test/releases/tag/v1.3.0")

    assertEquals("v1.3.0", ServerMetrics.availableUpdateVersion.value)
    assertEquals("https://github.com/test/releases/tag/v1.3.0", ServerMetrics.availableUpdateUrl.value)
  }

  @Test
  fun clearAvailableUpdateSetsNull() {
    ServerMetrics.setAvailableUpdate("v1.3.0", "https://example.com")
    ServerMetrics.setAvailableUpdate(null, null)

    assertNull(ServerMetrics.availableUpdateVersion.value)
    assertNull(ServerMetrics.availableUpdateUrl.value)
  }

  @Test
  fun updateInfoPersistsAcrossServerStop() {
    // Update info should NOT be cleared when the server stops —
    // it's discovered by a background worker independent of the server lifecycle
    ServerMetrics.setAvailableUpdate("v1.3.0", "https://example.com")
    ServerMetrics.onServerStopped()

    assertEquals("v1.3.0", ServerMetrics.availableUpdateVersion.value)
    assertEquals("https://example.com", ServerMetrics.availableUpdateUrl.value)
  }

  @Test
  fun updateInfoPersistsAcrossServerRunningCycle() {
    ServerMetrics.setAvailableUpdate("v1.3.0", "https://example.com")
    ServerMetrics.onServerRunning("0.0.0.0:8000")
    ServerMetrics.onServerStopped()

    assertEquals("v1.3.0", ServerMetrics.availableUpdateVersion.value)
  }

  @Test
  fun updateVersionReplacedWithNewer() {
    ServerMetrics.setAvailableUpdate("v1.3.0", "https://example.com/v1.3.0")
    ServerMetrics.setAvailableUpdate("v1.4.0", "https://example.com/v1.4.0")

    assertEquals("v1.4.0", ServerMetrics.availableUpdateVersion.value)
    assertEquals("https://example.com/v1.4.0", ServerMetrics.availableUpdateUrl.value)
  }

  @Test
  fun initialStateIsNull() {
    assertNull(ServerMetrics.availableUpdateVersion.value)
    assertNull(ServerMetrics.availableUpdateUrl.value)
  }
}
