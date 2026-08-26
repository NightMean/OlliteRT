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

import com.ollitert.llm.server.common.ServerMetrics
import android.util.Log
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import com.ollitert.llm.server.service.http.KtorServer
import com.ollitert.llm.server.service.inference.ModelLifecycle
import kotlinx.coroutines.Job
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles safe model and server teardown prior to reloading the service.
 */
object ServerReloadCoordinator {
  private const val TAG = "OlliteRT.ReloadCoordinator"

  /** Hard cap for waiting on Ktor teardown before continuing without it. */
  internal const val SERVER_STOP_JOIN_TIMEOUT_MS = 2_000L

  /**
   * Stops the embedded Ktor server without blocking the caller's thread for an
   * unbounded time. `engine.stop()` tears down connections synchronously and can
   * stall while LAN clients hold open requests; reload/start/destroy run on the
   * main looper, so the stop runs on a worker thread and the caller waits with a
   * hard cap. Ordering still holds in the normal case (the replacement server
   * rebinds the same port immediately after); if the cap expires, the subsequent
   * bind surfaces a port-in-use error through the existing failure path instead
   * of wedging the main thread into ANR territory.
   */
  fun stopKtorServerBounded(server: KtorServer?) {
    if (server == null) return
    val stopped = java.util.concurrent.CountDownLatch(1)
    Thread {
      try {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 0)
      } catch (e: Exception) {
        Log.w(TAG, "Ktor server stop failed", e)
      } finally {
        stopped.countDown()
      }
    }.apply {
      name = "OlliteRT-KtorStop"
      isDaemon = true
      start()
    }
    val stoppedInTime = stopped.await(SERVER_STOP_JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    if (!stoppedInTime) {
      Log.w(TAG, "Ktor stop exceeded ${SERVER_STOP_JOIN_TIMEOUT_MS}ms — continuing reload")
    }
  }

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
    stopKtorServerBounded(server)
    modelLifecycle.defaultModel?.let { ServerLlmModelHelper.stopResponse(it) }
    loadJob?.cancel()
    inferenceExecutor?.shutdownNow()

    previousModelName?.let { modelName ->
      RequestLogStore.addEvent(
        "Unloading model: $modelName",
        modelName = modelName,
        category = EventCategory.MODEL,
      )
    }
    // Shared with onDestroy so the two teardown paths cannot drift.
    ServerCleanupCoordinator.collectAndEnqueueModelCleanup(
      modelLifecycle = modelLifecycle,
      loadJob = loadJob,
      inferenceExecutor = inferenceExecutor,
      source = "ReloadCleanup",
    )
    ServerMetrics.onServerStopped()
  }
}
