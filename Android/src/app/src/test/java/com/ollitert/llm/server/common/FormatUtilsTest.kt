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

package com.ollitert.llm.server.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for pure utility functions in FormatUtils.kt that have no Android dependencies.
 */
class FormatUtilsTest {

  // ── cleanUpLiteRtErrorMessage() ────────────────────────────────────

  @Test
  fun cleanUpLiteRtErrorMessageTrimsTrace() {
    val msg = "Model failed to load=== Source Location Trace: file.cc:123\nmore stack data"
    assertEquals("Model failed to load", cleanUpLiteRtErrorMessage(msg))
  }

  @Test
  fun cleanUpLiteRtErrorMessageNoTraceReturnsUnchanged() {
    val msg = "Some normal error message"
    assertEquals(msg, cleanUpLiteRtErrorMessage(msg))
  }

  @Test
  fun cleanUpLiteRtErrorMessageEmptyString() {
    assertEquals("", cleanUpLiteRtErrorMessage(""))
  }

  @Test
  fun cleanUpLiteRtErrorMessageTraceAtStart() {
    val msg = "=== Source Location Trace: everything after"
    assertEquals("", cleanUpLiteRtErrorMessage(msg))
  }

  @Test
  fun cleanUpLiteRtErrorMessagePreservesTextBeforeTrace() {
    val msg = "Error: context overflow (6579 >= 4000)=== Source Location Trace: ..."
    assertEquals("Error: context overflow (6579 >= 4000)", cleanUpLiteRtErrorMessage(msg))
  }

  // ── humanReadableSize() ───────────────────────────────────────────

  @Test
  fun humanReadableSizeFormatsBytesCorrectly() {
    assertEquals("500 B", 500L.humanReadableSize())
    assertEquals("1.0 kB", 1000L.humanReadableSize(si = true))
    assertEquals("1.0 MB", 1_000_000L.humanReadableSize(si = true))
    assertEquals("1.50 GB", 1_500_000_000L.humanReadableSize(si = true, extraDecimalForGbAndAbove = true))
  }
}
