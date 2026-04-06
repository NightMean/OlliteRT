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

  private val _lastLatencyMs = MutableStateFlow(0L)
  val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

  private val _totalLatencyMs = AtomicLong(0)
  private val _latencyCount = AtomicLong(0)
  private val _avgLatencyMs = MutableStateFlow(0L)
  val avgLatencyMs: StateFlow<Long> = _avgLatencyMs.asStateFlow()

  fun onServerStarting(port: Int, modelName: String?) {
    _status.value = ServerStatus.LOADING
    _port.value = port
    _activeModelName.value = modelName
  }

  fun onServerRunning(bindAddress: String?) {
    _status.value = ServerStatus.RUNNING
    _bindAddress.value = bindAddress
    _startedAtMs.value = System.currentTimeMillis()
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
    _lastLatencyMs.value = 0L
    _totalLatencyMs.set(0)
    _latencyCount.set(0)
    _avgLatencyMs.value = 0L
  }

  fun onServerError() {
    _status.value = ServerStatus.ERROR
    _startedAtMs.value = 0L
  }

  fun incrementRequestCount() {
    _requestCountFlow.value = _requestCount.incrementAndGet()
  }

  fun addTokens(count: Long) {
    _tokensGeneratedFlow.value = _tokensGenerated.addAndGet(count)
  }

  fun recordLatency(ms: Long) {
    _lastLatencyMs.value = ms
    val totalMs = _totalLatencyMs.addAndGet(ms)
    val count = _latencyCount.incrementAndGet()
    _avgLatencyMs.value = totalMs / count
  }
}
