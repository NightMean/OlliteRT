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
import com.ollitert.llm.server.data.BLOCKING_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.CHAT_COMPLETIONS_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.ErrorKind
import com.ollitert.llm.server.data.EventCategory
import com.ollitert.llm.server.data.LOG_ERROR_PREVIEW_SHORT_CHARS
import com.ollitert.llm.server.data.LOG_STREAMING_PREVIEW_DEBOUNCE_MS
import com.ollitert.llm.server.data.LogLevel
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RESPONSES_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.RequestLogEntry
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import com.ollitert.llm.server.data.SSE_PING_INTERVAL_MS
import com.ollitert.llm.server.data.STREAM_OUTER_TIMEOUT_SAFETY_BUFFER_SECONDS
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.WARMUP_MESSAGE
import com.ollitert.llm.server.data.isThinkingEnabled
import com.ollitert.llm.server.data.llmSupportAudio
import com.ollitert.llm.server.data.llmSupportImage
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.data.maxTokensInt
import com.ollitert.llm.server.data.maxTokensLong
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
import java.util.concurrent.atomic.AtomicReference

internal data class ConversationPreparation(
  val incrementalUserText: String?,
  val cacheGeneration: Long,
  val systemInstruction: Contents? = null,
)

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

  /**
   * Re-initialize the model if needed (null instance or missing vision support).
   * Must be called inside synchronized(inferenceLock). Returns an error message on failure, or null on success.
   *
   * Passes the persisted base config directly to initialize() via configOverrides,
   * avoiding the previous pattern of temporarily swapping model.configValues which
   * was visible to unsynchronized readers on Ktor threads.
   */
  private fun reinitIfNeeded(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
  ): String? {
    val needsReinit = model.instance == null ||
      (supportImage && !model.initializedWithVision)
    if (!needsReinit) return null

    if (model.instance != null) {
      Log.i(TAG, "Re-initializing model for vision/audio support")
      ServerLlmModelHelper.safeCleanup(model)
    }
    val initConfig = ServerPrefs.getInferenceConfig(context, model.prefsKey)
    var err = ""
    ServerLlmModelHelper.initialize(
      context = context,
      model = model,
      supportImage = supportImage,
      supportAudio = supportAudio,
      onDone = { err = it },
      systemInstruction = buildSystemInstruction(model.prefsKey),
      configOverrides = initConfig,
    )
    if (err.isNotEmpty()) {
      model.instance = null
      return err
    }
    model.initializedWithVision = supportImage
    return null
  }

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

    val enableThinking = if (model.llmSupportThinking) {
      enableThinkingOverride ?: model.isThinkingEnabled
    } else {
      false
    }
    val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

    // Captured inside the resetConversation lambda (which runs under inferenceLock) so
    // that concurrent updateConfigValues() writes are visible before we snapshot.
    var originalConfig: Map<String, Any>? = null
    val capturedNativeToolCalls = AtomicReference<List<com.google.ai.edge.litertlm.ToolCall>?>(null)
    var preparedIncrementalUserText = incrementalUserText
    var preparedCacheGeneration = conversationCacheGeneration
    var preparedSystemInstruction: Contents? = null

    val result = InferenceGateway.execute(
      prompt = prompt,
      timeoutSeconds = timeoutSeconds,
      executor = executor,
      inferenceLock = inferenceLock,
      resetConversation = {
        // Skip inference entirely if cancelled while queued.
        if (cancellationBridge.cancellationWasAccepted()) {
          throw java.util.concurrent.CancellationException("cancelled_while_queued")
        }
        val initErr = reinitIfNeeded(model, supportImage, supportAudio)
        if (initErr != null) throw RuntimeException("model_init_failed: $initErr")
        inferenceActuallyStarted.set(true)
        ServerMetrics.onInferenceStarted()
        if (logId != null) RequestLogStore.update(logId) { it.copy(isGenerating = true) }
        if (configSnapshot != null) {
          originalConfig = model.configValues
          model.configValues = configSnapshot
        }
        prepareConversation?.invoke()?.let { prepared ->
          preparedIncrementalUserText = prepared.incrementalUserText
          preparedCacheGeneration = prepared.cacheGeneration
          preparedSystemInstruction = prepared.systemInstruction
        }
        if (preparedIncrementalUserText != null) {
          Log.i(TAG, "INCREMENTAL_REUSE_BLOCKING requestId=$requestId model=${model.name} userTextLen=${preparedIncrementalUserText?.length}")
        } else {
          ServerLlmModelHelper.resetConversation(
            model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            systemInstruction = if (prepareConversation != null) preparedSystemInstruction else if (suppressPerModelSystem) null else buildSystemInstruction(model.prefsKey),
            tools = schemaInjectionProviders,
            initialMessages = schemaInjectionMessages,
            enableConversationConstrainedDecoding = schemaInjectionProviders.isNotEmpty(),
            conversationCacheGeneration = preparedCacheGeneration,
          )
        }
      },
      runInference = { input, onPartial, onError ->
        ServerLlmModelHelper.runInference(
          model = model,
          input = input,
          resultListener = { partial, done, thought -> onPartial(partial, done, thought) },
          cleanUpListener = {},
          onError = onError,
          images = images,
          audioClips = audioClips,
          extraContext = extraContext,
          incrementalUserText = preparedIncrementalUserText,
          conversationCacheGeneration = preparedCacheGeneration,
          onNativeToolCalls = if (schemaInjectionProviders.isNotEmpty()) { calls ->
            capturedNativeToolCalls.set(calls)
          } else null,
        )
      },
      cancelInference = { ServerLlmModelHelper.stopResponse(model) },
      recoverConversation = {
        if (originalConfig != null && model.instance != null) {
          model.configValues = originalConfig
        }
        ServerLlmModelHelper.resetConversation(
          model,
          supportImage = supportImage,
          supportAudio = supportAudio,
          systemInstruction = if (prepareConversation != null) preparedSystemInstruction else if (suppressPerModelSystem) null else buildSystemInstruction(model.prefsKey),
          tools = schemaInjectionProviders,
          initialMessages = schemaInjectionMessages,
          conversationCacheGeneration = preparedCacheGeneration,
        )
      },
      onInferenceFinished = {
        if (originalConfig != null && model.instance != null) {
          model.configValues = originalConfig
        }
        if (inferenceActuallyStarted.get()) ServerMetrics.onInferenceCompleted()
      },
      elapsedMs = { SystemClock.elapsedRealtime() },
      onCaughtThrowable = { t -> emitDebugStackTrace(t, "execute", model.name) },
      onExecutionReady = cancellationBridge::attach,
    )
    if (logId != null) RequestLogStore.unregisterCancellation(logId)

    val nativeCalls = capturedNativeToolCalls.get()
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
    // KV-cache reuse: when non-null, send only this user text via Message.user(...)
    // on the existing Conversation instead of resetting + sending the full rendered
    // [prompt]. Caller (EndpointHandlers) decides eligibility via decideIncrementalReuse.
    incrementalUserText: String? = null,
    conversationCacheGeneration: Long? = null,
    prepareConversation: (() -> ConversationPreparation)? = null,
    onConversationFinished: (Boolean, String?) -> Unit = { _, _ -> },
  ): HttpResponse {
    val streamStartMs = SystemClock.elapsedRealtime()
    ServerMetrics.addTokensIn(estimateTokensLong(prompt))
    ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = audioClips.isNotEmpty())

    // Pre-validation must happen BEFORE returning HttpResponse.Sse — the caller needs
    // a JSON error response, not a streaming response that immediately errors.
    val eagerVision = prefs?.eagerVisionInit ?: ServerPrefs.isEagerVisionInit(context)
    val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVision)
    val supportAudio = model.llmSupportAudio

    // Register cancel callback before any lock so queued requests are immediately cancellable.
    val cancellationBridge = RequestCancellationBridge()
    val channelRef = AtomicReference<Channel<StreamEvent>?>(null)
    val inferenceControl = AtomicReference<InferenceGateway.InferenceControl?>(null)
    if (logId != null) {
      RequestLogStore.registerCancellation(logId) {
        when (cancellationBridge.request()) {
          CancellationRequestStatus.PENDING,
          CancellationRequestStatus.ACCEPTED -> {
            channelRef.get()?.close()
            true
          }
          CancellationRequestStatus.REJECTED -> false
        }
      }
    }

    val enableThinking = if (model.llmSupportThinking) {
      enableThinkingOverride ?: model.isThinkingEnabled
    } else {
      false
    }
    val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

    // Read prefs eagerly (before the Ktor coroutine runs) — SharedPreferences reads
    // should happen on the calling thread, not inside the SSE writer lambda.
    val streamPreview = prefs?.streamLogsPreview ?: ServerPrefs.isStreamLogsPreview(context)
    val keepPartial = prefs?.keepPartialResponse ?: ServerPrefs.isKeepPartialResponse(context)

    // Outer Ktor-writer safety timeout: one extra safety buffer beyond the inner
    // channel-consumption timeout below, so the outer net only fires if the inner
    // timeout + cleanup fails to unwind. Both derive from the user's configurable
    // per-endpoint timeout, so raising the setting raises this cap too.
    val outerTimeoutMs = (timeoutSeconds + 2 * STREAM_OUTER_TIMEOUT_SAFETY_BUFFER_SECONDS) * 1000
    return HttpResponse.Sse(outerTimeoutMs = outerTimeoutMs) { writer ->
      val channel = Channel<StreamEvent>(Channel.UNLIMITED)
      channelRef.set(channel)
      val state = StreamState(
        context,
        model,
        requestId,
        endpoint,
        logId,
        streamStartMs,
        keepPartial,
        inferenceControl,
        onConversationFinished,
        onLogEvent = { msg -> logEvent(msg) },
      )

      // Captured inside the resetConversation lambda (which runs under inferenceLock) so
      // that concurrent updateConfigValues() writes are visible before we snapshot.
      var originalConfig: Map<String, Any>? = null
      val capturedNativeToolCalls = AtomicReference<List<com.google.ai.edge.litertlm.ToolCall>?>(null)
      var preparedIncrementalUserText = incrementalUserText
      var preparedCacheGeneration = conversationCacheGeneration
      var preparedSystemInstruction: Contents? = null

      // Pre-emit the format's header (e.g. Anthropic `message_start`) as the very
      // first SSE bytes so the client sees a response before prefill begins. Without
      // this, on-device prefill (often >30s for multi-KB prompts on Gemma-4-E2B)
      // exceeds the SDK's idle timeout and the client cancels with zero output.
      // OAI-shape formats opt out via emitsHeaderEarly=false.
      if (!format.bufferAllTokens && format.emitsHeaderEarly && !writer.isCancelled) {
        try {
          format.emitHeader(writer)
          state.headerWritten = true
        } catch (e: Exception) {
          Log.w(TAG, "Pre-emit header failed for $requestId", e)
        }
      }

      // Heartbeat coroutine: while inference is still in prefill (no token observed)
      // and the writer is alive, emit a format-specific ping every SSE_PING_INTERVAL_MS
      // so the client's idle-stream timeout doesn't fire. Anthropic's spec defines the
      // `ping` event explicitly; non-Anthropic formats default to a no-op so this loop
      // is harmless on every code path. Cancelled as soon as the first token arrives,
      // the channel closes, or the SSE writer reports cancellation.
      val heartbeatJob = if (format.emitsHeaderEarly) {
        CoroutineScope(kotlin.coroutines.coroutineContext).launch {
          try {
            while (isActive) {
              delay(SSE_PING_INTERVAL_MS)
              if (writer.isCancelled || state.firstTokenMs != 0L || state.inferenceCompleted) break
              try {
                format.emitPing(writer)
              } catch (e: Exception) {
                Log.w(TAG, "Heartbeat ping failed for $requestId", e)
                break
              }
            }
          } catch (_: kotlinx.coroutines.CancellationException) {
            // Normal scope cancel — nothing to do.
          }
        }
      } else null

      // Launch inference on the executor thread. Callbacks send events into the channel
      // via trySend() — non-blocking from the executor thread's perspective.
      InferenceGateway.executeStreaming(
        prompt = prompt,
        timeoutSeconds = timeoutSeconds,
        executor = executor,
        inferenceLock = inferenceLock,
        resetConversation = {
          if (cancellationBridge.cancellationWasAccepted()) {
            throw java.util.concurrent.CancellationException("cancelled_while_queued")
          }
          val initErr = reinitIfNeeded(model, supportImage, supportAudio)
          if (initErr != null) throw RuntimeException("model_init_failed: $initErr")
          state.markStarted()
          if (logId != null) RequestLogStore.update(logId) { it.copy(isGenerating = true) }
          if (configSnapshot != null) {
            originalConfig = model.configValues
            model.configValues = configSnapshot
          }
          prepareConversation?.invoke()?.let { prepared ->
            preparedIncrementalUserText = prepared.incrementalUserText
            preparedCacheGeneration = prepared.cacheGeneration
            preparedSystemInstruction = prepared.systemInstruction
          }
          if (preparedIncrementalUserText != null) {
            // Reuse the live Conversation: SDK has the prior history in its internal
            // diff state, and runInference will dispatch via Message.user(text) so
            // only the new turn is prefilled.
            Log.i(TAG, "INCREMENTAL_REUSE requestId=$requestId model=${model.name} userTextLen=${preparedIncrementalUserText?.length}")
          } else {
            ServerLlmModelHelper.resetConversation(
              model,
              supportImage = supportImage,
              supportAudio = supportAudio,
              systemInstruction = if (prepareConversation != null) preparedSystemInstruction else if (suppressPerModelSystem) null else buildSystemInstruction(model.prefsKey),
              tools = schemaInjectionProviders,
              initialMessages = schemaInjectionMessages,
              enableConversationConstrainedDecoding = schemaInjectionProviders.isNotEmpty(),
              conversationCacheGeneration = preparedCacheGeneration,
            )
          }
        },
        runInference = { input, onPartial, onError ->
          ServerLlmModelHelper.runInference(
            model = model,
            input = input,
            resultListener = { partial, done, thought -> onPartial(partial, done, thought) },
            cleanUpListener = {},
            onError = onError,
            images = images,
            audioClips = audioClips,
            extraContext = extraContext,
            incrementalUserText = preparedIncrementalUserText,
            conversationCacheGeneration = preparedCacheGeneration,
            onNativeToolCalls = if (schemaInjectionProviders.isNotEmpty()) { calls ->
              capturedNativeToolCalls.set(calls)
            } else null,
          )
        },
        cancelInference = { ServerLlmModelHelper.stopResponse(model) },
        recoverConversation = {
          if (originalConfig != null && model.instance != null) {
            model.configValues = originalConfig
          }
          ServerLlmModelHelper.resetConversation(
            model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            systemInstruction = if (prepareConversation != null) preparedSystemInstruction else if (suppressPerModelSystem) null else buildSystemInstruction(model.prefsKey),
            tools = schemaInjectionProviders,
            initialMessages = schemaInjectionMessages,
            conversationCacheGeneration = preparedCacheGeneration,
          )
        },
        onToken = { partial, done, thought ->
          channel.trySend(StreamEvent.Token(partial, done, thought))
        },
        onError = { error ->
          channel.trySend(StreamEvent.Error(error))
        },
        onInferenceFinished = {
          if (originalConfig != null && model.instance != null) {
            model.configValues = originalConfig
          }
          state.markMetricsCompleted()
        },
        onCaughtThrowable = { t -> emitDebugStackTrace(t, format.sourceTag, model.name) },
        onExecutionReady = { control ->
          inferenceControl.set(control)
          cancellationBridge.attach(control)
          if (cancellationBridge.cancellationWasAccepted()) channel.close()
        },
      )

      // Consume events from the channel in the Ktor coroutine context.
      // The for-loop terminates when the channel is closed (by done, error, or cancellation).
      // Safety timeout: generous buffer beyond inference timeout to catch gateway bugs that
      // would otherwise hang this coroutine indefinitely.
      try {
        kotlinx.coroutines.withTimeout((timeoutSeconds + STREAM_OUTER_TIMEOUT_SAFETY_BUFFER_SECONDS) * 1000) {
          for (event in channel) {
            // Check for client disconnect (Ktor closed the writer)
            if (writer.isCancelled) {
              val elapsedMs = state.elapsedMs()
              Log.i(TAG, "STREAM_DISCONNECT requestId=$requestId endpoint=$endpoint elapsedMs=$elapsedMs " +
                "firstTokenMs=${state.firstTokenMs} headerWritten=${state.headerWritten} " +
                "fullText.len=${state.fullText.length} fullThinking.len=${state.fullThinking.length}")
              inferenceControl.get()?.cancel(InferenceGateway.CancellationReason.CALLER)
              state.markCompleted()
              state.logCancellation()
              format.emitCancellation(writer, state.headerWritten)
              channel.close()
              break
            }

            when (event) {
              is StreamEvent.Token -> {
                try {
                  state.handleToken(event, format, writer, prompt, configSnapshot, prefs, streamPreview, channel, capturedNativeToolCalls)
                } catch (e: Exception) {
                  if (logId != null) RequestLogStore.unregisterCancellation(logId)
                  state.markCompleted()
                  Log.w(TAG, "Stream write failed for request $requestId", e)
                  logEvent("request_error id=$requestId endpoint=$endpoint error=stream_write_failed streaming=true")
                  if (logId != null) {
                    val errorJson = ResponseRenderer.renderJsonError("stream_write_failed")
                    RequestLogStore.update(logId) { it.copy(partialText = null, responseBody = errorJson, isPending = false, latencyMs = state.elapsedMs(), level = LogLevel.ERROR) }
                  }
                  try { writer.finish() } catch (e2: Exception) { Log.w(TAG, "writer.finish() failed during cleanup", e2) }
                  channel.close()
                }
              }

              is StreamEvent.Error -> {
                state.handleError(event.error, writer, channel, format)
              }
            }
          }
        }
        // Channel closed externally (user tapped Cancel in Logs) — clean up.
        // Normal completion and error paths call markCompleted() before closing
        // the channel, so this block only fires for the external-cancel case.
        if (!state.inferenceCompleted) {
          state.markCompleted()
          state.logCancellation()
          try { format.emitCancellation(writer, state.headerWritten) } catch (e: Exception) { Log.w(TAG, "emitCancellation failed during cleanup", e) }
        }
      } catch (_: kotlinx.coroutines.CancellationException) {
        // Ktor cancelled the coroutine (client disconnect or withTimeout expired) — clean up
        inferenceControl.get()?.cancel(InferenceGateway.CancellationReason.CALLER)
        channel.close()
        if (!state.inferenceCompleted) {
          // Finalize the log entry (isPending=false, isCancelled, 499) so it doesn't
          // stay stuck "generating", then emit the format's terminating sequence
          // (footer + [DONE]/message_stop) so a client still reading the stream gets a
          // clean close instead of hanging. On a genuine client disconnect the socket is
          // already dead and the emit throws harmlessly; on a server-side timeout the
          // socket is alive and the client sees proper closure. The emit runs under
          // NonCancellable because the surrounding coroutine context is already cancelled
          // here, which would otherwise make the suspending emit calls throw immediately.
          state.markCompleted()
          state.logCancellation()
          try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
              format.emitCancellation(writer, state.headerWritten)
            }
          } catch (e: Exception) {
            Log.w(TAG, "emitCancellation failed after stream cancellation for $requestId", e)
          }
        } else if (logId != null) {
          RequestLogStore.unregisterCancellation(logId)
        }
      } finally {
        // Safety net: guarantee isInferring flag is cleared even if an unexpected
        // exception bypasses normal completion/cancellation paths. markCompleted
        // flips the local emitting-done flag; markMetricsCompleted decrements
        // the ServerMetrics counter idempotently so the "processing" pill clears
        // even when onInferenceFinished was never reached.
        state.markCompleted()
        state.markMetricsCompleted()
        if (state.inferenceStarted) state.finishConversation(isReusable = false)
        heartbeatJob?.cancel()
      }
    }
  }

  // ── Cancellation helper ──────────────────────────────────────────────────

  private fun handleCancellation(
    result: InferenceResult,
    logId: String?,
    requestId: String,
    endpoint: String,
    prefs: RequestPrefsSnapshot?,
    logSuffix: String,
    returnMessage: String,
  ): Pair<String?, String> {
    val keepPartial = prefs?.keepPartialResponse ?: ServerPrefs.isKeepPartialResponse(context)
    val partial = if (keepPartial && !result.output.isNullOrEmpty()) result.output else null
    if (logId != null) {
      RequestLogStore.update(logId) {
        it.copy(partialText = partial, isPending = false, isCancelled = true, statusCode = 499, latencyMs = result.totalMs)
      }
    }
    logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=false $logSuffix outputChars=${result.output?.length ?: 0}")
    return null to returnMessage
  }

  // ── Warmup ───────────────────────────────────────────────────────────────

  /**
   * Warm up the model with a short test inference.
   * Used during model loading to pre-fill caches and verify the model works.
   */
  // Safe to use runBlocking: only called from OlliteRT-ModelLoad thread, never main thread.
  @WorkerThread
  fun warmUpModel(model: Model) {
    val startMs = SystemClock.elapsedRealtime()
    val eagerVision = ServerPrefs.isEagerVisionInit(context)
    val (result, error) = kotlinx.coroutines.runBlocking {
      runLlm(model, WARMUP_MESSAGE, "warmup", "warmup", timeoutSeconds = ServerPrefs.getTimeoutWarmup(context), eagerVisionInit = eagerVision)
    }
    val elapsedMs = SystemClock.elapsedRealtime() - startMs
    if (error != null && error.startsWith("model_init_failed:")) {
      throw RuntimeException(error.removePrefix("model_init_failed: "))
    }
    val snippet = result?.take(LOG_ERROR_PREVIEW_SHORT_CHARS)?.replace("\n", " ") ?: "no response"
    RequestLogStore.addEvent(
      "Sending a warmup message: \"$WARMUP_MESSAGE\" → \"$snippet\" (${elapsedMs}ms)",
      modelName = model.name,
      category = EventCategory.MODEL,
    )
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
    if (!(prefs?.verboseDebug ?: ServerPrefs.isVerboseDebugEnabled(context))) return
    val rt = Runtime.getRuntime()
    val heapTotalMb = rt.totalMemory() / (1024.0 * 1024.0)
    val heapFreeMb = rt.freeMemory() / (1024.0 * 1024.0)
    val nativeAllocMb = android.os.Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
    val nativeTotalMb = android.os.Debug.getNativeHeapSize() / (1024.0 * 1024.0)
    val decodeSpeed = if (outputTokens > 0 && generationMs > 0) outputTokens.toDouble() / (generationMs / 1000.0) else 0.0
    val prefillSpeed = if (inputTokens > 0 && ttfbMs > 0) inputTokens.toDouble() / (ttfbMs / 1000.0) else 0.0

    val body = buildString {
      appendLine("Timing: TTFB ${ttfbMs}ms, generation ${generationMs}ms, total ${totalMs}ms")
      appendLine("Tokens: ${inputTokens} input → ${outputTokens} output")
      appendLine("Speed: ${String.format(java.util.Locale.US, "%.1f", prefillSpeed)} t/s prefill, ${String.format(java.util.Locale.US, "%.1f", decodeSpeed)} t/s decode")
      appendLine("Heap: ${String.format(java.util.Locale.US, "%.1f", heapFreeMb)}MB free / ${String.format(java.util.Locale.US, "%.1f", heapTotalMb)}MB total")
      append("Native: ${String.format(java.util.Locale.US, "%.1f", nativeAllocMb)}MB allocated / ${String.format(java.util.Locale.US, "%.1f", nativeTotalMb)}MB total")
    }

    RequestLogStore.addEvent(
      "Inference details: ${inputTokens}→${outputTokens} tokens in ${totalMs}ms",
      level = LogLevel.DEBUG,
      modelName = modelName,
      category = EventCategory.SERVER,
      body = body,
    )
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
