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

package com.ollitert.llm.server.ui.floatingmonitor

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviousSuccessfulLatencyLatchTest {

  @Test
  fun `new processing sequence snapshots live latency once`() {
    val latch = PreviousSuccessfulLatencyLatch()

    assertEquals(0L, latch.valueFor(isProcessing = false, inferenceSequence = 0L, liveLatencyMs = 842L))
    assertEquals(842L, latch.valueFor(isProcessing = true, inferenceSequence = 1L, liveLatencyMs = 842L))
    assertEquals(842L, latch.valueFor(isProcessing = true, inferenceSequence = 1L, liveLatencyMs = 1_244L))
  }

  @Test
  fun `next processing sequence snapshots latest successful latency`() {
    val latch = PreviousSuccessfulLatencyLatch()

    assertEquals(842L, latch.valueFor(isProcessing = true, inferenceSequence = 1L, liveLatencyMs = 842L))
    assertEquals(842L, latch.valueFor(isProcessing = false, inferenceSequence = 1L, liveLatencyMs = 1_244L))
    assertEquals(1_244L, latch.valueFor(isProcessing = true, inferenceSequence = 2L, liveLatencyMs = 1_244L))
  }
}
