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

import com.ollitert.llm.server.data.CHARS_PER_TOKEN_ESTIMATE
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenEstimationTest {

  @Test
  fun `estimateTokens - empty string returns 0`() {
    assertEquals(0, estimateTokens(""))
  }

  @Test
  fun `estimateTokens - single char returns 1`() {
    assertEquals(1, estimateTokens("a"))
  }

  @Test
  fun `estimateTokens - chars below threshold returns 1`() {
    assertEquals(1, estimateTokens("ab"))
    assertEquals(1, estimateTokens("abc"))
  }

  @Test
  fun `estimateTokens - exactly one token worth of chars`() {
    val oneToken = "x".repeat(CHARS_PER_TOKEN_ESTIMATE)
    assertEquals(1, estimateTokens(oneToken))
  }

  @Test
  fun `estimateTokens - multiple tokens`() {
    val tenTokens = "x".repeat(CHARS_PER_TOKEN_ESTIMATE * 10)
    assertEquals(10, estimateTokens(tenTokens))
  }

  @Test
  fun `estimateTokens - fractional tokens truncated`() {
    val chars = CHARS_PER_TOKEN_ESTIMATE * 2 + 1
    assertEquals(2, estimateTokens("x".repeat(chars)))
  }

  @Test
  fun `estimateTokensByLength - zero returns 0`() {
    assertEquals(0, estimateTokensByLength(0))
  }

  @Test
  fun `estimateTokensByLength - positive below threshold returns 1`() {
    assertEquals(1, estimateTokensByLength(1))
    assertEquals(1, estimateTokensByLength(CHARS_PER_TOKEN_ESTIMATE - 1))
  }

  @Test
  fun `estimateTokensByLength - exact multiple`() {
    assertEquals(5, estimateTokensByLength(CHARS_PER_TOKEN_ESTIMATE * 5))
  }

  @Test
  fun `estimateTokensLong - returns Long type`() {
    val result: Long = estimateTokensLong("hello world test string")
    assertEquals(estimateTokens("hello world test string").toLong(), result)
  }

  @Test
  fun `estimateTokensLongByLength - returns Long type`() {
    val result: Long = estimateTokensLongByLength(100)
    assertEquals(estimateTokensByLength(100).toLong(), result)
  }
}
