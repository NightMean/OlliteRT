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

package com.ollitert.llm.server.data.model

import com.ollitert.llm.server.common.ErrorCategory

enum class LogLevel { DEBUG, INFO, WARNING, ERROR }

/** Category for EVENT-type log entries — drives the icon shown in the Logs tab. */
enum class EventCategory { GENERAL, MODEL, SETTINGS, SERVER, PROMPT, UPDATE }

/**
 * Specific error types, assigned at catch sites. Each maps to exactly one recovery suggestion.
 * Only add entries here when you can verify the exact error condition from the code.
 */
enum class ErrorKind(val category: ErrorCategory) {
  CONTEXT_OVERFLOW(ErrorCategory.INFERENCE),
  TIMEOUT(ErrorCategory.INFERENCE),
  MODEL_NOT_FOUND(ErrorCategory.MODEL_LOAD),
  MODEL_FILES_MISSING(ErrorCategory.MODEL_LOAD),
  PORT_BIND_FAILURE(ErrorCategory.NETWORK),
  MODEL_INSTANCE_NULL(ErrorCategory.INFERENCE),
  IMAGE_DECODE_FAILED(ErrorCategory.INFERENCE),
  OOM(ErrorCategory.SYSTEM),

  /** Errors from LiteRT SDK where we don't know the exact string. */
  UNKNOWN_LITERT(ErrorCategory.INFERENCE),

  /** Catch-all for unrecognized errors. */
  UNKNOWN(ErrorCategory.SYSTEM),
}

/**
 * A single API request/response pair displayed in the Logs screen and persisted in Room.
 */
data class RequestLogEntry(
  val id: String,
  val timestamp: Long = System.currentTimeMillis(),
  val method: String,
  val path: String,
  val requestBody: String? = null,
  /** Original request body size in chars before base64 compaction. 0 = no compaction applied. */
  val originalRequestBodySize: Int = 0,
  val responseBody: String? = null,
  val statusCode: Int = 200,
  val tokens: Long = 0,
  val latencyMs: Long = 0,
  val isStreaming: Boolean = false,
  val modelName: String? = null,
  val clientIp: String? = null,
  val level: LogLevel = LogLevel.INFO,
  val isPending: Boolean = false,
  val isGenerating: Boolean = false,
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
  /** Client-supplied sampler params that were ignored due to the "Ignore Client Sampler" setting. */
  val ignoredClientParams: String? = null,
  /** True when the response contains tool calls (finish_reason = "tool_calls"). */
  val hasToolCalls: Boolean = false,
  /** Classified error type, set when inference fails. Null for successful requests. */
  val errorKind: ErrorKind? = null,
  // ── Per-request performance metrics ──
  // Computed at inference completion and stored per-entry for the Logs info popup.
  /** Time to first token in ms (0 if unavailable, e.g. non-streaming without TTFB tracking). */
  val ttfbMs: Long = 0,
  /** Decode speed in tokens/sec for this request's generation phase. */
  val decodeSpeed: Double = 0.0,
  /** Prefill speed in tokens/sec (input tokens / TTFB). */
  val prefillSpeed: Double = 0.0,
  /** Inter-token latency in ms (average time between consecutive output tokens). */
  val itlMs: Double = 0.0,
)
