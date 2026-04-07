package com.ollitert.llm.server.service

import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
