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
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks the conversation state already prefilled into LiteRT's stateful Conversation
 * so a subsequent request that extends the same history can reuse the prefilled KV
 * cache instead of re-prefilling from scratch.
 *
 * State is keyed by model name because there is one Conversation per loaded model.
 */
object ConversationCacheTracker {
  data class ConversationTurn(val role: String, val text: String)

  data class ConversationCacheEntry(
    val turns: List<ConversationTurn>,
    val toolsHash: Int,
    val systemPrompts: List<String> = emptyList(),
    val samplerConfig: Map<String, Any> = emptyMap(),
  )

  data class ConversationCacheClaim(
    val generation: Long,
    val entry: ConversationCacheEntry?,
  )

  private data class ConversationCacheState(
    val generation: Long,
    val entry: ConversationCacheEntry?,
  )

  private val conversationCacheGeneration = AtomicLong(0)
  private val conversationCache = ConcurrentHashMap<String, ConversationCacheState>()

  fun getCachedTurns(modelName: String): ConversationCacheEntry? =
    conversationCache[modelName]?.entry

  /**
   * Atomically consumes the latest reusable state and gives this request publication ownership.
   * A later claim supersedes that ownership, so a delayed older SSE cannot mutate newer state.
   */
  fun claimCachedTurns(modelName: String): ConversationCacheClaim {
    val generation = conversationCacheGeneration.incrementAndGet()
    var claimedEntry: ConversationCacheEntry? = null
    conversationCache.compute(modelName) { _, current ->
      claimedEntry = current?.entry
      ConversationCacheState(generation, null)
    }
    return ConversationCacheClaim(generation, claimedEntry)
  }

  /** Test/setup helper that installs a published entry under a fresh generation. */
  fun updateCachedTurns(modelName: String, entry: ConversationCacheEntry) {
    conversationCache[modelName] = ConversationCacheState(
      generation = conversationCacheGeneration.incrementAndGet(),
      entry = entry,
    )
  }

  fun publishCachedTurns(
    modelName: String,
    generation: Long,
    entry: ConversationCacheEntry,
  ) {
    conversationCache.compute(modelName) { _, current ->
      if (current?.generation == generation) ConversationCacheState(generation, entry) else current
    }
  }

  fun discardCachedTurns(modelName: String, generation: Long) {
    conversationCache.compute(modelName) { _, current ->
      if (current?.generation == generation) ConversationCacheState(generation, null) else current
    }
  }

  /** Supersedes every outstanding request publication for this model. */
  fun invalidateCachedTurns(modelName: String) {
    conversationCache[modelName] = ConversationCacheState(
      generation = conversationCacheGeneration.incrementAndGet(),
      entry = null,
    )
  }
}
