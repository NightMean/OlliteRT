package com.ollitert.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpBridgeUtilsTest {

  @Test
  fun normalizesModelIdsAcrossFormats() {
    assertEquals("gemma31bit", LlmHttpBridgeUtils.normalizeModelKey("Gemma3-1B-IT"))
    assertEquals("gemma31bit", LlmHttpBridgeUtils.normalizeModelKey("gemma3_1b_it"))
    assertEquals("gemma31bit", LlmHttpBridgeUtils.normalizeModelKey("GEMMA3 1B IT"))
  }

  @Test
  fun bearerAuthIsPermissiveWhenTokenIsBlank() {
    assertTrue(LlmHttpBridgeUtils.isBearerAuthorized("", null))
    assertTrue(LlmHttpBridgeUtils.isBearerAuthorized("", "Bearer anything"))
  }

  @Test
  fun bearerAuthRequiresExactMatchWhenTokenExists() {
    assertTrue(LlmHttpBridgeUtils.isBearerAuthorized("secret", "Bearer secret"))
    assertFalse(LlmHttpBridgeUtils.isBearerAuthorized("secret", null))
    assertFalse(LlmHttpBridgeUtils.isBearerAuthorized("secret", "Bearer wrong"))
    assertFalse(LlmHttpBridgeUtils.isBearerAuthorized("secret", "secret"))
  }

  @Test
  fun resolveRequestedModelIdFallsBackToLocal() {
    assertEquals("local", LlmHttpBridgeUtils.resolveRequestedModelId(null))
    assertEquals("local", LlmHttpBridgeUtils.resolveRequestedModelId(""))
    assertEquals("local", LlmHttpBridgeUtils.resolveRequestedModelId("   "))
  }

  @Test
  fun resolveRequestedModelIdTrimsUserInput() {
    assertEquals("gemma3", LlmHttpBridgeUtils.resolveRequestedModelId("  gemma3  "))
  }

  @Test
  fun escapeSseTextEscapesBackslashesQuotesAndNewlines() {
    assertEquals("hello", LlmHttpBridgeUtils.escapeSseText("hello"))
    assertEquals("line1\\nline2", LlmHttpBridgeUtils.escapeSseText("line1\nline2"))
    assertEquals("say \\\"hi\\\"", LlmHttpBridgeUtils.escapeSseText("say \"hi\""))
    assertEquals("back\\\\slash", LlmHttpBridgeUtils.escapeSseText("back\\slash"))
  }

  // ── compactBase64DataUris ───────────────────────────────────────────────

  @Test
  fun `compactBase64DataUris - replaces plain base64 png data URI`() {
    // 2000 base64 chars ≈ 1500 decoded bytes → "1.5 KB"
    val payload = "A".repeat(2000)
    val input = """{"url":"data:image/png;base64,$payload"}"""
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

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
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

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
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

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
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    // 1500 base64 chars → 1500 * 3 / 4 = 1125 bytes = 1.1 KB
    assertTrue("Should show KB size", result.contains("1.1 KB"))
  }

  @Test
  fun `compactBase64DataUris - leaves short base64 unchanged`() {
    // Below 1365 chars threshold (< 1 KB decoded) — should not be replaced
    val shortPayload = "iVBORw0KGgo="
    val input = "data:image/png;base64,$shortPayload"
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    assertEquals("Short payloads should be unchanged", input, result)
  }

  @Test
  fun `compactBase64DataUris - leaves non-image text unchanged`() {
    val input = """{"role":"user","content":"Hello, how are you?"}"""
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    assertEquals("Text without data URIs should be unchanged", input, result)
  }

  @Test
  fun `compactBase64DataUris - replaces multiple data URIs in one body`() {
    val payload1 = "A".repeat(2000)
    val payload2 = "B".repeat(3000)
    val input = """[{"url":"data:image/png;base64,$payload1"},{"url":"data:image/jpeg;base64,$payload2"}]"""
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

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
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    assertTrue("Should show audio/wav MIME", result.contains("data:audio/wav;base64,"))
    assertTrue("Should show audio category", result.contains("audio data"))
  }

  @Test
  fun `compactBase64DataUris - shows MB for large payloads`() {
    // ~1.4 million base64 chars ≈ 1 MB decoded
    val payload = "A".repeat(1_400_000)
    val input = "data:image/png;base64,$payload"
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    assertTrue("Should show MB unit", result.contains("MB"))
    assertTrue("Should show image category", result.contains("image data"))
  }

  @Test
  fun `compactBase64DataUris - exact threshold boundary 1365 chars matches`() {
    val payload = "A".repeat(1365)
    val input = "data:image/png;base64,$payload"
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    assertTrue("Exactly 1365 chars should be replaced", result.contains("PLACEHOLDER"))
  }

  @Test
  fun `compactBase64DataUris - below threshold 1364 chars is not replaced`() {
    val payload = "A".repeat(1364)
    val input = "data:image/png;base64,$payload"
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    assertFalse("1364 chars should NOT be replaced", result.contains("PLACEHOLDER"))
    assertTrue("Original payload should remain", result.contains(payload))
  }

  @Test
  fun `compactBase64DataUris - padding chars excluded from byte size`() {
    // 1500 real chars + 100 '=' padding. Only the 1500 real chars contribute to decoded size.
    val payload = "A".repeat(1500) + "=".repeat(100)
    val input = "data:image/png;base64,$payload"
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    // 1500 base64 chars → 1500 * 3 / 4 = 1125 bytes = 1.1 KB (not inflated by padding)
    assertTrue("Padding should not inflate size", result.contains("1.1 KB"))
  }

  @Test
  fun `compactBase64DataUris - empty string returns empty`() {
    assertEquals("", LlmHttpBridgeUtils.compactBase64DataUris(""))
  }

  @Test
  fun `compactBase64DataUris - preserves surrounding JSON context`() {
    val payload = "A".repeat(2000)
    val input = """{"messages":[{"role":"user","content":[{"type":"text","text":"hello"},{"type":"image_url","image_url":{"url":"data:image/png;base64,$payload"}}]}],"model":"gemma"}"""
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    assertTrue("JSON structure before URI preserved", result.contains(""""type":"text","text":"hello"}}""") || result.contains(""""text":"hello""""))
    assertTrue("JSON structure after URI preserved", result.contains(""""model":"gemma""""))
    assertTrue("Placeholder inserted", result.contains("PLACEHOLDER"))
    assertFalse("Base64 removed", result.contains(payload))
  }

  @Test
  fun `compactBase64DataUris - placeholder format is exact`() {
    val payload = "A".repeat(2000)
    val input = "data:image/png;base64,$payload"
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    // Verify exact placeholder structure: data:MIME;base64,▌ PLACEHOLDER — SIZE CATEGORY data ▌
    val expected = "data:image/png;base64,▌ PLACEHOLDER — 1.5 KB image data ▌"
    assertEquals("Placeholder format must be exact", expected, result)
  }

  @Test
  fun `compactBase64DataUris - application MIME type uses application category`() {
    val payload = "A".repeat(2000)
    val input = "data:application/pdf;base64,$payload"
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    assertTrue("Should show application category", result.contains("application data"))
    assertTrue("Should preserve MIME", result.contains("data:application/pdf;base64,"))
  }

  @Test
  fun `compactBase64DataUris - formatByteSize boundary at exactly 1024 bytes`() {
    // 1024 bytes = 1365.33 base64 chars → use 1368 chars (divisible by 4) to get exactly 1026 bytes
    // which displays as "1.0 KB"
    val payload = "A".repeat(1368)
    val input = "data:image/png;base64,$payload"
    val result = LlmHttpBridgeUtils.compactBase64DataUris(input)

    assertTrue("Should show KB not bytes", result.contains("KB"))
    assertFalse("Should not show B unit", result.contains(" B "))
  }
}
