package com.ollitert.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [convertValueToTargetType], [createLlmChatConfigs],
 * and [createLlmChatConfigsForNpuModel].
 */
class ConfigTest {

  // ── convertValueToTargetType() — INT ─────────────────────────────────────

  @Test
  fun convertIntFromInt() {
    assertEquals(42, convertValueToTargetType(42, ValueType.INT))
  }

  @Test
  fun convertIntFromFloat() {
    assertEquals(3, convertValueToTargetType(3.7f, ValueType.INT))
  }

  @Test
  fun convertIntFromDouble() {
    assertEquals(5, convertValueToTargetType(5.9, ValueType.INT))
  }

  @Test
  fun convertIntFromValidString() {
    assertEquals(100, convertValueToTargetType("100", ValueType.INT))
  }

  @Test
  fun convertIntFromInvalidStringReturnsFallback() {
    assertEquals("", convertValueToTargetType("abc", ValueType.INT))
  }

  @Test
  fun convertIntFromBooleanTrue() {
    assertEquals(1, convertValueToTargetType(true, ValueType.INT))
  }

  @Test
  fun convertIntFromBooleanFalse() {
    assertEquals(0, convertValueToTargetType(false, ValueType.INT))
  }

  // ── convertValueToTargetType() — FLOAT ───────────────────────────────────

  @Test
  fun convertFloatFromFloat() {
    assertEquals(3.14f, convertValueToTargetType(3.14f, ValueType.FLOAT))
  }

  @Test
  fun convertFloatFromInt() {
    assertEquals(5.0f, convertValueToTargetType(5, ValueType.FLOAT))
  }

  @Test
  fun convertFloatFromDouble() {
    val result = convertValueToTargetType(2.5, ValueType.FLOAT)
    assertEquals(2.5f, result)
  }

  @Test
  fun convertFloatFromValidString() {
    assertEquals(1.5f, convertValueToTargetType("1.5", ValueType.FLOAT))
  }

  @Test
  fun convertFloatFromInvalidStringReturnsFallback() {
    assertEquals("", convertValueToTargetType("xyz", ValueType.FLOAT))
  }

  @Test
  fun convertFloatFromBooleanTrue() {
    assertEquals(1f, convertValueToTargetType(true, ValueType.FLOAT))
  }

  // ── convertValueToTargetType() — DOUBLE ──────────────────────────────────

  @Test
  fun convertDoubleFromDouble() {
    assertEquals(3.14, convertValueToTargetType(3.14, ValueType.DOUBLE))
  }

  @Test
  fun convertDoubleFromInt() {
    assertEquals(5.0, convertValueToTargetType(5, ValueType.DOUBLE))
  }

  @Test
  fun convertDoubleFromFloat() {
    val result = convertValueToTargetType(2.5f, ValueType.DOUBLE) as Double
    assertEquals(2.5, result, 0.001)
  }

  @Test
  fun convertDoubleFromValidString() {
    assertEquals(1.5, convertValueToTargetType("1.5", ValueType.DOUBLE))
  }

  @Test
  fun convertDoubleFromInvalidStringReturnsFallback() {
    assertEquals("", convertValueToTargetType("nope", ValueType.DOUBLE))
  }

  // ── convertValueToTargetType() — BOOLEAN ─────────────────────────────────

  @Test
  fun convertBooleanFromBoolean() {
    assertEquals(true, convertValueToTargetType(true, ValueType.BOOLEAN))
    assertEquals(false, convertValueToTargetType(false, ValueType.BOOLEAN))
  }

  @Test
  fun convertBooleanFromIntZeroReturnsTrue() {
    // Note: the implementation has `value == 0` which means 0 → true (quirk from upstream code)
    assertEquals(true, convertValueToTargetType(0, ValueType.BOOLEAN))
  }

  @Test
  fun convertBooleanFromIntNonZeroReturnsFalse() {
    assertEquals(false, convertValueToTargetType(1, ValueType.BOOLEAN))
    assertEquals(false, convertValueToTargetType(-1, ValueType.BOOLEAN))
  }

  @Test
  fun convertBooleanFromFloatNonZeroReturnsTrue() {
    // abs(1.0f) > 1e-6 → true
    assertEquals(true, convertValueToTargetType(1.0f, ValueType.BOOLEAN))
  }

  @Test
  fun convertBooleanFromFloatZeroReturnsFalse() {
    assertEquals(false, convertValueToTargetType(0.0f, ValueType.BOOLEAN))
  }

  @Test
  fun convertBooleanFromNonEmptyStringReturnsTrue() {
    assertEquals(true, convertValueToTargetType("hello", ValueType.BOOLEAN))
  }

  @Test
  fun convertBooleanFromEmptyStringReturnsFalse() {
    assertEquals(false, convertValueToTargetType("", ValueType.BOOLEAN))
  }

  // ── convertValueToTargetType() — STRING ──────────────────────────────────

  @Test
  fun convertStringFromAnyUsesToString() {
    assertEquals("42", convertValueToTargetType(42, ValueType.STRING))
    assertEquals("3.14", convertValueToTargetType(3.14f, ValueType.STRING))
    assertEquals("true", convertValueToTargetType(true, ValueType.STRING))
    assertEquals("hello", convertValueToTargetType("hello", ValueType.STRING))
  }

  // ── createLlmChatConfigs() ───────────────────────────────────────────────

  @Test
  fun createLlmChatConfigsContainsExpectedKeys() {
    val configs = createLlmChatConfigs()
    val keyIds = configs.map { it.key.id }
    assertTrue("should contain max_tokens", keyIds.contains("max_tokens"))
    assertTrue("should contain topk", keyIds.contains("topk"))
    assertTrue("should contain topp", keyIds.contains("topp"))
    assertTrue("should contain temperature", keyIds.contains("temperature"))
    assertTrue("should contain accelerator", keyIds.contains("accelerator"))
  }

  @Test
  fun createLlmChatConfigsDefaultHasNoThinkingToggle() {
    val configs = createLlmChatConfigs(supportThinking = false)
    val keyIds = configs.map { it.key.id }
    assertFalse("should not contain enable_thinking", keyIds.contains("enable_thinking"))
  }

  @Test
  fun createLlmChatConfigsWithThinkingAddsToggle() {
    val configs = createLlmChatConfigs(supportThinking = true)
    val keyIds = configs.map { it.key.id }
    assertTrue("should contain enable_thinking", keyIds.contains("enable_thinking"))
    val thinkingConfig = configs.first { it.key.id == "enable_thinking" }
    assertTrue("thinking config should be BooleanSwitch", thinkingConfig is BooleanSwitchConfig)
  }

  @Test
  fun createLlmChatConfigsMaxTokensIsLabelByDefault() {
    val configs = createLlmChatConfigs()
    val maxTokensConfig = configs.first { it.key.id == "max_tokens" }
    assertTrue("without context length, max_tokens should be LabelConfig", maxTokensConfig is LabelConfig)
  }

  @Test
  fun createLlmChatConfigsMaxTokensIsSliderWithContextLength() {
    val configs = createLlmChatConfigs(defaultMaxContextLength = 8000)
    val maxTokensConfig = configs.first { it.key.id == "max_tokens" }
    assertTrue("with context length, max_tokens should be NumberSliderConfig", maxTokensConfig is NumberSliderConfig)
    val slider = maxTokensConfig as NumberSliderConfig
    assertEquals(8000f, slider.sliderMax, 0.01f)
  }

  @Test
  fun createLlmChatConfigsSamplerParamsDoNotNeedReinit() {
    val configs = createLlmChatConfigs()
    val topk = configs.first { it.key.id == "topk" }
    val topp = configs.first { it.key.id == "topp" }
    val temp = configs.first { it.key.id == "temperature" }
    assertFalse("topk should not need reinit", topk.needReinitialization)
    assertFalse("topp should not need reinit", topp.needReinitialization)
    assertFalse("temperature should not need reinit", temp.needReinitialization)
  }

  // ── createLlmChatConfigsForNpuModel() ────────────────────────────────────

  @Test
  fun createLlmChatConfigsForNpuModelOnlyHasMaxTokensAndAccelerator() {
    val configs = createLlmChatConfigsForNpuModel()
    val keyIds = configs.map { it.key.id }
    assertEquals(listOf("max_tokens", "accelerator"), keyIds)
  }

  @Test
  fun createLlmChatConfigsForNpuModelMaxTokensIsLabel() {
    val configs = createLlmChatConfigsForNpuModel()
    val maxTokensConfig = configs.first { it.key.id == "max_tokens" }
    assertTrue("NPU max_tokens should be LabelConfig (no slider)", maxTokensConfig is LabelConfig)
  }

}
