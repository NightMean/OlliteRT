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

package com.ollitert.llm.server.data.db

import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestLogEntityTest {

  private fun fullEntry() = RequestLogEntry(
    id = "log-123-1",
    timestamp = 1700000000000,
    method = "POST",
    path = "/v1/chat/completions",
    requestBody = """{"messages":[{"role":"user","content":"hi"}]}""",
    responseBody = """{"choices":[{"message":{"content":"hello"}}]}""",
    statusCode = 200,
    tokens = 42,
    latencyMs = 1500,
    isStreaming = true,
    modelName = "gemma3-1b",
    clientIp = "192.168.1.100",
    level = LogLevel.INFO,
    isPending = false,
    isThinking = true,
    isCompacted = true,
    compactionDetails = "tools:compacted, truncated:-2 msgs",
    compactedPrompt = "compacted prompt text",
    isCancelled = false,
    cancelledByUser = false,
    partialText = null,
    eventCategory = EventCategory.GENERAL,
    inputTokenEstimate = 258,
    maxContextTokens = 4096,
    isExactTokenCount = false,
    ignoredClientParams = "unsupported_param, another_param",
  )

  // --- Round-trip conversion ---

  @Test
  fun roundTripPreservesAllFields() {
    val original = fullEntry()
    val entity = RequestLogEntity.fromEntry(original)
    val restored = entity.toEntry()

    assertEquals(original.id, restored.id)
    assertEquals(original.timestamp, restored.timestamp)
    assertEquals(original.method, restored.method)
    assertEquals(original.path, restored.path)
    assertEquals(original.requestBody, restored.requestBody)
    assertEquals(original.responseBody, restored.responseBody)
    assertEquals(original.statusCode, restored.statusCode)
    assertEquals(original.tokens, restored.tokens)
    assertEquals(original.latencyMs, restored.latencyMs)
    assertEquals(original.isStreaming, restored.isStreaming)
    assertEquals(original.modelName, restored.modelName)
    assertEquals(original.clientIp, restored.clientIp)
    assertEquals(original.level, restored.level)
    assertEquals(original.isPending, restored.isPending)
    assertEquals(original.isThinking, restored.isThinking)
    assertEquals(original.isCompacted, restored.isCompacted)
    assertEquals(original.compactionDetails, restored.compactionDetails)
    assertEquals(original.compactedPrompt, restored.compactedPrompt)
    assertEquals(original.isCancelled, restored.isCancelled)
    assertEquals(original.cancelledByUser, restored.cancelledByUser)
    assertEquals(original.partialText, restored.partialText)
    assertEquals(original.eventCategory, restored.eventCategory)
    assertEquals(original.inputTokenEstimate, restored.inputTokenEstimate)
    assertEquals(original.maxContextTokens, restored.maxContextTokens)
    assertEquals(original.isExactTokenCount, restored.isExactTokenCount)
    assertEquals(original.ignoredClientParams, restored.ignoredClientParams)
  }

  @Test
  fun roundTripPreservesNullFields() {
    val entry = RequestLogEntry(
      id = "null-test",
      method = "GET",
      path = "/v1/models",
      // All nullable fields left as null/default
    )
    val restored = RequestLogEntity.fromEntry(entry).toEntry()

    assertNull(restored.requestBody)
    assertNull(restored.responseBody)
    assertNull(restored.modelName)
    assertNull(restored.clientIp)
    assertNull(restored.compactionDetails)
    assertNull(restored.compactedPrompt)
    assertNull(restored.partialText)
    assertNull(restored.ignoredClientParams)
  }

  @Test
  fun roundTripPreservesEventEntry() {
    val event = RequestLogEntry(
      id = "event-123",
      method = "EVENT",
      path = "Model loaded: gemma3 (1234ms)",
      requestBody = """{"type":"model_loaded"}""",
      level = LogLevel.WARNING,
      modelName = "gemma3",
      eventCategory = EventCategory.MODEL,
    )
    val restored = RequestLogEntity.fromEntry(event).toEntry()

    assertEquals("EVENT", restored.method)
    assertEquals("Model loaded: gemma3 (1234ms)", restored.path)
    assertEquals(EventCategory.MODEL, restored.eventCategory)
    assertEquals(LogLevel.WARNING, restored.level)
    assertEquals("""{"type":"model_loaded"}""", restored.requestBody)
  }

  @Test
  fun roundTripPreservesCancelledState() {
    val entry = RequestLogEntry(
      id = "cancelled",
      method = "POST",
      path = "/v1/chat/completions",
      isCancelled = true,
      cancelledByUser = true,
      partialText = "partial output before cancel",
    )
    val restored = RequestLogEntity.fromEntry(entry).toEntry()

    assertTrue(restored.isCancelled)
    assertTrue(restored.cancelledByUser)
    assertEquals("partial output before cancel", restored.partialText)
  }

  @Test
  fun roundTripPreservesErrorEntry() {
    val entry = RequestLogEntry(
      id = "error",
      method = "POST",
      path = "/v1/chat/completions",
      statusCode = 500,
      level = LogLevel.ERROR,
      responseBody = """{"error":{"message":"context overflow"}}""",
      inputTokenEstimate = 6579,
      maxContextTokens = 4000,
      isExactTokenCount = true,
    )
    val restored = RequestLogEntity.fromEntry(entry).toEntry()

    assertEquals(500, restored.statusCode)
    assertEquals(LogLevel.ERROR, restored.level)
    assertEquals(6579L, restored.inputTokenEstimate)
    assertEquals(4000L, restored.maxContextTokens)
    assertTrue(restored.isExactTokenCount)
  }

  // --- Indexed columns correctness ---

  @Test
  fun fromEntryMapsIndexedColumnsCorrectly() {
    val entry = fullEntry()
    val entity = RequestLogEntity.fromEntry(entry)

    assertEquals("log-123-1", entity.id)
    assertEquals(1700000000000, entity.timestamp)
    assertEquals("POST", entity.method)
    assertEquals("/v1/chat/completions", entity.path)
    assertEquals(200, entity.statusCode)
    assertEquals("INFO", entity.level)
    assertEquals("gemma3-1b", entity.modelName)
    assertEquals("GENERAL", entity.eventCategory)
    assertEquals(1500L, entity.latencyMs)
    assertTrue(entity.isStreaming)
    assertEquals(258L, entity.inputTokenEstimate)
    assertEquals(4096L, entity.maxContextTokens)
  }

  @Test
  fun fromEntryStoresLevelAndCategoryAsEnumNames() {
    val entry = RequestLogEntry(
      id = "enum-test",
      method = "POST",
      path = "/test",
      level = LogLevel.ERROR,
      eventCategory = EventCategory.SETTINGS,
    )
    val entity = RequestLogEntity.fromEntry(entry)

    assertEquals("ERROR", entity.level)
    assertEquals("SETTINGS", entity.eventCategory)
  }

  // --- JSON extras blob ---

  @Test
  fun extrasColumnContainsNonIndexedFields() {
    val entry = fullEntry()
    val entity = RequestLogEntity.fromEntry(entry)

    // extras should be valid JSON containing the non-indexed fields
    assertTrue("extras should contain requestBody", entity.extras.contains("messages"))
    assertTrue("extras should contain isThinking", entity.extras.contains("isThinking"))
    assertTrue("extras should contain isCompacted", entity.extras.contains("isCompacted"))
    assertTrue("extras should contain compactionDetails", entity.extras.contains("tools:compacted"))
  }

  @Test
  fun extrasJsonWithUnknownKeysDeserializesGracefully() {
    // Simulate a DB row with extra unknown JSON keys (from a future app version)
    val entity = RequestLogEntity(
      id = "future",
      timestamp = 0,
      method = "POST",
      path = "/test",
      statusCode = 200,
      level = "INFO",
      modelName = null,
      eventCategory = "GENERAL",
      latencyMs = 0,
      isStreaming = false,
      inputTokenEstimate = 0,
      maxContextTokens = 0,
      extras = """{"requestBody":"hi","unknownFutureField":"value","anotherNew":42}""",
    )
    val restored = entity.toEntry()

    // Should deserialize without error, unknown keys silently ignored
    assertEquals("hi", restored.requestBody)
    assertEquals("future", restored.id)
  }

  @Test
  fun malformedExtrasJsonFallsBackToDefaults() {
    val entity = RequestLogEntity(
      id = "malformed",
      timestamp = 0,
      method = "POST",
      path = "/test",
      statusCode = 200,
      level = "INFO",
      modelName = null,
      eventCategory = "GENERAL",
      latencyMs = 0,
      isStreaming = false,
      inputTokenEstimate = 0,
      maxContextTokens = 0,
      extras = "this is not valid json",
    )
    val restored = entity.toEntry()

    // Should not throw — falls back to ExtrasJson defaults
    assertNull(restored.requestBody)
    assertNull(restored.responseBody)
    assertFalse(restored.isPending)
    assertFalse(restored.isCancelled)
  }

  @Test
  fun invalidLevelEnumFallsBackToInfo() {
    val entity = RequestLogEntity(
      id = "bad-enum",
      timestamp = 0,
      method = "GET",
      path = "/test",
      statusCode = 200,
      level = "NONEXISTENT_LEVEL",
      modelName = null,
      eventCategory = "GENERAL",
      latencyMs = 0,
      isStreaming = false,
      inputTokenEstimate = 0,
      maxContextTokens = 0,
      extras = "{}",
    )
    val restored = entity.toEntry()
    assertEquals(LogLevel.INFO, restored.level)
  }

  @Test
  fun invalidEventCategoryFallsBackToGeneral() {
    val entity = RequestLogEntity(
      id = "bad-category",
      timestamp = 0,
      method = "EVENT",
      path = "test event",
      statusCode = 200,
      level = "INFO",
      modelName = null,
      eventCategory = "DELETED_CATEGORY",
      latencyMs = 0,
      isStreaming = false,
      inputTokenEstimate = 0,
      maxContextTokens = 0,
      extras = "{}",
    )
    val restored = entity.toEntry()
    assertEquals(EventCategory.GENERAL, restored.eventCategory)
  }

  // --- Edge cases ---

  @Test
  fun roundTripWithLargeRequestBody() {
    val largeBody = """{"messages":[{"role":"user","content":"${"x".repeat(100_000)}"}]}"""
    val entry = RequestLogEntry(
      id = "large",
      method = "POST",
      path = "/v1/chat/completions",
      requestBody = largeBody,
    )
    val restored = RequestLogEntity.fromEntry(entry).toEntry()
    assertEquals(largeBody, restored.requestBody)
  }

  @Test
  fun roundTripWithSpecialCharactersInBody() {
    val body = """{"content":"quotes \" and backslash \\ and newline \n and tab \t and unicode \u00e9"}"""
    val entry = RequestLogEntry(
      id = "special",
      method = "POST",
      path = "/test",
      requestBody = body,
    )
    val restored = RequestLogEntity.fromEntry(entry).toEntry()
    assertEquals(body, restored.requestBody)
  }

  @Test
  fun roundTripWithAllBooleanFieldsTrue() {
    val entry = RequestLogEntry(
      id = "all-true",
      method = "POST",
      path = "/test",
      isStreaming = true,
      isPending = true,
      isThinking = true,
      isCompacted = true,
      isCancelled = true,
      cancelledByUser = true,
      isExactTokenCount = true,
    )
    val restored = RequestLogEntity.fromEntry(entry).toEntry()

    assertTrue(restored.isStreaming)
    assertTrue(restored.isPending)
    assertTrue(restored.isThinking)
    assertTrue(restored.isCompacted)
    assertTrue(restored.isCancelled)
    assertTrue(restored.cancelledByUser)
    assertTrue(restored.isExactTokenCount)
  }
}
