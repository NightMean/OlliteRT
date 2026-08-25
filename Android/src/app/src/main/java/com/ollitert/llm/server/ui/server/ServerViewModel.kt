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

package com.ollitert.llm.server.ui.server

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.prefs.ACTION_IN_FLIGHT_DEBOUNCE_MS
import com.ollitert.llm.server.data.repository.DefaultPreferencesRepository
import com.ollitert.llm.server.data.repository.DefaultServerStateRepository
import com.ollitert.llm.server.data.repository.PreferencesRepository
import com.ollitert.llm.server.data.repository.ServerStateRepository
import com.ollitert.llm.server.service.ServerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.ollitert.llm.server.service.queueReloadAfterLoad
import com.ollitert.llm.server.service.reload
import com.ollitert.llm.server.service.start
import com.ollitert.llm.server.service.stop

/**
 * ViewModel that exposes server state to the UI layer.
 * Reads from [ServerStateRepository] and provides start/stop controls.
 */
@HiltViewModel
class ServerViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val serverStateRepository: ServerStateRepository = DefaultServerStateRepository(),
  private val preferencesRepository: PreferencesRepository = DefaultPreferencesRepository(context),
) : ViewModel() {

  val status = serverStateRepository.status
  val isInferring = serverStateRepository.isInferring
  val activeModelName = serverStateRepository.activeModelName
  val activeModelSize = serverStateRepository.activeModelSize
  val port = serverStateRepository.port
  val bindAddress = serverStateRepository.bindAddress
  val isLoopbackOnly = serverStateRepository.isLoopbackOnly
  val startedAtMs = serverStateRepository.startedAtMs
  val requestCount = serverStateRepository.requestCount
  val tokensGenerated = serverStateRepository.tokensGenerated
  val tokensIn = serverStateRepository.tokensIn
  val lastLatencyMs = serverStateRepository.lastLatencyMs
  val peakLatencyMs = serverStateRepository.peakLatencyMs
  val avgLatencyMs = serverStateRepository.avgLatencyMs
  val textRequests = serverStateRepository.textRequests
  val imageRequests = serverStateRepository.imageRequests
  val audioRequests = serverStateRepository.audioRequests
  val errorCount = serverStateRepository.errorCount
  val lastTtfbMs = serverStateRepository.lastTtfbMs
  val avgTtfbMs = serverStateRepository.avgTtfbMs
  val lastDecodeSpeed = serverStateRepository.lastDecodeSpeed
  val peakDecodeSpeed = serverStateRepository.peakDecodeSpeed
  val lastPrefillSpeed = serverStateRepository.lastPrefillSpeed
  val lastItlMs = serverStateRepository.lastItlMs
  val lastContextUtilization = serverStateRepository.lastContextUtilization
  val activeAccelerator = serverStateRepository.activeAccelerator
  val thinkingEnabled = serverStateRepository.thinkingEnabled
  val speculativeDecodingEnabled = serverStateRepository.speculativeDecodingEnabled
  val modelLoadTimeMs = serverStateRepository.modelLoadTimeMs
  val isIdleUnloaded = serverStateRepository.isIdleUnloaded
  val loadingStartedAtMs = serverStateRepository.loadingStartedAtMs
  val lastError = serverStateRepository.lastError
  val nativeHeapBytes = serverStateRepository.nativeHeapBytes
  val appHeapUsedBytes = serverStateRepository.appHeapUsedBytes
  val appTotalPssBytes = serverStateRepository.appTotalPssBytes
  val deviceAvailRamBytes = serverStateRepository.deviceAvailRamBytes
  val deviceTotalRamBytes = serverStateRepository.deviceTotalRamBytes

  /** Debounce guard to prevent duplicate start/stop/reload intents from rapid taps. */
  private var actionInFlight = false

  /**
   * True once launch auto-start was attempted in this process. ViewModels survive
   * configuration changes (rotation) but not process death, so this suppresses the
   * auto-start effect on recreation — without it, rotating the phone while the
   * server is deliberately stopped would silently restart the server — while still
   * allowing a genuine cold launch to auto-start.
   */
  var hasAttemptedLaunchAutoStart: Boolean = false
    private set

  /** Marks launch auto-start as attempted — see [hasAttemptedLaunchAutoStart]. */
  fun markLaunchAutoStartAttempted() {
    hasAttemptedLaunchAutoStart = true
  }

  fun startServer(port: Int = preferencesRepository.getPort(), modelName: String? = null, source: String? = null) {
    if (actionInFlight) return
    setActionInFlight()
    ServerService.start(context, port, modelName, source = source)
  }

  fun stopServer() {
    if (actionInFlight) return
    setActionInFlight()
    ServerService.stop(context)
  }

  fun reloadServer(port: Int = preferencesRepository.getPort()) {
    if (actionInFlight) return
    setActionInFlight()
    val currentModel = activeModelName.value
    // Reloading mid-load is a known crash path: ServerService.reload runs cleanup
    // (Engine.close, executor shutdown) on a model whose native init hasn't returned
    // yet, and the SDK occasionally faults inside liblitertlm_jni.so when the
    // Conversation/Engine is destroyed while async work is still scheduled. Defer
    // the reload until the current load finishes — same pattern the inference
    // settings sheet uses (queueReloadAfterLoad).
    if (status.value == ServerStatus.LOADING && currentModel != null) {
      ServerService.queueReloadAfterLoad(port, currentModel, configValues = null)
    } else {
      ServerService.reload(context, port, currentModel)
    }
  }

  /**
   * Switches to a different model while the server is running. Sends a single reload
   * intent with the new model name, which cleans up the old model and starts the new one.
   * This avoids the stop + start race condition where the debounce guard drops the start.
   */
  fun switchModel(modelName: String, port: Int = preferencesRepository.getPort()) {
    if (actionInFlight) return
    setActionInFlight()
    ServerService.reload(context, port, modelName)
  }

  fun getBearerToken(): String = preferencesRepository.getBearerToken()
  fun getCorsAllowedOrigins(): String = preferencesRepository.getCorsAllowedOrigins()
  fun getClientIpPolicyConfig() = preferencesRepository.getClientIpPolicyConfig()
  fun isShowRequestTypes(): Boolean = preferencesRepository.isShowRequestTypes()
  fun isShowAdvancedMetrics(): Boolean = preferencesRepository.isShowAdvancedMetrics()

  fun shouldShowEngagementPrompt(): Boolean = preferencesRepository.shouldShowEngagementPrompt()
  fun incrementEngagementPromptShowCount() = preferencesRepository.incrementEngagementPromptShowCount()
  fun setEngagementPromptPermanentlyDismissed() = preferencesRepository.setEngagementPromptPermanentlyDismissed()
  fun isGpuUnavailableServerStartDismissed(): Boolean = preferencesRepository.isGpuUnavailableServerStartDismissed()
  fun setGpuUnavailableServerStartDismissed(dismissed: Boolean) = preferencesRepository.setGpuUnavailableServerStartDismissed(dismissed)
  fun incrementManualStartCount() = preferencesRepository.incrementManualStartCount()

  private fun setActionInFlight() {
    actionInFlight = true
    viewModelScope.launch {
      delay(ACTION_IN_FLIGHT_DEBOUNCE_MS)
      actionInFlight = false
    }
  }
}
