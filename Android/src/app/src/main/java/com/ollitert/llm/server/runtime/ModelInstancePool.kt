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

package com.ollitert.llm.server.runtime

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "OlliteRT.InstancePool"

/**
 * Registry of concurrently loaded [LlmModelInstance]s for a single model.
 *
 * OlliteRT can load N independent engine+conversation pairs for the same model
 * (the "model instance count", default 1). Fresh requests are routed
 * round-robin across instances so several clients can generate at the same
 * time, while each instance keeps its own [LlmModelInstance.inferenceLock] so
 * the non-thread-safe native SDK never sees two in-flight generations on one
 * engine.
 *
 * The pool also tracks the OWNER instance: the one whose live Conversation is
 * described by the per-model-name conversation cache (KV-cache incremental
 * reuse). Requests that extend the cached conversation are pinned to the owner
 * so they always meet the conversation they extend; when a request publishes a
 * fresh cache entry from its own instance, the owner moves to that instance.
 */
internal object ModelInstancePool {

  /** One loaded instance plus its position in the pool. */
  data class Slot(val instance: LlmModelInstance, val index: Int)

  /**
   * Consecutive native invoke failures after which an instance is marked
   * unhealthy and routing skips it. Measured: an engine whose GPU allocation
   * fails at init fails EVERY invoke (Status 13) for the life of the pool,
   * while its sibling instances serve fine — so 2 consecutive failures is
   * treated as "broken", and a single transient failure recovers (the count
   * resets on any success).
   */
  private const val UNHEALTHY_FAILURE_THRESHOLD = 2

  private class Entry(
    val instances: List<LlmModelInstance>,
    val counter: AtomicInteger,
    val owner: AtomicInteger,
  )

  private val pools = ConcurrentHashMap<String, Entry>()

  /** Registers the freshly created instances for [modelName], replacing any previous pool. */
  fun set(modelName: String, instances: List<LlmModelInstance>) {
    if (instances.isEmpty()) {
      pools.remove(modelName)
      return
    }
    instances.forEachIndexed { i, inst -> inst.index = i }
    pools[modelName] = Entry(instances, AtomicInteger(0), AtomicInteger(0))
  }

  fun instancesFor(modelName: String): List<LlmModelInstance> =
    pools[modelName]?.instances ?: emptyList()

  fun countFor(modelName: String): Int = pools[modelName]?.instances?.size ?: 0

  /** Round-robin pick for a fresh (non cache-extend) request; skips unhealthy instances. */
  fun nextFor(modelName: String): Slot? {
    val entry = pools[modelName] ?: return null
    val n = entry.instances.size
    if (n == 1) return Slot(entry.instances[0], 0)
    // Skip unhealthy instances; if ALL are marked (e.g. both engines flaky),
    // fall back to plain round-robin — trying is better than a 503.
    for (step in 0 until n) {
      val i = Math.floorMod(entry.counter.getAndIncrement(), n)
      val inst = entry.instances[i]
      if (!inst.markedUnhealthy) return Slot(inst, i)
    }
    val i = Math.floorMod(entry.counter.getAndIncrement(), n)
    return Slot(entry.instances[i], i)
  }

  /** The instance whose live conversation the model-name cache currently describes. */
  fun ownerFor(modelName: String): Slot? {
    val entry = pools[modelName] ?: return null
    val i = Math.floorMod(entry.owner.get(), entry.instances.size)
    val inst = entry.instances[i]
    // A broken owner can't serve its cached conversation — route fresh.
    return if (inst.markedUnhealthy) nextFor(modelName) else Slot(inst, i)
  }

  /**
   * Records a native invoke failure for [index]. Returns true once the
   * instance crossed the threshold and was marked unhealthy. The CALLER is
   * responsible for freeing the engine's native resources — NOT here,
   * because this runs on the SDK callback thread where closing a
   * conversation deadlocks.
   */
  fun recordFailure(modelName: String, index: Int): Boolean {
    val entry = pools[modelName] ?: return false
    val inst = entry.instances.getOrNull(index) ?: return false
    val failures = inst.consecutiveInvokeFailures.incrementAndGet()
    if (failures >= UNHEALTHY_FAILURE_THRESHOLD && !inst.markedUnhealthy) {
      inst.markedUnhealthy = true
      Log.w(
        TAG,
        "Instance $index of '$modelName' marked unhealthy after $failures consecutive " +
          "invoke failures — routing around it",
      )
    }
    return inst.markedUnhealthy
  }

  /** Records a successful invoke; clears the consecutive-failure count. */
  fun recordSuccess(modelName: String, index: Int) {
    pools[modelName]?.instances?.getOrNull(index)?.consecutiveInvokeFailures?.set(0)
  }

  /**
   * Routes one incoming request: cache-extend candidates go to the owner
   * instance (the conversation they extend lives there), everything else
   * round-robins across all instances.
   */
  fun route(modelName: String, isCacheCandidate: Boolean): Slot? =
    if (isCacheCandidate) ownerFor(modelName) ?: nextFor(modelName) else nextFor(modelName)

  /** Records that [modelName]'s cached conversation now lives on instance [index]. */
  fun setOwner(modelName: String, index: Int) {
    pools[modelName]?.owner?.set(index.coerceAtLeast(0))
  }

  fun clear(modelName: String) {
    pools.remove(modelName)
  }

  fun clearAll() {
    pools.clear()
  }
}
