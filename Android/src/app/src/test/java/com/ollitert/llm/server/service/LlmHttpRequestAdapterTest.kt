package com.ollitert.llm.server.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpRequestAdapterTest {
  @Test
  fun buildConversationPromptSingleTurnReturnsPlainText() {
    val prompt =
      LlmHttpRequestAdapter.buildConversationPrompt(
        listOf(
          InputMsg(
            role = "user",
            content =
              listOf(
                InputContent(type = "input_text", text = "keep me"),
                InputContent(type = "text", text = "final"),
              ),
          ),
        )
      )

    assertEquals("keep me final", prompt)
  }

  @Test
  fun buildConversationPromptMultiTurnFormatsHistory() {
    val prompt =
      LlmHttpRequestAdapter.buildConversationPrompt(
        listOf(
          InputMsg(
            role = "user",
            content = listOf(InputContent(type = "text", text = "first")),
          ),
          InputMsg(
            role = "assistant",
            content = listOf(InputContent(type = "text", text = "ignore")),
          ),
          InputMsg(
            role = "user",
            content =
              listOf(
                InputContent(type = "input_text", text = "keep me"),
                InputContent(type = "text", text = "final"),
              ),
          ),
        )
      )

    assertEquals("User: first\n\nAssistant: ignore\n\nUser: keep me final", prompt)
  }

  // ── Tool-aware prompt building ───────────────────────────────────────────

  @Test
  fun buildToolAwarePromptInjectsToolSchemas() {
    val tools = listOf(
      ToolSpec(function = ToolFunctionDef(name = "get_weather", description = "Get weather info")),
    )
    val msgs = listOf(ChatMessage("user", ChatContent("What's the weather?")))
    val prompt = LlmHttpRequestAdapter.buildToolAwarePrompt(msgs, tools, "auto", null)

    assertTrue("Should contain tool name", prompt.contains("get_weather"))
    assertTrue("Should contain tool description", prompt.contains("Get weather info"))
    assertTrue("Should contain JSON format instruction", prompt.contains("\"name\""))
  }

  @Test
  fun buildToolAwarePromptRequiredChoiceAddsInstruction() {
    val tools = listOf(
      ToolSpec(function = ToolFunctionDef(name = "fn")),
    )
    val msgs = listOf(ChatMessage("user", ChatContent("test")))
    val prompt = LlmHttpRequestAdapter.buildToolAwarePrompt(msgs, tools, "required", null)

    assertTrue("Should instruct model to call a tool", prompt.contains("MUST call"))
  }

  @Test
  fun buildToolAwarePromptPreservesUserMessages() {
    val tools = listOf(
      ToolSpec(function = ToolFunctionDef(name = "fn")),
    )
    val msgs = listOf(
      ChatMessage("user", ChatContent("Hello world")),
    )
    val prompt = LlmHttpRequestAdapter.buildToolAwarePrompt(msgs, tools, "auto", null)

    assertTrue("Should contain user message", prompt.contains("Hello world"))
  }

  // ── role: "tool" message handling ────────────────────────────────────────

  @Test
  fun buildChatPromptHandlesToolMessages() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("What's the weather?")),
      ChatMessage("assistant", ChatContent(""), tool_calls = listOf(
        ToolCall(id = "call_123", function = ToolCallFunction(name = "get_weather", arguments = "{\"location\":\"Boston\"}"))
      )),
      ChatMessage("tool", ChatContent("{\"temp\": 72}"), tool_call_id = "call_123", name = "get_weather"),
    )
    val prompt = LlmHttpRequestAdapter.buildChatPrompt(msgs)

    assertTrue("Should contain tool result", prompt.contains("Tool Result"))
    assertTrue("Should contain call_id", prompt.contains("call_123"))
    assertTrue("Should contain function name", prompt.contains("get_weather"))
    assertTrue("Should contain result data", prompt.contains("72"))
  }

  @Test
  fun buildChatPromptToolMessageWithoutCallId() {
    val msgs = listOf(
      ChatMessage("tool", ChatContent("{\"result\": \"data\"}")),
    )
    val prompt = LlmHttpRequestAdapter.buildChatPrompt(msgs)

    assertTrue("Should contain tool result", prompt.contains("Tool Result"))
    assertTrue("Should contain result data", prompt.contains("data"))
  }

  // ── resolveToolChoice ────────────────────────────────────────────────────

  @Test
  fun resolveToolChoiceString() {
    assertEquals("auto", LlmHttpRequestAdapter.resolveToolChoice(JsonPrimitive("auto")))
    assertEquals("none", LlmHttpRequestAdapter.resolveToolChoice(JsonPrimitive("none")))
    assertEquals("required", LlmHttpRequestAdapter.resolveToolChoice(JsonPrimitive("required")))
  }

  @Test
  fun resolveToolChoiceNull() {
    assertNull(LlmHttpRequestAdapter.resolveToolChoice(null))
  }

  @Test
  fun resolveToolChoiceObject() {
    val json = Json { ignoreUnknownKeys = true }
    val element = json.parseToJsonElement("""{"type":"function","function":{"name":"get_weather"}}""")
    val result = LlmHttpRequestAdapter.resolveToolChoice(element)
    assertEquals("get_weather", result)
  }

  @Test
  fun resolveToolChoiceObjectMissingName() {
    val json = Json { ignoreUnknownKeys = true }
    val element = json.parseToJsonElement("""{"type":"function"}""")
    val result = LlmHttpRequestAdapter.resolveToolChoice(element)
    assertEquals("auto", result) // Falls back to "auto"
  }
}
