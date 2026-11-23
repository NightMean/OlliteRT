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

import androidx.work.WorkInfo
import androidx.work.workDataOf
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.storage.KEY_MODELSCOPE_ACCESS_DENIED_SITE
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class UnfinishedModelWorkersTest {
  @Test fun queuedDownloadIsFoundWithoutAFileAndOtherWorkIsIgnored() {
    val workerId = UUID.randomUUID()
    val queued = mockk<WorkInfo> {
      every { state } returns WorkInfo.State.ENQUEUED
      every { tags } returns setOf("modelName:example")
      every { id } returns workerId
    }
    val other = mockk<WorkInfo> {
      every { state } returns WorkInfo.State.RUNNING
      every { tags } returns setOf("unrelated")
    }
    val finished = mockk<WorkInfo> {
      every { state } returns WorkInfo.State.SUCCEEDED
      every { tags } returns setOf("modelName:finished")
    }
    assertEquals(ModelDownloadWork(activeWorkers = mapOf("example" to workerId)),
      modelDownloadWork(listOf(queued, other, finished)))
  }

  @Test fun accessDeniedRestoresOnlyForModelsWithoutActiveWork() {
    val failure = mockk<WorkInfo> {
      every { state } returns WorkInfo.State.FAILED
      every { tags } returns setOf("modelName:example")
      every { id } returns UUID.fromString("db556960-2e52-43fd-9d2b-d5b80e6c73a4")
      every { outputData } returns workDataOf(KEY_MODELSCOPE_ACCESS_DENIED_SITE to ModelScopeSite.CHINA.name)
    }
    val queued = mockk<WorkInfo> {
      every { state } returns WorkInfo.State.ENQUEUED
      every { tags } returns failure.tags
      every { id } returns UUID.randomUUID()
    }
    val recovered = modelDownloadWork(listOf(failure)).accessDenied.getValue("example")
    assertEquals(failure.id.toString(), recovered.failureId)
    assertEquals(ModelScopeSite.CHINA.name, recovered.modelScopeAccessDeniedSite)
    assertEquals(emptyMap<String, Any>(), modelDownloadWork(listOf(queued, failure)).accessDenied)
  }

  @Test fun canceledAttemptIsKeptForPartialFileReconciliation() {
    val stopped = mockk<WorkInfo> {
      every { state } returns WorkInfo.State.CANCELLED
      every { tags } returns setOf("modelName:example")
    }
    assertEquals(setOf("example"), modelDownloadWork(listOf(stopped)).canceledModels)
  }
}
