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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [LlmHttpPromptCompactor] — progressive prompt compaction.
 *
 * These tests exercise the compaction strategies (truncation, tool compaction, trimming)
 * using real [LlmHttpRequestAdapter] prompt building. All functions are pure logic.
 */
class LlmHttpPromptCompactorTest {

  // ── estimateTokens() ─────────────────────────────────────────────────────

  @Test
  fun estimateTokensEmptyString() {
    assertEquals(0, estimateTokens(""))
  }

  @Test
  fun estimateTokensSingleChar() {
    // Single char → charLen/4 = 0, but coerceAtLeast(1) for non-empty
    assertEquals(1, estimateTokens("x"))
  }

  @Test
  fun estimateTokensNormalText() {
    // 12 chars / 4 = 3
    assertEquals(3, estimateTokens("Hello World!"))
  }

  @Test
  fun estimateTokensExactMultiple() {
    // 8 chars / 4 = 2
    assertEquals(2, estimateTokens("12345678"))
  }

  @Test
  fun estimateTokensThreeChars() {
    // 3 chars / 4 = 0, but coerceAtLeast(1) for non-empty
    assertEquals(1, estimateTokens("abc"))
  }

  // ── compactRawPrompt() ───────────────────────────────────────────────────

  @Test
  fun compactRawPromptFitsReturnsUnchanged() {
    val result = LlmHttpPromptCompactor.compactRawPrompt(
      prompt = "short",
      maxContext = 1000,
      trimPrompts = true,
    )
    assertEquals("short", result.prompt)
    assertFalse(result.compacted)
    assertTrue(result.strategies.isEmpty())
  }

  @Test
  fun compactRawPromptNullMaxContextReturnsUnchanged() {
    val result = LlmHttpPromptCompactor.compactRawPrompt(
      prompt = "anything goes",
      maxContext = null,
      trimPrompts = true,
    )
    assertEquals("anything goes", result.prompt)
    assertFalse(result.compacted)
  }

  @Test
  fun compactRawPromptTrimDisabledReturnsUnchanged() {
    val longPrompt = "x".repeat(10000)
    val result = LlmHttpPromptCompactor.compactRawPrompt(
      prompt = longPrompt,
      maxContext = 1, // way too small
      trimPrompts = false,
    )
    assertEquals(longPrompt, result.prompt)
    assertFalse(result.compacted)
  }

  @Test
  fun compactRawPromptTrimsTail() {
    val longPrompt = "START" + "x".repeat(100) + "END"
    // maxContext=5 → maxChars = 5*4 = 20 chars
    val result = LlmHttpPromptCompactor.compactRawPrompt(
      prompt = longPrompt,
      maxContext = 5,
      trimPrompts = true,
    )
    assertTrue(result.compacted)
    assertEquals(20, result.prompt.length)
    // Should keep the tail (most recent content)
    assertTrue("should end with END", result.prompt.endsWith("END"))
  }

  @Test
  fun compactRawPromptResultHasTrimmedStrategy() {
    val longPrompt = "x".repeat(100)
    val result = LlmHttpPromptCompactor.compactRawPrompt(
      prompt = longPrompt,
      maxContext = 5,
      trimPrompts = true,
    )
    assertEquals(listOf("trimmed"), result.strategies)
  }

  // ── compactConversationPrompt() ──────────────────────────────────────────

  @Test
  fun compactConversationPromptFitsReturnsUnchanged() {
    val messages = listOf(
      InputMsg("user", listOf(InputContent("input_text", "hello"))),
    )
    val result = LlmHttpPromptCompactor.compactConversationPrompt(
      messages = messages,
      chatTemplate = null,
      maxContext = 1000,
      truncateHistory = true,
      trimPrompts = true,
    )
    assertFalse(result.compacted)
    assertTrue(result.prompt.contains("hello"))
  }

  @Test
  fun compactConversationPromptNullMaxContextReturnsUnchanged() {
    val messages = listOf(
      InputMsg("user", listOf(InputContent("input_text", "hello"))),
    )
    val result = LlmHttpPromptCompactor.compactConversationPrompt(
      messages = messages,
      chatTemplate = null,
      maxContext = null,
      truncateHistory = true,
      trimPrompts = true,
    )
    assertFalse(result.compacted)
  }

  @Test
  fun compactConversationPromptTruncatesHistory() {
    // Create a long conversation that exceeds context limit
    val longText = "x".repeat(200) // ~50 tokens per message
    val messages = listOf(
      InputMsg("system", listOf(InputContent("text", "System instruction"))),
      InputMsg("user", listOf(InputContent("input_text", "old message 1 $longText"))),
      InputMsg("assistant", listOf(InputContent("output_text", "old reply 1 $longText"))),
      InputMsg("user", listOf(InputContent("input_text", "old message 2 $longText"))),
      InputMsg("assistant", listOf(InputContent("output_text", "old reply 2 $longText"))),
      InputMsg("user", listOf(InputContent("input_text", "current question"))),
    )
    val result = LlmHttpPromptCompactor.compactConversationPrompt(
      messages = messages,
      chatTemplate = null,
      maxContext = 20, // Very small — force truncation
      truncateHistory = true,
      trimPrompts = false,
    )
    // Should truncate, keeping system + latest non-system messages
    assertTrue("should contain truncation strategy", result.strategies.any { it.startsWith("truncated:") })
  }

  @Test
  fun compactConversationPromptPreservesSystemMessages() {
    val messages = listOf(
      InputMsg("system", listOf(InputContent("text", "SYSTEM_INSTRUCTION"))),
      InputMsg("user", listOf(InputContent("input_text", "old question " + "x".repeat(200)))),
      InputMsg("user", listOf(InputContent("input_text", "new question"))),
    )
    val result = LlmHttpPromptCompactor.compactConversationPrompt(
      messages = messages,
      chatTemplate = null,
      maxContext = 15, // Force truncation
      truncateHistory = true,
      trimPrompts = false,
    )
    // System instruction should survive truncation
    assertTrue("system instruction should be preserved", result.prompt.contains("SYSTEM_INSTRUCTION"))
  }

  @Test
  fun compactConversationPromptNoTogglesReturnsUncompacted() {
    val messages = listOf(
      InputMsg("user", listOf(InputContent("input_text", "x".repeat(1000)))),
    )
    val result = LlmHttpPromptCompactor.compactConversationPrompt(
      messages = messages,
      chatTemplate = null,
      maxContext = 1, // Way too small
      truncateHistory = false,
      trimPrompts = false,
    )
    assertFalse(result.compacted)
  }

  @Test
  fun compactConversationPromptNullMessages() {
    val result = LlmHttpPromptCompactor.compactConversationPrompt(
      messages = null,
      chatTemplate = null,
      maxContext = 1,
      truncateHistory = true,
      trimPrompts = true,
    )
    // With null messages, buildConversationPrompt returns "" which is ≤ any maxContext
    assertFalse(result.compacted)
  }

  // ── compactChatPrompt() ──────────────────────────────────────────────────

  @Test
  fun compactChatPromptFitsReturnsUnchanged() {
    val messages = listOf(
      ChatMessage("user", ChatContent("hello")),
    )
    val result = LlmHttpPromptCompactor.compactChatPrompt(
      messages = messages,
      tools = null,
      toolChoice = null,
      chatTemplate = null,
      maxContext = 1000,
      truncateHistory = true,
      compactToolSchemas = true,
      trimPrompts = true,
    )
    assertFalse(result.compacted)
    assertTrue(result.prompt.contains("hello"))
  }

  @Test
  fun compactChatPromptTruncatesHistory() {
    val longText = "x".repeat(200)
    val messages = listOf(
      ChatMessage("system", ChatContent("System instruction")),
      ChatMessage("user", ChatContent("old $longText")),
      ChatMessage("assistant", ChatContent("old reply $longText")),
      ChatMessage("user", ChatContent("current question")),
    )
    val result = LlmHttpPromptCompactor.compactChatPrompt(
      messages = messages,
      tools = null,
      toolChoice = null,
      chatTemplate = null,
      maxContext = 20,
      truncateHistory = true,
      compactToolSchemas = false,
      trimPrompts = false,
    )
    assertTrue("should contain truncation strategy", result.strategies.any { it.startsWith("truncated:") })
  }

  @Test
  fun compactChatPromptPreservesSystemMessage() {
    val messages = listOf(
      ChatMessage("system", ChatContent("IMPORTANT_SYSTEM")),
      ChatMessage("user", ChatContent("old " + "x".repeat(200))),
      ChatMessage("user", ChatContent("new question")),
    )
    val result = LlmHttpPromptCompactor.compactChatPrompt(
      messages = messages,
      tools = null,
      toolChoice = null,
      chatTemplate = null,
      maxContext = 15,
      truncateHistory = true,
      compactToolSchemas = false,
      trimPrompts = false,
    )
    assertTrue("system message should survive", result.prompt.contains("IMPORTANT_SYSTEM"))
  }

  @Test
  fun compactChatPromptNoTogglesReturnsOversized() {
    val messages = listOf(
      ChatMessage("user", ChatContent("x".repeat(1000))),
    )
    val result = LlmHttpPromptCompactor.compactChatPrompt(
      messages = messages,
      tools = null,
      toolChoice = null,
      chatTemplate = null,
      maxContext = 1,
      truncateHistory = false,
      compactToolSchemas = false,
      trimPrompts = false,
    )
    assertFalse(result.compacted)
    assertTrue(result.strategies.isEmpty())
  }

  @Test
  fun compactChatPromptTrimsAsLastResort() {
    val messages = listOf(
      ChatMessage("user", ChatContent("x".repeat(1000))),
    )
    val result = LlmHttpPromptCompactor.compactChatPrompt(
      messages = messages,
      tools = null,
      toolChoice = null,
      chatTemplate = null,
      maxContext = 5, // maxChars = 20
      truncateHistory = false,
      compactToolSchemas = false,
      trimPrompts = true,
    )
    assertTrue(result.compacted)
    assertTrue("should contain trimmed strategy", result.strategies.contains("trimmed"))
    // Trimmed prompt length should be maxContext * 4 = 20
    assertEquals(20, result.prompt.length)
  }
}
