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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEventCardTest {

  @Test
  fun currentLoadingEventOwnsTimer() {
    assertTrue(
      isActiveModelLoadingEvent(
        parsedEvent = ParsedEventType.Loading("Gemma"),
        entryTimestampMs = 1_001L,
        activeModelName = "Gemma",
        loadingStartedAtMs = 1_000L,
      )
    )
  }

  @Test
  fun historicalOrCompletedLoadingEventDoesNotOwnTimer() {
    val loadingEvent = ParsedEventType.Loading("Gemma")

    assertFalse(
      isActiveModelLoadingEvent(
        parsedEvent = loadingEvent,
        entryTimestampMs = 999L,
        activeModelName = "Gemma",
        loadingStartedAtMs = 1_000L,
      )
    )
    assertFalse(
      isActiveModelLoadingEvent(
        parsedEvent = loadingEvent,
        entryTimestampMs = 1_001L,
        activeModelName = "Gemma",
        loadingStartedAtMs = 0L,
      )
    )
    assertFalse(
      isActiveModelLoadingEvent(
        parsedEvent = loadingEvent,
        entryTimestampMs = 1_001L,
        activeModelName = "Other model",
        loadingStartedAtMs = 1_000L,
      )
    )
  }

  @Test
  fun currentKeepAliveReloadEventOwnsTimer() {
    assertTrue(
      isActiveKeepAliveReloadingEvent(
        parsedEvent = ParsedEventType.KeepAliveReloading("Gemma"),
        entryTimestampMs = 2_001L,
        keepAliveReloadStartedAtMs = 2_000L,
      )
    )
  }

  @Test
  fun historicalOrCompletedKeepAliveReloadEventDoesNotOwnTimer() {
    val reloadEvent = ParsedEventType.KeepAliveReloading("Gemma")

    assertFalse(
      isActiveKeepAliveReloadingEvent(
        parsedEvent = reloadEvent,
        entryTimestampMs = 1_999L,
        keepAliveReloadStartedAtMs = 2_000L,
      )
    )
    assertFalse(
      isActiveKeepAliveReloadingEvent(
        parsedEvent = reloadEvent,
        entryTimestampMs = 2_001L,
        keepAliveReloadStartedAtMs = 0L,
      )
    )
  }
}
