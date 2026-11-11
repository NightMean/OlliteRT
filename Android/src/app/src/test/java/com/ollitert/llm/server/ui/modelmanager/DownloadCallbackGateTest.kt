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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCallbackGateTest {
  @Test fun oldCancelledWorkerCannotOverwriteResumedWorker() {
    val gate = DownloadCallbackGate()
    val oldWorker = gate.next("model")
    gate.next("model") // cancellation invalidates the previous observer
    val resumedWorker = gate.next("model")
    assertFalse(gate.isCurrent("model", oldWorker))
    assertTrue(gate.isCurrent("model", resumedWorker))
    assertTrue(gate.isCurrent("other", gate.next("other")))
  }
}
