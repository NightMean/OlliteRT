package com.ollitert.llm.server.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.common.ErrorCategory
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

// ── HTTP Response Helpers ────────────────────────────────────────────────────
// Use NanoHTTPD.newFixedLengthResponse (static) so they work outside NanoHTTPD subclasses.
// Shared by LlmHttpServer (serve dispatcher) and LlmHttpEndpointHandlers (inference routes).

internal fun okJsonText(body: String): NanoHTTPD.Response =
  NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", body)

internal fun jsonError(
  status: NanoHTTPD.Response.Status,
  error: String,
  suggestion: String? = null,
  category: ErrorCategory? = null,
): NanoHTTPD.Response =
  NanoHTTPD.newFixedLengthResponse(status, "application/json", LlmHttpResponseRenderer.renderJsonError(error, suggestion, category))

internal fun badRequest(msg: String) = jsonError(NanoHTTPD.Response.Status.BAD_REQUEST, msg)
internal fun notFound(error: String = "not_found") = jsonError(NanoHTTPD.Response.Status.NOT_FOUND, error)
internal fun unauthorized(error: String) =
  jsonError(NanoHTTPD.Response.Status.UNAUTHORIZED, error).also { it.addHeader("WWW-Authenticate", "Bearer") }
internal fun methodNotAllowed() = jsonError(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed")
internal fun payloadTooLarge() = jsonError(NanoHTTPD.Response.Status.BAD_REQUEST, "payload_too_large")

/** SSE response from a pre-built payload string. Uses fixed-length since the full content is known. */
internal fun sseFixedResponse(payload: String): NanoHTTPD.Response {
  val resp = NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/event-stream", payload)
  resp.addHeader("Cache-Control", "no-cache")
  resp.addHeader("Connection", "keep-alive")
  return resp
}

// ── LlmHttpServer ────────────────────────────────────────────────────────────

/**
 * NanoHTTPD-based HTTP server. Routes requests to [LlmHttpEndpointHandlers] for
 * inference endpoints and handles server info, health, metrics, model listing,
 * and server control endpoints (stop/reload/thinking/config) directly.
 *
 * Separated from [LlmHttpService] to isolate HTTP concerns (routing, auth, CORS,
 * request/response logging) from Android Service lifecycle concerns.
 */
class LlmHttpServer(
  port: Int,
  private val serviceContext: Context,
  private val endpointHandlers: LlmHttpEndpointHandlers,
  private val modelLifecycle: LlmHttpModelLifecycle,
  private val json: kotlinx.serialization.json.Json,
  private val nextRequestId: () -> String,
  private val emitDebugStackTrace: (Throwable, String, String?) -> Unit,
) : NanoHTTPD("0.0.0.0", port) {

  private val logTag = "LlmHttpServer"

  // Convenience accessors for model state
  private val defaultModel: Model? get() = modelLifecycle.defaultModel
  private val keepAliveUnloadedModelName: String? get() = modelLifecycle.keepAliveUnloadedModelName

  override fun serve(session: IHTTPSession): Response {
    val startMs = SystemClock.elapsedRealtime()
    val method = session.method.name
    val path = session.uri
    val clientIp = session.remoteIpAddress
    // NanoHTTPD lowercases all header names
    val requestOrigin = session.headers["origin"]
    // Handle CORS preflight (no logging needed)
    if (session.method == Method.OPTIONS) {
      return corsOk(requestOrigin)
    }

    // Add a pending log entry immediately so it appears in the Logs tab
    val logId = "log-${System.currentTimeMillis()}-${System.nanoTime()}"
    RequestLogStore.add(
      RequestLogEntry(
        id = logId,
        method = method,
        path = path,
        modelName = defaultModel?.name ?: keepAliveUnloadedModelName,
        clientIp = clientIp,
        isPending = true,
      )
    )

    var requestBodySnapshot: String? = null
    var responseBodySnapshot: String? = null
    val response = try {
      if (!LlmHttpRouteResolver.isSupportedMethod(session.method)) {
        methodNotAllowed()
      } else {
        val route = LlmHttpRouteResolver.resolve(session.method, session.uri)
        val authError = if (route?.requiresAuth == true) requireAuth(session) else null
        if (route == null) {
          // Check if it's a known OpenAI endpoint we don't support
          val unsupportedMsg = LlmHttpRouteResolver.getUnsupportedEndpointMessage(session.uri)
          if (unsupportedMsg != null) jsonError(Response.Status.NOT_FOUND, unsupportedMsg)
          else notFound()
        } else if (authError != null) {
          authError
        } else {
          // Update the pending entry with the request body once parsed
          val captureBody = { body: String ->
            requestBodySnapshot = body
            RequestLogStore.update(logId) { it.copy(requestBody = body) }
          }
          when (route.handler) {
            LlmHttpRouteHandler.PING -> {
              val body = "{\"status\":\"ok\"}"
              responseBodySnapshot = body
              okJsonText(body)
            }
            LlmHttpRouteHandler.HEALTH -> {
              // session.parameters returns Map<String, List<String>> (non-deprecated API)
              val includeMetrics = session.parameters?.get("metrics")?.firstOrNull()?.equals("true", ignoreCase = true) == true
              val body = LlmHttpPayloadBuilders.health(defaultModel, keepAliveUnloadedModelName, includeMetrics)
              responseBodySnapshot = body
              okJsonText(body)
            }
            LlmHttpRouteHandler.SERVER_INFO -> {
              val body = LlmHttpPayloadBuilders.serverInfo(defaultModel)
              responseBodySnapshot = body
              okJsonText(body)
            }
            LlmHttpRouteHandler.VERSION -> {
              // Enhanced /api/version with update info from background UpdateCheckWorker
              val body = LlmHttpPayloadBuilders.serverInfo(defaultModel)
              responseBodySnapshot = body
              okJsonText(body)
            }
            LlmHttpRouteHandler.METRICS -> {
              val body = LlmHttpPrometheusRenderer.render()
              responseBodySnapshot = body
              NanoHTTPD.newFixedLengthResponse(
                Response.Status.OK,
                LlmHttpPrometheusRenderer.CONTENT_TYPE,
                body,
              )
            }
            LlmHttpRouteHandler.MODELS -> {
              val body = LlmHttpPayloadBuilders.modelsList(defaultModel, keepAliveUnloadedModelName, json)
              responseBodySnapshot = body
              okJsonText(body)
            }
            LlmHttpRouteHandler.MODEL_DETAIL -> {
              val body = LlmHttpPayloadBuilders.modelDetail(defaultModel, session.uri, json)
              if (body != null) {
                responseBodySnapshot = body
                okJsonText(body)
              } else {
                notFound("model_not_found")
              }
            }

            // ── Inference routes — delegated to LlmHttpEndpointHandlers ──
            LlmHttpRouteHandler.GENERATE -> endpointHandlers.handleGenerate(session, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
            LlmHttpRouteHandler.COMPLETIONS -> endpointHandlers.handleCompletions(session, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
            LlmHttpRouteHandler.CHAT_COMPLETIONS -> endpointHandlers.handleChatCompletion(session, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
            LlmHttpRouteHandler.RESPONSES -> endpointHandlers.handleResponses(session, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)

            // ── Server control endpoints ──
            // IMPORTANT: When adding new /v1/server/* endpoints, also update the HA YAML
            // template in SettingsScreen.kt (haConfig buildString block) with the new rest_command.
            LlmHttpRouteHandler.SERVER_STOP -> {
              // Trigger graceful shutdown via the same intent the notification Stop button uses.
              // The response is sent before the service actually stops.
              val stopIntent = Intent(serviceContext, LlmHttpService::class.java).apply {
                action = LlmHttpService.ACTION_STOP
              }
              serviceContext.startService(stopIntent)
              val body = """{"success":true,"message":"Server stopping"}"""
              responseBodySnapshot = body
              okJsonText(body)
            }
            LlmHttpRouteHandler.SERVER_RELOAD -> {
              // Trigger a model reload via the same intent the UI uses.
              val model = defaultModel
              if (model == null) {
                responseBodySnapshot = """{"success":false,"message":"No model loaded"}"""
                badRequest("No model loaded")
              } else {
                val reloadPort = ServerMetrics.port.value
                LlmHttpService.reload(serviceContext, reloadPort, model.name)
                val body = """{"success":true,"message":"Model reloading","model":"${model.name}"}"""
                responseBodySnapshot = body
                okJsonText(body)
              }
            }
            LlmHttpRouteHandler.SERVER_THINKING -> handleServerThinking(session).also { responseBodySnapshot = it.second; }.first
            LlmHttpRouteHandler.SERVER_CONFIG -> handleServerConfig(session).also { responseBodySnapshot = it.second }.first
          }
        }
      }
    } catch (t: Throwable) {
      if (t is OutOfMemoryError) {
        // Close the native Engine/Conversation before nullifying — just setting instance = null
        // leaks GB-scale native memory because GC may not finalize the wrapper promptly.
        defaultModel?.let { m ->
          try { ServerLlmModelHelper.cleanUp(m) {} } catch (_: Exception) {}
          m.instance = null
        }
        System.gc()
        ServerMetrics.onServerError(t.message ?: "Out of memory")
      }
      ServerMetrics.incrementErrorCount(ErrorCategory.SYSTEM)
      emitDebugStackTrace(t, "serve_catch_all", null)
      responseBodySnapshot = t.message
      jsonError(Response.Status.INTERNAL_ERROR, t.message ?: "internal_error")
    }

    // Finalize the log entry with response data.
    // For streaming responses (SSE), the streaming callbacks in streamLlm/streamChatLlm
    // handle their own log updates (partialText during generation, responseBody on done),
    // so we only update metadata here and leave isPending = true for them.
    val elapsedMs = SystemClock.elapsedRealtime() - startMs
    val statusCode = response.status?.requestStatus ?: 200
    val isStreaming = response.mimeType == "text/event-stream"
    val isThinking = responseBodySnapshot?.contains("<think>") == true
    RequestLogStore.update(logId) {
      // If the cancel handler already finalized this entry (user tapped Stop), don't overwrite it.
      if (it.isCancelled) return@update it.copy(
        requestBody = requestBodySnapshot ?: it.requestBody,
      )
      val level = when {
        statusCode !in 200..299 -> LogLevel.ERROR
        it.isCompacted -> LogLevel.WARNING
        else -> LogLevel.INFO
      }
      // For non-streaming error responses, the handler already set responseBody with the
      // detailed error JSON (e.g. from LiteRT). Preserve it if responseBodySnapshot is null
      // (captureResponse is only called for success responses).
      val finalResponseBody = if (isStreaming) it.responseBody
        else (responseBodySnapshot ?: it.responseBody)
      // Extract actual token counts from LiteRT error messages (e.g. "6579 >= 4000")
      // to replace our rough charLen/4 estimate with exact numbers from the engine.
      val actualTokens = finalResponseBody?.let { body -> LlmHttpInferenceRunner.extractActualTokenCounts(body) }
      // For non-streaming requests, read per-request performance metrics from ServerMetrics.
      // These were just set by runLlm() which holds the inference lock, so no interleaving.
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
    // Reset keep-alive idle timer after any inference request (POST routes that touch the model).
    // Non-inference GET routes (models, health, metrics) don't reset it — they shouldn't
    // keep the model loaded if only monitoring tools are polling.
    if (session.method == Method.POST && statusCode in 200..299) {
      modelLifecycle.resetKeepAliveTimer()
    }
    // x-request-id: standard request tracing header used by Open WebUI and other clients
    response.addHeader("x-request-id", logId)
    return addCorsHeaders(response, requestOrigin)
  }

  // ── Server control handlers ────────────────────────────────────────────────

  /**
   * Toggle thinking mode on/off for the active model.
   * Returns (Response, responseBodySnapshot) pair.
   */
  private fun handleServerThinking(session: IHTTPSession): Pair<Response, String?> {
    val model = defaultModel
    if (model == null) {
      val body = """{"success":false,"message":"No model loaded"}"""
      return badRequest("No model loaded") to body
    }
    if (!model.llmSupportThinking) {
      val body = """{"success":false,"message":"Model does not support thinking"}"""
      return badRequest("Model does not support thinking") to body
    }
    val payload = HashMap<String, String>()
    session.parseBody(payload)
    val raw = payload["postData"] ?: ""
    val currentState = model.configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean ?: false
    // Parse { "enabled": true/false } — default to toggling current state
    val requestedState = if (raw.isNotBlank()) {
      try {
        val obj = org.json.JSONObject(raw)
        obj.optBoolean("enabled", !currentState)
      } catch (_: Exception) {
        !currentState
      }
    } else {
      // No body = toggle
      !currentState
    }
    model.configValues = model.configValues.toMutableMap().apply {
      put(ConfigKeys.ENABLE_THINKING.label, requestedState)
    }
    // Persist and update metrics
    LlmHttpPrefs.setInferenceConfig(serviceContext, model.name, model.configValues)
    ServerMetrics.setThinkingEnabled(requestedState)
    // Log using the same "Settings updated" format as the Settings UI,
    // so the LogsScreen parser renders it with the proper card headline and arrow format.
    val oldLabel = if (currentState) "enabled" else "disabled"
    val newLabel = if (requestedState) "enabled" else "disabled"
    RequestLogStore.addEvent(
      "Config via REST API (1 change)",
      modelName = model.name,
      category = EventCategory.SETTINGS,
      body = "Thinking: $oldLabel → $newLabel",
    )
    val body = """{"success":true,"thinking_enabled":$requestedState,"model":"${model.name}"}"""
    return okJsonText(body) to body
  }

  /**
   * Update inference settings (temperature, max_tokens, top_k, top_p, thinking_enabled).
   * Returns (Response, responseBodySnapshot) pair.
   */
  private fun handleServerConfig(session: IHTTPSession): Pair<Response, String?> {
    val model = defaultModel
    if (model == null) {
      val body = """{"success":false,"message":"No model loaded"}"""
      return badRequest("No model loaded") to body
    }
    val payload = HashMap<String, String>()
    session.parseBody(payload)
    val raw = payload["postData"] ?: ""
    if (raw.isBlank()) {
      // GET-like: return current config
      val current = org.json.JSONObject().apply {
        val cfg = model.configValues
        put("temperature", (cfg[ConfigKeys.TEMPERATURE.label] as? Number)?.toDouble() ?: 0.0)
        put("max_tokens", (cfg[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt() ?: 0)
        put("top_k", (cfg[ConfigKeys.TOPK.label] as? Number)?.toInt() ?: 0)
        put("top_p", (cfg[ConfigKeys.TOPP.label] as? Number)?.toDouble() ?: 0.0)
        put("thinking_enabled", cfg[ConfigKeys.ENABLE_THINKING.label] as? Boolean ?: false)
        put("model", model.name)
      }
      val body = current.toString()
      return okJsonText(body) to body
    }
    return try {
      val obj = org.json.JSONObject(raw)
      val oldConfig = model.configValues
      val updated = oldConfig.toMutableMap()
      // Each change is logged as "Name: old → new" to match the Settings UI format
      val changes = mutableListOf<String>()
      if (obj.has("temperature")) {
        val old = (oldConfig[ConfigKeys.TEMPERATURE.label] as? Number)?.toFloat()
        val v = obj.getDouble("temperature").toFloat()
        updated[ConfigKeys.TEMPERATURE.label] = v
        changes.add("Temperature: ${old ?: "unset"} → $v")
      }
      if (obj.has("max_tokens")) {
        val old = (oldConfig[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
        val v = obj.getInt("max_tokens")
        updated[ConfigKeys.MAX_TOKENS.label] = v
        changes.add("Max Tokens: ${old ?: "unset"} → $v")
      }
      if (obj.has("top_k")) {
        val old = (oldConfig[ConfigKeys.TOPK.label] as? Number)?.toInt()
        val v = obj.getInt("top_k")
        updated[ConfigKeys.TOPK.label] = v
        changes.add("Top-K: ${old ?: "unset"} → $v")
      }
      if (obj.has("top_p")) {
        val old = (oldConfig[ConfigKeys.TOPP.label] as? Number)?.toFloat()
        val v = obj.getDouble("top_p").toFloat()
        updated[ConfigKeys.TOPP.label] = v
        changes.add("Top-P: ${old ?: "unset"} → $v")
      }
      if (obj.has("thinking_enabled")) {
        val v = obj.getBoolean("thinking_enabled")
        if (model.llmSupportThinking) {
          val old = oldConfig[ConfigKeys.ENABLE_THINKING.label] as? Boolean ?: false
          updated[ConfigKeys.ENABLE_THINKING.label] = v
          ServerMetrics.setThinkingEnabled(v)
          changes.add("Thinking: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
        }
      }
      if (changes.isEmpty()) {
        val body = """{"success":false,"message":"No recognized fields in request. Supported: temperature, max_tokens, top_k, top_p, thinking_enabled"}"""
        badRequest("No recognized config fields") to body
      } else {
        model.configValues = updated
        LlmHttpPrefs.setInferenceConfig(serviceContext, model.name, updated)
        // Log using the same format as the Settings UI so the LogsScreen parser
        // renders it with the proper card headline and old→new arrow format.
        RequestLogStore.addEvent(
          "Config via REST API (${changes.size} ${if (changes.size == 1) "change" else "changes"})",
          modelName = model.name,
          category = EventCategory.SETTINGS,
          body = changes.joinToString("\n"),
        )
        // Return the full current config after applying changes
        val current = org.json.JSONObject().apply {
          put("success", true)
          put("model", model.name)
          put("temperature", (updated[ConfigKeys.TEMPERATURE.label] as? Number)?.toDouble() ?: 0.0)
          put("max_tokens", (updated[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt() ?: 0)
          put("top_k", (updated[ConfigKeys.TOPK.label] as? Number)?.toInt() ?: 0)
          put("top_p", (updated[ConfigKeys.TOPP.label] as? Number)?.toDouble() ?: 0.0)
          put("thinking_enabled", updated[ConfigKeys.ENABLE_THINKING.label] as? Boolean ?: false)
        }
        val body = current.toString()
        okJsonText(body) to body
      }
    } catch (e: org.json.JSONException) {
      val body = """{"success":false,"message":"Invalid JSON: ${e.message}"}"""
      badRequest("Invalid JSON body") to body
    }
  }

  // ── Auth ────────────────────────────────────────────────────────────────────

  private fun requireAuth(session: IHTTPSession): Response? {
    val expected = LlmHttpPrefs.getBearerToken(serviceContext)
    if (expected.isBlank()) return null
    val header = session.headers["authorization"] ?: session.headers["Authorization"] ?: ""
    return if (LlmHttpBridgeUtils.isBearerAuthorized(expected, header)) null else unauthorized("unauthorized")
  }

  // ── CORS ────────────────────────────────────────────────────────────────────

  /**
   * Adds CORS headers to a response based on the configured allowed origins
   * and the request's Origin header. Uses [LlmHttpCorsHelper] for origin matching.
   */
  private fun addCorsHeaders(response: Response, requestOrigin: String?): Response {
    val allowedOrigins = LlmHttpPrefs.getCorsAllowedOrigins(serviceContext)
    val headers = LlmHttpCorsHelper.buildCorsHeaders(allowedOrigins, requestOrigin)
    for ((key, value) in headers) {
      response.addHeader(key, value)
    }
    return response
  }

  private fun corsOk(requestOrigin: String?): Response {
    val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    return addCorsHeaders(resp, requestOrigin)
  }
}
