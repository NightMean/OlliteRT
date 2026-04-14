package com.ollitert.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure-logic companion object functions in [LlmHttpInferenceRunner].
 * These functions are stateless utilities that can be tested without any Android context.
 */
class LlmHttpInferenceRunnerCompanionTest {

  // ── applyStopSequences() ─────────────────────────────────────────────────

  @Test
  fun applyStopSequencesNullReturnsOriginal() {
    val (text, truncated) = LlmHttpInferenceRunner.applyStopSequences("hello world", null)
    assertEquals("hello world", text)
    assertFalse(truncated)
  }

  @Test
  fun applyStopSequencesEmptyListReturnsOriginal() {
    val (text, truncated) = LlmHttpInferenceRunner.applyStopSequences("hello world", emptyList())
    assertEquals("hello world", text)
    assertFalse(truncated)
  }

  @Test
  fun applyStopSequencesTruncatesAtMatch() {
    val (text, truncated) = LlmHttpInferenceRunner.applyStopSequences("hello world", listOf("world"))
    assertEquals("hello ", text)
    assertTrue(truncated)
  }

  @Test
  fun applyStopSequencesNoMatchReturnsOriginal() {
    val (text, truncated) = LlmHttpInferenceRunner.applyStopSequences("hello world", listOf("xyz"))
    assertEquals("hello world", text)
    assertFalse(truncated)
  }

  @Test
  fun applyStopSequencesMultipleUsesEarliest() {
    val (text, truncated) = LlmHttpInferenceRunner.applyStopSequences(
      "abc|def|ghi",
      listOf("|ghi", "|def"),
    )
    assertEquals("abc", text)
    assertTrue(truncated)
  }

  @Test
  fun applyStopSequencesAtStartReturnsEmpty() {
    val (text, truncated) = LlmHttpInferenceRunner.applyStopSequences("stop here", listOf("stop"))
    assertEquals("", text)
    assertTrue(truncated)
  }

  @Test
  fun applyStopSequencesMatchesFirstOccurrence() {
    val (text, _) = LlmHttpInferenceRunner.applyStopSequences("a<end>b<end>c", listOf("<end>"))
    assertEquals("a", text)
  }

  // ── applyResponseFormat() ────────────────────────────────────────────────

  @Test
  fun applyResponseFormatNullReturnsOriginal() {
    assertEquals("prompt", LlmHttpInferenceRunner.applyResponseFormat("prompt", null))
  }

  @Test
  fun applyResponseFormatTextTypeReturnsOriginal() {
    assertEquals("prompt", LlmHttpInferenceRunner.applyResponseFormat("prompt", ResponseFormat("text")))
  }

  @Test
  fun applyResponseFormatJsonObjectPrependsInstruction() {
    val result = LlmHttpInferenceRunner.applyResponseFormat("prompt", ResponseFormat("json_object"))
    assertTrue(result.startsWith("Respond with valid JSON only."))
    assertTrue(result.endsWith("prompt"))
  }

  @Test
  fun applyResponseFormatJsonSchemaPrependsInstruction() {
    val result = LlmHttpInferenceRunner.applyResponseFormat("prompt", ResponseFormat("json_schema"))
    assertTrue(result.startsWith("Respond with valid JSON only."))
    assertTrue(result.endsWith("prompt"))
  }

  @Test
  fun applyResponseFormatUnknownTypeReturnsOriginal() {
    assertEquals("prompt", LlmHttpInferenceRunner.applyResponseFormat("prompt", ResponseFormat("xml")))
  }

  // ── enrichLlmError() ────────────────────────────────────────────────────

  @Test
  fun enrichLlmErrorContextOverflow() {
    val (enriched, kind) = LlmHttpInferenceRunner.enrichLlmError("6579 >= 4000")
    assertEquals(ErrorKind.CONTEXT_OVERFLOW, kind)
    // Should contain the original error and a suggestion
    assertTrue(enriched.contains("6579 >= 4000"))
    assertTrue(enriched.contains("—")) // suggestion separator
  }

  @Test
  fun enrichLlmErrorTimeoutClassifies() {
    val (_, kind) = LlmHttpInferenceRunner.enrichLlmError("timeout")
    assertEquals(ErrorKind.TIMEOUT, kind)
  }

  @Test
  fun enrichLlmErrorUnknownReturnsOriginalWithKind() {
    val (enriched, kind) = LlmHttpInferenceRunner.enrichLlmError("something weird happened")
    assertEquals(ErrorKind.UNKNOWN_LITERT, kind)
    // For unknown errors, enriched should equal original (no suggestion appended)
    assertEquals("something weird happened", enriched)
  }

  // ── extractActualTokenCounts() ───────────────────────────────────────────

  @Test
  fun extractActualTokenCountsValidPattern() {
    val result = LlmHttpInferenceRunner.extractActualTokenCounts("6579 >= 4000")
    assertEquals(6579L to 4000L, result)
  }

  @Test
  fun extractActualTokenCountsWithExtraSpaces() {
    val result = LlmHttpInferenceRunner.extractActualTokenCounts("6579  >=  4000")
    assertEquals(6579L to 4000L, result)
  }

  @Test
  fun extractActualTokenCountsInLongerMessage() {
    val result = LlmHttpInferenceRunner.extractActualTokenCounts(
      "Expected number of tokens in prompt is 6579 >= 4000 max context length"
    )
    assertEquals(6579L to 4000L, result)
  }

  @Test
  fun extractActualTokenCountsNoMatchReturnsNull() {
    assertNull(LlmHttpInferenceRunner.extractActualTokenCounts("some other error"))
  }

  @Test
  fun extractActualTokenCountsZeroValuesReturnsNull() {
    assertNull(LlmHttpInferenceRunner.extractActualTokenCounts("0 >= 0"))
  }

  @Test
  fun extractActualTokenCountsEmptyStringReturnsNull() {
    assertNull(LlmHttpInferenceRunner.extractActualTokenCounts(""))
  }
}
