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
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.prefs.LOG_STREAMING_PREVIEW_DEBOUNCE_MS
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import com.ollitert.llm.server.data.prefs.maxTokensInt
import com.ollitert.llm.server.data.prefs.maxTokensLong
import com.ollitert.llm.server.common.ErrorSuggestions
import com.ollitert.llm.server.service.formats.StreamingFormat
import com.ollitert.llm.server.service.http.ResponseRenderer
import com.ollitert.llm.server.service.http.SseWriter
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "OlliteRT.StreamLoop"

/**
 * Channel events bridging the executor thread (producer) to the Ktor coroutine (consumer).
 * The executor's onToken/onError callbacks send events via trySend(); the Ktor coroutine
 * consumes them and calls the appropriate SseWriter/format methods.
 */
internal sealed interface StreamEvent {
  data class Token(val partial: String, val done: Boolean, val thought: String?) : StreamEvent
  data class Error(val error: String) : StreamEvent
}

/**
 * State coordinator for an active SSE streaming inference session.
 */
internal class StreamState(
  val context: Context,
  val model: Model,
  val requestId: String,
  val endpoint: String,
  val logId: String?,
  val streamStartMs: Long,
  val keepPartial: Boolean,
  val inferenceControl: AtomicReference<InferenceGateway.InferenceControl?>,
  val onConversationFinished: (Boolean, String?) -> Unit,
  val onLogEvent: (String) -> Unit = {},
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
    onLogEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=true outputChars=${fullText.length}")
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
    InferenceMetricsCollector.logVerboseInferenceDetails(
      context = context,
      prefs = prefs,
      modelName = model.name,
      inputTokens = inputTokens.toInt(),
      outputTokens = outputTokens.toInt(),
      ttfbMs = ttfbMs,
      generationMs = totalLatencyMs - ttfbMs,
      totalMs = totalLatencyMs,
    )
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
    val parsedToolCalls = format.emitCompletion(
      writer,
      fullText.toString(),
      fullThinking.toString(),
      promptTokens,
      completionTokens,
      ttfbMs,
      totalLatencyMs,
      effectiveMaxTokens,
      convertedNativeCalls,
      stopSequenceTriggered,
      matchedStopSequence,
    )

    if (logId != null) {
      val combinedText = InferenceStreamingLoop.buildCombinedText(fullText, fullThinking)
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
    onLogEvent("request_done id=$requestId endpoint=$endpoint streaming=true totalMs=$totalLatencyMs ttfbMs=$ttfbMs outputChars=$outputLen${format.buildLogEventSuffix(parsedToolCalls)}")
    finishConversation(
      isReusable = !stopSequenceTriggered,
      assistantText = format.buildAssistantText(fullText, fullThinking),
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
    val (enrichedError, kind) = InferenceMetricsCollector.enrichLlmError(error, context)
    ServerMetrics.incrementErrorCount(kind.category)
    onLogEvent("request_error id=$requestId endpoint=$endpoint error=${error.take(200)} streaming=true")
    val suggestion = ErrorSuggestions.suggest(kind, context)
    val oaiErrorJson = ResponseRenderer.renderJsonError(enrichedError, suggestion, kind)
    val logErrorJson = format.buildLogErrorJson(enrichedError, suggestion, kind, oaiErrorJson)
    if (logId != null) {
      val actualTokens = InferenceMetricsCollector.extractActualTokenCounts(error)
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

/**
 * Encapsulates streaming text accumulation and thinking block formatting.
 */
object InferenceStreamingLoop {

  /**
   * Combines visible output and thinking text into a final formatted string.
   */
  fun buildCombinedText(fullText: CharSequence, fullThinking: CharSequence): String =
    if (fullThinking.isNotEmpty()) "<think>${fullThinking}</think>${fullText}" else fullText.toString()
}
