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

import android.content.Context
import androidx.core.content.edit

// -- Keys: Home Assistant / STT ---------------------------------------------
private const val KEY_HA_INTEGRATION_ENABLED = "ha_integration_enabled"
private const val KEY_STT_TRANSCRIPTION_PROMPT = "stt_transcription_prompt"
private const val DEFAULT_STT_TRANSCRIPTION_PROMPT = true
private const val KEY_STT_TRANSCRIPTION_PROMPT_TEXT = "stt_transcription_prompt_text"
internal const val DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT =
  "Transcribe the audio exactly as spoken. Output only the transcribed text, nothing else."

// -- Pref declarations -------------------------------------------------------

internal val HA_INTEGRATION_ENABLED = BoolPref(KEY_HA_INTEGRATION_ENABLED, false)
internal val STT_TRANSCRIPTION_PROMPT = BoolPref(KEY_STT_TRANSCRIPTION_PROMPT, DEFAULT_STT_TRANSCRIPTION_PROMPT)

  // ══════════════════════════════════════════════════════════════════════════
  // § Home Assistant / STT
  // ══════════════════════════════════════════════════════════════════════════

fun ServerPrefs.isHaIntegrationEnabled(context: Context): Boolean = get(context, HA_INTEGRATION_ENABLED)
fun ServerPrefs.setHaIntegrationEnabled(context: Context, enabled: Boolean) = set(context, HA_INTEGRATION_ENABLED, enabled)

fun ServerPrefs.isSttTranscriptionPromptEnabled(context: Context): Boolean = get(context, STT_TRANSCRIPTION_PROMPT)
fun ServerPrefs.setSttTranscriptionPromptEnabled(context: Context, enabled: Boolean) = set(context, STT_TRANSCRIPTION_PROMPT, enabled)

fun ServerPrefs.getSttTranscriptionPromptText(context: Context): String =
    prefs(context).getString(KEY_STT_TRANSCRIPTION_PROMPT_TEXT, DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT)
      ?: DEFAULT_STT_TRANSCRIPTION_PROMPT_TEXT

fun ServerPrefs.setSttTranscriptionPromptText(context: Context, text: String) {
    prefs(context).edit { putString(KEY_STT_TRANSCRIPTION_PROMPT_TEXT, text) }
  }
