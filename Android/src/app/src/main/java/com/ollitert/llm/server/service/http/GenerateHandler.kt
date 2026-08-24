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

internal class GenerateHandler(
  private val context: Context,
  private val json: Json,
  private val inferenceRunner: InferenceRunner,
  private val modelLifecycle: ModelLifecycle,
  private val logEvent: (String) -> Unit,
  private val nextRequestId: () -> String,
) {

  suspend fun handleGenerate(
    body: String,
    captureBody: (String) -> Unit = {},
    captureResponse: (String) -> Unit = {},
    logId: String? = null,
    prefs: RequestPrefsSnapshot = RequestPrefsSnapshot(),
  ): HttpResponse {
    val requestId = nextRequestId()
    captureBody(body)
    val req = try {
      json.decodeFromString<GenReq>(body)
    } catch (e: SerializationException) {
      return httpBadRequest("Invalid JSON: ${e.message}")
    }
    val model = when (val sel = modelLifecycle.selectModel(null)) {
      is ModelLifecycle.ModelSelection.Ok -> sel.model
      is ModelLifecycle.ModelSelection.Error -> return sel.toHttpResponse()
    }
    // Raw prompts have no message structure, so history truncation and tool schema compaction
    // aren't possible — only hard string trimming can reduce the prompt size.
    val trimPromptsGen = prefs.autoTrimPrompts
    val maxContextGen = model.maxContextTokens
    val compactionResultGen = PromptCompactor.compactRawPrompt(req.prompt, maxContextGen, trimPromptsGen)
    logCompactionResult(compactionResultGen, requestId, "/generate", logId, maxContext = null, logEvent, compactionLogUpdater(logId))
    val prompt = compactionResultGen.prompt
    // Store context utilization data in the log entry for per-request display
    recordContextUtilization(logId, prompt, maxContextGen)
    logEvent("request_start id=$requestId endpoint=/generate bodyLength=${body.length} promptChars=${prompt.length} model=default")
    val (text, llmError) = inferenceRunner.runLlm(model, prompt, requestId, "/generate", logId = logId, prefs = prefs)
    if (text == null) return handleBlockingInferenceError(llmError, logId, context)
    val promptTokens = estimateTokens(prompt)
    val completionTokens = estimateTokens(text)
    val timings = PayloadBuilders.buildTimings(promptTokens, completionTokens)
    val responseJson = json.encodeToString(GenRes(text = text, usage = Usage(promptTokens, completionTokens), timings = timings))
    captureResponse(responseJson)
    return httpOkJson(responseJson)
  }
}
