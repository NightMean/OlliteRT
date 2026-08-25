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

import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.service.inference.*

import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import kotlinx.serialization.json.Json

/**
 * Wire-up for the Anthropic Messages API endpoints.
 *
 * The actual chat-completion machinery lives in [EndpointHandlers.runChatCompletion];
 * this class converts the Anthropic body, captures the raw Anthropic body for the
 * request log, and re-shapes the OAI response to the Anthropic envelope before it
 * reaches the captured-response sink.
 *
 * Streaming requests (`stream:true`) reuse the same pipeline: pre-stream failures
 * come back as JSON and are re-shaped into Anthropic error envelopes below, while
 * successful streams dispatch to `streamMessagesLlm` and emit Anthropic SSE events
 * directly.
 */
class AnthropicEndpointHandlers(
  private val json: Json,
  private val endpointHandlers: EndpointHandlers,
  private val nextRequestId: () -> String,
) {

  /** Non-streaming `POST /v1/messages` handler. Streaming branch added in P6+. */
  suspend fun handleMessages(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse {
    captureBody(body)
    val anthropicReq = try {
      AnthropicConverter.parseRequest(json, body)
    } catch (e: AnthropicConversionError) {
      return httpAnthropicError(400, e.errorType, e.message)
    }

    val convertedReq = try {
      AnthropicConverter.toInternalChatRequest(anthropicReq)
    } catch (e: AnthropicConversionError) {
      return httpAnthropicError(400, e.errorType, e.message)
    }
    ServerMetrics.incrementMessagesRequests()

    val requestId = nextRequestId()
    val matchedStopRef = arrayOf<String?>(null)
    val anthropicAdapter: (String) -> Unit = { oaiBody ->
      val translated = try {
        AnthropicConverter.toAnthropicResponse(
          json = json,
          oaiResponseBody = oaiBody,
          requestedModelId = anthropicReq.model ?: "local",
          requestId = requestId,
          matchedStopSequence = matchedStopRef[0],
        )
      } catch (e: AnthropicConversionError) {
        // Fall back to a synthetic Anthropic error envelope so the captured log
        // and the wire response stay consistent.
        ResponseRenderer.renderAnthropicError(e.errorType, e.message)
      } catch (e: Exception) {
        // Unexpected reshape failure (kotlinx.serialization type mismatch, etc.).
        // Surface the message so the failure mode is visible in the logs and to
        // the client instead of bubbling out of the handler as a generic 500.
        ResponseRenderer.renderAnthropicError(
          "api_error",
          "Failed to re-shape upstream response: ${e.message ?: e.javaClass.simpleName}",
        )
      }
      captureResponse(translated)
    }

    val response = endpointHandlers.runChatCompletion(
      req = convertedReq,
      captureResponse = anthropicAdapter,
      logId = logId,
      prefs = prefs,
      suppressPerModelSystem = anthropicReq.system != null,
      bodyLength = body.length,
      endpoint = "/v1/messages",
      useAnthropicStream = anthropicReq.stream == true,
      enableThinkingOverride = resolveThinkingOverride(anthropicReq.thinking),
      thinkingBudgetTokens = resolveThinkingBudget(anthropicReq.thinking),
    )

    return when (response) {
      is HttpResponse.Json ->
        // Non-streaming success AND pre-stream failures both land here. Success
        // bodies are re-shaped OAI chat responses; non-200 bodies are OAI-shaped
        // errors from runChatCompletion (model selection, validation, busy server)
        // that must be converted even when the client asked for streaming —
        // Anthropic SDKs reject the OpenAI envelope on /v1/messages.
        reshapeAnthropicJsonResponse(json, response, anthropicReq.model ?: "local", requestId, matchedStopRef[0])
      // SSE responses already carry Anthropic-shaped events because runChatCompletion
      // dispatched to streamMessagesLlm when useAnthropicStream was true.
      else -> response
    }
  }

  /**
   * Map the Anthropic `thinking` config to the per-request override threaded into
   * the inference runner. `{type:"enabled"}` forces thinking on, `{type:"disabled"}`
   * forces it off, missing or any other type leaves the model's persisted setting
   * in charge.
   */
  private fun resolveThinkingOverride(config: AnthropicThinkingConfig?): Boolean? = when (config?.type) {
    "enabled" -> true
    "disabled" -> false
    else -> null
  }

  /**
   * Extract the reasoning token budget for the native thinking channel. A budget
   * implies enabled thinking for this turn; null keeps model-default behavior.
   */
  private fun resolveThinkingBudget(config: AnthropicThinkingConfig?): Int? {
    if (config?.budget_tokens == null) return null
    return config.budget_tokens.takeIf { it > 0 }
  }

  /**
   * `POST /v1/messages/count_tokens` — estimate input tokens for a request body.
   *
   * Intentionally skips model selection: the Anthropic spec lets clients call this
   * with no model loaded, and Claude Code does so before the first inference. The
   * estimate uses the same charLen/4 heuristic as the rest of the server.
   */
  suspend fun handleCountTokens(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") logId: String? = null,
    @Suppress("UNUSED_PARAMETER") prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse {
    captureBody(body)
    val anthropicReq = try {
      AnthropicConverter.parseRequest(json, body)
    } catch (e: AnthropicConversionError) {
      return httpAnthropicError(400, e.errorType, e.message)
    }
    // count_tokens accepts the same body as /v1/messages but max_tokens is not required.
    val converted = try {
      AnthropicConverter.toInternalChatRequest(
        anthropicReq.copy(max_tokens = anthropicReq.max_tokens ?: 1)
      )
    } catch (e: AnthropicConversionError) {
      return httpAnthropicError(400, e.errorType, e.message)
    }

    val tools = converted.tools.orEmpty()
    val toolChoiceStr = PromptBuilder.resolveToolChoice(converted.tool_choice)
    val prompt = if (tools.isNotEmpty() && toolChoiceStr != "none") {
      PromptBuilder.buildToolAwarePrompt(converted.messages, tools, toolChoiceStr, chatTemplate = null)
    } else {
      PromptBuilder.buildChatPrompt(converted.messages, chatTemplate = null)
    }

    val tokens = estimateTokens(prompt)
    val responseJson = """{"input_tokens":$tokens}"""
    captureResponse(responseJson)
    return httpOkJson(responseJson)
  }
}

/**
 * Pure re-shaper for OAI JSON bodies on /v1/messages — success bodies become
 * Anthropic message envelopes, non-200 OAI error envelopes become Anthropic error
 * envelopes. Top-level and internal so the protocol contract is unit-testable
 * without Android dependencies ([AnthropicEndpointHandlers] needs a Context-backed
 * [EndpointHandlers]).
 */
internal fun reshapeAnthropicJsonResponse(
  json: Json,
  response: HttpResponse.Json,
  requestedModelId: String,
  requestId: String,
  matchedStopSequence: String?,
): HttpResponse.Json {
  if (response.statusCode == 200) {
    val body = try {
      AnthropicConverter.toAnthropicResponse(
        json = json,
        oaiResponseBody = response.body,
        requestedModelId = requestedModelId,
        requestId = requestId,
        matchedStopSequence = matchedStopSequence,
      )
    } catch (e: AnthropicConversionError) {
      return httpAnthropicError(500, e.errorType, e.message)
    } catch (e: Exception) {
      return httpAnthropicError(
        500,
        "api_error",
        "Failed to re-shape upstream response: ${e.message ?: e.javaClass.simpleName}",
      )
    }
    return response.copy(body = body)
  }
  // Map OAI error envelope → Anthropic. Best-effort message extraction.
  val message = try {
    val obj = json.parseToJsonElement(response.body)
    val errObj = (obj as? kotlinx.serialization.json.JsonObject)?.get("error")
    (errObj as? kotlinx.serialization.json.JsonObject)?.get("message")?.toString()?.removeSurrounding("\"")
      ?: response.body
  } catch (_: Exception) {
    response.body
  }
  val errorType = when (response.statusCode) {
    400 -> "invalid_request_error"
    401 -> "authentication_error"
    404 -> "not_found_error"
    413 -> "request_too_large"
    503 -> "overloaded_error"
    else -> "api_error"
  }
  return httpAnthropicError(response.statusCode, errorType, message)
}
