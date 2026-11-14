/*
 * Copyright 2026 @NightMean (https://github.com/NightMean) — embedding pool added by the
 * OlliteRT-embed fork (Vasyl) for multi-instance embedding models.
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

import com.google.ai.edge.litertlm.EmbeddingEngine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Registry of concurrently loaded [EmbeddingEngine]s for a single embedding model.
 *
 * Mirrors [ModelInstancePool] for the embedding path (which stores bare
 * [EmbeddingEngine]s in [com.ollitert.llm.server.data.model.Model.instance]
 * instead of [LlmModelInstance]s): the server can load N independent embedding
 * engines for the same model (the "model instance count", default 1), and
 * incoming /v1/embeddings requests are routed round-robin across them so
 * several clients can embed at the same time on independent native handles.
 *
 * Each slot carries a gate: the native SDK is not safe for two concurrent
 * computes on ONE engine (single-engine bursts hit a native race — bogus
 * length counts / 500s, then self-recovery). Requests that land on the same
 * engine therefore serialize; requests on different engines overlap.
 */
internal object EmbeddingInstancePool {

  /** One loaded engine plus its position in the pool. */
  data class Slot(val engine: EmbeddingEngine, val index: Int) {
    /** Per-engine serialization gate (see class KDoc). */
    val gate: Semaphore = Semaphore(1)
  }

  private class Entry(
    val slots: List<Slot>,
    val counter: AtomicInteger,
  )

  private val pools = ConcurrentHashMap<String, Entry>()

  /** Registers the freshly created engines for [modelName], replacing any previous pool. */
  fun set(modelName: String, engines: List<EmbeddingEngine>) {
    if (engines.isEmpty()) {
      pools.remove(modelName)
      return
    }
    pools[modelName] = Entry(engines.mapIndexed { i, engine -> Slot(engine, i) }, AtomicInteger(0))
  }

  fun instancesFor(modelName: String): List<EmbeddingEngine> =
    pools[modelName]?.slots?.map { it.engine } ?: emptyList()

  fun countFor(modelName: String): Int = pools[modelName]?.slots?.size ?: 0

  /** Round-robin pick for an incoming request. */
  fun nextFor(modelName: String): Slot? {
    val entry = pools[modelName] ?: return null
    val n = entry.slots.size
    if (n == 1) return entry.slots[0]
    val i = Math.floorMod(entry.counter.getAndIncrement(), n)
    return entry.slots[i]
  }

  fun clear(modelName: String) {
    pools.remove(modelName)
  }

  fun clearAll() {
    pools.clear()
  }
}
