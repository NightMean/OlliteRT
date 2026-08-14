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

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

private const val TAG = "OlliteRT.PrefsMigration"
private const val KEY_PREFS_KEY_MIGRATION_DONE = "prefs_key_migration_v1"
private const val KEY_STT_KEY_MIGRATION_DONE = "stt_key_migration_v1"
private const val KEY_PREFIX_SYSTEM_PROMPT = "system_prompt_"
private const val KEY_PREFIX_INFERENCE_CONFIG = "inference_config_"
private const val KEY_STT_TRANSCRIPTION_PROMPT = "stt_transcription_prompt"
private const val KEY_STT_TRANSCRIPTION_PROMPT_TEXT = "stt_transcription_prompt_text"
private const val DEFAULT_STT_TRANSCRIPTION_PROMPT = true

internal object ServerPrefsMigrations {

  fun migratePerModelKeys(prefs: SharedPreferences, modelNameToDownloadFileName: Map<String, String>) {
    if (prefs.getBoolean(KEY_PREFS_KEY_MIGRATION_DONE, false)) return

    var migrated = 0

    prefs.edit {
      for ((oldName, newKey) in modelNameToDownloadFileName) {
        if (oldName == newKey) continue

        val oldPromptKey = KEY_PREFIX_SYSTEM_PROMPT + oldName
        val newPromptKey = KEY_PREFIX_SYSTEM_PROMPT + newKey
        val prompt = prefs.getString(oldPromptKey, null)
        if (prompt != null && !prefs.contains(newPromptKey)) {
          putString(newPromptKey, prompt)
          remove(oldPromptKey)
          migrated++
        }

        val oldConfigKey = KEY_PREFIX_INFERENCE_CONFIG + oldName
        val newConfigKey = KEY_PREFIX_INFERENCE_CONFIG + newKey
        val config = prefs.getString(oldConfigKey, null)
        if (config != null && !prefs.contains(newConfigKey)) {
          putString(newConfigKey, config)
          remove(oldConfigKey)
          migrated++
        }
      }

      putBoolean(KEY_PREFS_KEY_MIGRATION_DONE, true)
    }

    if (migrated > 0) {
      Log.i(TAG, "Migrated $migrated per-model prefs key(s) to stable format")
    }
  }

  fun renameModelPrefsKey(prefs: SharedPreferences, oldKey: String, newKey: String) {
    if (oldKey == newKey) return
    prefs.edit {
      val oldPromptKey = KEY_PREFIX_SYSTEM_PROMPT + oldKey
      val newPromptKey = KEY_PREFIX_SYSTEM_PROMPT + newKey
      val prompt = prefs.getString(oldPromptKey, null)
      if (prompt != null) {
        putString(newPromptKey, prompt)
        remove(oldPromptKey)
      }

      val oldConfigKey = KEY_PREFIX_INFERENCE_CONFIG + oldKey
      val newConfigKey = KEY_PREFIX_INFERENCE_CONFIG + newKey
      val config = prefs.getString(oldConfigKey, null)
      if (config != null) {
        putString(newConfigKey, config)
        remove(oldConfigKey)
      }
    }
  }

  fun migrateSttKeys(prefs: SharedPreferences) {
    if (prefs.getBoolean(KEY_STT_KEY_MIGRATION_DONE, false)) return

    var migrated = 0

    prefs.edit {
      val oldToggle = "ha_stt_transcription_prompt"
      if (prefs.contains(oldToggle) && !prefs.contains(KEY_STT_TRANSCRIPTION_PROMPT)) {
        putBoolean(KEY_STT_TRANSCRIPTION_PROMPT, prefs.getBoolean(oldToggle, DEFAULT_STT_TRANSCRIPTION_PROMPT))
        remove(oldToggle)
        migrated++
      }

      val oldText = "ha_stt_transcription_prompt_text"
      if (prefs.contains(oldText) && !prefs.contains(KEY_STT_TRANSCRIPTION_PROMPT_TEXT)) {
        putString(KEY_STT_TRANSCRIPTION_PROMPT_TEXT, prefs.getString(oldText, DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT))
        remove(oldText)
        migrated++
      }

      putBoolean(KEY_STT_KEY_MIGRATION_DONE, true)
    }

    if (migrated > 0) {
      Log.i(TAG, "Migrated $migrated STT prefs key(s) from ha_stt_* to stt_*")
    }
  }
}
