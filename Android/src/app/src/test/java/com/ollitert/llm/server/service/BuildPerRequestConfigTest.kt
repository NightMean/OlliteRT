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

import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.MAX_MAX_TOKENS
import com.ollitert.llm.server.data.MAX_TEMPERATURE
import com.ollitert.llm.server.data.MAX_TOPK
import com.ollitert.llm.server.data.MAX_TOPP
import com.ollitert.llm.server.data.MIN_MAX_TOKENS
import com.ollitert.llm.server.data.MIN_TEMPERATURE
import com.ollitert.llm.server.data.MIN_TOPK
import com.ollitert.llm.server.data.MIN_TOPP
import com.ollitert.llm.server.data.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildPerRequestConfigTest {

  private fun model(configValues: Map<String, Any> = emptyMap()): Model =
    Model(name = "test").also { it.configValues = configValues }

  // ── Null passthrough ──────────────────────────────────────────────────────

  @Test
  fun returnsNullWhenAllParamsNull() {
    assertNull(buildPerRequestConfig(model()))
  }

  // ── Temperature clamping ──────────────────────────────────────────────────

  @Test
  fun temperatureInRangePassesThrough() {
    val result = buildPerRequestConfig(model(), temperature = 1.0)!!
    assertEquals(1.0f, result[ConfigKeys.TEMPERATURE.label])
  }

  @Test
  fun temperatureBelowMinClampedToMin() {
    val result = buildPerRequestConfig(model(), temperature = -5.0)!!
    assertEquals(MIN_TEMPERATURE, result[ConfigKeys.TEMPERATURE.label])
  }

  @Test
  fun temperatureAboveMaxClampedToMax() {
    val result = buildPerRequestConfig(model(), temperature = 999.0)!!
    assertEquals(MAX_TEMPERATURE, result[ConfigKeys.TEMPERATURE.label])
  }

  // ── TopP clamping ─────────────────────────────────────────────────────────

  @Test
  fun topPInRangePassesThrough() {
    val result = buildPerRequestConfig(model(), topP = 0.5)!!
    assertEquals(0.5f, result[ConfigKeys.TOPP.label])
  }

  @Test
  fun topPBelowMinClampedToMin() {
    val result = buildPerRequestConfig(model(), topP = -1.0)!!
    assertEquals(MIN_TOPP, result[ConfigKeys.TOPP.label])
  }

  @Test
  fun topPAboveMaxClampedToMax() {
    val result = buildPerRequestConfig(model(), topP = 5.0)!!
    assertEquals(MAX_TOPP, result[ConfigKeys.TOPP.label])
  }

  // ── TopK clamping ─────────────────────────────────────────────────────────

  @Test
  fun topKInRangePassesThrough() {
    val result = buildPerRequestConfig(model(), topK = 40)!!
    assertEquals(40, result[ConfigKeys.TOPK.label])
  }

  @Test
  fun topKBelowMinClampedToMin() {
    val result = buildPerRequestConfig(model(), topK = -1)!!
    assertEquals(MIN_TOPK, result[ConfigKeys.TOPK.label])
  }

  @Test
  fun topKAboveMaxClampedToMax() {
    val result = buildPerRequestConfig(model(), topK = 999999)!!
    assertEquals(MAX_TOPK, result[ConfigKeys.TOPK.label])
  }

  // ── MaxTokens clamping ────────────────────────────────────────────────────

  @Test
  fun maxTokensInRangePassesThrough() {
    val result = buildPerRequestConfig(model(), maxTokens = 1024)!!
    assertEquals(1024, result[ConfigKeys.MAX_TOKENS.label])
  }

  @Test
  fun maxTokensBelowMinClampedToMin() {
    val result = buildPerRequestConfig(model(), maxTokens = -1)!!
    assertEquals(MIN_MAX_TOKENS, result[ConfigKeys.MAX_TOKENS.label])
  }

  @Test
  fun maxTokensZeroClampedToMin() {
    val result = buildPerRequestConfig(model(), maxTokens = 0)!!
    assertEquals(MIN_MAX_TOKENS, result[ConfigKeys.MAX_TOKENS.label])
  }

  @Test
  fun maxTokensAboveMaxClampedToMax() {
    val result = buildPerRequestConfig(model(), maxTokens = 999999)!!
    assertEquals(MAX_MAX_TOKENS, result[ConfigKeys.MAX_TOKENS.label])
  }

  @Test
  fun maxTokensClampedToEngineMaxWhenLower() {
    val engineMax = 512
    val m = model(mapOf(ConfigKeys.MAX_TOKENS.label to engineMax))
    val result = buildPerRequestConfig(m, maxTokens = 1024)!!
    assertEquals(engineMax, result[ConfigKeys.MAX_TOKENS.label])
  }

  @Test
  fun maxTokensNegativeWithEngineMaxClampedToMin() {
    val m = model(mapOf(ConfigKeys.MAX_TOKENS.label to 2048))
    val result = buildPerRequestConfig(m, maxTokens = -5)!!
    assertEquals(MIN_MAX_TOKENS, result[ConfigKeys.MAX_TOKENS.label])
  }

  // ── Boundary values ───────────────────────────────────────────────────────

  @Test
  fun temperatureAtExactBoundariesPassesThrough() {
    val resultMin = buildPerRequestConfig(model(), temperature = MIN_TEMPERATURE.toDouble())!!
    assertEquals(MIN_TEMPERATURE, resultMin[ConfigKeys.TEMPERATURE.label])
    val resultMax = buildPerRequestConfig(model(), temperature = MAX_TEMPERATURE.toDouble())!!
    assertEquals(MAX_TEMPERATURE, resultMax[ConfigKeys.TEMPERATURE.label])
  }

  @Test
  fun topKAtExactBoundariesPassesThrough() {
    val resultMin = buildPerRequestConfig(model(), topK = MIN_TOPK)!!
    assertEquals(MIN_TOPK, resultMin[ConfigKeys.TOPK.label])
    val resultMax = buildPerRequestConfig(model(), topK = MAX_TOPK)!!
    assertEquals(MAX_TOPK, resultMax[ConfigKeys.TOPK.label])
  }

  // ── Existing config values preserved ──────────────────────────────────────

  @Test
  fun existingConfigValuesPreservedWhenOverridingOneParam() {
    val existing = mapOf(
      ConfigKeys.TEMPERATURE.label to 0.5f,
      ConfigKeys.TOPK.label to 30,
    )
    val result = buildPerRequestConfig(model(existing), topP = 0.9)!!
    assertEquals(0.5f, result[ConfigKeys.TEMPERATURE.label])
    assertEquals(30, result[ConfigKeys.TOPK.label])
    assertEquals(0.9f, result[ConfigKeys.TOPP.label])
  }
}
