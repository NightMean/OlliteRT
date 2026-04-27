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
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.data.CORS_PREFLIGHT_MAX_AGE_SECONDS
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.data.configTemperature
import com.ollitert.llm.server.data.configThinkingEnabled
import com.ollitert.llm.server.data.configTopK
import com.ollitert.llm.server.data.configTopP
import com.ollitert.llm.server.data.maxTokensInt
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import java.util.concurrent.atomic.AtomicLong
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
import io.ktor.server.http.HttpRequestLifecycle
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveText
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Ktor CIO embedded HTTP server.
 *
 * Routes requests to [EndpointHandlers] for inference endpoints and
 * handles server info, health, metrics, model listing, and favicon directly.
 * CORS is configured via the Ktor CORS plugin; bearer auth uses constant-time
 * comparison.
 *
 * Separated from [ServerService] to isolate HTTP concerns (routing, auth,
 * CORS, request/response formatting) from Android Service lifecycle concerns.
 */
private const val TAG = "OlliteRT.Server"

class KtorServer(
  private val port: Int,
  private val serviceContext: Context,
  private val endpointHandlers: EndpointHandlers,
  private val modelLifecycle: ModelLifecycle,
  private val json: Json,
  private val nextRequestId: () -> String,
  private val emitDebugStackTrace: (Throwable, String, String?) -> Unit,
  private val audioTranscriptionHandler: AudioTranscriptionHandler,
  private val inferenceLock: Any,
) {

  private val logIdCounter = AtomicLong(0)

  private fun nextLogId() = "log-${System.currentTimeMillis()}-${logIdCounter.incrementAndGet()}"

  private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

  // Convenience accessors for model state
  private val defaultModel: Model? get() = modelLifecycle.defaultModel
  private val keepAliveUnloadedModelName: String? get() = modelLifecycle.keepAliveUnloadedModelName

  private val faviconBytes: ByteArray? by lazy {
    try {
      serviceContext.assets.open("favicon.png").use { it.readBytes() }
    } catch (e: Exception) {
      Log.d(TAG, "Failed to load favicon.png from assets", e)
      null
    }
  }

  fun start() {
    engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
      configureCors()
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
    val allowedOrigins = ServerPrefs.getCorsAllowedOrigins(serviceContext).trim()
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
      maxAgeInSeconds = CORS_PREFLIGHT_MAX_AGE_SECONDS

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
          } catch (e: Exception) {
            Log.w(TAG, "CORS: failed to parse origin \"$origin\", using raw host fallback: ${e.message}")
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
    val expected = ServerPrefs.getBearerToken(serviceContext)
    if (expected.isBlank()) return true // Auth disabled
    val header = call.request.headers["Authorization"] ?: ""
    if (BridgeUtils.isBearerAuthorized(expected, header)) return true
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
        // Streaming uses its own disconnect detection (SseWriter.isCancelled) which
        // gracefully stops inference, emits [DONE], and updates the log entry.
        // Shield from cancelCallOnClose so coroutine cancellation doesn't bypass that cleanup.
        respondTextWriter(contentType = ContentType.Text.EventStream) {
          kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            val writer = KtorSseWriterImpl(this@respondTextWriter)
            resp.writer(writer)
          }
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
      withGetLogging(call) {
        val body = PayloadBuilders.serverInfo(
          defaultModel, keepAliveUnloadedModelName, modelLifecycle.allowlistLoader,
        )
        httpOkJson(body)
      }
    }

    get("/v1") {
      withGetLogging(call) {
        val body = PayloadBuilders.serverInfo(
          defaultModel, keepAliveUnloadedModelName, modelLifecycle.allowlistLoader,
        )
        httpOkJson(body)
      }
    }

    get("/api/version") {
      withGetLogging(call) {
        val body = PayloadBuilders.serverInfo(
          defaultModel, keepAliveUnloadedModelName, modelLifecycle.allowlistLoader,
        )
        httpOkJson(body)
      }
    }

    get("/metrics") {
      withGetLogging(call) {
        val body = PrometheusRenderer.render()
        HttpResponse.PlainText(200, PrometheusRenderer.CONTENT_TYPE, body)
      }
    }

    get("/v1/models") {
      if (!requireAuth(call)) return@get
      withGetLogging(call) {
        val body = PayloadBuilders.modelsList(defaultModel, keepAliveUnloadedModelName, json)
        httpOkJson(body)
      }
    }

    get("/debug/models") {
      if (!requireAuth(call)) return@get
      withGetLogging(call) {
        val body = PayloadBuilders.modelsList(defaultModel, keepAliveUnloadedModelName, json)
        httpOkJson(body)
      }
    }

    get("/v1/models/{id...}") {
      if (!requireAuth(call)) return@get
      withGetLogging(call) {
        val body = PayloadBuilders.modelDetail(
          defaultModel, call.request.uri, json, keepAliveUnloadedModelName,
        )
        if (body != null) httpOkJson(body)
        else httpNotFound("model_not_found")
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

  // ── POST routes ────────────────────────────────────────────────────────────

  private fun Routing.configurePostRoutes() {
    // ── Inference routes — delegated to EndpointHandlers ──
    // Handlers return HttpResponse directly (including HttpResponse.Sse for streaming).

    post("/generate") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call) { body, captureBody, captureResponse, logId, _, prefs ->
        endpointHandlers.handleGenerate(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("/v1/completions") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call) { body, captureBody, captureResponse, logId, _, prefs ->
        endpointHandlers.handleCompletions(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("/v1/chat/completions") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call) { body, captureBody, captureResponse, logId, _, prefs ->
        endpointHandlers.handleChatCompletion(body, captureBody, captureResponse, logId, prefs)
      }
    }

    post("/v1/responses") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call) { body, captureBody, captureResponse, logId, _, prefs ->
        endpointHandlers.handleResponses(body, captureBody, captureResponse, logId, prefs)
      }
    }

    // ── Server control endpoints ──
    // IMPORTANT: When adding new /v1/server/* endpoints, also update the HA YAML
    // template in HomeAssistantCard.kt (haConfig buildString block) with the new rest_command.

    post("/v1/server/stop") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call) { _, _, _, _, _, _ ->
        handleServerStop()
      }
    }

    post("/v1/server/reload") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call) { _, _, _, _, _, _ ->
        handleServerReload()
      }
    }

    post("/v1/server/thinking") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call) { body, _, _, _, _, _ ->
        handleServerThinking(body)
      }
    }

    post("/v1/server/config") {
      if (!requireAuth(call)) return@post
      withRequestLogging(call) { body, _, _, _, _, _ ->
        handleServerConfig(body)
      }
    }

    // ── Audio transcription ──
    // Can't use withRequestLogging — multipart body requires receiveMultipart() instead of receiveText().
    post("/v1/audio/transcriptions") {
      if (!requireAuth(call)) return@post
      val prefs = ServerPrefs.captureRequestSnapshot(serviceContext)
      val startMs = SystemClock.elapsedRealtime()
      val logId = nextLogId()
      RequestLogStore.add(
        RequestLogEntry(
          id = logId,
          method = "POST",
          path = "/v1/audio/transcriptions",
          modelName = defaultModel?.name ?: keepAliveUnloadedModelName,
          clientIp = call.clientIp(prefs.resolveClientHostnames),
          isPending = true,
        ),
      )

      val contentLengthHeader = call.request.headers["Content-Length"]?.toLongOrNull()

      if (contentLengthHeader != null && contentLengthHeader > MAX_FILE_SIZE_BYTES) {
        val response = httpPayloadTooLarge("File too large (${contentLengthHeader / 1_000_000}MB). Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
        finalizeLogEntry(logId, startMs, response, null, response.body)
        call.response.headers.append("x-request-id", logId)
        call.respondHttpResponse(response)
        return@post
      }

      val multipart = call.receiveMultipart(formFieldLimit = MAX_FILE_SIZE_BYTES)
      var fileBytes: ByteArray? = null
      val fields = mutableMapOf<String, String>()
      try {
        multipart.forEachPart { part ->
          when (part) {
            is PartData.FileItem -> fileBytes = readBytesWithLimit(part.provider().readRemaining(), MAX_FILE_SIZE_BYTES)
            is PartData.FormItem -> fields[part.name ?: ""] = part.value
            else -> {}
          }
          part.dispose()
        }
      } catch (e: java.io.IOException) {
        Log.w(TAG, "Audio upload exceeded ${MAX_FILE_SIZE_BYTES / 1_000_000}MB limit: ${e.message}")
        val response = httpPayloadTooLarge("File too large. Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
        finalizeLogEntry(logId, startMs, response, "[multipart audio — rejected: too large]", response.body)
        call.response.headers.append("x-request-id", logId)
        call.respondHttpResponse(response)
        return@post
      }

      val actualSize = fileBytes?.size?.toLong() ?: contentLengthHeader ?: 0L

      val model = when (val sel = modelLifecycle.selectModel(null)) {
        is ModelLifecycle.ModelSelection.Ok -> sel.model
        is ModelLifecycle.ModelSelection.Error -> {
          val response = sel.toHttpResponse()
          finalizeLogEntry(logId, startMs, response, null, response.body)
          call.response.headers.append("x-request-id", logId)
          call.respondHttpResponse(response)
          return@post
        }
      }

      val response = try {
        audioTranscriptionHandler.handle(fileBytes, fields, actualSize, model, logId = logId, prefs = prefs)
      } catch (_: kotlinx.coroutines.CancellationException) {
        RequestLogStore.update(logId) {
          it.copy(requestBody = "[multipart audio $actualSize bytes]", isPending = false, isCancelled = true, statusCode = 499, latencyMs = SystemClock.elapsedRealtime() - startMs)
        }
        throw kotlinx.coroutines.CancellationException("Client disconnected")
      }
      val responseBody = when (response) {
        is HttpResponse.Json -> response.body
        is HttpResponse.PlainText -> response.body
        else -> null
      }
      finalizeLogEntry(logId, startMs, response, "[multipart audio $actualSize bytes]", responseBody)
      call.response.headers.append("x-request-id", logId)
      call.respondHttpResponse(response)
    }
  }

  // ── Request logging middleware ─────────────────────────────────────────────

  /**
   * Lightweight logging wrapper for GET routes. Creates a log entry, runs the
   * handler, finalizes with status/latency, and sends the response. No body
   * parsing or OOM protection needed — GET routes have no request body.
   */
  private suspend fun withGetLogging(
    call: ApplicationCall,
    handler: suspend () -> HttpResponse,
  ) {
    val startMs = SystemClock.elapsedRealtime()
    val logId = nextLogId()
    RequestLogStore.add(
      RequestLogEntry(
        id = logId,
        method = call.request.local.method.value,
        path = call.request.uri,
        modelName = defaultModel?.name ?: keepAliveUnloadedModelName,
        clientIp = call.clientIp(ServerPrefs.isResolveClientHostnames(serviceContext)),
        isPending = true,
      ),
    )

    val response = try {
      handler()
    } catch (t: Throwable) {
      ServerMetrics.incrementErrorCount(ErrorCategory.SYSTEM)
      emitDebugStackTrace(t, "ktor_get_catch_all", null)
      httpInternalError("internal_error")
    }

    val responseBodySnapshot = when (response) {
      is HttpResponse.Json -> response.body
      is HttpResponse.PlainText -> response.body
      is HttpResponse.Binary -> "[binary ${response.bytes.size} bytes]"
      is HttpResponse.Sse -> null
    }
    finalizeLogEntry(logId, startMs, response, requestBodySnapshot = null, responseBodySnapshot = responseBodySnapshot)
    call.response.headers.append("x-request-id", logId)
    call.respondHttpResponse(response)
  }

  /**
   * Wraps a POST route handler with request logging: creates a pending log entry,
   * reads the body with OOM protection, invokes the handler, finalizes the log
   * entry with status/latency/metrics, and sends the response.
   *
   * The handler lambda receives the request body, capture callbacks, log ID, and
   * SSE extra headers, and returns an [HttpResponse].
   */
  private suspend fun withRequestLogging(
    call: ApplicationCall,
    handler: suspend (
      body: String,
      captureBody: (String) -> Unit,
      captureResponse: (String) -> Unit,
      logId: String,
      sseExtraHeaders: Map<String, String>,
      prefs: RequestPrefsSnapshot,
    ) -> HttpResponse,
  ) {
    val prefs = ServerPrefs.captureRequestSnapshot(serviceContext)
    val startMs = SystemClock.elapsedRealtime()
    val method = call.request.local.method.value
    val path = call.request.uri
    val clientIp = call.clientIp(prefs.resolveClientHostnames)

    // Add a pending log entry immediately so it appears in the Logs tab
    val logId = nextLogId()
    RequestLogStore.add(
      RequestLogEntry(
        id = logId,
        method = method,
        path = path,
        modelName = defaultModel?.name ?: keepAliveUnloadedModelName,
        clientIp = clientIp,
        isPending = true,
      ),
    )

    // For streaming responses, x-request-id is set as a response header after the handler
    // returns. CORS is handled by Ktor's CORS plugin.
    val sseExtraHeaders = mapOf("x-request-id" to logId)

    var requestBodySnapshot: String? = null
    var responseBodySnapshot: String? = null

    val response: HttpResponse = try {
      // Read body with OOM protection — oversized payloads should fail the request,
      // not destroy the loaded model.
      val body = try {
        withContext(Dispatchers.IO) { call.receiveText() }
      } catch (_: OutOfMemoryError) {
        System.gc()
        Log.w(TAG, "receiveText() OOM — returning HTTP 413 to client")
        ServerMetrics.incrementErrorCount(ErrorCategory.NETWORK)
        val oomResponse = httpPayloadTooLarge(
          "Request body too large — server ran out of memory parsing the request",
        )
        requestBodySnapshot = "[OOM during body read]"
        finalizeLogEntry(logId, startMs, oomResponse, requestBodySnapshot, responseBodySnapshot)
        call.response.headers.append("x-request-id", logId)
        call.respondHttpResponse(oomResponse)
        return
      }

      // Capture body for logging (with optional base64 compaction for images)
      val compactImages = prefs.compactImageData
      val captureBody = { rawBody: String ->
        val stored = if (compactImages) BridgeUtils.compactBase64DataUris(rawBody) else rawBody
        val originalSize = if (compactImages && stored.length != rawBody.length) rawBody.length else 0
        requestBodySnapshot = stored
        RequestLogStore.update(logId) {
          it.copy(requestBody = stored, originalRequestBodySize = originalSize)
        }
      }
      val captureResponse = { resp: String -> responseBodySnapshot = resp }

      handler(body, captureBody, captureResponse, logId, sseExtraHeaders, prefs)
    } catch (_: kotlinx.coroutines.CancellationException) {
      RequestLogStore.update(logId) {
        it.copy(requestBody = requestBodySnapshot ?: it.requestBody, isPending = false, isCancelled = true, statusCode = 499, latencyMs = SystemClock.elapsedRealtime() - startMs)
      }
      throw kotlinx.coroutines.CancellationException("Client disconnected")
    } catch (t: Throwable) {
      if (t is OutOfMemoryError) {
        // Close native Engine/Conversation before nullifying — just setting instance = null
        // leaks GB-scale native memory because GC may not finalize the wrapper promptly.
        defaultModel?.let { ServerLlmModelHelper.safeCleanup(it) }
        ServerMetrics.onServerError(t.message ?: "Out of memory")
      }
      ServerMetrics.incrementErrorCount(ErrorCategory.SYSTEM)
      emitDebugStackTrace(t, "ktor_serve_catch_all", null)
      responseBodySnapshot = t.message
      httpInternalError("internal_error")
    }

    finalizeLogEntry(logId, startMs, response, requestBodySnapshot, responseBodySnapshot)

    // Set x-request-id response header for request tracing (Open WebUI, etc.)
    call.response.headers.append("x-request-id", logId)
    call.respondHttpResponse(response)

    // Reset keep-alive idle timer after successful POST requests (inference routes
    // that touch the model). Non-inference GET routes don't reset it.
    val statusCode = when (response) {
      is HttpResponse.Json -> response.statusCode
      is HttpResponse.PlainText -> response.statusCode
      is HttpResponse.Binary -> response.statusCode
      is HttpResponse.Sse -> 200 // SSE always starts as 200
    }
    if (statusCode in 200..299) {
      modelLifecycle.resetKeepAliveTimer()
    }
  }

  /**
   * Finalizes a log entry with status code, latency, streaming detection,
   * and per-request performance metrics. For streaming responses, metadata
   * is set but isPending is left for the streaming callbacks to finalize.
   */
  private fun finalizeLogEntry(
    logId: String,
    startMs: Long,
    response: HttpResponse,
    requestBodySnapshot: String?,
    responseBodySnapshot: String?,
  ) {
    val elapsedMs = SystemClock.elapsedRealtime() - startMs
    val statusCode = when (response) {
      is HttpResponse.Json -> response.statusCode
      is HttpResponse.PlainText -> response.statusCode
      is HttpResponse.Binary -> response.statusCode
      is HttpResponse.Sse -> 200
    }
    val isStreaming = response is HttpResponse.Sse
    val isThinking = responseBodySnapshot?.contains("<think>") == true

    RequestLogStore.update(logId) {
      // If the cancel handler already finalized this entry (user tapped Stop or client
      // disconnected), preserve the cancel state but fill in the status code if still at default.
      if (it.isCancelled) return@update it.copy(
        requestBody = requestBodySnapshot ?: it.requestBody,
        statusCode = if (it.statusCode == 200) statusCode else it.statusCode,
      )
      val level = when {
        statusCode !in 200..299 -> LogLevel.ERROR
        it.isCompacted -> LogLevel.WARNING
        else -> LogLevel.INFO
      }
      // For non-streaming error responses, the handler already set responseBody with the
      // detailed error JSON (e.g. from LiteRT). Preserve it if responseBodySnapshot is null.
      val finalResponseBody = if (isStreaming) it.responseBody
      else (responseBodySnapshot ?: it.responseBody)
      // Extract actual token counts from LiteRT error messages (e.g. "6579 >= 4000")
      val actualTokens = finalResponseBody?.let { body ->
        InferenceRunner.extractActualTokenCounts(body)
      }
      // For non-streaming requests, read per-request performance metrics from ServerMetrics.
      // Streaming requests set their own metrics in the done callback.
      val perReqTtfb = if (!isStreaming) ServerMetrics.lastTtfbMs.value else it.ttfbMs
      val perReqDecode = if (!isStreaming) ServerMetrics.lastDecodeSpeed.value else it.decodeSpeed
      val perReqPrefill = if (!isStreaming) ServerMetrics.lastPrefillSpeed.value else it.prefillSpeed
      val perReqItl = if (!isStreaming) ServerMetrics.lastItlMs.value else it.itlMs
      it.copy(
        requestBody = requestBodySnapshot ?: it.requestBody,
        responseBody = finalResponseBody,
        statusCode = statusCode,
        latencyMs = if (isStreaming) it.latencyMs else elapsedMs,
        isStreaming = isStreaming,
        isThinking = isThinking,
        modelName = defaultModel?.name,
        level = level,
        isPending = if (isStreaming) it.isPending else false,
        inputTokenEstimate = actualTokens?.first ?: it.inputTokenEstimate,
        maxContextTokens = actualTokens?.second ?: it.maxContextTokens,
        isExactTokenCount = actualTokens != null || it.isExactTokenCount,
        ttfbMs = perReqTtfb,
        decodeSpeed = perReqDecode,
        prefillSpeed = perReqPrefill,
        itlMs = perReqItl,
      )
    }
  }

  // ── Server control handlers ───────────────────────────────────────────────

  /**
   * Handles POST /v1/server/stop — triggers graceful shutdown via the same
   * intent the notification Stop button uses. Response is sent before the
   * service actually stops.
   */
  private fun handleServerStop(): HttpResponse {
    val stopIntent = Intent(serviceContext, ServerService::class.java).apply {
      action = ServerService.ACTION_STOP
    }
    return try {
      serviceContext.startService(stopIntent)
      httpOkJson("""{"success":true,"message":"Server stopping"}""")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to send stop intent", e)
      httpInternalError("Failed to stop server")
    }
  }

  /**
   * Handles POST /v1/server/reload — triggers a model reload via the same
   * intent the UI uses. Also works when the model is idle-unloaded by keep_alive.
   */
  private fun handleServerReload(): HttpResponse {
    val modelName = defaultModel?.name ?: keepAliveUnloadedModelName
      ?: return httpBadRequest("No model loaded")
    val reloadPort = ServerMetrics.port.value
    ServerService.reload(serviceContext, reloadPort, modelName)
    return httpOkJson("""{"success":true,"message":"Model reloading","model":"$modelName"}""")
  }

  /**
   * Handles POST /v1/server/thinking — toggle thinking mode on/off.
   */
  private fun handleServerThinking(body: String): HttpResponse {
    val model = defaultModel
    val isIdle = ServerMetrics.isIdleUnloaded.value
    val modelName = model?.name ?: keepAliveUnloadedModelName
      ?: return httpBadRequest("No model loaded")
    val modelPrefsKey = model?.prefsKey ?: modelLifecycle.keepAliveUnloadedModelPrefsKey
      ?: return httpBadRequest("No model loaded")
    // When model is loaded, check thinking support. When idle-unloaded, skip the check —
    // we can't inspect model capabilities without the Model object, but the saved config
    // will be applied when the model reloads.
    if (model != null && !model.llmSupportThinking) {
      return httpBadRequest("Model does not support thinking")
    }
    // Read current state from model if loaded, otherwise from persisted prefs
    val currentConfig = model?.configValues
      ?: ServerPrefs.getInferenceConfig(serviceContext, modelPrefsKey)
    val currentState = currentConfig?.configThinkingEnabled() ?: false
    // Parse { "enabled": true/false } — default to toggling current state
    val requestedState = if (body.isNotBlank()) {
      try {
        val obj = Json.parseToJsonElement(body).jsonObject
        obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: !currentState
      } catch (_: Exception) {
        !currentState
      }
    } else {
      !currentState // No body = toggle
    }
    // Update in-memory config if model is loaded, always persist to prefs
    val updatedConfig = (currentConfig ?: emptyMap()) + (ConfigKeys.ENABLE_THINKING.label to requestedState)
    if (model != null) {
      synchronized(inferenceLock) { model.configValues = updatedConfig }
    }
    ServerPrefs.setInferenceConfig(serviceContext, modelPrefsKey, updatedConfig)
    ServerMetrics.setThinkingEnabled(requestedState)
    // Log using the same "Settings updated" format as the Settings UI
    val oldLabel = if (currentState) "enabled" else "disabled"
    val newLabel = if (requestedState) "enabled" else "disabled"
    RequestLogStore.addEvent(
      "Config via REST API (1 change)",
      modelName = modelName,
      category = EventCategory.SETTINGS,
      body = "Thinking: $oldLabel → $newLabel",
    )
    val result = buildJsonObject {
      put("success", true)
      put("thinking_enabled", requestedState)
      put("model", modelName)
      put("model_loaded", !isIdle)
    }
    return httpOkJson(result.toString())
  }

  /**
   * Handles POST /v1/server/config — update inference settings.
   */
  private fun handleServerConfig(body: String): HttpResponse {
    val model = defaultModel
    val isIdle = ServerMetrics.isIdleUnloaded.value
    val modelName = model?.name ?: keepAliveUnloadedModelName
      ?: return httpBadRequest("No model loaded")
    val modelPrefsKey = model?.prefsKey ?: modelLifecycle.keepAliveUnloadedModelPrefsKey
      ?: return httpBadRequest("No model loaded")
    // Read config from model if loaded, otherwise from persisted prefs
    val currentConfig = model?.configValues
      ?: ServerPrefs.getInferenceConfig(serviceContext, modelPrefsKey) ?: emptyMap()
    if (body.isBlank()) {
      // GET-like: return current config
      val current = buildJsonObject {
        put("temperature", currentConfig.configTemperature()?.toDouble() ?: 0.0)
        put("max_tokens", currentConfig.maxTokensInt() ?: 0)
        put("top_k", currentConfig.configTopK() ?: 0)
        put("top_p", currentConfig.configTopP()?.toDouble() ?: 0.0)
        put("thinking_enabled", currentConfig.configThinkingEnabled() ?: false)
        put("model", modelName)
        put("model_loaded", !isIdle)
        put("auto_truncate_history", ServerPrefs.isAutoTruncateHistory(serviceContext))
        put("auto_trim_prompts", ServerPrefs.isAutoTrimPrompts(serviceContext))
        put("compact_tool_schemas", ServerPrefs.isCompactToolSchemas(serviceContext))
        put("warmup_enabled", ServerPrefs.isWarmupEnabled(serviceContext))
        put("keep_alive_enabled", ServerPrefs.isKeepAliveEnabled(serviceContext))
        put("keep_alive_minutes", ServerPrefs.getKeepAliveMinutes(serviceContext))
        put("custom_prompts_enabled", ServerPrefs.isCustomPromptsEnabled(serviceContext))
        put("system_prompt", ServerPrefs.getSystemPrompt(serviceContext, modelPrefsKey))
      }
      return httpOkJson(current.toString())
    }
    val obj = try {
      Json.parseToJsonElement(body).jsonObject
    } catch (e: Exception) {
      return httpBadRequest("Invalid JSON body: ${e.message?.take(200) ?: "parse error"}")
    }
    return try {
      val updated = currentConfig.toMutableMap()
      val changes = mutableListOf<String>()
      parseConfigDouble(obj, "temperature")?.let { raw ->
        val old = currentConfig.configTemperature()
        val v = clampTemperature(raw)
        updated[ConfigKeys.TEMPERATURE.label] = v
        changes.add("Temperature: ${old ?: "unset"} → $v")
      }
      parseConfigInt(obj, "max_tokens")?.let { raw ->
        val old = currentConfig.maxTokensInt()
        val v = clampMaxTokens(raw)
        updated[ConfigKeys.MAX_TOKENS.label] = v
        changes.add("Max Tokens: ${old ?: "unset"} → $v")
      }
      parseConfigInt(obj, "top_k")?.let { raw ->
        val old = currentConfig.configTopK()
        val v = clampTopK(raw)
        updated[ConfigKeys.TOPK.label] = v
        changes.add("Top-K: ${old ?: "unset"} → $v")
      }
      parseConfigDouble(obj, "top_p")?.let { raw ->
        val old = currentConfig.configTopP()
        val v = clampTopP(raw)
        updated[ConfigKeys.TOPP.label] = v
        changes.add("Top-P: ${old ?: "unset"} → $v")
      }
      parseConfigBool(obj, "thinking_enabled")?.let { v ->
        if (model == null || model.llmSupportThinking) {
          val old = currentConfig.configThinkingEnabled() ?: false
          updated[ConfigKeys.ENABLE_THINKING.label] = v
          ServerMetrics.setThinkingEnabled(v)
          changes.add("Thinking: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
        }
      }
      // ── Behavior toggles (persisted directly to SharedPreferences, not model configValues) ──
      parseConfigBool(obj, "auto_truncate_history")?.let { v ->
        val old = ServerPrefs.isAutoTruncateHistory(serviceContext)
        ServerPrefs.setAutoTruncateHistory(serviceContext, v)
        changes.add("Auto Truncate History: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      parseConfigBool(obj, "auto_trim_prompts")?.let { v ->
        val old = ServerPrefs.isAutoTrimPrompts(serviceContext)
        ServerPrefs.setAutoTrimPrompts(serviceContext, v)
        changes.add("Auto Trim Prompts: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      parseConfigBool(obj, "compact_tool_schemas")?.let { v ->
        val old = ServerPrefs.isCompactToolSchemas(serviceContext)
        ServerPrefs.setCompactToolSchemas(serviceContext, v)
        changes.add("Compact Tool Schemas: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      parseConfigBool(obj, "warmup_enabled")?.let { v ->
        val old = ServerPrefs.isWarmupEnabled(serviceContext)
        ServerPrefs.setWarmupEnabled(serviceContext, v)
        changes.add("Warmup: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      parseConfigBool(obj, "keep_alive_enabled")?.let { v ->
        val old = ServerPrefs.isKeepAliveEnabled(serviceContext)
        ServerPrefs.setKeepAliveEnabled(serviceContext, v)
        if (v) modelLifecycle.resetKeepAliveTimer() else modelLifecycle.cancelKeepAliveTimer()
        changes.add("Keep Alive: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      parseConfigInt(obj, "keep_alive_minutes")?.let { v ->
        if (v < 1 || v > 7200) {
          return httpBadRequest("keep_alive_minutes out of range (1–7200)")
        }
        val old = ServerPrefs.getKeepAliveMinutes(serviceContext)
        ServerPrefs.setKeepAliveMinutes(serviceContext, v)
        if (ServerPrefs.isKeepAliveEnabled(serviceContext)) modelLifecycle.resetKeepAliveTimer()
        changes.add("Keep Alive Minutes: $old → $v")
      }
      parseConfigBool(obj, "custom_prompts_enabled")?.let { v ->
        val old = ServerPrefs.isCustomPromptsEnabled(serviceContext)
        ServerPrefs.setCustomPromptsEnabled(serviceContext, v)
        changes.add("Custom Prompts: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      parseConfigString(obj, "system_prompt")?.let { v ->
        val old = ServerPrefs.getSystemPrompt(serviceContext, modelPrefsKey)
        ServerPrefs.setSystemPrompt(serviceContext, modelPrefsKey, v)
        val oldDisplay = if (old.isBlank()) "(empty)" else "\"${old.take(40)}${if (old.length > 40) "…" else ""}\""
        val newDisplay = if (v.isBlank()) "(empty)" else "\"${v.take(40)}${if (v.length > 40) "…" else ""}\""
        changes.add("System Prompt: $oldDisplay → $newDisplay")
      }
      if (changes.isEmpty()) {
        httpBadRequest("No recognized config fields")
      } else {
        // Update in-memory config if model is loaded, always persist to prefs
        if (model != null) {
          synchronized(inferenceLock) { model.configValues = updated.toMap() }
        }
        ServerPrefs.setInferenceConfig(serviceContext, modelPrefsKey, updated)
        // Log using the same format as the Settings UI
        RequestLogStore.addEvent(
          "Config via REST API (${changes.size} ${if (changes.size == 1) "change" else "changes"})",
          modelName = modelName,
          category = EventCategory.SETTINGS,
          body = changes.joinToString("\n"),
        )
        val current = buildJsonObject {
          put("success", true)
          put("model", modelName)
          put("model_loaded", !isIdle)
          put("temperature", updated.configTemperature()?.toDouble() ?: 0.0)
          put("max_tokens", updated.maxTokensInt() ?: 0)
          put("top_k", updated.configTopK() ?: 0)
          put("top_p", updated.configTopP()?.toDouble() ?: 0.0)
          put("thinking_enabled", updated.configThinkingEnabled() ?: false)
          put("auto_truncate_history", ServerPrefs.isAutoTruncateHistory(serviceContext))
          put("auto_trim_prompts", ServerPrefs.isAutoTrimPrompts(serviceContext))
          put("compact_tool_schemas", ServerPrefs.isCompactToolSchemas(serviceContext))
          put("warmup_enabled", ServerPrefs.isWarmupEnabled(serviceContext))
          put("keep_alive_enabled", ServerPrefs.isKeepAliveEnabled(serviceContext))
          put("keep_alive_minutes", ServerPrefs.getKeepAliveMinutes(serviceContext))
          put("custom_prompts_enabled", ServerPrefs.isCustomPromptsEnabled(serviceContext))
          put("system_prompt", ServerPrefs.getSystemPrompt(serviceContext, modelPrefsKey))
        }
        httpOkJson(current.toString())
      }
    } catch (e: ConfigFieldException) {
      httpBadRequest(e.message ?: "Invalid config field '${e.fieldName}'")
    } catch (e: Exception) {
      httpBadRequest("Invalid request body: ${e.message?.take(200) ?: "unknown error"}")
    }
  }

  // ── Shared route handlers ─────────────────────────────────────────────────

  private suspend fun handleHealth(call: ApplicationCall) {
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

internal class ConfigFieldException(
  val fieldName: String,
  expectedType: String,
  cause: Throwable? = null,
) : IllegalArgumentException("Invalid value for '$fieldName': expected $expectedType", cause)

internal fun parseConfigDouble(obj: JsonObject, field: String): Double? {
  if (!obj.containsKey(field)) return null
  return try {
    obj.getValue(field).jsonPrimitive.double
  } catch (e: Exception) {
    throw ConfigFieldException(field, "number", e)
  }
}

internal fun parseConfigInt(obj: JsonObject, field: String): Int? {
  if (!obj.containsKey(field)) return null
  return try {
    obj.getValue(field).jsonPrimitive.int
  } catch (e: Exception) {
    throw ConfigFieldException(field, "integer", e)
  }
}

internal fun parseConfigBool(obj: JsonObject, field: String): Boolean? {
  if (!obj.containsKey(field)) return null
  return try {
    obj.getValue(field).jsonPrimitive.boolean
  } catch (e: Exception) {
    throw ConfigFieldException(field, "boolean", e)
  }
}

internal fun parseConfigString(obj: JsonObject, field: String): String? {
  if (!obj.containsKey(field)) return null
  return try {
    obj.getValue(field).jsonPrimitive.content
  } catch (e: Exception) {
    throw ConfigFieldException(field, "string", e)
  }
}

/**
 * Reads all bytes from [source] up to [maxBytes]. Throws [java.io.IOException]
 * if the source contains more than [maxBytes], preventing unbounded heap allocation
 * from multipart uploads with missing or spoofed Content-Length headers.
 */
internal fun readBytesWithLimit(source: kotlinx.io.Source, maxBytes: Long): ByteArray {
  val buffer = kotlinx.io.Buffer()
  var totalRead = 0L
  while (true) {
    val chunk = source.readAtMostTo(buffer, minOf(8192L, maxBytes + 1 - totalRead))
    if (chunk == -1L) break
    totalRead += chunk
    if (totalRead > maxBytes) {
      throw java.io.IOException("File exceeds $maxBytes byte limit")
    }
  }
  return buffer.readByteArray()
}
