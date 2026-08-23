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

package com.ollitert.llm.server.service

import android.util.Log
import com.ollitert.llm.server.data.EventCategory
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.http.KtorServer
import com.ollitert.llm.server.service.inference.ModelLifecycle
import com.ollitert.llm.server.service.inference.ServerMetrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles safe model and server teardown prior to reloading the service.
 */
object ServerReloadCoordinator {
  private const val TAG = "OlliteRT.ReloadCoordinator"

  fun executeReloadCleanup(
    modelLifecycle: ModelLifecycle,
    loadGeneration: AtomicLong,
    server: KtorServer?,
    loadJob: Job?,
    inferenceExecutor: ExecutorService?,
  ) {
    modelLifecycle.cancelKeepAliveTimer()
    modelLifecycle.setKeepAliveUnloadedModel(null, null)
    val previousModelName = modelLifecycle.defaultModel?.name
    Log.i(TAG, "Reload requested — cleaning up current model before restart")

    // Bump generation FIRST so any in-flight load thread sees the stale generation
    loadGeneration.incrementAndGet()
    RequestLogStore.addEvent(
      "Model restart requested",
      modelName = previousModelName,
      category = EventCategory.MODEL,
    )
    RequestLogStore.cancelAllPending()
    server?.stop(gracePeriodMillis = 0, timeoutMillis = 0)
    modelLifecycle.defaultModel?.let { ServerLlmModelHelper.stopResponse(it) }
    loadJob?.cancel()
    inferenceExecutor?.shutdownNow()

    val modelsToCleanUp = linkedSetOf<Model>()
    synchronized(modelLifecycle.keepAliveLock) {
      modelLifecycle.defaultModel?.let(modelsToCleanUp::add)
      modelLifecycle.defaultModel = null
      modelsToCleanUp.addAll(modelLifecycle.modelCache.values.filter { it.instance != null })
      modelLifecycle.modelCache.clear()
    }
    previousModelName?.let { modelName ->
      RequestLogStore.addEvent(
        "Unloading model: $modelName",
        modelName = modelName,
        category = EventCategory.MODEL,
      )
    }
    if (loadJob != null || inferenceExecutor != null || modelsToCleanUp.isNotEmpty() ||
      modelLifecycle.hasActiveIdleCleanup()
    ) {
      ServerCleanupCoordinator.enqueueCleanup("OlliteRT-ReloadCleanup") {
        modelLifecycle.awaitIdleCleanup()
        loadJob?.let { job -> runBlocking { job.join() } }
        if (inferenceExecutor?.awaitTermination(15, TimeUnit.SECONDS) == false) {
          Log.w(TAG, "Inference executor did not terminate during reload cleanup")
        }
        for (model in modelsToCleanUp) {
          ServerLlmModelHelper.safeCleanup(model)
        }
        System.gc()
      }
    }
    ServerMetrics.onServerStopped()
  }
}
