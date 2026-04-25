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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpRouteResolverTest {
  @Test
  fun resolvesKnownRoutesWithExpectedAuth() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.PING, requiresAuth = false),
      LlmHttpRouteResolver.resolve("GET", "/ping"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.MODELS, requiresAuth = true),
      LlmHttpRouteResolver.resolve("GET", "/v1/models"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.GENERATE, requiresAuth = true),
      LlmHttpRouteResolver.resolve("POST", "/generate"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.CHAT_COMPLETIONS, requiresAuth = true),
      LlmHttpRouteResolver.resolve("POST", "/v1/chat/completions"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.RESPONSES, requiresAuth = true),
      LlmHttpRouteResolver.resolve("POST", "/v1/responses"),
    )
  }

  @Test
  fun resolvesServerInfoRoutes() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.SERVER_INFO, requiresAuth = false),
      LlmHttpRouteResolver.resolve("GET", "/"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.SERVER_INFO, requiresAuth = false),
      LlmHttpRouteResolver.resolve("GET", "/v1"),
    )
  }

  @Test
  fun returnsNullForUnknownRouteOrWrongMethod() {
    assertNull(LlmHttpRouteResolver.resolve("GET", "/generate"))
    assertNull(LlmHttpRouteResolver.resolve("POST", "/v1/models"))
    assertNull(LlmHttpRouteResolver.resolve("GET", "/unknown"))
  }

  @Test
  fun getPostAndOptionsAreSupported() {
    assertTrue(LlmHttpRouteResolver.isSupportedMethod("GET"))
    assertTrue(LlmHttpRouteResolver.isSupportedMethod("POST"))
    assertTrue(LlmHttpRouteResolver.isSupportedMethod("OPTIONS"))
    assertFalse(LlmHttpRouteResolver.isSupportedMethod("DELETE"))
    assertFalse(LlmHttpRouteResolver.isSupportedMethod("PUT"))
    assertFalse(LlmHttpRouteResolver.isSupportedMethod("PATCH"))
  }

  // ── Additional routes───────────────────────────────────────────────────

  @Test
  fun resolvesHealthRoutes() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.HEALTH, requiresAuth = false),
      LlmHttpRouteResolver.resolve("GET", "/health"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.HEALTH, requiresAuth = false),
      LlmHttpRouteResolver.resolve("GET", "/v1/health"),
    )
  }

  @Test
  fun resolvesCompletionsRoute() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.COMPLETIONS, requiresAuth = true),
      LlmHttpRouteResolver.resolve("POST", "/v1/completions"),
    )
  }

  @Test
  fun resolvesModelDetailRoute() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.MODEL_DETAIL, requiresAuth = true),
      LlmHttpRouteResolver.resolve("GET", "/v1/models/Gemma-3n-E4B"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.MODEL_DETAIL, requiresAuth = true),
      LlmHttpRouteResolver.resolve("GET", "/v1/models/some-model-name"),
    )
  }

  @Test
  fun modelListRouteStillWorks() {
    // /v1/models (without trailing path) should still resolve to MODELS, not MODEL_DETAIL
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.MODELS, requiresAuth = true),
      LlmHttpRouteResolver.resolve("GET", "/v1/models"),
    )
  }

  // ── Unsupported endpoint messages────────────────────────────────

  @Test
  fun unsupportedEndpointsReturnDescriptiveMessages() {
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/embeddings"))
    assertNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/audio/transcriptions"))
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/images/generations"))
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/fine_tuning/jobs"))
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/fine-tuning/jobs"))
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/files"))
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/batches"))
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/assistants"))
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/threads"))
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/vector_stores"))
  }

  @Test
  fun unsupportedEndpointMessagesAreDescriptive() {
    val msg = LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/embeddings")!!
    assertTrue("Message should explain why", msg.contains("not supported"))
    assertTrue("Message should be specific", msg.contains("inference-only"))
  }

  @Test
  fun resolvesMetricsRoute() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.METRICS, requiresAuth = false),
      LlmHttpRouteResolver.resolve("GET", "/metrics"),
    )
  }

  @Test
  fun resolvesVersionRoute() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.VERSION, requiresAuth = false),
      LlmHttpRouteResolver.resolve("GET", "/api/version"),
    )
  }

  @Test
  fun resolvesServerControlRoutes() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.SERVER_STOP, requiresAuth = true),
      LlmHttpRouteResolver.resolve("POST", "/v1/server/stop"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.SERVER_RELOAD, requiresAuth = true),
      LlmHttpRouteResolver.resolve("POST", "/v1/server/reload"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.SERVER_THINKING, requiresAuth = true),
      LlmHttpRouteResolver.resolve("POST", "/v1/server/thinking"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.SERVER_CONFIG, requiresAuth = true),
      LlmHttpRouteResolver.resolve("POST", "/v1/server/config"),
    )
  }

  @Test
  fun resolvesDebugModelsAlias() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.MODELS, requiresAuth = true),
      LlmHttpRouteResolver.resolve("GET", "/debug/models"),
    )
  }

  @Test
  fun metricsRouteDoesNotRequireAuth() {
    val route = LlmHttpRouteResolver.resolve("GET", "/metrics")
    assertNotNull(route)
    assertFalse("Metrics endpoint should not require auth", route!!.requiresAuth)
  }

  @Test
  fun unknownEndpointsReturnNull() {
    assertNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/chat/completions"))
    assertNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/models"))
    assertNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/some/random/path"))
    assertNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/"))
  }

  @Test
  fun resolvesAudioTranscriptionRoute() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.AUDIO_TRANSCRIPTION, requiresAuth = true),
      LlmHttpRouteResolver.resolve("POST", "/v1/audio/transcriptions"),
    )
  }

  @Test
  fun audioTranscriptionRouteRequiresAuth() {
    val route = LlmHttpRouteResolver.resolve("POST", "/v1/audio/transcriptions")
    assertNotNull(route)
    assertTrue("Audio transcription endpoint should require auth", route!!.requiresAuth)
  }

  @Test
  fun audioSpeechStillReturnsUnsupportedMessage() {
    val msg = LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/audio/speech")
    assertNotNull(msg)
    assertTrue("Message should explain speech synthesis is not supported", msg!!.contains("speech"))
  }
}
