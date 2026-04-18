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

import fi.iki.elonen.NanoHTTPD

enum class LlmHttpRouteHandler {
  PING,
  HEALTH,
  SERVER_INFO,
  VERSION,
  METRICS,
  MODELS,
  MODEL_DETAIL,
  GENERATE,
  COMPLETIONS,
  CHAT_COMPLETIONS,
  RESPONSES,
  SERVER_STOP,
  SERVER_RELOAD,
  SERVER_THINKING,
  SERVER_CONFIG,
  // TODO: Add SERVER_MODEL_SWITCH when multi-model support is implemented.
  // Would accept { "model": "model-name" } to switch the active model via API,
  // enabling HA automations like "switch to the small model at night to save battery".
  // Blocked until the server decouples model lifecycle from server lifecycle and
  // exposes all downloaded models.
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
          uri == "/api/version" -> LlmHttpRoute(handler = LlmHttpRouteHandler.VERSION, requiresAuth = false)
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
          "/v1/server/stop" -> LlmHttpRoute(handler = LlmHttpRouteHandler.SERVER_STOP, requiresAuth = true)
          "/v1/server/reload" -> LlmHttpRoute(handler = LlmHttpRouteHandler.SERVER_RELOAD, requiresAuth = true)
          "/v1/server/thinking" -> LlmHttpRoute(handler = LlmHttpRouteHandler.SERVER_THINKING, requiresAuth = true)
          "/v1/server/config" -> LlmHttpRoute(handler = LlmHttpRouteHandler.SERVER_CONFIG, requiresAuth = true)
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
