package com.ollitert.llm.server.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A single API request/response pair displayed in the Logs screen.
 */
data class RequestLogEntry(
  val id: String,
  val timestamp: Long = System.currentTimeMillis(),
  val method: String,
  val path: String,
  val requestBody: String? = null,
  val responseBody: String? = null,
  val statusCode: Int = 200,
  val tokens: Long = 0,
  val latencyMs: Long = 0,
  val isStreaming: Boolean = false,
  val modelName: String? = null,
)

/**
 * In-memory FIFO store for API request logs. Max [MAX_ENTRIES] entries.
 * Clears on app restart. Observable via [entries] StateFlow.
 */
object RequestLogStore {

  private const val MAX_ENTRIES = 100

  private val _entries = MutableStateFlow<List<RequestLogEntry>>(emptyList())
  val entries: StateFlow<List<RequestLogEntry>> = _entries.asStateFlow()

  fun add(entry: RequestLogEntry) {
    val current = _entries.value.toMutableList()
    current.add(0, entry) // newest first
    if (current.size > MAX_ENTRIES) {
      current.removeAt(current.lastIndex)
    }
    _entries.value = current
  }

  fun clear() {
    _entries.value = emptyList()
  }
}
