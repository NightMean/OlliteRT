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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LlmHttpPrefsTest {

  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setUp() {
    LlmHttpPrefs.resetToDefaults(context)
  }

  @After
  fun tearDown() {
    LlmHttpPrefs.resetToDefaults(context)
  }

  // --- Reset to Defaults ---

  @Test
  fun resetToDefaultsClearsAllSettings() {
    LlmHttpPrefs.setBearerToken(context, "secret-token")
    LlmHttpPrefs.setHfToken(context, "hf_abc123")
    LlmHttpPrefs.save(context, enabled = true, port = 9090)
    LlmHttpPrefs.setAutoStartOnBoot(context, true)
    LlmHttpPrefs.setKeepScreenOn(context, false)
    LlmHttpPrefs.setWarmupEnabled(context, false)
    LlmHttpPrefs.setKeepAliveEnabled(context, true)
    LlmHttpPrefs.setKeepAliveMinutes(context, 30)
    LlmHttpPrefs.setLogPersistenceEnabled(context, true)
    LlmHttpPrefs.setLogMaxEntries(context, 1000)
    LlmHttpPrefs.setHaIntegrationEnabled(context, true)
    LlmHttpPrefs.setVerboseDebugEnabled(context, true)
    LlmHttpPrefs.setUpdateCheckEnabled(context, false)

    LlmHttpPrefs.resetToDefaults(context)

    assertEquals("", LlmHttpPrefs.getBearerToken(context))
    assertEquals("", LlmHttpPrefs.getHfToken(context))
    assertFalse(LlmHttpPrefs.isEnabled(context))
    assertEquals(DEFAULT_PORT, LlmHttpPrefs.getPort(context))
    assertFalse(LlmHttpPrefs.isAutoStartOnBoot(context))
    assertTrue(LlmHttpPrefs.isKeepScreenOn(context))
    assertTrue(LlmHttpPrefs.isWarmupEnabled(context))
    assertFalse(LlmHttpPrefs.isKeepAliveEnabled(context))
    assertEquals(5, LlmHttpPrefs.getKeepAliveMinutes(context))
    assertFalse(LlmHttpPrefs.isLogPersistenceEnabled(context))
    assertEquals(500, LlmHttpPrefs.getLogMaxEntries(context))
    assertFalse(LlmHttpPrefs.isHaIntegrationEnabled(context))
    assertFalse(LlmHttpPrefs.isVerboseDebugEnabled(context))
    assertTrue(LlmHttpPrefs.isUpdateCheckEnabled(context))
  }

  @Test
  fun resetToDefaultsClearsPerModelConfigs() {
    LlmHttpPrefs.setSystemPrompt(context, "gemma-3-4b", "You are helpful.")
    LlmHttpPrefs.setInferenceConfig(context, "gemma-3-4b", mapOf("Temperature" to 0.5))
    LlmHttpPrefs.setSystemPrompt(context, "gemma-4-12b", "Be concise.")
    LlmHttpPrefs.setInferenceConfig(context, "gemma-4-12b", mapOf("Max Tokens" to 2048))

    LlmHttpPrefs.resetToDefaults(context)

    assertEquals("", LlmHttpPrefs.getSystemPrompt(context, "gemma-3-4b"))
    assertNull(LlmHttpPrefs.getInferenceConfig(context, "gemma-3-4b"))
    assertEquals("", LlmHttpPrefs.getSystemPrompt(context, "gemma-4-12b"))
    assertNull(LlmHttpPrefs.getInferenceConfig(context, "gemma-4-12b"))
  }

  @Test
  fun resetToDefaultsClearsEngagementState() {
    LlmHttpPrefs.incrementManualStartCount(context)
    LlmHttpPrefs.incrementManualStartCount(context)
    LlmHttpPrefs.incrementManualStartCount(context)
    LlmHttpPrefs.incrementEngagementPromptShowCount(context)

    LlmHttpPrefs.resetToDefaults(context)

    assertEquals(0, LlmHttpPrefs.getManualStartCount(context))
    assertEquals(0, LlmHttpPrefs.getEngagementPromptShowCount(context))
    assertFalse(LlmHttpPrefs.isEngagementPromptPermanentlyDismissed(context))
  }

  // --- Inference Config JSON Round-Trip ---

  @Test
  fun inferenceConfigJsonRoundTrip() {
    val config = mapOf(
      "Temperature" to 0.8,
      "Max Tokens" to 2048,
      "Top-K" to 40,
      "Top-P" to 0.95,
    )

    LlmHttpPrefs.setInferenceConfig(context, "test-model", config)
    val loaded = LlmHttpPrefs.getInferenceConfig(context, "test-model")!!

    assertEquals(0.8, (loaded["Temperature"] as Number).toDouble(), 0.001)
    assertEquals(2048, (loaded["Max Tokens"] as Number).toInt())
    assertEquals(40, (loaded["Top-K"] as Number).toInt())
    assertEquals(0.95, (loaded["Top-P"] as Number).toDouble(), 0.001)
  }

  @Test
  fun inferenceConfigReturnsNullWhenNotSet() {
    assertNull(LlmHttpPrefs.getInferenceConfig(context, "nonexistent-model"))
  }

  @Test
  fun clearInferenceConfigRemovesOnlyTargetModel() {
    LlmHttpPrefs.setInferenceConfig(context, "model-a", mapOf("Temperature" to 0.5))
    LlmHttpPrefs.setInferenceConfig(context, "model-b", mapOf("Temperature" to 0.9))

    LlmHttpPrefs.clearInferenceConfig(context, "model-a")

    assertNull(LlmHttpPrefs.getInferenceConfig(context, "model-a"))
    assertEquals(0.9, (LlmHttpPrefs.getInferenceConfig(context, "model-b")!!["Temperature"] as Number).toDouble(), 0.001)
  }

  // --- Engagement Prompt Logic ---

  @Test
  fun engagementPromptShowsAtFirstThreshold() {
    repeat(3) { LlmHttpPrefs.incrementManualStartCount(context) }
    assertTrue(LlmHttpPrefs.shouldShowEngagementPrompt(context))
  }

  @Test
  fun engagementPromptDoesNotShowBeforeThreshold() {
    repeat(2) { LlmHttpPrefs.incrementManualStartCount(context) }
    assertFalse(LlmHttpPrefs.shouldShowEngagementPrompt(context))
  }

  @Test
  fun engagementPromptPermanentlyDismissedBlocksAll() {
    repeat(3) { LlmHttpPrefs.incrementManualStartCount(context) }
    LlmHttpPrefs.setEngagementPromptPermanentlyDismissed(context)
    assertFalse(LlmHttpPrefs.shouldShowEngagementPrompt(context))
  }

  @Test
  fun engagementPromptStopsAfterMaxShows() {
    repeat(13) { LlmHttpPrefs.incrementManualStartCount(context) }
    LlmHttpPrefs.incrementEngagementPromptShowCount(context)
    LlmHttpPrefs.incrementEngagementPromptShowCount(context)
    assertFalse(LlmHttpPrefs.shouldShowEngagementPrompt(context))
  }

  // --- Update State ---

  @Test
  fun clearUpdateStateRemovesCachedValues() {
    LlmHttpPrefs.setCachedUpdateInfo(context, "v2.0.0", "https://example.com", "etag-123")
    LlmHttpPrefs.setLastDismissedUpdateVersion(context, "v2.0.0")
    LlmHttpPrefs.setUpdateCheckConsecutiveFailures(context, 5)

    LlmHttpPrefs.clearUpdateState(context)

    assertNull(LlmHttpPrefs.getCachedLatestVersion(context))
    assertNull(LlmHttpPrefs.getCachedReleaseHtmlUrl(context))
    assertNull(LlmHttpPrefs.getCachedReleaseETag(context))
    assertNull(LlmHttpPrefs.getLastDismissedUpdateVersion(context))
    assertEquals(0, LlmHttpPrefs.getUpdateCheckConsecutiveFailures(context))
  }

  // --- Default Model Name ---

  @Test
  fun defaultModelNameNullWhenNotSet() {
    assertNull(LlmHttpPrefs.getDefaultModelName(context))
  }

  @Test
  fun defaultModelNameRoundTrip() {
    LlmHttpPrefs.setDefaultModelName(context, "gemma-3-4b")
    assertEquals("gemma-3-4b", LlmHttpPrefs.getDefaultModelName(context))
  }

  @Test
  fun defaultModelNameSetToNullRemovesKey() {
    LlmHttpPrefs.setDefaultModelName(context, "gemma-3-4b")
    LlmHttpPrefs.setDefaultModelName(context, null)
    assertNull(LlmHttpPrefs.getDefaultModelName(context))
  }

  // --- CORS ---

  @Test
  fun corsDefaultsToWildcard() {
    assertEquals("*", LlmHttpPrefs.getCorsAllowedOrigins(context))
  }

  @Test
  fun corsRoundTrip() {
    LlmHttpPrefs.setCorsAllowedOrigins(context, "http://homeassistant.local:8123")
    assertEquals("http://homeassistant.local:8123", LlmHttpPrefs.getCorsAllowedOrigins(context))
  }
}
