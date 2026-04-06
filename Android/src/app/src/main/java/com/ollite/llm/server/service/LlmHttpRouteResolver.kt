package com.ollite.llm.server.service

import fi.iki.elonen.NanoHTTPD

enum class LlmHttpRouteHandler {
  PING,
  MODELS,
  GENERATE,
  CHAT_COMPLETIONS,
  RESPONSES,
}

data class LlmHttpRoute(
  val handler: LlmHttpRouteHandler,
  val requiresAuth: Boolean,
)

object LlmHttpRouteResolver {
  fun isSupportedMethod(method: NanoHTTPD.Method): Boolean {
    return method == NanoHTTPD.Method.GET || method == NanoHTTPD.Method.POST || method == NanoHTTPD.Method.OPTIONS
  }

  fun resolve(method: NanoHTTPD.Method, uri: String): LlmHttpRoute? {
    return when (method) {
      NanoHTTPD.Method.GET ->
        when (uri) {
          "/ping" -> LlmHttpRoute(handler = LlmHttpRouteHandler.PING, requiresAuth = false)
          "/v1/models", "/debug/models" ->
            LlmHttpRoute(handler = LlmHttpRouteHandler.MODELS, requiresAuth = true)
          else -> null
        }
      NanoHTTPD.Method.POST ->
        when (uri) {
          "/generate" -> LlmHttpRoute(handler = LlmHttpRouteHandler.GENERATE, requiresAuth = true)
          "/v1/chat/completions" ->
            LlmHttpRoute(handler = LlmHttpRouteHandler.CHAT_COMPLETIONS, requiresAuth = true)
          "/v1/responses" -> LlmHttpRoute(handler = LlmHttpRouteHandler.RESPONSES, requiresAuth = true)
          else -> null
        }
      else -> null
    }
  }
}
