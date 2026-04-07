package com.ollitert.llm.server.service

import com.ollitert.llm.server.ui.navigation.ServerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton holding live server metrics. Written by [LlmHttpService], read by the UI layer.
 */
object ServerMetrics {

  private val _status = MutableStateFlow(ServerStatus.STOPPED)
  val status: StateFlow<ServerStatus> = _status.asStateFlow()

  private val _activeModelName = MutableStateFlow<String?>(null)
  val activeModelName: StateFlow<String?> = _activeModelName.asStateFlow()

  private val _port = MutableStateFlow(LlmHttpService.DEFAULT_PORT)
  val port: StateFlow<Int> = _port.asStateFlow()

  private val _bindAddress = MutableStateFlow<String?>(null)
  val bindAddress: StateFlow<String?> = _bindAddress.asStateFlow()

  /** Epoch millis when the server entered RUNNING state, or 0 if stopped. */
  private val _startedAtMs = MutableStateFlow(0L)
  val startedAtMs: StateFlow<Long> = _startedAtMs.asStateFlow()

  private val _requestCount = AtomicLong(0)
  private val _requestCountFlow = MutableStateFlow(0L)
  val requestCount: StateFlow<Long> = _requestCountFlow.asStateFlow()

  private val _tokensGenerated = AtomicLong(0)
  private val _tokensGeneratedFlow = MutableStateFlow(0L)
  val tokensGenerated: StateFlow<Long> = _tokensGeneratedFlow.asStateFlow()

  private val _tokensIn = AtomicLong(0)
  private val _tokensInFlow = MutableStateFlow(0L)
  val tokensIn: StateFlow<Long> = _tokensInFlow.asStateFlow()

  private val _lastLatencyMs = MutableStateFlow(0L)
  val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

  private val _peakLatencyMs = MutableStateFlow(0L)
  val peakLatencyMs: StateFlow<Long> = _peakLatencyMs.asStateFlow()

  private val _totalLatencyMs = AtomicLong(0)
  private val _latencyCount = AtomicLong(0)
  private val _avgLatencyMs = MutableStateFlow(0L)
  val avgLatencyMs: StateFlow<Long> = _avgLatencyMs.asStateFlow()

  // Request modality counters
  private val _textRequests = AtomicLong(0)
  private val _textRequestsFlow = MutableStateFlow(0L)
  val textRequests: StateFlow<Long> = _textRequestsFlow.asStateFlow()

  private val _imageRequests = AtomicLong(0)
  private val _imageRequestsFlow = MutableStateFlow(0L)
  val imageRequests: StateFlow<Long> = _imageRequestsFlow.asStateFlow()

  private val _audioRequests = AtomicLong(0)
  private val _audioRequestsFlow = MutableStateFlow(0L)
  val audioRequests: StateFlow<Long> = _audioRequestsFlow.asStateFlow()

  // Error tracking
  private val _errorCount = AtomicLong(0)
  private val _errorCountFlow = MutableStateFlow(0L)
  val errorCount: StateFlow<Long> = _errorCountFlow.asStateFlow()

  /** Model load (warm-up) time in milliseconds. */
  private val _modelLoadTimeMs = MutableStateFlow(0L)
  val modelLoadTimeMs: StateFlow<Long> = _modelLoadTimeMs.asStateFlow()

  /** Epoch millis when model loading started, or 0 if not loading. */
  private val _loadingStartedAtMs = MutableStateFlow(0L)
  val loadingStartedAtMs: StateFlow<Long> = _loadingStartedAtMs.asStateFlow()

  /** Human-readable error message when status is ERROR, or null. */
  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  /** True while the model is actively generating a response. */
  private val _isInferring = MutableStateFlow(false)
  val isInferring: StateFlow<Boolean> = _isInferring.asStateFlow()

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
    _loadingStartedAtMs.value = 0L
    _lastError.value = null
    _isInferring.value = false
  }

  fun onServerStopped() {
    _status.value = ServerStatus.STOPPED
    _activeModelName.value = null
    _bindAddress.value = null
    _startedAtMs.value = 0L
    _requestCount.set(0)
    _requestCountFlow.value = 0L
    _tokensGenerated.set(0)
    _tokensGeneratedFlow.value = 0L
    _tokensIn.set(0)
    _tokensInFlow.value = 0L
    _lastLatencyMs.value = 0L
    _peakLatencyMs.value = 0L
    _totalLatencyMs.set(0)
    _latencyCount.set(0)
    _avgLatencyMs.value = 0L
    _textRequests.set(0)
    _textRequestsFlow.value = 0L
    _imageRequests.set(0)
    _imageRequestsFlow.value = 0L
    _audioRequests.set(0)
    _audioRequestsFlow.value = 0L
    _errorCount.set(0)
    _errorCountFlow.value = 0L
    _modelLoadTimeMs.value = 0L
    _loadingStartedAtMs.value = 0L
    _lastError.value = null
    _isInferring.value = false
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
    // Update peak if this is the highest latency seen
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

  fun incrementErrorCount() {
    _errorCountFlow.value = _errorCount.incrementAndGet()
  }

  fun recordModelLoadTime(ms: Long) {
    _modelLoadTimeMs.value = ms
  }

  fun onInferenceStarted() {
    _isInferring.value = true
  }

  fun onInferenceCompleted() {
    _isInferring.value = false
  }
}
