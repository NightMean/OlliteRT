package com.ollitert.llm.server.service

import fi.iki.elonen.NanoHTTPD

enum class LlmHttpRouteHandler {
  PING,
  HEALTH,
  SERVER_INFO,
  METRICS,
  MODELS,
  MODEL_DETAIL,
  GENERATE,
  COMPLETIONS,
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
        when {
          uri == "/ping" -> LlmHttpRoute(handler = LlmHttpRouteHandler.PING, requiresAuth = false)
          uri == "/health" || uri == "/v1/health" -> LlmHttpRoute(handler = LlmHttpRouteHandler.HEALTH, requiresAuth = false)
          uri == "/" || uri == "/v1" -> LlmHttpRoute(handler = LlmHttpRouteHandler.SERVER_INFO, requiresAuth = false)
          uri == "/metrics" -> LlmHttpRoute(handler = LlmHttpRouteHandler.METRICS, requiresAuth = false)
          uri == "/v1/models" || uri == "/debug/models" ->
            LlmHttpRoute(handler = LlmHttpRouteHandler.MODELS, requiresAuth = true)
          uri.startsWith("/v1/models/") -> LlmHttpRoute(handler = LlmHttpRouteHandler.MODEL_DETAIL, requiresAuth = true)
          else -> null
        }
      NanoHTTPD.Method.POST ->
        when (uri) {
          "/generate" -> LlmHttpRoute(handler = LlmHttpRouteHandler.GENERATE, requiresAuth = true)
          "/v1/completions" -> LlmHttpRoute(handler = LlmHttpRouteHandler.COMPLETIONS, requiresAuth = true)
          "/v1/chat/completions" ->
            LlmHttpRoute(handler = LlmHttpRouteHandler.CHAT_COMPLETIONS, requiresAuth = true)
          "/v1/responses" -> LlmHttpRoute(handler = LlmHttpRouteHandler.RESPONSES, requiresAuth = true)
          else -> null
        }
      else -> null
    }
  }

  /**
   * Returns a descriptive error message for known OpenAI endpoints that this server
   * cannot support, or null if the URI is not a recognized unsupported endpoint.
   */
  fun getUnsupportedEndpointMessage(uri: String): String? = when {
    uri.startsWith("/v1/embeddings") -> "Embeddings are not supported — this server runs inference-only models"
    uri.startsWith("/v1/audio") -> "Audio endpoints are not supported by this server"
    uri.startsWith("/v1/images") -> "Image generation is not supported by this server"
    uri.startsWith("/v1/fine_tuning") || uri.startsWith("/v1/fine-tuning") -> "Fine-tuning is not supported by this server"
    uri.startsWith("/v1/files") -> "File management is not supported by this server"
    uri.startsWith("/v1/batches") -> "Batch processing is not supported by this server"
    uri.startsWith("/v1/assistants") || uri.startsWith("/v1/threads") -> "Assistants API is not supported by this server"
    uri.startsWith("/v1/vector_stores") -> "Vector stores are not supported by this server"
    else -> null
  }
}
