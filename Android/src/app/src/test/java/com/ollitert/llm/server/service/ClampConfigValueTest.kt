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

import com.ollitert.llm.server.data.MAX_MAX_TOKENS
import com.ollitert.llm.server.data.MAX_TEMPERATURE
import com.ollitert.llm.server.data.MAX_TOPK
import com.ollitert.llm.server.data.MAX_TOPP
import com.ollitert.llm.server.data.MIN_MAX_TOKENS
import com.ollitert.llm.server.data.MIN_TEMPERATURE
import com.ollitert.llm.server.data.MIN_TOPK
import com.ollitert.llm.server.data.MIN_TOPP
import org.junit.Assert.assertEquals
import org.junit.Test

class ClampConfigValueTest {

  // ── Temperature ──────────────────────────────────────────────────────────

  @Test
  fun temperatureInRangeUnchanged() {
    assertEquals(1.0f, clampTemperature(1.0))
  }

  @Test
  fun temperatureBelowMinClampedToMin() {
    assertEquals(MIN_TEMPERATURE, clampTemperature(-5.0))
  }

  @Test
  fun temperatureAboveMaxClampedToMax() {
    assertEquals(MAX_TEMPERATURE, clampTemperature(999.0))
  }

  @Test
  fun temperatureAtBoundaries() {
    assertEquals(MIN_TEMPERATURE, clampTemperature(MIN_TEMPERATURE.toDouble()))
    assertEquals(MAX_TEMPERATURE, clampTemperature(MAX_TEMPERATURE.toDouble()))
  }

  // ── Top-P ────────────────────────────────────────────────────────────────

  @Test
  fun topPInRangeUnchanged() {
    assertEquals(0.5f, clampTopP(0.5))
  }

  @Test
  fun topPBelowMinClampedToMin() {
    assertEquals(MIN_TOPP, clampTopP(-1.0))
  }

  @Test
  fun topPAboveMaxClampedToMax() {
    assertEquals(MAX_TOPP, clampTopP(5.0))
  }

  // ── Top-K ────────────────────────────────────────────────────────────────

  @Test
  fun topKInRangeUnchanged() {
    assertEquals(40, clampTopK(40))
  }

  @Test
  fun topKBelowMinClampedToMin() {
    assertEquals(MIN_TOPK, clampTopK(-1))
  }

  @Test
  fun topKAboveMaxClampedToMax() {
    assertEquals(MAX_TOPK, clampTopK(999999))
  }

  // ── Max Tokens ───────────────────────────────────────────────────────────

  @Test
  fun maxTokensInRangeUnchanged() {
    assertEquals(1024, clampMaxTokens(1024))
  }

  @Test
  fun maxTokensBelowMinClampedToMin() {
    assertEquals(MIN_MAX_TOKENS, clampMaxTokens(-1))
  }

  @Test
  fun maxTokensAboveMaxClampedToMax() {
    assertEquals(MAX_MAX_TOKENS, clampMaxTokens(999999))
  }

  @Test
  fun maxTokensZeroClampedToMin() {
    assertEquals(MIN_MAX_TOKENS, clampMaxTokens(0))
  }
}
