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

package com.ollitert.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptionFormatterTest {

  // ---- toText ---------------------------------------------------------------

  @Test
  fun toText_returnsPlainText() {
    assertEquals("Hello world", TranscriptionFormatter.toText("Hello world"))
  }

  @Test
  fun toText_preservesWhitespace() {
    assertEquals("Line one\nLine two", TranscriptionFormatter.toText("Line one\nLine two"))
  }

  // ---- toJson ---------------------------------------------------------------

  @Test
  fun toJson_wrapsTextInJsonObject() {
    assertEquals("""{"text":"Hello world"}""", TranscriptionFormatter.toJson("Hello world"))
  }

  @Test
  fun toJson_escapesQuotesAndNewlines() {
    val input = "He said \"hello\"\nnew line"
    val result = TranscriptionFormatter.toJson(input)
    assertEquals("""{"text":"He said \"hello\"\nnew line"}""", result)
  }

  @Test
  fun toJson_escapesBackslash() {
    assertEquals("""{"text":"a\\b"}""", TranscriptionFormatter.toJson("a\\b"))
  }

  @Test
  fun toJson_escapesControlChars() {
    val input = "tab\there"
    assertEquals("""{"text":"tab\there"}""", TranscriptionFormatter.toJson(input))
  }

  // ---- toVerboseJson --------------------------------------------------------
  // org.json.JSONObject is an Android stub in JVM unit tests, so we use string matching.

  @Test
  fun toVerboseJson_containsAllTopLevelFields() {
    val result = TranscriptionFormatter.toVerboseJson(
      text = "Hello world",
      language = "en",
      durationSeconds = 2.5,
    )
    assert(result.contains(""""task":"transcribe"""")) { result }
    assert(result.contains(""""language":"en"""")) { result }
    assert(result.contains(""""duration":2.5""")) { result }
    assert(result.contains(""""text":"Hello world"""")) { result }
  }

  @Test
  fun toVerboseJson_hasSingleSegment() {
    val result = TranscriptionFormatter.toVerboseJson(
      text = "Hello world",
      language = "en",
      durationSeconds = 3.0,
    )
    assert(result.contains(""""segments":[{""")) { result }
    assert(result.contains(""""id":0""")) { result }
    assert(result.contains(""""start":0.0""")) { result }
    assert(result.contains(""""end":3.0""")) { result }
  }

  @Test
  fun toVerboseJson_nullLanguageDefaultsToEmpty() {
    val result = TranscriptionFormatter.toVerboseJson(
      text = "Hello",
      language = null,
      durationSeconds = 1.0,
    )
    assert(result.contains(""""language":""""")) { result }
  }

  @Test
  fun toVerboseJson_zeroDuration() {
    val result = TranscriptionFormatter.toVerboseJson(
      text = "Hello",
      language = "ja",
      durationSeconds = 0.0,
    )
    assert(result.contains(""""duration":0.0""")) { result }
    assert(result.contains(""""end":0.0""")) { result }
  }

  @Test
  fun toVerboseJson_escapesSpecialCharsInText() {
    val result = TranscriptionFormatter.toVerboseJson(
      text = "He said \"hi\"\nnewline",
      language = "en",
      durationSeconds = 1.0,
    )
    assert(result.contains("""He said \"hi\"\n""")) { result }
  }
}
