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

package com.ollitert.llm.server.ui.server.logs

import com.ollitert.llm.server.data.model.RequestLogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class LogRequestDiagnosticsTest {

  @Test
  fun requestSizePrefersOriginalPreCompactionSize() {
    val entry = requestEntry(requestBody = "compact", originalRequestBodySize = 42_000)

    assertEquals(42_000, requestBodySizeChars(entry))
  }

  @Test
  fun requestSizeFallsBackToStoredBodyLength() {
    val entry = requestEntry(requestBody = "stored body", originalRequestBodySize = 0)

    assertEquals(11, requestBodySizeChars(entry))
  }

  @Test
  fun compactionDetailsProduceDetailedStrategyBadges() {
    assertEquals(
      listOf("Truncated: -4 msgs", "Trimmed"),
      parseCompactionBadges("truncated:-4 msgs, trimmed").map { it.first },
    )
  }

  private fun requestEntry(
    requestBody: String,
    originalRequestBodySize: Int,
  ) = RequestLogEntry(
    id = "entry",
    timestamp = 1L,
    method = "POST",
    path = "/v1/chat/completions",
    statusCode = 200,
    latencyMs = 0L,
    requestBody = requestBody,
    originalRequestBodySize = originalRequestBodySize,
  )
}
