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

class LogResponseContentTest {

  @Test
  fun pendingEntryKeepsLivePartialOutputAndGenerationState() {
    val entry = requestEntry(isPending = true, isGenerating = true, partialText = "partial")

    assertEquals(
      LogResponseContent.Pending(partialText = "partial", isGenerating = true),
      resolveLogResponseContent(entry),
    )
  }

  @Test
  fun cancelledEntryKeepsPartialOutputAndCancellationSource() {
    val entry = requestEntry(
      isCancelled = true,
      cancelledByUser = true,
      partialText = "generated before stop",
    )

    assertEquals(
      LogResponseContent.Cancelled(
        partialText = "generated before stop",
        cancelledByUser = true,
      ),
      resolveLogResponseContent(entry),
    )
  }

  @Test
  fun completedEntryUsesResponseBody() {
    val entry = requestEntry(responseBody = "{\"ok\":true}")

    assertEquals(
      LogResponseContent.Completed("{\"ok\":true}"),
      resolveLogResponseContent(entry),
    )
  }

  @Test
  fun entryWithoutResponseStateResolvesToNone() {
    assertEquals(LogResponseContent.None, resolveLogResponseContent(requestEntry()))
  }

  private fun requestEntry(
    isPending: Boolean = false,
    isGenerating: Boolean = false,
    isCancelled: Boolean = false,
    cancelledByUser: Boolean = false,
    partialText: String? = null,
    responseBody: String? = null,
  ) = RequestLogEntry(
    id = "entry",
    timestamp = 1L,
    method = "POST",
    path = "/v1/chat/completions",
    statusCode = 200,
    latencyMs = 0L,
    isPending = isPending,
    isGenerating = isGenerating,
    isCancelled = isCancelled,
    cancelledByUser = cancelledByUser,
    partialText = partialText,
    responseBody = responseBody,
  )
}
