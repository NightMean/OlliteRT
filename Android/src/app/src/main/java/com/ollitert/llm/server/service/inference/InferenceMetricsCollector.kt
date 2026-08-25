/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.service.inference

import android.content.Context
import com.ollitert.llm.server.common.ErrorSuggestions
import com.ollitert.llm.server.data.model.ErrorKind
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.prefs.isVerboseDebugEnabled
/**
 * Handles LLM error classification, token extraction, and verbose inference metrics logging.
 */
object InferenceMetricsCollector {

  // Parses "N >= M" from LiteRT native overflow errors (N=input tokens, M=context limit)
  private val TOKEN_OVERFLOW_REGEX = Regex("(\\d+)\\s*>=\\s*(\\d+)")

  /**
   * Classify an opaque LLM error string and return the enriched message with a
   * recovery suggestion appended (if one is available for the classified error kind).
   *
   * Also returns the [ErrorKind] so callers can use it for metrics and API responses.
   */
  fun enrichLlmError(error: String, context: Context): Pair<String, ErrorKind> {
    val kind = ErrorSuggestions.classifyFromString(error)
    val suggestion = ErrorSuggestions.suggest(kind, context)
    val enriched = if (suggestion != null) "$error — $suggestion" else error
    return enriched to kind
  }

  /**
   * Extract actual token counts from LiteRT error messages.
   * LiteRT reports context overflow as "N >= M" (e.g. "6579 >= 4000").
   * Returns (actualInputTokens, maxContextTokens) or null if not a context overflow error.
   */
  fun extractActualTokenCounts(responseBody: String): Pair<Long, Long>? {
    val match = TOKEN_OVERFLOW_REGEX.find(responseBody) ?: return null
    val actual = match.groupValues[1].toLongOrNull() ?: return null
    val max = match.groupValues[2].toLongOrNull() ?: return null
    if (actual <= 0 || max <= 0) return null
    return actual to max
  }

  /**
   * Logs comprehensive inference performance metrics and JVM/native memory usage when verbose debug is enabled.
   */
  fun logVerboseInferenceDetails(
    context: Context,
    prefs: RequestPrefsSnapshot?,
    modelName: String,
    inputTokens: Int,
    outputTokens: Int,
    ttfbMs: Long,
    generationMs: Long,
    totalMs: Long,
  ) {
    if (!(prefs?.verboseDebug ?: ServerPrefs.isVerboseDebugEnabled(context))) return
    val rt = Runtime.getRuntime()
    val heapTotalMb = rt.totalMemory() / (1024.0 * 1024.0)
    val heapFreeMb = rt.freeMemory() / (1024.0 * 1024.0)
    val nativeAllocMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
    val nativeTotalMb = android.os.Debug.getNativeHeapSize() / (1024.0 * 1024.0)
    val decodeSpeed = if (outputTokens > 0 && generationMs > 0) outputTokens.toDouble() / (generationMs / 1000.0) else 0.0
    val prefillSpeed = if (inputTokens > 0 && ttfbMs > 0) inputTokens.toDouble() / (ttfbMs / 1000.0) else 0.0

    val body = buildString {
      appendLine("Timing: TTFB ${ttfbMs}ms, generation ${generationMs}ms, total ${totalMs}ms")
      appendLine("Tokens: ${inputTokens} input → ${outputTokens} output")
      appendLine("Speed: ${String.format(java.util.Locale.US, "%.1f", prefillSpeed)} t/s prefill, ${String.format(java.util.Locale.US, "%.1f", decodeSpeed)} t/s decode")
      appendLine("Heap: ${String.format(java.util.Locale.US, "%.1f", heapFreeMb)}MB free / ${String.format(java.util.Locale.US, "%.1f", heapTotalMb)}MB total")
      append("Native: ${String.format(java.util.Locale.US, "%.1f", nativeAllocMb)}MB allocated / ${String.format(java.util.Locale.US, "%.1f", nativeTotalMb)}MB total")
    }

    RequestLogStore.addEvent(
      "Inference details: ${inputTokens}→${outputTokens} tokens in ${totalMs}ms",
      level = LogLevel.DEBUG,
      modelName = modelName,
      category = EventCategory.SERVER,
      body = body,
    )
  }
}
