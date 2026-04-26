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
import com.ollitert.llm.server.data.CHAT_COMPLETIONS_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.MAX_MAX_TOKENS
import com.ollitert.llm.server.data.MAX_TEMPERATURE
import com.ollitert.llm.server.data.MAX_TOPK
import com.ollitert.llm.server.data.MAX_TOPP
import com.ollitert.llm.server.data.MIN_MAX_TOKENS
import com.ollitert.llm.server.data.MIN_TEMPERATURE
import com.ollitert.llm.server.data.MIN_TOPK
import com.ollitert.llm.server.data.MIN_TOPP
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RESPONSES_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.llmSupportAudio
import com.ollitert.llm.server.data.llmSupportImage
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles the four inference API endpoints:
 * - POST /generate
 * - POST /v1/chat/completions
 * - POST /v1/completions
 * - POST /v1/responses
 *
 * Separated from KtorServer to isolate request parsing, prompt compaction,
 * per-request config management, and response building from HTTP routing,
 * auth, CORS, and server control concerns.
 */
class EndpointHandlers(
  private val context: Context,
  private val json: Json,
  private val inferenceRunner: InferenceRunner,
  private val modelLifecycle: ModelLifecycle,
  private val logEvent: (String) -> Unit,
  private val logPayload: (label: String, body: String, requestId: String) -> Unit,
  private val nextRequestId: () -> String,
) {

  // ── /generate ────────────────────────────────────────────────────────────

  suspend fun handleGenerate(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
  ): HttpResponse {
    val requestId = nextRequestId()
    captureBody(body)
    logPayload("POST /generate raw", body, requestId)
    val req = try { json.decodeFromString<GenReq>(body) }
      catch (e: SerializationException) { return httpBadRequest("Invalid JSON: ${e.message}") }
    val model = when (val sel = modelLifecycle.selectModel(null)) {
      is ModelLifecycle.ModelSelection.Ok -> sel.model
      is ModelLifecycle.ModelSelection.Error -> return HttpResponse.Json(
        statusCode = sel.statusCode,
        body = ResponseRenderer.renderJsonError(sel.message),
        extraHeaders = buildMap { sel.retryAfterSeconds?.let { put("Retry-After", it.toString()) } },
      )
    }
    // Raw prompts have no message structure, so history truncation and tool schema compaction
    // aren't possible — only hard string trimming can reduce the prompt size.
    val trimPromptsGen = ServerPrefs.isAutoTrimPrompts(context)
    val maxContextGen = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
    val compactionResultGen = PromptCompactor.compactRawPrompt(req.prompt, maxContextGen, trimPromptsGen)
    if (compactionResultGen.compacted) {
      val details = compactionResultGen.strategies.joinToString(", ")
      logEvent("prompt_compacted id=$requestId endpoint=/generate strategies=[$details]")
      if (logId != null) {
        RequestLogStore.update(logId) { it.copy(isCompacted = true, compactionDetails = details, compactedPrompt = compactionResultGen.prompt) }
      }
    }
    val prompt = compactionResultGen.prompt
    // Store context utilization data in the log entry for per-request display
    val maxCtxGen = (maxContextGen ?: 0).toLong()
    if (logId != null) {
      val inputEst = estimateTokensLong(prompt)
      RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtxGen) }
    }
    logPayload("POST /generate prompt", prompt, requestId)
    logEvent("request_start id=$requestId endpoint=/generate bodyLength=${body.length} promptChars=${prompt.length} model=default")
    ServerMetrics.onInferenceStarted()
    val (text, llmError) = inferenceRunner.runLlm(model, prompt, requestId, "/generate", logId = logId)
    ServerMetrics.onInferenceCompleted()
    if (text == null) {
      val (enrichedError, kind) = InferenceRunner.enrichLlmError(llmError ?: "llm error", context)
      ServerMetrics.incrementErrorCount(kind.category)
      return httpBadRequest(enrichedError)
    }
    val promptTokens = estimateTokens(prompt)
    val completionTokens = estimateTokens(text)
    val timings = PayloadBuilders.buildTimings(promptTokens, completionTokens)
    val responseJson = json.encodeToString(GenRes(text = text, usage = Usage(promptTokens, completionTokens), timings = timings))
    captureResponse(responseJson)
    return httpOkJson(responseJson)
  }

  // ── /v1/chat/completions ─────────────────────────────────────────────────

  suspend fun handleChatCompletion(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
  ): HttpResponse {
    val requestId = nextRequestId()
    captureBody(body)
    logPayload("POST /v1/chat/completions raw", body, requestId)
    val req = try { json.decodeFromString<ChatRequest>(body) }
      catch (e: SerializationException) { return httpBadRequest("Invalid JSON: ${e.message}") }
    val toolChoiceStr = PromptBuilder.resolveToolChoice(req.tool_choice)
    if (req.tools.isNullOrEmpty() && toolChoiceStr == "required")
      return httpBadRequest("tool_choice required but tools empty")
    val requestedId = BridgeUtils.resolveRequestedModelId(req.model)
    val model = when (val sel = modelLifecycle.selectModel(req.model)) {
      is ModelLifecycle.ModelSelection.Ok -> sel.model
      is ModelLifecycle.ModelSelection.Error -> return HttpResponse.Json(
        statusCode = sel.statusCode,
        body = ResponseRenderer.renderJsonError(sel.message),
        extraHeaders = buildMap { sel.retryAfterSeconds?.let { put("Retry-After", it.toString()) } },
      )
    }
    // Build prompt with progressive compaction if context window is exceeded.
    // Three independent toggles for progressive prompt compaction:
    // "Truncate History" (drop older messages), "Compact Tool Schemas" (reduce tool definitions,
    // useful for Home Assistant), "Trim Prompt" (hard-cut as last resort).
    val tools = req.tools.orEmpty()
    val hasTools = tools.isNotEmpty() && toolChoiceStr != "none"
    val truncateHistory = ServerPrefs.isAutoTruncateHistory(context)
    val compactToolSchemas = ServerPrefs.isCompactToolSchemas(context)
    val trimPrompts = ServerPrefs.isAutoTrimPrompts(context)
    val maxContext = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()

    // Insert image placeholder tokens in the prompt when the model supports vision and the
    // request contains image_url parts. This allows the inference layer to interleave
    // Content.Text and Content.ImageBytes at the correct conversation positions.
    val hasImageParts = model.llmSupportImage && req.messages.any { msg ->
      msg.content.parts.any { it.type == "image_url" }
    }

    val compactionResult = PromptCompactor.compactChatPrompt(
      messages = req.messages,
      tools = if (hasTools) tools else null,
      toolChoice = toolChoiceStr,
      chatTemplate = null,
      maxContext = maxContext,
      truncateHistory = truncateHistory,
      compactToolSchemas = compactToolSchemas,
      trimPrompts = trimPrompts,
      interleaveImagePlaceholders = hasImageParts,
    )

    if (compactionResult.compacted) {
      val details = compactionResult.strategies.joinToString(", ")
      logEvent("prompt_compacted id=$requestId endpoint=/v1/chat/completions strategies=[$details] estimatedTokens=${estimateTokens(compactionResult.prompt)} maxContext=$maxContext")
      if (logId != null) {
        RequestLogStore.update(logId) { it.copy(isCompacted = true, compactionDetails = details, compactedPrompt = compactionResult.prompt) }
      }
    }

    // Apply response_format JSON mode prompt injection
    var prompt = InferenceRunner.applyResponseFormat(compactionResult.prompt, req.response_format)
    // Store context utilization data in the log entry for per-request display
    val maxCtxChat = (maxContext ?: 0).toLong()
    if (logId != null) {
      val inputEst = estimateTokensLong(prompt)
      RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtxChat) }
    }
    logPayload("POST /v1/chat/completions prompt", prompt, requestId)
    // Extract images for multimodal models (before blank-prompt check so image-only requests work).
    val images = if (model.llmSupportImage) modelLifecycle.decodeImageDataUris(req.messages) else emptyList()
    // Extract audio clips for models that support audio input. Models that don't support audio
    // silently receive an empty list — same as the image handling pattern above.
    val audioClips = if (model.llmSupportAudio) {
      val audioData = PromptBuilder.extractAudioData(req.messages)
      modelLifecycle.decodeAudioData(audioData)
    } else emptyList()

    logEvent("request_start id=$requestId endpoint=/v1/chat/completions bodyLength=${body.length} promptChars=${prompt.length} images=${images.size} audio=${audioClips.size} model=$requestedId resolved=${model.name}")

    if (prompt.isBlank() && images.isEmpty() && audioClips.isEmpty()) {
      logEvent("request_empty id=$requestId endpoint=/v1/chat/completions")
      return emptyChatResponse(model.name, stream = req.stream == true)
    }

    val includeUsage = req.stream_options?.include_usage == true
    val effectiveMaxTokens = req.max_completion_tokens ?: req.max_tokens

    // When "Ignore Client Sampler Parameters" is enabled, discard client-supplied
    // temperature/top_p/top_k/max_tokens and use the server's configured values instead.
    val ignoreClientSampler = ServerPrefs.isIgnoreClientSamplerParams(context)
    val clientTemp = if (ignoreClientSampler) null else req.temperature
    val clientTopP = if (ignoreClientSampler) null else req.top_p
    val clientTopK = if (ignoreClientSampler) null else req.top_k
    val clientMaxTokens = if (ignoreClientSampler) null else effectiveMaxTokens
    if (ignoreClientSampler && logId != null) {
      val ignored = describeClientSamplerParams(req.temperature, req.top_p, req.top_k, effectiveMaxTokens)
      if (ignored != null) RequestLogStore.update(logId) { it.copy(ignoredClientParams = ignored) }
    }

    // Apply per-request sampler overrides (temperature, top_p, top_k, max_tokens).
    // These are picked up by resetConversation() before inference — no model reload needed.
    // Config is applied inside the executor thread (via configSnapshot) to avoid a race
    // where the calling thread restores config before the executor reads it.
    val stopSeqs = req.stop.ifEmpty { null }
    return if (req.stream == true) {
      val configSnapshot = buildPerRequestConfig(model, clientTemp, clientTopP, clientTopK, clientMaxTokens)
      ServerMetrics.onInferenceStarted()
      inferenceRunner.streamChatLlm(model, prompt, requestId, "/v1/chat/completions", timeoutSeconds = CHAT_COMPLETIONS_TIMEOUT_SECONDS, images = images, audioClips = audioClips, logId = logId, includeUsage = includeUsage, stopSequences = stopSeqs, tools = if (hasTools) tools else null, configSnapshot = configSnapshot, json = json)
    } else {
      val configSnapshotBlocking = buildPerRequestConfig(model, clientTemp, clientTopP, clientTopK, clientMaxTokens)
      ServerMetrics.onInferenceStarted()
      val (rawText, llmError) = inferenceRunner.runLlm(model, prompt, requestId, "/v1/chat/completions", timeoutSeconds = CHAT_COMPLETIONS_TIMEOUT_SECONDS, images = images, audioClips = audioClips, logId = logId, configSnapshot = configSnapshotBlocking)
      ServerMetrics.onInferenceCompleted()
      if (rawText == null) {
        val (errorMsg, kind) = InferenceRunner.enrichLlmError(llmError ?: "llm error", context)
        ServerMetrics.incrementErrorCount(kind.category)
        if (logId != null) {
          val suggestion = ErrorSuggestions.suggest(kind, context)
          val errorJson = ResponseRenderer.renderJsonError(errorMsg, suggestion, kind)
          RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR, errorKind = kind) }
        }
        return httpBadRequest(errorMsg)
      }
      val (text, _) = InferenceRunner.applyStopSequences(rawText, stopSeqs)

      val promptTokens = estimateTokens(prompt)

      // Check if the model output contains tool call(s) — supports parallel calls
      if (hasTools) {
        val toolCalls = ToolCallParser.parseAll(text, tools)
        if (toolCalls.isNotEmpty()) {
          logEvent("request_tool_calls id=$requestId endpoint=/v1/chat/completions tools=${toolCalls.joinToString(",") { it.function.name }} count=${toolCalls.size}")
          val completionTokens = estimateTokens(toolCalls.joinToString("") { it.function.arguments })
          val timings = PayloadBuilders.buildTimings(promptTokens, completionTokens)
          val responseJson = json.encodeToString(PayloadBuilders.chatResponseWithToolCalls(model.name, toolCalls, promptLen = prompt.length, timings = timings))
          captureResponse(responseJson)
          return httpOkJson(responseJson)
        }
      }

      val completionTokens = estimateTokens(text)
      val effectiveMax = (configSnapshotBlocking ?: model.configValues)[ConfigKeys.MAX_TOKENS.label] as? Number
      val finishReason = FinishReason.infer(completionTokens, effectiveMax?.toInt())
      val timings = PayloadBuilders.buildTimings(promptTokens, completionTokens)
      val responseJson = json.encodeToString(PayloadBuilders.chatResponseWithText(model.name, text, promptLen = prompt.length, finishReason = finishReason, timings = timings))
      captureResponse(responseJson)
      httpOkJson(responseJson)
    }
  }

  // ── /v1/completions ──────────────────────────────────────────────────────

  suspend fun handleCompletions(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
  ): HttpResponse {
    val requestId = nextRequestId()
    captureBody(body)
    logPayload("POST /v1/completions raw", body, requestId)
    val req = try { json.decodeFromString<CompletionRequest>(body) }
      catch (e: SerializationException) { return httpBadRequest("Invalid JSON: ${e.message}") }
    val model = when (val sel = modelLifecycle.selectModel(req.model)) {
      is ModelLifecycle.ModelSelection.Ok -> sel.model
      is ModelLifecycle.ModelSelection.Error -> return HttpResponse.Json(
        statusCode = sel.statusCode,
        body = ResponseRenderer.renderJsonError(sel.message),
        extraHeaders = buildMap { sel.retryAfterSeconds?.let { put("Retry-After", it.toString()) } },
      )
    }
    // Raw prompts have no message structure, so history truncation and tool schema compaction
    // aren't possible — only hard string trimming can reduce the prompt size.
    val trimPromptsCompl = ServerPrefs.isAutoTrimPrompts(context)
    val maxContextCompl = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
    val compactionResultCompl = PromptCompactor.compactRawPrompt(req.prompt, maxContextCompl, trimPromptsCompl)
    if (compactionResultCompl.compacted) {
      val details = compactionResultCompl.strategies.joinToString(", ")
      logEvent("prompt_compacted id=$requestId endpoint=/v1/completions strategies=[$details] estimatedTokens=${estimateTokens(compactionResultCompl.prompt)} maxContext=$maxContextCompl")
      if (logId != null) {
        RequestLogStore.update(logId) { it.copy(isCompacted = true, compactionDetails = details, compactedPrompt = compactionResultCompl.prompt) }
      }
    }
    val prompt = compactionResultCompl.prompt
    // Store context utilization data in the log entry for per-request display
    val maxCtxCompl = (maxContextCompl ?: 0).toLong()
    if (logId != null) {
      val inputEst = estimateTokensLong(prompt)
      RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtxCompl) }
    }
    logEvent("request_start id=$requestId endpoint=/v1/completions bodyLength=${body.length} promptChars=${prompt.length} model=${model.name}")

    if (prompt.isBlank()) {
      val responseJson = json.encodeToString(CompletionResponse(
        id = BridgeUtils.generateCompletionId(),
        created = BridgeUtils.epochSeconds(),
        model = model.name,
        choices = listOf(CompletionChoice(text = "", index = 0, finish_reason = FinishReason.STOP)),
        usage = Usage(0, 0),
      ))
      captureResponse(responseJson)
      return httpOkJson(responseJson)
    }

    // OpenAI spec allows `"stop": "text"` (single string) or `"stop": ["a","b"]` (array).
    val stopSequences: List<String>? = when (req.stop) {
      is JsonNull -> null
      is JsonPrimitive -> req.stop.jsonPrimitive.content.takeIf { it.isNotBlank() }?.let { listOf(it) }
      is JsonArray -> req.stop.jsonArray.map { it.jsonPrimitive.content }
      else -> null
    }

    // Apply per-request sampler overrides (ignored when "Ignore Client Sampler" is on)
    val ignoreClientSamplerC = ServerPrefs.isIgnoreClientSamplerParams(context)
    val cTemp = if (ignoreClientSamplerC) null else req.temperature
    val cTopP = if (ignoreClientSamplerC) null else req.top_p
    val cMaxTokens = if (ignoreClientSamplerC) null else req.max_tokens
    if (ignoreClientSamplerC && logId != null) {
      val ignored = describeClientSamplerParams(req.temperature, req.top_p, topK = null, req.max_tokens)
      if (ignored != null) RequestLogStore.update(logId) { it.copy(ignoredClientParams = ignored) }
    }
    val configSnapshotBlocking = buildPerRequestConfig(model, cTemp, cTopP, topK = null, cMaxTokens)
    ServerMetrics.onInferenceStarted()
    val (rawText, llmError) = inferenceRunner.runLlm(model, prompt, requestId, "/v1/completions", timeoutSeconds = CHAT_COMPLETIONS_TIMEOUT_SECONDS, logId = logId, configSnapshot = configSnapshotBlocking)
    ServerMetrics.onInferenceCompleted()
    if (rawText == null) {
      val (errorMsg, kind) = InferenceRunner.enrichLlmError(llmError ?: "llm error", context)
      ServerMetrics.incrementErrorCount(kind.category)
      if (logId != null) {
        val suggestion = ErrorSuggestions.suggest(kind, context)
        val errorJson = ResponseRenderer.renderJsonError(errorMsg, suggestion, kind)
        RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR, errorKind = kind) }
      }
      return httpBadRequest(errorMsg)
    }

    val (text, _) = InferenceRunner.applyStopSequences(rawText, stopSequences?.ifEmpty { null })
    val promptTokens = estimateTokens(prompt)
    val completionTokens = estimateTokens(text)
    val effectiveMaxCompl = (configSnapshotBlocking ?: model.configValues)[ConfigKeys.MAX_TOKENS.label] as? Number
    val finishReasonCompl = FinishReason.infer(completionTokens, effectiveMaxCompl?.toInt())
    val timings = PayloadBuilders.buildTimings(promptTokens, completionTokens)
    val responseJson = json.encodeToString(CompletionResponse(
      id = BridgeUtils.generateCompletionId(),
      created = BridgeUtils.epochSeconds(),
      model = model.name,
      choices = listOf(CompletionChoice(text = text, index = 0, finish_reason = finishReasonCompl)),
      usage = Usage(promptTokens, completionTokens),
      timings = timings,
    ))
    captureResponse(responseJson)
    return httpOkJson(responseJson)
  }

  // ── /v1/responses ────────────────────────────────────────────────────────

  suspend fun handleResponses(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
  ): HttpResponse {
    val requestId = nextRequestId()
    captureBody(body)
    logPayload("POST /v1/responses raw", body, requestId)
    val req = try { json.decodeFromString<ResponsesRequest>(body) }
      catch (e: SerializationException) { return httpBadRequest("Invalid JSON: ${e.message}") }
    val toolChoiceStr = PromptBuilder.resolveToolChoice(req.tool_choice)
    if (req.tools.isNullOrEmpty() && toolChoiceStr == "required")
      return httpBadRequest("tool_choice required but tools empty")
    val requestedId = BridgeUtils.resolveRequestedModelId(req.model)
    val model = when (val sel = modelLifecycle.selectModel(req.model)) {
      is ModelLifecycle.ModelSelection.Ok -> sel.model
      is ModelLifecycle.ModelSelection.Error -> return HttpResponse.Json(
        statusCode = sel.statusCode,
        body = ResponseRenderer.renderJsonError(sel.message),
        extraHeaders = buildMap { sel.retryAfterSeconds?.let { put("Retry-After", it.toString()) } },
      )
    }
    // Build prompt with progressive compaction if context window is exceeded
    val truncateHistoryResp = ServerPrefs.isAutoTruncateHistory(context)
    val trimPromptsResp = ServerPrefs.isAutoTrimPrompts(context)
    val maxContextResp = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
    val compactionResultResp = PromptCompactor.compactConversationPrompt(
      messages = req.messages ?: req.input,
      chatTemplate = null,
      maxContext = maxContextResp,
      truncateHistory = truncateHistoryResp,
      trimPrompts = trimPromptsResp,
    )
    if (compactionResultResp.compacted) {
      val details = compactionResultResp.strategies.joinToString(", ")
      logEvent("prompt_compacted id=$requestId endpoint=/v1/responses strategies=[$details] estimatedTokens=${estimateTokens(compactionResultResp.prompt)} maxContext=$maxContextResp")
      if (logId != null) {
        RequestLogStore.update(logId) { it.copy(isCompacted = true, compactionDetails = details, compactedPrompt = compactionResultResp.prompt) }
      }
    }
    val prompt = compactionResultResp.prompt
    // Store context utilization data in the log entry for per-request display
    val maxCtxResp = (maxContextResp ?: 0).toLong()
    if (logId != null) {
      val inputEst = estimateTokensLong(prompt)
      RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtxResp) }
    }
    logPayload("POST /v1/responses prompt", prompt, requestId)
    logEvent("request_start id=$requestId endpoint=/v1/responses bodyLength=${body.length} promptChars=${prompt.length} model=$requestedId resolved=${model.name}")

    if (prompt.isBlank()) {
      logEvent("request_empty id=$requestId endpoint=/v1/responses")
      return emptyResponse(model.name, stream = req.stream == true)
    }

    val tools = req.tools.orEmpty()
    val hasTools = tools.isNotEmpty() && toolChoiceStr != "none"

    // Apply per-request sampler overrides (ignored when "Ignore Client Sampler" is on)
    val ignoreClientSamplerR = ServerPrefs.isIgnoreClientSamplerParams(context)
    val rTemp = if (ignoreClientSamplerR) null else req.temperature
    val rTopP = if (ignoreClientSamplerR) null else req.top_p
    val rTopK = if (ignoreClientSamplerR) null else req.top_k
    val rMaxTokens = if (ignoreClientSamplerR) null else req.max_output_tokens
    if (ignoreClientSamplerR && logId != null) {
      val ignored = describeClientSamplerParams(req.temperature, req.top_p, req.top_k, req.max_output_tokens)
      if (ignored != null) RequestLogStore.update(logId) { it.copy(ignoredClientParams = ignored) }
    }
    // For streaming: config is applied inside the executor thread (via configSnapshot) to avoid
    // a race where the calling thread restores config before the executor reads it.
    // For non-streaming: config is also applied via configSnapshot on the executor thread.
    return if (req.stream == true) {
      val configSnapshot = buildPerRequestConfig(model, rTemp, rTopP, rTopK, rMaxTokens)
      ServerMetrics.onInferenceStarted()
      inferenceRunner.streamLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = RESPONSES_TIMEOUT_SECONDS, logId = logId, configSnapshot = configSnapshot, json = json, tools = if (hasTools) tools else null)
    } else {
      val configSnapshotBlocking = buildPerRequestConfig(model, rTemp, rTopP, rTopK, rMaxTokens)
      ServerMetrics.onInferenceStarted()
      val (text, llmError) = inferenceRunner.runLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = RESPONSES_TIMEOUT_SECONDS, logId = logId, configSnapshot = configSnapshotBlocking)
      ServerMetrics.onInferenceCompleted()
      if (text == null) {
        val (errorMsg, kind) = InferenceRunner.enrichLlmError(llmError ?: "llm error", context)
        ServerMetrics.incrementErrorCount(kind.category)
        if (logId != null) {
          val suggestion = ErrorSuggestions.suggest(kind, context)
          val errorJson = ResponseRenderer.renderJsonError(errorMsg, suggestion, kind)
          RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR, errorKind = kind) }
        }
        return httpBadRequest(errorMsg)
      }

      // Check if the model output contains tool call(s)
      if (hasTools) {
        val toolCalls = ToolCallParser.parseAll(text, tools)
        if (toolCalls.isNotEmpty()) {
          val responseJson = json.encodeToString(PayloadBuilders.responsesResponseWithToolCalls(model.name, toolCalls, promptLen = prompt.length))
          captureResponse(responseJson)
          return httpOkJson(responseJson)
        }
      }

      val responseJson = json.encodeToString(PayloadBuilders.responsesResponseWithText(model.name, text, promptLen = prompt.length))
      captureResponse(responseJson)
      httpOkJson(responseJson)
    }
  }

  // ── SSE response helpers ─────────────────────────────────────────────────

  /** Empty response for /v1/chat/completions — returns SSE or JSON depending on stream flag. */
  private fun emptyChatResponse(modelId: String, stream: Boolean): HttpResponse {
    return if (stream) {
      val chatId = "chatcmpl-${java.util.UUID.randomUUID()}"
      val now = System.currentTimeMillis() / 1000
      val payload = ResponseRenderer.buildChatStreamFirstChunk(chatId, modelId, now) +
        ResponseRenderer.buildChatStreamFinalChunk(chatId, modelId, now) +
        ResponseRenderer.SSE_DONE
      HttpResponse.Sse { writer ->
        writer.emit(payload)
        writer.finish()
      }
    } else {
      httpOkJson(json.encodeToString(PayloadBuilders.emptyChatResponse(modelId)))
    }
  }

  /** Empty response for /v1/responses — returns SSE or JSON depending on stream flag. */
  private fun emptyResponse(modelId: String, stream: Boolean): HttpResponse {
    val body = PayloadBuilders.responsesResponseWithText(modelId, "")
    return if (stream) {
      val payload = ResponseRenderer.buildTextSsePayload(modelId, "")
      HttpResponse.Sse { writer ->
        writer.emit(payload)
        writer.finish()
      }
    } else {
      httpOkJson(json.encodeToString(body))
    }
  }

  // ── Per-request config helpers ───────────────────────────────────────────

  /**
   * Builds a human-readable summary of client-supplied sampler params that will be ignored.
   * Returns null if the client didn't send any overrides.
   */
}

internal fun describeClientSamplerParams(
  temperature: Double?,
  topP: Double?,
  topK: Int?,
  maxTokens: Int?,
): String? {
  val parts = mutableListOf<String>()
  temperature?.let { parts.add("temperature=$it") }
  topP?.let { parts.add("top_p=$it") }
  topK?.let { parts.add("top_k=$it") }
  maxTokens?.let { parts.add("max_tokens=$it") }
  return if (parts.isEmpty()) null else parts.joinToString(", ")
}

/**
 * Builds a config snapshot with per-request sampler overrides applied.
 * Returns null if no overrides are needed. Extracted as a top-level function
 * so multiple endpoint handlers (chat completions, transcription) can share it.
 * Used for streaming requests where the config must be applied on the executor
 * thread, not the request-handling thread.
 */
internal fun buildPerRequestConfig(
  model: Model,
  temperature: Double? = null,
  topP: Double? = null,
  topK: Int? = null,
  maxTokens: Int? = null,
): Map<String, Any>? {
  if (temperature == null && topP == null && topK == null && maxTokens == null) return null
  val overridden = model.configValues.toMutableMap()
  temperature?.let {
    overridden[ConfigKeys.TEMPERATURE.label] = it.toFloat().coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)
  }
  topP?.let {
    overridden[ConfigKeys.TOPP.label] = it.toFloat().coerceIn(MIN_TOPP, MAX_TOPP)
  }
  topK?.let {
    overridden[ConfigKeys.TOPK.label] = it.coerceIn(MIN_TOPK, MAX_TOPK)
  }
  maxTokens?.let {
    val clamped = it.coerceIn(MIN_MAX_TOKENS, MAX_MAX_TOKENS)
    val engineMax = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
    overridden[ConfigKeys.MAX_TOKENS.label] = if (engineMax != null) clamped.coerceAtMost(engineMax) else clamped
  }
  return overridden.toMap()
}
