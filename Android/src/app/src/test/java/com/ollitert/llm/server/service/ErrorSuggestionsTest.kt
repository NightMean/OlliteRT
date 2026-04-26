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

import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ErrorCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LlmHttpErrorSuggestionsTest {

  // ── suggestionResId() — verified error kinds return non-null resource IDs ──

  @Test
  fun `suggestionResId CONTEXT_OVERFLOW returns compaction hint resource`() {
    assertEquals(R.string.suggestion_context_overflow, ErrorSuggestions.suggestionResId(ErrorKind.CONTEXT_OVERFLOW))
  }

  @Test
  fun `suggestionResId TIMEOUT returns non-null`() {
    assertNotNull(ErrorSuggestions.suggestionResId(ErrorKind.TIMEOUT))
  }

  @Test
  fun `suggestionResId MODEL_NOT_FOUND returns Models screen resource`() {
    assertEquals(R.string.suggestion_model_not_found, ErrorSuggestions.suggestionResId(ErrorKind.MODEL_NOT_FOUND))
  }

  @Test
  fun `suggestionResId PORT_BIND_FAILURE returns port resource`() {
    assertEquals(R.string.suggestion_port_bind_failure, ErrorSuggestions.suggestionResId(ErrorKind.PORT_BIND_FAILURE))
  }

  @Test
  fun `suggestionResId IMAGE_DECODE_FAILED returns base64 resource`() {
    assertEquals(R.string.suggestion_image_decode_failed, ErrorSuggestions.suggestionResId(ErrorKind.IMAGE_DECODE_FAILED))
  }

  @Test
  fun `suggestionResId OOM returns memory resource`() {
    assertEquals(R.string.suggestion_oom, ErrorSuggestions.suggestionResId(ErrorKind.OOM))
  }

  @Test
  fun `suggestionResId MODEL_INSTANCE_NULL returns not loaded resource`() {
    assertEquals(R.string.suggestion_model_instance_null, ErrorSuggestions.suggestionResId(ErrorKind.MODEL_INSTANCE_NULL))
  }

  @Test
  fun `suggestionResId MODEL_FILES_MISSING returns re-download resource`() {
    assertEquals(R.string.suggestion_model_files_missing, ErrorSuggestions.suggestionResId(ErrorKind.MODEL_FILES_MISSING))
  }

  // ── suggestionResId() — unknown kinds return null ──

  @Test
  fun `suggestionResId UNKNOWN_LITERT returns null`() {
    assertNull(ErrorSuggestions.suggestionResId(ErrorKind.UNKNOWN_LITERT))
  }

  @Test
  fun `suggestionResId UNKNOWN returns null`() {
    assertNull(ErrorSuggestions.suggestionResId(ErrorKind.UNKNOWN))
  }

  // ── classifyFromString() — pattern matching ──

  @Test
  fun `classifyFromString detects context overflow with greater-than-or-equal`() {
    assertEquals(ErrorKind.CONTEXT_OVERFLOW, ErrorSuggestions.classifyFromString("6579 >= 4000"))
  }

  @Test
  fun `classifyFromString detects context overflow with too long`() {
    assertEquals(ErrorKind.CONTEXT_OVERFLOW, ErrorSuggestions.classifyFromString("Input is too long for context window"))
  }

  @Test
  fun `classifyFromString detects context overflow with exceed`() {
    assertEquals(ErrorKind.CONTEXT_OVERFLOW, ErrorSuggestions.classifyFromString("Tokens exceed maximum allowed"))
  }

  @Test
  fun `classifyFromString detects context overflow with too many tokens`() {
    assertEquals(ErrorKind.CONTEXT_OVERFLOW, ErrorSuggestions.classifyFromString("Error: too many tokens in input"))
  }

  @Test
  fun `classifyFromString detects exact timeout`() {
    assertEquals(ErrorKind.TIMEOUT, ErrorSuggestions.classifyFromString("timeout"))
  }

  @Test
  fun `classifyFromString detects timeout case insensitive`() {
    assertEquals(ErrorKind.TIMEOUT, ErrorSuggestions.classifyFromString("TIMEOUT"))
  }

  @Test
  fun `classifyFromString detects model not initialized`() {
    assertEquals(ErrorKind.MODEL_INSTANCE_NULL, ErrorSuggestions.classifyFromString("LlmModelInstance is not initialized."))
  }

  @Test
  fun `classifyFromString detects OOM from class name`() {
    assertEquals(ErrorKind.OOM, ErrorSuggestions.classifyFromString("java.lang.OutOfMemoryError"))
  }

  @Test
  fun `classifyFromString detects OOM from message`() {
    assertEquals(ErrorKind.OOM, ErrorSuggestions.classifyFromString("Out of memory allocating bitmap"))
  }

  @Test
  fun `classifyFromString returns UNKNOWN_LITERT for unrecognized error`() {
    assertEquals(ErrorKind.UNKNOWN_LITERT, ErrorSuggestions.classifyFromString("Some random LiteRT error"))
  }

  @Test
  fun `classifyFromString returns UNKNOWN_LITERT for blank string`() {
    assertEquals(ErrorKind.UNKNOWN_LITERT, ErrorSuggestions.classifyFromString(""))
  }

  @Test
  fun `classifyFromString returns UNKNOWN_LITERT for whitespace`() {
    assertEquals(ErrorKind.UNKNOWN_LITERT, ErrorSuggestions.classifyFromString("   "))
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
  fun `openAiErrorType returns invalid_request_error for CONTEXT_OVERFLOW`() {
    assertEquals("invalid_request_error", ErrorSuggestions.openAiErrorType(ErrorKind.CONTEXT_OVERFLOW))
  }

  @Test
  fun `openAiErrorType returns invalid_request_error for IMAGE_DECODE_FAILED`() {
    assertEquals("invalid_request_error", ErrorSuggestions.openAiErrorType(ErrorKind.IMAGE_DECODE_FAILED))
  }

  @Test
  fun `openAiErrorType returns not_found_error for MODEL_NOT_FOUND`() {
    assertEquals("not_found_error", ErrorSuggestions.openAiErrorType(ErrorKind.MODEL_NOT_FOUND))
  }

  @Test
  fun `openAiErrorType returns server_error for TIMEOUT`() {
    assertEquals("server_error", ErrorSuggestions.openAiErrorType(ErrorKind.TIMEOUT))
  }

  @Test
  fun `openAiErrorType returns server_error for OOM`() {
    assertEquals("server_error", ErrorSuggestions.openAiErrorType(ErrorKind.OOM))
  }

  @Test
  fun `openAiErrorType returns server_error for null`() {
    assertEquals("server_error", ErrorSuggestions.openAiErrorType(null))
  }

  // ── openAiErrorCode() ──

  @Test
  fun `openAiErrorCode returns context_length_exceeded for CONTEXT_OVERFLOW`() {
    assertEquals("context_length_exceeded", ErrorSuggestions.openAiErrorCode(ErrorKind.CONTEXT_OVERFLOW))
  }

  @Test
  fun `openAiErrorCode returns null for other kinds`() {
    assertNull(ErrorSuggestions.openAiErrorCode(ErrorKind.TIMEOUT))
    assertNull(ErrorSuggestions.openAiErrorCode(ErrorKind.OOM))
    assertNull(ErrorSuggestions.openAiErrorCode(null))
  }
}
