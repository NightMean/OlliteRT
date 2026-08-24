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

package com.ollitert.llm.server.data.repository

import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.RequestLogEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RequestLogStoreRepositoryTest {

  private val repository: RequestLogStoreRepository = DefaultRequestLogStoreRepository()

  @Before
  fun setUp() {
    RequestLogStore.resetForTesting()
  }

  @After
  fun tearDown() {
    RequestLogStore.resetForTesting()
  }

  @Test
  fun repositoryExposesEntriesAndReflectsAddAndClear() {
    assertEquals(0, repository.entries.value.size)

    val entry = RequestLogEntry(id = "test-1", method = "POST", path = "/v1/chat/completions")
    repository.add(entry)
    assertEquals(1, repository.entries.value.size)
    assertEquals("test-1", repository.entries.value[0].id)

    repository.clear()
    assertTrue(repository.entries.value.isEmpty())
  }

  @Test
  fun repositoryAddEventCreatesFormattedEventEntry() {
    repository.addEvent(
      message = "Server started",
      level = LogLevel.INFO,
      category = EventCategory.SERVER,
    )
    assertEquals(1, repository.entries.value.size)
    val event = repository.entries.value[0]
    assertEquals("EVENT", event.method)
    assertEquals("Server started", event.path)
    assertEquals(LogLevel.INFO, event.level)
    assertEquals(EventCategory.SERVER, event.eventCategory)
  }

  @Test
  fun repositoryCancelRequestUpdatesPendingEntry() {
    val entry = RequestLogEntry(id = "pending-1", method = "POST", path = "/v1/chat/completions", isPending = true)
    repository.add(entry)
    var cancelled = false
    repository.registerCancellation("pending-1") {
      cancelled = true
      true
    }

    repository.cancelRequest("pending-1")
    assertTrue(cancelled)
    val updated = repository.entries.value.find { it.id == "pending-1" }
    assertTrue(updated?.cancelledByUser == true)
  }
}
