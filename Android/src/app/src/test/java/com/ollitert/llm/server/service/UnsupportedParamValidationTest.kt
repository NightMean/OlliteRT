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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsupportedParamValidationTest {

  // ── n parameter ────────────────────────────────────────────────────────

  @Test
  fun `n null is valid`() {
    assertNull(validateNParam(null))
  }

  @Test
  fun `n equal to 1 is valid`() {
    assertNull(validateNParam(1))
  }

  @Test
  fun `n equal to 0 returns error`() {
    val error = validateNParam(0)!!
    assertEquals("n", error.first)
    assertTrue(error.second.contains("must be >= 1"))
  }

  @Test
  fun `n greater than 1 returns error`() {
    val error = validateNParam(2)!!
    assertEquals("n", error.first)
    assertTrue(error.second.contains("n"))
  }

  @Test
  fun `n large value returns error`() {
    val error = validateNParam(128)!!
    assertEquals("n", error.first)
  }

  // ── best_of parameter ──────────────────────────────────────────────────

  @Test
  fun `best_of null is valid`() {
    assertNull(validateBestOfParam(null))
  }

  @Test
  fun `best_of equal to 1 is valid`() {
    assertNull(validateBestOfParam(1))
  }

  @Test
  fun `best_of greater than 1 returns error`() {
    val error = validateBestOfParam(2)!!
    assertEquals("best_of", error.first)
    assertTrue(error.second.contains("best_of"))
  }
}
