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

package com.ollitert.llm.server.ui.server.logs

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.ui.theme.OlliteRTSearchHighlight

internal val SearchHighlightColor = OlliteRTSearchHighlight.copy(alpha = 0.35f)

// ── Log filter model ────────────────────────────────────────────────────────

/** HTTP status code range groupings for filter chips. */
enum class StatusRange(val label: String, val range: IntRange) {
  SUCCESS("2xx", 200..299),
  CLIENT_ERROR("4xx", 400..499),
  SERVER_ERROR("5xx", 500..599);
  fun contains(code: Int) = code in range
}

/**
 * Immutable filter state for the Logs screen. Chip filters check indexed fields only
 * (instant, <5ms for 1000 entries). Text search is triggered on Enter, not per-keystroke.
 * Body search always included (runs async on [Dispatchers.Default]).
 */
data class LogFilter(
  val query: String = "",
  val methods: Set<String> = emptySet(),
  val statusRanges: Set<StatusRange> = emptySet(),
  val levels: Set<LogLevel> = emptySet(),
) {
  /** True when any filter is active — used to show the result count banner. */
  val isActive: Boolean
    get() = query.isNotEmpty() || methods.isNotEmpty() ||
      statusRanges.isNotEmpty() || levels.isNotEmpty()
}

/** Check chip-only filters (method, status range, log level). */
internal fun RequestLogEntry.matchesChipFilters(filter: LogFilter): Boolean {
  if (filter.methods.isNotEmpty() && method !in filter.methods) return false
  // Status range filters (2xx/4xx/5xx) only apply to HTTP request entries.
  // EVENT entries have no status code — they should be excluded when status
  // filters are active unless the EVENT method is also explicitly selected.
  if (filter.statusRanges.isNotEmpty()) {
    if (method == "EVENT") {
      if ("EVENT" !in filter.methods) return false
    } else {
      if (filter.statusRanges.none { it.contains(statusCode) }) return false
    }
  }
  if (filter.levels.isNotEmpty() && level !in filter.levels) return false
  return true
}

/**
 * Check text query against all searchable fields including request/response bodies.
 * Always searches bodies — runs on [Dispatchers.Default] to avoid main-thread jank.
 */
internal fun RequestLogEntry.matchesTextQuery(query: String): Boolean {
  if (query.isEmpty()) return true
  if (path.contains(query, ignoreCase = true)) return true
  if (modelName?.contains(query, ignoreCase = true) == true) return true
  if (clientIp?.contains(query, ignoreCase = true) == true) return true
  if (method.contains(query, ignoreCase = true)) return true
  if (requestBody?.contains(query, ignoreCase = true) == true) return true
  if (responseBody?.contains(query, ignoreCase = true) == true) return true
  return false
}

/** Combined filter: chip filters + text query. Pending entries hidden during text search. */
internal fun RequestLogEntry.matchesFilter(filter: LogFilter): Boolean {
  // Pending entries hidden during active text search — incomplete content can't be
  // reliably matched. They reappear instantly when the user clears the search.
  if (isPending && filter.query.isNotEmpty()) return false
  if (!matchesChipFilters(filter)) return false
  if (!matchesTextQuery(filter.query)) return false
  return true
}

// ── Search highlighting ─────────────────────────────────────────────────────

/**
 * Builds an [AnnotatedString] with [SearchHighlightColor] background spans on all
 * case-insensitive matches of [query] within [text]. If [baseColor] is provided,
 * it's applied to the entire string as the default text color.
 */
internal fun buildHighlightedString(
  text: String,
  query: String,
  baseColor: Color? = null,
): AnnotatedString = buildAnnotatedString {
  if (baseColor != null) pushStyle(SpanStyle(color = baseColor))
  var start = 0
  val lowerText = text.lowercase()
  val lowerQuery = query.lowercase()
  while (start < text.length) {
    val idx = lowerText.indexOf(lowerQuery, start)
    if (idx < 0) { append(text.substring(start)); break }
    append(text.substring(start, idx))
    withStyle(SpanStyle(background = SearchHighlightColor)) {
      append(text.substring(idx, idx + query.length))
    }
    start = idx + query.length
  }
  if (baseColor != null) pop()
}

/**
 * Overlays search highlight [SpanStyle]s on top of an existing [AnnotatedString]
 * (e.g. one already styled with JSON syntax colors or event highlighting).
 * Does not disturb existing spans — only adds background highlights.
 */
internal fun overlaySearchHighlights(
  base: AnnotatedString,
  query: String,
): AnnotatedString {
  val text = base.text
  val lowerText = text.lowercase()
  val lowerQuery = query.lowercase()
  val matches = mutableListOf<IntRange>()
  var searchFrom = 0
  while (searchFrom < text.length) {
    val idx = lowerText.indexOf(lowerQuery, searchFrom)
    if (idx < 0) break
    matches.add(idx until (idx + query.length))
    searchFrom = idx + query.length
  }
  if (matches.isEmpty()) return base
  return buildAnnotatedString {
    append(base) // copies text + all existing SpanStyles
    for (range in matches) {
      addStyle(SpanStyle(background = SearchHighlightColor), range.first, range.last + 1)
    }
  }
}
