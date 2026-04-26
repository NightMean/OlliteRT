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

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for stream_options.include_usage extraction and defaults.
 *
 * The actual conditional emission lives in ChatCompletionsFormat (private inner class
 * of InferenceRunner) and requires the full inference stack to exercise. These tests
 * verify the deserialization and extraction layer that feeds into it.
 */
class InferenceRunnerStreamOptionsTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun includeUsageTrueExtractedFromRequest() {
    val req = json.decodeFromString<ChatRequest>(
      """{"messages":[],"stream":true,"stream_options":{"include_usage":true}}"""
    )
    val includeUsage = req.stream_options?.include_usage == true
    assertTrue(includeUsage)
  }

  @Test
  fun includeUsageFalseExtractedFromRequest() {
    val req = json.decodeFromString<ChatRequest>(
      """{"messages":[],"stream":true,"stream_options":{"include_usage":false}}"""
    )
    val includeUsage = req.stream_options?.include_usage == true
    assertFalse(includeUsage)
  }

  @Test
  fun missingStreamOptionsDefaultsToNoUsage() {
    val req = json.decodeFromString<ChatRequest>(
      """{"messages":[],"stream":true}"""
    )
    assertNull(req.stream_options)
    val includeUsage = req.stream_options?.include_usage == true
    assertFalse(includeUsage)
  }

  @Test
  fun streamOptionsWithoutIncludeUsageDefaultsToFalse() {
    val req = json.decodeFromString<ChatRequest>(
      """{"messages":[],"stream":true,"stream_options":{}}"""
    )
    assertEquals(false, req.stream_options?.include_usage)
    val includeUsage = req.stream_options?.include_usage == true
    assertFalse(includeUsage)
  }

  @Test
  fun nonStreamingRequestIgnoresStreamOptions() {
    val req = json.decodeFromString<ChatRequest>(
      """{"messages":[],"stream_options":{"include_usage":true}}"""
    )
    assertEquals(true, req.stream_options?.include_usage)
    assertNull(req.stream)
  }
}
