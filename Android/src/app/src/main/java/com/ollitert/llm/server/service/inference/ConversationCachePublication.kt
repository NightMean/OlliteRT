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

import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.service.http.*

import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Publishes request history only after the native and protocol response both succeed. */
internal class ConversationCachePublication(
  private val modelName: String,
  private val entry: ServerLlmModelHelper.ConversationCacheEntry,
  private val isIncrementalReuseEligible: Boolean,
) {
  private val isSettled = AtomicBoolean(false)
  private val generation = AtomicLong(0)
  private val preparedEntry = AtomicReference(entry)

  fun attachGeneration(
    value: Long,
    entry: ServerLlmModelHelper.ConversationCacheEntry = preparedEntry.get(),
  ) {
    check(generation.compareAndSet(0, value)) { "Conversation cache generation already attached" }
    preparedEntry.set(entry)
  }

  fun finish(isConversationReusable: Boolean, assistantText: String? = null) {
    if (!isSettled.compareAndSet(false, true)) return
    val ownedGeneration = generation.get()
    if (ownedGeneration == 0L) return
    if (isIncrementalReuseEligible && isConversationReusable && assistantText != null) {
      val prepared = preparedEntry.get()
      val completedEntry = prepared.copy(
        turns = prepared.turns + ServerLlmModelHelper.ConversationTurn("assistant", assistantText),
      )
      ServerLlmModelHelper.publishCachedTurns(modelName, ownedGeneration, completedEntry)
    } else {
      ServerLlmModelHelper.discardCachedTurns(modelName, ownedGeneration)
    }
  }
}
