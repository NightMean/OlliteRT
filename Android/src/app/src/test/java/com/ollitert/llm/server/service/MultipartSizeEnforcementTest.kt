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

package com.ollitert.llm.server.service

import kotlinx.io.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class MultipartSizeEnforcementTest {

  @Test
  fun `readBytesWithLimit returns bytes when under limit`() {
    val data = ByteArray(100) { it.toByte() }
    val buffer = Buffer().apply { write(data) }
    val result = readBytesWithLimit(buffer, maxBytes = 200L)
    assertArrayEquals(data, result)
  }

  @Test
  fun `readBytesWithLimit returns bytes when exactly at limit`() {
    val data = ByteArray(100) { it.toByte() }
    val buffer = Buffer().apply { write(data) }
    val result = readBytesWithLimit(buffer, maxBytes = 100L)
    assertArrayEquals(data, result)
  }

  @Test
  fun `readBytesWithLimit throws IOException when over limit`() {
    val data = ByteArray(150) { it.toByte() }
    val buffer = Buffer().apply { write(data) }
    val ex = assertThrows(IOException::class.java) {
      readBytesWithLimit(buffer, maxBytes = 100L)
    }
    assertEquals("File exceeds 100 byte limit", ex.message)
  }

  @Test
  fun `readBytesWithLimit handles empty source`() {
    val buffer = Buffer()
    val result = readBytesWithLimit(buffer, maxBytes = 100L)
    assertEquals(0, result.size)
  }

  @Test
  fun `readBytesWithLimit throws on single byte over limit`() {
    val data = ByteArray(101) { it.toByte() }
    val buffer = Buffer().apply { write(data) }
    assertThrows(IOException::class.java) {
      readBytesWithLimit(buffer, maxBytes = 100L)
    }
  }
}
