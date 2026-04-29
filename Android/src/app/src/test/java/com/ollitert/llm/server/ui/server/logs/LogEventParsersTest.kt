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

package com.ollitert.llm.server.ui.server.logs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEventParsersTest {

  // ── parseEventType: Loading ───────────────────────────────────────────────

  @Test
  fun loadingModel() {
    val result = parseEventType("Loading model: Gemma 3 1B")
    assertTrue(result is ParsedEventType.Loading)
    assertEquals("Gemma 3 1B", (result as ParsedEventType.Loading).modelName)
  }

  @Test
  fun loadingModelWithSpecialChars() {
    val result = parseEventType("Loading model: gemma-4-e2b_it (v3)")
    assertTrue(result is ParsedEventType.Loading)
    assertEquals("gemma-4-e2b_it (v3)", (result as ParsedEventType.Loading).modelName)
  }

  // ── parseEventType: Ready ─────────────────────────────────────────────────

  @Test
  fun modelReady() {
    val result = parseEventType("Model ready: Gemma 3 1B (1234ms)")
    assertTrue(result is ParsedEventType.Ready)
    val ready = result as ParsedEventType.Ready
    assertEquals("Gemma 3 1B", ready.modelName)
    assertEquals("1234", ready.timeMs)
  }

  @Test
  fun modelReadySingleDigitMs() {
    val result = parseEventType("Model ready: TinyModel (5ms)")
    assertTrue(result is ParsedEventType.Ready)
    assertEquals("5", (result as ParsedEventType.Ready).timeMs)
  }

  @Test
  fun modelReadyNoMatch() {
    val result = parseEventType("Model ready: NoParens")
    assertNull(result)
  }

  // ── parseEventType: Warmup ────────────────────────────────────────────────

  @Test
  fun warmupMessage() {
    val result = parseEventType("""Sending a warmup message: "Hello" → "Hi there!" (200ms)""")
    assertTrue(result is ParsedEventType.Warmup)
    val warmup = result as ParsedEventType.Warmup
    assertEquals("Hello", warmup.input)
    assertEquals("Hi there!", warmup.output)
    assertEquals("200", warmup.timeMs)
  }

  @Test
  fun warmupWithQuotesInOutput() {
    val result = parseEventType("""Sending a warmup message: "Hi" → "She said "hello" back" (100ms)""")
    assertTrue(result is ParsedEventType.Warmup)
    val warmup = result as ParsedEventType.Warmup
    assertEquals("Hi", warmup.input)
    assertEquals("""She said "hello" back""", warmup.output)
  }

  // ── parseEventType: ServerStopped ─────────────────────────────────────────

  @Test
  fun serverStopped() {
    val result = parseEventType("Server stopped")
    assertTrue(result is ParsedEventType.ServerStopped)
  }

  @Test
  fun serverStoppedPartialNoMatch() {
    val result = parseEventType("Server stopped unexpectedly")
    assertNull(result)
  }

  // ── parseEventType: WarmupSkipped ─────────────────────────────────────────

  @Test
  fun warmupSkipped() {
    val result = parseEventType("Warmup skipped — model loaded without test inference (disabled in Settings)")
    assertTrue(result is ParsedEventType.WarmupSkipped)
    assertEquals(
      "model loaded without test inference (disabled in Settings)",
      (result as ParsedEventType.WarmupSkipped).reason,
    )
  }

  // ── parseEventType: ModelLoadFailed ───────────────────────────────────────

  @Test
  fun modelLoadFailed() {
    val result = parseEventType("Model load failed: Out of memory")
    assertTrue(result is ParsedEventType.ModelLoadFailed)
    assertEquals("Out of memory", (result as ParsedEventType.ModelLoadFailed).errorMessage)
  }

  // ── parseEventType: ServerFailed ──────────────────────────────────────────

  @Test
  fun serverFailed() {
    val result = parseEventType("Server failed to start on port 8000: Address already in use")
    assertTrue(result is ParsedEventType.ServerFailed)
    assertEquals(
      "Server failed to start on port 8000: Address already in use",
      (result as ParsedEventType.ServerFailed).errorMessage,
    )
  }

  // ── parseEventType: ModelNotFound ─────────────────────────────────────────

  @Test
  fun modelNotFoundQuoted() {
    val result = parseEventType("Model 'gpt-4' not found")
    assertTrue(result is ParsedEventType.ModelNotFound)
    assertEquals("gpt-4 (not in downloaded or available models)", (result as ParsedEventType.ModelNotFound).detail)
  }

  @Test
  fun modelFilesNotFound() {
    val result = parseEventType("Model files not found on disk")
    assertTrue(result is ParsedEventType.ModelNotFound)
    assertEquals("Model files missing from device storage", (result as ParsedEventType.ModelNotFound).detail)
  }

  @Test
  fun modelNotFoundGeneric() {
    val result = parseEventType("Model not found in registry")
    assertTrue(result is ParsedEventType.ModelNotFound)
    assertEquals("Model not found in registry", (result as ParsedEventType.ModelNotFound).detail)
  }

  // ── parseEventType: ImageDecodeFailed ─────────────────────────────────────

  @Test
  fun imageDecodeFailed() {
    val result = parseEventType("Failed to decode image: Invalid base64")
    assertTrue(result is ParsedEventType.ImageDecodeFailed)
    assertEquals("Invalid base64", (result as ParsedEventType.ImageDecodeFailed).errorMessage)
  }

  // ── parseEventType: QueuedReload ──────────────────────────────────────────

  @Test
  fun queuedReload() {
    val result = parseEventType("Applying queued settings change — reloading model")
    assertTrue(result is ParsedEventType.QueuedReload)
  }

  // ── parseEventType: ConversationResetFailed ───────────────────────────────

  @Test
  fun conversationResetFailed() {
    val result = parseEventType("Failed to reset conversation: Engine closed")
    assertTrue(result is ParsedEventType.ConversationResetFailed)
    assertEquals("Engine closed", (result as ParsedEventType.ConversationResetFailed).errorMessage)
  }

  // ── parseEventType: RestartRequested ──────────────────────────────────────

  @Test
  fun restartRequested() {
    val result = parseEventType("Model restart requested")
    assertTrue(result is ParsedEventType.RestartRequested)
  }

  // ── parseEventType: Unloading ─────────────────────────────────────────────

  @Test
  fun unloadingModel() {
    val result = parseEventType("Unloading model: Gemma 3 1B")
    assertTrue(result is ParsedEventType.Unloading)
    assertEquals("Gemma 3 1B", (result as ParsedEventType.Unloading).modelName)
  }

  // ── parseEventType: KeepAlive ─────────────────────────────────────────────

  @Test
  fun keepAliveUnloaded() {
    val result = parseEventType("Model unloaded: Gemma 3 1B (after 30m idle, keep_alive)")
    assertTrue(result is ParsedEventType.KeepAliveUnloaded)
    val ka = result as ParsedEventType.KeepAliveUnloaded
    assertEquals("Gemma 3 1B", ka.modelName)
    assertEquals("30", ka.idleMinutes)
  }

  @Test
  fun keepAliveReloading() {
    val result = parseEventType("Auto-reloading model: Gemma 3 1B (keep_alive wake-up)")
    assertTrue(result is ParsedEventType.KeepAliveReloading)
    assertEquals("Gemma 3 1B", (result as ParsedEventType.KeepAliveReloading).modelName)
  }

  @Test
  fun keepAliveReloaded() {
    val result = parseEventType("Model reloaded: Gemma 3 1B (850ms, keep_alive wake-up)")
    assertTrue(result is ParsedEventType.KeepAliveReloaded)
    val ka = result as ParsedEventType.KeepAliveReloaded
    assertEquals("Gemma 3 1B", ka.modelName)
    assertEquals("850", ka.timeMs)
  }

  // ── parseEventType: PromptActive ──────────────────────────────────────────

  @Test
  fun systemPromptActiveNoBody() {
    val result = parseEventType("System prompt active: \"test\"")
    assertTrue(result is ParsedEventType.PromptActive)
    assertEquals("System prompt", (result as ParsedEventType.PromptActive).promptType)
  }

  @Test
  fun chatTemplateActiveNoBody() {
    val result = parseEventType("Chat template active: \"<start>\"")
    assertTrue(result is ParsedEventType.PromptActive)
    assertEquals("Chat template", (result as ParsedEventType.PromptActive).promptType)
  }

  // ── parseEventType: Update ────────────────────────────────────────────────

  @Test
  fun updateAvailable() {
    val result = parseEventType("Update available: v1.2.0", "Current: v1.1.0\nRelease: https://github.com/repo/releases/tag/v1.2.0")
    assertTrue(result is ParsedEventType.UpdateAvailable)
    val ua = result as ParsedEventType.UpdateAvailable
    assertEquals("v1.2.0", ua.version)
    assertEquals("https://github.com/repo/releases/tag/v1.2.0", ua.releaseUrl)
    assertEquals("Current: v1.1.0\nRelease: https://github.com/repo/releases/tag/v1.2.0", ua.body)
  }

  @Test
  fun updateAvailableWithoutUrl() {
    val result = parseEventType("Update available: v1.2.0", "Release notes only")
    assertTrue(result is ParsedEventType.UpdateAvailable)
    val ua = result as ParsedEventType.UpdateAvailable
    assertEquals("v1.2.0", ua.version)
    assertNull(ua.releaseUrl)
  }

  @Test
  fun updateAvailableWithNullBody() {
    val result = parseEventType("Update available: v1.2.0", null)
    assertTrue(result is ParsedEventType.UpdateAvailable)
    val ua = result as ParsedEventType.UpdateAvailable
    assertEquals("v1.2.0", ua.version)
    assertNull(ua.releaseUrl)
    assertNull(ua.body)
  }

  @Test
  fun updateCurrent() {
    val result = parseEventType("Already on latest version")
    assertTrue(result is ParsedEventType.UpdateCurrent)
  }

  @Test
  fun updateAutoDisabled() {
    val result = parseEventType("Update check auto-disabled after 5 failures")
    assertTrue(result is ParsedEventType.UpdateAutoDisabled)
  }

  // ── parseEventType: MemoryPressure ────────────────────────────────────────

  @Test
  fun memoryPressure() {
    val result = parseEventType("System memory pressure (critical)")
    assertTrue(result is ParsedEventType.MemoryPressure)
  }

  // ── parseEventType: SettingsToggle ────────────────────────────────────────

  @Test
  fun settingsToggleEnabled() {
    val result = parseEventType("Truncate History enabled")
    assertTrue(result is ParsedEventType.SettingsToggle)
    val st = result as ParsedEventType.SettingsToggle
    assertEquals("Truncate History", st.settingName)
    assertTrue(st.enabled)
  }

  @Test
  fun settingsToggleDisabled() {
    val result = parseEventType("Auto-Start on Boot disabled")
    assertTrue(result is ParsedEventType.SettingsToggle)
    val st = result as ParsedEventType.SettingsToggle
    assertEquals("Auto-Start on Boot", st.settingName)
    assertTrue(!st.enabled)
  }

  @Test
  fun settingsToggleIgnoresLongNames() {
    val longName = "A".repeat(81) + " enabled"
    assertNull(parseEventType(longName))
  }

  // ── parseEventType: SettingsBatch ─────────────────────────────────────────

  @Test
  fun settingsBatchWithArrow() {
    val body = "Port: 8000 → 8001\nMax Tokens: 1024 → 2048"
    val result = parseEventType("Settings updated (2 changes)", body)
    assertTrue(result is ParsedEventType.SettingsBatch)
    val batch = (result as ParsedEventType.SettingsBatch).changes
    assertEquals(2, batch.size)
    assertEquals("Port", batch[0].paramName)
    assertEquals("8000", batch[0].oldValue)
    assertEquals("8001", batch[0].newValue)
    assertEquals("Max Tokens", batch[1].paramName)
  }

  @Test
  fun settingsBatchWithToggle() {
    val body = "Auto-Start on Boot: enabled"
    val result = parseEventType("Settings updated (1 change)", body)
    assertTrue(result is ParsedEventType.SettingsBatch)
    val batch = (result as ParsedEventType.SettingsBatch).changes
    assertEquals(1, batch.size)
    assertEquals("Auto-Start on Boot", batch[0].paramName)
    assertEquals("", batch[0].oldValue)
    assertEquals("enabled", batch[0].newValue)
  }

  // ── parseEventType: ApiConfigChange ───────────────────────────────────────

  @Test
  fun apiConfigChange() {
    val body = "Temperature: 0.7 → 1.0"
    val result = parseEventType("Config via REST API (1 change)", body)
    assertTrue(result is ParsedEventType.ApiConfigChange)
    val changes = (result as ParsedEventType.ApiConfigChange).changes
    assertEquals(1, changes.size)
    assertEquals("Temperature", changes[0].paramName)
    assertEquals("0.7", changes[0].oldValue)
    assertEquals("1.0", changes[0].newValue)
  }

  @Test
  fun apiConfigChangeNoBody() {
    val result = parseEventType("Config via REST API (1 change)")
    assertNull(result)
  }

  // ── parseEventType: AudioTranscription ─────────────────────────────────────

  @Test
  fun audioTranscriptionWithLanguage() {
    val result = parseEventType("Audio transcription: Gemma 4 (lang=en, wav, 245KB, 3.2s)")
    assertTrue(result is ParsedEventType.AudioTranscription)
    val at = result as ParsedEventType.AudioTranscription
    assertEquals("Gemma 4", at.modelName)
    assertEquals("en", at.language)
    assertEquals("wav", at.audioFormat)
    assertEquals("245KB", at.fileSize)
    assertEquals("3.2s", at.durationSec)
    assertFalse(at.forced)
    assertNull(at.serverPrompt)
    assertNull(at.transcription)
  }

  @Test
  fun audioTranscriptionWithoutLanguage() {
    val result = parseEventType("Audio transcription: Gemma 4 (mp3, 120KB, 1.5s)")
    assertTrue(result is ParsedEventType.AudioTranscription)
    val at = result as ParsedEventType.AudioTranscription
    assertEquals("Gemma 4", at.modelName)
    assertNull(at.language)
    assertEquals("mp3", at.audioFormat)
    assertEquals("120KB", at.fileSize)
    assertEquals("1.5s", at.durationSec)
    assertFalse(at.forced)
    assertNull(at.serverPrompt)
    assertNull(at.transcription)
  }

  @Test
  fun audioTranscriptionFlacFormat() {
    val result = parseEventType("Audio transcription: TinyModel (lang=ja, flac, 1.2MB, 10.0s)")
    assertTrue(result is ParsedEventType.AudioTranscription)
    val at = result as ParsedEventType.AudioTranscription
    assertEquals("TinyModel", at.modelName)
    assertEquals("ja", at.language)
    assertEquals("flac", at.audioFormat)
    assertEquals("1.2MB", at.fileSize)
    assertEquals("10.0s", at.durationSec)
    assertFalse(at.forced)
    assertNull(at.serverPrompt)
    assertNull(at.transcription)
  }

  @Test
  fun audioTranscriptionForced() {
    val result = parseEventType("Audio transcription: Gemma 4 (wav, 245KB, 3.2s, forced)")
    assertTrue(result is ParsedEventType.AudioTranscription)
    val at = result as ParsedEventType.AudioTranscription
    assertEquals("Gemma 4", at.modelName)
    assertNull(at.language)
    assertEquals("wav", at.audioFormat)
    assertEquals("245KB", at.fileSize)
    assertEquals("3.2s", at.durationSec)
    assertTrue(at.forced)
    assertNull(at.serverPrompt)
    assertNull(at.transcription)
  }

  @Test
  fun audioTranscriptionWithBody() {
    val body = """{"type":"audio_transcription","instruction":"Transcribe the audio exactly as spoken.","transcription":"Hello world"}"""
    val result = parseEventType("Audio transcription: Gemma 4 (wav, 245KB, 3.2s, forced)", body)
    assertTrue(result is ParsedEventType.AudioTranscription)
    val at = result as ParsedEventType.AudioTranscription
    assertEquals("Gemma 4", at.modelName)
    assertEquals("Transcribe the audio exactly as spoken.", at.serverPrompt)
    assertEquals("Hello world", at.transcription)
    assertTrue(at.forced)
  }

  @Test
  fun audioTranscriptionWithPlainTextBody() {
    val result = parseEventType("Audio transcription: Gemma 4 (wav, 245KB, 3.2s)", "Hello world")
    assertTrue(result is ParsedEventType.AudioTranscription)
    val at = result as ParsedEventType.AudioTranscription
    assertNull(at.serverPrompt)
    assertEquals("Hello world", at.transcription)
  }

  // ── parseEventType: unknown message ───────────────────────────────────────

  @Test
  fun unknownMessageReturnsNull() {
    assertNull(parseEventType("Something totally unknown happened"))
  }

  // ── parseInferenceSettingsMessage ──────────────────────────────────────────

  @Test
  fun inferenceSettingsWrongPrefix() {
    val result = parseInferenceSettingsMessage("Something else", "{}")
    assertNull(result)
  }

  @Test
  fun inferenceSettingsNullBody() {
    val result = parseInferenceSettingsMessage("Inference settings changed: TopK", null)
    assertNull(result)
  }

  @Test
  fun inferenceSettingsBlankBody() {
    val result = parseInferenceSettingsMessage("Inference settings changed: TopK", "")
    assertNull(result)
  }

  @Test
  fun inferenceSettingsFromJsonBody() {
    val body = """
      {
        "type": "inference_settings",
        "changes": [
          {"param": "TopK", "old": "14", "new": "15"},
          {"param": "Temperature", "old": "0.8", "new": "0.9"}
        ],
        "status": "reloading model"
      }
    """.trimIndent()
    val result = parseInferenceSettingsMessage("Inference settings changed: TopK: 14 → 15, Temperature: 0.8 → 0.9", body)
    assertNotNull(result)
    assertEquals(2, result!!.changes.size)
    assertEquals("TopK", result.changes[0].paramName)
    assertEquals("14", result.changes[0].oldValue)
    assertEquals("15", result.changes[0].newValue)
    assertEquals("reloading model", result.statusSuffix)
  }

  @Test
  fun inferenceSettingsWithPromptDiffs() {
    val body = """
      {
        "type": "inference_settings",
        "changes": [{"param": "TopK", "old": "10", "new": "20"}],
        "prompt_diffs": {
          "system_prompt": {"old": "Be helpful", "new": "Be concise"}
        }
      }
    """.trimIndent()
    val result = parseInferenceSettingsMessage("Inference settings changed: TopK: 10 → 20", body)
    assertNotNull(result)
    assertEquals(1, result!!.promptDiffs.size)
    assertEquals("system_prompt", result.promptDiffs[0].paramName)
    assertEquals("Be helpful", result.promptDiffs[0].oldText)
    assertEquals("Be concise", result.promptDiffs[0].newText)
  }

  @Test
  fun inferenceSettingsWithInvalidJsonReturnsNull() {
    val result = parseInferenceSettingsMessage("Inference settings changed: TopK", "{broken")
    assertNull(result)
  }

  // ── parseEventType: PromptActive with JSON body ──────────────────────────

  @Test
  fun promptActiveWithJsonBody() {
    val body = """{"type":"prompt_active","prompt_type":"system_prompt","text":"Be helpful."}"""
    val result = parseEventType("System prompt active: \"Be helpful.\"", body)
    assertTrue(result is ParsedEventType.PromptActive)
    val prompt = result as ParsedEventType.PromptActive
    assertEquals("System prompt", prompt.promptType)
    assertEquals("Be helpful.", prompt.promptText)
  }

  @Test
  fun promptActiveWithInvalidBodyFallsBack() {
    val result = parseEventType("System prompt active: \"test\"", "{bad json")
    assertTrue(result is ParsedEventType.PromptActive)
    assertEquals("", (result as ParsedEventType.PromptActive).promptText)
  }
}
