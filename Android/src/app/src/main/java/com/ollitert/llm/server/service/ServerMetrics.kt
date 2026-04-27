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

package com.ollitert.llm.server.service

import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.common.ServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton holding live server metrics. Written by [ServerService], read by the UI layer.
 */
object ServerMetrics {

  // Registry of session-scoped fields — reset together in onServerStopped().
  // New metrics declared with sessionFlow()/sessionAtomic() are automatically included.
  private val sessionFlowResets = mutableListOf<() -> Unit>()
  private val sessionAtomicResets = mutableListOf<() -> Unit>()

  private fun <T> sessionFlow(default: T): MutableStateFlow<T> {
    val flow = MutableStateFlow(default)
    sessionFlowResets.add { flow.value = default }
    return flow
  }

  private fun sessionAtomic(): AtomicLong {
    val atomic = AtomicLong(0)
    sessionAtomicResets.add { atomic.set(0) }
    return atomic
  }

  private val _status = sessionFlow(ServerStatus.STOPPED)
  val status: StateFlow<ServerStatus> = _status.asStateFlow()

  private val _activeModelName = sessionFlow<String?>(null)
  val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

  // App-level state — not reset on server stop, only on resetForTesting()
  private val _port = MutableStateFlow(ServerService.DEFAULT_PORT)
  val port: StateFlow<Int> = _port.asStateFlow()

  private val _bindAddress = sessionFlow<String?>(null)
  val bindAddress: StateFlow<String?> = _bindAddress.asStateFlow()

  /** Epoch millis when the server entered RUNNING state, or 0 if stopped. */
  private val _startedAtMs = sessionFlow(0L)
  val startedAtMs: StateFlow<Long> = _startedAtMs.asStateFlow()

  private val _requestCount = sessionAtomic()
  private val _requestCountFlow = sessionFlow(0L)
  val requestCount: StateFlow<Long> = _requestCountFlow.asStateFlow()

  private val _tokensGenerated = sessionAtomic()
  private val _tokensGeneratedFlow = sessionFlow(0L)
  val tokensGenerated: StateFlow<Long> = _tokensGeneratedFlow.asStateFlow()

  private val _tokensIn = sessionAtomic()
  private val _tokensInFlow = sessionFlow(0L)
  val tokensIn: StateFlow<Long> = _tokensInFlow.asStateFlow()

  private val _lastLatencyMs = sessionFlow(0L)
  val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

  private val _peakLatencyMs = sessionFlow(0L)
  val peakLatencyMs: StateFlow<Long> = _peakLatencyMs.asStateFlow()

  private val _totalLatencyMs = sessionAtomic()
  private val _latencyCount = sessionAtomic()
  private val _avgLatencyMs = sessionFlow(0L)
  val avgLatencyMs: StateFlow<Long> = _avgLatencyMs.asStateFlow()

  // Request modality counters
  private val _textRequests = sessionAtomic()
  private val _textRequestsFlow = sessionFlow(0L)
  val textRequests: StateFlow<Long> = _textRequestsFlow.asStateFlow()

  private val _imageRequests = sessionAtomic()
  private val _imageRequestsFlow = sessionFlow(0L)
  val imageRequests: StateFlow<Long> = _imageRequestsFlow.asStateFlow()

  private val _audioRequests = sessionAtomic()
  private val _audioRequestsFlow = sessionFlow(0L)
  val audioRequests: StateFlow<Long> = _audioRequestsFlow.asStateFlow()

  // Time to first token (TTFB) tracking
  private val _lastTtfbMs = sessionFlow(0L)
  val lastTtfbMs: StateFlow<Long> = _lastTtfbMs.asStateFlow()

  private val _totalTtfbMs = sessionAtomic()
  private val _ttfbCount = sessionAtomic()
  private val _avgTtfbMs = sessionFlow(0L)
  val avgTtfbMs: StateFlow<Long> = _avgTtfbMs.asStateFlow()

  // Per-request decode speed (tokens/sec for last completed request)
  private val _lastDecodeSpeed = sessionFlow(0.0)
  val lastDecodeSpeed: StateFlow<Double> = _lastDecodeSpeed.asStateFlow()

  // Peak decode speed — highest decode speed seen since server start
  private val _peakDecodeSpeed = sessionFlow(0.0)
  val peakDecodeSpeed: StateFlow<Double> = _peakDecodeSpeed.asStateFlow()

  // Prefill speed — input tokens processed per second (inputTokens / ttfbSeconds)
  private val _lastPrefillSpeed = sessionFlow(0.0)
  val lastPrefillSpeed: StateFlow<Double> = _lastPrefillSpeed.asStateFlow()

  // Inter-Token Latency — average ms between consecutive output tokens
  private val _lastItlMs = sessionFlow(0.0)
  val lastItlMs: StateFlow<Double> = _lastItlMs.asStateFlow()

  // Context utilization — last request's input tokens as % of model's max context window.
  // Displayed on the Status screen; uses estimated token counts (charLen / 4).
  private val _lastContextUtilization = sessionFlow(0.0)
  val lastContextUtilization: StateFlow<Double> = _lastContextUtilization.asStateFlow()

  // Cumulative timing counters for Prometheus /metrics endpoint (mirrors llama.cpp approach).
  // These track total wall-clock time spent in prefill and decode phases across all requests.
  private val _totalPrefillMs = sessionAtomic()
  val totalPrefillMs: Long get() = _totalPrefillMs.get()

  private val _totalDecodeMs = sessionAtomic()
  val totalDecodeMs: Long get() = _totalDecodeMs.get()

  // Error tracking — aggregate + per-category for Prometheus labeled metrics
  private val _errorCount = sessionAtomic()
  private val _errorCountFlow = sessionFlow(0L)
  val errorCount: StateFlow<Long> = _errorCountFlow.asStateFlow()

  private val _modelLoadErrors = sessionAtomic()
  private val _modelLoadErrorsFlow = sessionFlow(0L)
  val modelLoadErrors: StateFlow<Long> = _modelLoadErrorsFlow.asStateFlow()

  private val _inferenceErrors = sessionAtomic()
  private val _inferenceErrorsFlow = sessionFlow(0L)
  val inferenceErrors: StateFlow<Long> = _inferenceErrorsFlow.asStateFlow()

  private val _networkErrors = sessionAtomic()
  private val _networkErrorsFlow = sessionFlow(0L)
  val networkErrors: StateFlow<Long> = _networkErrorsFlow.asStateFlow()

  private val _systemErrors = sessionAtomic()
  private val _systemErrorsFlow = sessionFlow(0L)
  val systemErrors: StateFlow<Long> = _systemErrorsFlow.asStateFlow()

  /** Model load (warm-up) time in milliseconds. */
  private val _modelLoadTimeMs = sessionFlow(0L)
  val modelLoadTimeMs: StateFlow<Long> = _modelLoadTimeMs.asStateFlow()

  /** Epoch seconds when the model finished loading. Used as `created` in /v1/models responses. */
  private val _modelCreatedAtEpoch = sessionFlow(0L)
  val modelCreatedAtEpoch: StateFlow<Long> = _modelCreatedAtEpoch.asStateFlow()

  /** Epoch millis when model loading started, or 0 if not loading. */
  private val _loadingStartedAtMs = sessionFlow(0L)
  val loadingStartedAtMs: StateFlow<Long> = _loadingStartedAtMs.asStateFlow()

  /** Size of the active model in bytes, or 0 if none. */
  private val _activeModelSize = sessionFlow(0L)
  val activeModelSize: StateFlow<Long> = _activeModelSize.asStateFlow()

  /** Active accelerator backend (e.g. "GPU", "CPU") for the loaded model, or null if none. */
  private val _activeAccelerator = sessionFlow<String?>(null)
  val activeAccelerator: StateFlow<String?> = _activeAccelerator.asStateFlow()

  /** Whether the "Allow Thinking" toggle is enabled for the active model. */
  private val _thinkingEnabled = sessionFlow(false)
  val thinkingEnabled: StateFlow<Boolean> = _thinkingEnabled.asStateFlow()

  /** Human-readable error message when status is ERROR, or null. */
  private val _lastError = sessionFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  /** True while the model is actively generating a response. */
  private val _isInferring = sessionFlow(false)
  val isInferring: StateFlow<Boolean> = _isInferring.asStateFlow()

  // App-level state — not reset on server stop, only on resetForTesting()
  private val _availableUpdateVersion = MutableStateFlow<String?>(null)
  val availableUpdateVersion: StateFlow<String?> = _availableUpdateVersion.asStateFlow()

  /** URL of the GitHub Release page for the available update, or null. */
  private val _availableUpdateUrl = MutableStateFlow<String?>(null)
  val availableUpdateUrl: StateFlow<String?> = _availableUpdateUrl.asStateFlow()

  fun setAvailableUpdate(version: String?, url: String?) {
    _availableUpdateVersion.value = version
    _availableUpdateUrl.value = url
  }

  /**
   * True when the model was unloaded due to keep_alive idle timeout.
   * The server is still running (Ktor up, port bound) but the native Engine/Conversation
   * have been freed to reclaim RAM. The next inference request will auto-reload the model.
   */
  private val _isIdleUnloaded = sessionFlow(false)
  val isIdleUnloaded: StateFlow<Boolean> = _isIdleUnloaded.asStateFlow()

  // ── Memory snapshot (updated periodically by UI-side polling) ──────────

  /** Native heap allocated bytes — dominated by LiteRT model weights. */
  private val _nativeHeapBytes = sessionFlow(0L)
  val nativeHeapBytes: StateFlow<Long> = _nativeHeapBytes.asStateFlow()

  /** JVM heap used bytes (totalMemory - freeMemory). */
  private val _appHeapUsedBytes = sessionFlow(0L)
  val appHeapUsedBytes: StateFlow<Long> = _appHeapUsedBytes.asStateFlow()

  /**
   * Total process PSS (Proportional Set Size) in bytes.
   * Includes JVM heap + native heap + mmap'd model pages resident in RAM.
   * This is the actual RAM footprint of the app — the same metric Android's
   * Settings > Apps > Memory shows. Measured via [android.app.ActivityManager.getProcessMemoryInfo].
   */
  private val _appTotalPssBytes = sessionFlow(0L)
  val appTotalPssBytes: StateFlow<Long> = _appTotalPssBytes.asStateFlow()

  /** Device available RAM from ActivityManager.MemoryInfo.availMem. */
  private val _deviceAvailRamBytes = sessionFlow(0L)
  val deviceAvailRamBytes: StateFlow<Long> = _deviceAvailRamBytes.asStateFlow()

  /** Device total RAM from ActivityManager.MemoryInfo.totalMem (or advertisedMem on API 34+). */
  private val _deviceTotalRamBytes = sessionFlow(0L)
  val deviceTotalRamBytes: StateFlow<Long> = _deviceTotalRamBytes.asStateFlow()

  fun onServerStarting(port: Int, modelName: String?) {
    _status.value = ServerStatus.LOADING
    _port.value = port
    _activeModelName.value = modelName
    _loadingStartedAtMs.value = System.currentTimeMillis()
    _lastError.value = null
  }

  fun onServerRunning(bindAddress: String?) {
    _status.value = ServerStatus.RUNNING
    _bindAddress.value = bindAddress
    _startedAtMs.value = System.currentTimeMillis()
    _modelCreatedAtEpoch.value = System.currentTimeMillis() / 1000
    _loadingStartedAtMs.value = 0L
    _lastError.value = null
    _isInferring.value = false
    _isIdleUnloaded.value = false
  }

  fun onServerStopped() {
    sessionAtomicResets.forEach { it() }
    sessionFlowResets.forEach { it() }
  }

  fun onServerError(message: String? = null) {
    _status.value = ServerStatus.ERROR
    _startedAtMs.value = 0L
    _loadingStartedAtMs.value = 0L
    _lastError.value = message
  }

  fun incrementRequestCount() {
    _requestCountFlow.value = _requestCount.incrementAndGet()
  }

  fun addTokens(count: Long) {
    _tokensGeneratedFlow.value = _tokensGenerated.addAndGet(count)
  }

  fun addTokensIn(count: Long) {
    _tokensInFlow.value = _tokensIn.addAndGet(count)
  }

  fun recordLatency(ms: Long) {
    _lastLatencyMs.value = ms
    // Synchronized: MutableStateFlow.value read-compare-write isn't atomic without explicit locking.
    synchronized(this) {
      if (ms > _peakLatencyMs.value) _peakLatencyMs.value = ms
    }
    val totalMs = _totalLatencyMs.addAndGet(ms)
    val count = _latencyCount.incrementAndGet()
    _avgLatencyMs.value = totalMs / count
  }

  fun recordModality(hasImages: Boolean, hasAudio: Boolean) {
    when {
      hasImages -> _imageRequestsFlow.value = _imageRequests.incrementAndGet()
      hasAudio -> _audioRequestsFlow.value = _audioRequests.incrementAndGet()
      else -> _textRequestsFlow.value = _textRequests.incrementAndGet()
    }
  }

  /** Increment both the aggregate and per-category error counters. */
  fun incrementErrorCount(category: ErrorCategory) {
    _errorCountFlow.value = _errorCount.incrementAndGet()
    when (category) {
      ErrorCategory.MODEL_LOAD -> _modelLoadErrorsFlow.value = _modelLoadErrors.incrementAndGet()
      ErrorCategory.INFERENCE -> _inferenceErrorsFlow.value = _inferenceErrors.incrementAndGet()
      ErrorCategory.NETWORK -> _networkErrorsFlow.value = _networkErrors.incrementAndGet()
      ErrorCategory.SYSTEM -> _systemErrorsFlow.value = _systemErrors.incrementAndGet()
    }
  }

  fun setActiveModelSize(bytes: Long) {
    _activeModelSize.value = bytes
  }

  fun setActiveAccelerator(accelerator: String?) {
    _activeAccelerator.value = accelerator
  }

  fun setThinkingEnabled(enabled: Boolean) {
    _thinkingEnabled.value = enabled
  }

  fun recordModelLoadTime(ms: Long) {
    _modelLoadTimeMs.value = ms
  }

  /** Record time to first token in milliseconds. Values <= 0 are ignored. */
  fun recordTtfb(ms: Long) {
    if (ms <= 0) return
    _lastTtfbMs.value = ms
    val totalMs = _totalTtfbMs.addAndGet(ms)
    val count = _ttfbCount.incrementAndGet()
    _avgTtfbMs.value = totalMs / count
  }

  /**
   * Record per-request performance metrics derived from timing data.
   * Called once per completed request with all available timing info.
   *
   * @param inputTokens  estimated input token count via [estimateTokens]
   * @param outputTokens estimated output token count via [estimateTokens]
   * @param ttfbMs       time to first token (ms) — approximates prefill time
   * @param generationMs time from first token to last token (totalMs - ttfbMs)
   * @param maxContextTokens model's max context window size (0 if unknown)
   */
  fun recordInferenceMetrics(
    inputTokens: Long,
    outputTokens: Long,
    ttfbMs: Long,
    generationMs: Long,
    maxContextTokens: Long = 0,
  ) {
    // Accumulate cumulative timing for Prometheus counters
    if (ttfbMs > 0) _totalPrefillMs.addAndGet(ttfbMs)
    if (generationMs > 0) _totalDecodeMs.addAndGet(generationMs)

    // Decode speed: output tokens / generation time (excludes prefill)
    if (outputTokens > 0 && generationMs > 0) {
      val decodeSpeed = outputTokens.toDouble() / (generationMs.toDouble() / 1000.0)
      _lastDecodeSpeed.value = decodeSpeed
      synchronized(this) {
        if (decodeSpeed > _peakDecodeSpeed.value) _peakDecodeSpeed.value = decodeSpeed
      }
    }

    // Prefill speed: input tokens / TTFB (TTFB ≈ prefill time at HTTP layer)
    if (inputTokens > 0 && ttfbMs > 0) {
      _lastPrefillSpeed.value = inputTokens.toDouble() / (ttfbMs.toDouble() / 1000.0)
    }

    // Inter-Token Latency: average ms between consecutive output tokens
    if (outputTokens > 1 && generationMs > 0) {
      _lastItlMs.value = generationMs.toDouble() / (outputTokens.toDouble() - 1)
    }

    // Context utilization: input tokens as % of max context window
    if (maxContextTokens > 0 && inputTokens > 0) {
      _lastContextUtilization.value = (inputTokens.toDouble() / maxContextTokens.toDouble()) * 100.0
    }
  }

  fun onInferenceStarted() {
    _isInferring.value = true
  }

  fun onInferenceCompleted() {
    _isInferring.value = false
  }

  fun onModelIdleUnloaded() {
    _isIdleUnloaded.value = true
  }

  fun onModelReloadedFromIdle() {
    _isIdleUnloaded.value = false
  }

  /**
   * Push a memory snapshot from the UI polling loop.
   * Called every few seconds by a [LaunchedEffect] in [com.ollitert.llm.server.ui.navigation.OlliteRTBottomNavBar] using
   * [android.os.Debug.getNativeHeapAllocatedSize], [Runtime.getRuntime], and
   * [android.app.ActivityManager.MemoryInfo].
   */
  fun updateMemorySnapshot(
    nativeHeapBytes: Long,
    appHeapUsedBytes: Long,
    appTotalPssBytes: Long,
    deviceAvailRamBytes: Long,
    deviceTotalRamBytes: Long,
  ) {
    _nativeHeapBytes.value = nativeHeapBytes
    _appHeapUsedBytes.value = appHeapUsedBytes
    _appTotalPssBytes.value = appTotalPssBytes
    _deviceAvailRamBytes.value = deviceAvailRamBytes
    _deviceTotalRamBytes.value = deviceTotalRamBytes
  }

  /**
   * Reset ALL mutable state for test isolation.
   * Calls [onServerStopped] (which resets server-session state) then also resets
   * app-level state that [onServerStopped] intentionally preserves.
   */
  fun resetForTesting() {
    onServerStopped()
    _availableUpdateVersion.value = null
    _availableUpdateUrl.value = null
    _port.value = ServerService.DEFAULT_PORT
  }
}
