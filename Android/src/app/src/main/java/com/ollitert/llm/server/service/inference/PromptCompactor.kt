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

/**
 * Progressive prompt compaction to handle context window overflow.
 *
 * When a prompt exceeds the model's max context tokens, strategies are applied
 * in order until the prompt fits:
 * 1. Conversation history truncation — keep system/developer messages + last N non-system messages
 * 2. Prompt trimming — hard-trim the string, keeping the tail (most recent content)
 *
 * Each strategy is gated by its own setting toggle:
 * - "Truncate Conversation History" gates history truncation
 * - "Trim Prompt" gates hard-trimming
 */
object PromptCompactor {

  /**
   * Result of a compaction attempt.
   * @param prompt The (possibly compacted) prompt string ready for inference.
   * @param compacted Whether any compaction strategy was applied.
   * @param strategies Short tags for strategies applied, e.g. ["truncated:-4 msgs", "trimmed"].
   */
  data class CompactionResult(
    val prompt: String,
    val compacted: Boolean,
    val strategies: List<String>,
  )

  /**
   * Progressive compaction for /v1/chat/completions requests.
   *
   * @param messages Original message list from the client.
   * @param tools Tool definitions (null or empty if no tools).
   * @param toolChoice Resolved tool_choice string.
   * @param chatTemplate Optional per-model chat template.
   * @param maxContext Max context tokens for the model (null = unknown, skip compaction).
   * @param truncateHistory Whether "Truncate Conversation History" is enabled.
   * @param trimPrompts Whether "Trim Prompt" is enabled.
   * @param interleaveImagePlaceholders When true, inserts image placeholder tokens between messages.
   */
  fun compactChatPrompt(
    messages: List<ChatMessage>,
    tools: List<ToolSpec>?,
    toolChoice: String?,
    chatTemplate: String?,
    maxContext: Int?,
    truncateHistory: Boolean,
    trimPrompts: Boolean,
    interleaveImagePlaceholders: Boolean = false,
  ): CompactionResult {
    val hasTools = !tools.isNullOrEmpty() && toolChoice != "none"

    // Build the full (uncompacted) prompt
    val fullPrompt = if (hasTools) {
      PromptBuilder.buildToolAwarePrompt(messages, tools, toolChoice, chatTemplate, interleaveImagePlaceholders = interleaveImagePlaceholders)
    } else {
      PromptBuilder.buildChatPrompt(messages, chatTemplate, interleaveImagePlaceholders)
    }

    // If no context limit is known, or prompt fits, return as-is
    if (maxContext == null || estimateTokens(fullPrompt) <= maxContext) {
      return CompactionResult(fullPrompt, false, emptyList())
    }

    // No compaction toggle is enabled — return the oversized prompt and let inference error
    if (!truncateHistory && !trimPrompts) {
      return CompactionResult(fullPrompt, false, emptyList())
    }

    val strategies = mutableListOf<String>()
    var currentMessages = messages
    // Prompt for the fully-truncated "system + last 1 non-system message" list,
    // set once strategy 1 exhausts the search without fitting. Reused by the
    // trim/best-effort paths instead of rebuilding the identical string.
    var tailPrompt: String? = null

    /** Single place choosing the builder so all compaction variants stay consistent. */
    fun buildPrompt(msgs: List<ChatMessage>): String =
      if (hasTools) {
        PromptBuilder.buildToolAwarePrompt(msgs, tools, toolChoice, chatTemplate, interleaveImagePlaceholders = interleaveImagePlaceholders)
      } else {
        PromptBuilder.buildChatPrompt(msgs, chatTemplate, interleaveImagePlaceholders)
      }

    // --- Strategy 1: Conversation history truncation ---
    // Keep system/developer messages at their original positions + last N non-system messages
    if (truncateHistory && messages.size > 2) {
      val systemMsgs = messages.filter { it.role == "system" || it.role == "developer" }
      val nonSystemMsgs = messages.filter { it.role != "system" && it.role != "developer" }

      if (nonSystemMsgs.size > 1) {
        // Binary search for the largest keep-count whose prompt fits — the
        // "fits" predicate is monotone in keep (fewer kept messages can only
        // shorten the prompt), so this finds the same point as a descending
        // linear scan while building O(log n) candidates instead of O(n).
        // The naive version rebuilt every candidate from scratch each step:
        // O(messages²) total string building on oversized prompts.
        var fitKeep = -1
        var fitCandidate: String? = null
        var lo = 1
        var hi = nonSystemMsgs.size - 1
        while (lo <= hi) {
          val mid = (lo + hi) / 2
          val candidate = buildPrompt(systemMsgs + nonSystemMsgs.takeLast(mid))
          if (estimateTokens(candidate) <= maxContext) {
            fitKeep = mid
            fitCandidate = candidate
            lo = mid + 1
          } else {
            hi = mid - 1
          }
        }
        if (fitKeep > 0) {
          strategies.add("truncated:-${nonSystemMsgs.size - fitKeep} msgs")
          return CompactionResult(fitCandidate!!, true, strategies)
        }
        // Even keeping just 1 non-system message didn't fit — continue with truncated list
        currentMessages = systemMsgs + nonSystemMsgs.takeLast(1)
        tailPrompt = buildPrompt(currentMessages)
        strategies.add("truncated:-${nonSystemMsgs.size - 1} msgs")
      }
    }

    // --- Strategy 2: Prompt trimming ---
    if (trimPrompts) {
      // If strategy 1 ran, currentMessages changed and its prompt is tailPrompt;
      // otherwise nothing changed and the already-built fullPrompt is still exact.
      val currentPrompt = tailPrompt ?: fullPrompt

      val maxChars = maxContext * 4
      if (currentPrompt.length > maxChars) {
        val trimmed = currentPrompt.takeLast(maxChars)
        strategies.add("trimmed")
        return CompactionResult(trimmed, true, strategies)
      }
      return CompactionResult(currentPrompt, strategies.isNotEmpty(), strategies)
    }

    // Compaction strategies exhausted or not enabled — return best effort
    val bestPrompt = tailPrompt ?: fullPrompt
    return CompactionResult(bestPrompt, strategies.isNotEmpty(), strategies)
  }

  /**
   * Progressive compaction for /v1/responses requests.
   * Only supports history truncation and prompt trimming (no tools injected into prompt).
   *
   * @param messages Original message list from the client.
   * @param chatTemplate Optional per-model chat template.
   * @param maxContext Max context tokens for the model.
   * @param truncateHistory Whether "Truncate Conversation History" is enabled.
   * @param trimPrompts Whether "Trim Prompt" is enabled.
   */
  fun compactConversationPrompt(
    messages: List<InputMsg>?,
    chatTemplate: String?,
    maxContext: Int?,
    truncateHistory: Boolean,
    trimPrompts: Boolean,
  ): CompactionResult {
    val fullPrompt = PromptBuilder.buildConversationPrompt(messages, chatTemplate)

    if (maxContext == null || estimateTokens(fullPrompt) <= maxContext) {
      return CompactionResult(fullPrompt, false, emptyList())
    }

    if (!truncateHistory && !trimPrompts) {
      return CompactionResult(fullPrompt, false, emptyList())
    }

    if (messages == null || messages.size <= 1) {
      // Only one message or none — can only trim
      return if (trimPrompts) trimPrompt(fullPrompt, maxContext) else CompactionResult(fullPrompt, false, emptyList())
    }

    val strategies = mutableListOf<String>()
    var tailPrompt: String? = null

    // Strategy 1: Conversation history truncation
    if (truncateHistory) {
      val systemMsgs = messages.filter { it.role == "system" || it.role == "developer" }
      val nonSystemMsgs = messages.filter { it.role != "system" && it.role != "developer" }

      if (nonSystemMsgs.size > 1) {
        // Binary search, mirroring compactChatPrompt: monotone fits-predicate,
        // O(log n) candidate builds instead of a descending O(n) rebuild loop.
        var fitKeep = -1
        var fitCandidate: String? = null
        var lo = 1
        var hi = nonSystemMsgs.size - 1
        while (lo <= hi) {
          val mid = (lo + hi) / 2
          val candidate = PromptBuilder.buildConversationPrompt(systemMsgs + nonSystemMsgs.takeLast(mid), chatTemplate)
          if (estimateTokens(candidate) <= maxContext) {
            fitKeep = mid
            fitCandidate = candidate
            lo = mid + 1
          } else {
            hi = mid - 1
          }
        }
        if (fitKeep > 0) {
          strategies.add("truncated:-${nonSystemMsgs.size - fitKeep} msgs")
          return CompactionResult(fitCandidate!!, true, strategies)
        }
        strategies.add("truncated:-${nonSystemMsgs.size - 1} msgs")
        tailPrompt = PromptBuilder.buildConversationPrompt(systemMsgs + nonSystemMsgs.takeLast(1), chatTemplate)
      }
    }

    // Strategy 2: Prompt trimming (last resort)
    if (trimPrompts) {
      val minPrompt = tailPrompt ?: fullPrompt
      return trimPrompt(minPrompt, maxContext, strategies)
    }

    // No more strategies — return best effort with whatever truncation was done
    return CompactionResult(tailPrompt ?: fullPrompt, strategies.isNotEmpty(), strategies)
  }

  /**
   * Compaction for raw prompt strings (/v1/completions, /generate).
   * Only prompt trimming is possible — there's no message structure to truncate.
   *
   * @param prompt The raw prompt string.
   * @param maxContext Max context tokens for the model.
   * @param trimPrompts Whether "Trim Prompt" is enabled.
   */
  fun compactRawPrompt(
    prompt: String,
    maxContext: Int?,
    trimPrompts: Boolean,
  ): CompactionResult {
    if (maxContext == null || !trimPrompts || estimateTokens(prompt) <= maxContext) {
      return CompactionResult(prompt, false, emptyList())
    }
    return trimPrompt(prompt, maxContext)
  }

  /**
   * Hard-trim a prompt to fit within maxContext tokens, keeping the tail (most recent content).
   */
  private fun trimPrompt(
    prompt: String,
    maxContext: Int,
    existingStrategies: MutableList<String> = mutableListOf(),
  ): CompactionResult {
    val maxChars = maxContext * 4
    if (prompt.length <= maxChars) {
      return CompactionResult(prompt, existingStrategies.isNotEmpty(), existingStrategies)
    }
    val trimmed = prompt.takeLast(maxChars)
    existingStrategies.add("trimmed")
    return CompactionResult(trimmed, true, existingStrategies)
  }
}
