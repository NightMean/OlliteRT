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

import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.RequestPrefsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointHandlersHelpersTest {

  private fun model(): Model = Model(name = "test")

  // ── describeClientSamplerParams ─────────────────────────────────────────

  @Test
  fun describeAllNullReturnsNull() {
    assertNull(describeClientSamplerParams(null, null, null, null))
  }

  @Test
  fun describeTemperatureOnly() {
    assertEquals("temperature=0.5", describeClientSamplerParams(0.5, null, null, null))
  }

  @Test
  fun describeMultipleParams() {
    val result = describeClientSamplerParams(0.5, 0.9, 40, 1024)!!
    assertTrue(result.contains("temperature=0.5"))
    assertTrue(result.contains("top_p=0.9"))
    assertTrue(result.contains("top_k=40"))
    assertTrue(result.contains("max_tokens=1024"))
  }

  @Test
  fun describeTopPOnly() {
    assertEquals("top_p=0.95", describeClientSamplerParams(null, 0.95, null, null))
  }

  @Test
  fun describeMaxTokensOnly() {
    assertEquals("max_tokens=512", describeClientSamplerParams(null, null, null, 512))
  }

  // ── logCompactionResult ──────────────────────────────────────────────────

  @Test
  fun logCompactionResult_notCompacted_doesNothing() {
    val logged = mutableListOf<String>()
    var storeUpdated = false
    logCompactionResult(
      result = PromptCompactor.CompactionResult(prompt = "hello", compacted = false, strategies = emptyList()),
      requestId = "r1",
      endpoint = "/generate",
      logId = "log1",
      maxContext = null,
      logEvent = { logged.add(it) },
      updateLog = { _, _ -> storeUpdated = true },
    )
    assertTrue(logged.isEmpty())
    assertFalse(storeUpdated)
  }

  @Test
  fun logCompactionResult_compactedWithMaxContext_logsTokenEstimate() {
    val logged = mutableListOf<String>()
    logCompactionResult(
      result = PromptCompactor.CompactionResult(prompt = "hello world", compacted = true, strategies = listOf("truncated:-2 msgs")),
      requestId = "r2",
      endpoint = "/v1/chat/completions",
      logId = null,
      maxContext = 2048,
      logEvent = { logged.add(it) },
      updateLog = { _, _ -> },
    )
    assertEquals(1, logged.size)
    assertTrue(logged[0].contains("prompt_compacted"))
    assertTrue(logged[0].contains("endpoint=/v1/chat/completions"))
    assertTrue(logged[0].contains("strategies=[truncated:-2 msgs]"))
    assertTrue(logged[0].contains("estimatedTokens="))
    assertTrue(logged[0].contains("maxContext=2048"))
  }

  @Test
  fun logCompactionResult_compactedWithoutMaxContext_omitsTokenFields() {
    val logged = mutableListOf<String>()
    logCompactionResult(
      result = PromptCompactor.CompactionResult(prompt = "hi", compacted = true, strategies = listOf("trimmed")),
      requestId = "r3",
      endpoint = "/generate",
      logId = null,
      maxContext = null,
      logEvent = { logged.add(it) },
      updateLog = { _, _ -> },
    )
    assertEquals(1, logged.size)
    assertTrue(logged[0].contains("prompt_compacted"))
    assertTrue(logged[0].contains("endpoint=/generate"))
    assertTrue(logged[0].contains("strategies=[trimmed]"))
    assertFalse(logged[0].contains("estimatedTokens="))
    assertFalse(logged[0].contains("maxContext="))
  }

  @Test
  fun logCompactionResult_compactedWithLogId_callsUpdateLogWithDetails() {
    var capturedDetails = ""
    var capturedPrompt = ""
    logCompactionResult(
      result = PromptCompactor.CompactionResult(prompt = "data", compacted = true, strategies = listOf("a", "b")),
      requestId = "r4",
      endpoint = "/v1/completions",
      logId = "log99",
      maxContext = 1024,
      logEvent = {},
      updateLog = { details, prompt -> capturedDetails = details; capturedPrompt = prompt },
    )
    assertEquals("a, b", capturedDetails)
    assertEquals("data", capturedPrompt)
  }

  @Test
  fun logCompactionResult_compactedWithoutLogId_skipsUpdateLog() {
    var updateCalled = false
    logCompactionResult(
      result = PromptCompactor.CompactionResult(prompt = "data", compacted = true, strategies = listOf("a")),
      requestId = "r5",
      endpoint = "/v1/responses",
      logId = null,
      maxContext = null,
      logEvent = {},
      updateLog = { _, _ -> updateCalled = true },
    )
    assertFalse(updateCalled)
  }

  @Test
  fun logCompactionResult_multipleStrategies_joinedWithComma() {
    val logged = mutableListOf<String>()
    logCompactionResult(
      result = PromptCompactor.CompactionResult(prompt = "x", compacted = true, strategies = listOf("truncated:-3 msgs", "tools:compacted", "trimmed")),
      requestId = "r6",
      endpoint = "/v1/chat/completions",
      logId = null,
      maxContext = 4096,
      logEvent = { logged.add(it) },
      updateLog = { _, _ -> },
    )
    assertTrue(logged[0].contains("strategies=[truncated:-3 msgs, tools:compacted, trimmed]"))
  }

  // ── resolveSamplerOverrides ───────────────────────────────────────────

  @Test
  fun resolveSampler_ignoreDisabled_passesClientValues() {
    val prefs = RequestPrefsSnapshot(ignoreClientSamplerParams = false)
    val result = resolveSamplerOverrides(
      model = model(), prefs = prefs,
      temperature = 0.7, topP = 0.9, topK = 40, maxTokens = 512, logId = null,
    )
    val config = result.configSnapshot!!
    assertEquals(0.7f, config[ConfigKeys.TEMPERATURE.label])
    assertEquals(0.9f, config[ConfigKeys.TOPP.label])
    assertEquals(40, config[ConfigKeys.TOPK.label])
  }

  @Test
  fun resolveSampler_ignoreEnabled_returnsNullConfig() {
    val prefs = RequestPrefsSnapshot(ignoreClientSamplerParams = true)
    val result = resolveSamplerOverrides(
      model = model(), prefs = prefs,
      temperature = 0.7, topP = 0.9, topK = 40, maxTokens = 512, logId = null,
    )
    assertNull(result.configSnapshot)
  }

  @Test
  fun resolveSampler_allNullClientParams_returnsNullConfig() {
    val prefs = RequestPrefsSnapshot(ignoreClientSamplerParams = false)
    val result = resolveSamplerOverrides(
      model = model(), prefs = prefs,
      temperature = null, topP = null, topK = null, maxTokens = null, logId = null,
    )
    assertNull(result.configSnapshot)
  }

  @Test
  fun resolveSampler_partialParams_onlyOverridesProvided() {
    val prefs = RequestPrefsSnapshot(ignoreClientSamplerParams = false)
    val result = resolveSamplerOverrides(
      model = model(), prefs = prefs,
      temperature = 0.5, topP = null, topK = null, maxTokens = null, logId = null,
    )
    val config = result.configSnapshot!!
    assertEquals(0.5f, config[ConfigKeys.TEMPERATURE.label])
  }
}
