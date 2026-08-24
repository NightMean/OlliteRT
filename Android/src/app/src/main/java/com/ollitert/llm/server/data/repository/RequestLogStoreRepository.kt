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
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface exposing in-memory request log entries, partial text streaming flow,
 * and log lifecycle operations to UI components and ViewModels.
 * Decouples the UI layer from static singleton access to [RequestLogStore].
 */
interface RequestLogStoreRepository {
  val entries: StateFlow<List<RequestLogEntry>>
  val pendingPartialText: StateFlow<Pair<String, String?>>
  val maxEntries: Int
  val effectiveMaxEntries: Int

  fun add(entry: RequestLogEntry)
  fun update(id: String, transform: (RequestLogEntry) -> RequestLogEntry)
  fun updatePartialText(id: String, text: String)
  fun addEvent(
    message: String,
    level: LogLevel = LogLevel.INFO,
    modelName: String? = null,
    category: EventCategory = EventCategory.GENERAL,
    body: String? = null,
  )
  fun registerCancellation(id: String, onCancel: () -> Boolean)
  fun unregisterCancellation(id: String)
  fun cancelRequest(id: String)
  fun cancelAllPending()
  fun clear()
  fun setMaxEntries(max: Int)
  fun loadEntries(entries: List<RequestLogEntry>)
  fun removeOlderThan(cutoffMs: Long)
  fun trimToPercentage(percentage: Int)
}

/**
 * Default implementation of [RequestLogStoreRepository] delegating directly to [RequestLogStore].
 */
@Singleton
class DefaultRequestLogStoreRepository @Inject constructor() : RequestLogStoreRepository {
  override val entries: StateFlow<List<RequestLogEntry>> get() = RequestLogStore.entries
  override val pendingPartialText: StateFlow<Pair<String, String?>> get() = RequestLogStore.pendingPartialText
  override val maxEntries: Int get() = RequestLogStore.maxEntries
  override val effectiveMaxEntries: Int get() = RequestLogStore.effectiveMaxEntries

  override fun add(entry: RequestLogEntry) = RequestLogStore.add(entry)
  override fun update(id: String, transform: (RequestLogEntry) -> RequestLogEntry) = RequestLogStore.update(id, transform)
  override fun updatePartialText(id: String, text: String) = RequestLogStore.updatePartialText(id, text)
  override fun addEvent(
    message: String,
    level: LogLevel,
    modelName: String?,
    category: EventCategory,
    body: String?,
  ) = RequestLogStore.addEvent(message, level, modelName, category, body)

  override fun registerCancellation(id: String, onCancel: () -> Boolean) = RequestLogStore.registerCancellation(id, onCancel)
  override fun unregisterCancellation(id: String) = RequestLogStore.unregisterCancellation(id)
  override fun cancelRequest(id: String) = RequestLogStore.cancelRequest(id)
  override fun cancelAllPending() = RequestLogStore.cancelAllPending()
  override fun clear() = RequestLogStore.clear()
  override fun setMaxEntries(max: Int) = RequestLogStore.setMaxEntries(max)
  override fun loadEntries(entries: List<RequestLogEntry>) = RequestLogStore.loadEntries(entries)
  override fun removeOlderThan(cutoffMs: Long) = RequestLogStore.removeOlderThan(cutoffMs)
  override fun trimToPercentage(percentage: Int) = RequestLogStore.trimToPercentage(percentage)
}
