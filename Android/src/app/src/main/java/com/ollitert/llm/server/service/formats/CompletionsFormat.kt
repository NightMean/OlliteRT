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

package com.ollitert.llm.server.service.formats

import com.ollitert.llm.server.service.BridgeUtils
import com.ollitert.llm.server.service.CompletionChoice
import com.ollitert.llm.server.service.CompletionResponse
import com.ollitert.llm.server.service.FinishReason
import com.ollitert.llm.server.service.PayloadBuilders
import com.ollitert.llm.server.service.ResponseRenderer
import com.ollitert.llm.server.service.SseWriter
import com.ollitert.llm.server.service.ToolCall
import com.ollitert.llm.server.service.Usage
import com.ollitert.llm.server.service.estimateTokensByLength
import com.ollitert.llm.server.service.estimateTokensLongByLength
import kotlinx.serialization.json.Json

/**
 * Streaming format for `/v1/completions` (legacy text completions).
 */
internal class CompletionsFormat(
  private val modelName: String,
  private val now: Long,
  override val stopSequences: List<String>?,
  private val json: Json,
  private val includeUsage: Boolean,
) : StreamingFormat {
  private val cmplId = BridgeUtils.generateCompletionId()
  override val sourceTag = "executeStreaming_completions"
  override val bufferAllTokens = false

  override suspend fun emitHeader(writer: SseWriter) {
  }
  override suspend fun emitThinkingDelta(writer: SseWriter, text: String) {
    writer.emit(ResponseRenderer.buildCompletionStreamChunk(cmplId, modelName, now, text))
  }
  override suspend fun emitContentDelta(writer: SseWriter, text: String) {
    writer.emit(ResponseRenderer.buildCompletionStreamChunk(cmplId, modelName, now, text))
  }
  override suspend fun emitThinkingClose(writer: SseWriter) {
    writer.emit(ResponseRenderer.buildCompletionStreamChunk(cmplId, modelName, now, "</think>"))
  }
  override suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean) {
    writer.emit(ResponseRenderer.buildCompletionStreamFinalChunk(cmplId, modelName, now, FinishReason.STOP))
    writer.emit(ResponseRenderer.SSE_DONE)
    writer.finish()
  }
  override fun estimateInputTokens(prompt: String): Long = estimateTokensLongByLength(prompt.length)
  override fun estimateInputTokensInt(prompt: String): Int = estimateTokensByLength(prompt.length)
  override suspend fun emitCompletion(
    writer: SseWriter,
    fullText: String,
    fullThinking: String,
    promptTokens: Int,
    completionTokens: Int,
    ttfbMs: Long,
    totalLatencyMs: Long,
    maxTokens: Int?,
    nativeToolCalls: List<ToolCall>,
    stopSequenceTriggered: Boolean,
    matchedStopSequence: String?,
  ): List<ToolCall> {
    val finishReason = FinishReason.infer(completionTokens, maxTokens)
    writer.emit(ResponseRenderer.buildCompletionStreamFinalChunk(cmplId, modelName, now, finishReason))
    if (includeUsage) {
      val timings = PayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
      val timingsJson = if (timings != null) json.encodeToString(timings) else null
      writer.emit(ResponseRenderer.buildCompletionStreamUsageChunk(cmplId, modelName, now, promptTokens, completionTokens, timingsJson))
    }
    writer.emit(ResponseRenderer.SSE_DONE)
    writer.finish()
    return emptyList()
  }
  override fun buildLogResponseJson(
    combinedText: String,
    promptLen: Int,
    promptTokens: Int,
    completionTokens: Int,
    ttfbMs: Long,
    totalLatencyMs: Long,
    parsedToolCalls: List<ToolCall>,
  ): String {
    val timings = PayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
    return json.encodeToString(CompletionResponse(
      id = cmplId,
      created = now,
      model = modelName,
      choices = listOf(CompletionChoice(text = combinedText, index = 0, finish_reason = FinishReason.infer(completionTokens, null))),
      usage = Usage(promptTokens, completionTokens),
      timings = timings,
    ))
  }
  override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String = ""
}
