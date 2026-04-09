package com.ollitert.llm.server.service

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { INFO, WARNING, ERROR }

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
 * In-memory FIFO store for API request logs. Max [MAX_ENTRIES] entries.
 * Clears on app restart. Observable via [entries] StateFlow.
 */
object RequestLogStore {

  private const val MAX_ENTRIES = 100
  private val idCounter = AtomicLong(0)

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

  /** Update an existing entry by ID. */
  fun update(id: String, transform: (RequestLogEntry) -> RequestLogEntry) {
    val current = _entries.value.toMutableList()
    val index = current.indexOfFirst { it.id == id }
    if (index >= 0) {
      current[index] = transform(current[index])
      _entries.value = current
    }
  }

  fun clear() {
    _entries.value = emptyList()
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
