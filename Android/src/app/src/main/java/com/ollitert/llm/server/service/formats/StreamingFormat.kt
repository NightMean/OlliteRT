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

import com.ollitert.llm.server.data.ErrorKind
import com.ollitert.llm.server.service.ResponseRenderer
import com.ollitert.llm.server.service.SseWriter
import com.ollitert.llm.server.service.ToolCall

/**
 * Strategy interface defining wire-format-specific streaming serialization, token estimation,
 * error rendering, and log response formatting for an SSE request.
 */
internal sealed interface StreamingFormat {
  val sourceTag: String
  // When true, all tokens are collected in memory and sent as a single response after
  // inference completes. This prevents word-by-word streaming but is required when tools
  // are present without native SDK tool calling — the server must see the full output to
  // determine if it's a tool call or plain text before sending anything to the client.
  val bufferAllTokens: Boolean
  val stopSequences: List<String>?

  suspend fun emitHeader(writer: SseWriter)
  suspend fun emitThinkingDelta(writer: SseWriter, text: String)
  suspend fun emitContentDelta(writer: SseWriter, text: String)
  suspend fun emitThinkingClose(writer: SseWriter)
  suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean)
  // True when the format wants its `message_start`-equivalent header sent
  // immediately at request acceptance (before prefill begins) instead of being
  // gated on the first token. Anthropic's Messages spec defines an explicit
  // `message_start` event and ping events for the prefill window; OAI-shape
  // formats have no such concept, so they keep the existing first-token gate.
  val emitsHeaderEarly: Boolean get() = false
  // Format-specific keep-alive emission while inference is still in prefill.
  // Default is a no-op so non-Anthropic formats stay silent until first token.
  suspend fun emitPing(writer: SseWriter) {}
  fun estimateInputTokens(prompt: String): Long
  fun estimateInputTokensInt(prompt: String): Int
  suspend fun emitCompletion(
    writer: SseWriter,
    fullText: String,
    fullThinking: String,
    promptTokens: Int,
    completionTokens: Int,
    ttfbMs: Long,
    totalLatencyMs: Long,
    maxTokens: Int?,
    nativeToolCalls: List<ToolCall> = emptyList(),
    // Whether the streaming truncator matched a configured stop string.
    // OAI-shape formats ignore this and continue to derive finish_reason from token counts.
    // Only the Anthropic format consumes it (to emit stop_reason="stop_sequence").
    stopSequenceTriggered: Boolean = false,
    // The matched stop string when stopSequenceTriggered is true. Null otherwise.
    matchedStopSequence: String? = null,
  ): List<ToolCall>
  fun buildLogResponseJson(
    combinedText: String,
    promptLen: Int,
    promptTokens: Int,
    completionTokens: Int,
    ttfbMs: Long,
    totalLatencyMs: Long,
    parsedToolCalls: List<ToolCall>,
  ): String
  fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String

  /**
   * Builds the formatted assistant text for multi-turn conversation caching.
   * By default, includes `<think>` tags if thinking content was produced.
   */
  fun buildAssistantText(fullText: CharSequence, fullThinking: CharSequence): String =
    buildCombinedText(fullText, fullThinking)

  /**
   * Emit a mid-stream error in the format the client expects, then close the stream.
   *
   * Default implementation writes the OAI-shape error JSON (already produced by
   * `ResponseRenderer.renderJsonError`) followed by `data: [DONE]` — same layout
   * OAI clients expect. Anthropic's format overrides this to emit
   * `event: error\ndata: {"type":"error","error":{...}}` per the Messages API spec.
   *
   * `headerWritten` lets the format synthesize a `message_start` first when an
   * error fires before any token did — Anthropic SDKs need at least the start
   * event before they accept an error event.
   */
  suspend fun emitError(
    writer: SseWriter,
    enrichedMessage: String,
    suggestion: String?,
    kind: ErrorKind,
    oaiErrorJson: String,
    headerWritten: Boolean,
  ) {
    writer.emit("data: $oaiErrorJson\n\n")
    writer.emit(ResponseRenderer.SSE_DONE)
  }

  /**
   * Body to persist in the request log entry when the stream errors out.
   * Default returns the OAI-shape JSON the wire would have emitted; Anthropic's
   * format overrides this so the Logs tab shows a matching Anthropic envelope.
   */
  fun buildLogErrorJson(enrichedMessage: String, suggestion: String?, kind: ErrorKind, oaiErrorJson: String): String =
    oaiErrorJson

  companion object {
    fun buildCombinedText(fullText: CharSequence, fullThinking: CharSequence): String =
      if (fullThinking.isNotEmpty()) "<think>${fullThinking}</think>${fullText}" else fullText.toString()
  }
}
