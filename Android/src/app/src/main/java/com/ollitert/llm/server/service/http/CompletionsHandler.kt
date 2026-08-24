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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

internal class CompletionsHandler(
  private val context: Context,
  private val json: Json,
  private val inferenceRunner: InferenceRunner,
  private val modelLifecycle: ModelLifecycle,
  private val logEvent: (String) -> Unit,
  private val nextRequestId: () -> String,
) {

  suspend fun handleCompletions(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse {
    val requestId = nextRequestId()
    captureBody(body)
    val req = try {
      json.decodeFromString<CompletionRequest>(body)
    } catch (e: SerializationException) {
      return httpBadRequest("Invalid JSON: ${e.message}")
    }
    validateNParam(req.n)?.let { (param, msg) ->
      logEvent("request_rejected id=$requestId endpoint=/v1/completions param=$param value=${req.n}")
      return httpBadRequest(msg)
    }
    validateBestOfParam(req.best_of)?.let { (param, msg) ->
      logEvent("request_rejected id=$requestId endpoint=/v1/completions param=$param value=${req.best_of}")
      return httpBadRequest(msg)
    }
    val model = when (val sel = modelLifecycle.selectModel(req.model)) {
      is ModelLifecycle.ModelSelection.Ok -> sel.model
      is ModelLifecycle.ModelSelection.Error -> return sel.toHttpResponse()
    }
    // Raw prompts have no message structure, so history truncation and tool schema compaction
    // aren't possible — only hard string trimming can reduce the prompt size.
    val trimPromptsCompl = prefs.autoTrimPrompts
    val maxContextCompl = model.maxContextTokens
    val compactionResultCompl = PromptCompactor.compactRawPrompt(req.prompt, maxContextCompl, trimPromptsCompl)
    logCompactionResult(compactionResultCompl, requestId, "/v1/completions", logId, maxContextCompl, logEvent, compactionLogUpdater(logId))
    val prompt = compactionResultCompl.prompt
    // Store context utilization data in the log entry for per-request display
    recordContextUtilization(logId, prompt, maxContextCompl)
    val requestedIdCompl = BridgeUtils.resolveRequestedModelId(req.model)
    logEvent("request_start id=$requestId endpoint=/v1/completions bodyLength=${body.length} promptChars=${prompt.length} model=$requestedIdCompl resolved=${model.name}")

    if (prompt.isBlank()) {
      logEvent("request_empty id=$requestId endpoint=/v1/completions")
      return emptyCompletionResponse(model.name, stream = req.stream == true, logId = logId)
    }

    // OpenAI spec allows `"stop": "text"` (single string) or `"stop": ["a","b"]` (array).
    val stopSequences: List<String>? = when (val stop = req.stop) {
      is JsonNull, null -> null
      is JsonPrimitive -> stop.content.takeIf { it.isNotBlank() }?.let { listOf(it) }
      is JsonArray -> stop.map { it.jsonPrimitive.content }
      else -> null
    }

    val sampler = resolveSamplerOverrides(
      model = model,
      prefs = prefs,
      temperature = req.temperature,
      topP = req.top_p,
      topK = null,
      maxTokens = req.max_tokens,
      seed = req.seed,
      logId = logId,
    )
    val includeUsage = req.stream_options?.include_usage == true ||
      ServerPrefs.isForceStreamUsage(context)
    val stopSeqs = stopSequences?.ifEmpty { null }

    return if (req.stream == true) {
      inferenceRunner.streamCompletions(
        model = model,
        prompt = prompt,
        requestId = requestId,
        endpoint = "/v1/completions",
        timeoutSeconds = ServerPrefs.getTimeoutChatCompletions(context),
        logId = logId,
        includeUsage = includeUsage,
        stopSequences = stopSeqs,
        configSnapshot = sampler,
        json = json,
        prefs = prefs,
      )
    } else {
      val (rawText, llmError) = inferenceRunner.runLlm(
        model = model,
        prompt = prompt,
        requestId = requestId,
        endpoint = "/v1/completions",
        timeoutSeconds = ServerPrefs.getTimeoutChatCompletions(context),
        logId = logId,
        configSnapshot = sampler,
        prefs = prefs,
      )
      if (rawText == null) return handleBlockingInferenceError(llmError, logId, context)

      val (text, _) = InferenceRunner.applyStopSequences(rawText, stopSeqs)
      val promptTokens = estimateTokens(prompt)
      val completionTokens = estimateTokens(text)
      val effectiveMaxCompl = (sampler ?: model.configValues).maxTokensInt()
      val finishReasonCompl = FinishReason.infer(completionTokens, effectiveMaxCompl)
      val timings = PayloadBuilders.buildTimings(promptTokens, completionTokens)
      val responseJson = json.encodeToString(
        CompletionResponse(
          id = BridgeUtils.generateCompletionId(),
          created = BridgeUtils.epochSeconds(),
          model = model.name,
          choices = listOf(CompletionChoice(text = text, index = 0, finish_reason = finishReasonCompl)),
          usage = Usage(promptTokens, completionTokens),
          timings = timings,
        )
      )
      captureResponse(responseJson)
      httpOkJson(responseJson)
    }
  }

  /** Empty response for /v1/completions — returns SSE or JSON depending on stream flag. */
  private fun emptyCompletionResponse(modelId: String, stream: Boolean, logId: String?): HttpResponse {
    return if (stream) {
      val cmplId = BridgeUtils.generateCompletionId()
      val now = System.currentTimeMillis() / 1000
      val payload = ResponseRenderer.buildCompletionStreamFinalChunk(cmplId, modelId, now) +
        ResponseRenderer.SSE_DONE
      HttpResponse.Sse { writer ->
        writer.emit(payload)
        writer.finish()
        if (logId != null) RequestLogStore.update(logId) { it.copy(isPending = false) }
      }
    } else {
      httpOkJson(
        json.encodeToString(
          CompletionResponse(
            id = BridgeUtils.generateCompletionId(),
            created = BridgeUtils.epochSeconds(),
            model = modelId,
            choices = listOf(CompletionChoice(text = "", index = 0, finish_reason = FinishReason.STOP)),
            usage = Usage(0, 0),
          )
        )
      )
    }
  }
}
