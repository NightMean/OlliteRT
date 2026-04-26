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
      Route(LlmHttpRouteHandler.PING, requiresAuth = false),
      RouteResolver.resolve("GET", "/ping"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.MODELS, requiresAuth = true),
      RouteResolver.resolve("GET", "/v1/models"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.GENERATE, requiresAuth = true),
      RouteResolver.resolve("POST", "/generate"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.CHAT_COMPLETIONS, requiresAuth = true),
      RouteResolver.resolve("POST", "/v1/chat/completions"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.RESPONSES, requiresAuth = true),
      RouteResolver.resolve("POST", "/v1/responses"),
    )
  }

  @Test
  fun resolvesServerInfoRoutes() {
    assertEquals(
      Route(LlmHttpRouteHandler.SERVER_INFO, requiresAuth = false),
      RouteResolver.resolve("GET", "/"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.SERVER_INFO, requiresAuth = false),
      RouteResolver.resolve("GET", "/v1"),
    )
  }

  @Test
  fun returnsNullForUnknownRouteOrWrongMethod() {
    assertNull(RouteResolver.resolve("GET", "/generate"))
    assertNull(RouteResolver.resolve("POST", "/v1/models"))
    assertNull(RouteResolver.resolve("GET", "/unknown"))
  }

  @Test
  fun getPostAndOptionsAreSupported() {
    assertTrue(RouteResolver.isSupportedMethod("GET"))
    assertTrue(RouteResolver.isSupportedMethod("POST"))
    assertTrue(RouteResolver.isSupportedMethod("OPTIONS"))
    assertFalse(RouteResolver.isSupportedMethod("DELETE"))
    assertFalse(RouteResolver.isSupportedMethod("PUT"))
    assertFalse(RouteResolver.isSupportedMethod("PATCH"))
  }

  // ── Additional routes───────────────────────────────────────────────────

  @Test
  fun resolvesHealthRoutes() {
    assertEquals(
      Route(LlmHttpRouteHandler.HEALTH, requiresAuth = false),
      RouteResolver.resolve("GET", "/health"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.HEALTH, requiresAuth = false),
      RouteResolver.resolve("GET", "/v1/health"),
    )
  }

  @Test
  fun resolvesCompletionsRoute() {
    assertEquals(
      Route(LlmHttpRouteHandler.COMPLETIONS, requiresAuth = true),
      RouteResolver.resolve("POST", "/v1/completions"),
    )
  }

  @Test
  fun resolvesModelDetailRoute() {
    assertEquals(
      Route(LlmHttpRouteHandler.MODEL_DETAIL, requiresAuth = true),
      RouteResolver.resolve("GET", "/v1/models/Gemma-3n-E4B"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.MODEL_DETAIL, requiresAuth = true),
      RouteResolver.resolve("GET", "/v1/models/some-model-name"),
    )
  }

  @Test
  fun modelListRouteStillWorks() {
    // /v1/models (without trailing path) should still resolve to MODELS, not MODEL_DETAIL
    assertEquals(
      Route(LlmHttpRouteHandler.MODELS, requiresAuth = true),
      RouteResolver.resolve("GET", "/v1/models"),
    )
  }

  // ── Unsupported endpoint messages────────────────────────────────

  @Test
  fun unsupportedEndpointsReturnDescriptiveMessages() {
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/embeddings"))
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/v1/audio/transcriptions"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/images/generations"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/fine_tuning/jobs"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/fine-tuning/jobs"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/files"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/batches"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/assistants"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/threads"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/vector_stores"))
  }

  @Test
  fun unsupportedEndpointMessagesAreDescriptive() {
    val msg = RouteResolver.getUnsupportedEndpointMessage("/v1/embeddings")!!
    assertTrue("Message should explain why", msg.contains("not supported"))
    assertTrue("Message should be specific", msg.contains("inference-only"))
  }

  @Test
  fun resolvesMetricsRoute() {
    assertEquals(
      Route(LlmHttpRouteHandler.METRICS, requiresAuth = false),
      RouteResolver.resolve("GET", "/metrics"),
    )
  }

  @Test
  fun resolvesVersionRoute() {
    assertEquals(
      Route(LlmHttpRouteHandler.VERSION, requiresAuth = false),
      RouteResolver.resolve("GET", "/api/version"),
    )
  }

  @Test
  fun resolvesServerControlRoutes() {
    assertEquals(
      Route(LlmHttpRouteHandler.SERVER_STOP, requiresAuth = true),
      RouteResolver.resolve("POST", "/v1/server/stop"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.SERVER_RELOAD, requiresAuth = true),
      RouteResolver.resolve("POST", "/v1/server/reload"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.SERVER_THINKING, requiresAuth = true),
      RouteResolver.resolve("POST", "/v1/server/thinking"),
    )
    assertEquals(
      Route(LlmHttpRouteHandler.SERVER_CONFIG, requiresAuth = true),
      RouteResolver.resolve("POST", "/v1/server/config"),
    )
  }

  @Test
  fun resolvesDebugModelsAlias() {
    assertEquals(
      Route(LlmHttpRouteHandler.MODELS, requiresAuth = true),
      RouteResolver.resolve("GET", "/debug/models"),
    )
  }

  @Test
  fun metricsRouteDoesNotRequireAuth() {
    val route = RouteResolver.resolve("GET", "/metrics")
    assertNotNull(route)
    assertFalse("Metrics endpoint should not require auth", route!!.requiresAuth)
  }

  @Test
  fun unknownEndpointsReturnNull() {
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/v1/chat/completions"))
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/v1/models"))
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/some/random/path"))
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/"))
  }

  @Test
  fun resolvesAudioTranscriptionRoute() {
    assertEquals(
      Route(LlmHttpRouteHandler.AUDIO_TRANSCRIPTION, requiresAuth = true),
      RouteResolver.resolve("POST", "/v1/audio/transcriptions"),
    )
  }

  @Test
  fun audioTranscriptionRouteRequiresAuth() {
    val route = RouteResolver.resolve("POST", "/v1/audio/transcriptions")
    assertNotNull(route)
    assertTrue("Audio transcription endpoint should require auth", route!!.requiresAuth)
  }

  @Test
  fun audioSpeechStillReturnsUnsupportedMessage() {
    val msg = RouteResolver.getUnsupportedEndpointMessage("/v1/audio/speech")
    assertNotNull(msg)
    assertTrue("Message should explain speech synthesis is not supported", msg!!.contains("speech"))
  }
}
