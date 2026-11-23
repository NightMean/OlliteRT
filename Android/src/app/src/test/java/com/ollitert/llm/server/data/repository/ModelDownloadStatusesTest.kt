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

import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE
import com.ollitert.llm.server.data.storage.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES
import com.ollitert.llm.server.data.storage.KEY_MODEL_START_UNZIPPING
import com.ollitert.llm.server.data.storage.KEY_MODELSCOPE_ACCESS_DENIED_SITE
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadStatusesTest {
  private val workerId = UUID.randomUUID()

  private fun info(workState: WorkInfo.State, workProgress: Data = Data.EMPTY, output: Data = Data.EMPTY): WorkInfo = mockk {
    every { state } returns workState
    every { progress } returns workProgress
    every { outputData } returns output
    every { id } returns workerId
  }

  @Test fun pendingWorkEmitsProgressAndOneFailureThenReleasesItsSubscription() = runTest {
    val message = "Provider denied access"
    val site = ModelScopeSite.CHINA.name
    val changes = flow {
      emit(info(WorkInfo.State.ENQUEUED))
      emit(info(WorkInfo.State.RUNNING, workDataOf(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES to 25L)))
      emit(info(WorkInfo.State.RUNNING, workDataOf(KEY_MODEL_START_UNZIPPING to true)))
      emit(info(WorkInfo.State.FAILED, output = workDataOf(
        KEY_MODEL_DOWNLOAD_ERROR_MESSAGE to message, KEY_MODELSCOPE_ACCESS_DENIED_SITE to site,
      )))
      error("A completed worker must release its subscriber")
    }
    val statuses = modelDownloadStatuses(changes, totalBytes = 100L).toList()
    assertEquals(
      listOf(ModelDownloadStatusType.IN_PROGRESS, ModelDownloadStatusType.UNZIPPING, ModelDownloadStatusType.FAILED),
      statuses.map { it.status },
    )
    assertEquals(25L, statuses.last().receivedBytes)
    assertEquals(100L, statuses.last().totalBytes)
    assertEquals(message, statuses.last().errorMessage)
    assertEquals(site, statuses.last().modelScopeAccessDeniedSite)
    assertEquals(workerId.toString(), statuses.last().failureId)
  }

  @Test fun anAlreadyTerminalWorkerStillEmitsItsResult() = runTest {
    val cancelled = modelDownloadStatuses(flowOf(info(WorkInfo.State.CANCELLED)), 100L).toList().single()
    assertEquals(ModelDownloadStatusType.NOT_DOWNLOADED, cancelled.status)
    assertEquals("", cancelled.failureId)
    val finished = modelDownloadStatuses(flowOf(info(WorkInfo.State.SUCCEEDED)), 100L).toList().single()
    assertEquals(ModelDownloadStatusType.SUCCEEDED, finished.status)
  }

  @Test fun cancellingTheObserverUnsubscribesBeforeTheWorkerFinishes() = runTest {
    var released = false
    var subscribed = false
    val work = flow<WorkInfo?> {
      try {
        subscribed = true
        emit(info(WorkInfo.State.RUNNING))
        awaitCancellation()
      } finally {
        released = true
      }
    }
    val observer = launch { modelDownloadStatuses(work, 100L).collect { } }
    runCurrent()
    assertTrue(subscribed)
    observer.cancelAndJoin()
    assertTrue(released)
  }

  @Test fun aMissingWorkRecordEndsObservationAndReportsFailure() = runTest {
    val work = flow {
      emit(info(WorkInfo.State.RUNNING))
      emit(null)
      error("A missing record must end observation")
    }
    try {
      modelDownloadStatuses(work, 100L).toList()
      fail("Expected an unavailable work record")
    } catch (e: IllegalStateException) {
      assertEquals("The download record is no longer available", e.message)
    }
  }
}
