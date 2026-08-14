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
import com.ollitert.llm.server.service.PayloadBuilders
import com.ollitert.llm.server.service.ResponseRenderer
import com.ollitert.llm.server.service.SseWriter
import com.ollitert.llm.server.service.ToolCall
import com.ollitert.llm.server.service.ToolCallParser
import com.ollitert.llm.server.service.ToolSpec
import com.ollitert.llm.server.service.estimateTokensByLength
import com.ollitert.llm.server.service.estimateTokensLongByLength
import com.ollitert.llm.server.service.formats.StreamingFormat.Companion.buildCombinedText
import kotlinx.serialization.json.Json

/**
 * Streaming format for `/v1/responses` endpoints.
 */
internal class ResponsesApiFormat(
  private val modelName: String,
  private val now: Long,
  private val json: Json,
  private val tools: List<ToolSpec>?,
  private val hasSchemaInjection: Boolean = false,
) : StreamingFormat {
  private val respId = BridgeUtils.generateResponseId()
  private val msgId = BridgeUtils.generateMessageId()
  override val sourceTag = "executeStreaming_responses"
  // Buffer only when tools are present AND native tool calling is not active.
  override val bufferAllTokens = tools != null && !hasSchemaInjection
  override val stopSequences: List<String>? = null

  override suspend fun emitHeader(writer: SseWriter) {
    writer.emit(ResponseRenderer.buildStreamingHeader(modelName, respId, msgId, now))
  }
  override suspend fun emitThinkingDelta(writer: SseWriter, text: String) {
    val esc = BridgeUtils.escapeSseText(text)
    writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
  }
  override suspend fun emitContentDelta(writer: SseWriter, text: String) {
    val esc = BridgeUtils.escapeSseText(text)
    writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
  }
  override suspend fun emitThinkingClose(writer: SseWriter) {
    val esc = BridgeUtils.escapeSseText("</think>")
    writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, esc))
  }
  override suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean) {
    if (!headerWritten) {
      writer.emit(ResponseRenderer.buildStreamingHeader(modelName, respId, msgId, now))
    }
    writer.emit(ResponseRenderer.buildStreamingFooter(modelName, respId, msgId, now, ""))
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
    val parsedToolCalls = nativeToolCalls.ifEmpty {
      if (tools != null) ToolCallParser.parseAll(fullText, tools) else emptyList()
    }

    if (parsedToolCalls.isNotEmpty()) {
      writer.emit(ResponseRenderer.buildResponsesStreamToolCallEvents(
        respId, modelName, now, parsedToolCalls, promptTokens, completionTokens))
    } else {
      val combinedText = buildCombinedText(fullText, fullThinking)
      if (bufferAllTokens) {
        writer.emit(ResponseRenderer.buildStreamingHeader(modelName, respId, msgId, now))
        if (fullThinking.isNotEmpty()) {
          val thinkEsc = BridgeUtils.escapeSseText("<think>$fullThinking</think>")
          writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, thinkEsc))
        }
        if (fullText.isNotEmpty()) {
          val textEsc = BridgeUtils.escapeSseText(fullText)
          writer.emit(ResponseRenderer.buildTextDeltaSseEvent(msgId, textEsc))
        }
      }
      val esc = BridgeUtils.escapeSseText(combinedText)
      writer.emit(ResponseRenderer.buildStreamingFooter(
        modelName, respId, msgId, now, esc,
        inputTokens = promptTokens, outputTokens = completionTokens))
    }
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
    return if (parsedToolCalls.isNotEmpty()) {
      json.encodeToString(PayloadBuilders.responsesResponseWithToolCalls(modelName, parsedToolCalls, promptLen = promptLen))
    } else {
      json.encodeToString(PayloadBuilders.responsesResponseWithText(modelName, combinedText, promptLen = promptLen))
    }
  }
  override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String {
    if (parsedToolCalls.isEmpty()) return ""
    return " tool_calls=${parsedToolCalls.joinToString(",") { it.function.name }} count=${parsedToolCalls.size}"
  }
}
