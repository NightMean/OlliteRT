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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogJsonHighlighterTest {

  // ── tryParseJson ──────────────────────────────────────────────────────────
  // tryParseJson and prettyPrintJson use org.json.JSONObject/JSONArray which
  // require Android runtime. Only the plain-text fallback paths are testable
  // in JVM unit tests; JSON-parsing paths need instrumented tests.

  @Test
  fun returnsOriginalForPlainText() {
    val input = "just some text"
    val result = tryParseJson(input)
    assertEquals(input, result)
  }

  @Test
  fun returnsOriginalForEmptyString() {
    val result = tryParseJson("")
    assertEquals("", result)
  }

  // ── prettyPrintJson (non-JSON paths) ──────────────────────────────────────

  @Test
  fun prettyPrintReturnsOriginalForNonJson() {
    val input = "not json"
    assertEquals(input, prettyPrintJson(input))
  }

  // ── jsonTokenRegex ────────────────────────────────────────────────────────

  @Test
  fun regexMatchesObjectKey() {
    val match = jsonTokenRegex.find(""""key": "val"""")
    assertTrue(match != null)
    assertTrue(match!!.groupValues[1].isNotEmpty())
  }

  @Test
  fun regexMatchesStringValue() {
    val text = """: "hello""""
    val matches = jsonTokenRegex.findAll(text).toList()
    assertTrue(matches.any { it.groupValues[2].isNotEmpty() })
  }

  @Test
  fun regexMatchesNumber() {
    val matches = jsonTokenRegex.findAll("42").toList()
    assertTrue(matches.any { it.groupValues[3].isNotEmpty() })
  }

  @Test
  fun regexMatchesNegativeNumber() {
    val matches = jsonTokenRegex.findAll("-3.14").toList()
    assertTrue(matches.any { it.groupValues[3].isNotEmpty() })
  }

  @Test
  fun regexMatchesScientificNotation() {
    val matches = jsonTokenRegex.findAll("1.5e10").toList()
    assertTrue(matches.any { it.groupValues[3].isNotEmpty() })
  }

  @Test
  fun regexMatchesBooleanTrue() {
    val matches = jsonTokenRegex.findAll("true").toList()
    assertTrue(matches.any { it.groupValues[4] == "true" })
  }

  @Test
  fun regexMatchesBooleanFalse() {
    val matches = jsonTokenRegex.findAll("false").toList()
    assertTrue(matches.any { it.groupValues[4] == "false" })
  }

  @Test
  fun regexMatchesNull() {
    val matches = jsonTokenRegex.findAll("null").toList()
    assertTrue(matches.any { it.groupValues[4] == "null" })
  }

  @Test
  fun regexMatchesBraces() {
    val matches = jsonTokenRegex.findAll("{}[],:").toList()
    assertTrue(matches.count { it.groupValues[5].isNotEmpty() } >= 4)
  }

  @Test
  fun regexHandlesEscapedQuotes() {
    val text = """"key with \"escaped\" quotes""""
    val match = jsonTokenRegex.find(text)
    assertTrue(match != null)
  }
}
