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

package com.ollitert.llm.server.data.prefs
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelCapability
import com.ollitert.llm.server.data.model.isSpeculativeDecodingEnabled
import com.ollitert.llm.server.data.model.isThinkingEnabled

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigHelpersTest {

  private val key = ConfigKeys.MAX_TOKENS.id

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

  // ── configTemperature ───────────────────────────────────────────────────

  @Test
  fun configTemperatureFromFloat() {
    val map = mapOf(ConfigKeys.TEMPERATURE.id to 0.7f)
    assertEquals(0.7f, map.configTemperature())
  }

  @Test
  fun configTemperatureFromDouble() {
    val map = mapOf(ConfigKeys.TEMPERATURE.id to 0.7)
    assertEquals(0.7f, map.configTemperature() ?: Float.NaN, 0.001f)
  }

  @Test
  fun configTemperatureMissingReturnsNull() {
    assertNull(emptyMap<String, Any>().configTemperature())
  }

  // ── configTopK ──────────────────────────────────────────────────────────

  @Test
  fun configTopKFromInt() {
    val map = mapOf(ConfigKeys.TOPK.id to 40)
    assertEquals(40, map.configTopK())
  }

  @Test
  fun configTopKMissingReturnsNull() {
    assertNull(emptyMap<String, Any>().configTopK())
  }

  // ── configTopP ──────────────────────────────────────────────────────────

  @Test
  fun configTopPFromFloat() {
    val map = mapOf(ConfigKeys.TOPP.id to 0.95f)
    assertEquals(0.95f, map.configTopP())
  }

  @Test
  fun configTopPMissingReturnsNull() {
    assertNull(emptyMap<String, Any>().configTopP())
  }

  // ── configThinkingEnabled ───────────────────────────────────────────────

  @Test
  fun configThinkingEnabledTrue() {
    val map = mapOf(ConfigKeys.ENABLE_THINKING.id to true)
    assertEquals(true, map.configThinkingEnabled())
  }

  @Test
  fun configThinkingEnabledFalse() {
    val map = mapOf(ConfigKeys.ENABLE_THINKING.id to false)
    assertEquals(false, map.configThinkingEnabled())
  }

  @Test
  fun configThinkingEnabledMissingReturnsNull() {
    assertNull(emptyMap<String, Any>().configThinkingEnabled())
  }

  @Test
  fun configThinkingEnabledNonBooleanReturnsNull() {
    val map = mapOf(ConfigKeys.ENABLE_THINKING.id to "yes")
    assertNull(map.configThinkingEnabled())
  }

  // ── thinkingBudgetTokens ────────────────────────────────────────────────

  @Test
  fun thinkingBudgetTokensFromInt() {
    assertEquals(1024, mapOf(ConfigKeys.THINKING_BUDGET.id to 1024).thinkingBudgetTokens())
  }

  @Test
  fun thinkingBudgetTokensFromLong() {
    assertEquals(512, mapOf(ConfigKeys.THINKING_BUDGET.id to 512L).thinkingBudgetTokens())
  }

  @Test
  fun thinkingBudgetTokensMissingReturnsNull() {
    assertNull(emptyMap<String, Any>().thinkingBudgetTokens())
  }

  @Test
  fun thinkingBudgetTokensNonNumericReturnsNull() {
    assertNull(mapOf(ConfigKeys.THINKING_BUDGET.id to "lots").thinkingBudgetTokens())
  }

  // ── Model.isThinkingEnabled ─────────────────────────────────────────────

  @Test
  fun isThinkingEnabledTrueWhenCapableAndEnabled() {
    val model = Model(
      name = "test",
      capabilities = setOf(ModelCapability.THINKING),
    ).apply {
      configValues = mapOf(ConfigKeys.ENABLE_THINKING.id to true)
    }
    assertTrue(model.isThinkingEnabled)
  }

  @Test
  fun isThinkingEnabledTrueWhenCapableAndMissing() {
    val model = Model(
      name = "test",
      capabilities = setOf(ModelCapability.THINKING),
    )
    assertTrue(model.isThinkingEnabled)
  }

  @Test
  fun isThinkingEnabledFalseWhenCapableButDisabled() {
    val model = Model(
      name = "test",
      capabilities = setOf(ModelCapability.THINKING),
    ).apply {
      configValues = mapOf(ConfigKeys.ENABLE_THINKING.id to false)
    }
    assertFalse(model.isThinkingEnabled)
  }

  @Test
  fun isThinkingEnabledFalseWhenNotCapable() {
    val model = Model(name = "test").apply {
      configValues = mapOf(ConfigKeys.ENABLE_THINKING.id to true)
    }
    assertFalse(model.isThinkingEnabled)
  }

  // ── configSpeculativeDecodingEnabled ────────────────────────────────────

  @Test
  fun configSpeculativeDecodingEnabledTrue() {
    val map = mapOf(ConfigKeys.ENABLE_SPECULATIVE_DECODING.id to true)
    assertEquals(true, map.configSpeculativeDecodingEnabled())
  }

  @Test
  fun configSpeculativeDecodingEnabledFalse() {
    val map = mapOf(ConfigKeys.ENABLE_SPECULATIVE_DECODING.id to false)
    assertEquals(false, map.configSpeculativeDecodingEnabled())
  }

  @Test
  fun configSpeculativeDecodingEnabledMissingReturnsNull() {
    assertNull(emptyMap<String, Any>().configSpeculativeDecodingEnabled())
  }

  @Test
  fun configSpeculativeDecodingEnabledNonBooleanReturnsNull() {
    val map = mapOf(ConfigKeys.ENABLE_SPECULATIVE_DECODING.id to "yes")
    assertNull(map.configSpeculativeDecodingEnabled())
  }

  // ── Model.isSpeculativeDecodingEnabled ──────────────────────────────────

  @Test
  fun isSpeculativeDecodingEnabledTrueWhenCapableAndEnabled() {
    val model = Model(
      name = "test",
      capabilities = setOf(ModelCapability.SPECULATIVE_DECODING),
    ).apply {
      configValues = mapOf(ConfigKeys.ENABLE_SPECULATIVE_DECODING.id to true)
    }
    assertTrue(model.isSpeculativeDecodingEnabled)
  }

  @Test
  fun isSpeculativeDecodingEnabledFalseWhenCapableButMissing() {
    val model = Model(
      name = "test",
      capabilities = setOf(ModelCapability.SPECULATIVE_DECODING),
    )
    assertFalse(model.isSpeculativeDecodingEnabled)
  }

  @Test
  fun isSpeculativeDecodingEnabledFalseWhenCapableButDisabled() {
    val model = Model(
      name = "test",
      capabilities = setOf(ModelCapability.SPECULATIVE_DECODING),
    ).apply {
      configValues = mapOf(ConfigKeys.ENABLE_SPECULATIVE_DECODING.id to false)
    }
    assertFalse(model.isSpeculativeDecodingEnabled)
  }

  @Test
  fun isSpeculativeDecodingEnabledFalseWhenNotCapable() {
    val model = Model(name = "test").apply {
      configValues = mapOf(ConfigKeys.ENABLE_SPECULATIVE_DECODING.id to true)
    }
    assertFalse(model.isSpeculativeDecodingEnabled)
  }
}
