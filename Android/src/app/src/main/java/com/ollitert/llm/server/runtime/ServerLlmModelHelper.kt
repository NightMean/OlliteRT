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

package com.ollitert.llm.server.runtime

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ToolProvider
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_SHORT_CHARS
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.repository.RequestLogStore
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

typealias ResultListener =
  (partialResult: String, done: Boolean, partialThinkingResult: String?) -> Unit

typealias CleanUpListener = () -> Unit

private const val TAG = "OlliteRT.ModelHelper"

internal class ModelLoadDiagnosticState {
  private val gpuSamplerWarningClaimed = AtomicBoolean(false)

  fun claimGpuSamplerWarning(): Boolean = gpuSamplerWarningClaimed.compareAndSet(false, true)
}

data class LlmModelInstance(val engine: Engine, var conversation: Conversation) {
  /** Diagnostics scoped to this loaded Engine, preserved across Conversation resets. */
  internal val diagnostics = ModelLoadDiagnosticState()
}

/**
 * High-level coordinator facade for native LiteRT-LM model execution, lifecycle,
 * and conversation session management.
 */
object ServerLlmModelHelper {
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = ConcurrentHashMap()

  // Backwards-compatible typealiases for callers and tests
  typealias ConversationTurn = ConversationCacheTracker.ConversationTurn
  typealias ConversationCacheEntry = ConversationCacheTracker.ConversationCacheEntry
  typealias ConversationCacheClaim = ConversationCacheTracker.ConversationCacheClaim

  fun getCachedTurns(modelName: String): ConversationCacheEntry? =
    ConversationCacheTracker.getCachedTurns(modelName)

  fun claimCachedTurns(modelName: String): ConversationCacheClaim =
    ConversationCacheTracker.claimCachedTurns(modelName)

  fun updateCachedTurns(modelName: String, entry: ConversationCacheEntry) =
    ConversationCacheTracker.updateCachedTurns(modelName, entry)

  fun publishCachedTurns(modelName: String, generation: Long, entry: ConversationCacheEntry) =
    ConversationCacheTracker.publishCachedTurns(modelName, generation, entry)

  fun discardCachedTurns(modelName: String, generation: Long) =
    ConversationCacheTracker.discardCachedTurns(modelName, generation)

  fun invalidateCachedTurns(modelName: String) =
    ConversationCacheTracker.invalidateCachedTurns(modelName)

  /**
   * Initialize the native engine for [model].
   *
   * CONTRACT: [onDone] is invoked SYNCHRONOUSLY before this function returns —
   * callers may capture the error string in a local `var` set inside the
   * callback and read it immediately after the call. Do not make the callback
   * asynchronous without migrating every caller to a suspending/result API.
   */
  @OptIn(ExperimentalApi::class)
  fun initialize(
    context: Context,
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents? = null,
    tools: List<ToolProvider> = listOf(),
    initialMessages: List<Message> = listOf(),
    enableConversationConstrainedDecoding: Boolean = false,
    coroutineScope: CoroutineScope? = null,
    configOverrides: Map<String, Any>? = null,
  ) {
    LiteRtEngineFactory.createAndInitEngine(
      context = context,
      model = model,
      supportImage = supportImage,
      supportAudio = supportAudio,
      configOverrides = configOverrides,
      systemInstruction = systemInstruction,
      tools = tools,
      initialMessages = initialMessages,
      enableConversationConstrainedDecoding = enableConversationConstrainedDecoding,
      onDone = onDone,
    )
  }

  @OptIn(ExperimentalApi::class)
  fun resetConversation(
    model: Model,
    supportImage: Boolean = false,
    supportAudio: Boolean = false,
    systemInstruction: Contents? = null,
    tools: List<ToolProvider> = listOf(),
    initialMessages: List<Message> = listOf(),
    enableConversationConstrainedDecoding: Boolean = false,
    conversationCacheGeneration: Long? = null,
  ) {
    try {
      Log.d(TAG, "Resetting conversation for model '${model.name}'")

      if (conversationCacheGeneration != null) {
        discardCachedTurns(model.name, conversationCacheGeneration)
      } else {
        invalidateCachedTurns(model.name)
      }

      val instance = model.instance as? LlmModelInstance ?: return

      try {
        instance.conversation.close()
      } catch (e: Exception) {
        Log.w(TAG, "Old conversation close failed (proceeding with new): ${e.message}")
      }

      instance.conversation = LiteRtEngineFactory.createConversation(
        engine = instance.engine,
        model = model,
        systemInstruction = systemInstruction,
        tools = tools,
        initialMessages = initialMessages,
        enableConversationConstrainedDecoding = enableConversationConstrainedDecoding,
      )

      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to reset conversation completely", e)
      RequestLogStore.addEvent(
        "Failed to reset conversation: ${e.message?.take(LOG_ERROR_PREVIEW_SHORT_CHARS) ?: "Unknown error"}",
        level = LogLevel.ERROR,
        modelName = model.name,
      )
      try { (model.instance as? LlmModelInstance)?.engine?.close() } catch (closeEx: Exception) {
        Log.w(TAG, "Engine.close() failed during conversation reset", closeEx)
      }
      cleanUpListeners.remove(model.name)?.invoke()
      model.instance = null
      System.gc()
    }
  }

  /** Safe cleanup: close native resources with try-catch, null instance, hint GC. */
  fun safeCleanup(model: Model) {
    invalidateCachedTurns(model.name)
    try {
      cleanUp(model) {}
    } catch (e: Exception) {
      Log.w(TAG, "Error during model cleanup: ${e.message}")
    }
    model.instance = null
    System.gc()
  }

  fun cleanUp(model: Model, onDone: () -> Unit) {
    val instance = model.instance as? LlmModelInstance ?: return

    try {
      instance.conversation.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the conversation: ${e.message}")
    }

    try {
      instance.engine.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the engine: ${e.message}")
    }

    cleanUpListeners.remove(model.name)?.invoke()
    model.instance = null

    onDone()
    Log.d(TAG, "Clean up done.")
  }

  fun stopResponse(model: Model) {
    val instance = model.instance as? LlmModelInstance ?: return
    try {
      instance.conversation.cancelProcess()
    } catch (_: IllegalStateException) {
      Log.d(TAG, "stopResponse: conversation already closed, skipping cancel")
    }
  }

  fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit = {},
    images: List<ByteArray> = listOf(),
    audioClips: List<ByteArray> = listOf(),
    coroutineScope: CoroutineScope? = null,
    extraContext: Map<String, String>? = null,
    onNativeToolCalls: ((List<com.google.ai.edge.litertlm.ToolCall>) -> Unit)? = null,
    incrementalUserText: String? = null,
    conversationCacheGeneration: Long? = null,
    // When non-null, the response is natively constrained to this JSON schema
    // (LiteRT-LM ResponseFormat) — requires enableResponseFormat=true on the
    // conversation config, which createConversation always sets.
    responseFormatSchema: String? = null,
    // When non-null, natively controls the thinking channel for this turn
    // (enable flag + reasoning token budget).
    thinkingConfig: com.google.ai.edge.litertlm.ThinkingConfig? = null,
    // When non-null, applies native repetition/presence/frequency penalties.
    repetitionPenaltyConfig: com.google.ai.edge.litertlm.RepetitionPenaltyConfig? = null,
    // Per-turn output token cap applied natively on top of the engine-level
    // maxNumTokens so client max_tokens requests are honored exactly.
    maxOutputToken: Int? = null,
  ) {
    val instance = model.instance as? LlmModelInstance
    if (instance == null) {
      onError("LlmModelInstance is not initialized.")
      return
    }

    cleanUpListeners.putIfAbsent(model.name, cleanUpListener)
    val conversation = instance.conversation
    val sdkResponseFormat = responseFormatSchema?.let {
      com.google.ai.edge.litertlm.ResponseFormat.json(it)
    }

    // Incremental path: send just the new user turn as a Message.user
    if (incrementalUserText != null) {
      conversation.sendMessageAsync(
        Message.user(incrementalUserText),
        object : MessageCallback {
          override fun onMessage(message: Message) {
            if (onNativeToolCalls != null && message.toolCalls.isNotEmpty()) {
              onNativeToolCalls.invoke(message.toolCalls)
            }
            resultListener(message.toString(), false, message.channels["thought"])
          }
          override fun onDone() { resultListener("", true, null) }
          override fun onError(throwable: Throwable) {
            if (conversationCacheGeneration != null) {
              discardCachedTurns(model.name, conversationCacheGeneration)
            } else {
              invalidateCachedTurns(model.name)
            }
            if (throwable is CancellationException) {
              Log.i(TAG, "The inference is cancelled (incremental).")
              resultListener("", true, null)
            } else {
              Log.e(TAG, "Incremental inference onError for '${model.name}': " +
                "[${throwable::class.simpleName}] ${throwable.message}", throwable)
              onError("Error: ${throwable.message}")
            }
          }
        },
        extraContext ?: emptyMap(),
        maxOutputToken = maxOutputToken,
        repetitionPenaltyConfig = repetitionPenaltyConfig,
        thinkingConfig = thinkingConfig,
        responseFormat = sdkResponseFormat,
      )
      return
    }

    val contents = MultimodalContentBuilder.buildContents(
      input = input,
      images = images,
      audioClips = audioClips,
    )

    conversation.sendMessageAsync(
      contents,
      object : MessageCallback {
        override fun onMessage(message: Message) {
          if (onNativeToolCalls != null && message.toolCalls.isNotEmpty()) {
            onNativeToolCalls.invoke(message.toolCalls)
          }
          resultListener(message.toString(), false, message.channels["thought"])
        }

        override fun onDone() {
          resultListener("", true, null)
        }

        override fun onError(throwable: Throwable) {
          if (throwable is CancellationException) {
            Log.i(TAG, "The inference is cancelled.")
            resultListener("", true, null)
          } else {
            Log.e(TAG, "Inference onError for '${model.name}': " +
              "[${throwable::class.simpleName}] ${throwable.message}", throwable)
            onError("Error: ${throwable.message}")
          }
        }
      },
      extraContext ?: emptyMap(),
      maxOutputToken = maxOutputToken,
      repetitionPenaltyConfig = repetitionPenaltyConfig,
      thinkingConfig = thinkingConfig,
      responseFormat = sdkResponseFormat,
    )
  }
}
