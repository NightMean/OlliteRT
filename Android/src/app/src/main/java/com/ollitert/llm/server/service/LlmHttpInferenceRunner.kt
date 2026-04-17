package com.ollitert.llm.server.service

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.common.ErrorCategory
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.WARMUP_MESSAGE
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
    timeoutSeconds: Long = 30,
    images: List<Bitmap> = emptyList(),
    eagerVisionInit: Boolean = false,
    logId: String? = null,
  ): Pair<String?, String?> {
    // Track input tokens (rough estimate: ~4 chars per token)
    ServerMetrics.addTokensIn((prompt.length / 4).toLong().coerceAtLeast(1))
    // Track request modality
    ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = false)

    val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVisionInit)
    val supportAudio = model.llmSupportAudio
    synchronized(this) {
      // Re-initialize if images are requested but engine lacks vision support.
      val needsReinit = model.instance == null ||
        (supportImage && !model.initializedWithVision)
      if (needsReinit) {
        if (model.instance != null) {
          Log.i(logTag, "Re-initializing model with vision support")
          try {
            ServerLlmModelHelper.cleanUp(model) {}
          } catch (e: Exception) {
            Log.w(logTag, "Error cleaning up model for reinit: ${e.message}")
          }
          // Always null instance after cleanup attempt to prevent using a half-destroyed Engine
          model.instance = null
          System.gc()
        }
        // initialize() reads configValues[MAX_TOKENS] to set EngineConfig.maxNumTokens (the
        // engine's context window). If a per-request override (withPerRequestConfig) is active,
        // configValues may contain the client's small max_tokens (e.g. 69) instead of the
        // model's configured context window (e.g. 4000) — which would permanently create the
        // Engine with a tiny context window. Save the full overridden configValues, restore the
        // persisted base config for initialize(), then put the override back so
        // resetConversation() picks up the per-request sampler values.
        val overriddenConfig = model.configValues
        val savedConfig = LlmHttpPrefs.getInferenceConfig(context, model.name)
        if (savedConfig != null) {
          model.configValues = savedConfig
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
        // Restore the per-request overridden config so resetConversation() picks up sampler values
        model.configValues = overriddenConfig
        if (err.isNotEmpty()) {
          model.instance = null  // Ensure null on failed init
          return null to "Model initialization failed: $err"
        }
        model.initializedWithVision = supportImage
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

    val result = LlmHttpInferenceGateway.execute(
      prompt = prompt,
      timeoutSeconds = timeoutSeconds,
      executor = executor,
      inferenceLock = inferenceLock,
      resetConversation = {
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
          extraContext = extraContext,
        )
      },
      cancelInference = { ServerLlmModelHelper.stopResponse(model) },
      elapsedMs = { SystemClock.elapsedRealtime() },
      onCaughtThrowable = { t -> emitDebugStackTrace(t, "execute", model.name) },
    )
    // Recycle Bitmaps after inference — toPngByteArray() already extracted the bytes,
    // so the native Bitmap memory is no longer needed. Without this, multi-MB Bitmaps
    // from multimodal requests linger until GC finalizes them.
    images.forEach { it.recycle() }
    if (logId != null) RequestLogStore.unregisterCancellation(logId)

    // If the user tapped Stop in the Logs screen, return a cancellation error
    // instead of the (potentially partial) inference output.
    if (userCancelFlag.get()) {
      if (logId != null) RequestLogStore.unregisterCancellation(logId)
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
      val inputTokens = (prompt.length / 4).toLong().coerceAtLeast(1)
      val outputTokens = (outputLen / 4).toLong().coerceAtLeast(1)
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

  // ── Streaming inference: /v1/responses ───────────────────────────────────

  /**
   * Stream inference for /v1/responses using the Responses API SSE format.
   * Returns either a streaming SSE response or an error response (if model init fails).
   *
   * The [configSnapshot] (if non-null) is applied on the executor thread during resetConversation
   * and restored after streaming completes, to avoid a race where the NanoHTTPD thread
   * restores config before the executor reads it.
   */
  fun streamLlm(
    model: Model,
    prompt: String,
    requestId: String,
    endpoint: String,
    timeoutSeconds: Long = 90,
    images: List<Bitmap> = emptyList(),
    logId: String? = null,
    promptLen: Int = 0,
    configSnapshot: Map<String, Any>? = null,
    json: Json,
  ): NanoHTTPD.Response {
    val streamStartMs = SystemClock.elapsedRealtime()
    // Track input tokens (rough estimate: ~4 chars per token)
    ServerMetrics.addTokensIn((prompt.length / 4).toLong().coerceAtLeast(1))
    // Track request modality
    ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = false)

    val eagerVision = LlmHttpPrefs.isEagerVisionInit(context)
    val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVision)
    val supportAudio = model.llmSupportAudio
    synchronized(this) {
      val needsReinit = model.instance == null ||
        (supportImage && !model.initializedWithVision)
      if (needsReinit) {
        if (model.instance != null) {
          Log.i(logTag, "Re-initializing model with vision support (stream)")
          try {
            ServerLlmModelHelper.cleanUp(model) {}
          } catch (e: Exception) {
            Log.w(logTag, "Error cleaning up model for reinit (stream): ${e.message}")
          }
          model.instance = null
          System.gc()
        }
        // Protect against per-request config override poisoning EngineConfig.maxNumTokens —
        // see detailed comment in runLlm's reinit block.
        val overriddenConfig = model.configValues
        val savedConfig = LlmHttpPrefs.getInferenceConfig(context, model.name)
        if (savedConfig != null) {
          model.configValues = savedConfig
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
          if (logId != null) {
            val errorJson = LlmHttpResponseRenderer.renderJsonError("model_init_failed: $err")
            RequestLogStore.update(logId) { it.copy(responseBody = errorJson, isPending = false, level = LogLevel.ERROR) }
          }
          return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json",
            LlmHttpResponseRenderer.renderJsonError("model_init_failed"),
          )
        }
        model.initializedWithVision = supportImage
      }
    }

    val enableThinking = model.llmSupportThinking &&
      (model.configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
    val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

    val now = System.currentTimeMillis() / 1000
    val respId = "resp-${java.util.UUID.randomUUID()}"
    val msgId = "msg-${java.util.UUID.randomUUID()}"
    val fullText = StringBuilder()
    val fullThinking = StringBuilder()
    var headerWritten = false
    var thinkingTagOpened = false
    var lastLogUpdateMs = 0L
    // Track time of first content/thinking token for TTFB and decode speed calculations
    var firstTokenMs = 0L
    val streamPreview = LlmHttpPrefs.isStreamLogsPreview(context)
    val keepPartial = LlmHttpPrefs.isKeepPartialResponse(context)

    val stream = BlockingQueueInputStream()

    // Allow the user to stop this streaming request from the Logs screen.
    if (logId != null) {
      RequestLogStore.registerCancellation(logId) { stream.cancel() }
    }

    // Capture original config so we can restore after streaming completes.
    // Only restore if the model's Engine is still alive — if a reload happened during
    // streaming, the model was re-initialized with new config and restoring would revert it.
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
          extraContext = extraContext,
        )
        // Recycle Bitmaps after sendMessageAsync — PNG bytes already extracted by toPngByteArray().
        // This runs on the executor thread after inference starts, so the Bitmaps are no longer needed.
        images.forEach { it.recycle() }
      },
      cancelInference = { ServerLlmModelHelper.stopResponse(model) },
      elapsedMs = { SystemClock.elapsedRealtime() },
      onToken = { partial, done, thought ->
        if (stream.isCancelled) {
          if (logId != null) RequestLogStore.unregisterCancellation(logId)
          if (originalConfig != null && model.instance != null) model.configValues = originalConfig
          ServerLlmModelHelper.stopResponse(model)
          ServerMetrics.onInferenceCompleted()
          if (logId != null) {
            // Include thinking content so the Logs screen can show what the
            // model was reasoning about before the request was cancelled.
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
          // Capture first token time for TTFB calculation
          if (firstTokenMs == 0L && (partial.isNotEmpty() || !thought.isNullOrEmpty())) {
            firstTokenMs = SystemClock.elapsedRealtime()
          }
          if (!headerWritten) {
            headerWritten = true
            stream.enqueue(LlmHttpResponseRenderer.buildStreamingHeader(model.name, respId, msgId, now))
          }
          // Emit thinking content wrapped in <think> tags
          if (!thought.isNullOrEmpty()) {
            fullThinking.append(thought)
            val thinkText = if (!thinkingTagOpened) {
              thinkingTagOpened = true
              "<think>$thought"
            } else {
              thought
            }
            val esc = LlmHttpBridgeUtils.escapeSseText(thinkText)
            stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
          }
          if (partial.isNotEmpty()) {
            // Close thinking tag before first regular content
            val text = if (thinkingTagOpened) {
              thinkingTagOpened = false
              "</think>$partial"
            } else {
              partial
            }
            fullText.append(partial)
            val esc = LlmHttpBridgeUtils.escapeSseText(text)
            stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
          }
          // Update log with partial text via lightweight flow (debounced to ~300ms).
          // Uses updatePartialText() which emits via a dedicated StateFlow, avoiding
          // full entries-list replacement that would cause entire LazyColumn recomposition.
          if (streamPreview && logId != null && !done) {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastLogUpdateMs >= LOG_STREAMING_PREVIEW_DEBOUNCE_MS) {
              lastLogUpdateMs = nowMs
              // Include thinking content in the preview so the Logs screen
              // can show it during streaming (not just after completion).
              val previewText = try {
                buildString {
                  if (fullThinking.isNotEmpty()) {
                    append("<think>")
                    append(fullThinking)
                    // Still thinking — no closing tag yet
                    if (!thinkingTagOpened) append("</think>")
                  }
                  append(fullText)
                }
              } catch (e: Exception) {
                Log.e("OlliteRT", "Error building thinking preview: ${e.message}", e)
                fullText.toString() // fall back to content-only
              }
              RequestLogStore.updatePartialText(logId, previewText)
            }
          }
          if (done) {
            if (logId != null) RequestLogStore.unregisterCancellation(logId)
            if (originalConfig != null && model.instance != null) model.configValues = originalConfig
            // Close thinking tag if still open (thinking-only response with no regular content)
            if (thinkingTagOpened) {
              thinkingTagOpened = false
              val esc = LlmHttpBridgeUtils.escapeSseText("</think>")
              stream.enqueue(LlmHttpResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
            }
            val outputLen = fullText.length
            val inputTokens = (promptLen / 4).toLong().coerceAtLeast(if (promptLen > 0) 1L else 0L)
            val outputTokens = (outputLen / 4).toLong().coerceAtLeast(1)
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
            ServerMetrics.onInferenceCompleted()
            // Include thinking in the full output for footer/log
            val combinedText = if (fullThinking.isNotEmpty()) {
              "<think>${fullThinking}</think>${fullText}"
            } else {
              fullText.toString()
            }
            val promptTokens = (promptLen / 4).coerceAtLeast(if (promptLen > 0) 1 else 0)
            val completionTokens = (outputLen / 4).coerceAtLeast(if (outputLen > 0) 1 else 0)
            val esc = LlmHttpBridgeUtils.escapeSseText(combinedText)
            stream.enqueue(LlmHttpResponseRenderer.buildStreamingFooter(model.name, respId, msgId, now, esc, inputTokens = promptTokens, outputTokens = completionTokens))
            stream.finish()
            if (logId != null) {
              val responseJson = json.encodeToString(LlmHttpPayloadBuilders.responsesResponseWithText(model.name, combinedText, promptLen = promptLen))
              // Compute per-request performance metrics for the Logs info popup
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
            logEvent("request_done id=$requestId endpoint=$endpoint streaming=true totalMs=$totalLatencyMs ttfbMs=$ttfbMs outputChars=$outputLen")
          }
        } catch (e: Exception) {
          if (logId != null) RequestLogStore.unregisterCancellation(logId)
          if (originalConfig != null && model.instance != null) model.configValues = originalConfig
          ServerMetrics.onInferenceCompleted()
          logEvent("request_error id=$requestId endpoint=$endpoint error=stream_write_failed msg=${e.message} streaming=true")
          if (logId != null) {
            val errorJson = LlmHttpResponseRenderer.renderJsonError("stream_write_failed: ${e.message}")
            RequestLogStore.update(logId) { it.copy(partialText = null, responseBody = errorJson, isPending = false, latencyMs = SystemClock.elapsedRealtime() - streamStartMs, level = LogLevel.ERROR) }
          }
          try { stream.finish() } catch (_: Exception) {}
        }
      },
      onError = { error ->
        if (logId != null) RequestLogStore.unregisterCancellation(logId)
        if (originalConfig != null && model.instance != null) model.configValues = originalConfig
        ServerMetrics.onInferenceCompleted()
        val (enrichedError, kind) = enrichLlmError(error)
        ServerMetrics.incrementErrorCount(kind.category)
        logEvent("request_error id=$requestId endpoint=$endpoint error=$error streaming=true")
        val suggestion = LlmHttpErrorSuggestions.suggest(kind)
        if (logId != null) {
          val errorJson = LlmHttpResponseRenderer.renderJsonError(enrichedError, suggestion, kind.category)
          // Extract actual token counts from LiteRT error (e.g. "4467 >= 4000") to replace charLen/4 estimate
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
        } catch (_: Exception) {}
      },
      onCaughtThrowable = { t -> emitDebugStackTrace(t, "executeStreaming_responses", model.name) },
    )

    return FlushingSseResponse(stream)
  }

  // ── Streaming inference: /v1/chat/completions ────────────────────────────

  /**
   * True per-token streaming for /v1/chat/completions using chat.completion.chunk SSE format.
   *
   * When [tools] are present, tokens are **buffered** instead of streamed. After generation
   * completes, the parser checks the full output: if a tool call is detected, proper OpenAI
   * streaming `tool_calls` SSE chunks are emitted; otherwise buffered content is flushed as
   * regular text. This is necessary because tool call detection requires the full output.
   */
  fun streamChatLlm(
    model: Model,
    prompt: String,
    requestId: String,
    endpoint: String,
    timeoutSeconds: Long = 120,
    images: List<Bitmap> = emptyList(),
    logId: String? = null,
    @Suppress("UNUSED_PARAMETER") includeUsage: Boolean = false, // Usage+timings are always sent for client compatibility
    stopSequences: List<String>? = null,
    tools: List<ToolSpec>? = null,
    configSnapshot: Map<String, Any>? = null,
    json: Json,
  ): NanoHTTPD.Response {
    val streamStartMs = SystemClock.elapsedRealtime()
    ServerMetrics.addTokensIn((prompt.length / 4).toLong().coerceAtLeast(1))
    ServerMetrics.recordModality(hasImages = images.isNotEmpty(), hasAudio = false)

    val eagerVision = LlmHttpPrefs.isEagerVisionInit(context)
    val supportImage = model.llmSupportImage && (images.isNotEmpty() || eagerVision)
    val supportAudio = model.llmSupportAudio
    synchronized(this) {
      val needsReinit = model.instance == null ||
        (supportImage && !model.initializedWithVision)
      if (needsReinit) {
        if (model.instance != null) {
          Log.i(logTag, "Re-initializing model with vision support (stream-chat)")
          try {
            ServerLlmModelHelper.cleanUp(model) {}
          } catch (e: Exception) {
            Log.w(logTag, "Error cleaning up model for reinit (stream-chat): ${e.message}")
          }
          model.instance = null
          System.gc()
        }
        // Protect against per-request config override poisoning EngineConfig.maxNumTokens —
        // see detailed comment in runLlm's reinit block.
        val overriddenConfig = model.configValues
        val savedConfig = LlmHttpPrefs.getInferenceConfig(context, model.name)
        if (savedConfig != null) {
          model.configValues = savedConfig
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
          if (logId != null) {
            val errorJson = LlmHttpResponseRenderer.renderJsonError("model_init_failed: $err")
            RequestLogStore.update(logId) { it.copy(responseBody = errorJson, isPending = false, level = LogLevel.ERROR) }
          }
          return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json",
            LlmHttpResponseRenderer.renderJsonError("model_init_failed"),
          )
        }
        model.initializedWithVision = supportImage
      }
    }

    val enableThinking = model.llmSupportThinking &&
      (model.configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean) != false
    val extraContext = if (enableThinking) mapOf("enable_thinking" to "true") else null

    val now = System.currentTimeMillis() / 1000
    val chatId = "chatcmpl-${java.util.UUID.randomUUID()}"
    val fullText = StringBuilder()
    val fullThinking = StringBuilder()
    var headerWritten = false
    var thinkingTagOpened = false
    var lastLogUpdateMs = 0L
    // Track time of first content/thinking token for TTFB and decode speed calculations
    var firstTokenMs = 0L
    val streamPreview = LlmHttpPrefs.isStreamLogsPreview(context)
    val keepPartial = LlmHttpPrefs.isKeepPartialResponse(context)

    val stream = BlockingQueueInputStream()
    // When tools are present, buffer all tokens instead of streaming them.
    // We can't know if the output is a tool call until generation completes,
    // so we must buffer first, then emit either tool_calls or content.
    val bufferForTools = tools != null

    // Allow the user to stop this streaming request from the Logs screen.
    if (logId != null) {
      RequestLogStore.registerCancellation(logId) { stream.cancel() }
    }

    // Capture original config so we can restore after streaming completes.
    // configSnapshot (if non-null) is applied in resetConversation on the executor thread
    // to avoid a race where config is restored before inference reads it.
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
          extraContext = extraContext,
        )
        // Recycle Bitmaps after sendMessageAsync — PNG bytes already extracted by toPngByteArray().
        images.forEach { it.recycle() }
      },
      cancelInference = { ServerLlmModelHelper.stopResponse(model) },
      elapsedMs = { SystemClock.elapsedRealtime() },
      onToken = { partial, done, thought ->
        if (stream.isCancelled) {
          if (logId != null) RequestLogStore.unregisterCancellation(logId)
          if (originalConfig != null && model.instance != null) model.configValues = originalConfig
          ServerLlmModelHelper.stopResponse(model)
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
          // Capture first token time for TTFB calculation
          if (firstTokenMs == 0L && (partial.isNotEmpty() || !thought.isNullOrEmpty())) {
            firstTokenMs = SystemClock.elapsedRealtime()
          }
          if (!bufferForTools) {
            if (!headerWritten) {
              headerWritten = true
              stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFirstChunk(chatId, model.name, now))
            }
            // Emit thinking content wrapped in <think> tags
            if (!thought.isNullOrEmpty()) {
              fullThinking.append(thought)
              val thinkText = if (!thinkingTagOpened) {
                thinkingTagOpened = true
                "<think>$thought"
              } else {
                thought
              }
              stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, thinkText))
            }
          } else {
            // Still accumulate thinking text when buffering for tools
            if (!thought.isNullOrEmpty()) fullThinking.append(thought)
          }
          if (partial.isNotEmpty()) {
            fullText.append(partial)
            // Check for stop sequences in accumulated text
            if (!stopSequences.isNullOrEmpty()) {
              val currentText = fullText.toString()
              var stopIdx = currentText.length
              for (stop in stopSequences) {
                val idx = currentText.indexOf(stop)
                if (idx in 0 until stopIdx) stopIdx = idx
              }
              if (stopIdx < currentText.length) {
                // Stop sequence found — truncate and finish streaming
                fullText.clear()
                fullText.append(currentText.substring(0, stopIdx))
                ServerLlmModelHelper.stopResponse(model)
                // Don't emit the stop-triggering token; fall through to done block below
              }
            }
            if (!bufferForTools) {
              val text = if (thinkingTagOpened) {
                thinkingTagOpened = false
                "</think>$partial"
              } else {
                partial
              }
              stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, text))
            }
          }
          // Update log with partial text via lightweight flow (debounced to ~300ms).
          // Uses updatePartialText() which emits via a dedicated StateFlow, avoiding
          // full entries-list replacement that would cause entire LazyColumn recomposition.
          if (streamPreview && logId != null && !done) {
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastLogUpdateMs >= LOG_STREAMING_PREVIEW_DEBOUNCE_MS) {
              lastLogUpdateMs = nowMs
              // Include thinking content in the preview so the Logs screen
              // can show it during streaming (not just after completion).
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
            val inputTokens = (prompt.length / 4).toLong().coerceAtLeast(1)
            val outputTokens = (outputLen / 4).toLong().coerceAtLeast(1)
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
            ServerMetrics.onInferenceCompleted()
            val promptTokens = (prompt.length / 4).coerceAtLeast(1)
            val completionTokens = (outputLen / 4).coerceAtLeast(if (outputLen > 0) 1 else 0)

            // Check for tool call(s) in completed output — supports parallel calls
            val parsedToolCalls = if (tools != null) LlmHttpToolCallParser.parseAll(fullText.toString(), tools) else emptyList()

            if (bufferForTools && parsedToolCalls.isNotEmpty()) {
              // Emit proper OpenAI streaming tool_calls format with per-call indexing
              stream.enqueue(LlmHttpResponseRenderer.buildChatStreamToolCallChunks(chatId, model.name, now, parsedToolCalls))
            } else {
              // Emit buffered content (if we were buffering) or close the stream normally
              if (bufferForTools) {
                // Was buffering but no tool call — emit all content at once
                stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFirstChunk(chatId, model.name, now))
                if (fullThinking.isNotEmpty()) {
                  stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, "<think>${fullThinking}</think>"))
                }
                if (fullText.isNotEmpty()) {
                  stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, fullText.toString()))
                }
              } else {
                if (thinkingTagOpened) {
                  thinkingTagOpened = false
                  stream.enqueue(LlmHttpResponseRenderer.buildChatStreamDeltaChunk(chatId, model.name, now, "</think>"))
                }
              }
              stream.enqueue(LlmHttpResponseRenderer.buildChatStreamFinalChunk(chatId, model.name, now, "stop"))
            }
            // Build non-standard performance timings (used by Open WebUI and other local LLM clients)
            val timings = LlmHttpPayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
            val timingsJson = if (timings != null) json.encodeToString(timings) else null
            // Always send usage+timings chunk, not just when stream_options.include_usage is set.
            // Most local LLM clients (Open WebUI, etc.) expect usage data in every streaming
            // response for token tracking and performance display.
            stream.enqueue(LlmHttpResponseRenderer.buildChatStreamUsageChunk(chatId, model.name, now, promptTokens, completionTokens, timingsJson))
            stream.enqueue(LlmHttpResponseRenderer.SSE_DONE)
            stream.finish()
            if (logId != null) {
              val combinedText = if (fullThinking.isNotEmpty()) {
                "<think>${fullThinking}</think>${fullText}"
              } else {
                fullText.toString()
              }
              val responseJson = if (parsedToolCalls.isNotEmpty()) {
                json.encodeToString(LlmHttpPayloadBuilders.chatResponseWithToolCalls(model.name, parsedToolCalls, promptLen = prompt.length, timings = timings))
              } else {
                json.encodeToString(LlmHttpPayloadBuilders.chatResponseWithText(model.name, combinedText, promptLen = prompt.length, timings = timings))
              }
              // Compute per-request performance metrics for the Logs info popup
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
            logEvent("request_done id=$requestId endpoint=$endpoint streaming=true totalMs=$totalLatencyMs ttfbMs=$ttfbMs outputChars=$outputLen${if (parsedToolCalls.isNotEmpty()) " tool_calls=${parsedToolCalls.joinToString(",") { it.function.name }} count=${parsedToolCalls.size}" else ""}")
          }
        } catch (e: Exception) {
          if (logId != null) RequestLogStore.unregisterCancellation(logId)
          if (originalConfig != null && model.instance != null) model.configValues = originalConfig
          ServerMetrics.onInferenceCompleted()
          logEvent("request_error id=$requestId endpoint=$endpoint error=stream_write_failed msg=${e.message} streaming=true")
          if (logId != null) {
            val errorJson = LlmHttpResponseRenderer.renderJsonError("stream_write_failed: ${e.message}")
            RequestLogStore.update(logId) { it.copy(partialText = null, responseBody = errorJson, isPending = false, latencyMs = SystemClock.elapsedRealtime() - streamStartMs, level = LogLevel.ERROR) }
          }
          try { stream.finish() } catch (_: Exception) {}
        }
      },
      onError = { error ->
        if (logId != null) RequestLogStore.unregisterCancellation(logId)
        if (originalConfig != null && model.instance != null) model.configValues = originalConfig
        ServerMetrics.onInferenceCompleted()
        val (enrichedError, kind) = enrichLlmError(error)
        ServerMetrics.incrementErrorCount(kind.category)
        logEvent("request_error id=$requestId endpoint=$endpoint error=$error streaming=true")
        val suggestion = LlmHttpErrorSuggestions.suggest(kind)
        if (logId != null) {
          val errorJson = LlmHttpResponseRenderer.renderJsonError(enrichedError, suggestion, kind.category)
          // Extract actual token counts from LiteRT error (e.g. "4467 >= 4000") to replace charLen/4 estimate
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
        } catch (_: Exception) {}
      },
      onCaughtThrowable = { t -> emitDebugStackTrace(t, "executeStreaming_chat", model.name) },
    )

    return FlushingSseResponse(stream)
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

    val body = org.json.JSONObject().apply {
      put("type", "debug_inference")
      put("ttfb_ms", ttfbMs)
      put("generation_ms", generationMs)
      put("total_ms", totalMs)
      put("input_tokens_est", inputTokens)
      put("output_tokens_est", outputTokens)
      put("decode_speed_tps", String.format(java.util.Locale.US, "%.1f", decodeSpeed))
      put("prefill_speed_tps", String.format(java.util.Locale.US, "%.1f", prefillSpeed))
      put("heap_total_mb", String.format(java.util.Locale.US, "%.1f", heapTotalMb))
      put("heap_free_mb", String.format(java.util.Locale.US, "%.1f", heapFreeMb))
      put("native_allocated_mb", String.format(java.util.Locale.US, "%.1f", nativeAllocMb))
      put("native_total_mb", String.format(java.util.Locale.US, "%.1f", nativeTotalMb))
    }.toString()

    RequestLogStore.addEvent(
      "Inference details: ${inputTokens}→${outputTokens} tokens in ${totalMs}ms",
      level = LogLevel.DEBUG,
      modelName = modelName,
      category = EventCategory.SERVER,
      body = body,
    )
  }

  companion object {
    /** Debounce interval for updating the Logs screen preview during streaming inference. */
    private const val LOG_STREAMING_PREVIEW_DEBOUNCE_MS = 300L

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
    fun enrichLlmError(error: String): Pair<String, ErrorKind> {
      val kind = LlmHttpErrorSuggestions.classifyFromString(error)
      val suggestion = LlmHttpErrorSuggestions.suggest(kind)
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
      val match = Regex("(\\d+)\\s*>=\\s*(\\d+)").find(responseBody) ?: return null
      val actual = match.groupValues[1].toLongOrNull() ?: return null
      val max = match.groupValues[2].toLongOrNull() ?: return null
      if (actual <= 0 || max <= 0) return null
      return actual to max
    }
  }
}
