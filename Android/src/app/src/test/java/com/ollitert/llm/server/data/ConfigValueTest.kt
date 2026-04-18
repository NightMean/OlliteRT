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
import org.junit.Test

/**
 * Tests for [ConfigValue] sealed class and the getter functions
 * [getIntConfigValue], [getFloatConfigValue], [getStringConfigValue].
 */
class ConfigValueTest {

  // ── getIntConfigValue() ──────────────────────────────────────────────────

  @Test
  fun getIntConfigValueNullReturnsDefault() {
    assertEquals(42, getIntConfigValue(null, 42))
  }

  @Test
  fun getIntConfigValueFromIntValue() {
    assertEquals(10, getIntConfigValue(ConfigValue.IntValue(10), 0))
  }

  @Test
  fun getIntConfigValueFromFloatValueTruncates() {
    // 3.7f → 3 (truncation, not rounding)
    assertEquals(3, getIntConfigValue(ConfigValue.FloatValue(3.7f), 0))
  }

  @Test
  fun getIntConfigValueFromFloatValueNegativeTruncates() {
    // -2.9f → -2
    assertEquals(-2, getIntConfigValue(ConfigValue.FloatValue(-2.9f), 0))
  }

  @Test
  fun getIntConfigValueFromStringValueReturnsZero() {
    assertEquals(0, getIntConfigValue(ConfigValue.StringValue("hello"), 99))
  }

  // ── getFloatConfigValue() ────────────────────────────────────────────────

  @Test
  fun getFloatConfigValueNullReturnsDefault() {
    assertEquals(1.5f, getFloatConfigValue(null, 1.5f), 0.001f)
  }

  @Test
  fun getFloatConfigValueFromFloatValue() {
    assertEquals(3.14f, getFloatConfigValue(ConfigValue.FloatValue(3.14f), 0f), 0.001f)
  }

  @Test
  fun getFloatConfigValueFromIntValueConverts() {
    assertEquals(5.0f, getFloatConfigValue(ConfigValue.IntValue(5), 0f), 0.001f)
  }

  @Test
  fun getFloatConfigValueFromStringValueReturnsZero() {
    assertEquals(0f, getFloatConfigValue(ConfigValue.StringValue("text"), 99f), 0.001f)
  }

  // ── getStringConfigValue() ───────────────────────────────────────────────

  @Test
  fun getStringConfigValueNullReturnsDefault() {
    assertEquals("default", getStringConfigValue(null, "default"))
  }

  @Test
  fun getStringConfigValueFromStringValue() {
    assertEquals("hello", getStringConfigValue(ConfigValue.StringValue("hello"), ""))
  }

  @Test
  fun getStringConfigValueFromIntValueConverts() {
    assertEquals("42", getStringConfigValue(ConfigValue.IntValue(42), ""))
  }

  @Test
  fun getStringConfigValueFromFloatValueConverts() {
    val result = getStringConfigValue(ConfigValue.FloatValue(3.14f), "")
    // Kotlin's Float.toString() produces "3.14"
    assertEquals("3.14", result)
  }
}
