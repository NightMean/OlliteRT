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

/**
 * Progressive prompt compaction to handle context window overflow.
 *
 * When a prompt exceeds the model's max context tokens, strategies are applied
 * in order until the prompt fits:
 * 1. Conversation history truncation — keep system/developer messages + last N non-system messages
 * 2. Tool schema compaction — reduce to names+descriptions only
 * 3. Prompt trimming — hard-trim the string, keeping the tail (most recent content)
 * 4. Full tool removal — strip tool schemas entirely (only reachable when trimming is disabled)
 *
 * Each strategy is gated by its own setting toggle:
 * - "Truncate Conversation History" gates history truncation
 * - "Compact Tool Schemas" gates tool compaction (2) and full tool removal (4)
 * - "Trim Prompt" gates hard-trimming; when enabled, trimming always resolves before (4)
 */
object LlmHttpPromptCompactor {

  /**
   * Result of a compaction attempt.
   * @param prompt The (possibly compacted) prompt string ready for inference.
   * @param compacted Whether any compaction strategy was applied.
   * @param strategies Short tags for strategies applied, e.g. ["truncated:-4 msgs", "tools:compacted"].
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
   * @param compactToolSchemas Whether "Compact Tool Schemas" is enabled.
   * @param trimPrompts Whether "Trim Prompt" is enabled.
   */
  fun compactChatPrompt(
    messages: List<ChatMessage>,
    tools: List<ToolSpec>?,
    toolChoice: String?,
    chatTemplate: String?,
    maxContext: Int?,
    truncateHistory: Boolean,
    compactToolSchemas: Boolean,
    trimPrompts: Boolean,
    interleaveImagePlaceholders: Boolean = false,
  ): CompactionResult {
    val hasTools = !tools.isNullOrEmpty() && toolChoice != "none"

    // Build the full (uncompacted) prompt
    val fullPrompt = if (hasTools) {
      LlmHttpRequestAdapter.buildToolAwarePrompt(messages, tools, toolChoice, chatTemplate, interleaveImagePlaceholders = interleaveImagePlaceholders)
    } else {
      LlmHttpRequestAdapter.buildChatPrompt(messages, chatTemplate, interleaveImagePlaceholders)
    }

    // If no context limit is known, or prompt fits, return as-is
    if (maxContext == null || estimateTokens(fullPrompt) <= maxContext) {
      return CompactionResult(fullPrompt, false, emptyList())
    }

    // No compaction toggle is enabled — return the oversized prompt and let inference error
    if (!truncateHistory && !compactToolSchemas && !trimPrompts) {
      return CompactionResult(fullPrompt, false, emptyList())
    }

    val strategies = mutableListOf<String>()
    var currentToolsCompact = false
    var currentToolsRemoved = false
    var currentMessages = messages

    // --- Strategy 1: Conversation history truncation ---
    // Keep system/developer messages at their original positions + last N non-system messages
    if (truncateHistory && messages.size > 2) {
      val systemMsgs = messages.filter { it.role == "system" || it.role == "developer" }
      val nonSystemMsgs = messages.filter { it.role != "system" && it.role != "developer" }

      if (nonSystemMsgs.size > 1) {
        // Try progressively fewer non-system messages until the prompt fits
        for (keep in (nonSystemMsgs.size - 1) downTo 1) {
          val truncatedMsgs = systemMsgs + nonSystemMsgs.takeLast(keep)
          val candidate = if (hasTools) {
            LlmHttpRequestAdapter.buildToolAwarePrompt(truncatedMsgs, tools, toolChoice, chatTemplate, interleaveImagePlaceholders = interleaveImagePlaceholders)
          } else {
            LlmHttpRequestAdapter.buildChatPrompt(truncatedMsgs, chatTemplate, interleaveImagePlaceholders)
          }
          if (estimateTokens(candidate) <= maxContext) {
            val dropped = nonSystemMsgs.size - keep
            strategies.add("truncated:-$dropped msgs")
            return CompactionResult(candidate, true, strategies)
          }
        }
        // Even keeping just 1 non-system message didn't fit — continue with truncated list
        currentMessages = systemMsgs + nonSystemMsgs.takeLast(1)
        strategies.add("truncated:-${nonSystemMsgs.size - 1} msgs")
      }
    }

    // --- Strategy 2: Tool schema compaction ---
    // Only compacts tools to names+descriptions. Does NOT remove tools — gives Strategy 3
    // (trimming) a chance to fit the prompt with compact tools before escalating to removal.
    if (hasTools && compactToolSchemas) {
      val compactPrompt = LlmHttpRequestAdapter.buildToolAwarePrompt(
        currentMessages, tools, toolChoice, chatTemplate, compact = true, interleaveImagePlaceholders = interleaveImagePlaceholders,
      )
      if (estimateTokens(compactPrompt) <= maxContext) {
        strategies.add("tools:compacted")
        return CompactionResult(compactPrompt, true, strategies)
      }
      currentToolsCompact = true
      strategies.add("tools:compacted")
    }

    // --- Strategy 3: Prompt trimming ---
    if (trimPrompts) {
      val currentPrompt = when {
        hasTools && !currentToolsRemoved && currentToolsCompact ->
          LlmHttpRequestAdapter.buildToolAwarePrompt(currentMessages, tools, toolChoice, chatTemplate, compact = true, interleaveImagePlaceholders = interleaveImagePlaceholders)
        hasTools && !currentToolsRemoved ->
          LlmHttpRequestAdapter.buildToolAwarePrompt(currentMessages, tools, toolChoice, chatTemplate, interleaveImagePlaceholders = interleaveImagePlaceholders)
        else ->
          LlmHttpRequestAdapter.buildChatPrompt(currentMessages, chatTemplate, interleaveImagePlaceholders)
      }

      val maxChars = maxContext * 4
      if (currentPrompt.length > maxChars) {
        val trimmed = currentPrompt.takeLast(maxChars)
        strategies.add("trimmed")
        return CompactionResult(trimmed, true, strategies)
      }
      return CompactionResult(currentPrompt, strategies.isNotEmpty(), strategies)
    }

    // --- Strategy 4: Full tool removal (last resort before giving up) ---
    if (hasTools && compactToolSchemas && !currentToolsRemoved) {
      val noToolsPrompt = LlmHttpRequestAdapter.buildChatPrompt(currentMessages, chatTemplate, interleaveImagePlaceholders)
      if (estimateTokens(noToolsPrompt) <= maxContext) {
        strategies.add("tools:removed")
        return CompactionResult(noToolsPrompt, true, strategies)
      }
      currentToolsRemoved = true
      strategies.add("tools:removed")
    }

    // Compaction strategies exhausted or not enabled — return best effort
    val bestPrompt = when {
      hasTools && !currentToolsRemoved && currentToolsCompact ->
        LlmHttpRequestAdapter.buildToolAwarePrompt(currentMessages, tools, toolChoice, chatTemplate, compact = true, interleaveImagePlaceholders = interleaveImagePlaceholders)
      hasTools && !currentToolsRemoved ->
        LlmHttpRequestAdapter.buildToolAwarePrompt(currentMessages, tools, toolChoice, chatTemplate, interleaveImagePlaceholders = interleaveImagePlaceholders)
      else ->
        LlmHttpRequestAdapter.buildChatPrompt(currentMessages, chatTemplate, interleaveImagePlaceholders)
    }
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
    val fullPrompt = LlmHttpRequestAdapter.buildConversationPrompt(messages, chatTemplate)

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

    // Strategy 1: Conversation history truncation
    if (truncateHistory) {
      val systemMsgs = messages.filter { it.role == "system" || it.role == "developer" }
      val nonSystemMsgs = messages.filter { it.role != "system" && it.role != "developer" }

      if (nonSystemMsgs.size > 1) {
        for (keep in (nonSystemMsgs.size - 1) downTo 1) {
          val truncatedMsgs = systemMsgs + nonSystemMsgs.takeLast(keep)
          val candidate = LlmHttpRequestAdapter.buildConversationPrompt(truncatedMsgs, chatTemplate)
          if (estimateTokens(candidate) <= maxContext) {
            val dropped = nonSystemMsgs.size - keep
            strategies.add("truncated:-$dropped msgs")
            return CompactionResult(candidate, true, strategies)
          }
        }
        strategies.add("truncated:-${nonSystemMsgs.size - 1} msgs")
      }
    }

    // Strategy 2: Prompt trimming (last resort)
    if (trimPrompts) {
      val minPrompt = if (truncateHistory && messages.size > 1) {
        val systemMsgs = messages.filter { it.role == "system" || it.role == "developer" }
        val nonSystemMsgs = messages.filter { it.role != "system" && it.role != "developer" }
        LlmHttpRequestAdapter.buildConversationPrompt(systemMsgs + nonSystemMsgs.takeLast(1), chatTemplate)
      } else {
        fullPrompt
      }
      return trimPrompt(minPrompt, maxContext, strategies)
    }

    // No more strategies — return best effort with whatever truncation was done
    val bestPrompt = if (strategies.isNotEmpty()) {
      val systemMsgs = messages.filter { it.role == "system" || it.role == "developer" }
      val nonSystemMsgs = messages.filter { it.role != "system" && it.role != "developer" }
      LlmHttpRequestAdapter.buildConversationPrompt(systemMsgs + nonSystemMsgs.takeLast(1), chatTemplate)
    } else {
      fullPrompt
    }
    return CompactionResult(bestPrompt, strategies.isNotEmpty(), strategies)
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
