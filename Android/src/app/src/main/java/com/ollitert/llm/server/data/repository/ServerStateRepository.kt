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
import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.common.ServerLifecycleState
import com.ollitert.llm.server.common.ModelLoadPhase
import com.ollitert.llm.server.data.model.Repository
import com.ollitert.llm.server.data.prefs.isLoopbackOnly

import com.ollitert.llm.server.common.ServerStatus
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface exposing reactive server state and live telemetry flows to consumers.
 * Decouples the UI layer and ViewModels from direct static references to [ServerMetrics].
 */
interface ServerStateRepository {
  val status: StateFlow<ServerStatus>
  val lifecycleState: StateFlow<ServerLifecycleState>
  val isInferring: StateFlow<Boolean>
  val activeModelName: StateFlow<String?>
  val activeModelSize: StateFlow<Long>
  val port: StateFlow<Int>
  val bindAddress: StateFlow<String?>
  val isLoopbackOnly: StateFlow<Boolean>
  val startedAtMs: StateFlow<Long>
  val requestCount: StateFlow<Long>
  val tokensGenerated: StateFlow<Long>
  val tokensIn: StateFlow<Long>
  val lastLatencyMs: StateFlow<Long>
  val peakLatencyMs: StateFlow<Long>
  val avgLatencyMs: StateFlow<Long>
  val textRequests: StateFlow<Long>
  val imageRequests: StateFlow<Long>
  val audioRequests: StateFlow<Long>
  val errorCount: StateFlow<Long>
  val lastTtfbMs: StateFlow<Long>
  val avgTtfbMs: StateFlow<Long>
  val lastDecodeSpeed: StateFlow<Double>
  val peakDecodeSpeed: StateFlow<Double>
  val lastPrefillSpeed: StateFlow<Double>
  val lastItlMs: StateFlow<Double>
  val lastContextUtilization: StateFlow<Double>
  val activeAccelerator: StateFlow<String?>
  val thinkingEnabled: StateFlow<Boolean>
  val speculativeDecodingEnabled: StateFlow<Boolean>
  val modelLoadTimeMs: StateFlow<Long>
  val isIdleUnloaded: StateFlow<Boolean>
  val loadingStartedAtMs: StateFlow<Long>
  val modelLoadPhase: StateFlow<ModelLoadPhase>
  val lastError: StateFlow<String?>
  val nativeHeapBytes: StateFlow<Long>
  val appHeapUsedBytes: StateFlow<Long>
  val appTotalPssBytes: StateFlow<Long>
  val deviceAvailRamBytes: StateFlow<Long>
  val deviceTotalRamBytes: StateFlow<Long>
  val availableUpdateVersion: StateFlow<String?>
  val availableUpdateUrl: StateFlow<String?>

  fun setAvailableUpdate(version: String?, url: String?)
  fun clearErrorIfModel(modelName: String)
  fun updateMemorySnapshot(
    nativeHeapBytes: Long,
    appHeapUsedBytes: Long,
    appTotalPssBytes: Long,
    deviceAvailRamBytes: Long,
    deviceTotalRamBytes: Long,
  )
}

/**
 * Default implementation of [ServerStateRepository] delegating to [ServerMetrics].
 */
@Singleton
class DefaultServerStateRepository @Inject constructor() : ServerStateRepository {
  override val status: StateFlow<ServerStatus> get() = ServerMetrics.status
  override val lifecycleState: StateFlow<ServerLifecycleState> get() = ServerMetrics.lifecycleState
  override val isInferring: StateFlow<Boolean> get() = ServerMetrics.isInferring
  override val activeModelName: StateFlow<String?> get() = ServerMetrics.activeModelName
  override val activeModelSize: StateFlow<Long> get() = ServerMetrics.activeModelSize
  override val port: StateFlow<Int> get() = ServerMetrics.port
  override val bindAddress: StateFlow<String?> get() = ServerMetrics.bindAddress
  override val isLoopbackOnly: StateFlow<Boolean> get() = ServerMetrics.isLoopbackOnly
  override val startedAtMs: StateFlow<Long> get() = ServerMetrics.startedAtMs
  override val requestCount: StateFlow<Long> get() = ServerMetrics.requestCount
  override val tokensGenerated: StateFlow<Long> get() = ServerMetrics.tokensGenerated
  override val tokensIn: StateFlow<Long> get() = ServerMetrics.tokensIn
  override val lastLatencyMs: StateFlow<Long> get() = ServerMetrics.lastLatencyMs
  override val peakLatencyMs: StateFlow<Long> get() = ServerMetrics.peakLatencyMs
  override val avgLatencyMs: StateFlow<Long> get() = ServerMetrics.avgLatencyMs
  override val textRequests: StateFlow<Long> get() = ServerMetrics.textRequests
  override val imageRequests: StateFlow<Long> get() = ServerMetrics.imageRequests
  override val audioRequests: StateFlow<Long> get() = ServerMetrics.audioRequests
  override val errorCount: StateFlow<Long> get() = ServerMetrics.errorCount
  override val lastTtfbMs: StateFlow<Long> get() = ServerMetrics.lastTtfbMs
  override val avgTtfbMs: StateFlow<Long> get() = ServerMetrics.avgTtfbMs
  override val lastDecodeSpeed: StateFlow<Double> get() = ServerMetrics.lastDecodeSpeed
  override val peakDecodeSpeed: StateFlow<Double> get() = ServerMetrics.peakDecodeSpeed
  override val lastPrefillSpeed: StateFlow<Double> get() = ServerMetrics.lastPrefillSpeed
  override val lastItlMs: StateFlow<Double> get() = ServerMetrics.lastItlMs
  override val lastContextUtilization: StateFlow<Double> get() = ServerMetrics.lastContextUtilization
  override val activeAccelerator: StateFlow<String?> get() = ServerMetrics.activeAccelerator
  override val thinkingEnabled: StateFlow<Boolean> get() = ServerMetrics.thinkingEnabled
  override val speculativeDecodingEnabled: StateFlow<Boolean> get() = ServerMetrics.speculativeDecodingEnabled
  override val modelLoadTimeMs: StateFlow<Long> get() = ServerMetrics.modelLoadTimeMs
  override val isIdleUnloaded: StateFlow<Boolean> get() = ServerMetrics.isIdleUnloaded
  override val loadingStartedAtMs: StateFlow<Long> get() = ServerMetrics.loadingStartedAtMs
  override val modelLoadPhase: StateFlow<ModelLoadPhase> get() = ServerMetrics.modelLoadPhase
  override val lastError: StateFlow<String?> get() = ServerMetrics.lastError
  override val nativeHeapBytes: StateFlow<Long> get() = ServerMetrics.nativeHeapBytes
  override val appHeapUsedBytes: StateFlow<Long> get() = ServerMetrics.appHeapUsedBytes
  override val appTotalPssBytes: StateFlow<Long> get() = ServerMetrics.appTotalPssBytes
  override val deviceAvailRamBytes: StateFlow<Long> get() = ServerMetrics.deviceAvailRamBytes
  override val deviceTotalRamBytes: StateFlow<Long> get() = ServerMetrics.deviceTotalRamBytes
  override val availableUpdateVersion: StateFlow<String?> get() = ServerMetrics.availableUpdateVersion
  override val availableUpdateUrl: StateFlow<String?> get() = ServerMetrics.availableUpdateUrl

  override fun setAvailableUpdate(version: String?, url: String?) = ServerMetrics.setAvailableUpdate(version, url)
  override fun clearErrorIfModel(modelName: String) = ServerMetrics.clearErrorIfModel(modelName)
  override fun updateMemorySnapshot(
    nativeHeapBytes: Long,
    appHeapUsedBytes: Long,
    appTotalPssBytes: Long,
    deviceAvailRamBytes: Long,
    deviceTotalRamBytes: Long,
  ) = ServerMetrics.updateMemorySnapshot(
    nativeHeapBytes,
    appHeapUsedBytes,
    appTotalPssBytes,
    deviceAvailRamBytes,
    deviceTotalRamBytes,
  )
}
