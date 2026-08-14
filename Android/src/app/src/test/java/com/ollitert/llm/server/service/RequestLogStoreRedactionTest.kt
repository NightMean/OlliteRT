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
import com.ollitert.llm.server.service.inference.*

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestLogStoreRedactionTest {

  @Test
  fun redactsAuthorizationHeaderRegardlessOfCase() {
    val redacted = RequestLogStore.redactSensitiveHeaders(mapOf("Authorization" to "Bearer secret"))
    assertEquals("<redacted>", redacted["Authorization"])
  }

  @Test
  fun redactsXApiKeyHeader() {
    val redacted = RequestLogStore.redactSensitiveHeaders(mapOf("x-api-key" to "sk-abc-123"))
    assertEquals("<redacted>", redacted["x-api-key"])
  }

  @Test
  fun redactsCookieAndProxyAuthorization() {
    val redacted = RequestLogStore.redactSensitiveHeaders(
      mapOf(
        "Cookie" to "session=abc",
        "Proxy-Authorization" to "Basic xyz",
      )
    )
    assertEquals("<redacted>", redacted["Cookie"])
    assertEquals("<redacted>", redacted["Proxy-Authorization"])
  }

  @Test
  fun preservesNonSensitiveHeaders() {
    val redacted = RequestLogStore.redactSensitiveHeaders(
      mapOf(
        "Content-Type" to "application/json",
        "anthropic-version" to "2023-06-01",
        "User-Agent" to "claude-cli/1.0",
      )
    )
    assertEquals("application/json", redacted["Content-Type"])
    assertEquals("2023-06-01", redacted["anthropic-version"])
    assertEquals("claude-cli/1.0", redacted["User-Agent"])
  }

  @Test
  fun matchesHeaderNamesCaseInsensitively() {
    val redacted = RequestLogStore.redactSensitiveHeaders(
      mapOf("AUTHORIZATION" to "Bearer secret", "X-Api-Key" to "raw")
    )
    assertEquals("<redacted>", redacted["AUTHORIZATION"])
    assertEquals("<redacted>", redacted["X-Api-Key"])
  }
}
