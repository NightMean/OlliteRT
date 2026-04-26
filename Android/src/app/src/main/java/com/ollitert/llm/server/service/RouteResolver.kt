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
  AUDIO_TRANSCRIPTION,
  // TODO: Add SERVER_MODEL_SWITCH when multi-model support is implemented.
  // Would accept { "model": "model-name" } to switch the active model via API,
  // enabling HA automations like "switch to the small model at night to save battery".
  // Blocked until the server decouples model lifecycle from server lifecycle and
  // exposes all downloaded models.
}

data class Route(
  val handler: LlmHttpRouteHandler,
  val requiresAuth: Boolean,
)

object RouteResolver {
  fun isSupportedMethod(method: String): Boolean {
    return method == "GET" || method == "POST" || method == "OPTIONS"
  }

  fun resolve(method: String, rawUri: String): Route? {
    val normalized = rawUri.replace("//", "/")
    val uri = if (normalized.length > 1 && normalized.endsWith("/")) normalized.dropLast(1) else normalized
    return when (method) {
      "GET" ->
        when {
          uri == "/ping" -> Route(handler = LlmHttpRouteHandler.PING, requiresAuth = false)
          uri == "/health" || uri == "/v1/health" -> Route(handler = LlmHttpRouteHandler.HEALTH, requiresAuth = false)
          uri == "/" || uri == "/v1" -> Route(handler = LlmHttpRouteHandler.SERVER_INFO, requiresAuth = false)
          uri == "/api/version" -> Route(handler = LlmHttpRouteHandler.VERSION, requiresAuth = false)
          uri == "/metrics" -> Route(handler = LlmHttpRouteHandler.METRICS, requiresAuth = false)
          uri == "/v1/models" || uri == "/debug/models" ->
            Route(handler = LlmHttpRouteHandler.MODELS, requiresAuth = true)
          uri.startsWith("/v1/models/") -> Route(handler = LlmHttpRouteHandler.MODEL_DETAIL, requiresAuth = true)
          else -> null
        }
      "POST" ->
        when (uri) {
          "/generate" -> Route(handler = LlmHttpRouteHandler.GENERATE, requiresAuth = true)
          "/v1/completions" -> Route(handler = LlmHttpRouteHandler.COMPLETIONS, requiresAuth = true)
          "/v1/chat/completions" ->
            Route(handler = LlmHttpRouteHandler.CHAT_COMPLETIONS, requiresAuth = true)
          "/v1/responses" -> Route(handler = LlmHttpRouteHandler.RESPONSES, requiresAuth = true)
          "/v1/server/stop" -> Route(handler = LlmHttpRouteHandler.SERVER_STOP, requiresAuth = true)
          "/v1/server/reload" -> Route(handler = LlmHttpRouteHandler.SERVER_RELOAD, requiresAuth = true)
          "/v1/server/thinking" -> Route(handler = LlmHttpRouteHandler.SERVER_THINKING, requiresAuth = true)
          "/v1/server/config" -> Route(handler = LlmHttpRouteHandler.SERVER_CONFIG, requiresAuth = true)
          "/v1/audio/transcriptions" ->
            Route(handler = LlmHttpRouteHandler.AUDIO_TRANSCRIPTION, requiresAuth = true)
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
    uri.startsWith("/v1/audio/speech") -> "Audio speech synthesis is not supported by this server"
    uri.startsWith("/v1/audio") && !uri.startsWith("/v1/audio/transcriptions") ->
      "Audio endpoint not supported by this server"
    uri.startsWith("/v1/images") -> "Image generation is not supported by this server"
    uri.startsWith("/v1/fine_tuning") || uri.startsWith("/v1/fine-tuning") -> "Fine-tuning is not supported by this server"
    uri.startsWith("/v1/files") -> "File management is not supported by this server"
    uri.startsWith("/v1/batches") -> "Batch processing is not supported by this server"
    uri.startsWith("/v1/assistants") || uri.startsWith("/v1/threads") -> "Assistants API is not supported by this server"
    uri.startsWith("/v1/vector_stores") -> "Vector stores are not supported by this server"
    else -> null
  }
}
