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

import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.storage.ModelDownloadLocks
import com.ollitert.llm.server.data.storage.canRemoveDiscoveredModel
import com.ollitert.llm.server.data.prefs.DEFAULT_PORT
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ServerModelStartGateTest {
  @get:Rule val temp = TemporaryFolder()
  @Before fun setup() = ServerMetrics.resetForTesting()
  @After fun teardown() = ServerMetrics.resetForTesting()

  @Test fun actualLifecyclePublicationPreventsRemovalAfterStart() = runBlocking {
    val modelFile = temp.newFile()
    assertTrue(reserveModelForServer("example", modelFile, DEFAULT_PORT))
    val allowed = ModelDownloadLocks.withModel("example") {
      canRemoveDiscoveredModel("example", ModelDownloadStatusType.SUCCEEDED, ServerMetrics.activeModelName.value,
        ServerMetrics.status.value, ModelDownloadLocks.isCleanupPending(), false, ModelDownloadStatusType.SUCCEEDED)
    }
    assertEquals(ServerStatus.LOADING, ServerMetrics.status.value)
    assertFalse(allowed)
  }

  @Test fun removalBeforeStartCannotPublishUseOfMissingFile() = runBlocking {
    val modelFile = temp.newFile()
    ModelDownloadLocks.withModel("example") { assertTrue(modelFile.delete()) }
    assertFalse(reserveModelForServer("example", modelFile, DEFAULT_PORT))
    assertEquals(ServerStatus.STOPPED, ServerMetrics.status.value)
  }

  @Test fun stoppedStatusDoesNotPermitRemovalWhileNativeCleanupIsPending() {
    val previous = ServerCleanupCoordinator.cleanupLatch.getAndSet(null)
    val release = CountDownLatch(1)
    try {
      val modelFile = temp.newFile()
      runBlocking { assertTrue(reserveModelForServer("example", modelFile, DEFAULT_PORT)) }
      ServerCleanupCoordinator.enqueueCleanup("test-cleanup") { release.await(5, TimeUnit.SECONDS) }
      val cleanup = ServerCleanupCoordinator.cleanupLatch.get()!!
      ServerMetrics.onServerStopped()
      assertFalse(canRemoveDiscoveredModel("example", ModelDownloadStatusType.SUCCEEDED, ServerMetrics.activeModelName.value,
        ServerMetrics.status.value, ModelDownloadLocks.isCleanupPending(), false, ModelDownloadStatusType.SUCCEEDED))
      release.countDown()
      assertTrue(cleanup.await(5, TimeUnit.SECONDS))
      assertTrue(canRemoveDiscoveredModel("example", ModelDownloadStatusType.SUCCEEDED, ServerMetrics.activeModelName.value,
        ServerMetrics.status.value, ModelDownloadLocks.isCleanupPending(), false, ModelDownloadStatusType.SUCCEEDED))
    } finally {
      release.countDown()
      ServerCleanupCoordinator.cleanupLatch.set(previous)
    }
  }
}
