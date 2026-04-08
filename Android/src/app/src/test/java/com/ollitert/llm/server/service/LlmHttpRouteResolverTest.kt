package com.ollitert.llm.server.service

import fi.iki.elonen.NanoHTTPD
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
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/ping"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.MODELS, requiresAuth = true),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/v1/models"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.GENERATE, requiresAuth = true),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.POST, "/generate"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.CHAT_COMPLETIONS, requiresAuth = true),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.POST, "/v1/chat/completions"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.RESPONSES, requiresAuth = true),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.POST, "/v1/responses"),
    )
  }

  @Test
  fun resolvesServerInfoRoutes() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.SERVER_INFO, requiresAuth = false),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.SERVER_INFO, requiresAuth = false),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/v1"),
    )
  }

  @Test
  fun returnsNullForUnknownRouteOrWrongMethod() {
    assertNull(LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/generate"))
    assertNull(LlmHttpRouteResolver.resolve(NanoHTTPD.Method.POST, "/v1/models"))
    assertNull(LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/unknown"))
  }

  @Test
  fun onlyGetAndPostAreSupported() {
    assertTrue(LlmHttpRouteResolver.isSupportedMethod(NanoHTTPD.Method.GET))
    assertTrue(LlmHttpRouteResolver.isSupportedMethod(NanoHTTPD.Method.POST))
    assertFalse(LlmHttpRouteResolver.isSupportedMethod(NanoHTTPD.Method.DELETE))
  }

  // ── Additional routes───────────────────────────────────────────────────

  @Test
  fun resolvesHealthRoutes() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.HEALTH, requiresAuth = false),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/health"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.HEALTH, requiresAuth = false),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/v1/health"),
    )
  }

  @Test
  fun resolvesCompletionsRoute() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.COMPLETIONS, requiresAuth = true),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.POST, "/v1/completions"),
    )
  }

  @Test
  fun resolvesModelDetailRoute() {
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.MODEL_DETAIL, requiresAuth = true),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/v1/models/Gemma-3n-E4B"),
    )
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.MODEL_DETAIL, requiresAuth = true),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/v1/models/some-model-name"),
    )
  }

  @Test
  fun modelListRouteStillWorks() {
    // /v1/models (without trailing path) should still resolve to MODELS, not MODEL_DETAIL
    assertEquals(
      LlmHttpRoute(LlmHttpRouteHandler.MODELS, requiresAuth = true),
      LlmHttpRouteResolver.resolve(NanoHTTPD.Method.GET, "/v1/models"),
    )
  }

  // ── Unsupported endpoint messages────────────────────────────────

  @Test
  fun unsupportedEndpointsReturnDescriptiveMessages() {
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/embeddings"))
    assertNotNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/audio/transcriptions"))
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
  fun unknownEndpointsReturnNull() {
    assertNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/chat/completions"))
    assertNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/v1/models"))
    assertNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/some/random/path"))
    assertNull(LlmHttpRouteResolver.getUnsupportedEndpointMessage("/"))
  }
}
