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

package com.ollitert.llm.server.service.inference

import android.util.Log
import com.google.ai.edge.litertlm.Contents
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.isThinkingEnabled
import com.ollitert.llm.server.data.model.llmSupportThinking
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared per-request dispatch state used by BOTH the blocking path
 * ([InferenceRunner.runLlm]) and the streaming path
 * ([InferenceStreamingCoordinator.streamInference]).
 *
 * Owns the request-scoped mutable state that must stay consistent across the
 * `resetConversation` / `runInference` / `recoverConversation` lifecycle hooks handed
 * to [InferenceGateway]: thinking-mode resolution, per-request config snapshot
 * capture/restore, KV-cache incremental-reuse preparation, native tool-call capture,
 * and the system-instruction resolution rules. Extracting it here guarantees the two
 * execution paths cannot drift apart semantically.
 *
 * One instance per HTTP request. `resetConversation` and `recoverConversation` run
 * under the inference lock (invoked by the gateway); `capturedNativeToolCalls` is
 * atomic because it bridges the executor thread and the HTTP coroutine.
 */
internal class ConversationDispatchSession(
  private val debugLogTag: String,
  private val incrementalReuseLabel: String,
  private val model: Model,
  private val requestId: String,
  private val images: List<ByteArray>,
  private val audioClips: List<ByteArray>,
  private val supportImage: Boolean,
  private val supportAudio: Boolean,
  enableThinkingOverride: Boolean?,
  private val suppressPerModelSystem: Boolean,
  private val buildSystemInstruction: (modelName: String) -> Contents?,
  private val configSnapshot: Map<String, Any>?,
  incrementalUserText: String?,
  conversationCacheGeneration: Long?,
  private val prepareConversation: (() -> ConversationPreparation)?,
  private val schemaInjectionProviders: List<com.google.ai.edge.litertlm.ToolProvider>,
  private val schemaInjectionMessages: List<com.google.ai.edge.litertlm.Message>,
  private val reinitIfNeeded: () -> String?,
  private val logId: String?,
  // Non-null = natively constrain the response to this JSON schema for this session.
  private val responseFormatSchema: String? = null,
  // Reasoning token budget override (Anthropic thinking.budget_tokens). Implies
  // thinking enabled for this turn via the native ThinkingConfig channel.
  private val thinkingBudgetTokens: Int? = null,
) {
  /**
   * Per-request thinking override resolved against model capability. Forced-on requests
   * on a model without thinking support are silently downgraded to off (capability wins).
   */
  val enableThinking: Boolean =
    if (model.llmSupportThinking) {
      enableThinkingOverride ?: model.isThinkingEnabled
    } else {
      false
    }
  val extraContext: Map<String, String>? =
    if (enableThinking) mapOf("enable_thinking" to "true") else null

  /** Native tool calls captured during inference when schema injection is active. */
  val capturedNativeToolCalls = AtomicReference<List<com.google.ai.edge.litertlm.ToolCall>?>(null)

  private var originalConfig: Map<String, Any>? = null
  var preparedIncrementalUserText: String? = incrementalUserText
    private set
  var preparedCacheGeneration: Long? = conversationCacheGeneration
    private set
  private var preparedSystemInstruction: Contents? = null

  /**
   * Prepares the conversation before dispatch: honours queued-cancellation, reinitializes
   * the model for vision/audio when needed, snapshots/restores per-request config
   * overrides, stages KV-cache incremental reuse, and resets the native conversation.
   * Runs under the inference lock.
   *
   * @param cancelledWhileQueued result of the caller's cancellation bridge check
   * @param onInferenceStarted invoked between model init and log update; callers hook
   *   their own started-markers here (metrics counter for blocking, [StreamState] for
   *   streaming)
   */
  fun resetConversation(cancelledWhileQueued: Boolean, onInferenceStarted: () -> Unit) {
    // Skip inference entirely if cancelled while queued.
    if (cancelledWhileQueued) {
      throw java.util.concurrent.CancellationException("cancelled_while_queued")
    }
    val initErr = reinitIfNeeded()
    if (initErr != null) throw RuntimeException("model_init_failed: $initErr")
    onInferenceStarted()
    if (logId != null) RequestLogStore.update(logId) { it.copy(isGenerating = true) }
    if (configSnapshot != null) {
      originalConfig = model.configValues
      model.configValues = configSnapshot
    }
    prepareConversation?.invoke()?.let { prepared ->
      preparedIncrementalUserText = prepared.incrementalUserText
      preparedCacheGeneration = prepared.cacheGeneration
      preparedSystemInstruction = prepared.systemInstruction
    }
    if (preparedIncrementalUserText != null) {
      Log.i(debugLogTag, "$incrementalReuseLabel requestId=$requestId model=${model.name} userTextLen=${preparedIncrementalUserText?.length}")
    } else {
      ServerLlmModelHelper.resetConversation(
        model,
        supportImage = supportImage,
        supportAudio = supportAudio,
        systemInstruction = resolveSystemInstruction(),
        tools = schemaInjectionProviders,
        initialMessages = schemaInjectionMessages,
        enableConversationConstrainedDecoding = schemaInjectionProviders.isNotEmpty(),
        conversationCacheGeneration = preparedCacheGeneration,
      )
    }
  }

  /** Runs one native inference turn on the executor thread. Signature matches [InferenceGateway.InferenceFn]. */
  fun runInference(
    input: String,
    onPartial: (partial: String, done: Boolean, thought: String?) -> Unit,
    onError: (message: String) -> Unit,
  ) {
    ServerLlmModelHelper.runInference(
      model = model,
      input = input,
      resultListener = { partial, done, thought -> onPartial(partial, done, thought) },
      cleanUpListener = {},
      onError = onError,
      images = images,
      audioClips = audioClips,
      extraContext = extraContext,
      incrementalUserText = preparedIncrementalUserText,
      conversationCacheGeneration = preparedCacheGeneration,
      onNativeToolCalls = if (schemaInjectionProviders.isNotEmpty()) { calls ->
        capturedNativeToolCalls.set(calls)
      } else null,
      responseFormatSchema = responseFormatSchema,
      thinkingConfig = sdkThinkingConfig,
    )
  }

  /**
   * Native per-turn thinking channel config. Only set when the request carries an
   * explicit token budget — without one, thinking keeps using the enable_thinking
   * template variable so existing model behavior is unchanged.
   */
  private val sdkThinkingConfig: com.google.ai.edge.litertlm.ThinkingConfig?
    get() = thinkingBudgetTokens?.let {
      com.google.ai.edge.litertlm.ThinkingConfig(enableThinking = true, thinkingTokenBudget = it)
    }

  /** Best-effort conversation recovery after a failed request; runs under the inference lock. */
  fun recoverConversation() {
    restoreOriginalConfigIfLoaded()
    ServerLlmModelHelper.resetConversation(
      model,
      supportImage = supportImage,
      supportAudio = supportAudio,
      systemInstruction = resolveSystemInstruction(),
      tools = schemaInjectionProviders,
      initialMessages = schemaInjectionMessages,
      conversationCacheGeneration = preparedCacheGeneration,
    )
  }

  /**
   * Restores the model's persisted config after a per-request snapshot override.
   * Only applied when a loaded model instance exists so a failed init never receives
   * restored values. Called by recovery and normal-completion hooks.
   */
  fun restoreOriginalConfigIfLoaded() {
    val snapshot = originalConfig ?: return
    if (model.instance != null) {
      model.configValues = snapshot
    }
  }

  /**
   * System-instruction precedence shared by reset and recovery: an explicit preparation
   * overrides everything; otherwise the per-model prompt is suppressed when the request
   * body already carries one; otherwise the configured per-model prompt is built.
   */
  private fun resolveSystemInstruction(): Contents? =
    if (prepareConversation != null) {
      preparedSystemInstruction
    } else if (suppressPerModelSystem) {
      null
    } else {
      buildSystemInstruction(model.prefsKey)
    }
}
