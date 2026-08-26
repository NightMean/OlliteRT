/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.service.http

import android.content.Context
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.model.*
import com.ollitert.llm.server.data.allowlist.*
import com.ollitert.llm.server.data.storage.*
import com.ollitert.llm.server.data.repository.*
import com.ollitert.llm.server.data.prefs.*
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.inference.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Result of the shared chat pipeline, including protocol-neutral completion metadata. */
internal data class ChatCompletionExecution(
  val response: HttpResponse,
  val matchedStopSequence: String? = null,
)

internal class ChatCompletionsHandler(
  private val context: Context,
  private val json: Json,
  private val inferenceRunner: InferenceRunner,
  private val modelLifecycle: ModelLifecycle,
  private val logEvent: (String) -> Unit,
  private val nextRequestId: () -> String,
) {

  suspend fun handleChatCompletion(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse {
    captureBody(body)
    val req = try {
      json.decodeFromString<ChatRequest>(body)
    } catch (e: SerializationException) {
      return httpBadRequest("Invalid JSON: ${e.message}")
    }
    return runChatCompletion(
      req = req,
      captureResponse = captureResponse,
      logId = logId,
      prefs = prefs,
      suppressPerModelSystem = false,
      bodyLength = body.length,
      endpoint = "/v1/chat/completions",
    ).response
  }

  /**
   * Core chat-completion pipeline. Extracted so the Anthropic /v1/messages handler can
   * convert its request to ChatRequest and reuse the entire prompt-compaction →
   * inference → response-shaping flow without duplicating logic.
   *
   * Body capture is the caller's responsibility — the captured string is endpoint-specific
   * (raw OAI body for /v1/chat/completions, raw Anthropic body for /v1/messages).
   */
  suspend fun runChatCompletion(
    req: ChatRequest,
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
    suppressPerModelSystem: Boolean = false,
    bodyLength: Int = 0,
    endpoint: String = "/v1/chat/completions",
    // When true the streaming branch returns an Anthropic SSE response built by
    // streamMessagesLlm instead of the OAI streamChatLlm. The non-stream branch
    // is unaffected — the Anthropic handler re-shapes the OAI JSON response itself.
    useAnthropicStream: Boolean = false,
    // Per-request thinking override (null falls back to model.isThinkingEnabled).
    enableThinkingOverride: Boolean? = null,
    // Reasoning token budget override (Anthropic thinking.budget_tokens) applied
    // via the native thinking channel. Implies thinking enabled for this turn.
    thinkingBudgetTokens: Int? = null,
    // OpenAI frequency/presence penalties, already gated and clamped by the caller.
    frequencyPenalty: Double? = null,
    presencePenalty: Double? = null,
  ): ChatCompletionExecution {
    val requestId = nextRequestId()
    validateNParam(req.n)?.let { (param, msg) ->
      logEvent("request_rejected id=$requestId endpoint=$endpoint param=$param value=${req.n}")
      return ChatCompletionExecution(httpBadRequest(msg))
    }
    val toolChoiceStr = PromptBuilder.resolveToolChoice(req.tool_choice)
    if (req.tools.isNullOrEmpty() && toolChoiceStr == "required") {
      return ChatCompletionExecution(httpBadRequest("tool_choice required but tools empty"))
    }
    val requestedId = BridgeUtils.resolveRequestedModelId(req.model)
    val model = when (val sel = modelLifecycle.selectModel(req.model)) {
      is ModelLifecycle.ModelSelection.Ok -> sel.model
      is ModelLifecycle.ModelSelection.Error -> return ChatCompletionExecution(sel.toHttpResponse())
    }
    // Build prompt with progressive compaction if context window is exceeded.
    // Two independent toggles: "Truncate History" (drop older messages) and
    // "Trim Prompt" (hard-cut as last resort).
    val tools = req.tools.orEmpty()
    val hasTools = tools.isNotEmpty() && toolChoiceStr != "none"
    val useSchemaInjection = hasTools && prefs.schemaInjectionToolCalling
    val schemaInjectionProviders = if (useSchemaInjection) SchemaInjectionBridge.toolSpecsToProviders(tools) else emptyList()
    val schemaInjectionMessages = if (useSchemaInjection) SchemaInjectionBridge.buildInitialMessages(req.messages) else emptyList()
    val truncateHistory = prefs.autoTruncateHistory
    val trimPrompts = prefs.autoTrimPrompts
    val maxContext = model.maxContextTokens

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
      trimPrompts = trimPrompts,
      interleaveImagePlaceholders = hasImageParts,
    )

    logCompactionResult(compactionResult, requestId, endpoint, logId, maxContext, logEvent, compactionLogUpdater(logId))

    // json_schema requests with an actual schema use native constrained decoding
    // (engine enforces valid JSON) — the prompt hint is skipped for them since it
    // would only fight the grammar constraint.
    val responseFormatSchema = req.response_format?.constrainedJsonSchema()
    // OpenAI penalties → native RepetitionPenaltyConfig. Honors the "ignore client
    // sampler params" setting; clamped to the OpenAI spec range [-2, 2].
    val clientPenaltiesActive = !prefs.ignoreClientSamplerParams
    val frequencyPenalty = req.frequency_penalty?.coerceIn(-2.0, 2.0)?.takeIf { clientPenaltiesActive && it != 0.0 }
    val presencePenalty = req.presence_penalty?.coerceIn(-2.0, 2.0)?.takeIf { clientPenaltiesActive && it != 0.0 }
    // Apply response_format JSON mode prompt injection
    val prompt = if (useSchemaInjection) {
      InferenceRunner.applyResponseFormat(SchemaInjectionBridge.buildLastUserInput(req.messages), req.response_format)
    } else {
      InferenceRunner.applyResponseFormat(compactionResult.prompt, req.response_format)
    }
    // Store context utilization data in the log entry for per-request display
    recordContextUtilization(logId, prompt, maxContext)
    // Extract images for multimodal models (before blank-prompt check so image-only requests work).
    val images = if (model.llmSupportImage) modelLifecycle.decodeImageDataUris(req.messages) else emptyList()
    // Extract audio clips for models that support audio input. Models that don't support audio
    // silently receive an empty list — same as the image handling pattern above.
    val audioClips = if (model.llmSupportAudio) {
      val audioData = PromptBuilder.extractAudioData(req.messages)
      modelLifecycle.decodeAudioData(audioData)
    } else emptyList()

    val effectiveThinkingBudget = thinkingBudgetTokens
      ?: model.configValues.thinkingBudgetTokens()?.takeIf { enableThinkingOverride != false && model.isThinkingEnabled }

    logEvent("request_start id=$requestId endpoint=$endpoint bodyLength=$bodyLength promptChars=${prompt.length} images=${images.size} audio=${audioClips.size} model=$requestedId resolved=${model.name}" +
      (effectiveThinkingBudget?.let { " thinkBudget=$it" } ?: ""))

    if (prompt.isBlank() && images.isEmpty() && audioClips.isEmpty()) {
      logEvent("request_empty id=$requestId endpoint=$endpoint")
      return ChatCompletionExecution(
        emptyChatResponse(model.name, stream = req.stream == true, logId = logId)
      )
    }

    // KV-cache reuse detection. LiteRT's Conversation API renders the prompt
    // template incrementally — when we keep the same Conversation alive and
    // append the new user turn via Message.user(text), the SDK only prefills
    // the new portion. We detect "this request extends the previous conversation"
    // by comparing the request's history (all but the last user message) to the
    // server's cached state, then log and dispatch the safe incremental path.
    val requestSystemPrompt = req.messages
      .filter { it.role == "system" }
      .joinToString("\u0000") { it.content.text }
    val toolsHash = tools.hashCode()
    // Always emit usage chunk when the user has enabled the Metrics setting so clients
    // like Open WebUI / llama.cpp that never set stream_options.include_usage still see
    // tokens/sec stats.
    val includeUsage = req.stream_options?.include_usage == true ||
      ServerPrefs.isForceStreamUsage(context)
    val effectiveMaxTokens = req.max_completion_tokens ?: req.max_tokens

    val sampler = resolveSamplerOverrides(model, prefs, req.temperature, req.top_p, req.top_k, effectiveMaxTokens, req.seed, logId)

    val stopSeqs = req.stop.ifEmpty { null }
    // Stage the user turns now, but publish them only after inference and response
    // processing succeed. Errors, cancellation, and stop-sequence truncation invalidate
    // the cache because native Conversation state is no longer safe to extend.
    val sentTurns = req.messages
      .filter { it.role != "system" }
      .map { ServerLlmModelHelper.ConversationTurn(it.role, it.content.text) }
    val stagedCacheEntry = ServerLlmModelHelper.ConversationCacheEntry(
      turns = sentTurns,
      toolsHash = toolsHash,
    )
    val cachePublication = ConversationCachePublication(
      modelName = model.name,
      entry = stagedCacheEntry,
      isIncrementalReuseEligible = !hasTools && images.isEmpty() && audioClips.isEmpty() &&
        !compactionResult.compacted && req.response_format == null,
    )
    // Claim reusable state inside the serialized native preparation step. This keeps
    // cache-claim order identical to inference order even when concurrent Ktor requests
    // begin writing their response bodies in a different order.
    val prepareConversation = {
      val perModelSystemPrompt = if (
        !suppressPerModelSystem && ServerPrefs.isCustomPromptsEnabled(context)
      ) {
        ServerPrefs.getSystemPrompt(context, model.prefsKey)
      } else {
        ""
      }
      val systemPrompts = listOf(perModelSystemPrompt, requestSystemPrompt)
      val samplerConfig = model.configValues.toMap()
      val decision = decideIncrementalReuse(
        modelName = model.name,
        messages = req.messages,
        systemPrompts = systemPrompts,
        toolsHash = toolsHash,
        samplerConfig = samplerConfig,
        hasTools = hasTools,
        hasImages = images.isNotEmpty(),
        hasAudio = audioClips.isNotEmpty(),
        promptWasTransformed = compactionResult.compacted || req.response_format != null,
      )
      cachePublication.attachGeneration(
        decision.cacheGeneration,
        stagedCacheEntry.copy(
          systemPrompts = systemPrompts,
          samplerConfig = samplerConfig,
        ),
      )
      if (prefs.verboseDebug) {
        logEvent("request_incremental id=$requestId endpoint=$endpoint decision=${decision.kind} reason=${decision.reason}")
      }
      ConversationPreparation(
        incrementalUserText = decision.newUserText.takeIf { decision.kind == IncrementalDecision.Kind.EXTEND },
        cacheGeneration = decision.cacheGeneration,
        systemInstruction = perModelSystemPrompt
          .takeIf { it.isNotBlank() }
          ?.let { Contents.of(listOf(Content.Text(it))) },
      )
    }
    return if (req.stream == true) {
      if (useAnthropicStream) {
        ChatCompletionExecution(
          response = inferenceRunner.streamMessagesLlm(
            model = model,
            prompt = prompt,
            requestId = requestId,
            endpoint = endpoint,
            timeoutSeconds = ServerPrefs.getTimeoutChatCompletions(context),
            images = images,
            audioClips = audioClips,
            logId = logId,
            stopSequences = stopSeqs,
            tools = if (hasTools) tools else null,
            configSnapshot = sampler,
            prefs = prefs,
            schemaInjectionProviders = schemaInjectionProviders,
            schemaInjectionMessages = schemaInjectionMessages,
            suppressPerModelSystem = suppressPerModelSystem,
            enableThinkingOverride = enableThinkingOverride,
            thinkingBudgetTokens = thinkingBudgetTokens,
            frequencyPenalty = frequencyPenalty,
            presencePenalty = presencePenalty,
            maxOutputToken = effectiveMaxTokens,
            requestModelId = requestedId,
            prepareConversation = prepareConversation,
            onConversationFinished = cachePublication::finish,
          )
        )
      } else {
        ChatCompletionExecution(
          response = inferenceRunner.streamChatLlm(
            model = model,
            prompt = prompt,
            requestId = requestId,
            endpoint = endpoint,
            timeoutSeconds = ServerPrefs.getTimeoutChatCompletions(context),
            images = images,
            audioClips = audioClips,
            logId = logId,
            includeUsage = includeUsage,
            stopSequences = stopSeqs,
            tools = if (hasTools) tools else null,
            configSnapshot = sampler,
            json = json,
            prefs = prefs,
            schemaInjectionProviders = schemaInjectionProviders,
            schemaInjectionMessages = schemaInjectionMessages,
            suppressPerModelSystem = suppressPerModelSystem,
            enableThinkingOverride = enableThinkingOverride,
            thinkingBudgetTokens = thinkingBudgetTokens,
            frequencyPenalty = frequencyPenalty,
            presencePenalty = presencePenalty,
            maxOutputToken = effectiveMaxTokens,
            prepareConversation = prepareConversation,
            onConversationFinished = cachePublication::finish,
            responseFormatSchema = responseFormatSchema,
          )
        )
      }
    } else {
      var schemaInjectionToolCalls: List<ToolCall> = emptyList()
      val (rawText, llmError) = inferenceRunner.runLlm(
        model = model,
        prompt = prompt,
        requestId = requestId,
        endpoint = endpoint,
        timeoutSeconds = ServerPrefs.getTimeoutChatCompletions(context),
        images = images,
        audioClips = audioClips,
        logId = logId,
        configSnapshot = sampler,
        prefs = prefs,
        schemaInjectionProviders = schemaInjectionProviders,
        schemaInjectionMessages = schemaInjectionMessages,
        onNativeToolCalls = if (useSchemaInjection) {
          { calls -> schemaInjectionToolCalls = calls }
        } else null,
        suppressPerModelSystem = suppressPerModelSystem,
        enableThinkingOverride = enableThinkingOverride,
        thinkingBudgetTokens = thinkingBudgetTokens,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        maxOutputToken = effectiveMaxTokens,
        prepareConversation = prepareConversation,
        responseFormatSchema = responseFormatSchema,
      )
      if (rawText == null) {
        cachePublication.finish(isConversationReusable = false)
        return ChatCompletionExecution(handleBlockingInferenceError(llmError, logId, context))
      }
      val (text, stopSequenceTriggered, matchedStopSequence) = InferenceRunner.applyStopSequences(rawText, stopSeqs)
      val completionStopSequence = matchedStopSequence.takeIf { stopSequenceTriggered }

      val promptTokens = estimateTokens(prompt)

      // Check if the model output contains tool call(s) — supports parallel calls
      if (hasTools) {
        val toolCalls = if (useSchemaInjection) {
          schemaInjectionToolCalls.ifEmpty { ToolCallParser.parseAll(text, tools) }
        } else {
          ToolCallParser.parseAll(text, tools)
        }
        if (toolCalls.isNotEmpty()) {
          if (logId != null) RequestLogStore.update(logId) { it.copy(hasToolCalls = true) }
          val source = if (useSchemaInjection && schemaInjectionToolCalls.isNotEmpty()) "schema_injection" else "text_parse"
          logEvent("request_tool_calls id=$requestId endpoint=$endpoint tools=${toolCalls.joinToString(",") { it.function.name }} count=${toolCalls.size} source=$source")
          val completionTokens = estimateTokens(toolCalls.joinToString("") { it.function.arguments })
          val timings = PayloadBuilders.buildTimings(promptTokens, completionTokens)
          val responseJson = json.encodeToString(PayloadBuilders.chatResponseWithToolCalls(model.name, toolCalls, promptLen = prompt.length, timings = timings))
          captureResponse(responseJson)
          cachePublication.finish(
            isConversationReusable = !stopSequenceTriggered,
            assistantText = if (endpoint == "/v1/messages") {
              AnthropicConverter.splitThinkingAndText(text).second
            } else {
              text
            },
          )
          return ChatCompletionExecution(httpOkJson(responseJson), completionStopSequence)
        }
      }

      val completionTokens = estimateTokens(text)
      val effectiveMax = (sampler ?: model.configValues).maxTokensInt()
      val finishReason = FinishReason.infer(completionTokens, effectiveMax)
      val timings = PayloadBuilders.buildTimings(promptTokens, completionTokens)
      val responseJson = json.encodeToString(
        PayloadBuilders.chatResponseWithText(
          model.name,
          text,
          promptLen = prompt.length,
          finishReason = finishReason,
          timings = timings,
        )
      )
      captureResponse(responseJson)
      cachePublication.finish(
        isConversationReusable = !stopSequenceTriggered,
        assistantText = if (endpoint == "/v1/messages") {
          AnthropicConverter.splitThinkingAndText(text).second
        } else {
          text
        },
      )
      ChatCompletionExecution(httpOkJson(responseJson), completionStopSequence)
    }
  }

  /** Empty response for /v1/chat/completions — returns SSE or JSON depending on stream flag. */
  private fun emptyChatResponse(modelId: String, stream: Boolean, logId: String?): HttpResponse {
    return if (stream) {
      val chatId = "chatcmpl-${java.util.UUID.randomUUID()}"
      val now = System.currentTimeMillis() / 1000
      val payload = ResponseRenderer.buildChatStreamFirstChunk(chatId, modelId, now) +
        ResponseRenderer.buildChatStreamFinalChunk(chatId, modelId, now) +
        ResponseRenderer.SSE_DONE
      HttpResponse.Sse { writer ->
        writer.emit(payload)
        writer.finish()
        if (logId != null) RequestLogStore.update(logId) { it.copy(isPending = false) }
      }
    } else {
      httpOkJson(json.encodeToString(PayloadBuilders.emptyChatResponse(modelId)))
    }
  }
}

