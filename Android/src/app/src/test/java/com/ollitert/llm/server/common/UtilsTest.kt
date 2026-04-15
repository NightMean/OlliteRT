package com.ollitert.llm.server.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for pure utility functions in Utils.kt that have no Android dependencies.
 * Functions requiring Context (Wifi, Bitmap, Uri) are excluded.
 */
class UtilsTest {

  // ── cleanUpMediapipeTaskErrorMessage() ────────────────────────────────────

  @Test
  fun cleanUpMediapipeTaskErrorMessageTrimsTrace() {
    val msg = "Model failed to load=== Source Location Trace: file.cc:123\nmore stack data"
    assertEquals("Model failed to load", cleanUpMediapipeTaskErrorMessage(msg))
  }

  @Test
  fun cleanUpMediapipeTaskErrorMessageNoTraceReturnsUnchanged() {
    val msg = "Some normal error message"
    assertEquals(msg, cleanUpMediapipeTaskErrorMessage(msg))
  }

  @Test
  fun cleanUpMediapipeTaskErrorMessageEmptyString() {
    assertEquals("", cleanUpMediapipeTaskErrorMessage(""))
  }

  @Test
  fun cleanUpMediapipeTaskErrorMessageTraceAtStart() {
    val msg = "=== Source Location Trace: everything after"
    assertEquals("", cleanUpMediapipeTaskErrorMessage(msg))
  }

  @Test
  fun cleanUpMediapipeTaskErrorMessagePreservesTextBeforeTrace() {
    val msg = "Error: context overflow (6579 >= 4000)=== Source Location Trace: ..."
    assertEquals("Error: context overflow (6579 >= 4000)", cleanUpMediapipeTaskErrorMessage(msg))
  }

}
