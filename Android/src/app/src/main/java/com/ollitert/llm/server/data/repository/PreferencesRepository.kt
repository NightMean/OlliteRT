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

import android.content.Context
import com.ollitert.llm.server.data.prefs.ClientIpPolicyConfig
import com.ollitert.llm.server.data.prefs.ServerBindConfig
import com.ollitert.llm.server.data.prefs.ServerPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface exposing application and server preferences to consumers.
 * Decouples ViewModels, workers, and services from direct static references to [ServerPrefs].
 */
interface PreferencesRepository {
  fun getPort(): Int
  fun savePort(port: Int)
  fun getServerBindConfig(): ServerBindConfig
  fun setServerBindConfig(config: ServerBindConfig)
  fun getClientIpPolicyConfig(): ClientIpPolicyConfig
  fun setClientIpPolicyConfig(config: ClientIpPolicyConfig)
  fun getCorsAllowedOrigins(): String
  fun setCorsAllowedOrigins(origins: String)
  fun getBearerToken(): String
  fun setBearerToken(token: String)
  fun getHfToken(): String
  fun setHfToken(token: String)
  fun getDefaultModelName(): String?
  fun setDefaultModelName(name: String?)
  fun isAutoStartOnBoot(): Boolean
  fun setAutoStartOnBoot(enabled: Boolean)
  fun isKeepScreenOn(): Boolean
  fun setKeepScreenOn(enabled: Boolean)
  fun isAutoExpandLogs(): Boolean
  fun setAutoExpandLogs(enabled: Boolean)
  fun isWrapLogText(): Boolean
  fun setWrapLogText(enabled: Boolean)
  fun isStreamLogsPreview(): Boolean
  fun setStreamLogsPreview(enabled: Boolean)
  fun isLogPersistenceEnabled(): Boolean
  fun setLogPersistenceEnabled(enabled: Boolean)
  fun getLogMaxEntries(): Int
  fun setLogMaxEntries(max: Int)
  fun getLogAutoDeleteMinutes(): Long
  fun setLogAutoDeleteMinutes(minutes: Long)
  fun isKeepAliveEnabled(): Boolean
  fun setKeepAliveEnabled(enabled: Boolean)
  fun getKeepAliveMinutes(): Int
  fun setKeepAliveMinutes(minutes: Int)
  fun isRejectWhenBusy(): Boolean
  fun setRejectWhenBusy(enabled: Boolean)
  fun isVerboseDebugEnabled(): Boolean
  fun setVerboseDebugEnabled(enabled: Boolean)
  fun isHaIntegrationEnabled(): Boolean
  fun setHaIntegrationEnabled(enabled: Boolean)
  fun isUpdateCheckEnabled(): Boolean
  fun setUpdateCheckEnabled(enabled: Boolean)
  fun getUpdateCheckIntervalHours(): Int
  fun setUpdateCheckIntervalHours(hours: Int)
  fun isCustomPromptsEnabled(): Boolean
  fun setCustomPromptsEnabled(enabled: Boolean)
  fun isAutoTruncateHistory(): Boolean
  fun setAutoTruncateHistory(enabled: Boolean)
  fun isAutoTrimPrompts(): Boolean
  fun setAutoTrimPrompts(enabled: Boolean)
  fun isSchemaInjectionToolCalling(): Boolean
  fun setSchemaInjectionToolCalling(enabled: Boolean)
  fun isWarmupEnabled(): Boolean
  fun setWarmupEnabled(enabled: Boolean)
  fun isEagerVisionInit(): Boolean
  fun setEagerVisionInit(enabled: Boolean)
  fun isIgnoreClientSamplerParams(): Boolean
  fun setIgnoreClientSamplerParams(enabled: Boolean)
  fun isForceStreamUsage(): Boolean
  fun setForceStreamUsage(enabled: Boolean)
  fun isResolveClientHostnames(): Boolean
  fun setResolveClientHostnames(enabled: Boolean)
  fun isHideHealthLogs(): Boolean
  fun setHideHealthLogs(enabled: Boolean)
  fun isCompactImageData(): Boolean
  fun setCompactImageData(enabled: Boolean)
  fun isNotifShowRequestCount(): Boolean
  fun setNotifShowRequestCount(enabled: Boolean)
  fun isShowRequestTypes(): Boolean
  fun setShowRequestTypes(enabled: Boolean)
  fun isShowAdvancedMetrics(): Boolean
  fun setShowAdvancedMetrics(enabled: Boolean)
  fun isClearLogsOnStop(): Boolean
  fun setClearLogsOnStop(enabled: Boolean)
  fun isConfirmClearLogs(): Boolean
  fun setConfirmClearLogs(enabled: Boolean)
  fun isShowModelRecommendations(): Boolean
  fun setShowModelRecommendations(enabled: Boolean)
  fun getSystemPrompt(modelName: String): String
  fun setSystemPrompt(modelName: String, prompt: String)
  fun getInferenceConfig(modelName: String): Map<String, Any>?
  fun setInferenceConfig(modelName: String, configValues: Map<String, Any>)
  fun clearInferenceConfig(modelName: String)
  fun renameModelPrefsKey(oldName: String, newName: String)
  fun migratePerModelKeys(nameToPrefsKey: Map<String, String>)
}

/**
 * Default implementation of [PreferencesRepository] delegating to [ServerPrefs].
 */
@Singleton
class DefaultPreferencesRepository @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : PreferencesRepository {
  override fun getPort(): Int = ServerPrefs.getPort(context)
  override fun savePort(port: Int) = ServerPrefs.save(context, port)
  override fun getServerBindConfig(): ServerBindConfig = ServerPrefs.getServerBindConfig(context)
  override fun setServerBindConfig(config: ServerBindConfig) = ServerPrefs.setServerBindConfig(context, config)
  override fun getClientIpPolicyConfig(): ClientIpPolicyConfig = ServerPrefs.getClientIpPolicyConfig(context)
  override fun setClientIpPolicyConfig(config: ClientIpPolicyConfig) = ServerPrefs.setClientIpPolicyConfig(context, config)
  override fun getCorsAllowedOrigins(): String = ServerPrefs.getCorsAllowedOrigins(context)
  override fun setCorsAllowedOrigins(origins: String) = ServerPrefs.setCorsAllowedOrigins(context, origins)
  override fun getBearerToken(): String = ServerPrefs.getBearerToken(context)
  override fun setBearerToken(token: String) = ServerPrefs.setBearerToken(context, token)
  override fun getHfToken(): String = ServerPrefs.getHfToken(context)
  override fun setHfToken(token: String) = ServerPrefs.setHfToken(context, token)
  override fun getDefaultModelName(): String? = ServerPrefs.getDefaultModelName(context)
  override fun setDefaultModelName(name: String?) = ServerPrefs.setDefaultModelName(context, name)
  override fun isAutoStartOnBoot(): Boolean = ServerPrefs.isAutoStartOnBoot(context)
  override fun setAutoStartOnBoot(enabled: Boolean) = ServerPrefs.setAutoStartOnBoot(context, enabled)
  override fun isKeepScreenOn(): Boolean = ServerPrefs.isKeepScreenOn(context)
  override fun setKeepScreenOn(enabled: Boolean) = ServerPrefs.setKeepScreenOn(context, enabled)
  override fun isAutoExpandLogs(): Boolean = ServerPrefs.isAutoExpandLogs(context)
  override fun setAutoExpandLogs(enabled: Boolean) = ServerPrefs.setAutoExpandLogs(context, enabled)
  override fun isWrapLogText(): Boolean = ServerPrefs.isWrapLogText(context)
  override fun setWrapLogText(enabled: Boolean) = ServerPrefs.setWrapLogText(context, enabled)
  override fun isStreamLogsPreview(): Boolean = ServerPrefs.isStreamLogsPreview(context)
  override fun setStreamLogsPreview(enabled: Boolean) = ServerPrefs.setStreamLogsPreview(context, enabled)
  override fun isLogPersistenceEnabled(): Boolean = ServerPrefs.isLogPersistenceEnabled(context)
  override fun setLogPersistenceEnabled(enabled: Boolean) = ServerPrefs.setLogPersistenceEnabled(context, enabled)
  override fun getLogMaxEntries(): Int = ServerPrefs.getLogMaxEntries(context)
  override fun setLogMaxEntries(max: Int) = ServerPrefs.setLogMaxEntries(context, max)
  override fun getLogAutoDeleteMinutes(): Long = ServerPrefs.getLogAutoDeleteMinutes(context)
  override fun setLogAutoDeleteMinutes(minutes: Long) = ServerPrefs.setLogAutoDeleteMinutes(context, minutes)
  override fun isKeepAliveEnabled(): Boolean = ServerPrefs.isKeepAliveEnabled(context)
  override fun setKeepAliveEnabled(enabled: Boolean) = ServerPrefs.setKeepAliveEnabled(context, enabled)
  override fun getKeepAliveMinutes(): Int = ServerPrefs.getKeepAliveMinutes(context)
  override fun setKeepAliveMinutes(minutes: Int) = ServerPrefs.setKeepAliveMinutes(context, minutes)
  override fun isRejectWhenBusy(): Boolean = ServerPrefs.isRejectWhenBusy(context)
  override fun setRejectWhenBusy(enabled: Boolean) = ServerPrefs.setRejectWhenBusy(context, enabled)
  override fun isVerboseDebugEnabled(): Boolean = ServerPrefs.isVerboseDebugEnabled(context)
  override fun setVerboseDebugEnabled(enabled: Boolean) = ServerPrefs.setVerboseDebugEnabled(context, enabled)
  override fun isHaIntegrationEnabled(): Boolean = ServerPrefs.isHaIntegrationEnabled(context)
  override fun setHaIntegrationEnabled(enabled: Boolean) = ServerPrefs.setHaIntegrationEnabled(context, enabled)
  override fun isUpdateCheckEnabled(): Boolean = ServerPrefs.isUpdateCheckEnabled(context)
  override fun setUpdateCheckEnabled(enabled: Boolean) = ServerPrefs.setUpdateCheckEnabled(context, enabled)
  override fun getUpdateCheckIntervalHours(): Int = ServerPrefs.getUpdateCheckIntervalHours(context)
  override fun setUpdateCheckIntervalHours(hours: Int) = ServerPrefs.setUpdateCheckIntervalHours(context, hours)
  override fun isCustomPromptsEnabled(): Boolean = ServerPrefs.isCustomPromptsEnabled(context)
  override fun setCustomPromptsEnabled(enabled: Boolean) = ServerPrefs.setCustomPromptsEnabled(context, enabled)
  override fun isAutoTruncateHistory(): Boolean = ServerPrefs.isAutoTruncateHistory(context)
  override fun setAutoTruncateHistory(enabled: Boolean) = ServerPrefs.setAutoTruncateHistory(context, enabled)
  override fun isAutoTrimPrompts(): Boolean = ServerPrefs.isAutoTrimPrompts(context)
  override fun setAutoTrimPrompts(enabled: Boolean) = ServerPrefs.setAutoTrimPrompts(context, enabled)
  override fun isSchemaInjectionToolCalling(): Boolean = ServerPrefs.isSchemaInjectionToolCalling(context)
  override fun setSchemaInjectionToolCalling(enabled: Boolean) = ServerPrefs.setSchemaInjectionToolCalling(context, enabled)
  override fun isWarmupEnabled(): Boolean = ServerPrefs.isWarmupEnabled(context)
  override fun setWarmupEnabled(enabled: Boolean) = ServerPrefs.setWarmupEnabled(context, enabled)
  override fun isEagerVisionInit(): Boolean = ServerPrefs.isEagerVisionInit(context)
  override fun setEagerVisionInit(enabled: Boolean) = ServerPrefs.setEagerVisionInit(context, enabled)
  override fun isIgnoreClientSamplerParams(): Boolean = ServerPrefs.isIgnoreClientSamplerParams(context)
  override fun setIgnoreClientSamplerParams(enabled: Boolean) = ServerPrefs.setIgnoreClientSamplerParams(context, enabled)
  override fun isForceStreamUsage(): Boolean = ServerPrefs.isForceStreamUsage(context)
  override fun setForceStreamUsage(enabled: Boolean) = ServerPrefs.setForceStreamUsage(context, enabled)
  override fun isResolveClientHostnames(): Boolean = ServerPrefs.isResolveClientHostnames(context)
  override fun setResolveClientHostnames(enabled: Boolean) = ServerPrefs.setResolveClientHostnames(context, enabled)
  override fun isHideHealthLogs(): Boolean = ServerPrefs.isHideHealthLogs(context)
  override fun setHideHealthLogs(enabled: Boolean) = ServerPrefs.setHideHealthLogs(context, enabled)
  override fun isCompactImageData(): Boolean = ServerPrefs.isCompactImageData(context)
  override fun setCompactImageData(enabled: Boolean) = ServerPrefs.setCompactImageData(context, enabled)
  override fun isNotifShowRequestCount(): Boolean = ServerPrefs.isNotifShowRequestCount(context)
  override fun setNotifShowRequestCount(enabled: Boolean) = ServerPrefs.setNotifShowRequestCount(context, enabled)
  override fun isShowRequestTypes(): Boolean = ServerPrefs.isShowRequestTypes(context)
  override fun setShowRequestTypes(enabled: Boolean) = ServerPrefs.setShowRequestTypes(context, enabled)
  override fun isShowAdvancedMetrics(): Boolean = ServerPrefs.isShowAdvancedMetrics(context)
  override fun setShowAdvancedMetrics(enabled: Boolean) = ServerPrefs.setShowAdvancedMetrics(context, enabled)
  override fun isClearLogsOnStop(): Boolean = ServerPrefs.isClearLogsOnStop(context)
  override fun setClearLogsOnStop(enabled: Boolean) = ServerPrefs.setClearLogsOnStop(context, enabled)
  override fun isConfirmClearLogs(): Boolean = ServerPrefs.isConfirmClearLogs(context)
  override fun setConfirmClearLogs(enabled: Boolean) = ServerPrefs.setConfirmClearLogs(context, enabled)
  override fun isShowModelRecommendations(): Boolean = ServerPrefs.isShowModelRecommendations(context)
  override fun setShowModelRecommendations(enabled: Boolean) = ServerPrefs.setShowModelRecommendations(context, enabled)
  override fun getSystemPrompt(modelName: String): String = ServerPrefs.getSystemPrompt(context, modelName)
  override fun setSystemPrompt(modelName: String, prompt: String) = ServerPrefs.setSystemPrompt(context, modelName, prompt)
  override fun getInferenceConfig(modelName: String): Map<String, Any>? = ServerPrefs.getInferenceConfig(context, modelName)
  override fun setInferenceConfig(modelName: String, configValues: Map<String, Any>) = ServerPrefs.setInferenceConfig(context, modelName, configValues)
  override fun clearInferenceConfig(modelName: String) = ServerPrefs.clearInferenceConfig(context, modelName)
  override fun renameModelPrefsKey(oldName: String, newName: String) = ServerPrefs.renameModelPrefsKey(context, oldName, newName)
  override fun migratePerModelKeys(nameToPrefsKey: Map<String, String>) = ServerPrefs.migratePerModelKeys(context, nameToPrefsKey)
}
