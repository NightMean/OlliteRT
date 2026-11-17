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
import org.junit.Assert.assertNull
import org.junit.Test

class ModelScopeAccessPromptTest {
  @Test fun failuresArePresentedOnceInOrderAndDoNotDependOnVisibleCards() {
    val china = ModelDownloadStatus(ModelDownloadStatusType.FAILED, modelScopeAccessDeniedSite = "CHINA", failureId = "first")
    val international = china.copy(modelScopeAccessDeniedSite = "INTERNATIONAL", failureId = "second")
    val irrelevant = china.copy(modelScopeAccessDeniedSite = "unknown", failureId = "third")
    val statuses = listOf(irrelevant, china, international)
    assertEquals(china, nextModelScopeAccessFailure(statuses, emptyList()))
    assertEquals(international, nextModelScopeAccessFailure(statuses, listOf("first")))
    assertNull(nextModelScopeAccessFailure(statuses, listOf("first", "second")))
    assertNull(nextModelScopeAccessFailure(listOf(china.copy(failureId = "")), emptyList()))
    assertNull(nextModelScopeAccessFailure(listOf(china.copy(status = ModelDownloadStatusType.IN_PROGRESS)), emptyList()))
  }
}
