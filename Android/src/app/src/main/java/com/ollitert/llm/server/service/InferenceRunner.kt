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
import com.ollitert.llm.server.data.LOG_STREAMING_PREVIEW_DEBOUNCE_MS
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RESPONSES_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.WARMUP_MESSAGE
import com.ollitert.llm.server.data.WARMUP_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.llmSupportAudio
import com.ollitert.llm.server.data.llmSupportImage
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.data.isThinkingEnabled
import com.ollitert.llm.server.data.maxTokensInt
import com.ollitert.llm.server.data.maxTokensLong
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
  suspend fun runLlm(
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
  ): Pair<String?, String?> {
    // Track input tokens (rough estimate: ~4 chars per token)
    ServerMetrics.addTokensIn(estimateTokensLong(prompt))
    // Track request modality
    ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = audioClips.isNotEmpty())

    val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVisionInit)
    val supportAudio = model.llmSupportAudio

    val userCancelFlag = AtomicBoolean(false)
    val inferenceActuallyStarted = AtomicBoolean(false)
    val lifecycleLatchRef = AtomicReference<java.util.concurrent.CountDownLatch?>(null)
    // Register cancel callback before any lock acquisition so queued requests are cancellable.
    if (logId != null) {
      RequestLogStore.registerCancellation(logId) {
        userCancelFlag.set(true)
        if (inferenceActuallyStarted.get()) ServerLlmModelHelper.stopResponse(model)
        lifecycleLatchRef.get()?.countDown()
      }
    }

    val enableThinking = model.isThinkingEnabled
    val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

    // Captured inside the resetConversation lambda (which runs under inferenceLock) so
    // that concurrent updateConfigValues() writes are visible before we snapshot.
    var originalConfig: Map<String, Any>? = null
    val capturedNativeToolCalls = AtomicReference<List<com.google.ai.edge.litertlm.ToolCall>?>(null)

    val result = InferenceGateway.execute(
      prompt = prompt,
      timeoutSeconds = timeoutSeconds,
      executor = executor,
      inferenceLock = inferenceLock,
      resetConversation = {
        // Skip inference entirely if cancelled while queued.
        if (userCancelFlag.get()) throw java.util.concurrent.CancellationException("cancelled_while_queued")
        val initErr = reinitIfNeeded(model, supportImage, supportAudio)
        if (initErr != null) throw RuntimeException("model_init_failed: $initErr")
        inferenceActuallyStarted.set(true)
        ServerMetrics.onInferenceStarted()
        if (logId != null) RequestLogStore.update(logId) { it.copy(isGenerating = true) }
        if (configSnapshot != null) {
          originalConfig = model.configValues
          model.configValues = configSnapshot
        }
        ServerLlmModelHelper.resetConversation(
          model,
          supportImage = supportImage,
          supportAudio = supportAudio,
          systemInstruction = buildSystemInstruction(model.prefsKey),
          tools = schemaInjectionProviders,
          initialMessages = schemaInjectionMessages,
        )
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
          onNativeToolCalls = if (schemaInjectionProviders.isNotEmpty()) { calls ->
            capturedNativeToolCalls.set(calls)
          } else null,
        )
      },
      cancelInference = { ServerLlmModelHelper.stopResponse(model) },
      onInferenceFinished = {
        if (originalConfig != null && model.instance != null) {
          model.configValues = originalConfig
        }
        if (inferenceActuallyStarted.get()) ServerMetrics.onInferenceCompleted()
      },
      elapsedMs = { SystemClock.elapsedRealtime() },
      onCaughtThrowable = { t -> emitDebugStackTrace(t, "execute", model.name) },
      earlyUnblock = { latch -> lifecycleLatchRef.set(latch) },
    )
    if (logId != null) RequestLogStore.unregisterCancellation(logId)

    val nativeCalls = capturedNativeToolCalls.get()
    if (nativeCalls != null && nativeCalls.isNotEmpty() && onNativeToolCalls != null) {
      onNativeToolCalls(SchemaInjectionBridge.convertNativeToolCalls(nativeCalls))
    }

    if (result.error == "client_disconnected") {
      return handleCancellation(result, logId, requestId, endpoint, prefs, logSuffix = "client_disconnected=true", returnMessage = "Client disconnected")
    }

    if (userCancelFlag.get()) {
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
    val bufferAllTokens: Boolean
    val stopSequences: List<String>?

    suspend fun emitHeader(writer: SseWriter)
    suspend fun emitThinkingDelta(writer: SseWriter, text: String)
    suspend fun emitContentDelta(writer: SseWriter, text: String)
    suspend fun emitThinkingClose(writer: SseWriter)
    suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean)
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
  }

  private class ResponsesApiFormat(
    private val modelName: String,
    private val now: Long,
    private val json: Json,
    private val tools: List<ToolSpec>?,
  ) : StreamingFormat {
    private val respId = BridgeUtils.generateResponseId()
    private val msgId = BridgeUtils.generateMessageId()
    override val sourceTag = "executeStreaming_responses"
    // When tools are present, buffer all tokens so tool calls can be parsed atomically
    // before emitting any SSE events. Streaming partial tool call JSON would be invalid.
    override val bufferAllTokens = tools != null
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
    ): List<ToolCall> {
      val parsedToolCalls = nativeToolCalls.ifEmpty {
        if (tools != null) ToolCallParser.parseAll(fullText, tools) else emptyList()
      }

      if (bufferAllTokens && parsedToolCalls.isNotEmpty()) {
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
  ) : StreamingFormat {
    private val chatId = BridgeUtils.generateChatCompletionId()
    override val sourceTag = "executeStreaming_chat"
    // Buffer all tokens when tools are present — tool calls must be parsed atomically.
    override val bufferAllTokens = tools != null

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
    ): List<ToolCall> {
      val parsedToolCalls = nativeToolCalls.ifEmpty {
        if (tools != null) ToolCallParser.parseAll(fullText, tools) else emptyList()
      }
      if (bufferAllTokens && parsedToolCalls.isNotEmpty()) {
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

  // ── Streaming state management ──────────────────────────────────────────

  private inner class StreamState(
    val model: Model,
    val requestId: String,
    val endpoint: String,
    val logId: String?,
    val streamStartMs: Long,
    val keepPartial: Boolean,
  ) {
    val fullText = StringBuilder()
    val fullThinking = StringBuilder()
    var headerWritten = false
    var thinkingTagOpened = false
    var lastLogUpdateMs = 0L
    var firstTokenMs = 0L
    var inferenceStarted = false
    var inferenceCompleted = false
    var stopSequenceTriggered = false

    fun markStarted() {
      if (!inferenceStarted) {
        inferenceStarted = true
        ServerMetrics.onInferenceStarted()
      }
    }

    fun markCompleted() {
      inferenceCompleted = true
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
      for (stop in stopSequences) {
        val idx = currentText.indexOf(stop)
        if (idx in 0 until earliest) earliest = idx
      }
      if (earliest < currentText.length) {
        fullText.clear()
        fullText.append(currentText.substring(0, earliest))
        stopSequenceTriggered = true
        ServerLlmModelHelper.stopResponse(model)
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

      if (!event.done) {
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
      val parsedToolCalls = format.emitCompletion(writer, fullText.toString(), fullThinking.toString(), promptTokens, completionTokens, ttfbMs, totalLatencyMs, effectiveMaxTokens, convertedNativeCalls)

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
      channel.close()
    }

    suspend fun handleError(
      error: String,
      writer: SseWriter,
      channel: Channel<StreamEvent>,
    ) {
      if (logId != null) RequestLogStore.unregisterCancellation(logId)
      markCompleted()
      val (enrichedError, kind) = enrichLlmError(error, context)
      ServerMetrics.incrementErrorCount(kind.category)
      logEvent("request_error id=$requestId endpoint=$endpoint error=${error.take(200)} streaming=true")
      val suggestion = ErrorSuggestions.suggest(kind, context)
      if (logId != null) {
        val errorJson = ResponseRenderer.renderJsonError(enrichedError, suggestion, kind)
        val actualTokens = extractActualTokenCounts(error)
        RequestLogStore.update(logId) {
          it.copy(
            partialText = null,
            responseBody = errorJson,
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
        writer.emit("data: ${ResponseRenderer.renderJsonError(enrichedError, suggestion, kind)}\n\n")
        writer.emit(ResponseRenderer.SSE_DONE)
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
    val format = ResponsesApiFormat(model.name, now, json, tools)
    return streamInference(model, prompt, requestId, endpoint, format, timeoutSeconds, images, audioClips, logId, configSnapshot, prefs, schemaInjectionProviders, schemaInjectionMessages)
  }

  // ── Streaming inference: /v1/chat/completions ────────────────────────────

  fun streamChatLlm(
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
  ): HttpResponse {
    val now = BridgeUtils.epochSeconds()
    val format = ChatCompletionsFormat(model.name, now, stopSequences, tools, json, includeUsage)
    return streamInference(model, prompt, requestId, endpoint, format, timeoutSeconds, images, audioClips, logId, configSnapshot, prefs, schemaInjectionProviders, schemaInjectionMessages)
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
    val userCancelFlag = AtomicBoolean(false)
    val channelRef = AtomicReference<Channel<StreamEvent>?>(null)
    val stateRef = AtomicReference<StreamState?>(null)
    if (logId != null) {
      RequestLogStore.registerCancellation(logId) {
        userCancelFlag.set(true)
        channelRef.get()?.close()
        stateRef.get()?.let { if (it.inferenceStarted) ServerLlmModelHelper.stopResponse(model) }
      }
    }

    val enableThinking = model.isThinkingEnabled
    val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

    // Read prefs eagerly (before the Ktor coroutine runs) — SharedPreferences reads
    // should happen on the calling thread, not inside the SSE writer lambda.
    val streamPreview = prefs?.streamLogsPreview ?: ServerPrefs.isStreamLogsPreview(context)
    val keepPartial = prefs?.keepPartialResponse ?: ServerPrefs.isKeepPartialResponse(context)

    return HttpResponse.Sse { writer ->
      val channel = Channel<StreamEvent>(Channel.UNLIMITED)
      channelRef.set(channel)
      val state = StreamState(model, requestId, endpoint, logId, streamStartMs, keepPartial)
      stateRef.set(state)

      // Captured inside the resetConversation lambda (which runs under inferenceLock) so
      // that concurrent updateConfigValues() writes are visible before we snapshot.
      var originalConfig: Map<String, Any>? = null
      val capturedNativeToolCalls = AtomicReference<List<com.google.ai.edge.litertlm.ToolCall>?>(null)

      // Launch inference on the executor thread. Callbacks send events into the channel
      // via trySend() — non-blocking from the executor thread's perspective.
      InferenceGateway.executeStreaming(
        prompt = prompt,
        timeoutSeconds = timeoutSeconds,
        executor = executor,
        inferenceLock = inferenceLock,
        resetConversation = {
          if (userCancelFlag.get()) throw java.util.concurrent.CancellationException("cancelled_while_queued")
          val initErr = reinitIfNeeded(model, supportImage, supportAudio)
          if (initErr != null) throw RuntimeException("model_init_failed: $initErr")
          state.markStarted()
          if (logId != null) RequestLogStore.update(logId) { it.copy(isGenerating = true) }
          if (configSnapshot != null) {
            originalConfig = model.configValues
            model.configValues = configSnapshot
          }
          ServerLlmModelHelper.resetConversation(
            model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            systemInstruction = buildSystemInstruction(model.prefsKey),
            tools = schemaInjectionProviders,
            initialMessages = schemaInjectionMessages,
          )
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
            onNativeToolCalls = if (schemaInjectionProviders.isNotEmpty()) { calls ->
              capturedNativeToolCalls.set(calls)
            } else null,
          )
        },
        cancelInference = { ServerLlmModelHelper.stopResponse(model) },
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
          if (state.inferenceStarted) ServerMetrics.onInferenceCompleted()
        },
        onCaughtThrowable = { t -> emitDebugStackTrace(t, format.sourceTag, model.name) },
      )

      // Consume events from the channel in the Ktor coroutine context.
      // The for-loop terminates when the channel is closed (by done, error, or cancellation).
      // Safety timeout: generous buffer beyond inference timeout to catch gateway bugs that
      // would otherwise hang this coroutine indefinitely.
      try {
        kotlinx.coroutines.withTimeout((timeoutSeconds + 30) * 1000) {
          for (event in channel) {
            // Check for client disconnect (Ktor closed the writer)
            if (writer.isCancelled) {
              ServerLlmModelHelper.stopResponse(model)
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
                state.handleError(event.error, writer, channel)
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
        if (logId != null) RequestLogStore.unregisterCancellation(logId)
        ServerLlmModelHelper.stopResponse(model)
        channel.close()
        logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=true outputChars=${state.fullText.length}")
      } finally {
        // Safety net: guarantee isInferring flag is cleared even if an unexpected
        // exception bypasses normal completion/cancellation paths.
        state.markCompleted()
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
    val (result, _) = kotlinx.coroutines.runBlocking {
      runLlm(model, WARMUP_MESSAGE, "warmup", "warmup", timeoutSeconds = WARMUP_TIMEOUT_SECONDS, eagerVisionInit = eagerVision)
    }
    val elapsedMs = SystemClock.elapsedRealtime() - startMs
    val snippet = result?.take(80)?.replace("\n", " ") ?: "no response"
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
     * Returns the truncated text and whether truncation occurred.
     */
    fun applyStopSequences(text: String, stopSequences: List<String>?): Pair<String, Boolean> {
      if (stopSequences.isNullOrEmpty()) return text to false
      var earliest = text.length
      for (stop in stopSequences) {
        val idx = text.indexOf(stop)
        if (idx in 0 until earliest) earliest = idx
      }
      return if (earliest < text.length) text.substring(0, earliest) to true
      else text to false
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
