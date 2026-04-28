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
class ServerPrefsTest {

  private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setUp() {
    ServerPrefs.resetToDefaults(context)
  }

  @After
  fun tearDown() {
    ServerPrefs.resetToDefaults(context)
  }

  // --- Reset to Defaults ---

  @Test
  fun resetToDefaultsClearsAllSettings() {
    ServerPrefs.setBearerToken(context, "secret-token")
    ServerPrefs.setHfToken(context, "hf_abc123")
    ServerPrefs.save(context, port = 9090)
    ServerPrefs.setAutoStartOnBoot(context, true)
    ServerPrefs.setKeepScreenOn(context, false)
    ServerPrefs.setWarmupEnabled(context, false)
    ServerPrefs.setKeepAliveEnabled(context, true)
    ServerPrefs.setKeepAliveMinutes(context, 30)
    ServerPrefs.setLogPersistenceEnabled(context, true)
    ServerPrefs.setLogMaxEntries(context, 1000)
    ServerPrefs.setHaIntegrationEnabled(context, true)
    ServerPrefs.setVerboseDebugEnabled(context, true)
    ServerPrefs.setUpdateCheckEnabled(context, false)

    ServerPrefs.resetToDefaults(context)

    assertEquals("", ServerPrefs.getBearerToken(context))
    assertEquals("", ServerPrefs.getHfToken(context))
    assertEquals(DEFAULT_PORT, ServerPrefs.getPort(context))
    assertFalse(ServerPrefs.isAutoStartOnBoot(context))
    assertTrue(ServerPrefs.isKeepScreenOn(context))
    assertTrue(ServerPrefs.isWarmupEnabled(context))
    assertFalse(ServerPrefs.isKeepAliveEnabled(context))
    assertEquals(5, ServerPrefs.getKeepAliveMinutes(context))
    assertFalse(ServerPrefs.isLogPersistenceEnabled(context))
    assertEquals(500, ServerPrefs.getLogMaxEntries(context))
    assertFalse(ServerPrefs.isHaIntegrationEnabled(context))
    assertFalse(ServerPrefs.isVerboseDebugEnabled(context))
    assertTrue(ServerPrefs.isUpdateCheckEnabled(context))
  }

  @Test
  fun resetToDefaultsClearsPerModelConfigs() {
    ServerPrefs.setSystemPrompt(context, "gemma-3-4b", "You are helpful.")
    ServerPrefs.setInferenceConfig(context, "gemma-3-4b", mapOf("Temperature" to 0.5))
    ServerPrefs.setSystemPrompt(context, "gemma-4-12b", "Be concise.")
    ServerPrefs.setInferenceConfig(context, "gemma-4-12b", mapOf("Max Tokens" to 2048))

    ServerPrefs.resetToDefaults(context)

    assertEquals("", ServerPrefs.getSystemPrompt(context, "gemma-3-4b"))
    assertNull(ServerPrefs.getInferenceConfig(context, "gemma-3-4b"))
    assertEquals("", ServerPrefs.getSystemPrompt(context, "gemma-4-12b"))
    assertNull(ServerPrefs.getInferenceConfig(context, "gemma-4-12b"))
  }

  @Test
  fun resetToDefaultsClearsEngagementState() {
    ServerPrefs.incrementManualStartCount(context)
    ServerPrefs.incrementManualStartCount(context)
    ServerPrefs.incrementManualStartCount(context)
    ServerPrefs.incrementEngagementPromptShowCount(context)

    ServerPrefs.resetToDefaults(context)

    assertEquals(0, ServerPrefs.getManualStartCount(context))
    assertEquals(0, ServerPrefs.getEngagementPromptShowCount(context))
    assertFalse(ServerPrefs.isEngagementPromptPermanentlyDismissed(context))
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

    ServerPrefs.setInferenceConfig(context, "test-model", config)
    val loaded = ServerPrefs.getInferenceConfig(context, "test-model")!!

    assertEquals(0.8, (loaded["Temperature"] as Number).toDouble(), 0.001)
    assertEquals(2048, (loaded["Max Tokens"] as Number).toInt())
    assertEquals(40, (loaded["Top-K"] as Number).toInt())
    assertEquals(0.95, (loaded["Top-P"] as Number).toDouble(), 0.001)
  }

  @Test
  fun inferenceConfigReturnsNullWhenNotSet() {
    assertNull(ServerPrefs.getInferenceConfig(context, "nonexistent-model"))
  }

  @Test
  fun clearInferenceConfigRemovesOnlyTargetModel() {
    ServerPrefs.setInferenceConfig(context, "model-a", mapOf("Temperature" to 0.5))
    ServerPrefs.setInferenceConfig(context, "model-b", mapOf("Temperature" to 0.9))

    ServerPrefs.clearInferenceConfig(context, "model-a")

    assertNull(ServerPrefs.getInferenceConfig(context, "model-a"))
    assertEquals(0.9, (ServerPrefs.getInferenceConfig(context, "model-b")!!["Temperature"] as Number).toDouble(), 0.001)
  }

  // --- Engagement Prompt Logic ---

  @Test
  fun engagementPromptShowsAtFirstThreshold() {
    repeat(3) { ServerPrefs.incrementManualStartCount(context) }
    assertTrue(ServerPrefs.shouldShowEngagementPrompt(context))
  }

  @Test
  fun engagementPromptDoesNotShowBeforeThreshold() {
    repeat(2) { ServerPrefs.incrementManualStartCount(context) }
    assertFalse(ServerPrefs.shouldShowEngagementPrompt(context))
  }

  @Test
  fun engagementPromptPermanentlyDismissedBlocksAll() {
    repeat(3) { ServerPrefs.incrementManualStartCount(context) }
    ServerPrefs.setEngagementPromptPermanentlyDismissed(context)
    assertFalse(ServerPrefs.shouldShowEngagementPrompt(context))
  }

  @Test
  fun engagementPromptStopsAfterMaxShows() {
    repeat(13) { ServerPrefs.incrementManualStartCount(context) }
    ServerPrefs.incrementEngagementPromptShowCount(context)
    ServerPrefs.incrementEngagementPromptShowCount(context)
    assertFalse(ServerPrefs.shouldShowEngagementPrompt(context))
  }

  // --- Update State ---

  @Test
  fun clearUpdateStateRemovesCachedValues() {
    ServerPrefs.setCachedUpdateInfo(context, "v2.0.0", "https://example.com", "etag-123")
    ServerPrefs.setLastDismissedUpdateVersion(context, "v2.0.0")
    ServerPrefs.setUpdateCheckConsecutiveFailures(context, 5)

    ServerPrefs.clearUpdateState(context)

    assertNull(ServerPrefs.getCachedLatestVersion(context))
    assertNull(ServerPrefs.getCachedReleaseHtmlUrl(context))
    assertNull(ServerPrefs.getCachedReleaseETag(context))
    assertNull(ServerPrefs.getLastDismissedUpdateVersion(context))
    assertEquals(0, ServerPrefs.getUpdateCheckConsecutiveFailures(context))
  }

  // --- Default Model Name ---

  @Test
  fun defaultModelNameNullWhenNotSet() {
    assertNull(ServerPrefs.getDefaultModelName(context))
  }

  @Test
  fun defaultModelNameRoundTrip() {
    ServerPrefs.setDefaultModelName(context, "gemma-3-4b")
    assertEquals("gemma-3-4b", ServerPrefs.getDefaultModelName(context))
  }

  @Test
  fun defaultModelNameSetToNullRemovesKey() {
    ServerPrefs.setDefaultModelName(context, "gemma-3-4b")
    ServerPrefs.setDefaultModelName(context, null)
    assertNull(ServerPrefs.getDefaultModelName(context))
  }

  // --- Per-Model Prefs Key Migration ---

  @Test
  fun migratePerModelKeysMovesDataToNewKeys() {
    ServerPrefs.setSystemPrompt(context, "Gemma-4-E2B-it", "You are helpful.")
    ServerPrefs.setInferenceConfig(context, "Gemma-4-E2B-it", mapOf("Temperature" to 0.7))

    ServerPrefs.migratePerModelKeys(context, mapOf("Gemma-4-E2B-it" to "gemma-4-E2B-it.litertlm"))

    assertEquals("You are helpful.", ServerPrefs.getSystemPrompt(context, "gemma-4-E2B-it.litertlm"))
    assertEquals(0.7, (ServerPrefs.getInferenceConfig(context, "gemma-4-E2B-it.litertlm")!!["Temperature"] as Number).toDouble(), 0.001)
    assertEquals("", ServerPrefs.getSystemPrompt(context, "Gemma-4-E2B-it"))
    assertNull(ServerPrefs.getInferenceConfig(context, "Gemma-4-E2B-it"))
  }

  @Test
  fun migratePerModelKeysSkipsWhenNewKeyAlreadyExists() {
    ServerPrefs.setSystemPrompt(context, "gemma-4-E2B-it.litertlm", "Keep this one.")
    ServerPrefs.setSystemPrompt(context, "Gemma-4-E2B-it", "Don't overwrite.")

    ServerPrefs.migratePerModelKeys(context, mapOf("Gemma-4-E2B-it" to "gemma-4-E2B-it.litertlm"))

    assertEquals("Keep this one.", ServerPrefs.getSystemPrompt(context, "gemma-4-E2B-it.litertlm"))
  }

  @Test
  fun migratePerModelKeysRunsOnlyOnce() {
    ServerPrefs.setSystemPrompt(context, "ModelA", "prompt1")
    ServerPrefs.migratePerModelKeys(context, mapOf("ModelA" to "model-a.litertlm"))
    assertEquals("prompt1", ServerPrefs.getSystemPrompt(context, "model-a.litertlm"))

    ServerPrefs.setSystemPrompt(context, "ModelA", "prompt2")
    ServerPrefs.migratePerModelKeys(context, mapOf("ModelA" to "model-a.litertlm"))
    assertEquals("prompt2", ServerPrefs.getSystemPrompt(context, "ModelA"))
  }

  // --- CORS ---

  @Test
  fun corsDefaultsToWildcard() {
    assertEquals("*", ServerPrefs.getCorsAllowedOrigins(context))
  }

  @Test
  fun corsRoundTrip() {
    ServerPrefs.setCorsAllowedOrigins(context, "http://homeassistant.local:8123")
    assertEquals("http://homeassistant.local:8123", ServerPrefs.getCorsAllowedOrigins(context))
  }

  // --- Pref Delegate Round-Trips ---

  @Test
  fun boolPrefDelegateRoundTrips() {
    assertTrue(ServerPrefs.isKeepScreenOn(context))
    ServerPrefs.setKeepScreenOn(context, false)
    assertFalse(ServerPrefs.isKeepScreenOn(context))
    ServerPrefs.setKeepScreenOn(context, true)
    assertTrue(ServerPrefs.isKeepScreenOn(context))
  }

  @Test
  fun intPrefDelegateRoundTrips() {
    assertEquals(5, ServerPrefs.getKeepAliveMinutes(context))
    ServerPrefs.setKeepAliveMinutes(context, 60)
    assertEquals(60, ServerPrefs.getKeepAliveMinutes(context))
  }

  @Test
  fun longPrefDelegateRoundTrips() {
    val defaultMinutes = 7L * 24 * 60
    assertEquals(defaultMinutes, ServerPrefs.getLogAutoDeleteMinutes(context))
    ServerPrefs.setLogAutoDeleteMinutes(context, 1440L)
    assertEquals(1440L, ServerPrefs.getLogAutoDeleteMinutes(context))
  }
}
