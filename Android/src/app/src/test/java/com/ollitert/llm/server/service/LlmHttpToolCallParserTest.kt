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

  // ── Pattern 3: {"function": {"name": "...", "arguments": {...}}} ─────────

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
}
