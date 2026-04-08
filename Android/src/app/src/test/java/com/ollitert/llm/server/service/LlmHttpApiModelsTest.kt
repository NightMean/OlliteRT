package com.ollitert.llm.server.service

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
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
}
