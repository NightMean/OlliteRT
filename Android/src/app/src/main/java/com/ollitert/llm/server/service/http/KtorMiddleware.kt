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
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.data.prefs.CORS_PREFLIGHT_MAX_AGE_SECONDS
import com.ollitert.llm.server.data.prefs.ClientIpAccessPolicy
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.response.respondTextWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "OlliteRT.Middleware"

internal val INFERENCE_PATHS = setOf(
  "/generate", "/v1/completions", "/v1/chat/completions", "/v1/responses", "/v1/audio/transcriptions",
  "/v1/messages", "/v1/messages/count_tokens",
)

/**
 * Converts an [HttpResponse] sealed class into the appropriate Ktor response.
 * This bridges the handler layer (which returns [HttpResponse]) with Ktor's response API.
 */
suspend fun ApplicationCall.respondHttpResponse(resp: HttpResponse) {
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
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
          kotlinx.coroutines.withTimeout(resp.outerTimeoutMs) {
            val writer = KtorSseWriterImpl(this@respondTextWriter)
            resp.writer(writer)
          }
        }
      }
    }
  }
}

/**
 * Checks bearer token authorization. Returns `true` if authorized or auth is disabled.
 */
suspend fun requireAuth(call: ApplicationCall, serviceContext: Context): Boolean {
  val expected = ServerPrefs.getBearerToken(serviceContext)
  if (expected.isBlank()) return true
  val header = call.request.headers["Authorization"] ?: ""
  if (BridgeUtils.isBearerAuthorized(expected, header)) return true
  val apiKey = call.request.headers["x-api-key"] ?: ""
  if (BridgeUtils.isApiKeyAuthorized(expected, apiKey)) return true
  call.respondHttpResponse(httpUnauthorized("unauthorized"))
  return false
}

/**
 * Gates server management endpoints behind the HA integration toggle.
 */
suspend fun requireServerControl(call: ApplicationCall, serviceContext: Context): Boolean {
  if (!ServerPrefs.isHaIntegrationEnabled(serviceContext)) {
    call.respondHttpResponse(httpNotFound())
    return false
  }
  return requireAuth(call, serviceContext)
}

/**
 * Rejects blocked peers before routing, authentication, body reads, or inference admission.
 */
fun Application.configureClientIpAccess(
  policy: AtomicReference<ClientIpAccessPolicy>,
  nextLogId: () -> String,
  modelNameProvider: () -> String?,
) {
  intercept(ApplicationCallPipeline.Plugins) {
    val applicationCall = context
    val remoteAddress = applicationCall.request.local.remoteAddress
    if (policy.get().allows(remoteAddress)) return@intercept

    val logId = nextLogId()
    val response = httpJsonError(403, "client_ip_not_allowed")
    RequestLogStore.add(
      RequestLogEntry(
        id = logId,
        method = applicationCall.request.local.method.value,
        path = applicationCall.request.uri,
        responseBody = response.body,
        statusCode = response.statusCode,
        modelName = modelNameProvider(),
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
 */
fun Application.configureCors(serviceContext: Context) {
  val allowedOrigins = ServerPrefs.getCorsAllowedOrigins(serviceContext).trim()
  if (allowedOrigins.isEmpty()) return

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

/**
 * Lightweight logging wrapper for GET routes.
 */
suspend fun withGetLogging(
  call: ApplicationCall,
  serviceContext: Context,
  defaultModel: Model?,
  keepAliveUnloadedModelName: String?,
  nextLogId: () -> String,
  emitDebugStackTrace: (Throwable, String, String?) -> Unit,
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
  finalizeLogEntry(logId, startMs, response, requestBodySnapshot = null, responseBodySnapshot = responseBodySnapshot, defaultModelName = defaultModel?.name)
  call.response.headers.append("x-request-id", logId)
  call.respondHttpResponse(response)
}

/**
 * Wraps a POST route handler with request logging, OOM protection, and keep-alive resetting.
 */
suspend fun withRequestLogging(
  call: ApplicationCall,
  serviceContext: Context,
  defaultModel: Model?,
  keepAliveUnloadedModelName: String?,
  nextLogId: () -> String,
  emitDebugStackTrace: (Throwable, String, String?) -> Unit,
  modelLifecycle: ModelLifecycle,
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
    withRequestLoggingBody(
      call = call,
      serviceContext = serviceContext,
      defaultModel = defaultModel,
      keepAliveUnloadedModelName = keepAliveUnloadedModelName,
      nextLogId = nextLogId,
      emitDebugStackTrace = emitDebugStackTrace,
      modelLifecycle = modelLifecycle,
      handler = handler,
    ) {
      if (admitModelRequest) admission = modelLifecycle.acquireRequestAdmission()
    }
  } finally {
    admission?.close()
  }
}

private suspend fun withRequestLoggingBody(
  call: ApplicationCall,
  serviceContext: Context,
  defaultModel: Model?,
  keepAliveUnloadedModelName: String?,
  nextLogId: () -> String,
  emitDebugStackTrace: (Throwable, String, String?) -> Unit,
  modelLifecycle: ModelLifecycle,
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

  val sseExtraHeaders = mapOf("x-request-id" to logId)
  var requestBodySnapshot: String? = null
  var responseBodySnapshot: String? = null

  val response: HttpResponse = try {
    val contentLength = call.request.headers["Content-Length"]?.toLongOrNull()
    if (contentLength != null && contentLength > MAX_FILE_SIZE_BYTES) {
      val tooLargeResponse = httpPayloadTooLarge("Request body too large (${contentLength / 1_000_000}MB). Maximum: ${MAX_FILE_SIZE_BYTES / 1_000_000}MB.")
      requestBodySnapshot = "[Content-Length exceeded: $contentLength]"
      finalizeLogEntry(logId, startMs, tooLargeResponse, requestBodySnapshot, responseBodySnapshot, defaultModel?.name)
      call.response.headers.append("x-request-id", logId)
      call.respondHttpResponse(tooLargeResponse)
      return
    }

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
      finalizeLogEntry(logId, startMs, oomResponse, requestBodySnapshot, responseBodySnapshot, defaultModel?.name)
      call.response.headers.append("x-request-id", logId)
      call.respondHttpResponse(oomResponse)
      return
    }

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
      finalizeLogEntry(logId, startMs, busyResponse, requestBodySnapshot, responseBodySnapshot, defaultModel?.name)
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

  finalizeLogEntry(logId, startMs, response, requestBodySnapshot, responseBodySnapshot, defaultModel?.name)

  call.response.headers.append("x-request-id", logId)
  call.respondHttpResponse(response)

  if (response.statusCode in 200..299) {
    modelLifecycle.resetKeepAliveTimer()
  }
}

/**
 * Finalizes a log entry with status code, latency, streaming detection,
 * and per-request performance metrics.
 */
fun finalizeLogEntry(
  logId: String,
  startMs: Long,
  response: HttpResponse,
  requestBodySnapshot: String?,
  responseBodySnapshot: String?,
  defaultModelName: String?,
) {
  val elapsedMs = SystemClock.elapsedRealtime() - startMs
  val statusCode = response.statusCode
  val isStreaming = response is HttpResponse.Sse
  RequestLogStore.update(logId) {
    if (it.isCancelled) return@update it.copy(
      requestBody = requestBodySnapshot ?: it.requestBody,
      statusCode = if (it.statusCode == 200) statusCode else it.statusCode,
    )
    val level = when {
      statusCode !in 200..299 -> LogLevel.ERROR
      it.isCompacted -> LogLevel.WARNING
      else -> LogLevel.INFO
    }
    val finalResponseBody = if (isStreaming) it.responseBody
    else (responseBodySnapshot ?: it.responseBody)
    val actualTokens = finalResponseBody?.let { body ->
      InferenceRunner.extractActualTokenCounts(body)
    }
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
      modelName = defaultModelName,
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
