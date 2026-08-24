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

/**
 * Snapshot of server preferences captured at request entry time.
 *
 * Created once per HTTP request by [ServerPrefs.captureRequestSnapshot] to avoid repeated
 * SharedPreferences reads during token generation. Callers that don't provide a snapshot
 * (warmup, internal calls) fall back to live [ServerPrefs] reads via the
 * `prefs?.field ?: ServerPrefs.liveRead(context)` pattern — this is intentional.
 */
data class RequestPrefsSnapshot(
  val autoTruncateHistory: Boolean = false,
  val autoTrimPrompts: Boolean = false,
  val ignoreClientSamplerParams: Boolean = false,
  val eagerVisionInit: Boolean = false,
  val streamLogsPreview: Boolean = true,
  val keepPartialResponse: Boolean = false,
  val compactImageData: Boolean = true,
  val resolveClientHostnames: Boolean = false,
  val hideHealthLogs: Boolean = false,
  val verboseDebug: Boolean = false,
  val rejectWhenBusy: Boolean = false,
  val sttTranscriptionPromptEnabled: Boolean = true,
  val sttTranscriptionPromptText: String = "",
  val schemaInjectionToolCalling: Boolean = true,
)
