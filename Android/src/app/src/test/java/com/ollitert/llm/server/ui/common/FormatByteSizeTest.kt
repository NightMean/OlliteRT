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

package com.ollitert.llm.server.ui.common

import com.ollitert.llm.server.common.humanReadableSize
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatByteSizeTest {

  @Test
  fun `humanReadableSize - bytes range`() {
    assertEquals("0 B", 0L.humanReadableSize())
    assertEquals("512 B", 512L.humanReadableSize())
    assertEquals("999 B", 999L.humanReadableSize())
  }

  @Test
  fun `humanReadableSize - kB range`() {
    assertEquals("1.0 kB", 1000L.humanReadableSize())
    assertEquals("1.5 kB", 1500L.humanReadableSize())
    assertEquals("999.9 kB", 999_900L.humanReadableSize())
  }

  @Test
  fun `humanReadableSize - MB range`() {
    assertEquals("1.0 MB", 1_000_000L.humanReadableSize())
    assertEquals("2.5 MB", 2_500_000L.humanReadableSize())
  }

  @Test
  fun `humanReadableSize - GB range`() {
    assertEquals("1.0 GB", 1_000_000_000L.humanReadableSize())
    assertEquals("3.5 GB", 3_500_000_000L.humanReadableSize())
  }

  @Test
  fun `humanReadableSize - extra decimal for GB and above`() {
    assertEquals("1.00 GB", 1_000_000_000L.humanReadableSize(extraDecimalForGbAndAbove = true))
    assertEquals("1.5 kB", 1500L.humanReadableSize(extraDecimalForGbAndAbove = true))
    assertEquals("2.5 MB", 2_500_000L.humanReadableSize(extraDecimalForGbAndAbove = true))
  }

  @Test
  fun `humanReadableSize - binary mode`() {
    assertEquals("1.0 KiB", 1024L.humanReadableSize(si = false))
    assertEquals("1.0 MiB", (1024L * 1024L).humanReadableSize(si = false))
    assertEquals("1.0 GiB", (1024L * 1024L * 1024L).humanReadableSize(si = false))
  }

  @Test
  fun `humanReadableSize - Int overload delegates to Long`() {
    assertEquals("512 B", 512.humanReadableSize())
    assertEquals("1.0 kB", 1000.humanReadableSize())
  }
}
