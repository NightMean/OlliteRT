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

package com.ollitert.llm.server.service.http

import com.ollitert.llm.server.runtime.ServerLlmModelHelper

/**
 * Outcome of the cache-reuse decision for a chat-completion request.
 *
 * - [kind] = `EXTEND` means the request's history matches the server's cached
 *   conversation state and we can append only [newUserText] via the SDK's
 *   incremental path (KV cache reused, fast TTFB).
 * - [kind] = `RESET` means the conversation must be (re)built from scratch:
 *   different conversation, edited history, system prompt or tool change,
 *   first request, or multimodal input that the incremental path doesn't
 *   support yet.
 *
 * [reason] is a short tag for logging.
 */
internal data class IncrementalDecision(
  val kind: Kind,
  val reason: String,
  val cacheGeneration: Long,
  val newUserText: String? = null,
) {
  enum class Kind { EXTEND, RESET }
}

internal fun decideIncrementalReuse(
  modelName: String,
  messages: List<ChatMessage>,
  systemPrompts: List<String> = emptyList(),
  toolsHash: Int,
  hasTools: Boolean,
  hasImages: Boolean,
  hasAudio: Boolean,
  samplerConfig: Map<String, Any> = emptyMap(),
  promptWasTransformed: Boolean = false,
): IncrementalDecision {
  // Claim the published state atomically. While this request is pending, concurrent
  // requests must rebuild rather than extending the same native Conversation snapshot.
  val claim = ServerLlmModelHelper.claimCachedTurns(modelName)
  val cached = claim.entry
  fun reset(reason: String) = IncrementalDecision(
    kind = IncrementalDecision.Kind.RESET,
    reason = reason,
    cacheGeneration = claim.generation,
  )

  // Disable incremental for known-incompatible cases up-front.
  if (hasImages || hasAudio) return reset("multimodal_unsupported")
  if (hasTools) return reset("tools_unsupported")
  if (promptWasTransformed) return reset("prompt_transformed")
  if (messages.isEmpty()) return reset("empty_messages")

  // The last message must be a user turn for incremental append.
  val last = messages.last()
  if (last.role != "user" || last.content.text.isBlank()) {
    return reset("last_not_user_text")
  }

  cached ?: return reset("no_cache")

  if (cached.systemPrompts != systemPrompts) {
    return reset("system_prompt_changed")
  }
  if (cached.toolsHash != toolsHash) {
    return reset("tools_changed")
  }
  if (cached.samplerConfig != samplerConfig) {
    return reset("sampler_changed")
  }

  // Reuse is safe only when the complete client-supplied history matches the native
  // Conversation represented by the published cache, including assistant turns.
  val requestHistory = messages
    .filter { it.role != "system" }
    .dropLast(1)
    .map { ServerLlmModelHelper.ConversationTurn(it.role, it.content.text) }
  if (requestHistory != cached.turns) {
    return reset("history_changed")
  }

  return IncrementalDecision(
    kind = IncrementalDecision.Kind.EXTEND,
    reason = "history_matches",
    newUserText = last.content.text,
    cacheGeneration = claim.generation,
  )
}
