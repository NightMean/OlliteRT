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

package com.ollitert.llm.server.service.http

/**
 * Path-classification helpers for the serving middleware. Actual routing is
 * declared with the Ktor routing DSL in `service/routes/`; these helpers exist
 * for the pre-routing layers that only see a raw path (no parsed body) and must
 * still tell protocols/endpoints apart.
 */
object RouteResolver {

  /**
   * True when the request targets an Anthropic-protocol endpoint (/v1/messages*).
   *
   * Pre-routing rejection layers (auth failure, IP-block) have no parsed body to
   * tell protocols apart, so they classify by path and pick the matching error
   * envelope: Anthropic SDKs reject the OpenAI `{error:{...}}` shape — they only
   * parse `{type:"error", error:{...}}`.
   */
  fun isAnthropicApiPath(rawUri: String): Boolean {
    val uri = rawUri.substringBefore("?")
    return uri == "/v1/messages" || uri.startsWith("/v1/messages/")
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
