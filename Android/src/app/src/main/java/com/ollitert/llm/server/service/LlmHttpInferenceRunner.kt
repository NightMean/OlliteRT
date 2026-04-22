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
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ErrorCategory
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.llmSupportAudio
import com.ollitert.llm.server.data.llmSupportImage
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.data.LOG_STREAMING_PREVIEW_DEBOUNCE_MS
import com.ollitert.llm.server.data.WARMUP_MESSAGE
import com.ollitert.llm.server.data.BLOCKING_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.CHAT_COMPLETIONS_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.RESPONSES_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.WARMUP_TIMEOUT_SECONDS
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Executes LLM inference (blocking and streaming) against a loaded model.
 * Handles model re-initialization for vision/audio, token counting, timeout,
 * tool call detection, stop sequences, and performance metrics recording.
 *
 * Separated from LlmHttpService/NanoServer to isolate inference execution
 * from HTTP routing, service lifecycle, and notification concerns.
 *
 * Dependencies:
 * - [executor] / [inferenceLock]: serialized single-thread inference from NanoServer
 * - [context]: for reading SharedPreferences (LlmHttpPrefs)
 * - Callbacks for logging and system instruction — avoids coupling to the Service class
 * - Singletons: [ServerMetrics], [RequestLogStore], [ServerLlmModelHelper], [LlmHttpInferenceGateway]
 */
class LlmHttpInferenceRunner(
  private val context: Context,
  private val executor: ExecutorService,
  private val inferenceLock: Any,
  private val logEvent: (String) -> Unit,
  private val logPayload: (label: String, body: String, requestId: String) -> Unit,
  private val emitDebugStackTrace: (Throwable, source: String, modelName: String?) -> Unit,
  private val buildSystemInstruction: (modelName: String) -> Contents?,
) {

  private val logTag = "LlmHttpInferenceRunner"

  /**
   * Re-initialize the model if needed (null instance or missing vision support).
   * Must be called inside synchronized(inferenceLock). Returns an error message on failure, or null on success.
   *
   * Protects against per-request config overrides poisoning EngineConfig.maxNumTokens:
   * saves the overridden configValues, restores the persisted base config for initialize(),
   * then puts the override back so resetConversation() picks up per-request sampler values.
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
      Log.i(logTag, "Re-initializing model for vision/audio support")
      ServerLlmModelHelper.safeCleanup(model)
    }
    val overriddenConfig = model.configValues
    val savedConfig = LlmHttpPrefs.getInferenceConfig(context, model.name)
    if (savedConfig != null) {
      model.configValues = savedConfig.toMap()
    }
    var err = ""
    ServerLlmModelHelper.initialize(
      context = context,
      model = model,
      supportImage = supportImage,
      supportAudio = supportAudio,
      onDone = { err = it },
      systemInstruction = buildSystemInstruction(model.name),
    )
    model.configValues = overriddenConfig
    if (err.isNotEmpty()) {
      model.instance = null
      return err
    }
    model.initializedWithVision = supportImage
    return null
  }

  // ── Blocking inference ───────────────────────────────────────────────────

  /**
   * Run a single blocking inference pass. Returns (output, error) — one is always null.
   * Output includes thinking content wrapped in `<think>` tags if the model produced it.
   *
   * Called by endpoint handlers for non-streaming /generate, /v1/chat/completions,
   * /v1/completions, and /v1/responses.
   */
  fun runLlm(
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
  ): Pair<String?, String?> {
    // Track input tokens (rough estimate: ~4 chars per token)
    ServerMetrics.addTokensIn(estimateTokensLong(prompt))
    // Track request modality
    ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = audioClips.isNotEmpty())

    val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVisionInit)
    val supportAudio = model.llmSupportAudio
    synchronized(inferenceLock) {
      val initErr = reinitIfNeeded(model, supportImage, supportAudio)
      if (initErr != null) {

        return null to context.getString(R.string.error_model_init_failed, initErr)
      }
    }
    val enableThinking = model.llmSupportThinking &&
      (model.configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
    val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

    // Register a cancellation callback so the user can stop this request from the Logs screen.
    // For non-streaming requests, calling stopResponse triggers CancellationException in the
    // LiteRT SDK which completes inference early — we then check the flag and return an error.
    val userCancelFlag = AtomicBoolean(false)
    if (logId != null) {
      RequestLogStore.registerCancellation(logId) {
        userCancelFlag.set(true)
        ServerLlmModelHelper.stopResponse(model)
      }
    }

    val originalConfig = if (configSnapshot != null) model.configValues else null

    val result = LlmHttpInferenceGateway.execute(
      prompt = prompt,
      timeoutSeconds = timeoutSeconds,
      executor = executor,
      inferenceLock = inferenceLock,
      resetConversation = {
        if (configSnapshot != null) model.configValues = configSnapshot
        ServerLlmModelHelper.resetConversation(model, supportImage = supportImage, supportAudio = supportAudio, systemInstruction = buildSystemInstruction(model.name))
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
        )
      },
      cancelInference = { ServerLlmModelHelper.stopResponse(model) },
      elapsedMs = { SystemClock.elapsedRealtime() },
      onCaughtThrowable = { t -> emitDebugStackTrace(t, "execute", model.name) },
    )
    if (originalConfig != null && model.instance != null) model.configValues = originalConfig
    if (logId != null) RequestLogStore.unregisterCancellation(logId)

    // If the user tapped Stop in the Logs screen, return a cancellation error
    // instead of the (potentially partial) inference output.
    if (userCancelFlag.get()) {
      val keepPartial = LlmHttpPrefs.isKeepPartialResponse(context)
      val partial = if (keepPartial && !result.output.isNullOrEmpty()) result.output else null
      if (logId != null) {
        RequestLogStore.update(logId) {
          it.copy(partialText = partial, isPending = false, isCancelled = true, latencyMs = result.totalMs)
        }
      }
      logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=false user_stopped=true outputChars=${result.output?.length ?: 0}")
      return null to "Generation stopped by user in OlliteRT"
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
      val maxCtx = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toLong() ?: 0L
      ServerMetrics.addTokens(outputTokens)
      ServerMetrics.recordLatency(result.totalMs)
      ServerMetrics.recordTtfb(result.ttfbMs)
      if (result.ttfbMs > 0) {
        ServerMetrics.recordInferenceMetrics(inputTokens, outputTokens, result.ttfbMs, result.totalMs - result.ttfbMs, maxCtx)
      }
      emitDebugInferenceLog(inputTokens, outputTokens, result.ttfbMs, result.totalMs - result.ttfbMs, result.totalMs, model.name)
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

  private sealed interface StreamingFormat {
    val sourceTag: String
    val bufferAllTokens: Boolean
    val stopSequences: List<String>?

    fun emitHeader(stream: BlockingQueueInputStream)
    fun emitThinkingDelta(stream: BlockingQueueInputStream, text: String)
    fun emitContentDelta(stream: BlockingQueueInputStream, text: String)
    fun emitThinkingClose(stream: BlockingQueueInputStream)
    fun estimateInputTokens(prompt: String): Long
    fun estimateInputTokensInt(prompt: String): Int
    fun emitCompletion(
      stream: BlockingQueueInputStream,
      fullText: String,
      fullThinking: String,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
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
  ) : StreamingFormat {
    private val respId = LlmHttpBridgeUtils.generateResponseId()
    private val msgId = LlmHttpBridgeUtils.generateMessageId()
    override val sourceTag = "executeStreaming_responses"
    override val bufferAllTokens = false
    override val stopSequences: List<String>? = null

    override fun emitHeader(stream: BlockingQueueInputStream) {
      stream.enqueue(LlmHttpResponseRenderer.buildStreamingHeader(modelName, respId, msgId, now))
    }
    override fun emitThinkingDelta(stream: BlockingQueueInputStream, text: String) {
      val esc = LlmHttpBridgeUtils.escapeSseText(text)
      stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
    }
    override fun emitContentDelta(stream: BlockingQueueInputStream, text: String) {
      val esc = LlmHttpBridgeUtils.escapeSseText(text)
      stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
    }
    override fun emitThinkingClose(stream: BlockingQueueInputStream) {
      val esc = LlmHttpBridgeUtils.escapeSseText("</think>")
      stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
    }
    override fun estimateInputTokens(prompt: String): Long = estimateTokensLongByLength(prompt.length)
    override fun estimateInputTokensInt(prompt: String): Int = estimateTokensByLength(prompt.length)
    override fun emitCompletion(
      stream: BlockingQueueInputStream,
      fullText: String,
      fullThinking: String,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
    ): List<ToolCall> {
      val combinedText = buildCombinedText(fullText, fullThinking)
      val esc = LlmHttpBridgeUtils.escapeSseText(combinedText)
      stream.enqueue(LlmHttpResponseRenderer.buildStreamingFooter(modelName, respId, msgId, now, esc, inputTokens = promptTokens, outputTokens = completionTokens))
      stream.finish()
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
      return json.encodeToString(LlmHttpPayloadBuilders.responsesResponseWithText(modelName, combinedText, promptLen = promptLen))
    }
    override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>) = ""
  }

  private class ChatCompletionsFormat(
    private val modelName: String,
    private val now: Long,
    override val stopSequences: List<String>?,
    private val tools: List<ToolSpec>?,
    private val json: Json,
  ) : StreamingFormat {
    private val chatId = LlmHttpBridgeUtils.generateChatCompletionId()
    override val sourceTag = "executeStreaming_chat"
    override val bufferAllTokens = tools != null

    override fun emitHeader(stream: BlockingQueueInputStream) {
      stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFirstChunk(chatId, modelName, now))
    }
    override fun emitThinkingDelta(stream: BlockingQueueInputStream, text: String) {
      stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, text))
    }
    override fun emitContentDelta(stream: BlockingQueueInputStream, text: String) {
      stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, text))
    }
    override fun emitThinkingClose(stream: BlockingQueueInputStream) {
      stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, "</think>"))
    }
    override fun estimateInputTokens(prompt: String): Long = estimateTokensLong(prompt)
    override fun estimateInputTokensInt(prompt: String): Int = estimateTokens(prompt)
    override fun emitCompletion(
      stream: BlockingQueueInputStream,
      fullText: String,
      fullThinking: String,
      promptTokens: Int,
      completionTokens: Int,
      ttfbMs: Long,
      totalLatencyMs: Long,
    ): List<ToolCall> {
      val parsedToolCalls = if (tools != null) LlmHttpToolCallParser.parseAll(fullText, tools) else emptyList()
      if (bufferAllTokens && parsedToolCalls.isNotEmpty()) {
        stream.enqueue(LlmHttpResponseRenderer.buildChatStreamToolCallChunks(chatId, modelName, now, parsedToolCalls))
      } else {
        if (bufferAllTokens) {
          stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFirstChunk(chatId, modelName, now))
          if (fullThinking.isNotEmpty()) {
            stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, "<think>$fullThinking</think>"))
          }
          if (fullText.isNotEmpty()) {
            stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, fullText))
          }
        }
        stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFinalChunk(chatId, modelName, now, "stop"))
      }
      val timings = LlmHttpPayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
      val timingsJson = if (timings != null) json.encodeToString(timings) else null
      stream.enqueue(LlmHttpResponseRenderer.buildChatStreamUsageChunk(chatId, modelName, now, promptTokens, completionTokens, timingsJson))
      stream.enqueue(LlmHttpResponseRenderer.SSE_DONE)
      stream.finish()
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
      val timings = LlmHttpPayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
      return if (parsedToolCalls.isNotEmpty()) {
        json.encodeToString(LlmHttpPayloadBuilders.chatResponseWithToolCalls(modelName, parsedToolCalls, promptLen = promptLen, timings = timings))
      } else {
        json.encodeToString(LlmHttpPayloadBuilders.chatResponseWithText(modelName, combinedText, promptLen = promptLen, timings = timings))
      }
    }
    override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String {
      if (parsedToolCalls.isEmpty()) return ""
      return " tool_calls=${parsedToolCalls.joinToString(",") { it.function.name }} count=${parsedToolCalls.size}"
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
    sseExtraHeaders: Map<String, String> = emptyMap(),
  ): NanoHTTPD.Response {
    val now = LlmHttpBridgeUtils.epochSeconds()
    val format = ResponsesApiFormat(model.name, now, json)
    return streamInference(model, prompt, requestId, endpoint, format, timeoutSeconds, images, audioClips, logId, configSnapshot, sseExtraHeaders)
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
    @Suppress("UNUSED_PARAMETER") includeUsage: Boolean = false,
    stopSequences: List<String>? = null,
    tools: List<ToolSpec>? = null,
    configSnapshot: Map<String, Any>? = null,
    json: Json,
    sseExtraHeaders: Map<String, String> = emptyMap(),
  ): NanoHTTPD.Response {
    val now = LlmHttpBridgeUtils.epochSeconds()
    val format = ChatCompletionsFormat(model.name, now, stopSequences, tools, json)
    return streamInference(model, prompt, requestId, endpoint, format, timeoutSeconds, images, audioClips, logId, configSnapshot, sseExtraHeaders)
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
    sseExtraHeaders: Map<String, String>,
  ): NanoHTTPD.Response {
    val streamStartMs = SystemClock.elapsedRealtime()
    ServerMetrics.addTokensIn(estimateTokensLong(prompt))
    ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = audioClips.isNotEmpty())

    val eagerVision = LlmHttpPrefs.isEagerVisionInit(context)
    val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVision)
    val supportAudio = model.llmSupportAudio
    synchronized(inferenceLock) {
      val initErr = reinitIfNeeded(model, supportImage, supportAudio)
      if (initErr != null) {
        if (logId != null) {
          val errorJson = LlmHttpResponseRenderer.renderJsonError("model_init_failed: $initErr")
          RequestLogStore.update(logId) { it.copy(responseBody = errorJson, isPending = false, level = LogLevel.ERROR) }
        }
        return NanoHTTPD.newFixedLengthResponse(
          NanoHTTPD.Response.Status.INTERNAL_ERROR,
          "application/json; charset=utf-8",
          LlmHttpResponseRenderer.renderJsonError("model_init_failed"),
        )
      }
    }

    val enableThinking = model.llmSupportThinking &&
      (model.configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
    val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

    val fullText = StringBuilder()
    val fullThinking = StringBuilder()
    var headerWritten = false
    var thinkingTagOpened = false
    var lastLogUpdateMs = 0L
    var firstTokenMs = 0L
    var inferenceCompleted = false
    var stopSequenceTriggered = false
    val streamPreview = LlmHttpPrefs.isStreamLogsPreview(context)
    val keepPartial = LlmHttpPrefs.isKeepPartialResponse(context)

    val stream = BlockingQueueInputStream()

    if (logId != null) {
      RequestLogStore.registerCancellation(logId) {
        stream.cancel()
        // Signal LiteRT immediately so prefill-phase cancellation doesn't have to
        // wait for the first onToken callback before stopResponse is called.
        ServerLlmModelHelper.stopResponse(model)
      }
    }

    // Read before the executor applies the snapshot. configValues is @Volatile so the
    // read is visibility-safe without a lock. Do NOT wrap in synchronized(inferenceLock) —
    // the onToken callback runs inside the executor's synchronized block, and the LiteRT
    // SDK may deliver callbacks on a different thread, causing a deadlock.
    val originalConfig = if (configSnapshot != null) model.configValues else null

    LlmHttpInferenceGateway.executeStreaming(
      prompt = prompt,
      timeoutSeconds = timeoutSeconds,
      executor = executor,
      inferenceLock = inferenceLock,
      resetConversation = {
        if (configSnapshot != null) model.configValues = configSnapshot
        ServerLlmModelHelper.resetConversation(model, supportImage = supportImage, supportAudio = supportAudio, systemInstruction = buildSystemInstruction(model.name))
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
        )
      },
      cancelInference = { ServerLlmModelHelper.stopResponse(model) },
      onToken = { partial, done, thought ->
        if (stream.isCancelled) {
          if (logId != null) RequestLogStore.unregisterCancellation(logId)
          if (originalConfig != null && model.instance != null) model.configValues = originalConfig
          ServerLlmModelHelper.stopResponse(model)
          inferenceCompleted = true
          ServerMetrics.onInferenceCompleted()
          if (logId != null) {
            val cancelledPartial = if (keepPartial && (fullText.isNotEmpty() || fullThinking.isNotEmpty())) {
              buildString {
                if (fullThinking.isNotEmpty()) {
                  append("<think>"); append(fullThinking); append("</think>")
                }
                append(fullText)
              }
            } else null
            RequestLogStore.update(logId) {
              it.copy(partialText = cancelledPartial, isPending = false, isCancelled = true, latencyMs = SystemClock.elapsedRealtime() - streamStartMs)
            }
          }
          logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=true outputChars=${fullText.length}")
          return@executeStreaming
        }
        try {
          if (firstTokenMs == 0L && (partial.isNotEmpty() || !thought.isNullOrEmpty())) {
            firstTokenMs = SystemClock.elapsedRealtime()
          }
          if (!format.bufferAllTokens) {
            if (!headerWritten) {
              headerWritten = true
              format.emitHeader(stream)
            }
            if (!thought.isNullOrEmpty()) {
              fullThinking.append(thought)
              val thinkText = if (!thinkingTagOpened) {
                thinkingTagOpened = true
                "<think>$thought"
              } else {
                thought
              }
              format.emitThinkingDelta(stream, thinkText)
            }
          } else {
            if (!thought.isNullOrEmpty()) fullThinking.append(thought)
          }
          if (partial.isNotEmpty() && !stopSequenceTriggered) {
            fullText.append(partial)
            if (!format.stopSequences.isNullOrEmpty()) {
              val currentText = fullText.toString()
              var stopIdx = currentText.length
              for (stop in format.stopSequences!!) {
                val idx = currentText.indexOf(stop)
                if (idx in 0 until stopIdx) stopIdx = idx
              }
              if (stopIdx < currentText.length) {
                fullText.clear()
                fullText.append(currentText.substring(0, stopIdx))
                stopSequenceTriggered = true
                ServerLlmModelHelper.stopResponse(model)
              }
            }
            if (!format.bufferAllTokens && !stopSequenceTriggered) {
              val text = if (thinkingTagOpened) {
                thinkingTagOpened = false
                "</think>$partial"
              } else {
                partial
              }
              format.emitContentDelta(stream, text)
            }
          }
          if (streamPreview && logId != null && !done) {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastLogUpdateMs >= LOG_STREAMING_PREVIEW_DEBOUNCE_MS) {
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
                Log.e("OlliteRT", "Error building thinking preview: ${e.message}", e)
                fullText.toString()
              }
              RequestLogStore.updatePartialText(logId, previewText)
            }
          }
          if (done) {
            if (logId != null) RequestLogStore.unregisterCancellation(logId)
            if (originalConfig != null && model.instance != null) model.configValues = originalConfig
            val outputLen = fullText.length
            val inputTokens = format.estimateInputTokens(prompt)
            val outputTokens = estimateTokensLongByLength(outputLen)
            val totalLatencyMs = SystemClock.elapsedRealtime() - streamStartMs
            val ttfbMs = if (firstTokenMs > 0) firstTokenMs - streamStartMs else 0L
            val maxCtx = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toLong() ?: 0L
            ServerMetrics.addTokens(outputTokens)
            ServerMetrics.recordLatency(totalLatencyMs)
            ServerMetrics.recordTtfb(ttfbMs)
            if (firstTokenMs > 0) {
              ServerMetrics.recordInferenceMetrics(inputTokens, outputTokens, ttfbMs, totalLatencyMs - ttfbMs, maxCtx)
            }
            emitDebugInferenceLog(inputTokens, outputTokens, ttfbMs, totalLatencyMs - ttfbMs, totalLatencyMs, model.name)
            inferenceCompleted = true
            ServerMetrics.onInferenceCompleted()
            val promptTokens = format.estimateInputTokensInt(prompt)
            val completionTokens = estimateTokensByLength(outputLen)

            if (!format.bufferAllTokens && thinkingTagOpened) {
              thinkingTagOpened = false
              format.emitThinkingClose(stream)
            }

            val parsedToolCalls = format.emitCompletion(stream, fullText.toString(), fullThinking.toString(), promptTokens, completionTokens, ttfbMs, totalLatencyMs)

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
                  isThinking = fullThinking.isNotEmpty(),
                  ttfbMs = ttfbMs,
                  decodeSpeed = reqDecodeSpeed,
                  prefillSpeed = reqPrefillSpeed,
                  itlMs = reqItlMs,
                )
              }
            }
            logEvent("request_done id=$requestId endpoint=$endpoint streaming=true totalMs=$totalLatencyMs ttfbMs=$ttfbMs outputChars=$outputLen${format.buildLogEventSuffix(parsedToolCalls)}")
          }
        } catch (e: Exception) {
          if (logId != null) RequestLogStore.unregisterCancellation(logId)

          if (originalConfig != null && model.instance != null) model.configValues = originalConfig
          if (!inferenceCompleted) {
            inferenceCompleted = true
            ServerMetrics.onInferenceCompleted()
          }
          logEvent("request_error id=$requestId endpoint=$endpoint error=stream_write_failed msg=${e.message} streaming=true")
          if (logId != null) {
            val errorJson = LlmHttpResponseRenderer.renderJsonError("stream_write_failed: ${e.message}")
            RequestLogStore.update(logId) { it.copy(partialText = null, responseBody = errorJson, isPending = false, latencyMs = SystemClock.elapsedRealtime() - streamStartMs, level = LogLevel.ERROR) }
          }
          try { stream.finish() } catch (e: Exception) { Log.w("OlliteRT", "stream.finish() failed during cleanup", e) }
        }
      },
      onError = { error ->
        if (logId != null) RequestLogStore.unregisterCancellation(logId)

        if (originalConfig != null && model.instance != null) model.configValues = originalConfig
        if (!inferenceCompleted) {
          inferenceCompleted = true
          ServerMetrics.onInferenceCompleted()
        }
        val (enrichedError, kind) = enrichLlmError(error, context)
        ServerMetrics.incrementErrorCount(kind.category)
        logEvent("request_error id=$requestId endpoint=$endpoint error=$error streaming=true")
        val suggestion = LlmHttpErrorSuggestions.suggest(kind, context)
        if (logId != null) {
          val errorJson = LlmHttpResponseRenderer.renderJsonError(enrichedError, suggestion, kind.category)
          val actualTokens = extractActualTokenCounts(error)
          RequestLogStore.update(logId) {
            it.copy(
              partialText = null,
              responseBody = errorJson,
              isPending = false,
              latencyMs = SystemClock.elapsedRealtime() - streamStartMs,
              level = LogLevel.ERROR,
              inputTokenEstimate = actualTokens?.first ?: it.inputTokenEstimate,
              maxContextTokens = actualTokens?.second ?: it.maxContextTokens,
              isExactTokenCount = actualTokens != null || it.isExactTokenCount,
            )
          }
        }
        try {
          stream.enqueue("data: ${LlmHttpResponseRenderer.renderJsonError(enrichedError, suggestion, kind.category)}\n\n")
          stream.enqueue(LlmHttpResponseRenderer.SSE_DONE)
          stream.finish()
        } catch (e: Exception) { Log.w("OlliteRT", "stream.finish() failed during cleanup", e) }
      },
      onCaughtThrowable = { t -> emitDebugStackTrace(t, format.sourceTag, model.name) },
    )

    return FlushingSseResponse(stream, sseExtraHeaders)
  }

  // ── Warmup ───────────────────────────────────────────────────────────────

  /**
   * Warm up the model with a short test inference.
   * Used during model loading to pre-fill caches and verify the model works.
   */
  fun warmUpModel(model: Model) {
    val startMs = SystemClock.elapsedRealtime()
    val eagerVision = LlmHttpPrefs.isEagerVisionInit(context)
    val (result, _) = runLlm(model, WARMUP_MESSAGE, "warmup", "warmup", timeoutSeconds = WARMUP_TIMEOUT_SECONDS, eagerVisionInit = eagerVision)
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
  ) {
    if (!LlmHttpPrefs.isVerboseDebugEnabled(context)) return
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
      val kind = LlmHttpErrorSuggestions.classifyFromString(error)
      val suggestion = LlmHttpErrorSuggestions.suggest(kind, context)
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
