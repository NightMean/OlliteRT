package com.ollitert.llm.server.ui.server

import android.content.Context
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.service.LlmHttpService
import com.ollitert.llm.server.service.ServerMetrics
import com.ollitert.llm.server.ui.navigation.ServerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.lifecycle.ViewModel

/**
 * ViewModel that exposes server state to the UI layer.
 * Reads from [ServerMetrics] singleton and provides start/stop controls.
 */
@HiltViewModel
class ServerViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : ViewModel() {

  val status = ServerMetrics.status
  val activeModelName = ServerMetrics.activeModelName
  val port = ServerMetrics.port
  val bindAddress = ServerMetrics.bindAddress
  val startedAtMs = ServerMetrics.startedAtMs
  val requestCount = ServerMetrics.requestCount
  val tokensGenerated = ServerMetrics.tokensGenerated
  val lastLatencyMs = ServerMetrics.lastLatencyMs
  val avgLatencyMs = ServerMetrics.avgLatencyMs

  fun startServer(port: Int = LlmHttpPrefs.getPort(context), modelName: String? = null) {
    LlmHttpService.start(context, port, modelName)
  }

  fun stopServer() {
    LlmHttpService.stop(context)
  }

  fun isRunning(): Boolean = status.value == ServerStatus.RUNNING
}
