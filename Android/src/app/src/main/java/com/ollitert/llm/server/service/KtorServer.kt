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
import com.ollitert.llm.server.data.ClientIpAccessPolicy
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.EventCategory
import com.ollitert.llm.server.data.LogLevel
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RequestLogEntry
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.configTemperature
import com.ollitert.llm.server.data.configThinkingEnabled
import com.ollitert.llm.server.data.configTopK
import com.ollitert.llm.server.data.configTopP
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.data.maxTokensInt
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.routes.audioRoutes
import com.ollitert.llm.server.service.routes.chatRoutes
import com.ollitert.llm.server.service.routes.managementRoutes
import com.ollitert.llm.server.service.routes.modelRoutes
import com.ollitert.llm.server.service.routes.serverControlRoutes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.HttpRequestLifecycle
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "OlliteRT.Server"

private val INFERENCE_PATHS = setOf(
  "/generate", "/v1/completions", "/v1/chat/completions", "/v1/responses", "/v1/audio/transcriptions",
  "/v1/messages", "/v1/messages/count_tokens",
)

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

  internal fun nextLogId() = "log-${System.currentTimeMillis()}-${logIdCounter.incrementAndGet()}"

  private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

  // @Volatile on ModelLifecycle.defaultModel guarantees atomic reference reads without the
  // keepAliveLock. Ktor request threads see a consistent snapshot (either the old or new Model
  // reference). Model selection and locking happen deeper in selectModel()/InferenceRunner.
  internal val defaultModel: Model? get() = modelLifecycle.defaultModel
  internal val keepAliveUnloadedModelName: String? get() = modelLifecycle.keepAliveUnloadedModelName

  private val behaviorToggles = listOf(
    BooleanToggle("auto_truncate_history", "Auto Truncate History",
      ServerPrefs::isAutoTruncateHistory, ServerPrefs::setAutoTruncateHistory),
    BooleanToggle("auto_trim_prompts", "Auto Trim Prompts",
      ServerPrefs::isAutoTrimPrompts, ServerPrefs::setAutoTrimPrompts),
    BooleanToggle("warmup_enabled", "Warmup",
      ServerPrefs::isWarmupEnabled, ServerPrefs::setWarmupEnabled),
    BooleanToggle("keep_alive_enabled", "Keep Alive",
      ServerPrefs::isKeepAliveEnabled, ServerPrefs::setKeepAliveEnabled,
      onChanged = { v -> if (v) modelLifecycle.resetKeepAliveTimer() else modelLifecycle.cancelKeepAliveTimer() }),
    BooleanToggle("custom_prompts_enabled", "Custom Prompts",
      ServerPrefs::isCustomPromptsEnabled, ServerPrefs::setCustomPromptsEnabled),
  )

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
      configureClientIpAccess()
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

  // ── CORS ──────────────────────────────────────────────────────────────────

  /** Atomically applies a validated policy without restarting Ktor or unloading the model. */
  fun updateClientIpAccessPolicy(policy: ClientIpAccessPolicy) {
    clientIpAccessPolicy.set(policy)
  }

  /**
   * Rejects blocked peers before routing, authentication, body reads, or inference admission.
   * The direct socket address is intentional: forwarded headers are client-controlled unless a
   * trusted reverse-proxy boundary is explicitly configured.
   */
  private fun Application.configureClientIpAccess() {
    intercept(ApplicationCallPipeline.Plugins) {
      val applicationCall = context
      val remoteAddress = applicationCall.request.local.remoteAddress
      if (clientIpAccessPolicy.get().allows(remoteAddress)) return@intercept

      val logId = nextLogId()
      val response = httpJsonError(403, "client_ip_not_allowed")
      RequestLogStore.add(
        RequestLogEntry(
          id = logId,
          method = applicationCall.request.local.method.value,
          path = applicationCall.request.uri,
          responseBody = response.body,
          statusCode = response.statusCode,
          modelName = defaultModel?.name ?: keepAliveUnloadedModelName,
          clientIp = remoteAddress,
          level = LogLevel.WARNING,
        ),
      )
      applicationCall.response.headers.append("x-request-id", logId)
      applicationCall.respondHttpResponse(response)
      finish()
    }
  }

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
  internal suspend fun requireAuth(call: ApplicationCall): Boolean {
    val expected = ServerPrefs.getBearerToken(serviceContext)
    if (expected.isBlank()) return true // Auth disabled
    val header = call.request.headers["Authorization"] ?: ""
    if (BridgeUtils.isBearerAuthorized(expected, header)) return true
    // Anthropic SDKs and Claude Code authenticate via x-api-key with no Bearer prefix.
    val apiKey = call.request.headers["x-api-key"] ?: ""
    if (BridgeUtils.isApiKeyAuthorized(expected, apiKey)) return true
    call.respondHttpResponse(httpUnauthorized("unauthorized"))
    return false
  }

  // Gates /v1/server/* endpoints behind the HA integration toggle.
  // Returns 404 when disabled so the endpoints appear non-existent to scanners.
  internal suspend fun requireServerControl(call: ApplicationCall): Boolean {
    if (!ServerPrefs.isHaIntegrationEnabled(serviceContext)) {
      call.respondHttpResponse(httpNotFound())
      return false
    }
    return requireAuth(call)
  }

  // ── Response dispatcher ───────────────────────────────────────────────────

  /**
   * Converts an [HttpResponse] sealed class into the appropriate Ktor response.
   * This bridges the handler layer (which returns [HttpResponse]) with Ktor's
   * response API.
   */
  internal suspend fun ApplicationCall.respondHttpResponse(resp: HttpResponse) {
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
        // Timeout prevents indefinite hang if cancelProcess fails in the streaming handler.
        respondTextWriter(contentType = ContentType.Text.EventStream) {
          kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            // Outer safety-net timeout from the response itself (derived from the
            // user's configurable per-endpoint timeout). Must exceed the inner
            // streaming timeout so it only fires if inner cleanup hangs.
            kotlinx.coroutines.withTimeout(resp.outerTimeoutMs) {
              val writer = KtorSseWriterImpl(this@respondTextWriter)
              resp.writer(writer)
            }
          }
        }
      }
    }
  }

  // ── Request logging middleware ─────────────────────────────────────────────

  /**
   * Lightweight logging wrapper for GET routes. Creates a log entry, runs the
   * handler, finalizes with status/latency, and sends the response. No body
   * parsing or OOM protection needed — GET routes have no request body.
   */
  internal suspend fun withGetLogging(
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
    var admission: ModelLifecycle.RequestAdmission? = null
    try {
      withRequestLoggingBody(call, handler) {
        if (admitModelRequest) admission = modelLifecycle.acquireRequestAdmission()
      }
    } finally {
      admission?.close()
    }
  }

  private suspend fun withRequestLoggingBody(
    call: ApplicationCall,
    handler: suspend (
      body: String,
      captureBody: (String) -> Unit,
      captureResponse: (String) -> Unit,
      logId: String,
      sseExtraHeaders: Map<String, String>,
      prefs: RequestPrefsSnapshot,
    ) -> HttpResponse,
    beforeHandler: () -> Unit,
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
      // Defense-in-depth: reject obviously oversized requests before allocating memory.
      // The OOM catch below is the safety net for spoofed/missing Content-Length.
      val contentLength = call.request.headers["Content-Length"]?.toLongOrNull()
      if (contentLength != null && contentLength > MAX_FILE_SIZE_BYTES) {
        val tooLargeResponse = httpPayloadTooLarge("Request body too large (${contentLength / 1_000_000}MB). Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
        requestBodySnapshot = "[Content-Length exceeded: $contentLength]"
        finalizeLogEntry(logId, startMs, tooLargeResponse, requestBodySnapshot, responseBodySnapshot)
        call.response.headers.append("x-request-id", logId)
        call.respondHttpResponse(tooLargeResponse)
        return
      }

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

      if (prefs.rejectWhenBusy && ServerMetrics.isInferring.value) {
        val busyResponse = httpServiceUnavailable(
          "Server is busy processing another request. Disable \"Reject Requests When Busy\" in settings to queue instead.",
        )
        requestBodySnapshot = body
        finalizeLogEntry(logId, startMs, busyResponse, requestBodySnapshot, responseBodySnapshot)
        call.response.headers.append("x-request-id", logId)
        call.respondHttpResponse(busyResponse)
        return
      }

      beforeHandler()
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
        modelLifecycle.defaultModel = null
        ServerMetrics.onServerError(t.message ?: "Out of memory")
      }
      val errorCategory = if (t is OutOfMemoryError) ErrorCategory.INFERENCE else ErrorCategory.SYSTEM
      ServerMetrics.incrementErrorCount(errorCategory)
      emitDebugStackTrace(t, "ktor_serve_catch_all", null)
      responseBodySnapshot = t.message
      val errorMsg = if (t is OutOfMemoryError) "out_of_memory" else "internal_error"
      val suggestion = if (t is OutOfMemoryError) "Try a smaller model or reduce max_tokens" else null
      httpInternalError(errorMsg, suggestion)
    }

    finalizeLogEntry(logId, startMs, response, requestBodySnapshot, responseBodySnapshot)

    // Set x-request-id response header for request tracing (Open WebUI, etc.)
    call.response.headers.append("x-request-id", logId)
    call.respondHttpResponse(response)

    // Reset keep-alive idle timer after successful POST requests (inference routes
    // that touch the model). Non-inference GET routes don't reset it.
    if (response.statusCode in 200..299) {
      modelLifecycle.resetKeepAliveTimer()
    }
  }

  /**
   * Finalizes a log entry with status code, latency, streaming detection,
   * and per-request performance metrics. For streaming responses, metadata
   * is set but isPending is left for the streaming callbacks to finalize.
   */
  internal fun finalizeLogEntry(
    logId: String,
    startMs: Long,
    response: HttpResponse,
    requestBodySnapshot: String?,
    responseBodySnapshot: String?,
  ) {
    val elapsedMs = SystemClock.elapsedRealtime() - startMs
    val statusCode = response.statusCode
    val isStreaming = response is HttpResponse.Sse
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
      val isThinking = ServerMetrics.thinkingEnabled.value && it.path in INFERENCE_PATHS
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
  internal fun handleServerStop(): HttpResponse {
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
  internal fun handleServerReload(): HttpResponse {
    val modelName = defaultModel?.name ?: keepAliveUnloadedModelName
      ?: return httpBadRequest("No model loaded")
    val reloadPort = ServerMetrics.port.value
    ServerService.reload(serviceContext, reloadPort, modelName)
    val result = buildJsonObject {
      put("success", true)
      put("message", "Model reloading")
      put("model", modelName)
    }
    return httpOkJson(result.toString())
  }

  private data class ModelContext(
    val model: Model?,
    val isIdle: Boolean,
    val modelName: String,
    val modelPrefsKey: String,
  )

  private fun resolveModelContext(): ModelContext? {
    val model = defaultModel
    val isIdle = ServerMetrics.isIdleUnloaded.value
    val modelName = model?.name ?: keepAliveUnloadedModelName ?: return null
    val modelPrefsKey = model?.prefsKey ?: modelLifecycle.keepAliveUnloadedModelPrefsKey ?: return null
    return ModelContext(model, isIdle, modelName, modelPrefsKey)
  }

  /**
   * Handles POST /v1/server/thinking — toggle thinking mode on/off.
   */
  internal fun handleServerThinking(body: String): HttpResponse {
    val ctx = resolveModelContext() ?: return httpBadRequest("No model loaded")
    val (model, isIdle, modelName, modelPrefsKey) = ctx
    if (model != null && !model.llmSupportThinking) {
      return httpBadRequest("Model does not support thinking")
    }
    val currentState: Boolean
    val requestedState: Boolean
    val updatedConfig: Map<String, Any>
    if (model != null) {
      synchronized(inferenceLock) {
        val config = model.configValues
        currentState = config.configThinkingEnabled() ?: false
        requestedState = parseThinkingRequestedState(body, currentState)
          ?: return httpBadRequest("Invalid JSON in request body")
        updatedConfig = config + (ConfigKeys.ENABLE_THINKING.id to requestedState)
        model.configValues = updatedConfig
      }
    } else {
      val config = ServerPrefs.getInferenceConfig(serviceContext, modelPrefsKey)
      currentState = config?.configThinkingEnabled() ?: false
      requestedState = parseThinkingRequestedState(body, currentState)
        ?: return httpBadRequest("Invalid JSON in request body")
      updatedConfig = (config ?: emptyMap()) + (ConfigKeys.ENABLE_THINKING.id to requestedState)
    }
    ServerPrefs.setInferenceConfig(serviceContext, modelPrefsKey, updatedConfig)
    ServerMetrics.setThinkingEnabled(requestedState)
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
      if (isIdle) put("warning", "Model is idle-unloaded; thinking support cannot be verified until reload")
    }
    return httpOkJson(result.toString())
  }

  /**
   * Handles POST /v1/server/config — update inference settings.
   */
  internal fun handleServerConfig(body: String): HttpResponse {
    val ctx = resolveModelContext() ?: return httpBadRequest("No model loaded")
    val (model, isIdle, modelName, modelPrefsKey) = ctx
    if (body.isBlank()) {
      val currentConfig = if (model != null) {
        synchronized(inferenceLock) { model.configValues }
      } else {
        ServerPrefs.getInferenceConfig(serviceContext, modelPrefsKey) ?: emptyMap()
      }
      return httpOkJson(
        PayloadBuilders.serverConfig(currentConfig, modelName, !isIdle, modelPrefsKey, serviceContext),
      )
    }
    val obj = try {
      Json.parseToJsonElement(body).jsonObject
    } catch (e: Exception) {
      return httpBadRequest("Invalid JSON body: ${e.message?.take(200) ?: "parse error"}")
    }
    return try {
      val reqTemperature = parseConfigDouble(obj, "temperature")
      val reqMaxTokens = parseConfigInt(obj, "max_tokens")
      val reqTopK = parseConfigInt(obj, "top_k")
      val reqTopP = parseConfigDouble(obj, "top_p")
      val reqThinking = parseConfigBool(obj, "thinking_enabled")
      val updated: Map<String, Any>
      val configChanges: MutableList<String>
      if (model != null) {
        synchronized(inferenceLock) {
          val result = mergeInferenceConfig(
            model.configValues, model, reqTemperature, reqMaxTokens, reqTopK, reqTopP, reqThinking,
          )
          updated = result.first
          configChanges = result.second
          if (configChanges.isNotEmpty()) model.configValues = updated
        }
      } else {
        val base = ServerPrefs.getInferenceConfig(serviceContext, modelPrefsKey) ?: emptyMap()
        val result = mergeInferenceConfig(
          base, null, reqTemperature, reqMaxTokens, reqTopK, reqTopP, reqThinking,
        )
        updated = result.first
        configChanges = result.second
      }
      val changes = configChanges.toMutableList()
      applyBehaviorToggles(obj, changes)
      val specialFieldError = applySpecialFields(obj, modelPrefsKey, changes)
      if (specialFieldError != null) return specialFieldError
      if (changes.isEmpty()) {
        httpBadRequest("No recognized config fields")
      } else {
        ServerPrefs.setInferenceConfig(serviceContext, modelPrefsKey, updated)
        RequestLogStore.addEvent(
          "Config via REST API (${changes.size} ${if (changes.size == 1) "change" else "changes"})",
          modelName = modelName,
          category = EventCategory.SETTINGS,
          body = changes.joinToString("\n"),
        )
        httpOkJson(
          PayloadBuilders.serverConfig(updated, modelName, !isIdle, modelPrefsKey, serviceContext, success = true),
        )
      }
    } catch (e: ConfigFieldException) {
      httpBadRequest(e.message ?: "Invalid config field '${e.fieldName}'")
    } catch (e: Exception) {
      httpBadRequest("Invalid request body: ${e.message?.take(200) ?: "unknown error"}")
    }
  }

  private fun mergeInferenceConfig(
    currentConfig: Map<String, Any>,
    model: Model?,
    reqTemperature: Double?,
    reqMaxTokens: Int?,
    reqTopK: Int?,
    reqTopP: Double?,
    reqThinking: Boolean?,
  ): Pair<Map<String, Any>, MutableList<String>> {
    val updated = currentConfig.toMutableMap()
    val changes = mutableListOf<String>()
    reqTemperature?.let { raw ->
      val old = currentConfig.configTemperature()
      val v = clampTemperature(raw)
      updated[ConfigKeys.TEMPERATURE.id] = v
      changes.add("Temperature: ${old ?: "unset"} → $v")
    }
    reqMaxTokens?.let { raw ->
      val old = currentConfig.maxTokensInt()
      val v = clampMaxTokens(raw)
      updated[ConfigKeys.MAX_TOKENS.id] = v
      changes.add("Max Tokens: ${old ?: "unset"} → $v")
    }
    reqTopK?.let { raw ->
      val old = currentConfig.configTopK()
      val v = clampTopK(raw)
      updated[ConfigKeys.TOPK.id] = v
      changes.add("Top-K: ${old ?: "unset"} → $v")
    }
    reqTopP?.let { raw ->
      val old = currentConfig.configTopP()
      val v = clampTopP(raw)
      updated[ConfigKeys.TOPP.id] = v
      changes.add("Top-P: ${old ?: "unset"} → $v")
    }
    reqThinking?.let { v ->
      if (model == null || model.llmSupportThinking) {
        val old = currentConfig.configThinkingEnabled() ?: false
        updated[ConfigKeys.ENABLE_THINKING.id] = v
        ServerMetrics.setThinkingEnabled(v)
        changes.add("Thinking: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
    }
    return updated.toMap() to changes
  }

  private fun parseThinkingRequestedState(body: String, currentState: Boolean): Boolean? {
    if (body.isNotBlank()) {
      val obj = try {
        Json.parseToJsonElement(body).jsonObject
      } catch (_: Exception) {
        return null
      }
      return obj["enabled"]?.jsonPrimitive?.booleanOrNull ?: !currentState
    }
    return !currentState
  }

  private fun applyBehaviorToggles(obj: JsonObject, changes: MutableList<String>) {
    for (toggle in behaviorToggles) {
      parseConfigBool(obj, toggle.jsonKey)?.let { v ->
        val old = toggle.read(serviceContext)
        toggle.write(serviceContext, v)
        toggle.onChanged?.invoke(v)
        changes.add("${toggle.displayName}: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
    }
  }

  private fun applySpecialFields(
    obj: JsonObject,
    modelPrefsKey: String,
    changes: MutableList<String>,
  ): HttpResponse? {
    parseConfigInt(obj, "keep_alive_minutes")?.let { v ->
      if (v < 1 || v > 7200) {
        return httpBadRequest("keep_alive_minutes out of range (1–7200)")
      }
      val old = ServerPrefs.getKeepAliveMinutes(serviceContext)
      ServerPrefs.setKeepAliveMinutes(serviceContext, v)
      if (ServerPrefs.isKeepAliveEnabled(serviceContext)) modelLifecycle.resetKeepAliveTimer()
      changes.add("Keep Alive Minutes: $old → $v")
    }
    parseConfigString(obj, "system_prompt")?.let { v ->
      val old = ServerPrefs.getSystemPrompt(serviceContext, modelPrefsKey)
      ServerPrefs.setSystemPrompt(serviceContext, modelPrefsKey, v)
      val oldDisplay = if (old.isBlank()) "(empty)" else "\"${old.take(40)}${if (old.length > 40) "…" else ""}\""
      val newDisplay = if (v.isBlank()) "(empty)" else "\"${v.take(40)}${if (v.length > 40) "…" else ""}\""
      changes.add("System Prompt: $oldDisplay → $newDisplay")
    }
    return null
  }

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
      val body = PayloadBuilders.modelsList(defaultModel, keepAliveUnloadedModelName, json)
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

private sealed interface BehaviorSetting {
  val jsonKey: String
  val displayName: String
}

private class BooleanToggle(
  override val jsonKey: String,
  override val displayName: String,
  val read: (Context) -> Boolean,
  val write: (Context, Boolean) -> Unit,
  val onChanged: ((Boolean) -> Unit)? = null,
) : BehaviorSetting

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
