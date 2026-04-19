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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for LlmHttpToolCallParser — parsing tool/function calls from raw model text output.
 */
class LlmHttpToolCallParserTest {

  private val tools = listOf(
    ToolSpec(function = ToolFunctionDef(name = "get_weather", description = "Get weather")),
    ToolSpec(function = ToolFunctionDef(name = "search_docs", description = "Search docs")),
  )

  // ── Pattern 1: {"tool_call": {"name": "...", "arguments": {...}}} ─────────

  @Test
  fun parsesToolCallWrapperPattern() {
    val output = """{"tool_call": {"name": "get_weather", "arguments": {"location": "Boston"}}}"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("get_weather", result!!.function.name)
    assertTrue(result.function.arguments.contains("Boston"))
    assertTrue(result.id.startsWith("call_"))
  }

  // ── Pattern 2: <tool_call>...</tool_call> ────────────────────────────────

  @Test
  fun parsesXmlWrappedToolCall() {
    val output = """I'll check the weather for you.
<tool_call>{"name": "get_weather", "arguments": {"location": "NYC"}}</tool_call>"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("get_weather", result!!.function.name)
    assertTrue(result.function.arguments.contains("NYC"))
  }

  @Test
  fun parsesXmlWrappedWithWhitespace() {
    val output = """<tool_call>
  {"name": "search_docs", "arguments": {"query": "test"}}
</tool_call>"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("search_docs", result!!.function.name)
  }

  // ── Pattern 3: <|tool_call>call:FunctionName{args}<tool_call|> ────────────

  @Test
  fun parsesNativeGemmaToolCallEmptyArgs() {
    val output = "<|tool_call>call:get_weather{}<tool_call|>"
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("get_weather", result!!.function.name)
    assertEquals("{}", result.function.arguments)
    assertTrue(result.id.startsWith("call_"))
  }

  @Test
  fun parsesNativeGemmaToolCallWithArgs() {
    val output = """<|tool_call>call:get_weather{"location": "Boston"}<tool_call|>"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("get_weather", result!!.function.name)
    assertTrue(result.function.arguments.contains("Boston"))
  }

  @Test
  fun parsesNativeGemmaToolCallWithSurroundingText() {
    val output = """Let me check that for you.
<|tool_call>call:search_docs{"query": "API reference"}<tool_call|>"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("search_docs", result!!.function.name)
    assertTrue(result.function.arguments.contains("API reference"))
  }

  @Test
  fun returnsNullForNativeGemmaUnknownTool() {
    val output = "<|tool_call>call:unknown_func{}<tool_call|>"
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNull(result)
  }

  // ── Pattern 4/5: {"function": {"name": "...", "arguments": {...}}} ──────

  @Test
  fun parsesFunctionWrapperPattern() {
    val output = """{"function": {"name": "get_weather", "arguments": {"location": "London"}}}"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("get_weather", result!!.function.name)
    assertTrue(result.function.arguments.contains("London"))
  }

  // ── Pattern 4: {"name": "...", "arguments": {...}} — bare call ───────────

  @Test
  fun parsesBareCallPattern() {
    val output = """{"name": "get_weather", "arguments": {"location": "Paris"}}"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("get_weather", result!!.function.name)
    assertTrue(result.function.arguments.contains("Paris"))
  }

  @Test
  fun parsesBareCallWithSurroundingText() {
    val output = """Sure, I'll look that up for you.
{"name": "search_docs", "arguments": {"query": "API docs"}}
Let me know if you need more."""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("search_docs", result!!.function.name)
  }

  // ── Validation: function name must match available tools ──────────────────

  @Test
  fun returnsNullForUnknownToolName() {
    val output = """{"name": "unknown_function", "arguments": {"key": "value"}}"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNull(result)
  }

  @Test
  fun returnsNullForEmptyToolList() {
    val output = """{"name": "get_weather", "arguments": {"location": "Boston"}}"""
    val result = LlmHttpToolCallParser.parse(output, emptyList())
    assertNull(result)
  }

  // ── No tool call detected ────────────────────────────────────────────────

  @Test
  fun returnsNullForPlainText() {
    val output = "The weather in Boston is sunny and 72°F."
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNull(result)
  }

  @Test
  fun returnsNullForEmptyString() {
    val result = LlmHttpToolCallParser.parse("", tools)
    assertNull(result)
  }

  @Test
  fun returnsNullForJsonWithoutNameAndArguments() {
    val output = """{"response": "Hello, how can I help?"}"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNull(result)
  }

  @Test
  fun returnsNullForJsonWithNameButNoArguments() {
    val output = """{"name": "get_weather"}"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNull(result)
  }

  @Test
  fun returnsNullForMalformedJson() {
    val output = """{"name": "get_weather", "arguments": {broken"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNull(result)
  }

  // ── Nested JSON handling ─────────────────────────────────────────────────

  @Test
  fun handlesNestedJsonArguments() {
    val output = """{"name": "get_weather", "arguments": {"location": "Boston", "options": {"units": "metric", "detail": true}}}"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("get_weather", result!!.function.name)
    assertTrue(result.function.arguments.contains("metric"))
  }

  // ── Tool call ID format ──────────────────────────────────────────────────

  @Test
  fun generatesUniqueCallIds() {
    val output = """{"name": "get_weather", "arguments": {"location": "Boston"}}"""
    val result1 = LlmHttpToolCallParser.parse(output, tools)
    val result2 = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result1)
    assertNotNull(result2)
    assertTrue("IDs should be unique", result1!!.id != result2!!.id)
  }

  @Test
  fun callIdHasCorrectPrefix() {
    val output = """{"name": "get_weather", "arguments": {}}"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertTrue(result!!.id.startsWith("call_"))
  }

  // ── Edge case: JSON embedded in text ─────────────────────────────────────

  @Test
  fun parsesToolCallEmbeddedInMarkdown() {
    val output = """Here's the function call:
```json
{"name": "get_weather", "arguments": {"location": "Tokyo"}}
```"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("get_weather", result!!.function.name)
  }

  // ── String arguments (not just object) ───────────────────────────────────

  @Test
  fun handlesStringArguments() {
    // Some models may emit arguments as a JSON string rather than an object
    val output = """{"name": "get_weather", "arguments": "Boston"}"""
    val result = LlmHttpToolCallParser.parse(output, tools)
    assertNotNull(result)
    assertEquals("get_weather", result!!.function.name)
  }

  // ── parseAll: Multiple tool calls (parallel calling) ─────────────────────

  @Test
  fun parseAllReturnsMultipleXmlWrappedCalls() {
    val output = """I'll do both for you.
<tool_call>{"name": "get_weather", "arguments": {"location": "Boston"}}</tool_call>
<tool_call>{"name": "search_docs", "arguments": {"query": "forecast"}}</tool_call>"""
    val results = LlmHttpToolCallParser.parseAll(output, tools)
    assertEquals(2, results.size)
    assertEquals("get_weather", results[0].function.name)
    assertEquals("search_docs", results[1].function.name)
    assertTrue("IDs should be unique", results[0].id != results[1].id)
  }

  @Test
  fun parseAllReturnsMultipleNativeGemmaCalls() {
    val output = """<|tool_call>call:get_weather{"location": "NYC"}<tool_call|><|tool_call>call:search_docs{"query": "rain"}<tool_call|>"""
    val results = LlmHttpToolCallParser.parseAll(output, tools)
    assertEquals(2, results.size)
    assertEquals("get_weather", results[0].function.name)
    assertEquals("search_docs", results[1].function.name)
  }

  @Test
  fun parseAllReturnsJsonArray() {
    val output = """[{"name": "get_weather", "arguments": {"location": "Boston"}}, {"name": "search_docs", "arguments": {"query": "weather"}}]"""
    val results = LlmHttpToolCallParser.parseAll(output, tools)
    assertEquals(2, results.size)
    assertEquals("get_weather", results[0].function.name)
    assertEquals("search_docs", results[1].function.name)
  }

  @Test
  fun parseAllJsonArrayWithSurroundingText() {
    val output = """Sure, I'll call both:
[{"name": "get_weather", "arguments": {"location": "LA"}}, {"name": "search_docs", "arguments": {"query": "sun"}}]
Done!"""
    val results = LlmHttpToolCallParser.parseAll(output, tools)
    assertEquals(2, results.size)
  }

  @Test
  fun parseAllFallsBackToSingleCall() {
    // Single call should still work via parseAll
    val output = """{"name": "get_weather", "arguments": {"location": "Boston"}}"""
    val results = LlmHttpToolCallParser.parseAll(output, tools)
    assertEquals(1, results.size)
    assertEquals("get_weather", results[0].function.name)
  }

  @Test
  fun parseAllReturnsEmptyForPlainText() {
    val results = LlmHttpToolCallParser.parseAll("The weather is nice today.", tools)
    assertTrue(results.isEmpty())
  }

  @Test
  fun parseAllJsonArrayIgnoresSingleElement() {
    // Single-element arrays should fall through to single-call parsing (not multi-call path)
    val output = """[{"name": "get_weather", "arguments": {"location": "Boston"}}]"""
    val results = LlmHttpToolCallParser.parseAll(output, tools)
    // Should still find 1 via the fallback bare-call parser (extracts the inner JSON object)
    assertEquals(1, results.size)
  }

  @Test
  fun parseAllJsonArraySkipsInvalidEntries() {
    // One valid, one with unknown function name — only valid one returned
    val output = """[{"name": "get_weather", "arguments": {}}, {"name": "unknown_fn", "arguments": {}}]"""
    val results = LlmHttpToolCallParser.parseAll(output, tools)
    // Array has 2 entries but only 1 is valid → falls back to single-call (array needs 2+ valid)
    assertEquals(1, results.size)
    assertEquals("get_weather", results[0].function.name)
  }

  @Test
  fun parseAllMultipleXmlSkipsInvalid() {
    val output = """<tool_call>{"name": "get_weather", "arguments": {"location": "NYC"}}</tool_call>
<tool_call>{"name": "unknown", "arguments": {}}</tool_call>
<tool_call>{"name": "search_docs", "arguments": {"query": "test"}}</tool_call>"""
    val results = LlmHttpToolCallParser.parseAll(output, tools)
    assertEquals(2, results.size)
    assertEquals("get_weather", results[0].function.name)
    assertEquals("search_docs", results[1].function.name)
  }
}
