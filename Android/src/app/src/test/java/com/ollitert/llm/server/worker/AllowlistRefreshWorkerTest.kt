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

package com.ollitert.llm.server.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowlistRefreshWorkerTest {

  @Test
  fun modelUpdateNotificationIdIsDeterministic() {
    val id1 = AllowlistRefreshWorker.modelUpdateNotificationId("Gemma-4-E2B-it")
    val id2 = AllowlistRefreshWorker.modelUpdateNotificationId("Gemma-4-E2B-it")
    assertEquals(id1, id2)
  }

  @Test
  fun modelUpdateNotificationIdDiffersForDifferentModels() {
    val id1 = AllowlistRefreshWorker.modelUpdateNotificationId("Gemma-4-E2B-it")
    val id2 = AllowlistRefreshWorker.modelUpdateNotificationId("Gemma-4-1B-it")
    assertNotEquals(id1, id2)
  }

  @Test
  fun modelUpdateNotificationIdStartsFromBaseId() {
    val id = AllowlistRefreshWorker.modelUpdateNotificationId("any-model")
    assertTrue("ID should be >= 1000 (base ID)", id >= 1000)
  }

  @Test
  fun modelUpdateNotificationIdIsPositive() {
    val id = AllowlistRefreshWorker.modelUpdateNotificationId("any-model")
    assertTrue("ID should be positive", id > 0)
    val negativeHash = AllowlistRefreshWorker.modelUpdateNotificationId("a".repeat(100))
    assertTrue("ID should be positive even with large hash", negativeHash > 0)
  }
}
