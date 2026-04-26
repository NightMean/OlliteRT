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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpRequestAdapterTest {
  @Test
  fun buildConversationPromptSingleTurnReturnsPlainText() {
    val prompt =
      PromptBuilder.buildConversationPrompt(
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
      PromptBuilder.buildConversationPrompt(
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

  // ── Image placeholder interleaving ──────────────────────────────────────

  @Test
  fun `buildChatPrompt - single image inserts placeholder before text`() {
    // Vision models expect image-first ordering: image content before the referencing text.
    val msgs = listOf(
      ChatMessage("user", ChatContent("what is this", parts = listOf(
        ContentPart(type = "text", text = "what is this"),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,abc")),
      )))
    )
    val prompt = PromptBuilder.buildChatPrompt(msgs, interleaveImagePlaceholders = true)
    val placeholder = PromptBuilder.IMAGE_PLACEHOLDER
    assertEquals("${placeholder}what is this", prompt)
  }

  @Test
  fun `buildChatPrompt - multi-turn images insert placeholders before text at each turn`() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("what is this", parts = listOf(
        ContentPart(type = "text", text = "what is this"),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,img1")),
      ))),
      ChatMessage("assistant", ChatContent("It's a cat.")),
      ChatMessage("user", ChatContent("and this", parts = listOf(
        ContentPart(type = "text", text = "and this"),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,img2")),
      ))),
    )
    val prompt = PromptBuilder.buildChatPrompt(msgs, interleaveImagePlaceholders = true)
    val placeholder = PromptBuilder.IMAGE_PLACEHOLDER
    // Each turn's placeholder should come before its text
    assertTrue("First turn: placeholder before text", prompt.contains("${placeholder}what is this"))
    assertTrue("Second turn: placeholder before text", prompt.contains("${placeholder}and this"))
    // Verify ordering across turns: placeholder1 → text1 → assistant → placeholder2 → text2
    val firstIdx = prompt.indexOf(placeholder)
    val assistantIdx = prompt.indexOf("It's a cat.")
    val secondIdx = prompt.indexOf(placeholder, firstIdx + 1)
    assertTrue("First placeholder before assistant", firstIdx < assistantIdx)
    assertTrue("Second placeholder after assistant", secondIdx > assistantIdx)
  }

  @Test
  fun `buildChatPrompt - no placeholders when flag is false`() {
    // text field is populated by the serializer from text parts (matches real deserialization)
    val msgs = listOf(
      ChatMessage("user", ChatContent("what is this", parts = listOf(
        ContentPart(type = "text", text = "what is this"),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,abc")),
      )))
    )
    val prompt = PromptBuilder.buildChatPrompt(msgs, interleaveImagePlaceholders = false)
    assertFalse("Should not contain placeholder", prompt.contains(PromptBuilder.IMAGE_PLACEHOLDER))
    assertEquals("what is this", prompt)
  }

  @Test
  fun `buildChatPrompt - text-only messages unaffected by placeholder flag`() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("hello")),
      ChatMessage("assistant", ChatContent("hi there")),
    )
    val withFlag = PromptBuilder.buildChatPrompt(msgs, interleaveImagePlaceholders = true)
    val withoutFlag = PromptBuilder.buildChatPrompt(msgs, interleaveImagePlaceholders = false)
    assertEquals("Flag should not affect text-only messages", withoutFlag, withFlag)
  }

  @Test
  fun `buildChatPrompt - image-first parts order also puts placeholder before text`() {
    // Even when client sends [image, text], placeholder is still before text
    val msgs = listOf(
      ChatMessage("user", ChatContent("describe this", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,abc")),
        ContentPart(type = "text", text = "describe this"),
      )))
    )
    val prompt = PromptBuilder.buildChatPrompt(msgs, interleaveImagePlaceholders = true)
    val placeholder = PromptBuilder.IMAGE_PLACEHOLDER
    assertEquals("${placeholder}describe this", prompt)
  }

  @Test
  fun `buildChatPrompt - multiple images in one message all precede text`() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("compare these", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,img1")),
        ContentPart(type = "text", text = "compare these"),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,img2")),
      )))
    )
    val prompt = PromptBuilder.buildChatPrompt(msgs, interleaveImagePlaceholders = true)
    val placeholder = PromptBuilder.IMAGE_PLACEHOLDER
    // Both placeholders should come before the text
    assertEquals("${placeholder}${placeholder}compare these", prompt)
  }

  // ── Tool-aware prompt building ───────────────────────────────────────────

  @Test
  fun buildToolAwarePromptInjectsToolSchemas() {
    val tools = listOf(
      ToolSpec(function = ToolFunctionDef(name = "get_weather", description = "Get weather info")),
    )
    val msgs = listOf(ChatMessage("user", ChatContent("What's the weather?")))
    val prompt = PromptBuilder.buildToolAwarePrompt(msgs, tools, "auto", null)

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
    val prompt = PromptBuilder.buildToolAwarePrompt(msgs, tools, "required", null)

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
    val prompt = PromptBuilder.buildToolAwarePrompt(msgs, tools, "auto", null)

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
    val prompt = PromptBuilder.buildChatPrompt(msgs)

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
    val prompt = PromptBuilder.buildChatPrompt(msgs)

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
    val prompt = PromptBuilder.buildChatPrompt(msgs)

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
    val prompt = PromptBuilder.buildChatPrompt(msgs)

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
    val prompt = PromptBuilder.buildChatPrompt(msgs)

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
    val prompt = PromptBuilder.buildChatPrompt(msgs)

    assertTrue("Should contain tool result", prompt.contains("Tool Result"))
    assertTrue("Should contain result data", prompt.contains("data"))
  }

  // ── resolveToolChoice ────────────────────────────────────────────────────

  @Test
  fun resolveToolChoiceString() {
    assertEquals("auto", PromptBuilder.resolveToolChoice(JsonPrimitive("auto")))
    assertEquals("none", PromptBuilder.resolveToolChoice(JsonPrimitive("none")))
    assertEquals("required", PromptBuilder.resolveToolChoice(JsonPrimitive("required")))
  }

  @Test
  fun resolveToolChoiceNull() {
    assertNull(PromptBuilder.resolveToolChoice(null))
  }

  @Test
  fun resolveToolChoiceObject() {
    val json = Json { ignoreUnknownKeys = true }
    val element = json.parseToJsonElement("""{"type":"function","function":{"name":"get_weather"}}""")
    val result = PromptBuilder.resolveToolChoice(element)
    assertEquals("get_weather", result)
  }

  @Test
  fun resolveToolChoiceObjectMissingName() {
    val json = Json { ignoreUnknownKeys = true }
    val element = json.parseToJsonElement("""{"type":"function"}""")
    val result = PromptBuilder.resolveToolChoice(element)
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
    val result = PromptBuilder.extractImageDataUris(msgs)
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
    val result = PromptBuilder.extractImageDataUris(msgs)
    assertEquals(2, result.size)
  }

  @Test
  fun `extractImageDataUris - extracts from all messages in order`() {
    // The API is stateless — clients resend the full conversation history including all
    // images, and the server processes all images from all turns.
    val msgs = listOf(
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,old_img")),
        ContentPart(type = "text", text = "what is this?"),
      ))),
      ChatMessage("assistant", ChatContent("It's a cat.")),
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,new_img")),
        ContentPart(type = "text", text = "what about this one?"),
      ))),
    )
    val result = PromptBuilder.extractImageDataUris(msgs)
    assertEquals("Should extract from all user messages", 2, result.size)
    assertEquals("data:image/png;base64,old_img", result[0])
    assertEquals("data:image/png;base64,new_img", result[1])
  }

  @Test
  fun `extractImageDataUris - multiple images in last user message`() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,img1")),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/jpeg;base64,img2")),
        ContentPart(type = "text", text = "compare these two"),
      ))),
    )
    val result = PromptBuilder.extractImageDataUris(msgs)
    assertEquals("Both images from same message should be extracted", 2, result.size)
  }

  @Test
  fun `extractImageDataUris - extracts from all roles including assistant`() {
    // Images are extracted from all messages regardless of role, preserving order.
    val msgs = listOf(
      ChatMessage("assistant", ChatContent("", parts = listOf(
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,assistant_img")),
      ))),
      ChatMessage("user", ChatContent("hello")),
    )
    val result = PromptBuilder.extractImageDataUris(msgs)
    assertEquals("Should extract images from all roles", 1, result.size)
    assertEquals("data:image/png;base64,assistant_img", result[0])
  }

  @Test
  fun extractImageDataUrisIgnoresTextParts() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("hello", parts = listOf(
        ContentPart(type = "text", text = "describe this image"),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,abc")),
      )))
    )
    val result = PromptBuilder.extractImageDataUris(msgs)
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
    val result = PromptBuilder.extractImageDataUris(msgs)
    assertTrue(result.isEmpty())
  }

  @Test
  fun extractImageDataUrisReturnsEmptyForEmptyMessages() {
    val result = PromptBuilder.extractImageDataUris(emptyList())
    assertTrue(result.isEmpty())
  }

  // ── extractAudioData() ───────────────────────────────────────────────────

  @Test
  fun extractAudioDataSingleClip() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "input_audio", input_audio = InputAudio(data = "abc123", format = "wav"))
      )))
    )
    val result = PromptBuilder.extractAudioData(msgs)
    assertEquals(listOf("abc123"), result)
  }

  @Test
  fun extractAudioDataMultipleClipsAcrossMessages() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "input_audio", input_audio = InputAudio(data = "clip1", format = "wav"))
      ))),
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "input_audio", input_audio = InputAudio(data = "clip2", format = "mp3"))
      ))),
    )
    val result = PromptBuilder.extractAudioData(msgs)
    assertEquals(2, result.size)
    assertEquals("clip1", result[0])
    assertEquals("clip2", result[1])
  }

  @Test
  fun extractAudioDataIgnoresNonAudioParts() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("hello", parts = listOf(
        ContentPart(type = "text", text = "hello"),
        ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,abc")),
        ContentPart(type = "input_audio", input_audio = InputAudio(data = "audiodata", format = "wav")),
      )))
    )
    val result = PromptBuilder.extractAudioData(msgs)
    assertEquals(1, result.size)
    assertEquals("audiodata", result[0])
  }

  @Test
  fun extractAudioDataFiltersBlankData() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "input_audio", input_audio = InputAudio(data = "  ", format = "wav")),
        ContentPart(type = "input_audio", input_audio = InputAudio(data = "validdata", format = "wav")),
      )))
    )
    val result = PromptBuilder.extractAudioData(msgs)
    assertEquals(1, result.size)
    assertEquals("validdata", result[0])
  }

  @Test
  fun extractAudioDataFiltersNullInputAudio() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("", parts = listOf(
        ContentPart(type = "input_audio", input_audio = null),
      )))
    )
    val result = PromptBuilder.extractAudioData(msgs)
    assertTrue(result.isEmpty())
  }

  @Test
  fun extractAudioDataReturnsEmptyForEmptyMessages() {
    val result = PromptBuilder.extractAudioData(emptyList())
    assertTrue(result.isEmpty())
  }

  @Test
  fun extractAudioDataReturnsEmptyForTextOnlyMessages() {
    val msgs = listOf(
      ChatMessage("user", ChatContent("hello")),
      ChatMessage("assistant", ChatContent("hi")),
    )
    val result = PromptBuilder.extractAudioData(msgs)
    assertTrue(result.isEmpty())
  }
}
