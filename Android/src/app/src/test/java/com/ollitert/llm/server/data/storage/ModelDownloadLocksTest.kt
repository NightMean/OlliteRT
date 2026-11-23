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

package com.ollitert.llm.server.data.storage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch

class ModelDownloadLocksTest {
  @Test fun removalInspectionRejectsBusyModelWithoutWaiting() = runTest {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val worker = launch {
      ModelDownloadLocks.withModel("active model") {
        entered.complete(Unit)
        release.await()
      }
    }
    entered.await()
    try {
      val inspection = withTimeout(100) {
        ModelDownloadLocks.tryWithModel("active model") { error("Busy inspection ran") }
      }
      assertNull(inspection)
      assertTrue(ModelDownloadLocks.tryWithModel("another model") { true } == true)
    } finally {
      release.complete(Unit)
      worker.join()
    }
    assertTrue(ModelDownloadLocks.tryWithModel("active model") { true } == true)
  }

  @Test fun onlyIdleDownloadStatusesPermitRemovalInspection() {
    assertFalse(canInspectDiscoveredRemoval(null))
    assertFalse(canInspectDiscoveredRemoval(ModelDownloadStatusType.IN_PROGRESS))
    assertFalse(canInspectDiscoveredRemoval(ModelDownloadStatusType.UNZIPPING))
    assertTrue(canInspectDiscoveredRemoval(ModelDownloadStatusType.NOT_DOWNLOADED))
    assertTrue(canInspectDiscoveredRemoval(ModelDownloadStatusType.PARTIALLY_DOWNLOADED))
    assertTrue(canInspectDiscoveredRemoval(ModelDownloadStatusType.SUCCEEDED))
    assertTrue(canInspectDiscoveredRemoval(ModelDownloadStatusType.FAILED))
  }

  @Test fun removalWaitsForWorkerToLeaveModelLock() = runBlocking {
    val workerEntered = CompletableDeferred<Unit>()
    val workerMayFinish = CompletableDeferred<Unit>()
    val worker = async {
      ModelDownloadLocks.withModel("example") {
        workerEntered.complete(Unit)
        workerMayFinish.await()
      }
    }
    workerEntered.await()
    val removal = async { ModelDownloadLocks.withModel("example") { true } }
    yield()
    assertFalse(removal.isCompleted)
    workerMayFinish.complete(Unit)
    worker.await()
    assertTrue(removal.await())
  }

  @Test fun cancellingWaitingOperationKeepsLockUsable() = runBlocking {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val worker = launch {
      ModelDownloadLocks.withModel("example") {
        entered.complete(Unit)
        release.await()
      }
    }
    entered.await()
    val cancelled = launch { ModelDownloadLocks.withModel("example") { error("Cancelled waiter ran") } }
    yield()
    cancelled.cancelAndJoin()
    val next = async { ModelDownloadLocks.withModel("example") { true } }
    release.complete(Unit)
    worker.join()
    assertTrue(next.await())
  }

  @Test fun removalSeesModelUsePublishedByStartupUnderTheSameLock() = runBlocking {
    val published = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    var active: String? = null
    var status = ServerStatus.STOPPED
    val startup = launch {
      ModelDownloadLocks.withModel("example") {
        active = "example"
        status = ServerStatus.LOADING
        published.complete(Unit)
        release.await()
      }
    }
    published.await()
    val removal = async {
      ModelDownloadLocks.withModel("example") {
        canRemoveModelFiles("example", ModelDownloadStatusType.SUCCEEDED, active, status, false, false, ModelDownloadStatusType.SUCCEEDED)
      }
    }
    yield()
    assertFalse(removal.isCompleted)
    release.complete(Unit)
    startup.join()
    assertFalse(removal.await())
  }

  @Test fun cleanupLatchReportsPendingUntilNativeCleanupCompletes() {
    val previous = ModelDownloadLocks.cleanupLatch.get()
    val latch = CountDownLatch(1)
    try {
      ModelDownloadLocks.cleanupLatch.set(latch)
      assertTrue(ModelDownloadLocks.isCleanupPending())
      latch.countDown()
      assertFalse(ModelDownloadLocks.isCleanupPending())
    } finally {
      ModelDownloadLocks.cleanupLatch.set(previous)
    }
  }
}
