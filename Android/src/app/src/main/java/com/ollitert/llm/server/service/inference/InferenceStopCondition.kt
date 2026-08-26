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

import com.ollitert.llm.server.service.http.ResponseFormat

/**
 * Handles stop sequences matching and response format prompt injection.
 */
object InferenceStopCondition {

  /**
   * Truncates model output at the first occurrence of any stop sequence.
   * Returns (truncated text, was truncation applied, the stop string that matched
   * — null when nothing matched). The matched string is needed by the Anthropic
   * /v1/messages response, which echoes it back in the `stop_sequence` field.
   */
  fun applyStopSequences(text: String, stopSequences: List<String>?): Triple<String, Boolean, String?> {
    if (stopSequences.isNullOrEmpty()) return Triple(text, false, null)
    var earliest = text.length
    var matched: String? = null
    for (stop in stopSequences) {
      val idx = text.indexOf(stop)
      if (idx in 0 until earliest) {
        earliest = idx
        matched = stop
      }
    }
    return if (earliest < text.length) Triple(text.substring(0, earliest), true, matched)
    else Triple(text, false, null)
  }

  /** A stop-sequence match found by [IncrementalStopMatcher]. */
  data class Match(val index: Int, val sequence: String)

  /**
   * Incremental earliest-match scanner for streaming token feeds.
   *
   * The naive approach — `fullText.toString()` + full scan on every token — is
   * O(total length) per token: for a 4k-token output this copies hundreds of MB
   * cumulatively. Instead, track how far the buffer has already been proven free
   * of matches and only scan a small window around each append. Any match
   * starting before that window was fully contained in the previous buffer and
   * would have been reported then; a match straddling the boundary can start at
   * most `maxStopLength - 1` characters back.
   *
   * Semantics match [applyStopSequences]: across multiple stops, the earliest
   * index wins (ties resolved by list order).
   */
  class IncrementalStopMatcher(stopSequences: List<String>) {
    private val stops = stopSequences.filter { it.isNotEmpty() }
    private val maxStopLength = stops.maxOfOrNull { it.length } ?: 0

    /** Exclusive end of the region already scanned with no match found. */
    private var scannedUpTo = 0

    /**
     * Scan [text] for newly completed stop-sequence matches.
     * Returns null when no new complete match exists yet; the caller should
     * keep appending and call again with the grown buffer. Once non-null, the
     * matcher is spent — truncate at [Match.index] and stop feeding.
     */
    fun findNewMatch(text: CharSequence): Match? {
      if (stops.isEmpty() || maxStopLength == 0) return null
      val scanFrom = (scannedUpTo - (maxStopLength - 1)).coerceAtLeast(0)
      var earliest = Int.MAX_VALUE
      var matched: String? = null
      for (stop in stops) {
        val idx = text.indexOf(stop, scanFrom)
        if (idx in 0 until earliest) {
          earliest = idx
          matched = stop
        }
      }
      return if (matched != null) {
        Match(earliest, matched)
      } else {
        scannedUpTo = text.length
        null
      }
    }
  }

  /**
   * Injects a JSON mode instruction into the prompt when response_format is requested.
   *
   * `json_schema` requests carrying an actual schema are enforced by native
   * constrained decoding in the engine ([ResponseFormat.constrainedJsonSchema]) —
   * no prompt hint there, it would only fight the grammar constraint.
   */
  fun applyResponseFormat(prompt: String, responseFormat: ResponseFormat?): String {
    if (responseFormat == null || responseFormat.type == "text") return prompt
    if (responseFormat.constrainedJsonSchema() != null) return prompt
    val instruction = when (responseFormat.type) {
      "json_object" -> "Respond with valid JSON only. Do not include any text, explanation, or markdown outside the JSON object.\n\n"
      "json_schema" -> "Respond with valid JSON only. Output only the JSON object, nothing else.\n\n"
      else -> return prompt
    }
    return instruction + prompt
  }
}
