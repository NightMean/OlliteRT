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

import android.content.Context
import com.ollitert.llm.server.data.CORS_PREFLIGHT_MAX_AGE_SECONDS
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/**
 * Ktor CIO embedded HTTP server that replaces [LlmHttpServer] (NanoHTTPD).
 *
 * Routes requests to [LlmHttpEndpointHandlers] for inference endpoints and
 * handles server info, health, metrics, model listing, and favicon directly.
 * CORS is configured via the Ktor CORS plugin; bearer auth uses the same
 * constant-time comparison as the NanoHTTPD server.
 *
 * Separated from [LlmHttpService] to isolate HTTP concerns (routing, auth,
 * CORS, request/response formatting) from Android Service lifecycle concerns.
 */
class KtorServer(
  private val port: Int,
  private val serviceContext: Context,
  private val endpointHandlers: LlmHttpEndpointHandlers,
  private val modelLifecycle: LlmHttpModelLifecycle,
  private val json: Json,
  private val nextRequestId: () -> String,
  private val getRequestCount: () -> Long,
  private val emitDebugStackTrace: (Throwable, String, String?) -> Unit,
  private val audioTranscriptionHandler: LlmHttpAudioTranscriptionHandler,
  private val inferenceLock: Any,
) {

  private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

  // Convenience accessors for model state (mirrors LlmHttpServer pattern)
  private val defaultModel: Model? get() = modelLifecycle.defaultModel
  private val keepAliveUnloadedModelName: String? get() = modelLifecycle.keepAliveUnloadedModelName

  private val faviconBytes: ByteArray? by lazy {
    try {
      serviceContext.assets.open("favicon.png").use { it.readBytes() }
    } catch (_: Exception) {
      null
    }
  }

  fun start() {
    engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
      configureCors()
      install(ContentNegotiation) { json(json) }
      routing {
        configureGetRoutes()
        configurePostRoutes()
      }
    }
    engine?.start(wait = false)
  }

  fun stop() {
    engine?.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
    engine = null
  }

  // ── CORS ──────────────────────────────────────────────────────────────────

  /**
   * Configures the Ktor CORS plugin based on the user's SharedPreferences setting.
   * If the setting is empty, CORS is disabled (no plugin installed).
   */
  private fun Application.configureCors() {
    val allowedOrigins = LlmHttpPrefs.getCorsAllowedOrigins(serviceContext).trim()
    if (allowedOrigins.isEmpty()) return // CORS disabled

    install(CORS) {
      allowMethod(HttpMethod.Get)
      allowMethod(HttpMethod.Post)
      allowMethod(HttpMethod.Options)
      allowHeader(HttpHeaders.ContentType)
      allowHeader(HttpHeaders.Authorization)
      allowHeader("User-Agent")
      allowHeader("Accept")
      allowHeader("X-Requested-With")
      maxAgeInSeconds = CORS_PREFLIGHT_MAX_AGE_SECONDS.toLongOrNull() ?: 86400L

      if (allowedOrigins == "*") {
        anyHost()
      } else {
        val origins = allowedOrigins.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (origin in origins) {
          // allowHost expects "host:port" format with a schemes list.
          // Parse the origin URL to extract host, port, and scheme.
          try {
            val url = Url(origin)
            val hostWithPort = if (url.port != url.protocol.defaultPort) {
              "${url.host}:${url.port}"
            } else {
              url.host
            }
            allowHost(hostWithPort, schemes = listOf(url.protocol.name))
          } catch (_: Exception) {
            // Fallback: treat as a plain host string
            allowHost(origin.removePrefix("http://").removePrefix("https://"))
          }
        }
      }
    }
  }

  // ── Auth ─────────────────────────────────────────────────────────────────

  /**
   * Checks bearer token authorization. Returns `true` if the request is
   * authorized (or auth is disabled). Returns `false` and sends a 401
   * response if unauthorized.
   */
  private suspend fun requireAuth(call: ApplicationCall): Boolean {
    val expected = LlmHttpPrefs.getBearerToken(serviceContext)
    if (expected.isBlank()) return true // Auth disabled
    val header = call.request.headers["Authorization"] ?: ""
    if (LlmHttpBridgeUtils.isBearerAuthorized(expected, header)) return true
    call.respondHttpResponse(httpUnauthorized("unauthorized"))
    return false
  }

  // ── Response dispatcher ───────────────────────────────────────────────────

  /**
   * Converts an [HttpResponse] sealed class into the appropriate Ktor response.
   * This bridges the handler layer (which returns [HttpResponse]) with Ktor's
   * response API.
   */
  private suspend fun ApplicationCall.respondHttpResponse(resp: HttpResponse) {
    when (resp) {
      is HttpResponse.Json -> {
        for ((key, value) in resp.extraHeaders) {
          response.headers.append(key, value)
        }
        respondText(
          resp.body,
          ContentType.Application.Json.withCharset(Charsets.UTF_8),
          HttpStatusCode.fromValue(resp.statusCode),
        )
      }

      is HttpResponse.Binary -> {
        respondBytes(
          resp.bytes,
          ContentType.parse(resp.contentType),
          HttpStatusCode.fromValue(resp.statusCode),
        )
      }

      is HttpResponse.PlainText -> {
        respondText(
          resp.body,
          ContentType.parse(resp.contentType),
          HttpStatusCode.fromValue(resp.statusCode),
        )
      }

      is HttpResponse.Sse -> {
        respondTextWriter(contentType = ContentType.Text.EventStream) {
          val writer = KtorSseWriterImpl(this)
          resp.writer(writer)
        }
      }
    }
  }

  // ── GET routes ────────────────────────────────────────────────────────────

  private fun Routing.configureGetRoutes() {
    get("/ping") {
      call.respondHttpResponse(httpOkJson("""{"status":"ok"}"""))
    }

    get("/health") { handleHealth(call) }
    get("/v1/health") { handleHealth(call) }

    get("/") {
      val body = LlmHttpPayloadBuilders.serverInfo(
        defaultModel, keepAliveUnloadedModelName, modelLifecycle.allowlistLoader,
      )
      call.respondHttpResponse(httpOkJson(body))
    }

    get("/v1") {
      val body = LlmHttpPayloadBuilders.serverInfo(
        defaultModel, keepAliveUnloadedModelName, modelLifecycle.allowlistLoader,
      )
      call.respondHttpResponse(httpOkJson(body))
    }

    get("/api/version") {
      val body = LlmHttpPayloadBuilders.serverInfo(
        defaultModel, keepAliveUnloadedModelName, modelLifecycle.allowlistLoader,
      )
      call.respondHttpResponse(httpOkJson(body))
    }

    get("/metrics") {
      val body = LlmHttpPrometheusRenderer.render()
      call.respondHttpResponse(
        HttpResponse.PlainText(200, LlmHttpPrometheusRenderer.CONTENT_TYPE, body),
      )
    }

    get("/v1/models") {
      if (!requireAuth(call)) return@get
      val body = LlmHttpPayloadBuilders.modelsList(defaultModel, keepAliveUnloadedModelName, json)
      call.respondHttpResponse(httpOkJson(body))
    }

    get("/debug/models") {
      if (!requireAuth(call)) return@get
      val body = LlmHttpPayloadBuilders.modelsList(defaultModel, keepAliveUnloadedModelName, json)
      call.respondHttpResponse(httpOkJson(body))
    }

    get("/v1/models/{id...}") {
      if (!requireAuth(call)) return@get
      val body = LlmHttpPayloadBuilders.modelDetail(
        defaultModel, call.request.uri, json, keepAliveUnloadedModelName,
      )
      if (body != null) {
        call.respondHttpResponse(httpOkJson(body))
      } else {
        call.respondHttpResponse(httpNotFound("model_not_found"))
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

  // ── POST routes (placeholder for Task 6) ──────────────────────────────────

  private fun Routing.configurePostRoutes() {
    // Will be implemented in Task 6
  }

  // ── Shared route handlers ─────────────────────────────────────────────────

  private suspend fun handleHealth(call: ApplicationCall) {
    val includeMetrics =
      call.request.queryParameters["metrics"]?.equals("true", ignoreCase = true) == true
    val body = LlmHttpPayloadBuilders.health(
      defaultModel, keepAliveUnloadedModelName, includeMetrics,
    )
    call.respondHttpResponse(httpOkJson(body))
  }
}
