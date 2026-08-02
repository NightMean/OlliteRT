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

import com.ollitert.llm.server.runtime.ServerLlmModelHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationCachePublicationTest {
  private val modelName = "cache-publication-test"
  private val entry = ServerLlmModelHelper.ConversationCacheEntry(
    turns = listOf(ServerLlmModelHelper.ConversationTurn("user", "hello")),
    systemPrompts = listOf("system"),
    toolsHash = 22,
  )

  @After
  fun tearDown() {
    ServerLlmModelHelper.invalidateCachedTurns(modelName)
  }

  @Test
  fun reusableSuccessfulConversationIsPublished() {
    val publication = publication(entry)

    publication.finish(isConversationReusable = true, assistantText = "reply")

    assertEquals(completed(entry, "reply"), ServerLlmModelHelper.getCachedTurns(modelName))
  }

  @Test
  fun stopSequenceInvalidatesExistingConversationCache() {
    ServerLlmModelHelper.updateCachedTurns(modelName, entry)
    val publication = publication(entry)

    publication.finish(isConversationReusable = false)

    assertNull(ServerLlmModelHelper.getCachedTurns(modelName))
  }

  @Test
  fun unsupportedConversationInvalidatesCacheEvenAfterSuccess() {
    ServerLlmModelHelper.updateCachedTurns(modelName, entry)
    val publication = publication(entry, isEligible = false)

    publication.finish(isConversationReusable = true, assistantText = "reply")

    assertNull(ServerLlmModelHelper.getCachedTurns(modelName))
  }

  @Test
  fun firstTerminalOutcomeCannotBeOverwritten() {
    val publication = publication(entry)

    publication.finish(isConversationReusable = false)
    publication.finish(isConversationReusable = true, assistantText = "reply")

    assertNull(ServerLlmModelHelper.getCachedTurns(modelName))
  }

  @Test
  fun olderStreamCannotOverwriteNewerPublishedConversation() {
    val olderEntry = entry.copy(turns = listOf(ServerLlmModelHelper.ConversationTurn("user", "older")))
    val newerEntry = entry.copy(turns = listOf(ServerLlmModelHelper.ConversationTurn("user", "newer")))
    val older = publication(olderEntry)
    val newer = publication(newerEntry)

    newer.finish(isConversationReusable = true, assistantText = "newer reply")
    older.finish(isConversationReusable = true, assistantText = "older reply")

    assertEquals(completed(newerEntry, "newer reply"), ServerLlmModelHelper.getCachedTurns(modelName))
  }

  @Test
  fun olderStreamCannotInvalidateNewerPublishedConversation() {
    val older = publication(entry)
    val newerEntry = entry.copy(turns = listOf(ServerLlmModelHelper.ConversationTurn("user", "newer")))
    val newer = publication(newerEntry)

    newer.finish(isConversationReusable = true, assistantText = "newer reply")
    older.finish(isConversationReusable = false)

    assertEquals(completed(newerEntry, "newer reply"), ServerLlmModelHelper.getCachedTurns(modelName))
  }

  private fun publication(
    value: ServerLlmModelHelper.ConversationCacheEntry,
    isEligible: Boolean = true,
  ): ConversationCachePublication {
    val claim = ServerLlmModelHelper.claimCachedTurns(modelName)
    return ConversationCachePublication(
      modelName = modelName,
      entry = value,
      isIncrementalReuseEligible = isEligible,
    ).also { it.attachGeneration(claim.generation) }
  }

  private fun completed(
    value: ServerLlmModelHelper.ConversationCacheEntry,
    assistantText: String,
  ) = value.copy(
    turns = value.turns + ServerLlmModelHelper.ConversationTurn("assistant", assistantText),
  )
}
