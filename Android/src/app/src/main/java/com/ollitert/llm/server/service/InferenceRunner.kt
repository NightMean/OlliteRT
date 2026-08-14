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

  // ── Streaming format abstraction ──────────────────────────────────────────

  /**
   * Channel events bridging the executor thread (producer) to the Ktor coroutine (consumer).
   * The executor's onToken/onError callbacks send events via trySend(); the Ktor coroutine
   * consumes them and calls the appropriate SseWriter/format methods.
   */
  private sealed interface StreamEvent {
    data class Token(val partial: String, val done: Boolean, val thought: String?) : StreamEvent
    data class Error(val error: String) : StreamEvent
  }

  private sealed interface StreamingFormat {
    val sourceTag: String
    // When true, all tokens are collected in memory and sent as a single response after
    // inference completes. This prevents word-by-word streaming but is required when tools
    // are present without native SDK tool calling — the server must see the full output to
    // determine if it's a tool call or plain text before sending anything to the client.
    val bufferAllTokens: Boolean
    val stopSequences: List<String>?

    suspend fun emitHeader(writer: SseWriter)
    suspend fun emitThinkingDelta(writer: SseWriter, text: String)
    suspend fun emitContentDelta(writer: SseWriter, text: String)
    suspend fun emitThinkingClose(writer: SseWriter)
    suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean)
    // True when the format wants its `message_start`-equivalent header sent
    // immediately at request acceptance (before prefill begins) instead of being
    // gated on the first token. Anthropic's Messages spec defines an explicit
    // `message_start` event and ping events for the prefill window; OAI-shape
    // formats have no such concept, so they keep the existing first-token gate.
    val emitsHeaderEarly: Boolean get() = false
    // Format-specific keep-alive emission while inference is still in prefill.
    // Default is a no-op so non-Anthropic formats stay silent until first token.
    suspend fun emitPing(writer: SseWriter) {}
    fun estimateInputTokens(prompt: String): Long
    fun estimateInputTokensInt(prompt: String): Int
    suspend fun emitCompletion(
      writer: SseWriter,
      fullText: String,
      fullThinking: String,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      maxTokens: Int?,
      nativeToolCalls: List<ToolCall> = emptyList(),
      // Whether the streaming truncator matched a configured stop string.
      // OAI-shape formats ignore this and continue to derive finish_reason from token counts.
      // Only the Anthropic format consumes it (to emit stop_reason="stop_sequence").
      stopSequenceTriggered: Boolean = false,
      // The matched stop string when stopSequenceTriggered is true. Null otherwise.
      matchedStopSequence: String? = null,
    ): List<ToolCall>
    fun buildLogResponseJson(
      combinedText: String,
      promptLen: Int,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      parsedToolCalls: List<ToolCall>,
    ): String
    fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String

    /**
     * Emit a mid-stream error in the format the client expects, then close the stream.
     *
     * Default implementation writes the OAI-shape error JSON (already produced by
     * `ResponseRenderer.renderJsonError`) followed by `data: [DONE]` — same layout
     * OAI clients expect. Anthropic's format overrides this to emit
     * `event: error\ndata: {"type":"error","error":{...}}` per the Messages API spec.
     *
     * `headerWritten` lets the format synthesize a `message_start` first when an
     * error fires before any token did — Anthropic SDKs need at least the start
     * event before they accept an error event.
     */
    suspend fun emitError(
      writer: SseWriter,
      enrichedMessage: String,
      suggestion: String?,
      kind: ErrorKind,
      oaiErrorJson: String,
      headerWritten: Boolean,
    ) {
      writer.emit("data: $oaiErrorJson\n\n")
      writer.emit(ResponseRenderer.SSE_DONE)
    }

    /**
     * Body to persist in the request log entry when the stream errors out.
     * Default returns the OAI-shape JSON the wire would have emitted; Anthropic's
     * format overrides this so the Logs tab shows a matching Anthropic envelope.
     */
    fun buildLogErrorJson(enrichedMessage: String, suggestion: String?, kind: ErrorKind, oaiErrorJson: String): String =
      oaiErrorJson
  }

  private class ResponsesApiFormat(
    private val modelName: String,
    private val now: Long,
    private val json: Json,
    private val tools: List<ToolSpec>?,
    private val hasSchemaInjection: Boolean = false,
  ) : StreamingFormat {
    private val respId = BridgeUtils.generateResponseId()
    private val msgId = BridgeUtils.generateMessageId()
    override val sourceTag = "executeStreaming_responses"
    // Buffer only when tools are present AND native tool calling is not active.
    override val bufferAllTokens = tools != null && !hasSchemaInjection
    override val stopSequences: List<String>? = null

    override suspend fun emitHeader(writer: SseWriter) {
      writer.emit(ResponseRenderer.buildStreamingHeader(modelName, respId, msgId, now))
    }
    override suspend fun emitThinkingDelta(writer: SseWriter, text: String) {
      val esc = BridgeUtils.escapeSseText(text)
      writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
    }
    override suspend fun emitContentDelta(writer: SseWriter, text: String) {
      val esc = BridgeUtils.escapeSseText(text)
      writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
    }
    override suspend fun emitThinkingClose(writer: SseWriter) {
      val esc = BridgeUtils.escapeSseText("</think>")
      writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
    }
    override suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean) {
      if (!headerWritten) {
        writer.emit(ResponseRenderer.buildStreamingHeader(modelName, respId, msgId, now))
      }
      writer.emit(ResponseRenderer.buildStreamingFooter(modelName, respId, msgId, now, ""))
      writer.finish()
    }
    override fun estimateInputTokens(prompt: String): Long = estimateTokensLongByLength(prompt.length)
    override fun estimateInputTokensInt(prompt: String): Int = estimateTokensByLength(prompt.length)
    override suspend fun emitCompletion(
      writer: SseWriter,
      fullText: String,
      fullThinking: String,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      maxTokens: Int?,
      nativeToolCalls: List<ToolCall>,
      stopSequenceTriggered: Boolean,
      matchedStopSequence: String?,
    ): List<ToolCall> {
      val parsedToolCalls = nativeToolCalls.ifEmpty {
        if (tools != null) ToolCallParser.parseAll(fullText, tools) else emptyList()
      }

      if (parsedToolCalls.isNotEmpty()) {
        writer.emit(ResponseRenderer.buildResponsesStreamToolCallEvents(
          respId, modelName, now, parsedToolCalls, promptTokens, completionTokens))
      } else {
        val combinedText = buildCombinedText(fullText, fullThinking)
        if (bufferAllTokens) {
          writer.emit(ResponseRenderer.buildStreamingHeader(modelName, respId, msgId, now))
          if (fullThinking.isNotEmpty()) {
            val thinkEsc = BridgeUtils.escapeSseText("<think>$fullThinking</think>")
            writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, thinkEsc))
          }
          if (fullText.isNotEmpty()) {
            val textEsc = BridgeUtils.escapeSseText(fullText)
            writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, textEsc))
          }
        }
        val esc = BridgeUtils.escapeSseText(combinedText)
        writer.emit(ResponseRenderer.buildStreamingFooter(
          modelName, respId, msgId, now, esc,
          inputTokens = promptTokens, outputTokens = completionTokens))
      }
      writer.finish()
      return parsedToolCalls
    }
    override fun buildLogResponseJson(
      combinedText: String,
      promptLen: Int,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      parsedToolCalls: List<ToolCall>,
    ): String {
      return if (parsedToolCalls.isNotEmpty()) {
        json.encodeToString(PayloadBuilders.responsesResponseWithToolCalls(modelName, parsedToolCalls, promptLen = promptLen))
      } else {
        json.encodeToString(PayloadBuilders.responsesResponseWithText(modelName, combinedText, promptLen = promptLen))
      }
    }
    override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String {
      if (parsedToolCalls.isEmpty()) return ""
      return " tool_calls=${parsedToolCalls.joinToString(",") { it.function.name }} count=${parsedToolCalls.size}"
    }
  }

  private class ChatCompletionsFormat(
    private val modelName: String,
    private val now: Long,
    override val stopSequences: List<String>?,
    private val tools: List<ToolSpec>?,
    private val json: Json,
    private val includeUsage: Boolean,
    private val hasSchemaInjection: Boolean = false,
  ) : StreamingFormat {
    private val chatId = BridgeUtils.generateChatCompletionId()
    override val sourceTag = "executeStreaming_chat"
    // Buffer only when tools are present AND native tool calling is not active.
    // With schema injection, the SDK handles tool calls atomically via onNativeToolCalls,
    // so text can stream progressively without risk of emitting partial tool call JSON.
    override val bufferAllTokens = tools != null && !hasSchemaInjection

    override suspend fun emitHeader(writer: SseWriter) {
      writer.emit(ResponseRenderer.buildChatStreamFirstChunk(chatId, modelName, now))
    }
    override suspend fun emitThinkingDelta(writer: SseWriter, text: String) {
      writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, text))
    }
    override suspend fun emitContentDelta(writer: SseWriter, text: String) {
      writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, text))
    }
    override suspend fun emitThinkingClose(writer: SseWriter) {
      writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, "</think>"))
    }
    override suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean) {
      if (!headerWritten) {
        writer.emit(ResponseRenderer.buildChatStreamFirstChunk(chatId, modelName, now))
      }
      writer.emit(ResponseRenderer.buildChatStreamFinalChunk(chatId, modelName, now, FinishReason.STOP))
      writer.emit(ResponseRenderer.SSE_DONE)
      writer.finish()
    }
    override fun estimateInputTokens(prompt: String): Long = estimateTokensLong(prompt)
    override fun estimateInputTokensInt(prompt: String): Int = estimateTokens(prompt)
    override suspend fun emitCompletion(
      writer: SseWriter,
      fullText: String,
      fullThinking: String,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      maxTokens: Int?,
      nativeToolCalls: List<ToolCall>,
      stopSequenceTriggered: Boolean,
      matchedStopSequence: String?,
    ): List<ToolCall> {
      val parsedToolCalls = nativeToolCalls.ifEmpty {
        if (tools != null) ToolCallParser.parseAll(fullText, tools) else emptyList()
      }
      if (parsedToolCalls.isNotEmpty()) {
        writer.emit(ResponseRenderer.buildChatStreamToolCallChunks(chatId, modelName, now, parsedToolCalls))
      } else {
        if (bufferAllTokens) {
          writer.emit(ResponseRenderer.buildChatStreamFirstChunk(chatId, modelName, now))
          if (fullThinking.isNotEmpty()) {
            writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, "<think>$fullThinking</think>"))
          }
          if (fullText.isNotEmpty()) {
            writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, fullText))
          }
        }
        val finishReason = FinishReason.infer(completionTokens, maxTokens)
        writer.emit(ResponseRenderer.buildChatStreamFinalChunk(chatId, modelName, now, finishReason))
      }
      if (includeUsage) {
        val timings = PayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
        val timingsJson = if (timings != null) json.encodeToString(timings) else null
        writer.emit(ResponseRenderer.buildChatStreamUsageChunk(chatId, modelName, now, promptTokens, completionTokens, timingsJson))
      }
      writer.emit(ResponseRenderer.SSE_DONE)
      writer.finish()
      return parsedToolCalls
    }
    override fun buildLogResponseJson(
      combinedText: String,
      promptLen: Int,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      parsedToolCalls: List<ToolCall>,
    ): String {
      val timings = PayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
      return if (parsedToolCalls.isNotEmpty()) {
        json.encodeToString(PayloadBuilders.chatResponseWithToolCalls(modelName, parsedToolCalls, promptLen = promptLen, timings = timings))
      } else {
        json.encodeToString(PayloadBuilders.chatResponseWithText(modelName, combinedText, promptLen = promptLen, timings = timings))
      }
    }
    override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String {
      if (parsedToolCalls.isEmpty()) return ""
      return " tool_calls=${parsedToolCalls.joinToString(",") { it.function.name }} count=${parsedToolCalls.size}"
    }
  }

  // ── Streaming format: /v1/completions ──────────────────────────────────

  private class CompletionsFormat(
    private val modelName: String,
    private val now: Long,
    override val stopSequences: List<String>?,
    private val json: Json,
    private val includeUsage: Boolean,
  ) : StreamingFormat {
    private val cmplId = BridgeUtils.generateCompletionId()
    override val sourceTag = "executeStreaming_completions"
    override val bufferAllTokens = false

    override suspend fun emitHeader(writer: SseWriter) {
    }
    override suspend fun emitThinkingDelta(writer: SseWriter, text: String) {
      writer.emit(ResponseRenderer.buildCompletionStreamChunk(cmplId, modelName, now, text))
    }
    override suspend fun emitContentDelta(writer: SseWriter, text: String) {
      writer.emit(ResponseRenderer.buildCompletionStreamChunk(cmplId, modelName, now, text))
    }
    override suspend fun emitThinkingClose(writer: SseWriter) {
      writer.emit(ResponseRenderer.buildCompletionStreamChunk(cmplId, modelName, now, "</think>"))
    }
    override suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean) {
      writer.emit(ResponseRenderer.buildCompletionStreamFinalChunk(cmplId, modelName, now, FinishReason.STOP))
      writer.emit(ResponseRenderer.SSE_DONE)
      writer.finish()
    }
    override fun estimateInputTokens(prompt: String): Long = estimateTokensLongByLength(prompt.length)
    override fun estimateInputTokensInt(prompt: String): Int = estimateTokensByLength(prompt.length)
    override suspend fun emitCompletion(
      writer: SseWriter,
      fullText: String,
      fullThinking: String,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      maxTokens: Int?,
      nativeToolCalls: List<ToolCall>,
      stopSequenceTriggered: Boolean,
      matchedStopSequence: String?,
    ): List<ToolCall> {
      val finishReason = FinishReason.infer(completionTokens, maxTokens)
      writer.emit(ResponseRenderer.buildCompletionStreamFinalChunk(cmplId, modelName, now, finishReason))
      if (includeUsage) {
        val timings = PayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
        val timingsJson = if (timings != null) json.encodeToString(timings) else null
        writer.emit(ResponseRenderer.buildCompletionStreamUsageChunk(cmplId, modelName, now, promptTokens, completionTokens, timingsJson))
      }
      writer.emit(ResponseRenderer.SSE_DONE)
      writer.finish()
      return emptyList()
    }
    override fun buildLogResponseJson(
      combinedText: String,
      promptLen: Int,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      parsedToolCalls: List<ToolCall>,
    ): String {
      val timings = PayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
      return json.encodeToString(CompletionResponse(
        id = cmplId,
        created = now,
        model = modelName,
        choices = listOf(CompletionChoice(text = combinedText, index = 0, finish_reason = FinishReason.infer(completionTokens, null))),
        usage = Usage(promptTokens, completionTokens),
        timings = timings,
      ))
    }
    override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String = ""
  }

  // ── Streaming format: /v1/messages (Anthropic) ─────────────────────────

  /**
   * Anthropic Messages SSE event sequence:
   *   message_start → [content_block_start/delta/stop]+ → message_delta → message_stop
   *
   * Block indexing is lazy: [currentBlockIndex] starts at -1 and advances only when
   * a block is actually opened. Thinking blocks come first when present, then exactly
   * one text block (per Anthropic spec, all assistant text aggregates into a single
   * block), then one tool_use block per tool call when buffered tool emission fires.
   */
  private class AnthropicMessagesFormat(
    private val modelName: String,
    private val requestModelId: String,
    override val stopSequences: List<String>?,
    private val tools: List<ToolSpec>?,
    private val hasSchemaInjection: Boolean,
    // Verbose-debug only: when true, log SSE event counter + first-event timing so
    // disconnect-vs-stall cases can be distinguished post-hoc. Cheap (one Log.i per
    // event), but still gated to avoid spamming logcat in normal use.
    private val verboseDebug: Boolean = false,
  ) : StreamingFormat {
    private val msgId = "msg_${java.util.UUID.randomUUID().toString().replace("-", "").take(24)}"
    override val sourceTag = "executeStreaming_messages"
    // Same buffering rule as ChatCompletions: tools without schema injection require
    // the full text to parse tool calls before any output is emitted.
    override val bufferAllTokens = tools != null && !hasSchemaInjection

    private var currentBlockIndex = -1
    private var currentBlockOpen = false
    private var currentBlockKind: String? = null  // "thinking" | "text" | "tool_use"

    private var sseEventCount = 0
    private var firstEmitNanos = 0L
    private val createdNanos = System.nanoTime()

    private suspend fun emitSse(writer: SseWriter, eventName: String, payload: String) {
      writer.emit(ResponseRenderer.emitSseEvent(eventName, payload))
      sseEventCount += 1
      if (verboseDebug && firstEmitNanos == 0L) {
        firstEmitNanos = System.nanoTime()
        val ms = (firstEmitNanos - createdNanos) / 1_000_000
        Log.i(TAG, "ANTHROPIC_SSE first_emit msgId=$msgId firstEventMs=$ms event=$eventName")
      }
    }

    override val emitsHeaderEarly: Boolean = true

    override suspend fun emitHeader(writer: SseWriter) {
      val escapedModel = BridgeUtils.escapeSseText(requestModelId)
      val payload =
        """{"type":"message_start","message":{"id":"$msgId","type":"message","role":"assistant","model":"$escapedModel","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":0,"output_tokens":0}}}"""
      emitSse(writer, "message_start", payload)
    }

    override suspend fun emitPing(writer: SseWriter) {
      // Spec: https://docs.anthropic.com/en/api/messages-streaming#ping-events
      emitSse(writer, "ping", """{"type":"ping"}""")
    }

    private suspend fun openBlockIfNeeded(writer: SseWriter, kind: String) {
      if (currentBlockOpen && currentBlockKind == kind) return
      if (currentBlockOpen) closeCurrentBlock(writer)
      currentBlockIndex += 1
      currentBlockOpen = true
      currentBlockKind = kind
      val blockJson = when (kind) {
        "thinking" -> """{"type":"thinking","thinking":""}"""
        "text" -> """{"type":"text","text":""}"""
        else -> """{"type":"$kind"}"""
      }
      val payload = """{"type":"content_block_start","index":$currentBlockIndex,"content_block":$blockJson}"""
      emitSse(writer, "content_block_start", payload)
    }

    private suspend fun closeCurrentBlock(writer: SseWriter) {
      if (!currentBlockOpen) return
      val payload = """{"type":"content_block_stop","index":$currentBlockIndex}"""
      emitSse(writer, "content_block_stop", payload)
      currentBlockOpen = false
      currentBlockKind = null
    }

    override suspend fun emitThinkingDelta(writer: SseWriter, text: String) {
      // Strip the literal <think>...</think> wrappers that the OAI streaming path injects;
      // Anthropic clients want the raw thinking text in a typed thinking block.
      val cleaned = text.removePrefix("<think>")
      if (cleaned.isEmpty()) return
      openBlockIfNeeded(writer, "thinking")
      val esc = BridgeUtils.escapeSseText(cleaned)
      val payload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"thinking_delta","thinking":"$esc"}}"""
      emitSse(writer, "content_block_delta", payload)
    }

    override suspend fun emitContentDelta(writer: SseWriter, text: String) {
      // Strip the </think> close tag the OAI path emits at the thinking→text boundary.
      val cleaned = text.removePrefix("</think>")
      if (cleaned.isEmpty()) return
      openBlockIfNeeded(writer, "text")
      val esc = BridgeUtils.escapeSseText(cleaned)
      val payload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"text_delta","text":"$esc"}}"""
      emitSse(writer, "content_block_delta", payload)
    }

    override suspend fun emitThinkingClose(writer: SseWriter) {
      // No-op: openBlockIfNeeded("text") will close the thinking block on the next
      // content delta. Keeping this idempotent matches the OAI format's behavior.
      if (currentBlockOpen && currentBlockKind == "thinking") closeCurrentBlock(writer)
    }

    override suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean) {
      if (!headerWritten) emitHeader(writer)
      if (currentBlockOpen) closeCurrentBlock(writer)
      val delta = """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"input_tokens":0,"output_tokens":0}}"""
      emitSse(writer, "message_delta", delta)
      emitSse(writer, "message_stop", """{"type":"message_stop"}""")
      if (verboseDebug) {
        val totalMs = (System.nanoTime() - createdNanos) / 1_000_000
        Log.i(TAG, "ANTHROPIC_SSE cancellation msgId=$msgId totalEvents=$sseEventCount totalMs=$totalMs")
      }
      writer.finish()
    }

    /**
     * Emit an Anthropic-shaped mid-stream error event. The Anthropic spec defines:
     *
     *   event: error
     *   data: {"type":"error","error":{"type":"<api_type>","message":"<text>"}}
     *
     * No `[DONE]` sentinel and no `message_stop` after — the SDK closes the stream
     * on `event: error`. The server's per-kind suggestion (e.g. "Increase the
     * Chat Completions timeout in Settings → Advanced") is appended into the
     * message string because the Anthropic schema has no separate suggestion field.
     */
    override suspend fun emitError(
      writer: SseWriter,
      enrichedMessage: String,
      suggestion: String?,
      kind: ErrorKind,
      oaiErrorJson: String,
      headerWritten: Boolean,
    ) {
      if (!headerWritten) {
        // Some Anthropic SDKs require message_start before any other event.
        try { emitHeader(writer) } catch (_: Exception) { /* writer may be closed */ }
      }
      if (currentBlockOpen) {
        try { closeCurrentBlock(writer) } catch (_: Exception) { /* same */ }
      }
      val anthropicErrorType = mapErrorKindToAnthropicType(kind)
      // enrichedMessage already contains the suggestion appended by enrichLlmError
      // ("$error — $suggestion"), so do NOT append it again here.
      val payload = """{"type":"error","error":{"type":"${BridgeUtils.escapeSseText(anthropicErrorType)}","message":"${BridgeUtils.escapeSseText(enrichedMessage)}"}}"""
      emitSse(writer, "error", payload)
      if (verboseDebug) {
        val totalMs = (System.nanoTime() - createdNanos) / 1_000_000
        Log.i(TAG, "ANTHROPIC_SSE error msgId=$msgId errorType=$anthropicErrorType totalEvents=$sseEventCount totalMs=$totalMs")
      }
    }

    override fun buildLogErrorJson(enrichedMessage: String, suggestion: String?, kind: ErrorKind, oaiErrorJson: String): String {
      val anthropicErrorType = mapErrorKindToAnthropicType(kind)
      // enrichedMessage already includes the suggestion via enrichLlmError.
      return ResponseRenderer.renderAnthropicError(anthropicErrorType, enrichedMessage)
    }

    private fun mapErrorKindToAnthropicType(kind: ErrorKind): String = when (kind) {
      ErrorKind.CONTEXT_OVERFLOW -> "invalid_request_error"
      ErrorKind.TIMEOUT -> "api_error"
      ErrorKind.MODEL_NOT_FOUND -> "not_found_error"
      ErrorKind.MODEL_FILES_MISSING -> "not_found_error"
      ErrorKind.MODEL_INSTANCE_NULL -> "overloaded_error"
      ErrorKind.OOM -> "overloaded_error"
      ErrorKind.PORT_BIND_FAILURE -> "api_error"
      ErrorKind.IMAGE_DECODE_FAILED -> "invalid_request_error"
      else -> "api_error"
    }

    override fun estimateInputTokens(prompt: String): Long = estimateTokensLong(prompt)
    override fun estimateInputTokensInt(prompt: String): Int = estimateTokens(prompt)

    override suspend fun emitCompletion(
      writer: SseWriter,
      fullText: String,
      fullThinking: String,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      maxTokens: Int?,
      nativeToolCalls: List<ToolCall>,
      stopSequenceTriggered: Boolean,
      matchedStopSequence: String?,
    ): List<ToolCall> {
      val parsedToolCalls = nativeToolCalls.ifEmpty {
        if (tools != null) ToolCallParser.parseAll(fullText, tools) else emptyList()
      }

      // Buffered path: open/close blocks synthetically so the client sees a valid
      // event sequence even though no progressive deltas were emitted.
      if (bufferAllTokens) {
        if (fullThinking.isNotEmpty()) {
          openBlockIfNeeded(writer, "thinking")
          val esc = BridgeUtils.escapeSseText(fullThinking)
          val payload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"thinking_delta","thinking":"$esc"}}"""
          emitSse(writer, "content_block_delta", payload)
          closeCurrentBlock(writer)
        }
        if (fullText.isNotEmpty()) {
          openBlockIfNeeded(writer, "text")
          val esc = BridgeUtils.escapeSseText(fullText)
          val payload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"text_delta","text":"$esc"}}"""
          emitSse(writer, "content_block_delta", payload)
          closeCurrentBlock(writer)
        }
      } else {
        // Progressive path: close whichever block is still open from the last delta.
        if (currentBlockOpen) closeCurrentBlock(writer)
      }

      // Tool blocks emitted last — one block per call. Each block carries the id
      // and name in content_block_start; the JSON arguments arrive as a single
      // input_json_delta because the runtime emits tool calls atomically (no
      // partial_json streaming today).
      if (parsedToolCalls.isNotEmpty()) {
        if (currentBlockOpen) closeCurrentBlock(writer)
        for (call in parsedToolCalls) {
          currentBlockIndex += 1
          currentBlockOpen = true
          currentBlockKind = "tool_use"
          val startPayload = buildString {
            append("""{"type":"content_block_start","index":""")
            append(currentBlockIndex)
            append(""","content_block":{"type":"tool_use","id":"""")
            append(BridgeUtils.escapeSseText(call.id))
            append("""",""")
            append(""""name":"""")
            append(BridgeUtils.escapeSseText(call.function.name))
            append("""",""")
            append(""""input":{}}}""")
          }
          emitSse(writer, "content_block_start", startPayload)
          val argsEsc = BridgeUtils.escapeSseText(call.function.arguments.ifBlank { "{}" })
          val deltaPayload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"input_json_delta","partial_json":"$argsEsc"}}"""
          emitSse(writer, "content_block_delta", deltaPayload)
          closeCurrentBlock(writer)
        }
      }

      // Final message_delta + message_stop.
      val stopReason = when {
        stopSequenceTriggered -> "stop_sequence"
        parsedToolCalls.isNotEmpty() -> "tool_use"
        FinishReason.infer(completionTokens, maxTokens) == FinishReason.LENGTH -> "max_tokens"
        else -> "end_turn"
      }
      val stopSequenceField = if (stopReason == "stop_sequence" && matchedStopSequence != null) {
        "\"" + BridgeUtils.escapeSseText(matchedStopSequence) + "\""
      } else "null"
      val deltaPayload = """{"type":"message_delta","delta":{"stop_reason":"$stopReason","stop_sequence":$stopSequenceField},"usage":{"input_tokens":$promptTokens,"output_tokens":$completionTokens}}"""
      emitSse(writer, "message_delta", deltaPayload)
      emitSse(writer, "message_stop", """{"type":"message_stop"}""")
      if (verboseDebug) {
        val totalMs = (System.nanoTime() - createdNanos) / 1_000_000
        Log.i(TAG, "ANTHROPIC_SSE complete msgId=$msgId stopReason=$stopReason promptTokens=$promptTokens completionTokens=$completionTokens totalEvents=$sseEventCount totalMs=$totalMs")
      }
      writer.finish()
      return parsedToolCalls
    }

    override fun buildLogResponseJson(
      combinedText: String,
      promptLen: Int,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
      parsedToolCalls: List<ToolCall>,
    ): String {
      // Logs render the Anthropic-shaped response shell so the Logs tab shows a
      // coherent body. The matched stop sequence is unknown here (the streaming
      // session's matchedStopSequence is not threaded into buildLogResponseJson),
      // so we emit `null` — the wire response set the field correctly when needed.
      val (thinking, visibleText) = if (combinedText.startsWith("<think>")) {
        val close = combinedText.indexOf("</think>")
        if (close >= 0) {
          combinedText.substring("<think>".length, close) to combinedText.substring(close + "</think>".length)
        } else "" to combinedText
      } else "" to combinedText

      val contentBuilder = StringBuilder()
      if (thinking.isNotEmpty()) {
        contentBuilder.append("""{"type":"thinking","thinking":"""")
        contentBuilder.append(BridgeUtils.escapeSseText(thinking))
        contentBuilder.append(""""},""")
      }
      contentBuilder.append("""{"type":"text","text":"""")
      contentBuilder.append(BridgeUtils.escapeSseText(visibleText))
      contentBuilder.append(""""}""")
      for (call in parsedToolCalls) {
        contentBuilder.append(""",{"type":"tool_use","id":"""")
        contentBuilder.append(BridgeUtils.escapeSseText(call.id))
        contentBuilder.append("""",""")
        contentBuilder.append(""""name":"""")
        contentBuilder.append(BridgeUtils.escapeSseText(call.function.name))
        contentBuilder.append(""""}""")
      }

      val stopReason = when {
        parsedToolCalls.isNotEmpty() -> "tool_use"
        else -> "end_turn"
      }
      return """{"id":"$msgId","type":"message","role":"assistant","model":"${BridgeUtils.escapeSseText(requestModelId)}","content":[$contentBuilder],"stop_reason":"$stopReason","stop_sequence":null,"usage":{"input_tokens":$promptTokens,"output_tokens":$completionTokens}}"""
    }

    override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String {
      if (parsedToolCalls.isEmpty()) return ""
      return " tool_calls=${parsedToolCalls.joinToString(",") { it.function.name }} count=${parsedToolCalls.size}"
    }
  }

  // ── Streaming state management ──────────────────────────────────────────

  private inner class StreamState(
    val model: Model,
    val requestId: String,
    val endpoint: String,
    val logId: String?,
    val streamStartMs: Long,
    val keepPartial: Boolean,
    val inferenceControl: AtomicReference<InferenceGateway.InferenceControl?>,
    val onConversationFinished: (Boolean, String?) -> Unit,
  ) {
    val fullText = StringBuilder()
    val fullThinking = StringBuilder()
    var headerWritten = false
    var thinkingTagOpened = false
    var lastLogUpdateMs = 0L
    var firstTokenMs = 0L
    private val inferenceStartedFlag = AtomicBoolean(false)
    val inferenceStarted: Boolean get() = inferenceStartedFlag.get()
    var inferenceCompleted = false
    // True once ServerMetrics.onInferenceCompleted has been called for this request.
    // Tracked separately from inferenceCompleted because the metric decrement and the
    // local "we are done emitting" flag have different lifetimes — the metric pairs
    // with onInferenceStarted and must fire at most once even if both the gateway
    // callback and the safety-net finally try to clear it.
    private val metricsCompleted = AtomicBoolean(false)
    private var conversationFinished = false
    var stopSequenceTriggered = false
    // The actual stop string that matched, set in lock-step with stopSequenceTriggered.
    // Anthropic /v1/messages echoes this back in the response `stop_sequence` field;
    // OAI-shape formats ignore it.
    var matchedStopSequence: String? = null

    fun markStarted() {
      if (inferenceStartedFlag.compareAndSet(false, true)) {
        ServerMetrics.onInferenceStarted()
      }
    }

    fun markCompleted() {
      inferenceCompleted = true
    }

    /**
     * Idempotently decrement the inferring counter. Called from the gateway's
     * onInferenceFinished callback in the normal path, and from the streamInference
     * finally block as a safety net. Without the idempotent guard, an exception
     * that bypasses onInferenceFinished would leak the counter and pin the
     * "processing" pill on indefinitely.
     */
    fun markMetricsCompleted() {
      if (inferenceStarted && metricsCompleted.compareAndSet(false, true)) {
        ServerMetrics.onInferenceCompleted()
      }
    }

    fun finishConversation(isReusable: Boolean, assistantText: String? = null) {
      if (conversationFinished) return
      conversationFinished = true
      onConversationFinished(isReusable, assistantText)
    }

    fun buildCancelledPartial(): String? {
      if (!keepPartial || (fullText.isEmpty() && fullThinking.isEmpty())) return null
      return buildString {
        if (fullThinking.isNotEmpty()) {
          append("<think>"); append(fullThinking); append("</think>")
        }
        append(fullText)
      }
    }

    fun logCancellation() {
      if (logId != null) {
        RequestLogStore.unregisterCancellation(logId)
        RequestLogStore.update(logId) {
          it.copy(
            partialText = buildCancelledPartial(),
            isPending = false,
            isCancelled = true,
            statusCode = 499,
            latencyMs = SystemClock.elapsedRealtime() - streamStartMs,
          )
        }
      }
      logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=true outputChars=${fullText.length}")
    }

    fun elapsedMs(): Long = SystemClock.elapsedRealtime() - streamStartMs

    private fun checkStopSequence(stopSequences: List<String>?) {
      if (stopSequences.isNullOrEmpty() || stopSequenceTriggered) return
      val currentText = fullText.toString()
      var earliest = currentText.length
      var matched: String? = null
      for (stop in stopSequences) {
        val idx = currentText.indexOf(stop)
        if (idx in 0 until earliest) {
          earliest = idx
          matched = stop
        }
      }
      if (earliest < currentText.length) {
        fullText.clear()
        fullText.append(currentText.substring(0, earliest))
        stopSequenceTriggered = true
        matchedStopSequence = matched
        // The protocol response is successful, but native generation and its partial
        // Conversation must settle before another request can use the model.
        inferenceControl.get()?.stopSuccessfully()
      }
    }

    private suspend fun emitThinkingContent(
      thought: String?,
      format: StreamingFormat,
      writer: SseWriter,
    ) {
      if (thought.isNullOrEmpty()) return
      fullThinking.append(thought)
      if (!format.bufferAllTokens) {
        val thinkText = if (!thinkingTagOpened) {
          thinkingTagOpened = true
          "<think>$thought"
        } else {
          thought
        }
        format.emitThinkingDelta(writer, thinkText)
      }
    }

    private suspend fun emitContentToken(
      partial: String,
      format: StreamingFormat,
      writer: SseWriter,
    ) {
      if (partial.isEmpty() || stopSequenceTriggered) return
      fullText.append(partial)
      checkStopSequence(format.stopSequences)
      if (!format.bufferAllTokens && !stopSequenceTriggered) {
        val text = if (thinkingTagOpened) {
          thinkingTagOpened = false
          "</think>$partial"
        } else {
          partial
        }
        format.emitContentDelta(writer, text)
      }
    }

    private fun updateStreamPreview(streamPreview: Boolean) {
      if (!streamPreview || logId == null) return
      val nowMs = SystemClock.elapsedRealtime()
      if (nowMs - lastLogUpdateMs < LOG_STREAMING_PREVIEW_DEBOUNCE_MS) return
      lastLogUpdateMs = nowMs
      val previewText = try {
        buildString {
          if (fullThinking.isNotEmpty()) {
            append("<think>")
            append(fullThinking)
            if (!thinkingTagOpened) append("</think>")
          }
          append(fullText)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error building thinking preview: ${e.message}", e)
        fullText.toString()
      }
      RequestLogStore.updatePartialText(logId, previewText)
    }

    suspend fun handleToken(
      event: StreamEvent.Token,
      format: StreamingFormat,
      writer: SseWriter,
      prompt: String,
      configSnapshot: Map<String, Any>?,
      prefs: RequestPrefsSnapshot?,
      streamPreview: Boolean,
      channel: Channel<StreamEvent>,
      capturedNativeToolCalls: AtomicReference<List<com.google.ai.edge.litertlm.ToolCall>?>,
    ) {
      if (firstTokenMs == 0L && (event.partial.isNotEmpty() || !event.thought.isNullOrEmpty())) {
        firstTokenMs = SystemClock.elapsedRealtime()
      }
      if (!format.bufferAllTokens && !headerWritten) {
        headerWritten = true
        format.emitHeader(writer)
      }
      emitThinkingContent(event.thought, format, writer)
      emitContentToken(event.partial, format, writer)

      if (!event.done && !stopSequenceTriggered) {
        updateStreamPreview(streamPreview)
        return
      }

      // ── Completion path ──
      if (logId != null) RequestLogStore.unregisterCancellation(logId)
      val outputLen = fullText.length
      val inputTokens = format.estimateInputTokens(prompt)
      val outputTokens = estimateTokensLongByLength(outputLen)
      val totalLatencyMs = elapsedMs()
      val ttfbMs = if (firstTokenMs > 0) firstTokenMs - streamStartMs else 0L
      val maxCtx = model.configValues.maxTokensLong() ?: 0L
      ServerMetrics.addTokens(outputTokens)
      ServerMetrics.recordLatency(totalLatencyMs)
      ServerMetrics.recordTtfb(ttfbMs)
      if (firstTokenMs > 0) {
        ServerMetrics.recordInferenceMetrics(inputTokens, outputTokens, ttfbMs, totalLatencyMs - ttfbMs, maxCtx)
      }
      emitDebugInferenceLog(inputTokens, outputTokens, ttfbMs, totalLatencyMs - ttfbMs, totalLatencyMs, model.name, prefs)
      markCompleted()
      val promptTokens = format.estimateInputTokensInt(prompt)
      val completionTokens = estimateTokensByLength(outputLen)

      if (!format.bufferAllTokens && thinkingTagOpened) {
        thinkingTagOpened = false
        format.emitThinkingClose(writer)
      }

      val effectiveMaxTokens = (configSnapshot ?: model.configValues).maxTokensInt()
      val nativeCalls = capturedNativeToolCalls.get()
      val convertedNativeCalls = if (nativeCalls != null && nativeCalls.isNotEmpty()) {
        SchemaInjectionBridge.convertNativeToolCalls(nativeCalls)
      } else emptyList()
      val parsedToolCalls = format.emitCompletion(writer, fullText.toString(), fullThinking.toString(), promptTokens, completionTokens, ttfbMs, totalLatencyMs, effectiveMaxTokens, convertedNativeCalls, stopSequenceTriggered, matchedStopSequence)

      if (logId != null) {
        val combinedText = buildCombinedText(fullText, fullThinking)
        val responseJson = format.buildLogResponseJson(combinedText, prompt.length, promptTokens, completionTokens, ttfbMs, totalLatencyMs, parsedToolCalls)
        val generationMs = totalLatencyMs - ttfbMs
        val reqDecodeSpeed = if (outputTokens > 0 && generationMs > 0) outputTokens.toDouble() / (generationMs / 1000.0) else 0.0
        val reqPrefillSpeed = if (inputTokens > 0 && ttfbMs > 0) inputTokens.toDouble() / (ttfbMs / 1000.0) else 0.0
        val reqItlMs = if (outputTokens > 1 && generationMs > 0) generationMs.toDouble() / (outputTokens - 1) else 0.0
        RequestLogStore.update(logId) {
          it.copy(
            responseBody = responseJson,
            partialText = null,
            isPending = false,
            latencyMs = totalLatencyMs,
            isThinking = ServerMetrics.thinkingEnabled.value,
            hasToolCalls = parsedToolCalls.isNotEmpty(),
            ttfbMs = ttfbMs,
            decodeSpeed = reqDecodeSpeed,
            prefillSpeed = reqPrefillSpeed,
            itlMs = reqItlMs,
          )
        }
      }
      logEvent("request_done id=$requestId endpoint=$endpoint streaming=true totalMs=$totalLatencyMs ttfbMs=$ttfbMs outputChars=$outputLen${format.buildLogEventSuffix(parsedToolCalls)}")
      finishConversation(
        isReusable = !stopSequenceTriggered,
        assistantText = if (format is AnthropicMessagesFormat) {
          fullText.toString()
        } else {
          buildCombinedText(fullText, fullThinking)
        },
      )
      channel.close()
    }

    suspend fun handleError(
      error: String,
      writer: SseWriter,
      channel: Channel<StreamEvent>,
      format: StreamingFormat,
    ) {
      if (logId != null) RequestLogStore.unregisterCancellation(logId)
      markCompleted()
      val (enrichedError, kind) = enrichLlmError(error, context)
      ServerMetrics.incrementErrorCount(kind.category)
      logEvent("request_error id=$requestId endpoint=$endpoint error=${error.take(200)} streaming=true")
      val suggestion = ErrorSuggestions.suggest(kind, context)
      val oaiErrorJson = ResponseRenderer.renderJsonError(enrichedError, suggestion, kind)
      val logErrorJson = format.buildLogErrorJson(enrichedError, suggestion, kind, oaiErrorJson)
      if (logId != null) {
        val actualTokens = extractActualTokenCounts(error)
        RequestLogStore.update(logId) {
          it.copy(
            partialText = null,
            responseBody = logErrorJson,
            isPending = false,
            latencyMs = elapsedMs(),
            level = LogLevel.ERROR,
            errorKind = kind,
            inputTokenEstimate = actualTokens?.first ?: it.inputTokenEstimate,
            maxContextTokens = actualTokens?.second ?: it.maxContextTokens,
            isExactTokenCount = actualTokens != null || it.isExactTokenCount,
          )
        }
      }
      try {
        format.emitError(writer, enrichedError, suggestion, kind, oaiErrorJson, headerWritten)
        writer.finish()
      } catch (e: Exception) { Log.w(TAG, "writer.finish() failed during cleanup", e) }
      channel.close()
    }
  }

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
        model,
        requestId,
        endpoint,
        logId,
        streamStartMs,
        keepPartial,
        inferenceControl,
        onConversationFinished,
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
      if (fullThinking.isNotEmpty()) "<think>${fullThinking}</think>${fullText}" else fullText.toString()

    // Parses "N >= M" from LiteRT native overflow errors (N=input tokens, M=context limit)
    private val TOKEN_OVERFLOW_REGEX = Regex("(\\d+)\\s*>=\\s*(\\d+)")

    /**
     * Truncates model output at the first occurrence of any stop sequence.
     * Returns (truncated text, was truncation applied, the stop string that matched
     * — null when nothing matched). The matched string is needed by the Anthropic
     * /v1/messages response, which echoes it back in the `stop_sequence` field.
     */
    fun applyStopSequences(text: String, stopSequences: List<String>?): Triple<String, Boolean, String?> {
      if (stopSequences.isNullOrEmpty()) return Triple(text, false, null)
      var earliest = text.length
      var matched: String? = null
      for (stop in stopSequences) {
        val idx = text.indexOf(stop)
        if (idx in 0 until earliest) {
          earliest = idx
          matched = stop
        }
      }
      return if (earliest < text.length) Triple(text.substring(0, earliest), true, matched)
      else Triple(text, false, null)
    }

    /**
     * Injects a JSON mode instruction into the prompt when response_format is requested.
     */
    fun applyResponseFormat(prompt: String, responseFormat: ResponseFormat?): String {
      if (responseFormat == null || responseFormat.type == "text") return prompt
      val instruction = when (responseFormat.type) {
        "json_object" -> "Respond with valid JSON only. Do not include any text, explanation, or markdown outside the JSON object.\n\n"
        "json_schema" -> "Respond with valid JSON only. Output only the JSON object, nothing else.\n\n"
        else -> return prompt
      }
      return instruction + prompt
    }

    /**
     * Classify an opaque LLM error string and return the enriched message with a
     * recovery suggestion appended (if one is available for the classified error kind).
     *
     * Also returns the [ErrorKind] so callers can use it for metrics and API responses.
     */
    fun enrichLlmError(error: String, context: Context): Pair<String, ErrorKind> {
      val kind = ErrorSuggestions.classifyFromString(error)
      val suggestion = ErrorSuggestions.suggest(kind, context)
      val enriched = if (suggestion != null) "$error — $suggestion" else error
      return enriched to kind
    }

    /**
     * Extract actual token counts from LiteRT error messages.
     * LiteRT reports context overflow as "N >= M" (e.g. "6579 >= 4000").
     * Returns (actualInputTokens, maxContextTokens) or null if not a context overflow error.
     */
    fun extractActualTokenCounts(responseBody: String): Pair<Long, Long>? {
      // Pattern: "6579 >= 4000" — actual input tokens exceeding max context
      val match = TOKEN_OVERFLOW_REGEX.find(responseBody) ?: return null
      val actual = match.groupValues[1].toLongOrNull() ?: return null
      val max = match.groupValues[2].toLongOrNull() ?: return null
      if (actual <= 0 || max <= 0) return null
      return actual to max
    }
  }
}
