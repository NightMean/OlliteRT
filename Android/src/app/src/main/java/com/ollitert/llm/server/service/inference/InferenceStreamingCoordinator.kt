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
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import com.ollitert.llm.server.data.prefs.SSE_PING_INTERVAL_MS
import com.ollitert.llm.server.data.prefs.STREAM_OUTER_TIMEOUT_SAFETY_BUFFER_SECONDS
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.model.llmSupportAudio
import com.ollitert.llm.server.data.model.llmSupportImage
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.formats.StreamingFormat
import com.ollitert.llm.server.service.http.HttpResponse
import com.ollitert.llm.server.service.http.ResponseRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference
import com.ollitert.llm.server.data.prefs.isStreamLogsPreview
private const val TAG = "OlliteRT.InferenceStream"

internal class InferenceStreamingCoordinator(
  private val context: Context,
  private val executor: ExecutorService,
  private val inferenceLock: Any,
  private val logEvent: (String) -> Unit,
  private val emitDebugStackTrace: (Throwable, source: String, modelName: String?) -> Unit,
  private val buildSystemInstruction: (modelName: String) -> Contents?,
) {

  fun streamInference(
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
    // Non-null = natively constrain the response to this JSON schema (constrained decoding).
    responseFormatSchema: String? = null,
    thinkingBudgetTokens: Int? = null,
    frequencyPenalty: Double? = null,
    presencePenalty: Double? = null,
  ): HttpResponse {
    val streamStartMs = SystemClock.elapsedRealtime()
    ServerMetrics.addTokensIn(estimateTokensLong(prompt))
    ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = audioClips.isNotEmpty())

    val eagerVision = prefs?.eagerVisionInit ?: ServerPrefs.isEagerVisionInit(context)
    val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVision)
    val supportAudio = model.llmSupportAudio

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

    val session = ConversationDispatchSession(
      debugLogTag = TAG,
      incrementalReuseLabel = "INCREMENTAL_REUSE",
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
      reinitIfNeeded = {
        InferenceModelPreparer.reinitIfNeeded(
          context = context,
          model = model,
          supportImage = supportImage,
          supportAudio = supportAudio,
          buildSystemInstruction = buildSystemInstruction,
        )
      },
      logId = logId,
      responseFormatSchema = responseFormatSchema,
      thinkingBudgetTokens = thinkingBudgetTokens,
      frequencyPenalty = frequencyPenalty,
      presencePenalty = presencePenalty,
    )

    val streamPreview = prefs?.streamLogsPreview ?: ServerPrefs.isStreamLogsPreview(context)
    val keepPartial = prefs?.keepPartialResponse ?: ServerPrefs.isKeepPartialResponse(context)

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

      val capturedNativeToolCalls = session.capturedNativeToolCalls

      if (!format.bufferAllTokens && format.emitsHeaderEarly && !writer.isCancelled) {
        try {
          format.emitHeader(writer)
          state.headerWritten = true
        } catch (e: Exception) {
          Log.w(TAG, "Pre-emit header failed for $requestId", e)
        }
      }

      // Dedicated scope for the heartbeat: this lambda always runs inside
      // withContext(NonCancellable) (see respondHttpResponse), so a child of the
      // ambient coroutine context would ignore HTTP-call cancellation and only die
      // via the explicit cancel in the finally below. An owned SupervisorJob makes
      // that ownership explicit and cancels everything in one call.
      val heartbeatScope = if (format.emitsHeaderEarly) {
        CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
      } else null
      val heartbeatJob = heartbeatScope?.launch {
        try {
          while (isActive) {
            delay(SSE_PING_INTERVAL_MS)
            // Run until inference completes, not just until the first token — every
            // ping write is also a liveness probe that detects half-open client
            // connections during long generations. Ping events between deltas are
            // spec-legal for Anthropic; other formats never start a heartbeat.
            if (writer.isCancelled || state.inferenceCompleted) break
            try {
              format.emitPing(writer)
            } catch (e: Exception) {
              Log.w(TAG, "Heartbeat ping failed for $requestId", e)
              break
            }
          }
        } catch (_: kotlinx.coroutines.CancellationException) {
        }
      }

      InferenceGateway.executeStreaming(
        prompt = prompt,
        timeoutSeconds = timeoutSeconds,
        executor = executor,
        inferenceLock = inferenceLock,
        resetConversation = {
          session.resetConversation(cancellationBridge.cancellationWasAccepted()) {
            state.markStarted()
          }
        },
        runInference = session::runInference,
        cancelInference = { ServerLlmModelHelper.stopResponse(model) },
        recoverConversation = session::recoverConversation,
        onToken = { partial, done, thought ->
          channel.trySend(StreamEvent.Token(partial, done, thought))
        },
        onError = { error ->
          channel.trySend(StreamEvent.Error(error))
        },
        onInferenceFinished = {
          session.restoreOriginalConfigIfLoaded()
          state.markMetricsCompleted()
        },
        onCaughtThrowable = { t -> emitDebugStackTrace(t, format.sourceTag, model.name) },
        onExecutionReady = { control ->
          inferenceControl.set(control)
          cancellationBridge.attach(control)
          if (cancellationBridge.cancellationWasAccepted()) channel.close()
        },
      )

      try {
        kotlinx.coroutines.withTimeout((timeoutSeconds + STREAM_OUTER_TIMEOUT_SAFETY_BUFFER_SECONDS) * 1000) {
          for (event in channel) {
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
        if (!state.inferenceCompleted) {
          state.markCompleted()
          state.logCancellation()
          try { format.emitCancellation(writer, state.headerWritten) } catch (e: Exception) { Log.w(TAG, "emitCancellation failed during cleanup", e) }
        }
      } catch (_: kotlinx.coroutines.CancellationException) {
        inferenceControl.get()?.cancel(InferenceGateway.CancellationReason.CALLER)
        channel.close()
        if (!state.inferenceCompleted) {
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
        state.markCompleted()
        state.markMetricsCompleted()
        if (state.inferenceStarted) state.finishConversation(isReusable = false)
        heartbeatJob?.cancel()
        heartbeatScope?.cancel()
      }
    }
  }
}
