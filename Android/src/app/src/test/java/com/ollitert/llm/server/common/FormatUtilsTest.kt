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

class FormatUtilsTest {

  @Test
  fun `humanReadableSize formats bytes correctly`() {
    assertEquals("500 B", 500L.humanReadableSize())
    assertEquals("1.0 kB", 1000L.humanReadableSize(si = true))
    assertEquals("1.0 KiB", 1024L.humanReadableSize(si = false))
    assertEquals("1.5 MB", (1_500_000L).humanReadableSize(si = true))
    assertEquals("2.00 GB", (2_000_000_000L).humanReadableSize(si = true, extraDecimalForGbAndAbove = true))
  }

  @Test
  fun `bytesToGb and bytesToMb convert correctly`() {
    val oneGb = 1024L * 1024L * 1024L
    assertEquals(1.0f, oneGb.bytesToGb(), 0.001f)

    val fiveHundredMb = 500L * 1024L * 1024L
    assertEquals(500L, fiveHundredMb.bytesToMb())
  }

  @Test
  fun `cleanUpLiteRtErrorMessage strips source location trace`() {
    val raw = "Model inference failed at runtime\n=== Source Location Trace: /build/litert/runtime.cc:123"
    assertEquals("Model inference failed at runtime\n", cleanUpLiteRtErrorMessage(raw))
  }
}
