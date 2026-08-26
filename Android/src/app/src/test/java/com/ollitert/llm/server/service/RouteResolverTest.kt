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

import com.ollitert.llm.server.service.http.RouteResolver

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the path-classification helpers used by pre-routing middleware
 * (error-envelope selection and unsupported-endpoint messaging).
 */
class RouteResolverTest {

  // ── Unsupported endpoint messages────────────────────────────────

  @Test
  fun unsupportedEndpointsReturnDescriptiveMessages() {
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/embeddings"))
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/v1/audio/transcriptions"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/images/generations"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/fine_tuning/jobs"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/fine-tuning/jobs"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/files"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/batches"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/assistants"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/threads"))
    assertNotNull(RouteResolver.getUnsupportedEndpointMessage("/v1/vector_stores"))
  }

  @Test
  fun unsupportedEndpointMessagesAreDescriptive() {
    val msg = RouteResolver.getUnsupportedEndpointMessage("/v1/embeddings")!!
    assertTrue("Message should explain why", msg.contains("not supported"))
    assertTrue("Message should be specific", msg.contains("inference-only"))
  }

  @Test
  fun unknownEndpointsReturnNull() {
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/v1/chat/completions"))
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/v1/models"))
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/some/random/path"))
    assertNull(RouteResolver.getUnsupportedEndpointMessage("/"))
  }

  @Test
  fun audioSpeechStillReturnsUnsupportedMessage() {
    val msg = RouteResolver.getUnsupportedEndpointMessage("/v1/audio/speech")
    assertNotNull(msg)
    assertTrue("Message should explain speech synthesis is not supported", msg!!.contains("speech"))
  }

  // ── Anthropic path classification (error-envelope selection) ──────────────

  @Test
  fun anthropicApiPathMatchesMessagesEndpoints() {
    assertTrue(RouteResolver.isAnthropicApiPath("/v1/messages"))
    assertTrue(RouteResolver.isAnthropicApiPath("/v1/messages/count_tokens"))
    assertTrue(RouteResolver.isAnthropicApiPath("/v1/messages?beta=true"))
  }

  @Test
  fun anthropicApiPathRejectsOtherEndpoints() {
    assertFalse(RouteResolver.isAnthropicApiPath("/v1/chat/completions"))
    assertFalse(RouteResolver.isAnthropicApiPath("/v1/models"))
    assertFalse(RouteResolver.isAnthropicApiPath("/v1/messageships"))
    assertFalse(RouteResolver.isAnthropicApiPath("/health"))
  }
}
