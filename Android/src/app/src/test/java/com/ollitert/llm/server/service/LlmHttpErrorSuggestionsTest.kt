package com.ollitert.llm.server.service

import com.ollitert.llm.server.common.ErrorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpErrorSuggestionsTest {

  // ── suggest() — verified error kinds return non-null suggestions ──

  @Test
  fun `suggest CONTEXT_OVERFLOW returns compaction hint`() {
    val suggestion = LlmHttpErrorSuggestions.suggest(ErrorKind.CONTEXT_OVERFLOW)
    assertNotNull(suggestion)
    assertTrue(suggestion!!.contains("compaction", ignoreCase = true))
  }

  @Test
  fun `suggest TIMEOUT returns non-null`() {
    assertNotNull(LlmHttpErrorSuggestions.suggest(ErrorKind.TIMEOUT))
  }

  @Test
  fun `suggest MODEL_NOT_FOUND mentions Models screen`() {
    val suggestion = LlmHttpErrorSuggestions.suggest(ErrorKind.MODEL_NOT_FOUND)
    assertNotNull(suggestion)
    assertTrue(suggestion!!.contains("Models screen"))
  }

  @Test
  fun `suggest PORT_BIND_FAILURE mentions port`() {
    val suggestion = LlmHttpErrorSuggestions.suggest(ErrorKind.PORT_BIND_FAILURE)
    assertNotNull(suggestion)
    assertTrue(suggestion!!.contains("port", ignoreCase = true))
  }

  @Test
  fun `suggest IMAGE_DECODE_FAILED mentions base64`() {
    val suggestion = LlmHttpErrorSuggestions.suggest(ErrorKind.IMAGE_DECODE_FAILED)
    assertNotNull(suggestion)
    assertTrue(suggestion!!.contains("base64"))
  }

  @Test
  fun `suggest OOM mentions memory`() {
    val suggestion = LlmHttpErrorSuggestions.suggest(ErrorKind.OOM)
    assertNotNull(suggestion)
    assertTrue(suggestion!!.contains("memory"))
  }

  @Test
  fun `suggest MODEL_INSTANCE_NULL mentions not loaded`() {
    val suggestion = LlmHttpErrorSuggestions.suggest(ErrorKind.MODEL_INSTANCE_NULL)
    assertNotNull(suggestion)
    assertTrue(suggestion!!.contains("not loaded"))
  }

  @Test
  fun `suggest MODEL_FILES_MISSING mentions re-download`() {
    val suggestion = LlmHttpErrorSuggestions.suggest(ErrorKind.MODEL_FILES_MISSING)
    assertNotNull(suggestion)
    assertTrue(suggestion!!.contains("re-download", ignoreCase = true))
  }

  // ── suggest() — unknown kinds return null ──

  @Test
  fun `suggest UNKNOWN_LITERT returns null`() {
    assertNull(LlmHttpErrorSuggestions.suggest(ErrorKind.UNKNOWN_LITERT))
  }

  @Test
  fun `suggest UNKNOWN returns null`() {
    assertNull(LlmHttpErrorSuggestions.suggest(ErrorKind.UNKNOWN))
  }

  // ── classifyFromString() — pattern matching ──

  @Test
  fun `classifyFromString detects context overflow with greater-than-or-equal`() {
    assertEquals(ErrorKind.CONTEXT_OVERFLOW, LlmHttpErrorSuggestions.classifyFromString("6579 >= 4000"))
  }

  @Test
  fun `classifyFromString detects context overflow with too long`() {
    assertEquals(ErrorKind.CONTEXT_OVERFLOW, LlmHttpErrorSuggestions.classifyFromString("Input is too long for context window"))
  }

  @Test
  fun `classifyFromString detects context overflow with exceed`() {
    assertEquals(ErrorKind.CONTEXT_OVERFLOW, LlmHttpErrorSuggestions.classifyFromString("Tokens exceed maximum allowed"))
  }

  @Test
  fun `classifyFromString detects context overflow with too many tokens`() {
    assertEquals(ErrorKind.CONTEXT_OVERFLOW, LlmHttpErrorSuggestions.classifyFromString("Error: too many tokens in input"))
  }

  @Test
  fun `classifyFromString detects exact timeout`() {
    assertEquals(ErrorKind.TIMEOUT, LlmHttpErrorSuggestions.classifyFromString("timeout"))
  }

  @Test
  fun `classifyFromString detects timeout case insensitive`() {
    assertEquals(ErrorKind.TIMEOUT, LlmHttpErrorSuggestions.classifyFromString("TIMEOUT"))
  }

  @Test
  fun `classifyFromString detects model not initialized`() {
    assertEquals(ErrorKind.MODEL_INSTANCE_NULL, LlmHttpErrorSuggestions.classifyFromString("LlmModelInstance is not initialized."))
  }

  @Test
  fun `classifyFromString detects OOM from class name`() {
    assertEquals(ErrorKind.OOM, LlmHttpErrorSuggestions.classifyFromString("java.lang.OutOfMemoryError"))
  }

  @Test
  fun `classifyFromString detects OOM from message`() {
    assertEquals(ErrorKind.OOM, LlmHttpErrorSuggestions.classifyFromString("Out of memory allocating bitmap"))
  }

  @Test
  fun `classifyFromString returns UNKNOWN_LITERT for unrecognized error`() {
    assertEquals(ErrorKind.UNKNOWN_LITERT, LlmHttpErrorSuggestions.classifyFromString("Some random LiteRT error"))
  }

  @Test
  fun `classifyFromString returns UNKNOWN_LITERT for blank string`() {
    assertEquals(ErrorKind.UNKNOWN_LITERT, LlmHttpErrorSuggestions.classifyFromString(""))
  }

  @Test
  fun `classifyFromString returns UNKNOWN_LITERT for whitespace`() {
    assertEquals(ErrorKind.UNKNOWN_LITERT, LlmHttpErrorSuggestions.classifyFromString("   "))
  }

  // ── ErrorKind categories ──

  @Test
  fun `ErrorKind categories are correctly assigned`() {
    assertEquals(ErrorCategory.INFERENCE, ErrorKind.CONTEXT_OVERFLOW.category)
    assertEquals(ErrorCategory.INFERENCE, ErrorKind.TIMEOUT.category)
    assertEquals(ErrorCategory.MODEL_LOAD, ErrorKind.MODEL_NOT_FOUND.category)
    assertEquals(ErrorCategory.MODEL_LOAD, ErrorKind.MODEL_FILES_MISSING.category)
    assertEquals(ErrorCategory.NETWORK, ErrorKind.PORT_BIND_FAILURE.category)
    assertEquals(ErrorCategory.INFERENCE, ErrorKind.MODEL_INSTANCE_NULL.category)
    assertEquals(ErrorCategory.INFERENCE, ErrorKind.IMAGE_DECODE_FAILED.category)
    assertEquals(ErrorCategory.SYSTEM, ErrorKind.OOM.category)
    assertEquals(ErrorCategory.INFERENCE, ErrorKind.UNKNOWN_LITERT.category)
    assertEquals(ErrorCategory.SYSTEM, ErrorKind.UNKNOWN.category)
  }

  // ── openAiErrorType() ──

  @Test
  fun `openAiErrorType returns server_error for MODEL_LOAD`() {
    assertEquals("server_error", LlmHttpErrorSuggestions.openAiErrorType(ErrorCategory.MODEL_LOAD))
  }

  @Test
  fun `openAiErrorType returns invalid_request_error for NETWORK`() {
    assertEquals("invalid_request_error", LlmHttpErrorSuggestions.openAiErrorType(ErrorCategory.NETWORK))
  }

  @Test
  fun `openAiErrorType returns authentication_error for auth failures`() {
    assertEquals("authentication_error", LlmHttpErrorSuggestions.openAiErrorType(ErrorCategory.NETWORK, "unauthorized"))
  }

  @Test
  fun `openAiErrorType returns server_error for SYSTEM`() {
    assertEquals("server_error", LlmHttpErrorSuggestions.openAiErrorType(ErrorCategory.SYSTEM))
  }

  @Test
  fun `openAiErrorType returns server_error for INFERENCE`() {
    assertEquals("server_error", LlmHttpErrorSuggestions.openAiErrorType(ErrorCategory.INFERENCE))
  }
}
