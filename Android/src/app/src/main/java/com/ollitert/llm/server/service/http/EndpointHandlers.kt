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

package com.ollitert.llm.server.service.http

import android.content.Context
import com.ollitert.llm.server.data.*
import com.ollitert.llm.server.service.inference.*
import kotlinx.serialization.json.Json

/**
 * High-level facade dispatching HTTP endpoints (/generate, /v1/chat/completions,
 * /v1/completions, /v1/responses) to dedicated sub-handlers.
 */
class EndpointHandlers(
  private val context: Context,
  private val json: Json,
  private val inferenceRunner: InferenceRunner,
  private val modelLifecycle: ModelLifecycle,
  private val logEvent: (String) -> Unit,
  private val nextRequestId: () -> String,
) {

  private val generateHandler = GenerateHandler(
    context = context,
    json = json,
    inferenceRunner = inferenceRunner,
    modelLifecycle = modelLifecycle,
    logEvent = logEvent,
    nextRequestId = nextRequestId,
  )

  private val chatCompletionsHandler = ChatCompletionsHandler(
    context = context,
    json = json,
    inferenceRunner = inferenceRunner,
    modelLifecycle = modelLifecycle,
    logEvent = logEvent,
    nextRequestId = nextRequestId,
  )

  private val completionsHandler = CompletionsHandler(
    context = context,
    json = json,
    inferenceRunner = inferenceRunner,
    modelLifecycle = modelLifecycle,
    logEvent = logEvent,
    nextRequestId = nextRequestId,
  )

  private val responsesHandler = ResponsesHandler(
    context = context,
    json = json,
    inferenceRunner = inferenceRunner,
    modelLifecycle = modelLifecycle,
    logEvent = logEvent,
    nextRequestId = nextRequestId,
  )

  // ── /generate ────────────────────────────────────────────────────────────

  suspend fun handleGenerate(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse = generateHandler.handleGenerate(
    body = body,
    captureBody = captureBody,
    captureResponse = captureResponse,
    logId = logId,
    prefs = prefs,
  )

  // ── /v1/chat/completions ─────────────────────────────────────────────────

  suspend fun handleChatCompletion(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse = chatCompletionsHandler.handleChatCompletion(
    body = body,
    captureBody = captureBody,
    captureResponse = captureResponse,
    logId = logId,
    prefs = prefs,
  )

  /**
   * Core chat-completion pipeline. Extracted so the Anthropic /v1/messages handler can
   * convert its request to ChatRequest and reuse the entire prompt-compaction →
   * inference → response-shaping flow without duplicating logic.
   */
  suspend fun runChatCompletion(
    req: ChatRequest,
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
    suppressPerModelSystem: Boolean = false,
    bodyLength: Int = 0,
    endpoint: String = "/v1/chat/completions",
    useAnthropicStream: Boolean = false,
    enableThinkingOverride: Boolean? = null,
  ): HttpResponse = chatCompletionsHandler.runChatCompletion(
    req = req,
    captureResponse = captureResponse,
    logId = logId,
    prefs = prefs,
    suppressPerModelSystem = suppressPerModelSystem,
    bodyLength = bodyLength,
    endpoint = endpoint,
    useAnthropicStream = useAnthropicStream,
    enableThinkingOverride = enableThinkingOverride,
  )

  // ── /v1/completions ──────────────────────────────────────────────────────

  suspend fun handleCompletions(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse = completionsHandler.handleCompletions(
    body = body,
    captureBody = captureBody,
    captureResponse = captureResponse,
    logId = logId,
    prefs = prefs,
  )

  // ── /v1/responses ────────────────────────────────────────────────────────

  suspend fun handleResponses(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse = responsesHandler.handleResponses(
    body = body,
    captureBody = captureBody,
    captureResponse = captureResponse,
    logId = logId,
    prefs = prefs,
  )
}
