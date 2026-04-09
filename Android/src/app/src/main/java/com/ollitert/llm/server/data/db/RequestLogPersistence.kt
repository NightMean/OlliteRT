package com.ollitert.llm.server.data.db

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.service.RequestLogStore
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
  private val moshi: Moshi,
  @param:ApplicationContext private val context: Context,
) : RequestLogStore.PersistenceCallback {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val isEnabled: Boolean get() = LlmHttpPrefs.isLogPersistenceEnabled(context)

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
    val max = if (isEnabled) LlmHttpPrefs.getLogMaxEntries(context) else DEFAULT_IN_MEMORY_CAP
    RequestLogStore.setMaxEntries(max)
  }

  // --- PersistenceCallback implementation ---

  override fun onEntryAdded(entry: RequestLogEntry) {
    if (!isEnabled) return
    scope.launch {
      try {
        dao.upsert(RequestLogEntity.fromEntry(entry, moshi))
      } catch (e: Exception) {
        Log.e(TAG, "Failed to persist new log entry ${entry.id}", e)
      }
    }
  }

  override fun onEntryUpdated(entry: RequestLogEntry, isTerminal: Boolean) {
    // Only persist terminal state changes (pending→complete or cancelled).
    // Streaming partialText updates fire every ~150ms and are intentionally skipped.
    if (!isEnabled || !isTerminal) return
    scope.launch {
      try {
        dao.upsert(RequestLogEntity.fromEntry(entry, moshi))
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
        val entities = entries.map { RequestLogEntity.fromEntry(it, moshi) }
        dao.upsertAll(entities)
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
      } catch (e: Exception) {
        Log.e(TAG, "Failed to clear persisted logs", e)
      }
    }
  }

  // --- Internal ---

  private suspend fun loadFromDb() {
    val maxEntries = LlmHttpPrefs.getLogMaxEntries(context)
    val entities = dao.getRecent(maxEntries)
    if (entities.isNotEmpty()) {
      val entries = entities.map { it.toEntry(moshi) }
      RequestLogStore.loadEntries(entries)
    }
  }

  /** Run age-based and count-based pruning on both the database and in-memory entries. */
  private suspend fun prune() {
    try {
      // Age-based pruning — 0 means disabled (keep indefinitely).
      val retentionMinutes = LlmHttpPrefs.getLogAutoDeleteMinutes(context)
      if (retentionMinutes > 0) {
        val cutoffMs = System.currentTimeMillis() - (retentionMinutes * 60_000L)
        dao.deleteOlderThan(cutoffMs)
        RequestLogStore.removeOlderThan(cutoffMs)
      }

      // Count-based pruning — 0 means no limit (keep all).
      val maxCount = LlmHttpPrefs.getLogMaxEntries(context)
      if (maxCount > 0) {
        dao.pruneToCount(maxCount)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Log pruning failed", e)
    }
  }

  /**
   * Schedule periodic pruning based on the user's auto-delete setting.
   * Interval = configured retention period, clamped between 1 minute and 6 hours.
   * If auto-delete is disabled (0), falls back to 6 hours for count-based pruning only.
   */
  private fun schedulePruning() {
    scope.launch {
      while (true) {
        val retentionMinutes = LlmHttpPrefs.getLogAutoDeleteMinutes(context)
        val intervalMs = if (retentionMinutes > 0) {
          (retentionMinutes * 60_000L).coerceIn(MIN_PRUNE_INTERVAL_MS, MAX_PRUNE_INTERVAL_MS)
        } else {
          MAX_PRUNE_INTERVAL_MS // auto-delete disabled — still prune by count periodically
        }
        delay(intervalMs)
        if (isEnabled) prune()
      }
    }
  }

  companion object {
    private const val TAG = "LogPersistence"
    private const val DEFAULT_IN_MEMORY_CAP = 100
    private const val MIN_PRUNE_INTERVAL_MS = 60_000L        // 1 minute
    private const val MAX_PRUNE_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
  }
}
