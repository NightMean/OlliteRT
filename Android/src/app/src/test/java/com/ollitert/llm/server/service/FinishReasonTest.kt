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
import org.junit.Test

class FinishReasonTest {

  @Test
  fun inferReturnsLengthWhenTokensReachLimit() {
    assertEquals("length", FinishReason.infer(completionTokens = 100, maxTokens = 100))
  }

  @Test
  fun inferReturnsLengthWhenTokensExceedLimit() {
    assertEquals("length", FinishReason.infer(completionTokens = 105, maxTokens = 100))
  }

  @Test
  fun inferReturnsLengthWithinToleranceMargin() {
    assertEquals("length", FinishReason.infer(completionTokens = 96, maxTokens = 100))
  }

  @Test
  fun inferReturnsStopWhenWellBelowLimit() {
    assertEquals("stop", FinishReason.infer(completionTokens = 50, maxTokens = 100))
  }

  @Test
  fun inferReturnsStopWhenMaxTokensIsNull() {
    assertEquals("stop", FinishReason.infer(completionTokens = 100, maxTokens = null))
  }

  @Test
  fun inferReturnsStopWhenMaxTokensIsZero() {
    assertEquals("stop", FinishReason.infer(completionTokens = 100, maxTokens = 0))
  }

  @Test
  fun inferReturnsStopForEmptyOutput() {
    assertEquals("stop", FinishReason.infer(completionTokens = 0, maxTokens = 100))
  }

  @Test
  fun inferReturnsStopWhenNegativeMaxTokens() {
    assertEquals("stop", FinishReason.infer(completionTokens = 50, maxTokens = -1))
  }

  @Test
  fun toleranceScalesWithSmallLimits() {
    // maxTokens=10, threshold = floor(10 * 0.95) = 9 → 9 tokens triggers "length"
    assertEquals("length", FinishReason.infer(completionTokens = 9, maxTokens = 10))
    assertEquals("stop", FinishReason.infer(completionTokens = 8, maxTokens = 10))
    assertEquals("length", FinishReason.infer(completionTokens = 10, maxTokens = 10))
  }

  @Test
  fun inferWorksWithEstimatedTokensFromText() {
    val text = "a".repeat(400)
    val completionTokens = estimateTokens(text)
    assertEquals(100, completionTokens)
    assertEquals("length", FinishReason.infer(completionTokens, maxTokens = 100))
  }

  @Test
  fun inferReturnsStopForShortResponse() {
    val text = "a".repeat(100)
    val completionTokens = estimateTokens(text)
    assertEquals(25, completionTokens)
    assertEquals("stop", FinishReason.infer(completionTokens, maxTokens = 100))
  }
}
