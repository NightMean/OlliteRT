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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for API compatibility: response schema compliance with OpenAI spec.
 */
class LlmHttpApiModelsTest {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  // ── Usage: total_tokens──────────────────────────────────────────────

  @Test
  fun usageTotalTokensIsSum() {
    val usage = Usage(prompt_tokens = 10, completion_tokens = 20)
    assertEquals(30, usage.total_tokens)
  }

  @Test
  fun usageTotalTokensDefaultsToSum() {
    val usage = Usage(prompt_tokens = 0, completion_tokens = 0)
    assertEquals(0, usage.total_tokens)
  }

  @Test
  fun usageSerializesAllThreeFields() {
    val usage = Usage(prompt_tokens = 5, completion_tokens = 15)
    val serialized = json.encodeToString(usage)
    val obj = json.parseToJsonElement(serialized).jsonObject
    assertEquals(5, obj["prompt_tokens"]!!.jsonPrimitive.int)
    assertEquals(15, obj["completion_tokens"]!!.jsonPrimitive.int)
    assertEquals(20, obj["total_tokens"]!!.jsonPrimitive.int)
  }

  // ── ChatResponse: object field and system_fingerprint ────────────

  @Test
  fun chatResponseHasObjectField() {
    val resp = ChatResponse(
      id = "test", created = 1000, model = "m",
      choices = emptyList(), usage = Usage(0, 0),
    )
    assertEquals("chat.completion", resp.`object`)
  }

  @Test
  fun chatResponseSerializesObjectField() {
    val resp = ChatResponse(
      id = "test", created = 1000, model = "m",
      choices = emptyList(), usage = Usage(0, 0),
    )
    val serialized = json.encodeToString(resp)
    assertTrue("Should contain object field", serialized.contains("\"object\":\"chat.completion\""))
  }

  @Test
  fun chatResponseHasSystemFingerprintNull() {
    val resp = ChatResponse(
      id = "test", created = 1000, model = "m",
      choices = emptyList(), usage = Usage(0, 0),
    )
    assertNull(resp.system_fingerprint)
    val serialized = json.encodeToString(resp)
    assertTrue("Should contain system_fingerprint:null", serialized.contains("\"system_fingerprint\":null"))
  }

  // ── ChatRequest: stream_options──────────────────────────────────────

  @Test
  fun chatRequestDeserializesStreamOptions() {
    val input = """{"messages":[],"stream":true,"stream_options":{"include_usage":true}}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertEquals(true, req.stream_options?.include_usage)
  }

  @Test
  fun chatRequestStreamOptionsDefaultsToNull() {
    val input = """{"messages":[]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertNull(req.stream_options)
  }

  @Test
  fun chatRequestIgnoresUnknownFields() {
    // Clients like Open WebUI send extra fields (metadata, chat_id, etc.)
    val input = """{"messages":[],"stream":true,"metadata":{"chat_id":"abc"},"some_future_field":42}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertEquals(true, req.stream)
  }

  // ── Unique IDs: verify UUID format───────────────────────────────────

  @Test
  fun chatResponseIdPrefixFormat() {
    // When constructed with UUID, ID should start with "chatcmpl-"
    val id = "chatcmpl-${java.util.UUID.randomUUID()}"
    assertTrue(id.startsWith("chatcmpl-"))
    assertTrue(id.length > "chatcmpl-".length + 30) // UUID is 36 chars
  }

  @Test
  fun responsesResponseIdPrefixFormat() {
    val id = "resp-${java.util.UUID.randomUUID()}"
    assertTrue(id.startsWith("resp-"))
    assertTrue(id.length > "resp-".length + 30)
  }

  @Test
  fun uuidIdsAreUnique() {
    val id1 = "chatcmpl-${java.util.UUID.randomUUID()}"
    val id2 = "chatcmpl-${java.util.UUID.randomUUID()}"
    assertTrue("IDs should be unique", id1 != id2)
  }

  // ── CompletionRequest deserialization────────────────────────────

  @Test
  fun completionRequestDeserializesMinimal() {
    val input = """{"prompt":"Hello"}"""
    val req = json.decodeFromString<CompletionRequest>(input)
    assertEquals("Hello", req.prompt)
    assertNull(req.model)
    assertNull(req.stream)
    assertNull(req.max_tokens)
  }

  @Test
  fun completionRequestDeserializesFullFields() {
    val input = """{"model":"test","prompt":"Hi","max_tokens":100,"temperature":0.5,"top_p":0.9,"stream":false,"seed":42}"""
    val req = json.decodeFromString<CompletionRequest>(input)
    assertEquals("test", req.model)
    assertEquals("Hi", req.prompt)
    assertEquals(100, req.max_tokens)
    assertEquals(0.5, req.temperature!!, 0.001)
    assertEquals(0.9, req.top_p!!, 0.001)
    assertEquals(false, req.stream)
    assertEquals(42, req.seed)
  }

  @Test
  fun completionRequestIgnoresUnknownFields() {
    val input = """{"prompt":"test","some_future_field":"value","extra":123}"""
    val req = json.decodeFromString<CompletionRequest>(input)
    assertEquals("test", req.prompt)
  }

  // ── CompletionResponse serialization─────────────────────────────

  @Test
  fun completionResponseSerializesCorrectly() {
    val resp = CompletionResponse(
      id = "cmpl-test", created = 1000, model = "m",
      choices = listOf(CompletionChoice(text = "world", index = 0, finish_reason = "stop")),
      usage = Usage(5, 10),
    )
    val serialized = json.encodeToString(resp)
    assertTrue(serialized.contains("\"object\":\"text_completion\""))
    assertTrue(serialized.contains("\"id\":\"cmpl-test\""))
    assertTrue(serialized.contains("\"text\":\"world\""))
    assertTrue(serialized.contains("\"finish_reason\":\"stop\""))
    assertTrue(serialized.contains("\"total_tokens\":15"))
    assertTrue(serialized.contains("\"system_fingerprint\":null"))
  }

  @Test
  fun completionResponseEmptyPrompt() {
    val resp = CompletionResponse(
      id = "cmpl-empty", created = 1000, model = "m",
      choices = listOf(CompletionChoice(text = "", index = 0, finish_reason = "stop")),
      usage = Usage(0, 0),
    )
    val serialized = json.encodeToString(resp)
    assertTrue(serialized.contains("\"text\":\"\""))
    assertTrue(serialized.contains("\"total_tokens\":0"))
  }

  @Test
  fun completionIdPrefixFormat() {
    val id = "cmpl-${java.util.UUID.randomUUID()}"
    assertTrue(id.startsWith("cmpl-"))
    assertTrue(id.length > "cmpl-".length + 30)
  }

  // ── ChatMessage with tool_call_id and name ──────────────────────

  @Test
  fun chatMessageDeserializesToolCallId() {
    val input = """{"role":"tool","content":"result","tool_call_id":"call_abc","name":"get_weather"}"""
    val msg = json.decodeFromString<ChatMessage>(input)
    assertEquals("tool", msg.role)
    assertEquals("call_abc", msg.tool_call_id)
    assertEquals("get_weather", msg.name)
    assertEquals("result", msg.content.text)
  }

  @Test
  fun chatMessageToolCallIdDefaultsToNull() {
    val input = """{"role":"user","content":"hello"}"""
    val msg = json.decodeFromString<ChatMessage>(input)
    assertNull(msg.tool_call_id)
    assertNull(msg.name)
  }

  @Test
  fun chatMessageSerializesToolCallId() {
    val msg = ChatMessage(
      role = "tool",
      content = ChatContent("{\"temp\":72}"),
      tool_call_id = "call_xyz",
      name = "get_weather",
    )
    val serialized = json.encodeToString(msg)
    assertTrue(serialized.contains("\"tool_call_id\":\"call_xyz\""))
    assertTrue(serialized.contains("\"name\":\"get_weather\""))
  }

  // ── Polymorphic tool_choice─────────────────────────────────────

  @Test
  fun chatRequestToolChoiceAsString() {
    val input = """{"messages":[],"tool_choice":"auto"}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertNotNull(req.tool_choice)
  }

  @Test
  fun chatRequestToolChoiceAsObject() {
    val input = """{"messages":[],"tool_choice":{"type":"function","function":{"name":"get_weather"}}}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertNotNull(req.tool_choice)
    // Verify it's a JsonObject by checking it contains the expected structure
    val obj = req.tool_choice!!.jsonObject
    assertEquals("function", obj["type"]!!.jsonPrimitive.content)
  }

  @Test
  fun chatRequestToolChoiceDefaultsToNull() {
    val input = """{"messages":[]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertNull(req.tool_choice)
  }

  @Test
  fun chatRequestParallelToolCallsAccepted() {
    val input = """{"messages":[],"parallel_tool_calls":true}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertEquals(true, req.parallel_tool_calls)
  }

  // ── StopDeserializer edge cases ──────────────────────────────────────────

  @Test
  fun stopDeserializerHandlesNull() {
    val input = """{"messages":[],"stop":null}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertTrue("stop should be empty for null, got: ${req.stop}", req.stop.isEmpty())
  }

  @Test
  fun stopDeserializerHandlesString() {
    val input = """{"messages":[],"stop":"END"}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertEquals(listOf("END"), req.stop)
  }

  @Test
  fun stopDeserializerHandlesArray() {
    val input = """{"messages":[],"stop":["END","STOP"]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertEquals(listOf("END", "STOP"), req.stop)
  }

  @Test
  fun stopDeserializerHandlesEmptyArray() {
    val input = """{"messages":[],"stop":[]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertTrue(req.stop.isEmpty())
  }

  @Test
  fun stopDeserializerHandlesAbsent() {
    val input = """{"messages":[]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    assertTrue(req.stop.isEmpty())
  }

  // ── ContentPart / input_audio deserialization ────────────────────────────

  @Test
  fun inputAudioContentPartParsesValidPayload() {
    val input = """{"messages":[{"role":"user","content":[{"type":"input_audio","input_audio":{"data":"SGVsbG8=","format":"wav"}}]}]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    val parts = req.messages.first().content.parts
    assertEquals(1, parts.size)
    val part = parts.first()
    assertEquals("input_audio", part.type)
    assertNotNull(part.input_audio)
    assertEquals("SGVsbG8=", part.input_audio!!.data)
    assertEquals("wav", part.input_audio.format)
  }

  @Test
  fun inputAudioContentPartHandlesMissingDataField() {
    // `data` absent — should default to "" rather than crash
    val input = """{"messages":[{"role":"user","content":[{"type":"input_audio","input_audio":{"format":"mp3"}}]}]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    val part = req.messages.first().content.parts.first()
    assertEquals("input_audio", part.type)
    assertNotNull(part.input_audio)
    assertEquals("", part.input_audio!!.data)
    assertEquals("mp3", part.input_audio.format)
  }

  @Test
  fun inputAudioContentPartHandlesNullInputAudioObject() {
    // `input_audio` key present but null — should not crash, field should be null
    val input = """{"messages":[{"role":"user","content":[{"type":"input_audio","input_audio":null}]}]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    val part = req.messages.first().content.parts.first()
    assertEquals("input_audio", part.type)
    assertNull(part.input_audio)
  }

  @Test
  fun inputAudioContentPartHandlesAbsentFormat() {
    // `format` omitted — should default to null
    val input = """{"messages":[{"role":"user","content":[{"type":"input_audio","input_audio":{"data":"dGVzdA=="}}]}]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    val part = req.messages.first().content.parts.first()
    assertNotNull(part.input_audio)
    assertEquals("dGVzdA==", part.input_audio!!.data)
    assertNull(part.input_audio.format)
  }

  @Test
  fun mixedContentPartsTextImageAudio() {
    // All three content part types in a single message
    val input = """{"messages":[{"role":"user","content":[
      {"type":"text","text":"describe this"},
      {"type":"image_url","image_url":{"url":"data:image/png;base64,abc"}},
      {"type":"input_audio","input_audio":{"data":"SGVsbG8=","format":"wav"}}
    ]}]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    val parts = req.messages.first().content.parts
    assertEquals(3, parts.size)

    val textPart = parts[0]
    assertEquals("text", textPart.type)
    assertEquals("describe this", textPart.text)
    assertNull(textPart.image_url)
    assertNull(textPart.input_audio)

    val imagePart = parts[1]
    assertEquals("image_url", imagePart.type)
    assertNotNull(imagePart.image_url)
    assertEquals("data:image/png;base64,abc", imagePart.image_url!!.url)
    assertNull(imagePart.input_audio)

    val audioPart = parts[2]
    assertEquals("input_audio", audioPart.type)
    assertNotNull(audioPart.input_audio)
    assertEquals("SGVsbG8=", audioPart.input_audio!!.data)
    assertEquals("wav", audioPart.input_audio.format)
    assertNull(audioPart.text)
    assertNull(audioPart.image_url)
  }

  @Test
  fun existingTextAndImageUrlDeserializationUnchanged() {
    // Regression check: existing text + image_url parsing still works after adding input_audio
    val input = """{"messages":[{"role":"user","content":[
      {"type":"text","text":"hello"},
      {"type":"image_url","image_url":{"url":"https://example.com/img.png"}}
    ]}]}"""
    val req = json.decodeFromString<ChatRequest>(input)
    val parts = req.messages.first().content.parts
    assertEquals(2, parts.size)
    assertEquals("hello", parts[0].text)
    assertEquals("https://example.com/img.png", parts[1].image_url?.url)
    assertTrue(parts.all { it.input_audio == null })
  }
}
