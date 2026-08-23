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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ConversationCacheTrackerTest {

  @Before
  fun setUp() {
    ConversationCacheTracker.invalidateCachedTurns("test-model")
  }

  @Test
  fun claimAndPublishCachedTurns() {
    val initialEntry = ConversationCacheTracker.ConversationCacheEntry(
      turns = listOf(ConversationCacheTracker.ConversationTurn("user", "hi")),
      toolsHash = 42,
    )
    ConversationCacheTracker.updateCachedTurns("test-model", initialEntry)

    val claim = ConversationCacheTracker.claimCachedTurns("test-model")
    assertEquals(initialEntry, claim.entry)
    assertNull(ConversationCacheTracker.getCachedTurns("test-model"))

    val newEntry = ConversationCacheTracker.ConversationCacheEntry(
      turns = listOf(
        ConversationCacheTracker.ConversationTurn("user", "hi"),
        ConversationCacheTracker.ConversationTurn("assistant", "hello"),
      ),
      toolsHash = 42,
    )
    ConversationCacheTracker.publishCachedTurns("test-model", claim.generation, newEntry)

    assertEquals(newEntry, ConversationCacheTracker.getCachedTurns("test-model"))
  }

  @Test
  fun discardCachedTurnsRemovesEntry() {
    val claim = ConversationCacheTracker.claimCachedTurns("test-model")
    ConversationCacheTracker.discardCachedTurns("test-model", claim.generation)
    assertNull(ConversationCacheTracker.getCachedTurns("test-model"))
  }
}
