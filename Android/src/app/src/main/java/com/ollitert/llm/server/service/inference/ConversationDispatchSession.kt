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
import com.ollitert.llm.server.data.prefs.thinkingBudgetTokens
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.runtime.LlmModelInstance
import com.ollitert.llm.server.runtime.ModelInstancePool
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
  // Multi-instance pool: the specific instance this request was routed to
  // (null = single-instance model; helper falls back to model.instance).
  private val instance: LlmModelInstance? = null,
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
  // OpenAI frequency/presence penalties applied natively for this session.
  private val frequencyPenalty: Double? = null,
  private val presencePenalty: Double? = null,
  // Per-turn output token cap applied natively for this session.
  private val maxOutputToken: Int? = null,
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
        instance = instance,
      )
    }
  }

  /**
   * Deferred retry executor. The SDK's onError callback fires on a native
   * thread — calling Conversation.close() from inside that frame deadlocks
   * (the native side waits for the callback to return before completing the
   * close). Recovery + the retry invoke must run OUTSIDE the callback frame.
   */
  private companion object {
    val RETRY_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
      Thread(r, "OlliteRT-FirstInvokeRetry").apply { isDaemon = true }
    }
  }

  /** Runs one native inference turn on the executor thread. Signature matches [InferenceGateway.InferenceFn]. */
  fun runInference(
    input: String,
    onPartial: (partial: String, done: Boolean, thought: String?) -> Unit,
    onError: (message: String) -> Unit,
  ) {
    var attempt = 0
    var emittedAny = false
    ServerLlmModelHelper.runInference(
      model = model,
      input = input,
      resultListener = { partial, done, thought ->
        if (partial.isNotEmpty() || thought?.isNotEmpty() == true) emittedAny = true
        if (done) {
          val idx = instance?.index
          if (idx != null) ModelInstancePool.recordSuccess(model.name, idx)
        }
        onPartial(partial, done, thought)
      },
      cleanUpListener = {},
      onError = { message ->
        // Native first-invoke failure ("Failed to invoke the compiled model",
        // Status 13): the GPU executor transiently refuses a fresh
        // conversation's first invoke right after engine init — first
        // request after load/reload hits it. No tokens have been emitted,
        // so reset the (possibly half-initialized) conversation and retry
        // the SAME request once. Covers blocking + streaming paths.
        if (!emittedAny && attempt == 0 && isNativeFirstInvokeFailure(message)) {
          attempt = 1
          val idx = instance?.index
          if (idx != null) ModelInstancePool.recordFailure(model.name, idx)
          Log.i(
            debugLogTag,
            "Native first-invoke failure (${message.take(80)}) — resetting conversation and retrying once (requestId=$requestId, model=${model.name}, instance=${instance?.index})",
          )
          RequestLogStore.addEvent(
            "First-invoke native failure — transparent retry (model=${model.name})",
            modelName = model.name,
            category = com.ollitert.llm.server.data.model.EventCategory.MODEL,
          )
          // Deferred: recovery closes a conversation, which deadlocks if
          // called from inside the SDK callback frame (see RETRY_EXECUTOR).
          RETRY_EXECUTOR.execute {
            scheduleFirstInvokeRetry(input, onPartial, onError, message)
          }
        } else {
          val idx = instance?.index
          if (idx != null) ModelInstancePool.recordFailure(model.name, idx)
          onError(message)
        }
      },
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
      repetitionPenaltyConfig = sdkRepetitionPenaltyConfig,
      maxOutputToken = maxOutputToken,
      instance = instance,
    )
  }

  /**
   * First-invoke-failure retry, run on the [RETRY_EXECUTOR] thread (never the
   * SDK callback frame, where closing a conversation deadlocks). When the
   * failed instance was just marked unhealthy (repeated failures — its GPU
   * allocation is broken for the life of the pool), its native resources are
   * freed here and the retry re-routes to a healthy sibling instance;
   * otherwise the same instance's conversation is recovered and re-invoked.
   */
  private fun scheduleFirstInvokeRetry(
    input: String,
    onPartial: (partial: String, done: Boolean, thought: String?) -> Unit,
    onError: (message: String) -> Unit,
    originalError: String,
  ) {
    val failed = instance
    var retryTarget = failed
    var recovered = false
    if (failed != null && failed.markedUnhealthy && ModelInstancePool.countFor(model.name) > 1) {
      // This engine fails every invoke — free its native resources (routing
      // now skips it, and the GPU memory comes back) and retry on a sibling.
      try { failed.conversation.close() } catch (_: Exception) {}
      try { failed.engine.close() } catch (_: Exception) {}
      Log.i(
        debugLogTag,
        "Evicting unhealthy instance ${failed.index} of '${model.name}' (native resources freed, retrying on sibling) (requestId=$requestId)",
      )
      RequestLogStore.addEvent(
        "Evicted unhealthy instance ${failed.index} of '${model.name}'",
        level = com.ollitert.llm.server.data.model.LogLevel.WARNING,
        modelName = model.name,
        category = com.ollitert.llm.server.data.model.EventCategory.MODEL,
      )
      val sibling = ModelInstancePool.nextFor(model.name)?.instance
      if (sibling != null) {
        try {
          // Fresh conversation on the sibling; the null cache generation also
          // invalidates the stale per-model cache entry that described the
          // evicted conversation.
          ServerLlmModelHelper.resetConversation(
            model,
            supportImage = supportImage,
            supportAudio = supportAudio,
            systemInstruction = resolveSystemInstruction(),
            tools = schemaInjectionProviders,
            initialMessages = schemaInjectionMessages,
            enableConversationConstrainedDecoding = schemaInjectionProviders.isNotEmpty(),
            conversationCacheGeneration = null,
            instance = sibling,
          )
          retryTarget = sibling
          recovered = true
        } catch (siblingFailure: Exception) {
          Log.w(debugLogTag, "Sibling instance reset before retry failed: ${siblingFailure.message}")
        }
      }
    }
    if (!recovered) {
      recovered = try {
        recoverConversation()
        true
      } catch (recoveryFailure: Exception) {
        Log.w(debugLogTag, "Conversation recovery before retry failed: ${recoveryFailure.message}")
        false
      }
    }
    if (!recovered) {
      onError(originalError)
      return
    }
    ServerLlmModelHelper.runInference(
      model = model,
      input = input,
      resultListener = { partial, done, thought ->
        if (done) {
          val idx = retryTarget?.index
          if (idx != null) ModelInstancePool.recordSuccess(model.name, idx)
        }
        onPartial(partial, done, thought)
      },
      cleanUpListener = {},
      onError = { msg ->
        val idx = retryTarget?.index
        if (idx != null) ModelInstancePool.recordFailure(model.name, idx)
        onError(msg)
      },
      images = images,
      audioClips = audioClips,
      extraContext = extraContext,
      incrementalUserText = preparedIncrementalUserText,
      conversationCacheGeneration = if (retryTarget === instance) preparedCacheGeneration else null,
      onNativeToolCalls = if (schemaInjectionProviders.isNotEmpty()) { calls ->
        capturedNativeToolCalls.set(calls)
      } else null,
      responseFormatSchema = responseFormatSchema,
      thinkingConfig = sdkThinkingConfig,
      repetitionPenaltyConfig = sdkRepetitionPenaltyConfig,
      maxOutputToken = maxOutputToken,
      instance = retryTarget,
    )
  }

  /** True for the native "Failed to invoke the compiled model" first-invoke failure. */
  private fun isNativeFirstInvokeFailure(message: String): Boolean =
    message.contains("Failed to invoke the compiled model")

  /**
   * Native per-turn thinking channel config.
   *
   * Precedence: an explicit request budget (Anthropic budget_tokens) wins; otherwise
   * the model's configured per-model budget applies when thinking is enabled; with
   * neither, thinking keeps using the enable_thinking template variable so existing
   * model behavior is unchanged.
   */
  private val sdkThinkingConfig: com.google.ai.edge.litertlm.ThinkingConfig?
    get() = when {
      thinkingBudgetTokens != null ->
        com.google.ai.edge.litertlm.ThinkingConfig(enableThinking = true, thinkingTokenBudget = thinkingBudgetTokens)
      enableThinking -> model.configValues.thinkingBudgetTokens()?.takeIf { it > 0 }
        ?.let { com.google.ai.edge.litertlm.ThinkingConfig(enableThinking = true, thinkingTokenBudget = it) }
      else -> null
    }

  /** Native repetition penalties; null when the request carries neither penalty. */
  private val sdkRepetitionPenaltyConfig: com.google.ai.edge.litertlm.RepetitionPenaltyConfig?
    get() = if (frequencyPenalty == null && presencePenalty == null) {
      null
    } else {
      com.google.ai.edge.litertlm.RepetitionPenaltyConfig(
        frequencyPenalty = frequencyPenalty?.toFloat(),
        presencePenalty = presencePenalty?.toFloat(),
      )
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
      instance = instance,
    )
  }

  /**
   * Records this request's instance as the owner of the model's cached
   * conversation (KV-cache incremental reuse). Called on inference completion:
   * whatever conversation this instance just built/extended is now what the
   * per-model-name cache describes, so extend-candidates must route here next.
   * No-op for single-instance models (owner is always 0).
   */
  fun commitConversationOwner() {
    val idx = instance?.index ?: return
    ModelInstancePool.setOwner(model.name, idx)
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
