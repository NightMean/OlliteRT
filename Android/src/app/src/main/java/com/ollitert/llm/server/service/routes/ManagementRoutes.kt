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

import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*

import com.ollitert.llm.server.service.http.HttpResponse
import com.ollitert.llm.server.service.http.KtorServer
import com.ollitert.llm.server.service.http.PrometheusRenderer
import com.ollitert.llm.server.service.http.httpNotFound
import com.ollitert.llm.server.service.http.httpOkJson
import com.ollitert.llm.server.service.http.respondHttpResponse
import io.ktor.server.application.call
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.head

/**
 * Health, ping, version, metrics, and favicon route definitions.
 */
internal fun Routing.managementRoutes(server: KtorServer) {
  with(server) {
    get("/ping") {
      call.respondHttpResponse(httpOkJson("""{"status":"ok"}"""))
    }

    get("/health") { handleHealth(call) }
    get("/v1/health") { handleHealth(call) }

    get("/") { handleServerInfo(call) }
    get("/v1") { handleServerInfo(call) }
    get("/api/version") { handleServerInfo(call) }

    // HEAD probes used by Anthropic/OpenAI clients (Claude Code, curl -I, monitors)
    // to liveness-check the base URL before issuing real requests. RFC 7231 §4.3.2:
    // HEAD must succeed wherever GET succeeds.
    head("/") { call.respondHttpResponse(httpOkJson("")) }
    head("/v1") { call.respondHttpResponse(httpOkJson("")) }
    head("/ping") { call.respondHttpResponse(httpOkJson("")) }
    head("/health") { call.respondHttpResponse(httpOkJson("")) }
    head("/v1/health") { call.respondHttpResponse(httpOkJson("")) }

    get("/metrics") {
      withGetLogging(call) {
        val body = PrometheusRenderer.render()
        HttpResponse.PlainText(200, PrometheusRenderer.CONTENT_TYPE, body)
      }
    }

    get("/favicon.ico") {
      val bytes = faviconBytes
      if (bytes != null) {
        call.respondHttpResponse(HttpResponse.Binary(200, "image/png", bytes))
      } else {
        call.respondHttpResponse(httpNotFound())
      }
    }
  }
}
