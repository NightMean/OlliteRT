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

package com.ollitert.llm.server.service.inference

import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.service.http.*

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.WorkerThread
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.prefs.BLOCKING_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.prefs.CHAT_COMPLETIONS_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.model.ErrorKind
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_SHORT_CHARS
import com.ollitert.llm.server.data.prefs.LOG_STREAMING_PREVIEW_DEBOUNCE_MS
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.prefs.RESPONSES_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import com.ollitert.llm.server.data.prefs.SSE_PING_INTERVAL_MS
import com.ollitert.llm.server.data.prefs.STREAM_OUTER_TIMEOUT_SAFETY_BUFFER_SECONDS
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.prefs.WARMUP_MESSAGE
import com.ollitert.llm.server.data.model.llmSupportAudio
import com.ollitert.llm.server.data.model.llmSupportImage
import com.ollitert.llm.server.data.prefs.maxTokensInt
import com.ollitert.llm.server.data.prefs.maxTokensLong
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.formats.AnthropicMessagesFormat
import com.ollitert.llm.server.service.formats.ChatCompletionsFormat
import com.ollitert.llm.server.service.formats.CompletionsFormat
import com.ollitert.llm.server.service.formats.ResponsesApiFormat
import com.ollitert.llm.server.service.formats.StreamingFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes LLM inference (blocking and streaming) against a loaded model.
 * Handles model re-initialization for vision/audio, token counting, timeout,
 * tool call detection, stop sequences, and performance metrics recording.
 *
 * Separated from ServerService/KtorServer to isolate inference execution
 * from HTTP routing, service lifecycle, and notification concerns.
 *
 * Dependencies:
 * - [executor] / [inferenceLock]: serialized single-thread inference from ServerService
 * - [context]: for reading SharedPreferences (ServerPrefs)
 * - Callbacks for logging and system instruction — avoids coupling to the Service class
 * - Singletons: [ServerMetrics], [RequestLogStore], [ServerLlmModelHelper], [InferenceGateway],
 *   [ResponseRenderer], [PayloadBuilders], [ToolCallParser], [ErrorSuggestions]
 */
class InferenceRunner(
  private val context: Context,
  private val executor: ExecutorService,
  private val inferenceLock: Any,
  private val logEvent: (String) -> Unit,
  private val emitDebugStackTrace: (Throwable, source: String, modelName: String?) -> Unit,
  private val buildSystemInstruction: (modelName: String) -> Contents?,
) {

  private val streamingCoordinator = InferenceStreamingCoordinator(
    context = context,
    executor = executor,
    inferenceLock = inferenceLock,
    logEvent = logEvent,
    emitDebugStackTrace = emitDebugStackTrace,
    buildSystemInstruction = buildSystemInstruction,
  )

  private fun reinitIfNeeded(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
  ): String? = InferenceModelPreparer.reinitIfNeeded(
    context = context,
    model = model,
    supportImage = supportImage,
    supportAudio = supportAudio,
    buildSystemInstruction = buildSystemInstruction,
  )

  // ── Blocking inference ───────────────────────────────────────────────────

  suspend fun runLlm(model: Model, request: InferenceRequest): Pair<String?, String?> =
    runLlm(
      model = model,
      prompt = request.prompt,
      requestId = request.requestId,
      endpoint = request.endpoint,
      timeoutSeconds = request.timeoutSeconds,
      images = request.images,
      audioClips = request.audioClips,
      eagerVisionInit = request.eagerVisionInit,
      logId = request.logId,
      configSnapshot = request.configSnapshot,
      prefs = request.prefs,
    )

  /**
   * Run a single blocking inference pass. Returns (output, error) — one is always null.
   * Output includes thinking content wrapped in `<think>` tags if the model produced it.
   *
   * Called by endpoint handlers for non-streaming /generate, /v1/chat/completions,
   * /v1/completions, and /v1/responses.
   */
  internal suspend fun runLlm(
    model: Model,
    prompt: String,
    requestId: String,
    endpoint: String,
    timeoutSeconds: Long = BLOCKING_TIMEOUT_SECONDS,
    images: List<ByteArray> = emptyList(),
    audioClips: List<ByteArray> = emptyList(),
    eagerVisionInit: Boolean = false,
    logId: String? = null,
    configSnapshot: Map<String, Any>? = null,
    prefs: RequestPrefsSnapshot? = null,
    schemaInjectionProviders: List<com.google.ai.edge.litertlm.ToolProvider> = emptyList(),
    schemaInjectionMessages: List<com.google.ai.edge.litertlm.Message> = emptyList(),
    onNativeToolCalls: ((List<ToolCall>) -> Unit)? = null,
    // When true the per-model system prompt is suppressed — the request body already
    // carries an explicit system prompt (Anthropic /v1/messages `system` field) and we
    // do not want both layered.
    suppressPerModelSystem: Boolean = false,
    // Per-request thinking override. null = use the model's persisted setting; true/false
    // forces thinking on/off for this request only. Forced-on requests on a model that
    // does NOT support thinking are silently downgraded to off (the capability gate wins).
    enableThinkingOverride: Boolean? = null,
    // KV-cache reuse: when non-null, dispatch via Message.user(text) on the existing
    // Conversation (skipping resetConversation) so the SDK only prefills the new turn.
    incrementalUserText: String? = null,
    conversationCacheGeneration: Long? = null,
    prepareConversation: (() -> ConversationPreparation)? = null,
  ): Pair<String?, String?> {
    // Track input tokens (rough estimate: ~4 chars per token)
    ServerMetrics.addTokensIn(estimateTokensLong(prompt))
    // Track request modality
    ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = audioClips.isNotEmpty())

    val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVisionInit)
    val supportAudio = model.llmSupportAudio

    val cancellationBridge = RequestCancellationBridge()
    val inferenceActuallyStarted = AtomicBoolean(false)
    // Register cancel callback before any lock acquisition so queued requests are cancellable.
    if (logId != null) {
      RequestLogStore.registerCancellation(logId) {
        cancellationBridge.request() != CancellationRequestStatus.REJECTED
      }
    }

    val session = ConversationDispatchSession(
      debugLogTag = TAG,
      incrementalReuseLabel = "INCREMENTAL_REUSE_BLOCKING",
      model = model,
      requestId = requestId,
      images = images,
      audioClips = audioClips,
      supportImage = supportImage,
      supportAudio = supportAudio,
      enableThinkingOverride = enableThinkingOverride,
      suppressPerModelSystem = suppressPerModelSystem,
      buildSystemInstruction = buildSystemInstruction,
      configSnapshot = configSnapshot,
      incrementalUserText = incrementalUserText,
      conversationCacheGeneration = conversationCacheGeneration,
      prepareConversation = prepareConversation,
      schemaInjectionProviders = schemaInjectionProviders,
      schemaInjectionMessages = schemaInjectionMessages,
      reinitIfNeeded = { reinitIfNeeded(model, supportImage, supportAudio) },
      logId = logId,
    )

    val result = InferenceGateway.execute(
      prompt = prompt,
      timeoutSeconds = timeoutSeconds,
      executor = executor,
      inferenceLock = inferenceLock,
      resetConversation = {
        session.resetConversation(cancellationBridge.cancellationWasAccepted()) {
          inferenceActuallyStarted.set(true)
          ServerMetrics.onInferenceStarted()
        }
      },
      runInference = session::runInference,
      cancelInference = { ServerLlmModelHelper.stopResponse(model) },
      recoverConversation = session::recoverConversation,
      onInferenceFinished = {
        session.restoreOriginalConfigIfLoaded()
        if (inferenceActuallyStarted.get()) ServerMetrics.onInferenceCompleted()
      },
      elapsedMs = { SystemClock.elapsedRealtime() },
      onCaughtThrowable = { t -> emitDebugStackTrace(t, "execute", model.name) },
      onExecutionReady = cancellationBridge::attach,
    )
    if (logId != null) RequestLogStore.unregisterCancellation(logId)

    val nativeCalls = session.capturedNativeToolCalls.get()
    if (nativeCalls != null && nativeCalls.isNotEmpty() && onNativeToolCalls != null) {
      onNativeToolCalls(SchemaInjectionBridge.convertNativeToolCalls(nativeCalls))
    }

    if (result.error == "client_disconnected") {
      return handleCancellation(result, logId, requestId, endpoint, prefs, logSuffix = "client_disconnected=true", returnMessage = "Client disconnected")
    }

    if (cancellationBridge.cancellationWasAccepted()) {
      return handleCancellation(result, logId, requestId, endpoint, prefs, logSuffix = "user_stopped=true", returnMessage = "Generation stopped by user in OlliteRT")
    }

    return if (result.error != null) {
      // Error counting is done by the caller after classifying the error via enrichLlmError()
      logEvent("request_error id=$requestId endpoint=$endpoint error=${result.error} totalMs=${result.totalMs} ttfbMs=${result.ttfbMs} outputChars=${result.output?.length ?: 0}")
      null to result.error
    } else {
      val outputLen = result.output?.length ?: 0
      // Rough token estimate: ~4 chars per token
      val inputTokens = estimateTokensLong(prompt)
      val outputTokens = estimateTokensLongByLength(outputLen)
      val maxCtx = model.configValues.maxTokensLong() ?: 0L
      ServerMetrics.addTokens(outputTokens)
      ServerMetrics.recordLatency(result.totalMs)
      ServerMetrics.recordTtfb(result.ttfbMs)
      if (result.ttfbMs > 0) {
        ServerMetrics.recordInferenceMetrics(inputTokens, outputTokens, result.ttfbMs, result.totalMs - result.ttfbMs, maxCtx)
      }
      emitDebugInferenceLog(inputTokens, outputTokens, result.ttfbMs, result.totalMs - result.ttfbMs, result.totalMs, model.name, prefs)
      logEvent("request_done id=$requestId endpoint=$endpoint totalMs=${result.totalMs} ttfbMs=${result.ttfbMs} outputChars=$outputLen")
      // Prepend thinking content wrapped in <think> tags if present
      val output = if (!result.thinking.isNullOrEmpty()) {
        "<think>${result.thinking}</think>${result.output.orEmpty()}"
      } else {
        result.output
      }
      output to null
    }
  }

  // ── Streaming events & state management ───────────────────────────────────
  // StreamEvent and StreamState are defined in InferenceStreamingLoop.kt

  // ── Streaming inference: /v1/responses ───────────────────────────────────

  fun streamLlm(
    model: Model,
    prompt: String,
    requestId: String,
    endpoint: String,
    timeoutSeconds: Long = RESPONSES_TIMEOUT_SECONDS,
    images: List<ByteArray> = emptyList(),
    audioClips: List<ByteArray> = emptyList(),
    logId: String? = null,
    configSnapshot: Map<String, Any>? = null,
    json: Json,
    tools: List<ToolSpec>? = null,
    prefs: RequestPrefsSnapshot? = null,
    schemaInjectionProviders: List<com.google.ai.edge.litertlm.ToolProvider> = emptyList(),
    schemaInjectionMessages: List<com.google.ai.edge.litertlm.Message> = emptyList(),
  ): HttpResponse {
    val now = BridgeUtils.epochSeconds()
    val format = ResponsesApiFormat(model.name, now, json, tools, hasSchemaInjection = schemaInjectionProviders.isNotEmpty())
    return streamInference(model, prompt, requestId, endpoint, format, timeoutSeconds, images, audioClips, logId, configSnapshot, prefs, schemaInjectionProviders, schemaInjectionMessages)
  }

  // ── Streaming inference: /v1/chat/completions ────────────────────────────

  internal fun streamChatLlm(
    model: Model,
    prompt: String,
    requestId: String,
    endpoint: String,
    timeoutSeconds: Long = CHAT_COMPLETIONS_TIMEOUT_SECONDS,
    images: List<ByteArray> = emptyList(),
    audioClips: List<ByteArray> = emptyList(),
    logId: String? = null,
    includeUsage: Boolean = false,
    stopSequences: List<String>? = null,
    tools: List<ToolSpec>? = null,
    configSnapshot: Map<String, Any>? = null,
    json: Json,
    prefs: RequestPrefsSnapshot? = null,
    schemaInjectionProviders: List<com.google.ai.edge.litertlm.ToolProvider> = emptyList(),
    schemaInjectionMessages: List<com.google.ai.edge.litertlm.Message> = emptyList(),
    suppressPerModelSystem: Boolean = false,
    enableThinkingOverride: Boolean? = null,
    incrementalUserText: String? = null,
    conversationCacheGeneration: Long? = null,
    prepareConversation: (() -> ConversationPreparation)? = null,
    onConversationFinished: (Boolean, String?) -> Unit = { _, _ -> },
  ): HttpResponse {
    val now = BridgeUtils.epochSeconds()
    val format = ChatCompletionsFormat(model.name, now, stopSequences, tools, json, includeUsage, hasSchemaInjection = schemaInjectionProviders.isNotEmpty())
    return streamInference(model, prompt, requestId, endpoint, format, timeoutSeconds, images, audioClips, logId, configSnapshot, prefs, schemaInjectionProviders, schemaInjectionMessages, suppressPerModelSystem, enableThinkingOverride, incrementalUserText, conversationCacheGeneration, prepareConversation, onConversationFinished)
  }

  // ── Streaming inference: /v1/completions ───────────────────────────────

  fun streamCompletions(
    model: Model,
    prompt: String,
    requestId: String,
    endpoint: String,
    timeoutSeconds: Long = CHAT_COMPLETIONS_TIMEOUT_SECONDS,
    logId: String? = null,
    includeUsage: Boolean = false,
    stopSequences: List<String>? = null,
    configSnapshot: Map<String, Any>? = null,
    json: Json,
    prefs: RequestPrefsSnapshot? = null,
  ): HttpResponse {
    val now = BridgeUtils.epochSeconds()
    val format = CompletionsFormat(model.name, now, stopSequences, json, includeUsage)
    return streamInference(model, prompt, requestId, endpoint, format, timeoutSeconds, emptyList(), emptyList(), logId, configSnapshot, prefs)
  }

  // ── Streaming inference: /v1/messages (Anthropic) ───────────────────────

  internal fun streamMessagesLlm(
    model: Model,
    prompt: String,
    requestId: String,
    endpoint: String = "/v1/messages",
    timeoutSeconds: Long = CHAT_COMPLETIONS_TIMEOUT_SECONDS,
    images: List<ByteArray> = emptyList(),
    audioClips: List<ByteArray> = emptyList(),
    logId: String? = null,
    stopSequences: List<String>? = null,
    tools: List<ToolSpec>? = null,
    configSnapshot: Map<String, Any>? = null,
    prefs: RequestPrefsSnapshot? = null,
    schemaInjectionProviders: List<com.google.ai.edge.litertlm.ToolProvider> = emptyList(),
    schemaInjectionMessages: List<com.google.ai.edge.litertlm.Message> = emptyList(),
    suppressPerModelSystem: Boolean = false,
    enableThinkingOverride: Boolean? = null,
    requestModelId: String,
    incrementalUserText: String? = null,
    conversationCacheGeneration: Long? = null,
    prepareConversation: (() -> ConversationPreparation)? = null,
    onConversationFinished: (Boolean, String?) -> Unit = { _, _ -> },
  ): HttpResponse {
    val format = AnthropicMessagesFormat(
      modelName = model.name,
      requestModelId = requestModelId,
      stopSequences = stopSequences,
      tools = tools,
      hasSchemaInjection = schemaInjectionProviders.isNotEmpty(),
      verboseDebug = prefs?.verboseDebug ?: ServerPrefs.isVerboseDebugEnabled(context),
    )
    return streamInference(
      model, prompt, requestId, endpoint, format, timeoutSeconds, images, audioClips,
      logId, configSnapshot, prefs, schemaInjectionProviders, schemaInjectionMessages,
      suppressPerModelSystem, enableThinkingOverride, incrementalUserText,
      conversationCacheGeneration,
      prepareConversation,
      onConversationFinished,
    )
  }

  // ── Unified streaming implementation ────────────────────────────────────

  private fun streamInference(
    model: Model,
    prompt: String,
    requestId: String,
    endpoint: String,
    format: StreamingFormat,
    timeoutSeconds: Long,
    images: List<ByteArray>,
    audioClips: List<ByteArray>,
    logId: String?,
    configSnapshot: Map<String, Any>?,
    prefs: RequestPrefsSnapshot? = null,
    schemaInjectionProviders: List<com.google.ai.edge.litertlm.ToolProvider> = emptyList(),
    schemaInjectionMessages: List<com.google.ai.edge.litertlm.Message> = emptyList(),
    suppressPerModelSystem: Boolean = false,
    enableThinkingOverride: Boolean? = null,
    incrementalUserText: String? = null,
    conversationCacheGeneration: Long? = null,
    prepareConversation: (() -> ConversationPreparation)? = null,
    onConversationFinished: (Boolean, String?) -> Unit = { _, _ -> },
  ): HttpResponse = streamingCoordinator.streamInference(
    model = model,
    prompt = prompt,
    requestId = requestId,
    endpoint = endpoint,
    format = format,
    timeoutSeconds = timeoutSeconds,
    images = images,
    audioClips = audioClips,
    logId = logId,
    configSnapshot = configSnapshot,
    prefs = prefs,
    schemaInjectionProviders = schemaInjectionProviders,
    schemaInjectionMessages = schemaInjectionMessages,
    suppressPerModelSystem = suppressPerModelSystem,
    enableThinkingOverride = enableThinkingOverride,
    incrementalUserText = incrementalUserText,
    conversationCacheGeneration = conversationCacheGeneration,
    prepareConversation = prepareConversation,
    onConversationFinished = onConversationFinished,
  )

  // ── Cancellation helper ──────────────────────────────────────────────────

  private fun handleCancellation(
    result: InferenceResult,
    logId: String?,
    requestId: String,
    endpoint: String,
    prefs: RequestPrefsSnapshot?,
    logSuffix: String,
    returnMessage: String,
  ): Pair<String?, String> = InferenceWarmupHelper.handleCancellation(
    context = context,
    result = result,
    logId = logId,
    requestId = requestId,
    endpoint = endpoint,
    prefs = prefs,
    logSuffix = logSuffix,
    returnMessage = returnMessage,
    logEvent = logEvent,
  )

  // ── Warmup ───────────────────────────────────────────────────────────────

  /**
   * Warm up the model with a short test inference.
   * Used during model loading to pre-fill caches and verify the model works.
   */
  // Safe to use runBlocking: only called from OlliteRT-ModelLoad thread, never main thread.
  @WorkerThread
  fun warmUpModel(model: Model) {
    InferenceWarmupHelper.warmUpModel(context, model) { m, p, reqId, ep, timeout, eagerVision ->
      runLlm(m, p, reqId, ep, timeoutSeconds = timeout, eagerVisionInit = eagerVision)
    }
  }

  // ── Verbose debug logging ────────────────────────────────────────────────

  /**
   * Emit verbose debug log entries for per-request timing and memory usage.
   * Only logs when the verbose debug toggle is enabled in Settings.
   */
  private fun emitDebugInferenceLog(
    inputTokens: Long,
    outputTokens: Long,
    ttfbMs: Long,
    generationMs: Long,
    totalMs: Long,
    modelName: String?,
    prefs: RequestPrefsSnapshot? = null,
  ) {
    if (modelName != null) {
      InferenceMetricsCollector.logVerboseInferenceDetails(
        context = context,
        prefs = prefs,
        modelName = modelName,
        inputTokens = inputTokens.toInt(),
        outputTokens = outputTokens.toInt(),
        ttfbMs = ttfbMs,
        generationMs = generationMs,
        totalMs = totalMs,
      )
    }
  }

  companion object {
    private const val TAG = "OlliteRT.Inference"

    private fun buildCombinedText(fullText: CharSequence, fullThinking: CharSequence): String =
      InferenceStreamingLoop.buildCombinedText(fullText, fullThinking)

    /**
     * Truncates model output at the first occurrence of any stop sequence.
     * Returns (truncated text, was truncation applied, the stop string that matched
     * — null when nothing matched). The matched string is needed by the Anthropic
     * /v1/messages response, which echoes it back in the `stop_sequence` field.
     */
    fun applyStopSequences(text: String, stopSequences: List<String>?): Triple<String, Boolean, String?> =
      InferenceStopCondition.applyStopSequences(text, stopSequences)

    /**
     * Injects a JSON mode instruction into the prompt when response_format is requested.
     */
    fun applyResponseFormat(prompt: String, responseFormat: ResponseFormat?): String =
      InferenceStopCondition.applyResponseFormat(prompt, responseFormat)

    /**
     * Classify an opaque LLM error string and return the enriched message with a
     * recovery suggestion appended (if one is available for the classified error kind).
     *
     * Also returns the [ErrorKind] so callers can use it for metrics and API responses.
     */
    fun enrichLlmError(error: String, context: Context): Pair<String, ErrorKind> =
      InferenceMetricsCollector.enrichLlmError(error, context)

    /**
     * Extract actual token counts from LiteRT error messages.
     * LiteRT reports context overflow as "N >= M" (e.g. "6579 >= 4000").
     * Returns (actualInputTokens, maxContextTokens) or null if not a context overflow error.
     */
    fun extractActualTokenCounts(responseBody: String): Pair<Long, Long>? =
      InferenceMetricsCollector.extractActualTokenCounts(responseBody)
  }
}
