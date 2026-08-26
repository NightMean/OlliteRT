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

package com.ollitert.llm.server.service.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the incremental stop-sequence matcher used by the streaming loop:
 * feeding tokens one at a time must produce exactly the same truncation point
 * as the batch [InferenceStopCondition.applyStopSequences], including matches
 * that straddle token boundaries.
 */
class IncrementalStopMatcherTest {

  private fun feedAll(matcher: InferenceStopCondition.IncrementalStopMatcher, chunks: List<String>): StringBuilder {
    val sb = StringBuilder()
    for (chunk in chunks) {
      sb.append(chunk)
      val match = matcher.findNewMatch(sb)
      if (match != null) {
        val truncated = sb.substring(0, match.index)
        sb.setLength(0)
        sb.append(truncated)
        return sb
      }
    }
    return sb
  }

  @Test
  fun noStopsNeverMatches() {
    val matcher = InferenceStopCondition.IncrementalStopMatcher(emptyList())
    assertNull(matcher.findNewMatch(StringBuilder("hello END world")))
  }

  @Test
  fun singleTokenExactMatchTruncates() {
    val sb = feedAll(
      InferenceStopCondition.IncrementalStopMatcher(listOf("END")),
      listOf("hello ", "END", " world"),
    )
    assertEquals("hello ", sb.toString())
  }

  @Test
  fun matchStraddlingChunkBoundaryIsFound() {
    val sb = feedAll(
      InferenceStopCondition.IncrementalStopMatcher(listOf("STOP")),
      listOf("abc", "ST", "OP", "def"),
    )
    assertEquals("abc", sb.toString())
  }

  @Test
  fun partialPrefixThatNeverCompletesDoesNotMatch() {
    val sb = feedAll(
      InferenceStopCondition.IncrementalStopMatcher(listOf("END")),
      listOf("en", "de", "n"),
    )
    assertEquals("enden", sb.toString())
  }

  @Test
  fun earliestMatchWinsAcrossChunks() {
    val sb = feedAll(
      InferenceStopCondition.IncrementalStopMatcher(listOf("zzz", "END")),
      listOf("a", "E", "ND", "zzz", "b"),
    )
    assertEquals("a", sb.toString())
  }

  @Test
  fun repeatedNearMissesDoNotBreakBoundaryTracking() {
    val matcher = InferenceStopCondition.IncrementalStopMatcher(listOf("###"))
    val sb = StringBuilder()
    // Feed many partial '##' runs that never complete (separated by 'a'),
    // then finally complete a full '###'.
    for (i in 0 until 50) {
      sb.append("##")
      assertNull(matcher.findNewMatch(sb))
      sb.append("a")
      assertNull(matcher.findNewMatch(sb))
    }
    assertEquals(150, sb.length)
    sb.append("###")
    val match = matcher.findNewMatch(sb)
    assertEquals(150, match?.index)
    assertEquals("###", match?.sequence)
  }

  @Test
  fun longStopShorterTokensStillMatch() {
    val sb = feedAll(
      InferenceStopCondition.IncrementalStopMatcher(listOf("<|end_of_turn|>")),
      listOf("<|", "end", "_of", "_tu", "rn", "|>"),
    )
    assertEquals("", sb.toString())
  }

  @Test
  fun batchAndIncrementalAgreeOnSameInput() {
    val text = "The quick brown fox jumps END over the lazy dog"
    val stops = listOf("lazy", "END", "fox")
    val batch = InferenceStopCondition.applyStopSequences(text, stops)
    val matcher = InferenceStopCondition.IncrementalStopMatcher(stops)
    var idx: InferenceStopCondition.Match? = null
    val sb = StringBuilder()
    // Feed character by character to stress the straddle logic.
    for (c in text) {
      sb.append(c)
      idx = matcher.findNewMatch(sb)
      if (idx != null) break
    }
    assertEquals(batch.second, idx != null || batch.second)
    if (idx != null) {
      assertEquals(batch.first, sb.substring(0, idx.index))
      assertEquals(batch.third, idx.sequence)
    } else {
      assertEquals(batch.first, sb.toString())
      assertNull(batch.third)
    }
  }
}
