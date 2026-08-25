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

package com.ollitert.llm.server.data.repository

import com.ollitert.llm.server.data.prefs.ClientIpPolicyConfig
import com.ollitert.llm.server.data.prefs.ClientIpPolicyMode
import com.ollitert.llm.server.data.prefs.ServerBindConfig
import com.ollitert.llm.server.data.prefs.ServerBindMode

/**
 * In-memory test fake implementation of [PreferencesRepository].
 */
class FakePreferencesRepository : PreferencesRepository {
  private var _port: Int = 8000
  private var _serverBindConfig: ServerBindConfig = ServerBindConfig(ServerBindMode.ALL_INTERFACES, "")
  private var _clientIpPolicyConfig: ClientIpPolicyConfig = ClientIpPolicyConfig(ClientIpPolicyMode.ALLOW_ALL, "")
  private var _corsAllowedOrigins: String = "*"
  private var _bearerToken: String = ""
  private var _defaultModelName: String? = null
  private var _autoStartOnBoot: Boolean = false
  private var _keepScreenOn: Boolean = true
  private var _autoExpandLogs: Boolean = false
  private var _wrapLogText: Boolean = false
  private var _streamLogsPreview: Boolean = true
  private var _logPersistenceEnabled: Boolean = false
  private var _logMaxEntries: Int = 500
  private var _logAutoDeleteMinutes: Long = 10080L
  private var _keepAliveEnabled: Boolean = false
  private var _keepAliveMinutes: Int = 5
  private var _rejectWhenBusy: Boolean = false
  private var _verboseDebugEnabled: Boolean = false
  private var _haIntegrationEnabled: Boolean = false
  private var _updateCheckEnabled: Boolean = true
  private var _updateCheckIntervalHours: Int = 24
  private var _hfToken: String = ""
  private var _customPromptsEnabled: Boolean = false
  private var _autoTruncateHistory: Boolean = false
  private var _autoTrimPrompts: Boolean = false
  private var _schemaInjectionToolCalling: Boolean = true
  private var _warmupEnabled: Boolean = false
  private var _eagerVisionInit: Boolean = false
  private var _ignoreClientSamplerParams: Boolean = false
  private var _forceStreamUsage: Boolean = false
  private var _resolveClientHostnames: Boolean = false
  private var _hideHealthLogs: Boolean = false
  private var _compactImageData: Boolean = true
  private var _notifShowRequestCount: Boolean = true
  private var _showRequestTypes: Boolean = true
  private var _showAdvancedMetrics: Boolean = true
  private var _clearLogsOnStop: Boolean = false
  private var _confirmClearLogs: Boolean = true
  private var _showModelRecommendations: Boolean = true

  private var _keepPartialResponse: Boolean = false
  private var _sttTranscriptionPromptEnabled: Boolean = true
  private var _sttTranscriptionPromptText: String = ""
  private var _crossChannelNotifyEnabled: Boolean = true
  private var _timeoutChatCompletions: Long = 120L
  private var _timeoutResponses: Long = 90L
  private var _timeoutStreaming: Long = 90L
  private var _timeoutBlocking: Long = 30L
  private var _timeoutWarmup: Long = 10L
  private var _timeoutKeepAliveRecheckSeconds: Long = 30L
  private var _timeoutCleanupAwait: Long = 15L

  private val _systemPrompts = mutableMapOf<String, String>()
  private val _inferenceConfigs = mutableMapOf<String, Map<String, Any>>()

  override fun getPort(): Int = _port
  override fun savePort(port: Int) { this._port = port }
  override fun getServerBindConfig(): ServerBindConfig = _serverBindConfig
  override fun setServerBindConfig(config: ServerBindConfig) { this._serverBindConfig = config }
  override fun getClientIpPolicyConfig(): ClientIpPolicyConfig = _clientIpPolicyConfig
  override fun setClientIpPolicyConfig(config: ClientIpPolicyConfig) { this._clientIpPolicyConfig = config }
  override fun getCorsAllowedOrigins(): String = _corsAllowedOrigins
  override fun setCorsAllowedOrigins(origins: String) { this._corsAllowedOrigins = origins }
  override fun getBearerToken(): String = _bearerToken
  override fun setBearerToken(token: String) { this._bearerToken = token }
  override fun getDefaultModelName(): String? = _defaultModelName
  override fun setDefaultModelName(name: String?) { this._defaultModelName = name }
  override fun isAutoStartOnBoot(): Boolean = _autoStartOnBoot
  override fun setAutoStartOnBoot(enabled: Boolean) { this._autoStartOnBoot = enabled }
  override fun isKeepScreenOn(): Boolean = _keepScreenOn
  override fun setKeepScreenOn(enabled: Boolean) { this._keepScreenOn = enabled }
  override fun isAutoExpandLogs(): Boolean = _autoExpandLogs
  override fun setAutoExpandLogs(enabled: Boolean) { this._autoExpandLogs = enabled }
  override fun isWrapLogText(): Boolean = _wrapLogText
  override fun setWrapLogText(enabled: Boolean) { this._wrapLogText = enabled }
  override fun isStreamLogsPreview(): Boolean = _streamLogsPreview
  override fun setStreamLogsPreview(enabled: Boolean) { this._streamLogsPreview = enabled }
  override fun isLogPersistenceEnabled(): Boolean = _logPersistenceEnabled
  override fun setLogPersistenceEnabled(enabled: Boolean) { this._logPersistenceEnabled = enabled }
  override fun getLogMaxEntries(): Int = _logMaxEntries
  override fun setLogMaxEntries(max: Int) { this._logMaxEntries = max }
  override fun getLogAutoDeleteMinutes(): Long = _logAutoDeleteMinutes
  override fun setLogAutoDeleteMinutes(minutes: Long) { this._logAutoDeleteMinutes = minutes }
  override fun isKeepAliveEnabled(): Boolean = _keepAliveEnabled
  override fun setKeepAliveEnabled(enabled: Boolean) { this._keepAliveEnabled = enabled }
  override fun getKeepAliveMinutes(): Int = _keepAliveMinutes
  override fun setKeepAliveMinutes(minutes: Int) { this._keepAliveMinutes = minutes }
  override fun isRejectWhenBusy(): Boolean = _rejectWhenBusy
  override fun setRejectWhenBusy(enabled: Boolean) { this._rejectWhenBusy = enabled }
  override fun isVerboseDebugEnabled(): Boolean = _verboseDebugEnabled
  override fun setVerboseDebugEnabled(enabled: Boolean) { this._verboseDebugEnabled = enabled }
  override fun isHaIntegrationEnabled(): Boolean = _haIntegrationEnabled
  override fun setHaIntegrationEnabled(enabled: Boolean) { this._haIntegrationEnabled = enabled }
  override fun isUpdateCheckEnabled(): Boolean = _updateCheckEnabled
  override fun setUpdateCheckEnabled(enabled: Boolean) { this._updateCheckEnabled = enabled }
  override fun getUpdateCheckIntervalHours(): Int = _updateCheckIntervalHours
  override fun setUpdateCheckIntervalHours(hours: Int) { this._updateCheckIntervalHours = hours }
  override fun getHfToken(): String = _hfToken
  override fun setHfToken(token: String) { this._hfToken = token }
  override fun isCustomPromptsEnabled(): Boolean = _customPromptsEnabled
  override fun setCustomPromptsEnabled(enabled: Boolean) { this._customPromptsEnabled = enabled }
  override fun isAutoTruncateHistory(): Boolean = _autoTruncateHistory
  override fun setAutoTruncateHistory(enabled: Boolean) { this._autoTruncateHistory = enabled }
  override fun isAutoTrimPrompts(): Boolean = _autoTrimPrompts
  override fun setAutoTrimPrompts(enabled: Boolean) { this._autoTrimPrompts = enabled }
  override fun isSchemaInjectionToolCalling(): Boolean = _schemaInjectionToolCalling
  override fun setSchemaInjectionToolCalling(enabled: Boolean) { this._schemaInjectionToolCalling = enabled }
  override fun isWarmupEnabled(): Boolean = _warmupEnabled
  override fun setWarmupEnabled(enabled: Boolean) { this._warmupEnabled = enabled }
  override fun isEagerVisionInit(): Boolean = _eagerVisionInit
  override fun setEagerVisionInit(enabled: Boolean) { this._eagerVisionInit = enabled }
  private var _audioGpuAcceleration: Boolean = false
  override fun isAudioGpuAcceleration(): Boolean = _audioGpuAcceleration
  override fun setAudioGpuAcceleration(enabled: Boolean) { this._audioGpuAcceleration = enabled }
  override fun isIgnoreClientSamplerParams(): Boolean = _ignoreClientSamplerParams
  override fun setIgnoreClientSamplerParams(enabled: Boolean) { this._ignoreClientSamplerParams = enabled }
  override fun isForceStreamUsage(): Boolean = _forceStreamUsage
  override fun setForceStreamUsage(enabled: Boolean) { this._forceStreamUsage = enabled }
  override fun isResolveClientHostnames(): Boolean = _resolveClientHostnames
  override fun setResolveClientHostnames(enabled: Boolean) { this._resolveClientHostnames = enabled }
  override fun isHideHealthLogs(): Boolean = _hideHealthLogs
  override fun setHideHealthLogs(enabled: Boolean) { this._hideHealthLogs = enabled }
  override fun isCompactImageData(): Boolean = _compactImageData
  override fun setCompactImageData(enabled: Boolean) { this._compactImageData = enabled }
  override fun isNotifShowRequestCount(): Boolean = _notifShowRequestCount
  override fun setNotifShowRequestCount(enabled: Boolean) { this._notifShowRequestCount = enabled }
  override fun isShowRequestTypes(): Boolean = _showRequestTypes
  override fun setShowRequestTypes(enabled: Boolean) { this._showRequestTypes = enabled }
  override fun isShowAdvancedMetrics(): Boolean = _showAdvancedMetrics
  override fun setShowAdvancedMetrics(enabled: Boolean) { this._showAdvancedMetrics = enabled }
  override fun isClearLogsOnStop(): Boolean = _clearLogsOnStop
  override fun setClearLogsOnStop(enabled: Boolean) { this._clearLogsOnStop = enabled }
  override fun isConfirmClearLogs(): Boolean = _confirmClearLogs
  override fun setConfirmClearLogs(enabled: Boolean) { this._confirmClearLogs = enabled }
  override fun isShowModelRecommendations(): Boolean = _showModelRecommendations
  override fun setShowModelRecommendations(enabled: Boolean) { this._showModelRecommendations = enabled }
  override fun isKeepPartialResponse(): Boolean = _keepPartialResponse
  override fun setKeepPartialResponse(enabled: Boolean) { this._keepPartialResponse = enabled }
  override fun isSttTranscriptionPromptEnabled(): Boolean = _sttTranscriptionPromptEnabled
  override fun setSttTranscriptionPromptEnabled(enabled: Boolean) { this._sttTranscriptionPromptEnabled = enabled }
  override fun getSttTranscriptionPromptText(): String = _sttTranscriptionPromptText
  override fun setSttTranscriptionPromptText(text: String) { this._sttTranscriptionPromptText = text }
  override fun isCrossChannelNotifyEnabled(): Boolean = _crossChannelNotifyEnabled
  override fun setCrossChannelNotifyEnabled(enabled: Boolean) { this._crossChannelNotifyEnabled = enabled }
  override fun getTimeoutChatCompletions(): Long = _timeoutChatCompletions
  override fun setTimeoutChatCompletions(seconds: Long) { this._timeoutChatCompletions = seconds }
  override fun getTimeoutResponses(): Long = _timeoutResponses
  override fun setTimeoutResponses(seconds: Long) { this._timeoutResponses = seconds }
  override fun getTimeoutStreaming(): Long = _timeoutStreaming
  override fun setTimeoutStreaming(seconds: Long) { this._timeoutStreaming = seconds }
  override fun getTimeoutBlocking(): Long = _timeoutBlocking
  override fun setTimeoutBlocking(seconds: Long) { this._timeoutBlocking = seconds }
  override fun getTimeoutWarmup(): Long = _timeoutWarmup
  override fun setTimeoutWarmup(seconds: Long) { this._timeoutWarmup = seconds }
  override fun getTimeoutKeepAliveRecheckSeconds(): Long = _timeoutKeepAliveRecheckSeconds
  override fun setTimeoutKeepAliveRecheckSeconds(seconds: Long) { this._timeoutKeepAliveRecheckSeconds = seconds }
  override fun getTimeoutCleanupAwait(): Long = _timeoutCleanupAwait
  override fun setTimeoutCleanupAwait(seconds: Long) { this._timeoutCleanupAwait = seconds }
  override fun setCachedUpdateInfo(version: String?, tagName: String?, releaseNotes: String?) {}
  override fun resetToDefaults() {
    _port = 8000
    _serverBindConfig = ServerBindConfig(ServerBindMode.ALL_INTERFACES, "")
    _clientIpPolicyConfig = ClientIpPolicyConfig(ClientIpPolicyMode.ALLOW_ALL, "")
    _corsAllowedOrigins = "*"
    _bearerToken = ""
    _defaultModelName = null
    _autoStartOnBoot = false
    _keepScreenOn = false
    _autoExpandLogs = false
    _wrapLogText = false
    _streamLogsPreview = true
    _logPersistenceEnabled = false
    _logMaxEntries = 500
    _logAutoDeleteMinutes = 10080L
    _keepAliveEnabled = false
    _keepAliveMinutes = 5
    _rejectWhenBusy = false
    _verboseDebugEnabled = false
    _haIntegrationEnabled = false
    _updateCheckEnabled = true
    _updateCheckIntervalHours = 24
    _hfToken = ""
    _customPromptsEnabled = false
    _autoTruncateHistory = true
    _autoTrimPrompts = false
    _schemaInjectionToolCalling = true
    _warmupEnabled = false
    _eagerVisionInit = false
    _ignoreClientSamplerParams = false
    _forceStreamUsage = true
    _resolveClientHostnames = false
    _hideHealthLogs = false
    _compactImageData = true
    _notifShowRequestCount = false
    _showRequestTypes = false
    _showAdvancedMetrics = false
    _clearLogsOnStop = false
    _confirmClearLogs = true
    _showModelRecommendations = true
    _keepPartialResponse = false
    _sttTranscriptionPromptEnabled = true
    _sttTranscriptionPromptText = ""
    _crossChannelNotifyEnabled = true
    _timeoutChatCompletions = 120L
    _timeoutResponses = 90L
    _timeoutStreaming = 90L
    _timeoutBlocking = 30L
    _timeoutWarmup = 10L
    _timeoutKeepAliveRecheckSeconds = 30L
    _timeoutCleanupAwait = 15L
    _engagementPromptShowCount = 0
    _engagementPromptPermanentlyDismissed = false
    _gpuUnavailableDialogShown = false
    _gpuUnavailableServerStartDismissed = false
    _manualStartCount = 0
  }

  private var _engagementPromptShowCount: Int = 0
  private var _engagementPromptPermanentlyDismissed: Boolean = false
  private var _gpuUnavailableDialogShown: Boolean = false
  private var _gpuUnavailableServerStartDismissed: Boolean = false
  private var _manualStartCount: Int = 0

  override fun shouldShowEngagementPrompt(): Boolean =
    !_engagementPromptPermanentlyDismissed && _engagementPromptShowCount < 3
  override fun incrementEngagementPromptShowCount(): Int = ++_engagementPromptShowCount
  override fun setEngagementPromptPermanentlyDismissed() { _engagementPromptPermanentlyDismissed = true }
  override fun isGpuUnavailableDialogShown(): Boolean = _gpuUnavailableDialogShown
  override fun setGpuUnavailableDialogShown(shown: Boolean) { _gpuUnavailableDialogShown = shown }
  override fun isGpuUnavailableServerStartDismissed(): Boolean = _gpuUnavailableServerStartDismissed
  override fun setGpuUnavailableServerStartDismissed(dismissed: Boolean) { _gpuUnavailableServerStartDismissed = dismissed }
  override fun getManualStartCount(): Int = _manualStartCount
  override fun incrementManualStartCount(): Int = ++_manualStartCount

  override fun dumpToLogcat() {}
  override fun getSystemPrompt(modelName: String): String = _systemPrompts[modelName] ?: ""
  override fun setSystemPrompt(modelName: String, prompt: String) { _systemPrompts[modelName] = prompt }
  override fun getInferenceConfig(modelName: String): Map<String, Any>? = _inferenceConfigs[modelName]
  override fun setInferenceConfig(modelName: String, configValues: Map<String, Any>) { _inferenceConfigs[modelName] = configValues }
  override fun clearInferenceConfig(modelName: String) { _inferenceConfigs.remove(modelName) }
  override fun renameModelPrefsKey(oldName: String, newName: String) {
    _inferenceConfigs.remove(oldName)?.let { _inferenceConfigs[newName] = it }
    _systemPrompts.remove(oldName)?.let { _systemPrompts[newName] = it }
  }
  override fun migratePerModelKeys(nameToPrefsKey: Map<String, String>) {
    for ((oldKey, newKey) in nameToPrefsKey) {
      if (oldKey in _inferenceConfigs && newKey !in _inferenceConfigs) {
        _inferenceConfigs.remove(oldKey)?.let { _inferenceConfigs[newKey] = it }
      }
      if (oldKey in _systemPrompts && newKey !in _systemPrompts) {
        _systemPrompts.remove(oldKey)?.let { _systemPrompts[newKey] = it }
      }
    }
  }
}
