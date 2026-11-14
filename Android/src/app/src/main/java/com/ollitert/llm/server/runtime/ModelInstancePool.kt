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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

  /** Round-robin pick for a fresh (non cache-extend) request. */
  fun nextFor(modelName: String): Slot? {
    val entry = pools[modelName] ?: return null
    val n = entry.instances.size
    if (n == 1) return Slot(entry.instances[0], 0)
    val i = Math.floorMod(entry.counter.getAndIncrement(), n)
    return Slot(entry.instances[i], i)
  }

  /** The instance whose live conversation the model-name cache currently describes. */
  fun ownerFor(modelName: String): Slot? {
    val entry = pools[modelName] ?: return null
    val i = Math.floorMod(entry.owner.get(), entry.instances.size)
    return Slot(entry.instances[i], i)
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
