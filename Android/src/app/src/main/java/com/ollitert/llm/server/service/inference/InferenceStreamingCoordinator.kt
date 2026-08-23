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

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.LogLevel
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import com.ollitert.llm.server.data.SSE_PING_INTERVAL_MS
import com.ollitert.llm.server.data.STREAM_OUTER_TIMEOUT_SAFETY_BUFFER_SECONDS
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.isThinkingEnabled
import com.ollitert.llm.server.data.llmSupportAudio
import com.ollitert.llm.server.data.llmSupportImage
import com.ollitert.llm.server.data.llmSupportThinking
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.formats.StreamingFormat
import com.ollitert.llm.server.service.http.HttpResponse
import com.ollitert.llm.server.service.http.ResponseRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference

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

    val enableThinking = if (model.llmSupportThinking) {
      enableThinkingOverride ?: model.isThinkingEnabled
    } else {
      false
    }
    val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

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

      var originalConfig: Map<String, Any>? = null
      val capturedNativeToolCalls = AtomicReference<List<com.google.ai.edge.litertlm.ToolCall>?>(null)
      var preparedIncrementalUserText = incrementalUserText
      var preparedCacheGeneration = conversationCacheGeneration
      var preparedSystemInstruction: Contents? = null

      if (!format.bufferAllTokens && format.emitsHeaderEarly && !writer.isCancelled) {
        try {
          format.emitHeader(writer)
          state.headerWritten = true
        } catch (e: Exception) {
          Log.w(TAG, "Pre-emit header failed for $requestId", e)
        }
      }

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
          }
        }
      } else null

      InferenceGateway.executeStreaming(
        prompt = prompt,
        timeoutSeconds = timeoutSeconds,
        executor = executor,
        inferenceLock = inferenceLock,
        resetConversation = {
          if (cancellationBridge.cancellationWasAccepted()) {
            throw java.util.concurrent.CancellationException("cancelled_while_queued")
          }
          val initErr = InferenceModelPreparer.reinitIfNeeded(
            context = context,
            model = model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            buildSystemInstruction = buildSystemInstruction,
          )
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
      }
    }
  }
}
