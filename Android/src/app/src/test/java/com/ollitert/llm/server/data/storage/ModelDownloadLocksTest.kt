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
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloadLocksTest {
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
}
