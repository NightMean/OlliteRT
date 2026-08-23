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
import com.ollitert.llm.server.data.ACTION_IN_FLIGHT_DEBOUNCE_MS
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.service.ServerService
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.DefaultServerStateRepository
import com.ollitert.llm.server.data.ServerStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel that exposes server state to the UI layer.
 * Reads from [ServerStateRepository] and provides start/stop controls.
 */
@HiltViewModel
class ServerViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val serverStateRepository: ServerStateRepository = DefaultServerStateRepository(),
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

  fun startServer(port: Int = ServerPrefs.getPort(context), modelName: String? = null, source: String? = null) {
    if (actionInFlight) return
    setActionInFlight()
    ServerService.start(context, port, modelName, source = source)
  }

  fun stopServer() {
    if (actionInFlight) return
    setActionInFlight()
    ServerService.stop(context)
  }

  fun reloadServer(port: Int = ServerPrefs.getPort(context)) {
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
  fun switchModel(modelName: String, port: Int = ServerPrefs.getPort(context)) {
    if (actionInFlight) return
    setActionInFlight()
    ServerService.reload(context, port, modelName)
  }

  private fun setActionInFlight() {
    actionInFlight = true
    viewModelScope.launch {
      delay(ACTION_IN_FLIGHT_DEBOUNCE_MS)
      actionInFlight = false
    }
  }

}
