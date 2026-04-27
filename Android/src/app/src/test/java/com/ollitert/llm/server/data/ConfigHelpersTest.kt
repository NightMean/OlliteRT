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

package com.ollitert.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigHelpersTest {

  private val key = ConfigKeys.MAX_TOKENS.label

  // ── maxTokensInt ────────────────────────────────────────────────────────

  @Test
  fun maxTokensIntFromInt() {
    assertEquals(4096, mapOf(key to 4096).maxTokensInt())
  }

  @Test
  fun maxTokensIntFromFloat() {
    assertEquals(4096, mapOf(key to 4096.0f).maxTokensInt())
  }

  @Test
  fun maxTokensIntFromDouble() {
    assertEquals(2048, mapOf(key to 2048.0).maxTokensInt())
  }

  @Test
  fun maxTokensIntFromLong() {
    assertEquals(8192, mapOf(key to 8192L).maxTokensInt())
  }

  @Test
  fun maxTokensIntMissingKeyReturnsNull() {
    assertNull(emptyMap<String, Any>().maxTokensInt())
  }

  @Test
  fun maxTokensIntNonNumberReturnsNull() {
    assertNull(mapOf(key to "notANumber").maxTokensInt())
  }

  // ── maxTokensLong ───────────────────────────────────────────────────────

  @Test
  fun maxTokensLongFromInt() {
    assertEquals(4096L, mapOf(key to 4096).maxTokensLong())
  }

  @Test
  fun maxTokensLongFromFloat() {
    assertEquals(4096L, mapOf(key to 4096.0f).maxTokensLong())
  }

  @Test
  fun maxTokensLongMissingKeyReturnsNull() {
    assertNull(emptyMap<String, Any>().maxTokensLong())
  }
}
