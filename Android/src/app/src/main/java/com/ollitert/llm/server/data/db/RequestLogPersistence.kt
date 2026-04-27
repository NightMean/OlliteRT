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

package com.ollitert.llm.server.data.db

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.data.DEFAULT_IN_MEMORY_LOG_CAP
import com.ollitert.llm.server.data.HARD_MAX_IN_MEMORY_ENTRIES
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.service.RequestLogStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Write-behind persistence bridge between [RequestLogStore] (in-memory) and Room.
 *
 * Registers as a [RequestLogStore.PersistenceCallback] and asynchronously writes
 * entries to the database on [Dispatchers.IO]. Only terminal state changes are persisted
 * (create + complete/cancel) — streaming partialText updates are skipped, resulting in
 * exactly 2 DB writes per request.
 *
 * When persistence is disabled (default), no DB operations occur. The callback is still
 * registered so that enabling persistence mid-session works immediately.
 */
@Singleton
class RequestLogPersistence @Inject constructor(
  private val dao: RequestLogDao,
  @param:ApplicationContext private val context: Context,
) : RequestLogStore.PersistenceCallback {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var pruningJob: Job? = null
  private val isEnabled: Boolean get() = ServerPrefs.isLogPersistenceEnabled(context)

  /**
   * Initialize the persistence layer. Called once from [Application.onCreate].
   * Registers the callback, syncs max entries, loads from DB, and schedules pruning.
   */
  fun initialize() {
    RequestLogStore.setPersistenceCallback(this)
    updateMaxEntries()

    if (isEnabled) {
      scope.launch {
        try {
          // Prune first so expired/excess entries are removed before loading into memory.
          prune()
          loadFromDb()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Failed to load persisted logs", e)
        }
      }
      schedulePruning()
    }
  }

  /**
   * Sync the in-memory entry cap with the persistence setting.
   * When persistence is OFF → 100 (original default). When ON → configured max.
   */
  fun updateMaxEntries() {
    val max = if (isEnabled) ServerPrefs.getLogMaxEntries(context) else DEFAULT_IN_MEMORY_CAP
    RequestLogStore.setMaxEntries(max)
  }

  // --- PersistenceCallback implementation ---

  override fun onEntryAdded(entry: RequestLogEntry) {
    if (!isEnabled) return
    scope.launch {
      try {
        dao.upsert(RequestLogEntity.fromEntry(entry))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist new log entry ${entry.id}", e)
      }
    }
  }

  override fun onEntryUpdated(entry: RequestLogEntry, isTerminal: Boolean) {
    // Only persist terminal state changes (pending→complete or cancelled).
    // Streaming partialText updates fire every ~300ms and are intentionally skipped.
    if (!isEnabled || !isTerminal) return
    scope.launch {
      try {
        dao.upsert(RequestLogEntity.fromEntry(entry))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist log entry update ${entry.id}", e)
      }
    }
  }

  override fun onEntriesCleared() {
    if (!isEnabled) return
    scope.launch {
      try {
        dao.deleteAll()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to clear persisted logs", e)
      }
    }
  }

  // --- Public operations for Settings UI ---

  /**
   * Persist all current in-memory entries to the DB.
   * Called when the user enables persistence for the first time —
   * syncs the existing session's logs so they survive the next restart.
   */
  fun persistCurrentEntries() {
    scope.launch {
      try {
        val entries = RequestLogStore.entries.value
        val entities = entries.map { RequestLogEntity.fromEntry(it) }
        dao.upsertAll(entities)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to bulk-persist current entries", e)
      }
    }
  }

  /** Explicitly wipe the database (from "Clear Persisted Logs" button in Settings). */
  fun clearPersistedLogs() {
    scope.launch {
      try {
        dao.deleteAll()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to clear persisted logs", e)
      }
    }
  }

  // --- Internal ---

  private suspend fun loadFromDb() {
    val maxEntries = ServerPrefs.getLogMaxEntries(context)
    val dbLimit = if (maxEntries == 0) HARD_MAX_IN_MEMORY_ENTRIES else maxEntries
    val entities = dao.getRecent(dbLimit)
    if (entities.isNotEmpty()) {
      val entries = entities.map { it.toEntry() }
      RequestLogStore.loadEntries(entries)
    }
  }

  /** Run age-based and count-based pruning on both the database and in-memory entries. */
  private suspend fun prune() {
    try {
      // Age-based pruning — 0 means disabled (keep indefinitely).
      val retentionMinutes = ServerPrefs.getLogAutoDeleteMinutes(context)
      if (retentionMinutes > 0) {
        val cutoffMs = System.currentTimeMillis() - (retentionMinutes * 60_000L)
        dao.deleteOlderThan(cutoffMs)
        RequestLogStore.removeOlderThan(cutoffMs)
      }

      // Count-based pruning — 0 means no limit (keep all).
      val maxCount = ServerPrefs.getLogMaxEntries(context)
      if (maxCount > 0) {
        dao.pruneToCount(maxCount)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Log pruning failed", e)
    }
  }

  /**
   * Schedule periodic pruning based on the user's auto-delete setting.
   * Interval = configured retention period, clamped between 1 minute and 6 hours.
   * If auto-delete is disabled (0), falls back to 6 hours for count-based pruning only.
   */
  internal fun schedulePruning() {
    pruningJob?.cancel()
    if (!isEnabled) return
    pruningJob = scope.launch {
      while (isActive) {
        val retentionMinutes = ServerPrefs.getLogAutoDeleteMinutes(context)
        val intervalMs = if (retentionMinutes > 0) {
          (retentionMinutes * 60_000L).coerceIn(
            com.ollitert.llm.server.data.MIN_PRUNE_INTERVAL_MS,
            com.ollitert.llm.server.data.MAX_PRUNE_INTERVAL_MS
          )
        } else {
          com.ollitert.llm.server.data.MAX_PRUNE_INTERVAL_MS
        }
        delay(intervalMs)
        prune()
      }
    }
  }

  fun shutdown() {
    pruningJob?.cancel()
    // Do NOT cancel the scope — pending DB writes (e.g., "Server stopped" event,
    // clear-on-stop onEntriesCleared()) are launched on this scope earlier in onDestroy().
    // The scope will be GC'd when the process exits.
  }

  companion object {
    private const val TAG = "OlliteRT.LogPersist"
    private const val DEFAULT_IN_MEMORY_CAP = DEFAULT_IN_MEMORY_LOG_CAP
  }
}
