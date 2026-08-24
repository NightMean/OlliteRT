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

import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.RequestLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory test fake implementation of [RequestLogStoreRepository].
 */
class FakeRequestLogStoreRepository : RequestLogStoreRepository {

  private val _entries = MutableStateFlow<List<RequestLogEntry>>(emptyList())
  override val entries: StateFlow<List<RequestLogEntry>> = _entries.asStateFlow()

  private val _pendingPartialText = MutableStateFlow<Pair<String, String?>>("" to null)
  override val pendingPartialText: StateFlow<Pair<String, String?>> = _pendingPartialText.asStateFlow()

  private var _maxEntries: Int = 100
  override val maxEntries: Int get() = _maxEntries
  override val effectiveMaxEntries: Int get() = _maxEntries

  private val idCounter = AtomicLong(0)
  val cancelledRequestIds = mutableListOf<String>()

  override fun add(entry: RequestLogEntry) {
    _entries.update { listOf(entry) + it }
  }

  override fun update(id: String, transform: (RequestLogEntry) -> RequestLogEntry) {
    _entries.update { current ->
      current.map { if (it.id == id) transform(it) else it }
    }
  }

  override fun updatePartialText(id: String, text: String) {
    _pendingPartialText.value = id to text
  }

  override fun addEvent(
    message: String,
    level: LogLevel,
    modelName: String?,
    category: EventCategory,
    body: String?,
  ) {
    add(
      RequestLogEntry(
        id = "event-${System.currentTimeMillis()}-${idCounter.incrementAndGet()}",
        method = "EVENT",
        path = message,
        requestBody = body,
        level = level,
        modelName = modelName,
        eventCategory = category,
      )
    )
  }

  override fun registerCancellation(id: String, onCancel: () -> Boolean) = Unit

  override fun unregisterCancellation(id: String) = Unit

  override fun cancelRequest(id: String) {
    cancelledRequestIds.add(id)
    update(id) { it.copy(isPending = false, isCancelled = true, cancelledByUser = true) }
  }

  override fun cancelAllPending() {
    _entries.update { current ->
      current.map { if (it.isPending) it.copy(isPending = false, isCancelled = true) else it }
    }
  }

  override fun clear() {
    _entries.value = emptyList()
  }

  override fun setMaxEntries(max: Int) {
    _maxEntries = max
    _entries.update { if (it.size > max) it.take(max) else it }
  }

  override fun loadEntries(entries: List<RequestLogEntry>) {
    _entries.value = entries
  }

  override fun removeOlderThan(cutoffMs: Long) {
    _entries.update { current -> current.filter { it.timestamp >= cutoffMs } }
  }

  override fun trimToPercentage(percentage: Int) {
    _entries.update { current ->
      val keep = (current.size * percentage / 100).coerceAtLeast(1)
      current.take(keep)
    }
  }
}
