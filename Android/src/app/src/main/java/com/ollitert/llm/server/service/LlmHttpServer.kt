/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import fi.iki.elonen.NanoHTTPD

// ── HTTP Response Helpers ────────────────────────────────────────────────────
// Use NanoHTTPD.newFixedLengthResponse (static) so they work outside NanoHTTPD subclasses.
// Shared by LlmHttpServer (serve dispatcher) and LlmHttpEndpointHandlers (inference routes).

internal fun okJsonText(body: String): NanoHTTPD.Response =
  NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json; charset=utf-8", body)

internal fun jsonError(
  status: NanoHTTPD.Response.Status,
  error: String,
  suggestion: String? = null,
  kind: ErrorKind? = null,
): NanoHTTPD.Response =
  NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", LlmHttpResponseRenderer.renderJsonError(error, suggestion, kind))

internal fun badRequest(msg: String) = jsonError(NanoHTTPD.Response.Status.BAD_REQUEST, msg)
internal fun notFound(error: String = "not_found") = jsonError(NanoHTTPD.Response.Status.NOT_FOUND, error)
internal fun unauthorized(error: String) =
  jsonError(NanoHTTPD.Response.Status.UNAUTHORIZED, error).also { it.addHeader("WWW-Authenticate", "Bearer") }
internal fun methodNotAllowed() = jsonError(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "method_not_allowed")

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
  private val getRequestCount: () -> Long,
  private val emitDebugStackTrace: (Throwable, String, String?) -> Unit,
  private val audioTranscriptionHandler: LlmHttpAudioTranscriptionHandler,
  private val inferenceLock: Any,
) : NanoHTTPD("0.0.0.0", port) {

  private val logTag = "LlmHttpServer"

  private val faviconBytes: ByteArray? by lazy {
    try { serviceContext.assets.open("favicon.png").use { it.readBytes() } } catch (_: Exception) { null }
  }

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
    val allowedOrigins = LlmHttpPrefs.getCorsAllowedOrigins(serviceContext)
    val corsHeaders = LlmHttpCorsHelper.buildCorsHeaders(allowedOrigins, requestOrigin)
    // Handle CORS preflight (no logging needed)
    if (session.method == Method.OPTIONS) {
      return corsOk(corsHeaders)
    }

    // Suppress /health log entries when the user has enabled "Hide Health Logs"
    if (LlmHttpPrefs.isHideHealthLogs(serviceContext)) {
      val route = LlmHttpRouteResolver.resolve(session.method.name, session.uri)
      if (route?.handler == LlmHttpRouteHandler.HEALTH) {
        val includeMetrics = session.parameters?.get("metrics")?.firstOrNull()?.equals("true", ignoreCase = true) == true
        val body = LlmHttpPayloadBuilders.health(defaultModel, keepAliveUnloadedModelName, includeMetrics)
        return applyCorsHeaders(okJsonText(body), corsHeaders)
      }
    }

    // Add a pending log entry immediately so it appears in the Logs tab
    val logId = "log-${System.currentTimeMillis()}-${getRequestCount()}"
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

    // Pre-compute headers for streaming responses (FlushingSseResponse writes raw HTTP,
    // bypassing NanoHTTPD's header storage — CORS + x-request-id must be baked in at construction).
    val sseExtraHeaders = corsHeaders + ("x-request-id" to logId)

    var requestBodySnapshot: String? = null
    var responseBodySnapshot: String? = null
    val response = try {
      if (!LlmHttpRouteResolver.isSupportedMethod(session.method.name)) {
        methodNotAllowed()
      } else {
        val route = LlmHttpRouteResolver.resolve(session.method.name, session.uri)
        val authError = if (route?.requiresAuth == true) requireAuth(session) else null
        if (route == null) {
          // Browsers auto-request /favicon.ico when visiting the server URL — serve the app icon.
          // Future: add a "Hide browser requests (favicon, etc.)" toggle in Settings that moves
          // this handler above the RequestLogStore.add() call to suppress it from the Logs tab.
          if (path == "/favicon.ico") {
            val bytes = faviconBytes
            if (bytes != null) {
              responseBodySnapshot = "[favicon.png ${bytes.size} bytes]"
              NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "image/png", bytes.inputStream(), bytes.size.toLong())
            } else {
              notFound()
            }
          }
          // Check if it's a known OpenAI endpoint we don't support
          else {
            val unsupportedMsg = LlmHttpRouteResolver.getUnsupportedEndpointMessage(session.uri)
            if (unsupportedMsg != null) jsonError(Response.Status.NOT_FOUND, unsupportedMsg)
            else notFound()
          }
        } else if (authError != null) {
          authError
        } else {
          // Update the pending entry with the request body once parsed.
          // When "Compact Image Data" is enabled, replace inline base64 data URIs
          // with size placeholders before storing — a 4K image is ~5-10 MB of base64
          // text that freezes the Logs tab when Compose tries to render it.
          val compactImages = LlmHttpPrefs.isCompactImageData(serviceContext)
          val captureBody = { body: String ->
            val stored = if (compactImages) LlmHttpBridgeUtils.compactBase64DataUris(body) else body
            // Track original body size so the Logs badge shows the true request size,
            // not the smaller compacted size after base64 placeholder replacement.
            val originalSize = if (compactImages && stored.length != body.length) body.length else 0
            requestBodySnapshot = stored
            RequestLogStore.update(logId) { it.copy(requestBody = stored, originalRequestBodySize = originalSize) }
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
              val body = LlmHttpPayloadBuilders.serverInfo(defaultModel, keepAliveUnloadedModelName, modelLifecycle.allowlistLoader)
              responseBodySnapshot = body
              okJsonText(body)
            }
            LlmHttpRouteHandler.VERSION -> {
              // Same payload as / -- aliased for Home Assistant and monitoring tools
              val body = LlmHttpPayloadBuilders.serverInfo(defaultModel, keepAliveUnloadedModelName, modelLifecycle.allowlistLoader)
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
              val body = LlmHttpPayloadBuilders.modelDetail(defaultModel, session.uri, json, keepAliveUnloadedModelName)
              if (body != null) {
                responseBodySnapshot = body
                okJsonText(body)
              } else {
                notFound("model_not_found")
              }
            }

            // ── Inference routes — delegated to LlmHttpEndpointHandlers ──
            // Body is parsed here and passed pre-parsed so handlers are decoupled from NanoHTTPD.
            LlmHttpRouteHandler.GENERATE,
            LlmHttpRouteHandler.COMPLETIONS,
            LlmHttpRouteHandler.CHAT_COMPLETIONS,
            LlmHttpRouteHandler.RESPONSES -> {
              val inferenceBody: String = when (val parsed = safeParseBody(session)) {
                is Either.Left -> return@serve applyCorsHeaders(parsed.value, corsHeaders)
                is Either.Right -> parsed.value
              }
              when (route.handler) {
                LlmHttpRouteHandler.GENERATE -> endpointHandlers.handleGenerate(inferenceBody, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
                LlmHttpRouteHandler.COMPLETIONS -> endpointHandlers.handleCompletions(inferenceBody, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
                LlmHttpRouteHandler.CHAT_COMPLETIONS -> endpointHandlers.handleChatCompletion(inferenceBody, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId, sseExtraHeaders = sseExtraHeaders)
                LlmHttpRouteHandler.RESPONSES -> endpointHandlers.handleResponses(inferenceBody, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId, sseExtraHeaders = sseExtraHeaders)
              }
            }

            // ── Server control endpoints ──
            // IMPORTANT: When adding new /v1/server/* endpoints, also update the HA YAML
            // template in SettingsScreen.kt (haConfig buildString block) with the new rest_command.
            LlmHttpRouteHandler.SERVER_STOP -> {
              // Trigger graceful shutdown via the same intent the notification Stop button uses.
              // The response is sent before the service actually stops.
              val stopIntent = Intent(serviceContext, LlmHttpService::class.java).apply {
                action = LlmHttpService.ACTION_STOP
              }
              try {
                serviceContext.startService(stopIntent)
              } catch (e: Exception) {
                Log.e("LlmHttpServer", "Failed to send stop intent", e)
                val errBody = """{"success":false,"message":"Failed to stop server: ${LlmHttpBridgeUtils.escapeSseText(e.message ?: "unknown error")}"}"""
                responseBodySnapshot = errBody
                return@serve applyCorsHeaders(jsonError(NanoHTTPD.Response.Status.INTERNAL_ERROR, "Failed to stop server: ${e.message}"), corsHeaders)
              }
              val body = """{"success":true,"message":"Server stopping"}"""
              responseBodySnapshot = body
              okJsonText(body)
            }
            LlmHttpRouteHandler.SERVER_RELOAD -> {
              // Trigger a model reload via the same intent the UI uses.
              // Also works when model is idle-unloaded by keep_alive — wakes it up.
              val modelName = defaultModel?.name ?: keepAliveUnloadedModelName
              if (modelName == null) {
                responseBodySnapshot = """{"success":false,"message":"No model loaded"}"""
                badRequest("No model loaded")
              } else {
                val reloadPort = ServerMetrics.port.value
                LlmHttpService.reload(serviceContext, reloadPort, modelName)
                val body = """{"success":true,"message":"Model reloading","model":"$modelName"}"""
                responseBodySnapshot = body
                okJsonText(body)
              }
            }
            LlmHttpRouteHandler.SERVER_THINKING -> handleServerThinking(session).also { responseBodySnapshot = it.second; }.first
            LlmHttpRouteHandler.SERVER_CONFIG -> handleServerConfig(session).also { responseBodySnapshot = it.second }.first
            LlmHttpRouteHandler.AUDIO_TRANSCRIPTION -> {
              val model = when (val sel = modelLifecycle.selectModel(null)) {
                is LlmHttpModelLifecycle.ModelSelection.Ok -> sel.model
                is LlmHttpModelLifecycle.ModelSelection.Error -> {
                val status = NanoHTTPD.Response.Status.lookup(sel.statusCode) ?: NanoHTTPD.Response.Status.INTERNAL_ERROR
                return@serve applyCorsHeaders(jsonError(status, sel.message).also { r -> sel.retryAfterSeconds?.let { r.addHeader("Retry-After", it.toString()) } }, corsHeaders)
              }
              }
              audioTranscriptionHandler.handle(session, model, captureBody = captureBody, captureResponse = { responseBodySnapshot = it }, logId = logId)
            }
          }
        }
      }
    } catch (t: Throwable) {
      if (t is OutOfMemoryError) {
        // Close the native Engine/Conversation before nullifying — just setting instance = null
        // leaks GB-scale native memory because GC may not finalize the wrapper promptly.
        defaultModel?.let { ServerLlmModelHelper.safeCleanup(it) }
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
    return applyCorsHeaders(response, corsHeaders)
  }

  // ── Server control handlers ────────────────────────────────────────────────

  /**
   * Toggle thinking mode on/off for the active model.
   * Returns (Response, responseBodySnapshot) pair.
   */
  private fun handleServerThinking(session: IHTTPSession): Pair<Response, String?> {
    val model = defaultModel
    val isIdle = ServerMetrics.isIdleUnloaded.value
    // Resolve model name: active model, or the model that was unloaded by keep_alive.
    // Config is persisted in SharedPreferences so it can be read/written even when the model
    // is idle-unloaded — no need to reload the model just to toggle thinking.
    val modelName = model?.name ?: keepAliveUnloadedModelName
    if (modelName == null) {
      val body = """{"success":false,"message":"No model loaded"}"""
      return badRequest("No model loaded") to body
    }
    // When model is loaded, check thinking support. When idle-unloaded, skip the check —
    // we can't inspect model capabilities without the Model object, but the saved config
    // will be applied when the model reloads.
    if (model != null && !model.llmSupportThinking) {
      val body = """{"success":false,"message":"Model does not support thinking"}"""
      return badRequest("Model does not support thinking") to body
    }
    val raw = when (val parsed = safeParseBody(session, allowEmpty = true)) {
      is Either.Left -> return parsed.value to """{"success":false,"message":"Request body too large"}"""
      is Either.Right -> parsed.value
    }
    // Read current state from model if loaded, otherwise from persisted prefs
    val currentConfig = model?.configValues ?: LlmHttpPrefs.getInferenceConfig(serviceContext, modelName)
    val currentState = (currentConfig?.get(ConfigKeys.ENABLE_THINKING.label) as? Boolean) ?: false
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
    // Update in-memory config if model is loaded, always persist to prefs
    val updatedConfig = (currentConfig ?: emptyMap()) + (ConfigKeys.ENABLE_THINKING.label to requestedState)
    if (model != null) {
      synchronized(inferenceLock) { model.configValues = updatedConfig }
    }
    LlmHttpPrefs.setInferenceConfig(serviceContext, modelName, updatedConfig)
    ServerMetrics.setThinkingEnabled(requestedState)
    // Log using the same "Settings updated" format as the Settings UI,
    // so the LogsScreen parser renders it with the proper card headline and arrow format.
    val oldLabel = if (currentState) "enabled" else "disabled"
    val newLabel = if (requestedState) "enabled" else "disabled"
    RequestLogStore.addEvent(
      "Config via REST API (1 change)",
      modelName = modelName,
      category = EventCategory.SETTINGS,
      body = "Thinking: $oldLabel → $newLabel",
    )
    val result = org.json.JSONObject().apply {
      put("success", true)
      put("thinking_enabled", requestedState)
      put("model", modelName)
      put("model_loaded", !isIdle)
    }
    val body = result.toString()
    return okJsonText(body) to body
  }

  /**
   * Update inference settings (temperature, max_tokens, top_k, top_p, thinking_enabled).
   * Returns (Response, responseBodySnapshot) pair.
   */
  private fun handleServerConfig(session: IHTTPSession): Pair<Response, String?> {
    val model = defaultModel
    val isIdle = ServerMetrics.isIdleUnloaded.value
    // Resolve model name: active model, or the model that was unloaded by keep_alive.
    // Config is persisted in SharedPreferences so it can be read/written even when the model
    // is idle-unloaded — no need to reload the model just to change inference settings.
    val modelName = model?.name ?: keepAliveUnloadedModelName
    if (modelName == null) {
      val body = """{"success":false,"message":"No model loaded"}"""
      return badRequest("No model loaded") to body
    }
    // Read config from model if loaded, otherwise from persisted prefs
    val currentConfig = model?.configValues ?: LlmHttpPrefs.getInferenceConfig(serviceContext, modelName) ?: emptyMap()
    val raw = when (val parsed = safeParseBody(session, allowEmpty = true)) {
      is Either.Left -> return parsed.value to """{"success":false,"message":"Request body too large"}"""
      is Either.Right -> parsed.value
    }
    if (raw.isBlank()) {
      // GET-like: return current config
      val current = org.json.JSONObject().apply {
        put("temperature", (currentConfig[ConfigKeys.TEMPERATURE.label] as? Number)?.toDouble() ?: 0.0)
        put("max_tokens", (currentConfig[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt() ?: 0)
        put("top_k", (currentConfig[ConfigKeys.TOPK.label] as? Number)?.toInt() ?: 0)
        put("top_p", (currentConfig[ConfigKeys.TOPP.label] as? Number)?.toDouble() ?: 0.0)
        put("thinking_enabled", currentConfig[ConfigKeys.ENABLE_THINKING.label] as? Boolean ?: false)
        put("model", modelName)
        put("model_loaded", !isIdle)
        // Behavior toggles read from SharedPreferences — when adding fields, also update the HA configuration.yaml in Settings (HomeAssistantCard.kt)
        put("auto_truncate_history", LlmHttpPrefs.isAutoTruncateHistory(serviceContext))
        put("auto_trim_prompts", LlmHttpPrefs.isAutoTrimPrompts(serviceContext))
        put("compact_tool_schemas", LlmHttpPrefs.isCompactToolSchemas(serviceContext))
        put("warmup_enabled", LlmHttpPrefs.isWarmupEnabled(serviceContext))
        put("keep_alive_enabled", LlmHttpPrefs.isKeepAliveEnabled(serviceContext))
        put("keep_alive_minutes", LlmHttpPrefs.getKeepAliveMinutes(serviceContext))
        put("custom_prompts_enabled", LlmHttpPrefs.isCustomPromptsEnabled(serviceContext))
        put("system_prompt", LlmHttpPrefs.getSystemPrompt(serviceContext, modelName))
      }
      val body = current.toString()
      return okJsonText(body) to body
    }
    return try {
      val obj = org.json.JSONObject(raw)
      val updated = currentConfig.toMutableMap()
      // Each change is logged as "Name: old → new" to match the Settings UI format
      val changes = mutableListOf<String>()
      if (obj.has("temperature")) {
        val old = (currentConfig[ConfigKeys.TEMPERATURE.label] as? Number)?.toFloat()
        val v = obj.getDouble("temperature").toFloat()
        updated[ConfigKeys.TEMPERATURE.label] = v
        changes.add("Temperature: ${old ?: "unset"} → $v")
      }
      if (obj.has("max_tokens")) {
        val old = (currentConfig[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
        val v = obj.getInt("max_tokens")
        updated[ConfigKeys.MAX_TOKENS.label] = v
        changes.add("Max Tokens: ${old ?: "unset"} → $v")
      }
      if (obj.has("top_k")) {
        val old = (currentConfig[ConfigKeys.TOPK.label] as? Number)?.toInt()
        val v = obj.getInt("top_k")
        updated[ConfigKeys.TOPK.label] = v
        changes.add("Top-K: ${old ?: "unset"} → $v")
      }
      if (obj.has("top_p")) {
        val old = (currentConfig[ConfigKeys.TOPP.label] as? Number)?.toFloat()
        val v = obj.getDouble("top_p").toFloat()
        updated[ConfigKeys.TOPP.label] = v
        changes.add("Top-P: ${old ?: "unset"} → $v")
      }
      if (obj.has("thinking_enabled")) {
        val v = obj.getBoolean("thinking_enabled")
        // When model is loaded, only allow thinking toggle if model supports it.
        // When idle-unloaded, allow the change — it will be validated on model reload.
        if (model == null || model.llmSupportThinking) {
          val old = currentConfig[ConfigKeys.ENABLE_THINKING.label] as? Boolean ?: false
          updated[ConfigKeys.ENABLE_THINKING.label] = v
          ServerMetrics.setThinkingEnabled(v)
          changes.add("Thinking: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
        }
      }
      // ── Behavior toggles (persisted directly to SharedPreferences, not model configValues) ──
      if (obj.has("auto_truncate_history")) {
        val old = LlmHttpPrefs.isAutoTruncateHistory(serviceContext)
        val v = obj.getBoolean("auto_truncate_history")
        LlmHttpPrefs.setAutoTruncateHistory(serviceContext, v)
        changes.add("Auto Truncate History: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      if (obj.has("auto_trim_prompts")) {
        val old = LlmHttpPrefs.isAutoTrimPrompts(serviceContext)
        val v = obj.getBoolean("auto_trim_prompts")
        LlmHttpPrefs.setAutoTrimPrompts(serviceContext, v)
        changes.add("Auto Trim Prompts: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      if (obj.has("compact_tool_schemas")) {
        val old = LlmHttpPrefs.isCompactToolSchemas(serviceContext)
        val v = obj.getBoolean("compact_tool_schemas")
        LlmHttpPrefs.setCompactToolSchemas(serviceContext, v)
        changes.add("Compact Tool Schemas: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      if (obj.has("warmup_enabled")) {
        val old = LlmHttpPrefs.isWarmupEnabled(serviceContext)
        val v = obj.getBoolean("warmup_enabled")
        LlmHttpPrefs.setWarmupEnabled(serviceContext, v)
        changes.add("Warmup: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      if (obj.has("keep_alive_enabled")) {
        val old = LlmHttpPrefs.isKeepAliveEnabled(serviceContext)
        val v = obj.getBoolean("keep_alive_enabled")
        LlmHttpPrefs.setKeepAliveEnabled(serviceContext, v)
        if (v) modelLifecycle.resetKeepAliveTimer() else modelLifecycle.cancelKeepAliveTimer()
        changes.add("Keep Alive: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      if (obj.has("keep_alive_minutes")) {
        val old = LlmHttpPrefs.getKeepAliveMinutes(serviceContext)
        val v = obj.getInt("keep_alive_minutes")
        if (v < 1 || v > 7200) {
          val body = """{"success":false,"message":"keep_alive_minutes must be between 1 and 7200"}"""
          return badRequest("keep_alive_minutes out of range") to body
        }
        LlmHttpPrefs.setKeepAliveMinutes(serviceContext, v)
        if (LlmHttpPrefs.isKeepAliveEnabled(serviceContext)) modelLifecycle.resetKeepAliveTimer()
        changes.add("Keep Alive Minutes: $old → $v")
      }
      if (obj.has("custom_prompts_enabled")) {
        val old = LlmHttpPrefs.isCustomPromptsEnabled(serviceContext)
        val v = obj.getBoolean("custom_prompts_enabled")
        LlmHttpPrefs.setCustomPromptsEnabled(serviceContext, v)
        changes.add("Custom Prompts: ${if (old) "enabled" else "disabled"} → ${if (v) "enabled" else "disabled"}")
      }
      if (obj.has("system_prompt")) {
        val old = LlmHttpPrefs.getSystemPrompt(serviceContext, modelName)
        val v = obj.getString("system_prompt")
        LlmHttpPrefs.setSystemPrompt(serviceContext, modelName, v)
        val oldDisplay = if (old.isBlank()) "(empty)" else "\"${old.take(40)}${if (old.length > 40) "…" else ""}\""
        val newDisplay = if (v.isBlank()) "(empty)" else "\"${v.take(40)}${if (v.length > 40) "…" else ""}\""
        changes.add("System Prompt: $oldDisplay → $newDisplay")
      }
      if (changes.isEmpty()) {
        val body = """{"success":false,"message":"No recognized fields in request. Supported: temperature, max_tokens, top_k, top_p, thinking_enabled, auto_truncate_history, auto_trim_prompts, compact_tool_schemas, warmup_enabled, keep_alive_enabled, keep_alive_minutes, custom_prompts_enabled, system_prompt"}"""
        badRequest("No recognized config fields") to body
      } else {
        // Update in-memory config if model is loaded, always persist to prefs
        if (model != null) {
          synchronized(inferenceLock) { model.configValues = updated.toMap() }
        }
        LlmHttpPrefs.setInferenceConfig(serviceContext, modelName, updated)
        // Log using the same format as the Settings UI so the LogsScreen parser
        // renders it with the proper card headline and old→new arrow format.
        RequestLogStore.addEvent(
          "Config via REST API (${changes.size} ${if (changes.size == 1) "change" else "changes"})",
          modelName = modelName,
          category = EventCategory.SETTINGS,
          body = changes.joinToString("\n"),
        )
        // Return the full current config after applying changes
        val current = org.json.JSONObject().apply {
          put("success", true)
          put("model", modelName)
          put("model_loaded", !isIdle)
          put("temperature", (updated[ConfigKeys.TEMPERATURE.label] as? Number)?.toDouble() ?: 0.0)
          put("max_tokens", (updated[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt() ?: 0)
          put("top_k", (updated[ConfigKeys.TOPK.label] as? Number)?.toInt() ?: 0)
          put("top_p", (updated[ConfigKeys.TOPP.label] as? Number)?.toDouble() ?: 0.0)
          put("thinking_enabled", updated[ConfigKeys.ENABLE_THINKING.label] as? Boolean ?: false)
          put("auto_truncate_history", LlmHttpPrefs.isAutoTruncateHistory(serviceContext))
          put("auto_trim_prompts", LlmHttpPrefs.isAutoTrimPrompts(serviceContext))
          put("compact_tool_schemas", LlmHttpPrefs.isCompactToolSchemas(serviceContext))
          put("warmup_enabled", LlmHttpPrefs.isWarmupEnabled(serviceContext))
          put("keep_alive_enabled", LlmHttpPrefs.isKeepAliveEnabled(serviceContext))
          put("keep_alive_minutes", LlmHttpPrefs.getKeepAliveMinutes(serviceContext))
          put("custom_prompts_enabled", LlmHttpPrefs.isCustomPromptsEnabled(serviceContext))
          put("system_prompt", LlmHttpPrefs.getSystemPrompt(serviceContext, modelName))
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

  private fun applyCorsHeaders(response: Response, corsHeaders: Map<String, String>): Response {
    for ((key, value) in corsHeaders) {
      response.addHeader(key, value)
    }
    return response
  }

  private fun corsOk(corsHeaders: Map<String, String>): Response {
    val resp = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/plain", "")
    return applyCorsHeaders(resp, corsHeaders)
  }
}
