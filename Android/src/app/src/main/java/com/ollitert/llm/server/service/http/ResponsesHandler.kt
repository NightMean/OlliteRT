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
import com.ollitert.llm.server.data.model.*
import com.ollitert.llm.server.data.allowlist.*
import com.ollitert.llm.server.data.storage.*
import com.ollitert.llm.server.data.repository.*
import com.ollitert.llm.server.data.prefs.*
import com.ollitert.llm.server.service.inference.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class ResponsesHandler(
  private val context: Context,
  private val json: Json,
  private val inferenceRunner: InferenceRunner,
  private val modelLifecycle: ModelLifecycle,
  private val logEvent: (String) -> Unit,
  private val nextRequestId: () -> String,
) {

  suspend fun handleResponses(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse {
    val requestId = nextRequestId()
    captureBody(body)
    val req = try {
      json.decodeFromString<ResponsesRequest>(body)
    } catch (e: SerializationException) {
      return httpBadRequest("Invalid JSON: ${e.message}")
    }
    val toolChoiceStr = PromptBuilder.resolveToolChoice(req.tool_choice)
    if (req.tools.isNullOrEmpty() && toolChoiceStr == "required") {
      return httpBadRequest("tool_choice required but tools empty")
    }
    val requestedId = BridgeUtils.resolveRequestedModelId(req.model)
    val model = when (val sel = modelLifecycle.selectModel(req.model)) {
      is ModelLifecycle.ModelSelection.Ok -> sel.model
      is ModelLifecycle.ModelSelection.Error -> return sel.toHttpResponse()
    }
    // Build prompt with progressive compaction if context window is exceeded
    if (req.messages != null && !req.input.isNullOrEmpty()) {
      logEvent("request_warning id=$requestId endpoint=/v1/responses detail=input_ignored_when_messages_present")
    }
    val truncateHistoryResp = prefs.autoTruncateHistory
    val trimPromptsResp = prefs.autoTrimPrompts
    val maxContextResp = model.maxContextTokens
    val compactionResultResp = PromptCompactor.compactConversationPrompt(
      messages = req.messages ?: req.input,
      chatTemplate = null,
      maxContext = maxContextResp,
      truncateHistory = truncateHistoryResp,
      trimPrompts = trimPromptsResp,
    )
    logCompactionResult(compactionResultResp, requestId, "/v1/responses", logId, maxContextResp, logEvent, compactionLogUpdater(logId))
    val prompt = compactionResultResp.prompt
    // Store context utilization data in the log entry for per-request display
    recordContextUtilization(logId, prompt, maxContextResp)
    logEvent("request_start id=$requestId endpoint=/v1/responses bodyLength=${body.length} promptChars=${prompt.length} model=$requestedId resolved=${model.name}")

    if (prompt.isBlank()) {
      logEvent("request_empty id=$requestId endpoint=/v1/responses")
      return emptyResponse(model.name, stream = req.stream == true, logId = logId)
    }

    val tools = req.tools.orEmpty()
    val hasTools = tools.isNotEmpty() && toolChoiceStr != "none"
    val useSchemaInjectionResp = hasTools && prefs.schemaInjectionToolCalling
    val schemaInjectionProvidersResp = if (useSchemaInjectionResp) SchemaInjectionBridge.toolSpecsToProviders(tools) else emptyList()

    val sampler = resolveSamplerOverrides(model, prefs, req.temperature, req.top_p, req.top_k, req.max_output_tokens, req.seed, logId)

    return if (req.stream == true) {
      inferenceRunner.streamLlm(
        model = model,
        prompt = prompt,
        requestId = requestId,
        endpoint = "/v1/responses",
        timeoutSeconds = ServerPrefs.getTimeoutResponses(context),
        logId = logId,
        configSnapshot = sampler,
        json = json,
        tools = if (hasTools) tools else null,
        prefs = prefs,
        schemaInjectionProviders = schemaInjectionProvidersResp,
      )
    } else {
      var schemaInjectionToolCallsResp: List<ToolCall> = emptyList()
      val (text, llmError) = inferenceRunner.runLlm(
        model = model,
        prompt = prompt,
        requestId = requestId,
        endpoint = "/v1/responses",
        timeoutSeconds = ServerPrefs.getTimeoutResponses(context),
        logId = logId,
        configSnapshot = sampler,
        prefs = prefs,
        schemaInjectionProviders = schemaInjectionProvidersResp,
        onNativeToolCalls = if (useSchemaInjectionResp) {
          { calls -> schemaInjectionToolCallsResp = calls }
        } else null,
      )
      if (text == null) return handleBlockingInferenceError(llmError, logId, context)

      // Check if the model output contains tool call(s)
      if (hasTools) {
        val toolCalls = if (useSchemaInjectionResp) {
          schemaInjectionToolCallsResp.ifEmpty { ToolCallParser.parseAll(text, tools) }
        } else {
          ToolCallParser.parseAll(text, tools)
        }
        if (toolCalls.isNotEmpty()) {
          if (logId != null) RequestLogStore.update(logId) { it.copy(hasToolCalls = true) }
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

  /** Empty response for /v1/responses — returns SSE or JSON depending on stream flag. */
  private fun emptyResponse(modelId: String, stream: Boolean, logId: String?): HttpResponse {
    val body = PayloadBuilders.responsesResponseWithText(modelId, "")
    return if (stream) {
      val payload = ResponseRenderer.buildTextSsePayload(modelId, "")
      HttpResponse.Sse { writer ->
        writer.emit(payload)
        writer.finish()
        if (logId != null) RequestLogStore.update(logId) { it.copy(isPending = false) }
      }
    } else {
      httpOkJson(json.encodeToString(body))
    }
  }
}
