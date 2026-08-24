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
  private var _keepScreenOn: Boolean = false
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
