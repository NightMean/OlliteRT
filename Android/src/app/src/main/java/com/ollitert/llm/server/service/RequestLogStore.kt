package com.ollitert.llm.server.service

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

/** Category for EVENT-type log entries — drives the icon shown in the Logs tab. */
enum class EventCategory { GENERAL, MODEL, SETTINGS, SERVER, PROMPT }

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
  val clientIp: String? = null,
  val level: LogLevel = LogLevel.INFO,
  val isPending: Boolean = false,
  val isThinking: Boolean = false,
  val isCompacted: Boolean = false,
  val compactionDetails: String? = null,
  val compactedPrompt: String? = null,
  val isCancelled: Boolean = false,
  /** True when the user tapped "Stop" in the Logs screen (vs client disconnect). */
  val cancelledByUser: Boolean = false,
  val partialText: String? = null,
  val eventCategory: EventCategory = EventCategory.GENERAL,
  /** Estimated input token count (~charLen/4), or exact count if extracted from LiteRT error. */
  val inputTokenEstimate: Long = 0,
  /** Model's max context window in tokens. 0 if unknown. */
  val maxContextTokens: Long = 0,
  /** True when [inputTokenEstimate] was extracted from a LiteRT error (exact count, not estimate). */
  val isExactTokenCount: Boolean = false,
)

/**
 * In-memory FIFO store for API request logs. Max [maxEntries] entries.
 * Observable via [entries] StateFlow.
 *
 * When log persistence is enabled, a [PersistenceCallback] is registered to
 * asynchronously write entries to Room. The callback receives individual entry
 * events rather than observing the full StateFlow, which avoids reacting to
 * high-frequency [partialText] streaming updates (~150ms intervals).
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

  private val idCounter = AtomicLong(0)

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
    // 0 means no limit — skip trimming
    if (max > 0) {
      _entries.update { current ->
        if (current.size > max) current.take(max) else current
      }
    }
  }

  /**
   * Maps pending log-entry IDs to callbacks that cancel the in-flight inference.
   * For streaming: the callback calls [BlockingQueueInputStream.cancel].
   * For non-streaming: the callback calls [ServerLlmModelHelper.stopResponse].
   */
  private val pendingCancellations = ConcurrentHashMap<String, () -> Unit>()

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
        // maxEntries == 0 means no limit (keep all entries)
        if (maxEntries > 0 && size > maxEntries) removeAt(lastIndex)
      }
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

  /** Update an existing entry by ID. */
  fun update(id: String, transform: (RequestLogEntry) -> RequestLogEntry) {
    // Capture old/new for the persistence callback outside the atomic update.
    var old: RequestLogEntry? = null
    var updated: RequestLogEntry? = null

    _entries.update { current ->
      val index = current.indexOfFirst { it.id == id }
      if (index < 0) return@update current
      old = current[index]
      updated = transform(old!!)
      current.toMutableList().also { it[index] = updated!! }
    }

    if (updated != null) {
      // Only notify persistence for terminal state changes (pending→complete or cancelled).
      // This skips the high-frequency partialText streaming updates (~300ms intervals).
      val isTerminal = (old!!.isPending && !updated!!.isPending) ||
        (!old!!.isCancelled && updated!!.isCancelled)
      persistenceCallback?.onEntryUpdated(updated!!, isTerminal)
    }
  }

  /** Register a cancellation callback for an in-flight request. */
  fun registerCancellation(id: String, onCancel: () -> Unit) {
    pendingCancellations[id] = onCancel
  }

  /** Remove a cancellation callback (called when inference completes normally). */
  fun unregisterCancellation(id: String) {
    pendingCancellations.remove(id)
  }

  /**
   * Cancel a pending request from the UI (user tapped Stop).
   * Sets [RequestLogEntry.cancelledByUser] and invokes the registered callback.
   */
  fun cancelRequest(id: String) {
    update(id) { it.copy(cancelledByUser = true) }
    pendingCancellations.remove(id)?.invoke()
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
   * Bulk-load entries from the database on startup.
   * Replaces the current in-memory list without triggering persistence callbacks
   * (the data is already in the DB).
   */
  fun loadEntries(entries: List<RequestLogEntry>) {
    _entries.update { entries }
  }

  /**
   * Add an internal event (model load, error, etc.) visible in the Logs tab.
   *
   * @param body optional structured JSON stored in [RequestLogEntry.requestBody].
   *   All event bodies MUST be valid JSON with a `"type"` discriminator field.
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
}
