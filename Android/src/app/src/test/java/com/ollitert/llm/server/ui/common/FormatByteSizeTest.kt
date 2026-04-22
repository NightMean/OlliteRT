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

import com.ollitert.llm.server.common.formatByteSize
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatByteSizeTest {

  @Test
  fun `formatByteSize - bytes range`() {
    assertEquals("0 B", formatByteSize(0L))
    assertEquals("512 B", formatByteSize(512L))
    assertEquals("1023 B", formatByteSize(1023L))
  }

  @Test
  fun `formatByteSize - KB range`() {
    assertEquals("1.0 KB", formatByteSize(1024L))
    assertEquals("1.5 KB", formatByteSize(1536L))
    assertEquals("1023.9 KB", formatByteSize(1024L * 1024L - 100))
  }

  @Test
  fun `formatByteSize - MB range`() {
    assertEquals("1.0 MB", formatByteSize(1024L * 1024L))
    assertEquals("2.5 MB", formatByteSize((2.5 * 1024 * 1024).toLong()))
  }

  @Test
  fun `formatByteSize - Int overload delegates to Long`() {
    assertEquals("512 B", formatByteSize(512))
    assertEquals("1.0 KB", formatByteSize(1024))
  }
}
