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

import android.content.SharedPreferences
import androidx.core.content.edit

// ═══════════════════════════════════════════════════════════════════════════
// § Advanced Timeouts Keys & Accessors
// ═══════════════════════════════════════════════════════════════════════════

internal const val KEY_TIMEOUT_CHAT_COMPLETIONS = "timeout_chat_completions_seconds"
internal const val KEY_TIMEOUT_RESPONSES = "timeout_responses_seconds"
internal const val KEY_TIMEOUT_STREAMING = "timeout_streaming_seconds"
internal const val KEY_TIMEOUT_BLOCKING = "timeout_blocking_seconds"
internal const val KEY_TIMEOUT_WARMUP = "timeout_warmup_seconds"
internal const val KEY_TIMEOUT_KEEP_ALIVE_RECHECK = "timeout_keep_alive_recheck_seconds"
internal const val KEY_TIMEOUT_CLEANUP_AWAIT = "timeout_cleanup_await_seconds"

internal object ServerPrefsTimeouts {

  fun getTimeoutChatCompletions(prefs: SharedPreferences): Long =
    prefs.getLong(KEY_TIMEOUT_CHAT_COMPLETIONS, CHAT_COMPLETIONS_TIMEOUT_SECONDS)

  fun setTimeoutChatCompletions(prefs: SharedPreferences, seconds: Long) {
    prefs.edit { putLong(KEY_TIMEOUT_CHAT_COMPLETIONS, seconds) }
  }

  fun getTimeoutResponses(prefs: SharedPreferences): Long =
    prefs.getLong(KEY_TIMEOUT_RESPONSES, RESPONSES_TIMEOUT_SECONDS)

  fun setTimeoutResponses(prefs: SharedPreferences, seconds: Long) {
    prefs.edit { putLong(KEY_TIMEOUT_RESPONSES, seconds) }
  }

  fun getTimeoutStreaming(prefs: SharedPreferences): Long =
    prefs.getLong(KEY_TIMEOUT_STREAMING, STREAMING_TIMEOUT_SECONDS)

  fun setTimeoutStreaming(prefs: SharedPreferences, seconds: Long) {
    prefs.edit { putLong(KEY_TIMEOUT_STREAMING, seconds) }
  }

  fun getTimeoutBlocking(prefs: SharedPreferences): Long =
    prefs.getLong(KEY_TIMEOUT_BLOCKING, BLOCKING_TIMEOUT_SECONDS)

  fun setTimeoutBlocking(prefs: SharedPreferences, seconds: Long) {
    prefs.edit { putLong(KEY_TIMEOUT_BLOCKING, seconds) }
  }

  fun getTimeoutWarmup(prefs: SharedPreferences): Long =
    prefs.getLong(KEY_TIMEOUT_WARMUP, WARMUP_TIMEOUT_SECONDS)

  fun setTimeoutWarmup(prefs: SharedPreferences, seconds: Long) {
    prefs.edit { putLong(KEY_TIMEOUT_WARMUP, seconds) }
  }

  fun getTimeoutKeepAliveRecheckSeconds(prefs: SharedPreferences): Long =
    prefs.getLong(KEY_TIMEOUT_KEEP_ALIVE_RECHECK, KEEP_ALIVE_RECHECK_MS / 1000)

  fun setTimeoutKeepAliveRecheckSeconds(prefs: SharedPreferences, seconds: Long) {
    prefs.edit { putLong(KEY_TIMEOUT_KEEP_ALIVE_RECHECK, seconds) }
  }

  fun getTimeoutCleanupAwait(prefs: SharedPreferences): Long =
    prefs.getLong(KEY_TIMEOUT_CLEANUP_AWAIT, CLEANUP_AWAIT_TIMEOUT_SECONDS)

  fun setTimeoutCleanupAwait(prefs: SharedPreferences, seconds: Long) {
    prefs.edit { putLong(KEY_TIMEOUT_CLEANUP_AWAIT, seconds) }
  }
}
