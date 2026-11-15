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

import android.content.Context
import android.os.SystemClock
import androidx.annotation.WorkerThread
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_SHORT_CHARS
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.RequestPrefsSnapshot
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.prefs.WARMUP_MESSAGE
import com.ollitert.llm.server.runtime.EmbeddingInstancePool
import com.ollitert.llm.server.runtime.LlmModelInstance
import com.ollitert.llm.server.runtime.ModelInstancePool
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.google.ai.edge.litertlm.EmbeddingCallback
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.EmbeddingResponse
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.CompletableDeferred

/**
 * Handles warmup inference routines and request cancellation state reporting.
 */
internal object InferenceWarmupHelper {

  /**
   * Warm up the model with a short test inference.
   * Used during model loading to pre-fill caches and verify the model works.
   */
  @WorkerThread
  fun warmUpModel(
    context: Context,
    model: Model,
    isEmbeddingModel: (model: Model) -> Boolean = { false },
    initializeEngine: (model: Model) -> String = { "" },
    buildSystemInstruction: (modelPrefsKey: String) -> com.google.ai.edge.litertlm.Contents? = { null },
    runLlm: suspend (model: Model, prompt: String, requestId: String, endpoint: String, timeoutSeconds: Long, eagerVisionInit: Boolean) -> Pair<String?, String?>,
  ) {
    val startMs = SystemClock.elapsedRealtime()
    // First-request-after-load bug: the first native invoke on a freshly (re)loaded
    // engine fails with "Failed to invoke the compiled model" (Status Code 13,
    // kv_cache buffer requirements) — and a FRESH conversation on that engine
    // fails its first invoke too, until the engine has been invoked once. The
    // engine-level settle is per Engine: burn each pool instance's first invoke
    // here with a throwaway call, so the FIRST real request does not 500.
    // Previously the warmup's first-invoke error was swallowed (only
    // model_init_failed: was rethrown), the model reported ready, and the
    // failure surfaced as a 500 on the first user request instead.
    //
    // IMPORTANT: warmUpModel runs ON the model-load thread, and on the
    // warmup-enabled path it is what triggers engine initialization (the LLM
    // path via runLlm → reinitIfNeeded). It must therefore NEVER block waiting
    // for the engine to appear — that is circular. Embedding models cannot go
    // through runLlm at all (it requires an LlmModelInstance), so they get an
    // explicit initializeEngine + one computeEmbeddingAsync burn per engine.
    if (isEmbeddingModel(model)) {
      warmUpEmbeddingModel(model, initializeEngine)
      return
    }
    val eagerVision = ServerPrefs.isEagerVisionInit(context)
    val systemInstruction = buildSystemInstruction(model.prefsKey)
    // Pass 1 doubles as engine initialization (runLlm → reinitIfNeeded) and
    // burns instance 0's first invoke. With a multi-instance pool the OTHER
    // instances have never been invoked — the engine settle is per Engine, so
    // the first request round-robined to a cold instance would still 500.
    var (result, error) = runWarmupPass(context, model, runLlm, eagerVision)
    val initFailed = error?.startsWith("model_init_failed:") == true
    if (!initFailed) {
      // Engine is initialized now — burn every pool instance's first invoke
      // (throwaway conversation: reset → invoke → reset), so ALL engines are
      // settled and each is left with a fresh, uninvoked conversation.
      // Instances already marked unhealthy (repeated invoke failures) are
      // skipped — burning them can't help and just wastes load time.
      val allInstances = ModelInstancePool.instancesFor(model.name)
        .ifEmpty { listOfNotNull(model.instance as? LlmModelInstance) }
      val instances = allInstances.filter { !it.markedUnhealthy }.ifEmpty { allInstances }
      for (inst in instances) {
        synchronized(inst.inferenceLock) {
          ServerLlmModelHelper.burnFirstInvoke(model, inst, WARMUP_MESSAGE, systemInstruction)
        }
      }
      // If pass 1 hit the native first-invoke failure, run one final functional
      // pass to verify the request path now succeeds.
      if (error != null && isNativeInvokeFailure(error)) {
        RequestLogStore.addEvent(
          "Warmup first pass failed (${error.take(LOG_ERROR_PREVIEW_SHORT_CHARS)}) — burned ${instances.size} instance(s), verifying with a final pass",
          modelName = model.name,
          category = EventCategory.MODEL,
        )
        val retry = runWarmupPass(context, model, runLlm, eagerVision)
        result = retry.first
        error = retry.second
      }
    }
    if (error != null && error.startsWith("model_init_failed:")) {
      throw RuntimeException(error.removePrefix("model_init_failed: "))
    }
    if (error != null) {
      RequestLogStore.addEvent(
        "Warmup retry still failing (${error.take(LOG_ERROR_PREVIEW_SHORT_CHARS)}) — first requests may return an error",
        modelName = model.name,
        category = EventCategory.MODEL,
      )
    }
    val elapsedMs = SystemClock.elapsedRealtime() - startMs
    val snippet = result?.take(LOG_ERROR_PREVIEW_SHORT_CHARS)?.replace("\n", " ") ?: "no response"
    RequestLogStore.addEvent(
      "Sending a warmup message: \"$WARMUP_MESSAGE\" → \"$snippet\" (${elapsedMs}ms)",
      modelName = model.name,
      category = EventCategory.MODEL,
    )
  }

  /**
   * Burns the first-compute failure on every loaded [EmbeddingEngine].
   * Embedding models use EmbeddingInstancePool (bare engines, no conversations),
   * so the LLM warmup path can never touch them — their first
   * computeEmbeddingAsync after a (re)load fails with the same native first
   * invoke error, and the failure surfaced as a 500 on the first
   * /v1/embeddings request. One throwaway compute per engine settles it.
   *
   * [initializeEngine] is called first: on the warmup-enabled load path nothing
   * else initializes the engine (the LLM path does it via runLlm →
   * reinitIfNeeded, which embedding models can't use).
   */
  @WorkerThread
  private fun warmUpEmbeddingModel(model: Model, initializeEngine: (model: Model) -> String) {
    val startMs = SystemClock.elapsedRealtime()
    val initErr = initializeEngine(model)
    if (initErr.isNotEmpty()) {
      throw RuntimeException("model_init_failed: $initErr")
    }
    // initializeEngine is synchronous; the pool is registered before it
    // returns. Poll briefly only as a safety net.
    var slots = EmbeddingInstancePool.slotsFor(model.name)
    var waitedMs = 0L
    while (slots.isEmpty() && waitedMs < 10_000L) {
      Thread.sleep(250)
      waitedMs += 250
      slots = EmbeddingInstancePool.slotsFor(model.name)
    }
    var failures = 0
    for (slot in slots) {
      slot.gate.acquire()
      try {
        val deferred = CompletableDeferred<EmbeddingResponse>()
        try {
          slot.engine.computeEmbeddingAsync(
            listOf(InputData.Text(WARMUP_MESSAGE)),
            object : EmbeddingCallback<EmbeddingResponse> {
              override fun onSuccess(result: EmbeddingResponse) { deferred.complete(result) }
              override fun onError(t: Throwable) { deferred.completeExceptionally(t) }
            },
            EmbeddingOptions(normalize = true, outputSize = null),
          )
          kotlinx.coroutines.runBlocking { deferred.await() }
        } catch (e: Exception) {
          failures++
          RequestLogStore.addEvent(
            "Embedding warmup burn failed on engine ${slot.index} (${e.message?.take(LOG_ERROR_PREVIEW_SHORT_CHARS)}), engine should settle",
            modelName = model.name,
            category = EventCategory.MODEL,
          )
        }
      } finally {
        slot.gate.release()
      }
    }
    val elapsedMs = SystemClock.elapsedRealtime() - startMs
    RequestLogStore.addEvent(
      if (slots.isEmpty()) {
        "Embedding warmup: no engines registered after ${waitedMs}ms — first requests may fail"
      } else {
        "Embedding warmup: burned ${slots.size} engine(s) in ${elapsedMs}ms (${failures} expected first-compute failures)"
      },
      modelName = model.name,
      category = EventCategory.MODEL,
    )
  }

  private fun runWarmupPass(
    context: Context,
    model: Model,
    runLlm: suspend (model: Model, prompt: String, requestId: String, endpoint: String, timeoutSeconds: Long, eagerVisionInit: Boolean) -> Pair<String?, String?>,
    eagerVision: Boolean,
  ): Pair<String?, String?> = kotlinx.coroutines.runBlocking {
    runLlm(
      model,
      WARMUP_MESSAGE,
      "warmup",
      "warmup",
      ServerPrefs.getTimeoutWarmup(context),
      eagerVision,
    )
  }

  /**
   * True for the native first-invoke failure class: the LiteRT-LM engine rejects the
   * first inference after a (re)load ("Failed to invoke the compiled model",
   * Status Code 13, kv_cache buffer requirements). These are conversation-level,
   * not init-level: a reset + retry recovers, so they must not be treated as load
   * failures.
   */
  internal fun isNativeInvokeFailure(error: String): Boolean =
    error.contains("Failed to invoke", ignoreCase = true) ||
      error.contains("Status Code 13") ||
      error.contains("kv_cache", ignoreCase = true)

  /**
   * Handles non-streaming request cancellation state updates in RequestLogStore and event log.
   */
  fun handleCancellation(
    context: Context,
    result: InferenceResult,
    logId: String?,
    requestId: String,
    endpoint: String,
    prefs: RequestPrefsSnapshot?,
    logSuffix: String,
    returnMessage: String,
    logEvent: (String) -> Unit,
  ): Pair<String?, String> {
    val keepPartial = prefs?.keepPartialResponse ?: ServerPrefs.isKeepPartialResponse(context)
    val partial = if (keepPartial && !result.output.isNullOrEmpty()) result.output else null
    if (logId != null) {
      RequestLogStore.update(logId) {
        it.copy(
          partialText = partial,
          isPending = false,
          isCancelled = true,
          statusCode = 499,
          latencyMs = result.totalMs,
        )
      }
    }
    logEvent("request_cancelled id=$requestId endpoint=$endpoint streaming=false $logSuffix outputChars=${result.output?.length ?: 0}")
    return null to returnMessage
  }
}
