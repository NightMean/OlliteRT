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

import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.service.inference.*

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.data.prefs.ClientIpAccessPolicy
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.service.routes.audioRoutes
import com.ollitert.llm.server.service.routes.chatRoutes
import com.ollitert.llm.server.service.routes.managementRoutes
import com.ollitert.llm.server.service.routes.modelRoutes
import com.ollitert.llm.server.service.routes.serverControlRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.HttpRequestLifecycle
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.uri
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "OlliteRT.Server"

class KtorServer(
  private val port: Int,
  private val bindHost: String,
  initialClientIpAccessPolicy: ClientIpAccessPolicy,
  internal val serviceContext: Context,
  internal val endpointHandlers: EndpointHandlers,
  internal val modelLifecycle: ModelLifecycle,
  internal val json: Json,
  private val nextRequestId: () -> String,
  private val emitDebugStackTrace: (Throwable, String, String?) -> Unit,
  internal val audioTranscriptionHandler: AudioTranscriptionHandler,
  internal val anthropicEndpointHandlers: AnthropicEndpointHandlers,
  private val inferenceLock: Any,
) {

  private val logIdCounter = AtomicLong(0)
  private val clientIpAccessPolicy = AtomicReference(initialClientIpAccessPolicy)
  private val serverControlHandler = ServerControlHandler(serviceContext, modelLifecycle, inferenceLock)

  internal fun nextLogId() = "log-${System.currentTimeMillis()}-${logIdCounter.incrementAndGet()}"

  private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

  internal val defaultModel: Model? get() = modelLifecycle.defaultModel
  internal val keepAliveUnloadedModelName: String? get() = modelLifecycle.keepAliveUnloadedModelName

  internal val faviconBytes: ByteArray? by lazy {
    try {
      serviceContext.assets.open("favicon.png").use { it.readBytes() }
    } catch (e: Exception) {
      Log.d(TAG, "Failed to load favicon.png from assets", e)
      null
    }
  }

  fun start() {
    engine = embeddedServer(CIO, port = port, host = bindHost) {
      configureClientIpAccess(
        policy = clientIpAccessPolicy,
        nextLogId = ::nextLogId,
        modelNameProvider = { defaultModel?.name ?: keepAliveUnloadedModelName },
      )
      configureCors(serviceContext)
      install(ContentNegotiation) { json(json) }
      install(StatusPages) {
        status(HttpStatusCode.MethodNotAllowed) { call, _ ->
          withGetLogging(call) { httpMethodNotAllowed() }
        }
        status(HttpStatusCode.NotFound) { call, _ ->
          val uri = call.request.uri
          val unsupportedMsg = RouteResolver.getUnsupportedEndpointMessage(uri)
          val response = if (unsupportedMsg != null) httpJsonError(404, unsupportedMsg)
          else httpNotFound()
          withGetLogging(call) { response }
        }
      }
      install(HttpRequestLifecycle) {
        cancelCallOnClose = true
      }
      routing {
        managementRoutes(this@KtorServer)
        modelRoutes(this@KtorServer)
        chatRoutes(this@KtorServer)
        audioRoutes(this@KtorServer)
        serverControlRoutes(this@KtorServer)
      }
    }
    engine?.start(wait = false)
  }

  fun stop(gracePeriodMillis: Long = 3000, timeoutMillis: Long = 5000) {
    engine?.stop(gracePeriodMillis = gracePeriodMillis, timeoutMillis = timeoutMillis)
    engine = null
  }

  /** Atomically applies a validated policy without restarting Ktor or unloading the model. */
  fun updateClientIpAccessPolicy(policy: ClientIpAccessPolicy) {
    clientIpAccessPolicy.set(policy)
  }

  internal suspend fun requireAuth(call: ApplicationCall): Boolean =
    requireAuth(call, serviceContext)

  internal suspend fun requireServerControl(call: ApplicationCall): Boolean =
    requireServerControl(call, serviceContext)

  internal fun finalizeLogEntry(
    logId: String,
    startMs: Long,
    response: HttpResponse,
    requestBodySnapshot: String?,
    responseBodySnapshot: String?,
  ) {
    finalizeLogEntry(
      logId = logId,
      startMs = startMs,
      response = response,
      requestBodySnapshot = requestBodySnapshot,
      responseBodySnapshot = responseBodySnapshot,
      defaultModelName = defaultModel?.name,
    )
  }

  internal suspend fun withGetLogging(
    call: ApplicationCall,
    handler: suspend () -> HttpResponse,
  ) {
    withGetLogging(
      call = call,
      serviceContext = serviceContext,
      defaultModel = defaultModel,
      keepAliveUnloadedModelName = keepAliveUnloadedModelName,
      nextLogId = ::nextLogId,
      emitDebugStackTrace = emitDebugStackTrace,
      handler = handler,
    )
  }

  internal suspend fun withRequestLogging(
    call: ApplicationCall,
    admitModelRequest: Boolean = false,
    handler: suspend (
      body: String,
      captureBody: (String) -> Unit,
      captureResponse: (String) -> Unit,
      logId: String,
      sseExtraHeaders: Map<String, String>,
      prefs: RequestPrefsSnapshot,
    ) -> HttpResponse,
  ) {
    withRequestLogging(
      call = call,
      serviceContext = serviceContext,
      defaultModel = defaultModel,
      keepAliveUnloadedModelName = keepAliveUnloadedModelName,
      nextLogId = ::nextLogId,
      emitDebugStackTrace = emitDebugStackTrace,
      modelLifecycle = modelLifecycle,
      admitModelRequest = admitModelRequest,
      handler = handler,
    )
  }

  // ── Server control delegates ──────────────────────────────────────────────

  internal fun handleServerStop(): HttpResponse = serverControlHandler.handleServerStop()

  internal fun handleServerReload(): HttpResponse =
    serverControlHandler.handleServerReload(defaultModel, keepAliveUnloadedModelName)

  internal fun handleServerThinking(body: String): HttpResponse =
    serverControlHandler.handleServerThinking(body, defaultModel, keepAliveUnloadedModelName)

  internal fun handleServerConfig(body: String): HttpResponse =
    serverControlHandler.handleServerConfig(body, defaultModel, keepAliveUnloadedModelName)

  // ── Shared route handlers ─────────────────────────────────────────────────

  internal suspend fun handleServerInfo(call: ApplicationCall) {
    withGetLogging(call) {
      val body = PayloadBuilders.serverInfo(
        defaultModel, keepAliveUnloadedModelName, modelLifecycle.modelCatalogMerger,
      )
      httpOkJson(body)
    }
  }

  internal suspend fun handleModelsList(call: ApplicationCall) {
    if (!requireAuth(call)) return
    withGetLogging(call) {
      // GET /v1/models serves both protocols on one path. Anthropic SDKs always
      // send anthropic-version and reject the OpenAI envelope, so classify on
      // that header; OpenAI clients never send it.
      val body = if (call.request.headers["anthropic-version"] != null) {
        PayloadBuilders.anthropicModelsList(defaultModel, keepAliveUnloadedModelName)
      } else {
        PayloadBuilders.modelsList(defaultModel, keepAliveUnloadedModelName, json)
      }
      httpOkJson(body)
    }
  }

  internal suspend fun handleHealth(call: ApplicationCall) {
    val includeMetrics =
      call.request.queryParameters["metrics"]?.equals("true", ignoreCase = true) == true
    val response = httpOkJson(
      PayloadBuilders.health(defaultModel, keepAliveUnloadedModelName, includeMetrics),
    )
    val prefs = ServerPrefs.captureRequestSnapshot(serviceContext)
    if (prefs.hideHealthLogs) {
      call.respondHttpResponse(response)
    } else {
      withGetLogging(call) { response }
    }
  }
}
