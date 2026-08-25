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

import android.util.Log
import com.ollitert.llm.server.data.model.ErrorKind

private const val TAG = "OlliteRT.Inference"

/**
 * Anthropic Messages SSE event sequence:
 *   message_start → [content_block_start/delta/stop]+ → message_delta → message_stop
 *
 * Block indexing is lazy: [currentBlockIndex] starts at -1 and advances only when
 * a block is actually opened. Thinking blocks come first when present, then exactly
 * one text block (per Anthropic spec, all assistant text aggregates into a single
 * block), then one tool_use block per tool call when buffered tool emission fires.
 */
internal class AnthropicMessagesFormat(
  private val modelName: String,
  private val requestModelId: String,
  override val stopSequences: List<String>?,
  private val tools: List<ToolSpec>?,
  private val hasSchemaInjection: Boolean,
  // Verbose-debug only: when true, log SSE event counter + first-event timing so
  // disconnect-vs-stall cases can be distinguished post-hoc. Cheap (one Log.i per
  // event), but still gated to avoid spamming logcat in normal use.
  private val verboseDebug: Boolean = false,
) : StreamingFormat {
  private val msgId = "msg_${java.util.UUID.randomUUID().toString().replace("-", "").take(24)}"
  override val sourceTag = "executeStreaming_messages"
  // Same buffering rule as ChatCompletions: tools without schema injection require
  // the full text to parse tool calls before any output is emitted.
  override val bufferAllTokens = tools != null && !hasSchemaInjection

  private var currentBlockIndex = -1
  private var currentBlockOpen = false
  private var currentBlockKind: String? = null  // "thinking" | "text" | "tool_use"

  // Accumulated thinking text for the open block — feeds the signature emitted in
  // the signature_delta event that must precede content_block_stop for a thinking block.
  private val thinkingBuffer = StringBuilder()

  private var sseEventCount = 0
  private var firstEmitNanos = 0L
  private val createdNanos = System.nanoTime()

  private suspend fun emitSse(writer: SseWriter, eventName: String, payload: String) {
    writer.emit(ResponseRenderer.emitSseEvent(eventName, payload))
    sseEventCount += 1
    if (verboseDebug && firstEmitNanos == 0L) {
      firstEmitNanos = System.nanoTime()
      val ms = (firstEmitNanos - createdNanos) / 1_000_000
      Log.i(TAG, "ANTHROPIC_SSE first_emit msgId=$msgId firstEventMs=$ms event=$eventName")
    }
  }

  override val emitsHeaderEarly: Boolean = true

  override suspend fun emitHeader(writer: SseWriter) {
    val escapedModel = BridgeUtils.escapeSseText(requestModelId)
    val payload =
      """{"type":"message_start","message":{"id":"$msgId","type":"message","role":"assistant","model":"$escapedModel","content":[],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":0,"output_tokens":0}}}"""
    emitSse(writer, "message_start", payload)
  }

  override suspend fun emitPing(writer: SseWriter) {
    // Spec: https://docs.anthropic.com/en/api/messages-streaming#ping-events
    emitSse(writer, "ping", """{"type":"ping"}""")
  }

  private suspend fun openBlockIfNeeded(writer: SseWriter, kind: String) {
    if (currentBlockOpen && currentBlockKind == kind) return
    if (currentBlockOpen) closeCurrentBlock(writer)
    currentBlockIndex += 1
    currentBlockOpen = true
    currentBlockKind = kind
    val blockJson = when (kind) {
      "thinking" -> """{"type":"thinking","thinking":""}"""
      "text" -> """{"type":"text","text":""}"""
      else -> """{"type":"$kind"}"""
    }
    val payload = """{"type":"content_block_start","index":$currentBlockIndex,"content_block":$blockJson}"""
    emitSse(writer, "content_block_start", payload)
  }

  private suspend fun closeCurrentBlock(writer: SseWriter) {
    if (!currentBlockOpen) return
    // Spec event order for a thinking block: thinking_delta events → one
    // signature_delta → content_block_stop. The signature is a local opaque digest
    // (see AnthropicConverter.localThinkingSignature) — clients replay it verbatim;
    // this server drops echoed thinking blocks so it is never verified.
    if (currentBlockKind == "thinking") {
      val signature = AnthropicConverter.localThinkingSignature(thinkingBuffer.toString())
      thinkingBuffer.clear()
      val sigPayload =
        """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"signature_delta","signature":"${BridgeUtils.escapeSseText(signature)}"}}"""
      emitSse(writer, "content_block_delta", sigPayload)
    }
    val payload = """{"type":"content_block_stop","index":$currentBlockIndex}"""
    emitSse(writer, "content_block_stop", payload)
    currentBlockOpen = false
    currentBlockKind = null
  }

  override suspend fun emitThinkingDelta(writer: SseWriter, text: String) {
    // Strip the literal <think>...</think> wrappers that the OAI streaming path injects;
    // Anthropic clients want the raw thinking text in a typed thinking block.
    val cleaned = text.removePrefix("<think>")
    if (cleaned.isEmpty()) return
    openBlockIfNeeded(writer, "thinking")
    thinkingBuffer.append(cleaned)
    val esc = BridgeUtils.escapeSseText(cleaned)
    val payload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"thinking_delta","thinking":"$esc"}}"""
    emitSse(writer, "content_block_delta", payload)
  }

  override suspend fun emitContentDelta(writer: SseWriter, text: String) {
    // Strip the </think> close tag the OAI path emits at the thinking→text boundary.
    val cleaned = text.removePrefix("</think>")
    if (cleaned.isEmpty()) return
    openBlockIfNeeded(writer, "text")
    val esc = BridgeUtils.escapeSseText(cleaned)
    val payload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"text_delta","text":"$esc"}}"""
    emitSse(writer, "content_block_delta", payload)
  }

  override suspend fun emitThinkingClose(writer: SseWriter) {
    // No-op: openBlockIfNeeded("text") will close the thinking block on the next
    // content delta. Keeping this idempotent matches the OAI format's behavior.
    if (currentBlockOpen && currentBlockKind == "thinking") closeCurrentBlock(writer)
  }

  override suspend fun emitCancellation(writer: SseWriter, headerWritten: Boolean) {
    if (!headerWritten) emitHeader(writer)
    if (currentBlockOpen) closeCurrentBlock(writer)
    val delta = """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"input_tokens":0,"output_tokens":0}}"""
    emitSse(writer, "message_delta", delta)
    emitSse(writer, "message_stop", """{"type":"message_stop"}""")
    if (verboseDebug) {
      val totalMs = (System.nanoTime() - createdNanos) / 1_000_000
      Log.i(TAG, "ANTHROPIC_SSE cancellation msgId=$msgId totalEvents=$sseEventCount totalMs=$totalMs")
    }
    writer.finish()
  }

  /**
   * Emit an Anthropic-shaped mid-stream error event. The Anthropic spec defines:
   *
   *   event: error
   *   data: {"type":"error","error":{"type":"<api_type>","message":"<text>"}}
   *
   * No `[DONE]` sentinel and no `message_stop` after — the SDK closes the stream
   * on `event: error`. The server's per-kind suggestion (e.g. "Increase the
   * Chat Completions timeout in Settings → Advanced") is appended into the
   * message string because the Anthropic schema has no separate suggestion field.
   */
  override suspend fun emitError(
    writer: SseWriter,
    enrichedMessage: String,
    suggestion: String?,
    kind: ErrorKind,
    oaiErrorJson: String,
    headerWritten: Boolean,
  ) {
    if (!headerWritten) {
      // Some Anthropic SDKs require message_start before any other event.
      try { emitHeader(writer) } catch (_: Exception) { /* writer may be closed */ }
    }
    if (currentBlockOpen) {
      try { closeCurrentBlock(writer) } catch (_: Exception) { /* same */ }
    }
    val anthropicErrorType = mapErrorKindToAnthropicType(kind)
    // enrichedMessage already contains the suggestion appended by enrichLlmError
    // ("$error — $suggestion"), so do NOT append it again here.
    val payload = """{"type":"error","error":{"type":"${BridgeUtils.escapeSseText(anthropicErrorType)}","message":"${BridgeUtils.escapeSseText(enrichedMessage)}"}}"""
    emitSse(writer, "error", payload)
    if (verboseDebug) {
      val totalMs = (System.nanoTime() - createdNanos) / 1_000_000
      Log.i(TAG, "ANTHROPIC_SSE error msgId=$msgId errorType=$anthropicErrorType totalEvents=$sseEventCount totalMs=$totalMs")
    }
  }

  override fun buildLogErrorJson(enrichedMessage: String, suggestion: String?, kind: ErrorKind, oaiErrorJson: String): String {
    val anthropicErrorType = mapErrorKindToAnthropicType(kind)
    // enrichedMessage already includes the suggestion via enrichLlmError.
    return ResponseRenderer.renderAnthropicError(anthropicErrorType, enrichedMessage)
  }

  private fun mapErrorKindToAnthropicType(kind: ErrorKind): String = when (kind) {
    ErrorKind.CONTEXT_OVERFLOW -> "invalid_request_error"
    ErrorKind.TIMEOUT -> "api_error"
    ErrorKind.MODEL_NOT_FOUND -> "not_found_error"
    ErrorKind.MODEL_FILES_MISSING -> "not_found_error"
    ErrorKind.MODEL_INSTANCE_NULL -> "overloaded_error"
    ErrorKind.OOM -> "overloaded_error"
    ErrorKind.PORT_BIND_FAILURE -> "api_error"
    ErrorKind.IMAGE_DECODE_FAILED -> "invalid_request_error"
    else -> "api_error"
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

    // Buffered path: open/close blocks synthetically so the client sees a valid
    // event sequence even though no progressive deltas were emitted.
    if (bufferAllTokens) {
      if (fullThinking.isNotEmpty()) {
        openBlockIfNeeded(writer, "thinking")
        thinkingBuffer.append(fullThinking)
        val esc = BridgeUtils.escapeSseText(fullThinking)
        val payload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"thinking_delta","thinking":"$esc"}}"""
        emitSse(writer, "content_block_delta", payload)
        closeCurrentBlock(writer)
      }
      if (fullText.isNotEmpty()) {
        openBlockIfNeeded(writer, "text")
        val esc = BridgeUtils.escapeSseText(fullText)
        val payload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"text_delta","text":"$esc"}}"""
        emitSse(writer, "content_block_delta", payload)
        closeCurrentBlock(writer)
      }
    } else {
      // Progressive path: close whichever block is still open from the last delta.
      if (currentBlockOpen) closeCurrentBlock(writer)
    }

    // Tool blocks emitted last — one block per call. Each block carries the id
    // and name in content_block_start; the JSON arguments arrive as a single
    // input_json_delta because the runtime emits tool calls atomically (no
    // partial_json streaming today).
    if (parsedToolCalls.isNotEmpty()) {
      if (currentBlockOpen) closeCurrentBlock(writer)
      for (call in parsedToolCalls) {
        currentBlockIndex += 1
        currentBlockOpen = true
        currentBlockKind = "tool_use"
        val startPayload = buildString {
          append("""{"type":"content_block_start","index":""")
          append(currentBlockIndex)
          append(""","content_block":{"type":"tool_use","id":"""")
          append(BridgeUtils.escapeSseText(call.id))
          append("""",""")
          append(""""name":"""")
          append(BridgeUtils.escapeSseText(call.function.name))
          append("""",""")
          append(""""input":{}}}""")
        }
        emitSse(writer, "content_block_start", startPayload)
        val argsEsc = BridgeUtils.escapeSseText(call.function.arguments.ifBlank { "{}" })
        val deltaPayload = """{"type":"content_block_delta","index":$currentBlockIndex,"delta":{"type":"input_json_delta","partial_json":"$argsEsc"}}"""
        emitSse(writer, "content_block_delta", deltaPayload)
        closeCurrentBlock(writer)
      }
    }

    // Final message_delta + message_stop.
    val stopReason = when {
      stopSequenceTriggered -> "stop_sequence"
      parsedToolCalls.isNotEmpty() -> "tool_use"
      FinishReason.infer(completionTokens, maxTokens) == FinishReason.LENGTH -> "max_tokens"
      else -> "end_turn"
    }
    val stopSequenceField = if (stopReason == "stop_sequence" && matchedStopSequence != null) {
      "\"" + BridgeUtils.escapeSseText(matchedStopSequence) + "\""
    } else "null"
    val deltaPayload = """{"type":"message_delta","delta":{"stop_reason":"$stopReason","stop_sequence":$stopSequenceField},"usage":{"input_tokens":$promptTokens,"output_tokens":$completionTokens}}"""
    emitSse(writer, "message_delta", deltaPayload)
    emitSse(writer, "message_stop", """{"type":"message_stop"}""")
    if (verboseDebug) {
      val totalMs = (System.nanoTime() - createdNanos) / 1_000_000
      Log.i(TAG, "ANTHROPIC_SSE complete msgId=$msgId stopReason=$stopReason promptTokens=$promptTokens completionTokens=$completionTokens totalEvents=$sseEventCount totalMs=$totalMs")
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
    // Logs render the Anthropic-shaped response shell so the Logs tab shows a
    // coherent body. The matched stop sequence is unknown here (the streaming
    // session's matchedStopSequence is not threaded into buildLogResponseJson),
    // so we emit `null` — the wire response set the field correctly when needed.
    val (thinking, visibleText) = if (combinedText.startsWith("<think>")) {
      val close = combinedText.indexOf("</think>")
      if (close >= 0) {
        combinedText.substring("<think>".length, close) to combinedText.substring(close + "</think>".length)
      } else "" to combinedText
    } else "" to combinedText

    val contentBuilder = StringBuilder()
    if (thinking.isNotEmpty()) {
      contentBuilder.append("""{"type":"thinking","thinking":"""")
      contentBuilder.append(BridgeUtils.escapeSseText(thinking))
      contentBuilder.append(""""},""")
    }
    contentBuilder.append("""{"type":"text","text":"""")
    contentBuilder.append(BridgeUtils.escapeSseText(visibleText))
    contentBuilder.append(""""}""")
    for (call in parsedToolCalls) {
      contentBuilder.append(""",{"type":"tool_use","id":"""")
      contentBuilder.append(BridgeUtils.escapeSseText(call.id))
      contentBuilder.append("""",""")
      contentBuilder.append(""""name":"""")
      contentBuilder.append(BridgeUtils.escapeSseText(call.function.name))
      contentBuilder.append(""""}""")
    }

    val stopReason = when {
      parsedToolCalls.isNotEmpty() -> "tool_use"
      else -> "end_turn"
    }
    return """{"id":"$msgId","type":"message","role":"assistant","model":"${BridgeUtils.escapeSseText(requestModelId)}","content":[$contentBuilder],"stop_reason":"$stopReason","stop_sequence":null,"usage":{"input_tokens":$promptTokens,"output_tokens":$completionTokens}}"""
  }

  override fun buildLogEventSuffix(parsedToolCalls: List<ToolCall>): String {
    if (parsedToolCalls.isEmpty()) return ""
    return " tool_calls=${parsedToolCalls.joinToString(",") { it.function.name }} count=${parsedToolCalls.size}"
  }

  override fun buildAssistantText(fullText: CharSequence, fullThinking: CharSequence): String =
    fullText.toString()
}
