package com.ollitert.llm.server.service

import android.content.Context
import com.ollitert.llm.server.data.CHAT_COMPLETIONS_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RESPONSES_TIMEOUT_SECONDS
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
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
 * Separated from LlmHttpServer to isolate request parsing, prompt compaction,
 * per-request config management, and response building from HTTP routing,
 * auth, CORS, and server control concerns.
 */
class LlmHttpEndpointHandlers(
  private val context: Context,
  private val json: Json,
  private val inferenceRunner: LlmHttpInferenceRunner,
  private val modelLifecycle: LlmHttpModelLifecycle,
  private val logEvent: (String) -> Unit,
  private val logPayload: (label: String, body: String, requestId: String) -> Unit,
  private val nextRequestId: () -> String,
) {

  // ── /generate ────────────────────────────────────────────────────────────

  fun handleGenerate(
    session: NanoHTTPD.IHTTPSession,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
  ): NanoHTTPD.Response {
    val requestId = nextRequestId()
    val payload = HashMap<String, String>()
    session.parseBody(payload)
    val raw = payload["postData"] ?: return badRequest("empty body")
    val parsed = LlmHttpBodyParser.parse(raw) ?: return badRequest("empty body")
    captureBody(parsed.body)
    logPayload("POST /generate raw", parsed.body, requestId)
    val req = json.decodeFromString<GenReq>(parsed.body)
    val model = when (val sel = modelLifecycle.selectModel(null)) {
      is LlmHttpModelLifecycle.ModelSelection.Ok -> sel.model
      is LlmHttpModelLifecycle.ModelSelection.Error -> return jsonError(sel.status, sel.message)
    }
    // Apply prompt compaction for raw prompts (only trimming is possible)
    val trimPromptsGen = LlmHttpPrefs.isAutoTrimPrompts(context)
    val maxContextGen = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
    val compactionResultGen = LlmHttpPromptCompactor.compactRawPrompt(req.prompt, maxContextGen, trimPromptsGen)
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
    logEvent("request_start id=$requestId endpoint=/generate bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} model=default")
    ServerMetrics.onInferenceStarted()
    val (text, llmError) = inferenceRunner.runLlm(model, prompt, requestId, "/generate", logId = logId)
    ServerMetrics.onInferenceCompleted()
    if (text == null) {
      val (enrichedError, kind) = LlmHttpInferenceRunner.enrichLlmError(llmError ?: "llm error")
      ServerMetrics.incrementErrorCount(kind.category)
      return badRequest(enrichedError)
    }
    val promptTokens = estimateTokens(prompt)
    val completionTokens = estimateTokens(text)
    val timings = LlmHttpPayloadBuilders.buildTimings(promptTokens, completionTokens)
    val responseJson = json.encodeToString(GenRes(text = text, usage = Usage(promptTokens, completionTokens), timings = timings))
    captureResponse(responseJson)
    return okJsonText(responseJson)
  }

  // ── /v1/chat/completions ─────────────────────────────────────────────────

  fun handleChatCompletion(
    session: NanoHTTPD.IHTTPSession,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
  ): NanoHTTPD.Response {
    val requestId = nextRequestId()
    val payload = HashMap<String, String>()
    session.parseBody(payload)
    val raw = payload["postData"] ?: return badRequest("empty body")
    val parsed = LlmHttpBodyParser.parse(raw) ?: return badRequest("empty body")
    captureBody(parsed.body)
    logPayload("POST /v1/chat/completions raw", parsed.body, requestId)
    val req = json.decodeFromString<ChatRequest>(parsed.body)
    val toolChoiceStr = LlmHttpRequestAdapter.resolveToolChoice(req.tool_choice)
    if (req.tools.isNullOrEmpty() && toolChoiceStr == "required")
      return badRequest("tool_choice required but tools empty")
    val requestedId = LlmHttpBridgeUtils.resolveRequestedModelId(req.model)
    val model = when (val sel = modelLifecycle.selectModel(req.model)) {
      is LlmHttpModelLifecycle.ModelSelection.Ok -> sel.model
      is LlmHttpModelLifecycle.ModelSelection.Error -> return jsonError(sel.status, sel.message)
    }
    // Build prompt with progressive compaction if context window is exceeded.
    // Three independent toggles for progressive prompt compaction:
    // "Truncate History" (drop older messages), "Compact Tool Schemas" (reduce tool definitions,
    // useful for Home Assistant), "Trim Prompt" (hard-cut as last resort).
    val tools = req.tools.orEmpty()
    val hasTools = tools.isNotEmpty() && toolChoiceStr != "none"
    val truncateHistory = LlmHttpPrefs.isAutoTruncateHistory(context)
    val compactToolSchemas = LlmHttpPrefs.isCompactToolSchemas(context)
    val trimPrompts = LlmHttpPrefs.isAutoTrimPrompts(context)
    val maxContext = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()

    // Insert image placeholder tokens in the prompt when the model supports vision and the
    // request contains image_url parts. This allows the inference layer to interleave
    // Content.Text and Content.ImageBytes at the correct conversation positions.
    val hasImageParts = model.llmSupportImage && req.messages.any { msg ->
      msg.content.parts.any { it.type == "image_url" }
    }

    val compactionResult = LlmHttpPromptCompactor.compactChatPrompt(
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
    var prompt = LlmHttpInferenceRunner.applyResponseFormat(compactionResult.prompt, req.response_format)
    // Store context utilization data in the log entry for per-request display
    val maxCtxChat = (maxContext ?: 0).toLong()
    if (logId != null) {
      val inputEst = estimateTokensLong(prompt)
      RequestLogStore.update(logId) { it.copy(inputTokenEstimate = inputEst, maxContextTokens = maxCtxChat) }
    }
    logPayload("POST /v1/chat/completions prompt", prompt, requestId)
    // Extract images for multimodal models (before blank-prompt check so image-only requests work).
    val images = if (model.llmSupportImage) modelLifecycle.decodeImageDataUris(req.messages) else emptyList()

    logEvent("request_start id=$requestId endpoint=/v1/chat/completions bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} images=${images.size} model=$requestedId resolved=${model.name}")

    if (prompt.isBlank() && images.isEmpty()) {
      logEvent("request_empty id=$requestId endpoint=/v1/chat/completions")
      return okJsonText(json.encodeToString(LlmHttpPayloadBuilders.emptyChatResponse(model.name)))
    }

    val includeUsage = req.stream_options?.include_usage == true
    val effectiveMaxTokens = req.max_completion_tokens ?: req.max_tokens

    // When "Ignore Client Sampler Parameters" is enabled, discard client-supplied
    // temperature/top_p/top_k/max_tokens and use the server's configured values instead.
    val ignoreClientSampler = LlmHttpPrefs.isIgnoreClientSamplerParams(context)
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
    // For streaming: config is applied inside the executor thread (via configSnapshot) to avoid
    // a race where the NanoHTTPD thread restores config before the executor reads it.
    // For non-streaming: withPerRequestConfig wraps the blocking call safely.
    val stopSeqs = req.stop.ifEmpty { null }
    return if (req.stream == true) {
      val configSnapshot = buildPerRequestConfig(model, clientTemp, clientTopP, clientTopK, clientMaxTokens)
      ServerMetrics.onInferenceStarted()
      inferenceRunner.streamChatLlm(model, prompt, requestId, "/v1/chat/completions", timeoutSeconds = CHAT_COMPLETIONS_TIMEOUT_SECONDS, images = images, logId = logId, includeUsage = includeUsage, stopSequences = stopSeqs, tools = if (hasTools) tools else null, configSnapshot = configSnapshot, json = json)
    } else {
      withPerRequestConfig(model, clientTemp, clientTopP, clientTopK, clientMaxTokens) {
        ServerMetrics.onInferenceStarted()
        val (rawText, llmError) = inferenceRunner.runLlm(model, prompt, requestId, "/v1/chat/completions", timeoutSeconds = CHAT_COMPLETIONS_TIMEOUT_SECONDS, images = images, logId = logId)
        ServerMetrics.onInferenceCompleted()
        if (rawText == null) {
          val (errorMsg, kind) = LlmHttpInferenceRunner.enrichLlmError(llmError ?: "llm error")
          ServerMetrics.incrementErrorCount(kind.category)
          if (logId != null) {
            val suggestion = LlmHttpErrorSuggestions.suggest(kind)
            val errorJson = LlmHttpResponseRenderer.renderJsonError(errorMsg, suggestion, kind.category)
            RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR) }
          }
          return@withPerRequestConfig badRequest(errorMsg)
        }
        val (text, _) = LlmHttpInferenceRunner.applyStopSequences(rawText, stopSeqs)

        val promptTokens = estimateTokens(prompt)

        // Check if the model output contains tool call(s) — supports parallel calls
        if (hasTools) {
          val toolCalls = LlmHttpToolCallParser.parseAll(text, tools)
          if (toolCalls.isNotEmpty()) {
            logEvent("request_tool_calls id=$requestId endpoint=/v1/chat/completions tools=${toolCalls.joinToString(",") { it.function.name }} count=${toolCalls.size}")
            val completionTokens = estimateTokens(toolCalls.joinToString("") { it.function.arguments })
            val timings = LlmHttpPayloadBuilders.buildTimings(promptTokens, completionTokens)
            val responseJson = json.encodeToString(LlmHttpPayloadBuilders.chatResponseWithToolCalls(model.name, toolCalls, promptLen = prompt.length, timings = timings))
            captureResponse(responseJson)
            return@withPerRequestConfig okJsonText(responseJson)
          }
        }

        val completionTokens = estimateTokens(text)
        val timings = LlmHttpPayloadBuilders.buildTimings(promptTokens, completionTokens)
        val responseJson = json.encodeToString(LlmHttpPayloadBuilders.chatResponseWithText(model.name, text, promptLen = prompt.length, timings = timings))
        captureResponse(responseJson)
        okJsonText(responseJson)
      }
    }
  }

  // ── /v1/completions ──────────────────────────────────────────────────────

  fun handleCompletions(
    session: NanoHTTPD.IHTTPSession,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
  ): NanoHTTPD.Response {
    val requestId = nextRequestId()
    val payload = HashMap<String, String>()
    session.parseBody(payload)
    val raw = payload["postData"] ?: return badRequest("empty body")
    val parsed = LlmHttpBodyParser.parse(raw) ?: return badRequest("empty body")
    captureBody(parsed.body)
    logPayload("POST /v1/completions raw", parsed.body, requestId)
    val req = json.decodeFromString<CompletionRequest>(parsed.body)
    val model = when (val sel = modelLifecycle.selectModel(req.model)) {
      is LlmHttpModelLifecycle.ModelSelection.Ok -> sel.model
      is LlmHttpModelLifecycle.ModelSelection.Error -> return jsonError(sel.status, sel.message)
    }
    // Apply prompt compaction for raw prompts (only trimming is possible)
    val trimPromptsCompl = LlmHttpPrefs.isAutoTrimPrompts(context)
    val maxContextCompl = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
    val compactionResultCompl = LlmHttpPromptCompactor.compactRawPrompt(req.prompt, maxContextCompl, trimPromptsCompl)
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
    logEvent("request_start id=$requestId endpoint=/v1/completions bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} model=${model.name}")

    if (prompt.isBlank()) {
      val responseJson = json.encodeToString(CompletionResponse(
        id = "cmpl-${java.util.UUID.randomUUID()}",
        created = System.currentTimeMillis() / 1000,
        model = model.name,
        choices = listOf(CompletionChoice(text = "", index = 0, finish_reason = "stop")),
        usage = Usage(0, 0),
      ))
      captureResponse(responseJson)
      return okJsonText(responseJson)
    }

    // Parse stop sequences from JsonElement (can be String or List<String>)
    val stopSequences: List<String>? = when (req.stop) {
      is JsonNull -> null
      is JsonPrimitive -> req.stop.jsonPrimitive.content.takeIf { it.isNotBlank() }?.let { listOf(it) }
      is JsonArray -> req.stop.jsonArray.map { it.jsonPrimitive.content }
      else -> null
    }

    // Apply per-request sampler overrides (ignored when "Ignore Client Sampler" is on)
    val ignoreClientSamplerC = LlmHttpPrefs.isIgnoreClientSamplerParams(context)
    val cTemp = if (ignoreClientSamplerC) null else req.temperature
    val cTopP = if (ignoreClientSamplerC) null else req.top_p
    val cMaxTokens = if (ignoreClientSamplerC) null else req.max_tokens
    if (ignoreClientSamplerC && logId != null) {
      val ignored = describeClientSamplerParams(req.temperature, req.top_p, topK = null, req.max_tokens)
      if (ignored != null) RequestLogStore.update(logId) { it.copy(ignoredClientParams = ignored) }
    }
    return withPerRequestConfig(model, cTemp, cTopP, topK = null, cMaxTokens) {
      // Streaming completions: fall through to non-streaming for now
      ServerMetrics.onInferenceStarted()
      val (rawText, llmError) = inferenceRunner.runLlm(model, prompt, requestId, "/v1/completions", timeoutSeconds = CHAT_COMPLETIONS_TIMEOUT_SECONDS, logId = logId)
      ServerMetrics.onInferenceCompleted()
      if (rawText == null) {
        val (errorMsg, kind) = LlmHttpInferenceRunner.enrichLlmError(llmError ?: "llm error")
        ServerMetrics.incrementErrorCount(kind.category)
        if (logId != null) {
          val suggestion = LlmHttpErrorSuggestions.suggest(kind)
          val errorJson = LlmHttpResponseRenderer.renderJsonError(errorMsg, suggestion, kind.category)
          RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR) }
        }
        return@withPerRequestConfig badRequest(errorMsg)
      }

      val (text, _) = LlmHttpInferenceRunner.applyStopSequences(rawText, stopSequences?.ifEmpty { null })
      val promptTokens = estimateTokens(prompt)
      val completionTokens = estimateTokens(text)
      val timings = LlmHttpPayloadBuilders.buildTimings(promptTokens, completionTokens)
      val responseJson = json.encodeToString(CompletionResponse(
        id = "cmpl-${java.util.UUID.randomUUID()}",
        created = System.currentTimeMillis() / 1000,
        model = model.name,
        choices = listOf(CompletionChoice(text = text, index = 0, finish_reason = "stop")),
        usage = Usage(promptTokens, completionTokens),
        timings = timings,
      ))
      captureResponse(responseJson)
      okJsonText(responseJson)
    }
  }

  // ── /v1/responses ────────────────────────────────────────────────────────

  fun handleResponses(
    session: NanoHTTPD.IHTTPSession,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
  ): NanoHTTPD.Response {
    val requestId = nextRequestId()
    val payload = HashMap<String, String>()
    session.parseBody(payload)
    val raw = payload["postData"] ?: return badRequest("empty body")
    val parsed = LlmHttpBodyParser.parse(raw) ?: return badRequest("empty body")
    captureBody(parsed.body)
    logPayload("POST /v1/responses raw", parsed.body, requestId)
    val req = json.decodeFromString<ResponsesRequest>(parsed.body)
    val toolChoiceStr = LlmHttpRequestAdapter.resolveToolChoice(req.tool_choice)
    if (req.tools.isNullOrEmpty() && toolChoiceStr == "required")
      return badRequest("tool_choice required but tools empty")
    val requestedId = LlmHttpBridgeUtils.resolveRequestedModelId(req.model)
    val model = when (val sel = modelLifecycle.selectModel(req.model)) {
      is LlmHttpModelLifecycle.ModelSelection.Ok -> sel.model
      is LlmHttpModelLifecycle.ModelSelection.Error -> return jsonError(sel.status, sel.message)
    }
    // Build prompt with progressive compaction if context window is exceeded
    val truncateHistoryResp = LlmHttpPrefs.isAutoTruncateHistory(context)
    val trimPromptsResp = LlmHttpPrefs.isAutoTrimPrompts(context)
    val maxContextResp = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
    val compactionResultResp = LlmHttpPromptCompactor.compactConversationPrompt(
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
    logEvent("request_start id=$requestId endpoint=/v1/responses bodyBytes=${parsed.bodyBytes} promptChars=${prompt.length} model=$requestedId resolved=${model.name}")

    if (prompt.isBlank()) {
      logEvent("request_empty id=$requestId endpoint=/v1/responses")
      return emptyResponse(model.name, stream = req.stream == true)
    }

    val tools = req.tools.orEmpty()
    val hasTools = tools.isNotEmpty() && toolChoiceStr != "none"

    // Apply per-request sampler overrides (ignored when "Ignore Client Sampler" is on)
    val ignoreClientSamplerR = LlmHttpPrefs.isIgnoreClientSamplerParams(context)
    val rTemp = if (ignoreClientSamplerR) null else req.temperature
    val rTopP = if (ignoreClientSamplerR) null else req.top_p
    val rTopK = if (ignoreClientSamplerR) null else req.top_k
    val rMaxTokens = if (ignoreClientSamplerR) null else req.max_output_tokens
    if (ignoreClientSamplerR && logId != null) {
      val ignored = describeClientSamplerParams(req.temperature, req.top_p, req.top_k, req.max_output_tokens)
      if (ignored != null) RequestLogStore.update(logId) { it.copy(ignoredClientParams = ignored) }
    }
    // For streaming: config is applied inside the executor thread (via configSnapshot) to avoid
    // a race where the NanoHTTPD thread restores config before the executor reads it.
    // For non-streaming: withPerRequestConfig wraps the blocking call safely.
    return if (req.stream == true) {
      val configSnapshot = buildPerRequestConfig(model, rTemp, rTopP, rTopK, rMaxTokens)
      ServerMetrics.onInferenceStarted()
      inferenceRunner.streamLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = RESPONSES_TIMEOUT_SECONDS, logId = logId, promptLen = prompt.length, configSnapshot = configSnapshot, json = json)
    } else {
      withPerRequestConfig(model, rTemp, rTopP, rTopK, rMaxTokens) {
        ServerMetrics.onInferenceStarted()
        val (text, llmError) = inferenceRunner.runLlm(model, prompt, requestId, "/v1/responses", timeoutSeconds = RESPONSES_TIMEOUT_SECONDS, logId = logId)
        ServerMetrics.onInferenceCompleted()
        if (text == null) {
          val (errorMsg, kind) = LlmHttpInferenceRunner.enrichLlmError(llmError ?: "llm error")
          ServerMetrics.incrementErrorCount(kind.category)
          if (logId != null) {
            val suggestion = LlmHttpErrorSuggestions.suggest(kind)
            val errorJson = LlmHttpResponseRenderer.renderJsonError(errorMsg, suggestion, kind.category)
            RequestLogStore.update(logId) { it.copy(responseBody = errorJson, level = LogLevel.ERROR) }
          }
          return@withPerRequestConfig badRequest(errorMsg)
        }

        // Check if the model output contains tool call(s)
        if (hasTools) {
          val toolCalls = LlmHttpToolCallParser.parseAll(text, tools)
          if (toolCalls.isNotEmpty()) {
            // Responses API: use first tool call (Responses API doesn't batch tool calls the same way)
            val responseJson = json.encodeToString(LlmHttpPayloadBuilders.responsesResponseWithToolCall(model.name, toolCalls.first(), promptLen = prompt.length, json = json))
            captureResponse(responseJson)
            return@withPerRequestConfig okJsonText(responseJson)
          }
        }

        val responseJson = json.encodeToString(LlmHttpPayloadBuilders.responsesResponseWithText(model.name, text, promptLen = prompt.length))
        captureResponse(responseJson)
        okJsonText(responseJson)
      }
    }
  }

  // ── SSE response helpers ─────────────────────────────────────────────────

  /** Empty response for /v1/responses — returns SSE or JSON depending on stream flag. */
  private fun emptyResponse(modelId: String, stream: Boolean): NanoHTTPD.Response {
    val body = LlmHttpPayloadBuilders.responsesResponseWithText(modelId, "")
    return if (stream) {
      // Use chunked SSE (via BlockingQueueInputStream + FlushingSseResponse) to match
      // the transfer encoding that streaming inference responses use.
      val payload = LlmHttpResponseRenderer.buildTextSsePayload(modelId, "")
      val stream = BlockingQueueInputStream()
      stream.enqueue(payload)
      stream.finish()
      FlushingSseResponse(stream)
    } else {
      okJsonText(json.encodeToString(body))
    }
  }

  // ── Per-request config helpers ───────────────────────────────────────────

  /**
   * Builds a human-readable summary of client-supplied sampler params that will be ignored.
   * Returns null if the client didn't send any overrides.
   */
  private fun describeClientSamplerParams(
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
   * Builds a config snapshot with per-request overrides applied.
   * Returns null if no overrides are needed. Used for streaming requests
   * where the config must be applied on the executor thread, not the NanoHTTPD thread.
   */
  private fun buildPerRequestConfig(
    model: Model,
    temperature: Double? = null,
    topP: Double? = null,
    topK: Int? = null,
    maxTokens: Int? = null,
  ): Map<String, Any>? {
    if (temperature == null && topP == null && topK == null && maxTokens == null) return null
    val overridden = model.configValues.toMutableMap()
    temperature?.let { overridden[ConfigKeys.TEMPERATURE.label] = it.toFloat() }
    topP?.let { overridden[ConfigKeys.TOPP.label] = it.toFloat() }
    topK?.let { overridden[ConfigKeys.TOPK.label] = it }
    maxTokens?.let {
      val engineMax = (model.configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
      if (engineMax != null) {
        overridden[ConfigKeys.MAX_TOKENS.label] = it.coerceAtMost(engineMax)
      } else {
        overridden[ConfigKeys.MAX_TOKENS.label] = it
      }
    }
    return overridden
  }

  /**
   * Temporarily overrides model sampler config for the duration of a BLOCKING request.
   * For streaming requests, use [buildPerRequestConfig] + configSnapshot parameter instead,
   * since the config must be applied on the executor thread (not the NanoHTTPD thread).
   */
  private inline fun <R> withPerRequestConfig(
    model: Model,
    temperature: Double? = null,
    topP: Double? = null,
    topK: Int? = null,
    maxTokens: Int? = null,
    block: () -> R,
  ): R {
    if (temperature == null && topP == null && topK == null && maxTokens == null) return block()
    val originalConfig = model.configValues
    try {
      val overridden = originalConfig.toMutableMap()
      temperature?.let { overridden[ConfigKeys.TEMPERATURE.label] = it.toFloat() }
      topP?.let { overridden[ConfigKeys.TOPP.label] = it.toFloat() }
      topK?.let { overridden[ConfigKeys.TOPK.label] = it }
      maxTokens?.let {
        // Cap to engine's configured max to avoid exceeding EngineConfig.maxNumTokens
        val engineMax = (originalConfig[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt()
        if (engineMax != null) {
          overridden[ConfigKeys.MAX_TOKENS.label] = it.coerceAtMost(engineMax)
        } else {
          overridden[ConfigKeys.MAX_TOKENS.label] = it
        }
      }
      model.configValues = overridden
      return block()
    } finally {
      model.configValues = originalConfig
    }
  }

}
