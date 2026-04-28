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

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerPrefsMigrationTest {

  private val prefs: SharedPreferences = mockk(relaxed = true)
  private val editor: SharedPreferences.Editor = mockk(relaxed = true)
  private val context: Context = mockk(relaxed = true)

  @Before
  fun setUp() {
    mockkStatic(Log::class)
    every { Log.i(any(), any()) } returns 0
    every { Log.w(any<String>(), any<String>()) } returns 0

    every { context.getSharedPreferences(any(), any()) } returns prefs
    every { prefs.edit() } returns editor
    every { editor.putString(any(), any()) } returns editor
    every { editor.putBoolean(any(), any()) } returns editor
    every { editor.remove(any()) } returns editor
    every { editor.apply() } returns Unit

    // Reset cached prefs via reflection to ensure test isolation
    ServerPrefs.resetToDefaults(context)
  }

  @After
  fun tearDown() {
    unmockkStatic(Log::class)
  }

  // ── migratePerModelKeys ───────────────────────────────────────────────────

  @Test
  fun migratePerModelKeysSkipsWhenAlreadyDone() {
    every { prefs.getBoolean("prefs_key_migration_v1", false) } returns true

    ServerPrefs.migratePerModelKeys(context, mapOf("OldName" to "new_key"))

    verify(exactly = 0) { editor.putString(any(), any()) }
  }

  @Test
  fun migratePerModelKeysMigratesSystemPrompt() {
    every { prefs.getBoolean("prefs_key_migration_v1", false) } returns false
    every { prefs.getString("system_prompt_OldModel", null) } returns "My prompt"
    every { prefs.contains("system_prompt_new_file.litertlm") } returns false
    every { prefs.getString("inference_config_OldModel", null) } returns null

    ServerPrefs.migratePerModelKeys(context, mapOf("OldModel" to "new_file.litertlm"))

    verify { editor.putString("system_prompt_new_file.litertlm", "My prompt") }
    verify { editor.remove("system_prompt_OldModel") }
    verify { editor.putBoolean("prefs_key_migration_v1", true) }
    verify { editor.apply() }
  }

  @Test
  fun migratePerModelKeysMigratesInferenceConfig() {
    every { prefs.getBoolean("prefs_key_migration_v1", false) } returns false
    every { prefs.getString("system_prompt_OldModel", null) } returns null
    every { prefs.getString("inference_config_OldModel", null) } returns """{"temperature":0.7}"""
    every { prefs.contains("inference_config_new_file.litertlm") } returns false

    ServerPrefs.migratePerModelKeys(context, mapOf("OldModel" to "new_file.litertlm"))

    verify { editor.putString("inference_config_new_file.litertlm", """{"temperature":0.7}""") }
    verify { editor.remove("inference_config_OldModel") }
    verify { editor.putBoolean("prefs_key_migration_v1", true) }
  }

  @Test
  fun migratePerModelKeysSkipsWhenNewKeyExists() {
    every { prefs.getBoolean("prefs_key_migration_v1", false) } returns false
    every { prefs.getString("system_prompt_OldModel", null) } returns "My prompt"
    every { prefs.contains("system_prompt_new_file.litertlm") } returns true
    every { prefs.getString("inference_config_OldModel", null) } returns null

    ServerPrefs.migratePerModelKeys(context, mapOf("OldModel" to "new_file.litertlm"))

    verify(exactly = 0) { editor.putString("system_prompt_new_file.litertlm", any()) }
  }

  @Test
  fun migratePerModelKeysSkipsSameNameMapping() {
    every { prefs.getBoolean("prefs_key_migration_v1", false) } returns false

    ServerPrefs.migratePerModelKeys(context, mapOf("same_key" to "same_key"))

    verify(exactly = 0) { editor.putString(match { it.startsWith("system_prompt_") }, any()) }
    verify { editor.putBoolean("prefs_key_migration_v1", true) }
  }

  // ── migrateSttKeys ────────────────────────────────────────────────────────

  @Test
  fun migrateSttKeysSkipsWhenAlreadyDone() {
    every { prefs.getBoolean("stt_key_migration_v1", false) } returns true

    ServerPrefs.migrateSttKeys(context)

    verify(exactly = 0) { editor.putBoolean("stt_transcription_prompt", any()) }
  }

  @Test
  fun migrateSttKeysMigratesToggleAndText() {
    every { prefs.getBoolean("stt_key_migration_v1", false) } returns false
    every { prefs.contains("ha_stt_transcription_prompt") } returns true
    every { prefs.contains("stt_transcription_prompt") } returns false
    every { prefs.getBoolean("ha_stt_transcription_prompt", true) } returns true
    every { prefs.contains("ha_stt_transcription_prompt_text") } returns true
    every { prefs.contains("stt_transcription_prompt_text") } returns false
    every { prefs.getString("ha_stt_transcription_prompt_text", any()) } returns "Custom prompt"

    ServerPrefs.migrateSttKeys(context)

    verify { editor.putBoolean("stt_transcription_prompt", true) }
    verify { editor.remove("ha_stt_transcription_prompt") }
    verify { editor.putString("stt_transcription_prompt_text", "Custom prompt") }
    verify { editor.remove("ha_stt_transcription_prompt_text") }
    verify { editor.putBoolean("stt_key_migration_v1", true) }
  }

  @Test
  fun migrateSttKeysSkipsIfNewKeysAlreadyExist() {
    every { prefs.getBoolean("stt_key_migration_v1", false) } returns false
    every { prefs.contains("ha_stt_transcription_prompt") } returns true
    every { prefs.contains("stt_transcription_prompt") } returns true
    every { prefs.contains("ha_stt_transcription_prompt_text") } returns true
    every { prefs.contains("stt_transcription_prompt_text") } returns true

    ServerPrefs.migrateSttKeys(context)

    verify(exactly = 0) { editor.putBoolean("stt_transcription_prompt", any()) }
    verify(exactly = 0) { editor.putString("stt_transcription_prompt_text", any<String>()) }
    verify { editor.putBoolean("stt_key_migration_v1", true) }
  }

  @Test
  fun migrateSttKeysNoOldKeysDoesNothing() {
    every { prefs.getBoolean("stt_key_migration_v1", false) } returns false
    every { prefs.contains("ha_stt_transcription_prompt") } returns false
    every { prefs.contains("ha_stt_transcription_prompt_text") } returns false

    ServerPrefs.migrateSttKeys(context)

    verify(exactly = 0) { editor.putBoolean("stt_transcription_prompt", any()) }
    verify(exactly = 0) { editor.putString("stt_transcription_prompt_text", any<String>()) }
    verify { editor.putBoolean("stt_key_migration_v1", true) }
  }
}
