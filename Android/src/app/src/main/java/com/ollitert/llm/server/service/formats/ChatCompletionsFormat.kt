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

import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*

import kotlinx.serialization.json.Json

/**
 * Streaming format for `/v1/chat/completions` (OpenAI format).
 */
internal class ChatCompletionsFormat(
  private val modelName: String,
  private val now: Long,
  override val stopSequences: List<String>?,
  private val tools: List<ToolSpec>?,
  private val json: Json,
  private val includeUsage: Boolean,
  private val hasSchemaInjection: Boolean = false,
) : StreamingFormat {
  private val chatId = BridgeUtils.generateChatCompletionId()
  override val sourceTag = "executeStreaming_chat"
  // Buffer only when tools are present AND native tool calling is not active.
  // With schema injection, the SDK handles tool calls atomically via onNativeToolCalls,
  // so text can stream progressively without risk of emitting partial tool call JSON.
  override val bufferAllTokens = tools != null && !hasSchemaInjection

  override suspend fun emitHeader(writer: SseWriter) {
    writer.emit(ResponseRenderer.buildChatStreamFirstChunk(chatId, modelName, now))
  }
  override suspend fun emitThinkingDelta(writer: SseWriter, text: String) {
    writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, text))
  }
  override suspend fun emitContentDelta(writer: SseWriter, text: String) {
    writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, text))
  }
  override suspend fun emitThinkingClose(writer: SseWriter) {
    writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, "</think>"))
  }
  override suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean) {
    if (!headerWritten) {
      writer.emit(ResponseRenderer.buildChatStreamFirstChunk(chatId, modelName, now))
    }
    writer.emit(ResponseRenderer.buildChatStreamFinalChunk(chatId, modelName, now, FinishReason.STOP))
    writer.emit(ResponseRenderer.SSE_DONE)
    writer.finish()
  }
  override fun estimateInputTokens(prompt: String): Long = estimateTokensLong(prompt)
  override fun estimateInputTokensInt(prompt: String): Int = estimateTokens(prompt)
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
    val parsedToolCalls = nativeToolCalls.ifEmpty {
      if (tools != null) ToolCallParser.parseAll(fullText, tools) else emptyList()
    }
    if (parsedToolCalls.isNotEmpty()) {
      writer.emit(ResponseRenderer.buildChatStreamToolCallChunks(chatId, modelName, now, parsedToolCalls))
    } else {
      if (bufferAllTokens) {
        writer.emit(ResponseRenderer.buildChatStreamFirstChunk(chatId, modelName, now))
        if (fullThinking.isNotEmpty()) {
          writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, "<think>$fullThinking</think>"))
        }
        if (fullText.isNotEmpty()) {
          writer.emit(ResponseRenderer.buildChatStreamDeltaChunk(chatId, modelName, now, fullText))
        }
      }
      val finishReason = FinishReason.infer(completionTokens, maxTokens)
      writer.emit(ResponseRenderer.buildChatStreamFinalChunk(chatId, modelName, now, finishReason))
    }
    if (includeUsage) {
      val timings = PayloadBuilders.buildTimingsFromValues(promptTokens, completionTokens, ttfbMs, totalLatencyMs)
      val timingsJson = if (timings != null) json.encodeToString(timings) else null
      writer.emit(ResponseRenderer.buildChatStreamUsageChunk(chatId, modelName, now, promptTokens, completionTokens, timingsJson))
    }
    writer.emit(ResponseRenderer.SSE_DONE)
    writer.finish()
    return parsedToolCalls
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
    return if (parsedToolCalls.isNotEmpty()) {
      json.encodeToString(PayloadBuilders.chatResponseWithToolCalls(modelName, parsedToolCalls, promptLen = promptLen, timings = timings))
    } else {
      json.encodeToString(PayloadBuilders.chatResponseWithText(modelName, combinedText, promptLen = promptLen, timings = timings))
    }
  }
  override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String {
    if (parsedToolCalls.isEmpty()) return ""
    return " tool_calls=${parsedToolCalls.joinToString(",") { it.function.name }} count=${parsedToolCalls.size}"
  }
}
