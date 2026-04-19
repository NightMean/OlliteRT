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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUtilsTest {

  // ── matchesSearchQuery ────────────────────────────────────────────────────

  @Test
  fun blankQueryMatchesEverything() {
    assertTrue(matchesSearchQuery("any text", ""))
    assertTrue(matchesSearchQuery("any text", "   "))
  }

  @Test
  fun singleWordMatch() {
    assertTrue(matchesSearchQuery("Hello World", "hello"))
  }

  @Test
  fun singleWordNoMatch() {
    assertFalse(matchesSearchQuery("Hello World", "xyz"))
  }

  @Test
  fun multiWordAllPresent() {
    assertTrue(matchesSearchQuery("The quick brown fox", "quick fox"))
  }

  @Test
  fun multiWordOneMissing() {
    assertFalse(matchesSearchQuery("The quick brown fox", "quick cat"))
  }

  @Test
  fun caseInsensitive() {
    assertTrue(matchesSearchQuery("HELLO world", "hello WORLD"))
  }

  @Test
  fun wordsCanAppearInAnyOrder() {
    assertTrue(matchesSearchQuery("abc def ghi", "ghi abc"))
  }

  @Test
  fun extraWhitespaceInQuery() {
    assertTrue(matchesSearchQuery("hello world", "  hello   world  "))
  }

  @Test
  fun substringMatch() {
    assertTrue(matchesSearchQuery("temperature", "temp"))
  }

  @Test
  fun emptySearchableText() {
    assertFalse(matchesSearchQuery("", "query"))
  }

  @Test
  fun bothEmpty() {
    assertTrue(matchesSearchQuery("", ""))
  }

  // ── highlightSearchMatches (text output only, not styling) ────────────────

  @Test
  fun blankQueryReturnsUnstyledText() {
    val result = highlightSearchMatches("hello", "", androidx.compose.ui.graphics.Color.Yellow)
    assertEquals("hello", result.text)
  }

  @Test
  fun matchPreservesFullText() {
    val result = highlightSearchMatches("Hello World", "world", androidx.compose.ui.graphics.Color.Yellow)
    assertEquals("Hello World", result.text)
  }

  @Test
  fun noMatchPreservesFullText() {
    val result = highlightSearchMatches("Hello World", "xyz", androidx.compose.ui.graphics.Color.Yellow)
    assertEquals("Hello World", result.text)
  }

  @Test
  fun multiWordHighlight() {
    val result = highlightSearchMatches("The quick brown fox jumps", "quick fox", androidx.compose.ui.graphics.Color.Yellow)
    assertEquals("The quick brown fox jumps", result.text)
    assertTrue(result.spanStyles.isNotEmpty())
  }

  @Test
  fun overlappingRangesMerged() {
    val result = highlightSearchMatches("abcdef", "abc bcd", androidx.compose.ui.graphics.Color.Yellow)
    assertEquals("abcdef", result.text)
    assertEquals(1, result.spanStyles.size)
    assertEquals(0, result.spanStyles[0].start)
    assertEquals(4, result.spanStyles[0].end)
  }

  @Test
  fun multipleOccurrencesHighlighted() {
    val result = highlightSearchMatches("ab ab ab", "ab", androidx.compose.ui.graphics.Color.Yellow)
    assertEquals("ab ab ab", result.text)
    assertEquals(3, result.spanStyles.size)
  }
}
