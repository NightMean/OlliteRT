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

import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.common.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ServerStateRepositoryTest {

  private val repository: ServerStateRepository = DefaultServerStateRepository()

  @Before
  fun setUp() {
    ServerMetrics.resetForTesting()
  }

  @Test
  fun statusReflectsServerMetrics() {
    assertEquals(ServerStatus.STOPPED, repository.status.value)
    ServerMetrics.onServerRunning("192.168.1.50")
    assertEquals(ServerStatus.RUNNING, repository.status.value)
  }

  @Test
  fun activeModelNameReflectsServerMetrics() {
    assertEquals(null, repository.activeModelName.value)
    ServerMetrics.onServerStarting(8000, "gemma-2b")
    assertEquals("gemma-2b", repository.activeModelName.value)
  }

  @Test
  fun requestCountReflectsServerMetrics() {
    assertEquals(0L, repository.requestCount.value)
    ServerMetrics.incrementRequestCount()
    ServerMetrics.addTokens(15L)
    ServerMetrics.addTokensIn(5L)
    assertEquals(1L, repository.requestCount.value)
    assertEquals(15L, repository.tokensGenerated.value)
    assertEquals(5L, repository.tokensIn.value)
  }

  @Test
  fun setAvailableUpdateExposedThroughRepository() {
    assertEquals(null, repository.availableUpdateVersion.value)
    assertEquals(null, repository.availableUpdateUrl.value)
    repository.setAvailableUpdate("v2.0.0", "https://example.com/v2.0.0")
    assertEquals("v2.0.0", repository.availableUpdateVersion.value)
    assertEquals("https://example.com/v2.0.0", repository.availableUpdateUrl.value)
    repository.setAvailableUpdate(null, null)
    assertEquals(null, repository.availableUpdateVersion.value)
    assertEquals(null, repository.availableUpdateUrl.value)
  }

  @Test
  fun clearErrorIfModelDelegatesToServerMetrics() {
    // Put metrics into error state for a named model, then clear it via the repository
    ServerMetrics.onServerStarting(8000, "gemma-2b")
    ServerMetrics.onServerError("OOM")
    assertEquals(ServerStatus.ERROR, repository.status.value)
    repository.clearErrorIfModel("gemma-2b")
    assertEquals(ServerStatus.STOPPED, repository.status.value)
    assertEquals(null, repository.lastError.value)
  }
}
