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
import com.ollitert.llm.server.data.prefs.HARD_MAX_IN_MEMORY_ENTRIES
import com.ollitert.llm.server.data.prefs.HARD_MAX_RETAINED_LOG_BODY_CHARS

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory FIFO store for API request logs. Max [maxEntries] entries.
 * Observable via [entries] StateFlow.
 *
 * When log persistence is enabled, a [PersistenceCallback] is registered to
 * asynchronously write entries to Room. The callback receives individual entry
 * events rather than observing the full StateFlow, which avoids reacting to
 * high-frequency [partialText] streaming updates (~300ms intervals).
 */
object RequestLogStore {

  /** Default cap when persistence is disabled. */
  private const val DEFAULT_MAX_ENTRIES = 100

  /**
   * Maximum in-memory entries. When persistence is OFF, defaults to [DEFAULT_MAX_ENTRIES].
   * When ON, set to the user's configured max via [setMaxEntries].
   */
  @Volatile var maxEntries: Int = DEFAULT_MAX_ENTRIES
    private set

  /** Retained-body budget in chars; mutable for tests via [resetForTesting]. */
  @Volatile internal var bodyBudgetChars: Long = HARD_MAX_RETAINED_LOG_BODY_CHARS

  /**
   * Actual in-memory cap accounting for [HARD_MAX_IN_MEMORY_ENTRIES] ceiling.
   * When [maxEntries] is 0 ("no limit"), defaults to the hard ceiling.
   * Non-zero values are used as-is (the UI caps input at [HARD_MAX_IN_MEMORY_ENTRIES]).
   */
  val effectiveMaxEntries: Int
    get() = if (maxEntries == 0) HARD_MAX_IN_MEMORY_ENTRIES else maxEntries

  private val idCounter = AtomicLong(0)

  /**
   * Header names whose values must never be persisted in plain text — they carry
   * authentication material. Compared case-insensitively at the call site.
   */
  private val REDACTED_HEADERS = setOf(
    "authorization",
    "x-api-key",
    "cookie",
    "proxy-authorization",
  )

  /**
   * Redact authentication headers before they are stored or rendered. Returns a copy
   * of the input map with sensitive values replaced by `<redacted>`. Header names
   * are matched case-insensitively (HTTP header names are case-insensitive per RFC 7230).
   *
   * Today no header dump is captured, but the Anthropic endpoint introduces
   * `x-api-key` and `anthropic-version` — any future code path that records request
   * headers should pipe them through this helper first.
   */
  fun redactSensitiveHeaders(headers: Map<String, String>): Map<String, String> =
    headers.mapValues { (name, value) ->
      if (REDACTED_HEADERS.contains(name.lowercase())) "<redacted>" else value
    }

  /** Callback for the persistence layer to observe add/update/clear events. */
  interface PersistenceCallback {
    /** A new entry was added (create). */
    fun onEntryAdded(entry: RequestLogEntry)

    /**
     * An existing entry was updated.
     * [isTerminal] is true when the entry transitions to a final state
     * (isPending→false or isCancelled→true) — the persistence layer should
     * only write to the DB on terminal updates, skipping streaming partialText changes.
     */
    fun onEntryUpdated(entry: RequestLogEntry, isTerminal: Boolean)

    /** All entries were cleared. */
    fun onEntriesCleared()
  }

  @Volatile private var persistenceCallback: PersistenceCallback? = null

  /** Register a persistence callback (called by [RequestLogPersistence] on app start). */
  fun setPersistenceCallback(callback: PersistenceCallback?) {
    persistenceCallback = callback
  }

  /**
   * Update the in-memory entry cap. Called when persistence settings change.
   * If the new cap is lower than the current count, excess entries are trimmed immediately.
   */
  fun setMaxEntries(max: Int) {
    maxEntries = max
    val cap = effectiveMaxEntries
    _entries.update { current ->
      if (current.size > cap) current.take(cap) else current
    }
  }

  /**
   * Maps pending log-entry IDs to callbacks that cancel the in-flight inference.
   * Both streaming and blocking callbacks route through the request-scoped cancellation
   * bridge and report whether that exact request still accepted cancellation.
   */
  private val pendingCancellations = ConcurrentHashMap<String, () -> Boolean>()

  private val _entries = MutableStateFlow<List<RequestLogEntry>>(emptyList())
  val entries: StateFlow<List<RequestLogEntry>> = _entries.asStateFlow()

  /**
   * Lightweight channel for streaming partial text updates during inference.
   * Emits (entryId, partialText) pairs on every debounced update (~300ms).
   *
   * **Why this exists:** Without it, every partialText update replaces the entire
   * [_entries] list, which forces the LazyColumn to diff and recompose ALL visible
   * items ~3-6 times per second during generation — the main cause of scroll jank.
   * The Logs UI collects this flow separately and only recomposes the single
   * pending card, leaving the rest of the list untouched.
   */
  private val _pendingPartialText = MutableStateFlow<Pair<String, String?>>("" to null)
  val pendingPartialText: StateFlow<Pair<String, String?>> = _pendingPartialText.asStateFlow()

  fun add(entry: RequestLogEntry) {
    _entries.update { current ->
      buildList {
        add(entry) // newest first
        addAll(current)
        val cap = effectiveMaxEntries
        if (size > cap) removeAt(lastIndex)
      }.enforceBodyBudget(bodyBudgetChars)
    }
    persistenceCallback?.onEntryAdded(entry)
  }

  /**
   * Update only the partial text for a pending entry during streaming.
   * Emits via [pendingPartialText] flow without touching the main [_entries] list,
   * avoiding full LazyColumn recomposition on every token batch.
   */
  fun updatePartialText(id: String, text: String) {
    _pendingPartialText.value = id to text
  }

  /**
   * Update an existing entry by ID.
   *
   * Uses O(n) linear scan + full list copy. Acceptable at current scale (max ~500 entries,
   * capped by [HARD_MAX_IN_MEMORY_ENTRIES]). If scale grows significantly, consider an
   * indexed map (id → index) alongside the list for O(1) lookup.
   */
  fun update(id: String, transform: (RequestLogEntry) -> RequestLogEntry) {
    // Capture old/new for the persistence callback outside the atomic update.
    var oldEntry: RequestLogEntry? = null
    var newEntry: RequestLogEntry? = null

    _entries.update { current ->
      val index = current.indexOfFirst { it.id == id }
      if (index < 0) return@update current
      val found = current[index]
      val transformed = transform(found)
      oldEntry = found
      newEntry = transformed
      current.toMutableList().also { it[index] = transformed }.enforceBodyBudget(bodyBudgetChars)
    }

    val old = oldEntry ?: return
    val updated = newEntry ?: return
    // Only notify persistence for terminal state changes (pending→complete or cancelled).
    // This skips the high-frequency partialText streaming updates (~300ms intervals).
    val isTerminal = (old.isPending && !updated.isPending) ||
      (!old.isCancelled && updated.isCancelled)
    persistenceCallback?.onEntryUpdated(updated, isTerminal)
  }

  /** Register a cancellation callback for an in-flight request. */
  fun registerCancellation(id: String, onCancel: () -> Boolean) {
    pendingCancellations[id] = onCancel
  }

  /** Remove a cancellation callback (called when inference completes normally). */
  fun unregisterCancellation(id: String) {
    pendingCancellations.remove(id)
  }

  /**
   * Cancel a pending request from the UI (user tapped Stop).
   * Uses [ConcurrentHashMap.remove] as the single source of truth — if the callback was
   * already removed (inference completed normally), we don't mark the entry as cancelled.
   * This prevents a race where the entry shows "cancelled" but the full response was sent.
   *
   * Cosmetic race: between accepting cancellation and setting cancelledByUser=true,
   * a few more tokens may be generated and streamed. The UI may briefly show "Cancelled" while
   * the response body still grows. This is harmless — the final entry state is consistent once
   * the callback completes and isPending is set to false by the inference teardown path.
   */
  fun cancelRequest(id: String) {
    val callback = pendingCancellations.remove(id)
    if (callback?.invoke() == true) {
      update(id) { it.copy(cancelledByUser = true) }
    }
  }

  /**
   * Marks all in-flight (isPending=true) entries as cancelled and invokes their
   * inference callbacks. Called when the server stops or restarts so that pending
   * "Generating…" cards do not stay stuck forever if the service is killed mid-inference.
   *
   * Unlike [cancelRequest] (user-initiated), this sets [RequestLogEntry.isCancelled] without
   * [RequestLogEntry.cancelledByUser], so the card renders as server-cancelled rather than
   * user-cancelled.
   */
  fun cancelAllPending() {
    // Invoke all registered inference callbacks first (signals the running inference to stop).
    val callbacks = pendingCancellations.values.toList()
    pendingCancellations.clear()
    callbacks.forEach { it.invoke() }

    // Finalize all still-pending entries in the list and persist the terminal state —
    // without the callback, the DB keeps isPending=true rows forever (the startup
    // clamp only patches them cosmetically in memory on next launch).
    val cancelled = mutableListOf<RequestLogEntry>()
    _entries.update { current ->
      current.map { entry ->
        if (entry.isPending) {
          val updated = entry.copy(isPending = false, isCancelled = true, statusCode = 499)
          cancelled += updated
          updated
        } else entry
      }
    }
    cancelled.forEach { persistenceCallback?.onEntryUpdated(it, isTerminal = true) }
  }

  fun clear() {
    _entries.update { emptyList() }
    pendingCancellations.clear()
    persistenceCallback?.onEntriesCleared()
  }

  /**
   * Remove in-memory entries older than [cutoffMs] (epoch millis).
   * Does not trigger persistence callbacks — the caller is responsible for
   * pruning the database separately.
   */
  fun removeOlderThan(cutoffMs: Long) {
    _entries.update { current -> current.filter { it.timestamp >= cutoffMs } }
  }

  /**
   * Shed entries under memory pressure. Keeps the newest [percentage]% of entries.
   * Called from [ServerService.onTrimMemory] when the system is critically low on memory.
   */
  fun trimToPercentage(percentage: Int) {
    _entries.update { current ->
      if (current.isEmpty()) return@update current
      val keep = (current.size * percentage / 100).coerceAtLeast(1)
      current.take(keep)
    }
  }

  /**
   * Bulk-load entries from the database on startup.
   * Merges the persisted rows into the current in-memory list without triggering
   * persistence callbacks (the data is already in the DB).
   *
   * **Why merge instead of replace:** the load runs asynchronously after service
   * start, and events/requests recorded in between ("Server starting", autostart
   * model loads) would be silently wiped by a wholesale replace.
   *
   * Conflict rule: on an id present in both lists the in-memory version wins — it
   * is always at least as fresh as any DB snapshot, because memory is updated
   * synchronously while persistence is queued behind [RequestLogDatabaseWriter].
   * This also protects a request that went live mid-load from being clobbered by
   * its own stale pending row.
   *
   * Any loaded entry with [RequestLogEntry.isPending] = true is stale — the inference that
   * was generating died with the old process. These are clamped to cancelled state
   * so the card shows "Cancelled" instead of staying stuck on "Generating..." forever.
   */
  fun loadEntries(entries: List<RequestLogEntry>) {
    val clamped = entries.map { entry ->
      if (entry.isPending) entry.copy(isPending = false, isCancelled = true, statusCode = 499) else entry
    }
    _entries.update { current ->
      val currentIds = current.mapTo(HashSet()) { it.id }
      val persistedOnly = clamped.filterNot { it.id in currentIds }
      (persistedOnly + current)
        .sortedByDescending { it.timestamp }
        .take(effectiveMaxEntries)
        .enforceBodyBudget(bodyBudgetChars)
    }
  }

  /**
   * Add an internal event (model load, error, etc.) visible in the Logs tab.
   *
   * @param body optional text stored in [RequestLogEntry.requestBody].
   *   May be structured JSON (with a `"type"` discriminator for typed schemas below)
   *   or plain text (config dumps, change summaries, stack traces).
   *   Exported under `"data"` in the log JSON output.
   *
   *   Schemas by type:
   *
   *   **inference_settings** — parameter changes from the Inference Settings sheet.
   *   ```json
   *   {
   *     "type": "inference_settings",
   *     "changes": [{"param": "TopK", "old": "14", "new": "15"}, ...],
   *     "prompt_diffs": {                           // optional
   *       "system_prompt": {"old": "...", "new": "..."},
   *       "chat_template": {"old": "...", "new": "..."}
   *     },
   *     "status": "reloading model"                 // optional
   *   }
   *   ```
   *
   *   **prompt_active** — system prompt or chat template active on server start.
   *   ```json
   *   {
   *     "type": "prompt_active",
   *     "prompt_type": "system_prompt" | "chat_template",
   *     "text": "full prompt text..."
   *   }
   *   ```
   */
  fun addEvent(
    message: String,
    level: LogLevel = LogLevel.INFO,
    modelName: String? = null,
    category: EventCategory = EventCategory.GENERAL,
    body: String? = null,
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

  /**
   * Reset ALL mutable state for test isolation.
   * Clears entries, callbacks, cancellations, counters, and restores defaults.
   */
  fun resetForTesting() {
    _entries.value = emptyList()
    _pendingPartialText.value = "" to null
    pendingCancellations.clear()
    persistenceCallback = null
    maxEntries = DEFAULT_MAX_ENTRIES
    bodyBudgetChars = HARD_MAX_RETAINED_LOG_BODY_CHARS
    idCounter.set(0)
  }
}


/** Memory cost proxy for one retained entry: its stored body payloads. */
private fun RequestLogEntry.bodyChars(): Long =
  (requestBody?.length ?: 0).toLong() + (responseBody?.length ?: 0).toLong()

/**
 * Shed oldest entries until the summed body payload fits
 * [HARD_MAX_RETAINED_LOG_BODY_CHARS]. The newest entry is always kept even if
 * it alone exceeds the budget. Pure function of the input list.
 */
private fun List<RequestLogEntry>.enforceBodyBudget(budgetChars: Long): List<RequestLogEntry> {
  val total = sumOf { it.bodyChars() }
  val excess = total - budgetChars
  if (excess <= 0) return this
  var acc = 0L
  var cut = size
  while (cut > 0 && acc < excess) {
    cut--
    acc += this[cut].bodyChars()
  }
  // Always retain the newest entry.
  if (cut == 0) return listOf(first())
  return subList(0, cut).toList()
}
