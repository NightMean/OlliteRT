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

import com.ollitert.llm.server.service.http.*

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the /v1/messages response-envelope contract: every JSON body leaving the
 * endpoint must be Anthropic-shaped — including pre-stream failures on
 * `stream:true` requests, which previously leaked the OpenAI error envelope.
 */
class AnthropicEndpointReshapeTest {

  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  private fun reshape(statusCode: Int, body: String) =
    reshapeAnthropicJsonResponse(
      json = json,
      response = HttpResponse.Json(statusCode, body),
      requestedModelId = "requested-model",
      requestId = "abc",
      matchedStopSequence = null,
    )

  @Test
  fun oaiErrorEnvelopesBecomeAnthropicEnvelopes() {
    val cases = mapOf(
      400 to "invalid_request_error",
      401 to "authentication_error",
      404 to "not_found_error",
      413 to "request_too_large",
      503 to "overloaded_error",
      500 to "api_error",
    )
    for ((status, expectedType) in cases) {
      val oaiBody =
        """{"error":{"message":"boom","type":"server_error","param":null,"code":null}}"""
      val resp = reshape(status, oaiBody)
      assertEquals(status, resp.statusCode)
      val root = json.parseToJsonElement(resp.body).jsonObject
      assertEquals("error", root["type"]!!.jsonPrimitive.content)
      val err = root["error"]!!.jsonObject
      assertEquals(expectedType, err["type"]!!.jsonPrimitive.content)
      assertEquals("boom", err["message"]!!.jsonPrimitive.content)
    }
  }

  @Test
  fun successBodyIsReshapedToMessageEnvelope() {
    val oai =
      """{"id":"chatcmpl-1","model":"resolved","choices":[{"index":0,"message":{"role":"assistant","content":"hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}"""
    val resp = reshape(200, oai)
    assertEquals(200, resp.statusCode)
    val root = json.parseToJsonElement(resp.body).jsonObject
    assertEquals("message", root["type"]!!.jsonPrimitive.content)
    assertEquals("end_turn", root["stop_reason"]!!.jsonPrimitive.content)
  }
}
