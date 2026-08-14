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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Immutable domain model representing server runtime settings.
 */
data class ServerSettings(
  val hostPort: Int = DEFAULT_PORT,
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
  val logPersistenceEnabled: Boolean = false,
  val logMaxEntries: Int = DEFAULT_IN_MEMORY_LOG_CAP,
  val logAutoDeleteMinutes: Long = 0L,
) {
  fun toRequestPrefsSnapshot(): RequestPrefsSnapshot = RequestPrefsSnapshot(
    autoTruncateHistory = autoTruncateHistory,
    autoTrimPrompts = autoTrimPrompts,
    ignoreClientSamplerParams = ignoreClientSamplerParams,
    eagerVisionInit = eagerVisionInit,
    streamLogsPreview = streamLogsPreview,
    keepPartialResponse = keepPartialResponse,
    compactImageData = compactImageData,
    resolveClientHostnames = resolveClientHostnames,
    hideHealthLogs = hideHealthLogs,
    verboseDebug = verboseDebug,
    rejectWhenBusy = rejectWhenBusy,
    sttTranscriptionPromptEnabled = sttTranscriptionPromptEnabled,
    sttTranscriptionPromptText = sttTranscriptionPromptText,
    schemaInjectionToolCalling = schemaInjectionToolCalling,
  )
}

/**
 * Repository interface providing reactive StateFlow access and immutable snapshots of server settings.
 */
interface ServerSettingsRepository {
  /** Observable flow of live server settings. */
  val settings: StateFlow<ServerSettings>

  /** Return the currently cached settings snapshot without blocking disk I/O. */
  fun getSnapshot(): ServerSettings

  /** Return a [RequestPrefsSnapshot] for use in request handling and inference hot path. */
  fun createRequestPrefsSnapshot(): RequestPrefsSnapshot
}

@Singleton
class DefaultServerSettingsRepository(
  private val context: Context,
) : ServerSettingsRepository {
  private val sharedPreferences: SharedPreferences =
    context.getSharedPreferences("llm_http_prefs", Context.MODE_PRIVATE)

  private val _settings = MutableStateFlow(readCurrentSettings())
  override val settings: StateFlow<ServerSettings> = _settings.asStateFlow()

  private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
    _settings.value = readCurrentSettings()
  }

  init {
    sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
  }

  private fun readCurrentSettings(): ServerSettings {
    val snapshot = ServerPrefs.captureRequestSnapshot(context)
    return ServerSettings(
      hostPort = ServerPrefs.getPort(context),
      autoTruncateHistory = snapshot.autoTruncateHistory,
      autoTrimPrompts = snapshot.autoTrimPrompts,
      ignoreClientSamplerParams = snapshot.ignoreClientSamplerParams,
      eagerVisionInit = snapshot.eagerVisionInit,
      streamLogsPreview = snapshot.streamLogsPreview,
      keepPartialResponse = snapshot.keepPartialResponse,
      compactImageData = snapshot.compactImageData,
      resolveClientHostnames = snapshot.resolveClientHostnames,
      hideHealthLogs = snapshot.hideHealthLogs,
      verboseDebug = snapshot.verboseDebug,
      rejectWhenBusy = snapshot.rejectWhenBusy,
      sttTranscriptionPromptEnabled = snapshot.sttTranscriptionPromptEnabled,
      sttTranscriptionPromptText = snapshot.sttTranscriptionPromptText,
      schemaInjectionToolCalling = snapshot.schemaInjectionToolCalling,
      logPersistenceEnabled = ServerPrefs.isLogPersistenceEnabled(context),
      logMaxEntries = ServerPrefs.getLogMaxEntries(context),
      logAutoDeleteMinutes = ServerPrefs.getLogAutoDeleteMinutes(context),
    )
  }

  override fun getSnapshot(): ServerSettings = _settings.value

  override fun createRequestPrefsSnapshot(): RequestPrefsSnapshot =
    _settings.value.toRequestPrefsSnapshot()
}
