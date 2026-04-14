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

  // ── Assistant messages with tool_calls + empty/null content ──────────────

  @Test
  fun buildChatPromptPreservesAssistantToolCalls() {
    // HA sends: assistant message with null/empty content but tool_calls present.
    // The model must see its own prior tool call to correlate with the tool result.
    val msgs = listOf(
      ChatMessage("user", ChatContent("Turn on the lights")),
      ChatMessage("assistant", ChatContent(""), tool_calls = listOf(
        ToolCall(id = "call_abc", function = ToolCallFunction(name = "HassTurnOn", arguments = "{\"name\":\"Living Room Light\"}"))
      )),
      ChatMessage("tool", ChatContent("{\"success\":true}"), tool_call_id = "call_abc", name = "HassTurnOn"),
    )
    val prompt = LlmHttpRequestAdapter.buildChatPrompt(msgs)

    // The assistant message must NOT be dropped — it must show the tool call
    assertTrue("Should contain assistant's tool call name", prompt.contains("HassTurnOn"))
    assertTrue("Should contain assistant's tool call id", prompt.contains("call_abc"))
    assertTrue("Should contain tool call arguments", prompt.contains("Living Room Light"))
    // And the tool result must also be present
    assertTrue("Should contain tool result", prompt.contains("Tool Result"))
    assertTrue("Should contain success result", prompt.contains("success"))
  }

  @Test
  fun buildChatPromptPreservesMultipleToolCallsOnAssistant() {
    // Parallel tool calls: assistant returns multiple calls in one message
    val msgs = listOf(
      ChatMessage("user", ChatContent("Turn on lights and set thermostat")),
      ChatMessage("assistant", ChatContent(""), tool_calls = listOf(
        ToolCall(id = "call_1", function = ToolCallFunction(name = "HassTurnOn", arguments = "{\"name\":\"Light\"}")),
        ToolCall(id = "call_2", function = ToolCallFunction(name = "HassClimateSetTemperature", arguments = "{\"name\":\"Thermostat\",\"temperature\":72}")),
      )),
      ChatMessage("tool", ChatContent("{\"success\":true}"), tool_call_id = "call_1", name = "HassTurnOn"),
      ChatMessage("tool", ChatContent("{\"success\":true}"), tool_call_id = "call_2", name = "HassClimateSetTemperature"),
    )
    val prompt = LlmHttpRequestAdapter.buildChatPrompt(msgs)

    assertTrue("Should contain first tool call", prompt.contains("HassTurnOn"))
    assertTrue("Should contain second tool call", prompt.contains("HassClimateSetTemperature"))
    assertTrue("Should contain both tool results", prompt.contains("call_1") && prompt.contains("call_2"))
  }

  @Test
  fun buildChatPromptAssistantWithContentAndToolCalls() {
    // Some models output text content alongside tool_calls
    val msgs = listOf(
      ChatMessage("assistant", ChatContent("Let me check that."), tool_calls = listOf(
        ToolCall(id = "call_x", function = ToolCallFunction(name = "get_weather", arguments = "{}"))
      )),
    )
    val prompt = LlmHttpRequestAdapter.buildChatPrompt(msgs)

    assertTrue("Should contain content text", prompt.contains("Let me check that."))
    assertTrue("Should contain tool call", prompt.contains("get_weather"))
  }

  @Test
  fun fullHaMultiTurnToolConversation() {
    // Simulates the complete HA tool calling loop:
    // 1. User asks → 2. Assistant calls tool → 3. Tool result → 4. Assistant responds
    val msgs = listOf(
      ChatMessage("system", ChatContent("You are a helpful assistant controlling a smart home.")),
      ChatMessage("user", ChatContent("Turn on the living room lights")),
      ChatMessage("assistant", ChatContent(""), tool_calls = listOf(
        ToolCall(id = "call_abc123", function = ToolCallFunction(name = "HassTurnOn", arguments = "{\"name\":\"Living Room Light\"}"))
      )),
      ChatMessage("tool", ChatContent("{\"success\":true}"), tool_call_id = "call_abc123", name = "HassTurnOn"),
    )
    val prompt = LlmHttpRequestAdapter.buildChatPrompt(msgs)

    // Verify ordering: system → user → assistant (with tool call) → tool result
    val systemIdx = prompt.indexOf("smart home")
    val userIdx = prompt.indexOf("Turn on the living room lights")
    val toolCallIdx = prompt.indexOf("HassTurnOn")
    val toolResultIdx = prompt.indexOf("Tool Result")
    assertTrue("System before user", systemIdx < userIdx)
    assertTrue("User before tool call", userIdx < toolCallIdx)
    assertTrue("Tool call before tool result", toolCallIdx < toolResultIdx)
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

  // ── extractImageDataUris() ───────────────────────────────────────────────

  @Test
  fun extractImageDataUrisSingleUri() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,abc123"))
      )))
    )
    val result = LlmHttpRequestAdapter.extractImageDataUris(msgs)
    assertEquals(listOf("data:image/png;base64,abc123"), result)
  }

  @Test
  fun extractImageDataUrisMultipleUrisInOneMessage() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,img1")),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/jpeg;base64,img2")),
      )))
    )
    val result = LlmHttpRequestAdapter.extractImageDataUris(msgs)
    assertEquals(2, result.size)
  }

  @Test
  fun extractImageDataUrisAcrossMultipleMessages() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,img1")),
      ))),
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,img2")),
      ))),
    )
    val result = LlmHttpRequestAdapter.extractImageDataUris(msgs)
    assertEquals(2, result.size)
  }

  @Test
  fun extractImageDataUrisIgnoresTextParts() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("hello", parts = listOf(
        ContentPart(type = "text", text = "describe this image"),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,abc")),
      )))
    )
    val result = LlmHttpRequestAdapter.extractImageDataUris(msgs)
    assertEquals(1, result.size)
    assertEquals("data:image/png;base64,abc", result[0])
  }

  @Test
  fun extractImageDataUrisReturnsEmptyForTextOnly() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("hello", parts = listOf(
        ContentPart(type = "text", text = "just text"),
      )))
    )
    val result = LlmHttpRequestAdapter.extractImageDataUris(msgs)
    assertTrue(result.isEmpty())
  }

  @Test
  fun extractImageDataUrisReturnsEmptyForEmptyMessages() {
    val result = LlmHttpRequestAdapter.extractImageDataUris(emptyList())
    assertTrue(result.isEmpty())
  }
}
