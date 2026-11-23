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

package com.ollitert.llm.server.ui.modelmanager

import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DownloadRefreshTest {
  @Test fun refreshRetainsOnlyActiveStatusesForModelsStillInCatalog() {
    val partial = ModelDownloadStatus(ModelDownloadStatusType.PARTIALLY_DOWNLOADED)
    val running = ModelDownloadStatus(ModelDownloadStatusType.IN_PROGRESS, receivedBytes = 123)
    val unzipping = ModelDownloadStatus(ModelDownloadStatusType.UNZIPPING)
    val finished = ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)
    val loaded = mapOf("running" to partial, "unzipping" to partial, "finished" to partial)
    val old = mapOf("running" to running, "unzipping" to unzipping, "finished" to finished, "removed" to running)

    val result = preserveActiveDownloadStatuses(loaded, old, emptyMap(), emptyMap(), setOf("running", "unzipping"))

    assertEquals(running, result["running"])
    assertEquals(unzipping, result["unzipping"])
    assertEquals(partial, result["finished"])
    assertFalse(result.containsKey("removed"))
  }

  @Test fun refreshClearsUnobservedProgressOnlyAfterSuccessfulWorkInspection() {
    val active = mapOf(
      "failed-to-enqueue" to ModelDownloadStatus(ModelDownloadStatusType.IN_PROGRESS),
      "finished-while-unobserved" to ModelDownloadStatus(ModelDownloadStatusType.UNZIPPING),
    )
    val disk = mapOf(
      "failed-to-enqueue" to ModelDownloadStatus(ModelDownloadStatusType.NOT_DOWNLOADED),
      "finished-while-unobserved" to ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED),
    )

    assertEquals(active, preserveActiveDownloadStatuses(disk, active, emptyMap(), emptyMap(), null))
    assertEquals(disk, preserveActiveDownloadStatuses(disk, active, emptyMap(), emptyMap(), emptySet()))
    assertEquals(active, preserveActiveDownloadStatuses(disk, active, emptyMap(), emptyMap(), active.keys))
    val progressSinceRefreshStarted = active.keys.associateWith { 2L }
    assertEquals(disk, preserveActiveDownloadStatuses(disk, active, emptyMap(), progressSinceRefreshStarted, emptySet()))
  }

  @Test fun refreshKeepsTerminalStatusPublishedAfterDiskReadStarted() {
    val partial = ModelDownloadStatus(ModelDownloadStatusType.PARTIALLY_DOWNLOADED)
    val finished = ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)
    assertEquals(finished, preserveActiveDownloadStatuses(mapOf("m" to partial), mapOf("m" to finished), mapOf("m" to 1L), mapOf("m" to 2L), emptySet())["m"])
  }

  @Test fun refreshKeepsCancellationEvenWhenStatusReturnsToOriginalValue() {
    val partial = ModelDownloadStatus(ModelDownloadStatusType.PARTIALLY_DOWNLOADED)
    val cancelled = ModelDownloadStatus(ModelDownloadStatusType.NOT_DOWNLOADED)
    assertEquals(cancelled, preserveActiveDownloadStatuses(mapOf("m" to partial), mapOf("m" to cancelled), mapOf("m" to 1L), mapOf("m" to 3L), emptySet())["m"])
  }

  @Test fun refreshPreservesModelScopeAccessFailureUntilAnotherStatusReplacesIt() {
    val failed = ModelDownloadStatus(ModelDownloadStatusType.FAILED, modelScopeAccessDeniedSite = "CHINA", failureId = "one")
    val disk = ModelDownloadStatus(ModelDownloadStatusType.NOT_DOWNLOADED)
    assertEquals(failed, preserveActiveDownloadStatuses(mapOf("m" to disk), mapOf("m" to failed), emptyMap(), emptyMap(), emptySet())["m"])
    val downloaded = ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)
    assertEquals(downloaded, preserveActiveDownloadStatuses(mapOf("m" to downloaded), mapOf("m" to failed), emptyMap(), emptyMap(), emptySet())["m"])
  }
}
