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

package com.ollitert.llm.server.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

private val WHITESPACE_REGEX = "\\s+".toRegex()

/**
 * Returns true if every word in [query] appears somewhere in [searchableText].
 * Both inputs are compared case-insensitively.
 */
fun matchesSearchQuery(searchableText: String, query: String): Boolean {
  if (query.isBlank()) return true
  val lowerSearchable = searchableText.lowercase()
  return query.trim().lowercase().split(WHITESPACE_REGEX).all { word ->
    lowerSearchable.contains(word)
  }
}

/**
 * Highlights all occurrences of search query words in the given text.
 * Returns an [AnnotatedString] with matched regions styled in [highlightColor] + bold.
 */
fun highlightSearchMatches(
  text: String,
  query: String,
  highlightColor: Color,
): AnnotatedString {
  if (query.isBlank()) return AnnotatedString(text)
  val words = query.trim().lowercase().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
  if (words.isEmpty()) return AnnotatedString(text)
  val textLower = text.lowercase()
  val ranges = mutableListOf<IntRange>()
  for (word in words) {
    var start = 0
    while (true) {
      val idx = textLower.indexOf(word, start)
      if (idx < 0) break
      ranges.add(idx until idx + word.length)
      start = idx + 1
    }
  }
  if (ranges.isEmpty()) return AnnotatedString(text)
  val merged = ranges.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
    if (acc.isEmpty() || acc.last().last < r.first - 1) acc.add(r)
    else acc[acc.lastIndex] = acc.last().first..maxOf(acc.last().last, r.last)
    acc
  }
  return buildAnnotatedString {
    append(text)
    for (range in merged) {
      addStyle(
        SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold),
        start = range.first,
        end = range.last + 1,
      )
    }
  }
}
