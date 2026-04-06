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
}
