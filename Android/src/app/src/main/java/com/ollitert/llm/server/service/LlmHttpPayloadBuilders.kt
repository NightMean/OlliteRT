package com.ollitert.llm.server.service

import android.util.Log
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.Model
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure functions that build JSON payloads for informational and inference response
 * endpoints. Extracted from LlmHttpService to isolate payload construction from
 * service lifecycle, model management, and HTTP concerns.
 *
 * All functions are stateless — they read from [ServerMetrics] (a singleton) and
 * receive any mutable service state (active model, idle model name) as parameters.
 */
object LlmHttpPayloadBuilders {

  private const val LOG_TAG = "LlmHttpPayloadBuilders"

  // ── Info & Health ──────────────────────────────────────────────────────────

  /**
   * Builds the JSON response for GET /api/version and GET /v1/server/info.
   * Includes server identity, version, status, loaded model, uptime, update
   * availability, and the full list of supported endpoints.
   */
  fun serverInfo(activeModel: Model?, idleUnloadedModelName: String? = null): String {
    val status = ServerMetrics.status.value
    val isIdle = ServerMetrics.isIdleUnloaded.value
    val uptimeSeconds = if (ServerMetrics.startedAtMs.value > 0L)
      (System.currentTimeMillis() - ServerMetrics.startedAtMs.value) / 1000 else null
    val info = buildMap {
      put("name", JsonPrimitive("OlliteRT"))
      put("version", JsonPrimitive(BuildConfig.VERSION_NAME))
      put("build", JsonPrimitive(BuildConfig.VERSION_CODE))
      put("git_hash", JsonPrimitive(BuildConfig.GIT_HASH))
      // Report "idle" when model is unloaded due to keep_alive, matching /health behavior
      val statusStr = when {
        isIdle -> "idle"
        else -> status.name.lowercase()
      }
      put("status", JsonPrimitive(statusStr))
      val modelName = activeModel?.name ?: idleUnloadedModelName
      modelName?.let { put("model", JsonPrimitive(it)) }
      uptimeSeconds?.let { put("uptime_seconds", JsonPrimitive(it)) }
      // Surface cached update info from background UpdateCheckWorker (if a newer version was found)
      val latestVersion = ServerMetrics.availableUpdateVersion.value
      val updateUrl = ServerMetrics.availableUpdateUrl.value
      put("update_available", JsonPrimitive(latestVersion != null))
      if (latestVersion != null) {
        put("latest_version", JsonPrimitive(latestVersion.removePrefix("v")))
        if (updateUrl != null) put("release_url", JsonPrimitive(updateUrl))
      }
      put("compatibility", JsonPrimitive("openai"))
      put("endpoints", JsonArray(listOf(
        JsonPrimitive("/v1/models"),
        JsonPrimitive("/v1/completions"),
        JsonPrimitive("/v1/chat/completions"),
        JsonPrimitive("/v1/responses"),
        JsonPrimitive("/health"),
        JsonPrimitive("/metrics"),
        JsonPrimitive("/api/version"),
        JsonPrimitive("/v1/server/stop"),
        JsonPrimitive("/v1/server/reload"),
        JsonPrimitive("/v1/server/thinking"),
        JsonPrimitive("/v1/server/config"),
      )))
    }
    return JsonObject(info).toString()
  }

  /**
   * Builds the JSON response for GET /health.
   * When [includeMetrics] is true (via ?metrics=true query param), appends a full
   * ServerMetrics snapshot — designed for Home Assistant REST sensor integration
   * so a single poll returns status + all performance metrics.
   */
  // IMPORTANT: When adding or changing fields here, also update the HA YAML template
  // in SettingsScreen.kt (haConfig buildString block) so the generated configuration stays in sync.
  fun health(
    activeModel: Model?,
    idleUnloadedModelName: String?,
    includeMetrics: Boolean = false,
  ): String {
    val status = ServerMetrics.status.value
    val isIdle = ServerMetrics.isIdleUnloaded.value
    val uptimeSeconds = if (ServerMetrics.startedAtMs.value > 0L)
      (System.currentTimeMillis() - ServerMetrics.startedAtMs.value) / 1000 else null
    val info = buildMap {
      // Report "idle" when model is unloaded due to keep_alive — server is reachable but
      // the next inference request will have a cold-start delay while the model reloads.
      val statusStr = when {
        isIdle -> "idle"
        status == com.ollitert.llm.server.ui.navigation.ServerStatus.RUNNING -> "ok"
        else -> status.name.lowercase()
      }
      put("status", JsonPrimitive(statusStr))
      val modelName = activeModel?.name ?: idleUnloadedModelName
      modelName?.let { put("model", JsonPrimitive(it)) }
      uptimeSeconds?.let { put("uptime_seconds", JsonPrimitive(it)) }
      // Surface update availability in health response — lightweight boolean for monitoring dashboards
      put("update_available", JsonPrimitive(ServerMetrics.availableUpdateVersion.value != null))

      if (includeMetrics) {
        put("version", JsonPrimitive(BuildConfig.VERSION_NAME))
        put("thinking_enabled", JsonPrimitive(ServerMetrics.thinkingEnabled.value))
        put("accelerator", JsonPrimitive(ServerMetrics.activeAccelerator.value ?: "unknown"))
        put("is_idle_unloaded", JsonPrimitive(ServerMetrics.isIdleUnloaded.value))
        val metricsMap = buildMap {
          put("requests_total", JsonPrimitive(ServerMetrics.requestCount.value))
          put("errors_total", JsonPrimitive(ServerMetrics.errorCount.value))
          put("prompt_tokens_total", JsonPrimitive(ServerMetrics.tokensIn.value))
          put("generation_tokens_total", JsonPrimitive(ServerMetrics.tokensGenerated.value))
          put("requests_text", JsonPrimitive(ServerMetrics.textRequests.value))
          put("requests_image", JsonPrimitive(ServerMetrics.imageRequests.value))
          put("requests_audio", JsonPrimitive(ServerMetrics.audioRequests.value))
          put("ttfb_last_ms", JsonPrimitive(ServerMetrics.lastTtfbMs.value))
          put("ttfb_avg_ms", JsonPrimitive(ServerMetrics.avgTtfbMs.value))
          put("decode_tokens_per_second", JsonPrimitive(ServerMetrics.lastDecodeSpeed.value))
          put("decode_tokens_per_second_peak", JsonPrimitive(ServerMetrics.peakDecodeSpeed.value))
          put("prefill_tokens_per_second", JsonPrimitive(ServerMetrics.lastPrefillSpeed.value))
          put("inter_token_latency_ms", JsonPrimitive(ServerMetrics.lastItlMs.value))
          put("request_latency_last_ms", JsonPrimitive(ServerMetrics.lastLatencyMs.value))
          put("request_latency_avg_ms", JsonPrimitive(ServerMetrics.avgLatencyMs.value))
          put("request_latency_peak_ms", JsonPrimitive(ServerMetrics.peakLatencyMs.value))
          put("context_utilization_percent", JsonPrimitive(ServerMetrics.lastContextUtilization.value))
          put("model_load_time_seconds", JsonPrimitive(ServerMetrics.modelLoadTimeMs.value / 1000.0))
          put("is_inferring", JsonPrimitive(ServerMetrics.isInferring.value))
        }
        put("metrics", JsonObject(metricsMap))
      }
    }
    return JsonObject(info).toString()
  }

  // ── /v1/models ────────────────────────────────────────────────────────────

  /**
   * Builds the JSON response for GET /v1/models/{id}.
   * Returns null if the model ID doesn't match the active or idle-unloaded model.
   */
  fun modelDetail(activeModel: Model?, uri: String, json: Json, idleUnloadedModelName: String? = null): String? {
    val modelId = uri.removePrefix("/v1/models/")
    if (modelId.isBlank()) return null
    if (activeModel != null) {
      // Match against the currently loaded model
      if (!activeModel.name.equals(modelId, ignoreCase = true)) return null
      val item = LlmHttpModelItem(
        id = activeModel.name,
        capabilities = LlmHttpModelCapabilities(
          image = activeModel.llmSupportImage,
          audio = activeModel.llmSupportAudio,
          thinking = activeModel.llmSupportThinking && (activeModel.configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false,
        ),
      )
      return json.encodeToString(LlmHttpModelItem.serializer(), item)
    }
    // Model is idle-unloaded by keep_alive — return basic info without capabilities
    // (capabilities require the Model object which isn't available when unloaded)
    val idleName = idleUnloadedModelName ?: return null
    if (!idleName.equals(modelId, ignoreCase = true)) return null
    val item = LlmHttpModelItem(id = idleName)
    return json.encodeToString(LlmHttpModelItem.serializer(), item)
  }

  /**
   * Builds the JSON response for GET /v1/models.
   * Reports the active model, or the idle-unloaded model name if keep_alive has
   * unloaded the model but the server is still running.
   */
  fun modelsList(activeModel: Model?, idleUnloadedModelName: String?, json: Json): String {
    val model = activeModel
    if (model == null) {
      // If model is idle-unloaded (keep_alive), still report it so clients know
      // which model will serve their next request (after auto-reload).
      val idleName = idleUnloadedModelName
      if (idleName != null) {
        Log.i(LOG_TAG, "Models list: model idle-unloaded (keep_alive), reporting $idleName")
        val item = LlmHttpModelItem(id = idleName)
        return json.encodeToString(LlmHttpModelList(data = listOf(item)))
      }
      Log.i(LOG_TAG, "Models list: no model loaded")
      return json.encodeToString(LlmHttpModelList(data = emptyList()))
    }
    Log.i(LOG_TAG, "Models list: active model=${model.name}")
    val item = LlmHttpModelItem(
      id = model.name,
      capabilities = LlmHttpModelCapabilities(
        image = model.llmSupportImage,
        audio = model.llmSupportAudio,
        thinking = model.llmSupportThinking && (model.configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false,
      ),
    )
    return json.encodeToString(LlmHttpModelList(data = listOf(item)))
  }

  // ── Response factories ────────────────────────────────────────────────────
  // Token counts in all response builders below are **estimates** via estimateTokens().
  // LiteRT LM SDK has no standalone tokenizer API — see Usage class doc for details.

  /**
   * Build performance timings from the most recent inference metrics.
   * Safe to call right after runLlm() completes — inference is serialized via [inferenceLock],
   * so the ServerMetrics "last" values are guaranteed to be from the current request.
   *
   * Returns null if no valid timing data is available (e.g. TTFB was 0).
   */
  fun buildTimings(promptTokens: Int, completionTokens: Int): InferenceTimings? {
    val ttfbMs = ServerMetrics.lastTtfbMs.value
    val totalMs = ServerMetrics.lastLatencyMs.value
    if (ttfbMs <= 0 || totalMs <= 0) return null
    val promptMs = ttfbMs.toDouble()
    val predictedMs = (totalMs - ttfbMs).toDouble()
    return InferenceTimings(
      prompt_n = promptTokens,
      prompt_ms = promptMs,
      prompt_per_token_ms = if (promptTokens > 0) promptMs / promptTokens else 0.0,
      prompt_per_second = if (promptMs > 0) promptTokens * 1000.0 / promptMs else 0.0,
      predicted_n = completionTokens,
      predicted_ms = predictedMs,
      predicted_per_token_ms = if (completionTokens > 0) predictedMs / completionTokens else 0.0,
      predicted_per_second = if (predictedMs > 0) completionTokens * 1000.0 / predictedMs else 0.0,
    )
  }

  /**
   * Build performance timings from explicit timing values (for streaming paths
   * where timing data is computed locally, not read from ServerMetrics).
   */
  fun buildTimingsFromValues(promptTokens: Int, completionTokens: Int, ttfbMs: Long, totalMs: Long): InferenceTimings? {
    if (ttfbMs <= 0 || totalMs <= 0) return null
    val promptMs = ttfbMs.toDouble()
    val predictedMs = (totalMs - ttfbMs).toDouble()
    return InferenceTimings(
      prompt_n = promptTokens,
      prompt_ms = promptMs,
      prompt_per_token_ms = if (promptTokens > 0) promptMs / promptTokens else 0.0,
      prompt_per_second = if (promptMs > 0) promptTokens * 1000.0 / promptMs else 0.0,
      predicted_n = completionTokens,
      predicted_ms = predictedMs,
      predicted_per_token_ms = if (completionTokens > 0) predictedMs / completionTokens else 0.0,
      predicted_per_second = if (predictedMs > 0) completionTokens * 1000.0 / predictedMs else 0.0,
    )
  }

  fun emptyChatResponse(modelName: String) = ChatResponse(
    id = LlmHttpBridgeUtils.generateChatCompletionId(), created = LlmHttpBridgeUtils.epochSeconds(), model = modelName,
    choices = listOf(ChatChoice(0, ChatMessage("assistant", ChatContent("")), "stop")),
    usage = Usage(0, 0),
  )

  fun chatResponseWithText(modelName: String, text: String, promptLen: Int = 0, finishReason: String = "stop", timings: InferenceTimings? = null): ChatResponse {
    val promptTokens = estimateTokensByLength(promptLen)
    val completionTokens = estimateTokens(text)
    return ChatResponse(
      id = LlmHttpBridgeUtils.generateChatCompletionId(), created = LlmHttpBridgeUtils.epochSeconds(), model = modelName,
      choices = listOf(ChatChoice(0, ChatMessage("assistant", ChatContent(text)), finishReason)),
      usage = Usage(promptTokens, completionTokens),
      timings = timings,
    )
  }

  fun chatResponseWithToolCalls(modelName: String, toolCalls: List<ToolCall>, promptLen: Int = 0, timings: InferenceTimings? = null): ChatResponse {
    val promptTokens = estimateTokensByLength(promptLen)
    val completionTokens = estimateTokens(toolCalls.joinToString("") { it.function.arguments })
    return ChatResponse(
      id = LlmHttpBridgeUtils.generateChatCompletionId(), created = LlmHttpBridgeUtils.epochSeconds(), model = modelName,
      choices = listOf(ChatChoice(0, ChatMessage("assistant", ChatContent(""), tool_calls = toolCalls), "tool_calls")),
      usage = Usage(promptTokens, completionTokens),
      timings = timings,
    )
  }

  fun responsesResponseWithText(modelName: String, text: String, promptLen: Int = 0) = ResponsesResponse(
    id = LlmHttpBridgeUtils.generateResponseId(), created = LlmHttpBridgeUtils.epochSeconds(), model = modelName,
    output = listOf(RespMessage(content = listOf(RespContent(text = text)))),
    usage = Usage(
      prompt_tokens = estimateTokensByLength(promptLen),
      completion_tokens = estimateTokens(text),
    ),
  )

  fun responsesResponseWithToolCall(modelName: String, toolCall: ToolCall, promptLen: Int = 0, json: Json) = ResponsesResponse(
    id = LlmHttpBridgeUtils.generateResponseId(), created = LlmHttpBridgeUtils.epochSeconds(), model = modelName,
    output = listOf(RespMessage(content = listOf(RespContent(type = "output_tool_call", text = json.encodeToString(toolCall))), finish_reason = "tool_calls")),
    usage = Usage(
      prompt_tokens = estimateTokensByLength(promptLen),
      completion_tokens = estimateTokens(toolCall.function.arguments),
    ),
  )
}
