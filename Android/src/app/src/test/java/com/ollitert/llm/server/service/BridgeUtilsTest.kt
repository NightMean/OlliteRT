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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpBridgeUtilsTest {

  @Test
  fun normalizesModelIdsAcrossFormats() {
    assertEquals("gemma31bit", BridgeUtils.normalizeModelKey("Gemma3-1B-IT"))
    assertEquals("gemma31bit", BridgeUtils.normalizeModelKey("gemma3_1b_it"))
    assertEquals("gemma31bit", BridgeUtils.normalizeModelKey("GEMMA3 1B IT"))
  }

  @Test
  fun bearerAuthIsPermissiveWhenTokenIsBlank() {
    assertTrue(BridgeUtils.isBearerAuthorized("", null))
    assertTrue(BridgeUtils.isBearerAuthorized("", "Bearer anything"))
  }

  @Test
  fun bearerAuthRequiresExactMatchWhenTokenExists() {
    assertTrue(BridgeUtils.isBearerAuthorized("secret", "Bearer secret"))
    assertFalse(BridgeUtils.isBearerAuthorized("secret", null))
    assertFalse(BridgeUtils.isBearerAuthorized("secret", "Bearer wrong"))
    assertFalse(BridgeUtils.isBearerAuthorized("secret", "secret"))
  }

  @Test
  fun resolveRequestedModelIdFallsBackToLocal() {
    assertEquals("local", BridgeUtils.resolveRequestedModelId(null))
    assertEquals("local", BridgeUtils.resolveRequestedModelId(""))
    assertEquals("local", BridgeUtils.resolveRequestedModelId("   "))
  }

  @Test
  fun resolveRequestedModelIdTrimsUserInput() {
    assertEquals("gemma3", BridgeUtils.resolveRequestedModelId("  gemma3  "))
  }

  @Test
  fun escapeSseTextEscapesBackslashesQuotesAndNewlines() {
    assertEquals("hello", BridgeUtils.escapeSseText("hello"))
    assertEquals("line1\\nline2", BridgeUtils.escapeSseText("line1\nline2"))
    assertEquals("say \\\"hi\\\"", BridgeUtils.escapeSseText("say \"hi\""))
    assertEquals("back\\\\slash", BridgeUtils.escapeSseText("back\\slash"))
    assertEquals("line1\\rline2", BridgeUtils.escapeSseText("line1\rline2"))
    assertEquals("line1\\r\\nline2", BridgeUtils.escapeSseText("line1\r\nline2"))
    assertEquals("col1\\tcol2", BridgeUtils.escapeSseText("col1\tcol2"))
  }

  // ── ID generation ──────────────────────────────────────────────────────

  @Test
  fun `generateCompletionId - has cmpl prefix and UUID`() {
    val id = BridgeUtils.generateCompletionId()
    assertTrue(id.startsWith("cmpl-"))
    assertEquals(41, id.length) // "cmpl-" (5) + UUID (36)
  }

  @Test
  fun `generateChatCompletionId - has chatcmpl prefix and UUID`() {
    val id = BridgeUtils.generateChatCompletionId()
    assertTrue(id.startsWith("chatcmpl-"))
    assertEquals(45, id.length) // "chatcmpl-" (9) + UUID (36)
  }

  @Test
  fun `generateResponseId - has resp prefix and UUID`() {
    val id = BridgeUtils.generateResponseId()
    assertTrue(id.startsWith("resp-"))
    assertEquals(41, id.length) // "resp-" (5) + UUID (36)
  }

  @Test
  fun `generateMessageId - has msg prefix and UUID`() {
    val id = BridgeUtils.generateMessageId()
    assertTrue(id.startsWith("msg-"))
    assertEquals(40, id.length) // "msg-" (4) + UUID (36)
  }

  @Test
  fun `generateToolCallId - has call_ prefix and 24 hex chars`() {
    val id = BridgeUtils.generateToolCallId()
    assertTrue(id.startsWith("call_"))
    assertEquals(29, id.length) // "call_" (5) + 24 hex chars
    assertTrue(id.substring(5).all { it in '0'..'9' || it in 'a'..'f' })
  }

  @Test
  fun `generateBearerToken - 32 hex chars without dashes`() {
    val token = BridgeUtils.generateBearerToken()
    assertEquals(32, token.length)
    assertFalse(token.contains("-"))
    assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' })
  }

  @Test
  fun `generated IDs are unique across calls`() {
    val ids = (1..10).map { BridgeUtils.generateChatCompletionId() }.toSet()
    assertEquals(10, ids.size)
  }

  @Test
  fun `epochSeconds - returns current epoch in seconds`() {
    val before = System.currentTimeMillis() / 1000
    val result = BridgeUtils.epochSeconds()
    val after = System.currentTimeMillis() / 1000
    // Allow 1-second drift to avoid flakiness at second boundaries
    assertTrue(result >= before && result <= after + 1)
  }

  // ── compactBase64DataUris ───────────────────────────────────────────────

  @Test
  fun `compactBase64DataUris - replaces plain base64 png data URI`() {
    // 2000 base64 chars ≈ 1500 decoded bytes → "1.5 kB"
    val payload = "A".repeat(2000)
    val input = """{"url":"data:image/png;base64,$payload"}"""
    val result = BridgeUtils.compactBase64DataUris(input)

    assertTrue("Should contain placeholder", result.contains("PLACEHOLDER"))
    assertTrue("Should show image/png MIME", result.contains("data:image/png;base64,"))
    assertTrue("Should show image category", result.contains("image data"))
    assertFalse("Should not contain raw base64", result.contains(payload))
  }

  @Test
  fun `compactBase64DataUris - replaces JSON-escaped jpeg data URI`() {
    // Simulates JSON-escaped MIME type: image\/jpeg and payload with \/ sequences.
    // This is the actual format sent by OpenAI multimodal clients — JSON serializers
    // escape "/" as "\/" per RFC 8259 §7. In Kotlin raw strings, \/ is literal backslash + slash.
    val payload = """\/9j\/4AAQ""" + "A".repeat(2000)
    val input = """{"url":"data:image\/jpeg;base64,$payload"}"""
    val result = BridgeUtils.compactBase64DataUris(input)

    assertTrue("Should contain placeholder", result.contains("PLACEHOLDER"))
    // MIME type should be cleaned: image\/jpeg → image/jpeg
    assertTrue("Should show clean MIME type", result.contains("data:image/jpeg;base64,"))
    assertTrue("Should show image category", result.contains("image data"))
    assertFalse("Should not contain raw base64", result.contains(payload))
  }

  @Test
  fun `compactBase64DataUris - replaces JSON-escaped png data URI with real prefix`() {
    // Exact format from a real multimodal request: image\/png with standard PNG base64 header.
    // The MIME has a JSON-escaped slash but the base64 payload itself is plain (no \/ sequences).
    val pngHeader = "iVBORw0KGgoAAAANSUhEUgAABDgAAAlgCAIAAADieBCCAAABKGVYSWZNTQAqAAAACAAFAQAAAwAAAAEEOAAAAQEAAwAAAAEJYAAAATEAAgAAABkAAABKh2kABAAAAAEAAABjARIABAAAAAEAAAAAAAAAAEFuZHJvaW"
    val payload = pngHeader + "A".repeat(2000)
    val input = """{"url":"data:image\/png;base64,$payload"}"""
    val result = BridgeUtils.compactBase64DataUris(input)

    assertTrue("Should contain placeholder", result.contains("PLACEHOLDER"))
    assertTrue("Should show clean MIME type", result.contains("data:image/png;base64,"))
    assertTrue("Should show image category", result.contains("image data"))
    assertFalse("Should not contain PNG base64 header", result.contains(pngHeader))
  }

  @Test
  fun `compactBase64DataUris - excludes backslash chars from byte size`() {
    // 1500 real base64 chars + backslashes from JSON escaping.
    // Only real base64 chars (not \ or =) count toward the decoded size.
    val payload = "A".repeat(1500) + "\\".repeat(200)
    val input = "data:image/png;base64,$payload"
    val result = BridgeUtils.compactBase64DataUris(input)

    // 1500 base64 chars → 1500 * 3 / 4 = 1125 bytes = 1.1 kB
    assertTrue("Should show kB size", result.contains("1.1 kB"))
  }

  @Test
  fun `compactBase64DataUris - leaves short base64 unchanged`() {
    // Below 1365 chars threshold (< 1 KB decoded) — should not be replaced
    val shortPayload = "iVBORw0KGgo="
    val input = "data:image/png;base64,$shortPayload"
    val result = BridgeUtils.compactBase64DataUris(input)

    assertEquals("Short payloads should be unchanged", input, result)
  }

  @Test
  fun `compactBase64DataUris - leaves non-image text unchanged`() {
    val input = """{"role":"user","content":"Hello, how are you?"}"""
    val result = BridgeUtils.compactBase64DataUris(input)

    assertEquals("Text without data URIs should be unchanged", input, result)
  }

  @Test
  fun `compactBase64DataUris - replaces multiple data URIs in one body`() {
    val payload1 = "A".repeat(2000)
    val payload2 = "B".repeat(3000)
    val input = """[{"url":"data:image/png;base64,$payload1"},{"url":"data:image/jpeg;base64,$payload2"}]"""
    val result = BridgeUtils.compactBase64DataUris(input)

    assertFalse("Should not contain first payload", result.contains(payload1))
    assertFalse("Should not contain second payload", result.contains(payload2))
    // Both should be replaced with placeholders
    assertEquals("Should have two placeholders", 2,
      Regex("PLACEHOLDER").findAll(result).count())
  }

  @Test
  fun `compactBase64DataUris - handles audio MIME type`() {
    val payload = "A".repeat(2000)
    val input = "data:audio/wav;base64,$payload"
    val result = BridgeUtils.compactBase64DataUris(input)

    assertTrue("Should show audio/wav MIME", result.contains("data:audio/wav;base64,"))
    assertTrue("Should show audio category", result.contains("audio data"))
  }

  @Test
  fun `compactBase64DataUris - shows MB for large payloads`() {
    // ~1.4 million base64 chars ≈ 1 MB decoded
    val payload = "A".repeat(1_400_000)
    val input = "data:image/png;base64,$payload"
    val result = BridgeUtils.compactBase64DataUris(input)

    assertTrue("Should show MB unit", result.contains("MB"))
    assertTrue("Should show image category", result.contains("image data"))
  }

  @Test
  fun `compactBase64DataUris - exact threshold boundary 1365 chars matches`() {
    val payload = "A".repeat(1365)
    val input = "data:image/png;base64,$payload"
    val result = BridgeUtils.compactBase64DataUris(input)

    assertTrue("Exactly 1365 chars should be replaced", result.contains("PLACEHOLDER"))
  }

  @Test
  fun `compactBase64DataUris - below threshold 1364 chars is not replaced`() {
    val payload = "A".repeat(1364)
    val input = "data:image/png;base64,$payload"
    val result = BridgeUtils.compactBase64DataUris(input)

    assertFalse("1364 chars should NOT be replaced", result.contains("PLACEHOLDER"))
    assertTrue("Original payload should remain", result.contains(payload))
  }

  @Test
  fun `compactBase64DataUris - padding chars excluded from byte size`() {
    // 1500 real chars + 100 '=' padding. Only the 1500 real chars contribute to decoded size.
    val payload = "A".repeat(1500) + "=".repeat(100)
    val input = "data:image/png;base64,$payload"
    val result = BridgeUtils.compactBase64DataUris(input)

    // 1500 base64 chars → 1500 * 3 / 4 = 1125 bytes = 1.1 kB (not inflated by padding)
    assertTrue("Padding should not inflate size", result.contains("1.1 kB"))
  }

  @Test
  fun `compactBase64DataUris - empty string returns empty`() {
    assertEquals("", BridgeUtils.compactBase64DataUris(""))
  }

  @Test
  fun `compactBase64DataUris - preserves surrounding JSON context`() {
    val payload = "A".repeat(2000)
    val input = """{"messages":[{"role":"user","content":[{"type":"text","text":"hello"},{"type":"image_url","image_url":{"url":"data:image/png;base64,$payload"}}]}],"model":"gemma"}"""
    val result = BridgeUtils.compactBase64DataUris(input)

    assertTrue("JSON structure before URI preserved", result.contains(""""type":"text","text":"hello"}}""") || result.contains(""""text":"hello""""))
    assertTrue("JSON structure after URI preserved", result.contains(""""model":"gemma""""))
    assertTrue("Placeholder inserted", result.contains("PLACEHOLDER"))
    assertFalse("Base64 removed", result.contains(payload))
  }

  @Test
  fun `compactBase64DataUris - placeholder format is exact`() {
    val payload = "A".repeat(2000)
    val input = "data:image/png;base64,$payload"
    val result = BridgeUtils.compactBase64DataUris(input)

    // Verify exact placeholder structure: data:MIME;base64,▌ PLACEHOLDER — SIZE CATEGORY data ▌
    val expected = "data:image/png;base64,▌ PLACEHOLDER — 1.5 kB image data ▌"
    assertEquals("Placeholder format must be exact", expected, result)
  }

  @Test
  fun `compactBase64DataUris - application MIME type uses application category`() {
    val payload = "A".repeat(2000)
    val input = "data:application/pdf;base64,$payload"
    val result = BridgeUtils.compactBase64DataUris(input)

    assertTrue("Should show application category", result.contains("application data"))
    assertTrue("Should preserve MIME", result.contains("data:application/pdf;base64,"))
  }

  @Test
  fun `compactBase64DataUris - SI kB unit at threshold boundary`() {
    // 1368 base64 chars → 1368 * 3/4 = 1026 decoded bytes → "1.0 kB" (SI units, threshold ≥ 1000)
    // This also exceeds the 1365-char compaction threshold so the payload is replaced.
    val payload = "A".repeat(1368)
    val input = "data:image/png;base64,$payload"
    val result = BridgeUtils.compactBase64DataUris(input)

    assertTrue("Should show kB not bytes", result.contains("kB"))
    assertFalse("Should not show B unit", result.contains(" B "))
  }
}
