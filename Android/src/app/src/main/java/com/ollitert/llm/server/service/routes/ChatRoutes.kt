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

package com.ollitert.llm.server.service.routes

import android.util.Log
import com.ollitert.llm.server.service.KtorServer
import com.ollitert.llm.server.service.RequestLogStore
import io.ktor.server.application.call
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post

/**
 * Text and chat inference route definitions (OpenAI completions/chat/responses and Anthropic messages).
 */
internal fun Routing.chatRoutes(server: KtorServer) {
  with(server) {
    post("/generate") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call, admitModelRequest = true) { body, captureBody, captureResponse, logId, _, prefs ->
        endpointHandlers.handleGenerate(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("/v1/completions") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call, admitModelRequest = true) { body, captureBody, captureResponse, logId, _, prefs ->
        endpointHandlers.handleCompletions(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("/v1/chat/completions") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call, admitModelRequest = true) { body, captureBody, captureResponse, logId, _, prefs ->
        endpointHandlers.handleChatCompletion(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("/v1/responses") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call, admitModelRequest = true) { body, captureBody, captureResponse, logId, _, prefs ->
        endpointHandlers.handleResponses(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("/v1/messages") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call, admitModelRequest = true) { body, captureBody, captureResponse, logId, _, prefs ->
        if (prefs.verboseDebug) {
          val headers = call.request.headers
          val redacted = RequestLogStore.redactSensitiveHeaders(
            headers.names().associateWith { headers[it].orEmpty() }
          )
          val ua = redacted["User-Agent"] ?: redacted["user-agent"] ?: ""
          val av = redacted["anthropic-version"] ?: ""
          val accept = redacted["Accept"] ?: redacted["accept"] ?: ""
          val cl = redacted["Content-Length"] ?: redacted["content-length"] ?: ""
          val hasApiKey = (redacted["x-api-key"] != null || redacted["X-Api-Key"] != null).toString()
          Log.i("OlliteRT.Server", "ANTHROPIC_REQ headers: ua=\"$ua\" anthropic-version=\"$av\" accept=\"$accept\" content-length=$cl x-api-key=$hasApiKey body_chars=${body.length} body_head=\"${body.take(200).replace("\n", "\\n")}\"")
        }
        anthropicEndpointHandlers.handleMessages(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("/v1/messages/count_tokens") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call) { body, captureBody, captureResponse, logId, _, prefs ->
        anthropicEndpointHandlers.handleCountTokens(body, captureBody, captureResponse, logId, prefs)
      }
    }
  }
}
