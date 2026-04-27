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

import com.ollitert.llm.server.service.ModelLifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpResponseHelpersTest {

  // ── httpOkJson ──────────────────────────────────────────────────────────

  @Test
  fun httpOkJsonReturns200WithBody() {
    val resp = httpOkJson("""{"ok":true}""")
    assertEquals(200, resp.statusCode)
    assertEquals("""{"ok":true}""", resp.body)
    assertTrue(resp.extraHeaders.isEmpty())
  }

  // ── httpBadRequest ──────────────────────────────────────────────────────

  @Test
  fun httpBadRequestReturns400() {
    val resp = httpBadRequest("missing field")
    assertEquals(400, resp.statusCode)
    assertTrue(resp.body.contains("missing field"))
  }

  @Test
  fun httpBadRequestBodyIsValidJsonError() {
    val resp = httpBadRequest("bad input")
    assertTrue(resp.body.contains("\"error\""))
    assertTrue(resp.body.contains("\"message\""))
    assertTrue(resp.body.contains("\"param\":null"))
  }

  // ── httpNotFound ────────────────────────────────────────────────────────

  @Test
  fun httpNotFoundReturns404WithDefault() {
    val resp = httpNotFound()
    assertEquals(404, resp.statusCode)
    assertTrue(resp.body.contains("not_found"))
  }

  @Test
  fun httpNotFoundCustomMessage() {
    val resp = httpNotFound("model not found")
    assertEquals(404, resp.statusCode)
    assertTrue(resp.body.contains("model not found"))
  }

  // ── httpUnauthorized ────────────────────────────────────────────────────

  @Test
  fun httpUnauthorizedReturns401WithBearerHeader() {
    val resp = httpUnauthorized("invalid token")
    assertEquals(401, resp.statusCode)
    assertEquals("Bearer", resp.extraHeaders["WWW-Authenticate"])
    assertTrue(resp.body.contains("invalid token"))
  }

  // ── httpMethodNotAllowed ────────────────────────────────────────────────

  @Test
  fun httpMethodNotAllowedReturns405() {
    val resp = httpMethodNotAllowed()
    assertEquals(405, resp.statusCode)
    assertTrue(resp.body.contains("method_not_allowed"))
  }

  // ── httpPayloadTooLarge ─────────────────────────────────────────────────

  @Test
  fun httpPayloadTooLargeReturns413() {
    val resp = httpPayloadTooLarge("file too big")
    assertEquals(413, resp.statusCode)
    assertTrue(resp.body.contains("file too big"))
  }

  // ── httpInternalError ───────────────────────────────────────────────────

  @Test
  fun httpInternalErrorReturns500() {
    val resp = httpInternalError("inference failed")
    assertEquals(500, resp.statusCode)
    assertTrue(resp.body.contains("inference failed"))
  }

  @Test
  fun httpInternalErrorWithSuggestion() {
    val resp = httpInternalError("context overflow", suggestion = "Try a shorter prompt")
    assertTrue(resp.body.contains("context overflow"))
    assertTrue(resp.body.contains("Try a shorter prompt"))
    assertTrue(resp.body.contains("\"suggestion\""))
  }

  @Test
  fun httpInternalErrorWithKind() {
    val resp = httpInternalError("overflow", kind = ErrorKind.CONTEXT_OVERFLOW)
    assertTrue(resp.body.contains("\"type\":\"invalid_request_error\""))
    assertTrue(resp.body.contains("\"code\":\"context_length_exceeded\""))
  }

  @Test
  fun httpInternalErrorWithoutSuggestionOmitsSuggestionField() {
    val resp = httpInternalError("generic error")
    assertFalse(resp.body.contains("\"suggestion\""))
  }

  // ── httpJsonError ───────────────────────────────────────────────────────

  @Test
  fun httpJsonErrorCustomStatusCode() {
    val resp = httpJsonError(429, "rate limited")
    assertEquals(429, resp.statusCode)
    assertTrue(resp.body.contains("rate limited"))
  }

  // ── HttpResponse sealed class variants ──────────────────────────────────

  @Test
  fun jsonResponseDefaultEmptyHeaders() {
    val resp = HttpResponse.Json(200, "{}")
    assertTrue(resp.extraHeaders.isEmpty())
  }

  @Test
  fun jsonResponseCustomHeaders() {
    val resp = HttpResponse.Json(200, "{}", mapOf("X-Custom" to "value"))
    assertEquals("value", resp.extraHeaders["X-Custom"])
  }

  @Test
  fun plainTextResponse() {
    val resp = HttpResponse.PlainText(200, "text/plain", "hello")
    assertEquals(200, resp.statusCode)
    assertEquals("text/plain", resp.contentType)
    assertEquals("hello", resp.body)
  }

  @Test
  fun binaryResponse() {
    val bytes = byteArrayOf(1, 2, 3)
    val resp = HttpResponse.Binary(200, "application/octet-stream", bytes)
    assertEquals(200, resp.statusCode)
    assertEquals(3, resp.bytes.size)
  }

  @Test
  fun sseResponseHoldsWriter() {
    val resp = HttpResponse.Sse { }
    assertEquals(HttpResponse.Sse::class, resp::class)
  }

  // ── ModelSelection.Error.toHttpResponse ──────────────────────────────────

  @Test
  fun modelSelectionErrorToHttpResponse() {
    val error = ModelLifecycle.ModelSelection.Error(503, "No model loaded")
    val resp = error.toHttpResponse()
    assertEquals(503, resp.statusCode)
    assertTrue(resp.body.contains("No model loaded"))
    assertTrue(resp.body.contains("\"error\""))
    assertTrue(resp.extraHeaders.isEmpty())
  }

  @Test
  fun modelSelectionErrorWithRetryAfter() {
    val error = ModelLifecycle.ModelSelection.Error(503, "Model reloading", retryAfterSeconds = 30)
    val resp = error.toHttpResponse()
    assertEquals(503, resp.statusCode)
    assertTrue(resp.body.contains("Model reloading"))
    assertEquals("30", resp.extraHeaders["Retry-After"])
  }

  @Test
  fun modelSelectionErrorWithoutRetryAfterOmitsHeader() {
    val error = ModelLifecycle.ModelSelection.Error(400, "Wrong model", retryAfterSeconds = null)
    val resp = error.toHttpResponse()
    assertEquals(400, resp.statusCode)
    assertFalse(resp.extraHeaders.containsKey("Retry-After"))
  }
}
