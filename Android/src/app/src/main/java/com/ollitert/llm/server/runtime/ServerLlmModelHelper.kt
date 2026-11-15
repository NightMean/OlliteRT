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
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.EmbeddingEngine
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

/**
 * Minimum gap (ms) between a conversation close on an Engine and the next
 * invoke of a fresh conversation on that Engine. Measured: invokes starting
 * within ~2.3s of a close fail with Status 13; after ~3.8s they succeed.
 * 3500ms sits between the two.
 */
private const val INVOKE_SETTLE_MIN_GAP_MS = 3500L

internal class ModelLoadDiagnosticState {
  private val gpuSamplerWarningClaimed = AtomicBoolean(false)

  fun claimGpuSamplerWarning(): Boolean = gpuSamplerWarningClaimed.compareAndSet(false, true)
}

data class LlmModelInstance(val engine: Engine, var conversation: Conversation) {
  /** Diagnostics scoped to this loaded Engine, preserved across Conversation resets. */
  internal val diagnostics = ModelLoadDiagnosticState()

  /**
   * Position in the model's multi-instance pool (assigned by
   * [com.ollitert.llm.server.runtime.ModelInstancePool] at registration; 0 for
   * single-instance models).
   */
  var index: Int = 0

  /**
   * Per-instance serialization lock. The native SDK is not thread-safe per
   * Engine, so concurrent requests are spread across instances and each
   * instance's generations are serialized on this lock (requests on DIFFERENT
   * instances run in parallel on different executor threads).
   */
  val inferenceLock = Any()

  /**
   * Monotonic timestamp of the last conversation close on this Engine (or
   * engine init, at construction). The native executor fails a fresh
   * conversation's first invoke (Status 13) when it starts within ~3s of a
   * conversation close on the same Engine — buffer teardown is async — so
   * [runInference] settles (waits) when this is recent.
   */
  var lastConversationClosedAtMs: Long = SystemClock.elapsedRealtime()

  /** Consecutive native invoke failures; reset to 0 by any successful invoke. */
  val consecutiveInvokeFailures = java.util.concurrent.atomic.AtomicInteger(0)

  /**
   * Set by [com.ollitert.llm.server.runtime.ModelInstancePool.recordFailure]
   * once this instance has accumulated enough consecutive failures to be
   * considered broken (GPU allocation failure at init, etc.). Round-robin
   * and owner routing skip marked instances.
   */
  @Volatile var markedUnhealthy: Boolean = false
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
    instanceCount: Int = 1,
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
      instanceCount = instanceCount,
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
    instance: LlmModelInstance? = null,
  ) {
    try {
      Log.d(TAG, "Resetting conversation for model '${model.name}'${if (instance != null) " (multi-instance target)" else ""}")

      if (conversationCacheGeneration != null) {
        discardCachedTurns(model.name, conversationCacheGeneration)
      } else {
        invalidateCachedTurns(model.name)
      }

      // Per-request target when the model runs multiple instances; otherwise
      // reset every loaded instance of the model.
      val targets = instance?.let { listOf(it) }
        ?: ModelInstancePool.instancesFor(model.name)
          .ifEmpty { listOfNotNull(model.instance as? LlmModelInstance) }

      for (inst in targets) {
        try {
          inst.conversation.close()
        } catch (e: Exception) {
          Log.w(TAG, "Old conversation close failed (proceeding with new): ${e.message}")
        }
        // Close is async in the native layer — record it so the next invoke
        // on this Engine settles if it starts before teardown finishes.
        inst.lastConversationClosedAtMs = SystemClock.elapsedRealtime()

        inst.conversation = LiteRtEngineFactory.createConversation(
          engine = inst.engine,
          model = model,
          systemInstruction = systemInstruction,
          tools = tools,
          initialMessages = initialMessages,
          enableConversationConstrainedDecoding = enableConversationConstrainedDecoding,
        )
      }

      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to reset conversation completely", e)
      RequestLogStore.addEvent(
        "Failed to reset conversation: ${e.message?.take(LOG_ERROR_PREVIEW_SHORT_CHARS) ?: "Unknown error"}",
        level = LogLevel.ERROR,
        modelName = model.name,
      )
      for (inst in ModelInstancePool.instancesFor(model.name).ifEmpty { listOfNotNull(model.instance as? LlmModelInstance) }) {
        try { inst.engine.close() } catch (closeEx: Exception) {
          Log.w(TAG, "Engine.close() failed during conversation reset", closeEx)
        }
      }
      ModelInstancePool.clear(model.name)
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
    // Embedding models store a bare EmbeddingEngine in instance (no conversation),
    // possibly several (multi-instance pool).
    (model.instance as? EmbeddingEngine)?.let { primaryEngine ->
      for (embeddingEngine in EmbeddingInstancePool.instancesFor(model.name).ifEmpty { listOf(primaryEngine) }) {
        try {
          embeddingEngine.close()
        } catch (e: Exception) {
          Log.e(TAG, "Failed to close the embedding engine: ${e.message}")
        }
      }
      EmbeddingInstancePool.clear(model.name)
      model.instance = null
      onDone()
      Log.d(TAG, "Clean up done (embedding engine).")
      return
    }
    // Close every loaded instance of the model (multi-instance pools), not just
    // the primary one in model.instance.
    val instances = ModelInstancePool.instancesFor(model.name)
      .ifEmpty { listOfNotNull(model.instance as? LlmModelInstance) }

    for (inst in instances) {
      try {
        inst.conversation.close()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to close the conversation: ${e.message}")
      }

      try {
        inst.engine.close()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to close the engine: ${e.message}")
      }
    }

    ModelInstancePool.clear(model.name)
    cleanUpListeners.remove(model.name)?.invoke()
    model.instance = null

    onDone()
    Log.d(TAG, "Clean up done.")
  }

  /**
   * Cancels in-flight generation. When [instance] is given (per-request routing
   * with multiple instances) only that instance's conversation is cancelled;
   * otherwise every loaded instance is cancelled (legacy stop-all behavior).
   */
  fun stopResponse(model: Model, instance: LlmModelInstance? = null) {
    val targets = instance?.let { listOf(it) }
      ?: ModelInstancePool.instancesFor(model.name)
        .ifEmpty { listOfNotNull(model.instance as? LlmModelInstance) }
    for (inst in targets) {
      try {
        inst.conversation.cancelProcess()
      } catch (_: IllegalStateException) {
        Log.d(TAG, "stopResponse: conversation already closed, skipping cancel")
      }
    }
  }

  /**
   * Burns one instance's first-invoke failure (the deterministic Status Code 13
   * "Failed to invoke the compiled model" on the first conversation of a freshly
   * loaded engine). The engine settles after any one of its conversations is
   * invoked, so each pool instance needs its own throwaway conversation to fail
   * (or pass) — a reset + fresh conversation afterwards is healthy. The returned
   * conversation is the one real requests will use, so it is left UNINVOKED: the
   * engine is warm, the conversation is fresh.
   *
   * Must run under [LlmModelInstance.inferenceLock] (native SDK is not
   * thread-safe per engine). Best-effort: logs and returns false on failure.
   */
  @OptIn(ExperimentalApi::class)
  fun burnFirstInvoke(
    model: Model,
    instance: LlmModelInstance,
    prompt: String,
    systemInstruction: Contents? = null,
  ): Boolean {
    try {
      resetConversation(
        model = model,
        systemInstruction = systemInstruction,
        instance = instance,
      )
      var invokeError: String? = null
      val latch = java.util.concurrent.CountDownLatch(1)
      try {
        runInference(
          model = model,
          input = prompt,
          resultListener = { _, done, _ -> if (done) latch.countDown() },
          cleanUpListener = {},
          onError = { msg ->
            invokeError = msg
            latch.countDown()
          },
          instance = instance,
        )
        latch.await(120, java.util.concurrent.TimeUnit.SECONDS)
      } catch (e: Exception) {
        invokeError = e.message
      }
      // Reset again: the conversation that just (may have) failed is discarded
      // and a fresh one is in place for real requests.
      resetConversation(
        model = model,
        systemInstruction = systemInstruction,
        instance = instance,
      )
      Log.i(
        TAG,
        "First-invoke burn for '${model.name}' instance ${instance.index}: " +
          if (invokeError == null) "passed" else "failed (${invokeError?.take(LOG_ERROR_PREVIEW_SHORT_CHARS)}), engine settled"
      )
      return invokeError == null
    } catch (e: Exception) {
      Log.w(TAG, "First-invoke burn failed for '${model.name}' instance ${instance.index}: ${e.message}")
      return false
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
    // Multi-instance pools: run on this specific instance's conversation.
    instance: LlmModelInstance? = null,
  ) {
    val target = instance ?: (model.instance as? LlmModelInstance)
    if (target == null) {
      onError("LlmModelInstance is not initialized.")
      return
    }

    // Native settle guard: a fresh conversation's first invoke fails
    // (Status 13) when it starts within ~3s of a conversation close on the
    // same Engine (async buffer teardown). Interactive requests are spaced
    // further apart, so this only costs latency in tight sequences —
    // first request after load/reload, warmup burns, rapid-fire.
    val sinceCloseMs = SystemClock.elapsedRealtime() - target.lastConversationClosedAtMs
    if (sinceCloseMs < INVOKE_SETTLE_MIN_GAP_MS) {
      val settleMs = INVOKE_SETTLE_MIN_GAP_MS - sinceCloseMs
      Log.i(
        TAG,
        "Settling ${settleMs}ms before invoke on '${model.name}' instance ${target.index} " +
          "(conversation closed ${sinceCloseMs}ms ago)",
      )
      Thread.sleep(settleMs)
    }

    cleanUpListeners.putIfAbsent(model.name, cleanUpListener)
    val conversation = target.conversation
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
