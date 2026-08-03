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
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessingElapsedTrackerTest {

  @Test
  fun `processing transition starts once and clears when inference ends`() {
    var now = 1_000L
    val tracker = ProcessingElapsedTracker { now }

    assertNull(tracker.elapsedMillis())

    tracker.update(isInferring = true, inferenceSequence = 1)
    now = 2_500L
    assertEquals(1_500L, tracker.elapsedMillis())

    tracker.update(isInferring = true, inferenceSequence = 1)
    now = 4_000L
    assertEquals(3_000L, tracker.elapsedMillis())

    tracker.update(isInferring = false, inferenceSequence = 1)
    assertNull(tracker.elapsedMillis())
  }

  @Test
  fun `new processing transition uses a new start and dispose clears it`() {
    var now = 5_000L
    val tracker = ProcessingElapsedTracker { now }

    tracker.update(isInferring = true, inferenceSequence = 1)
    now = 6_250L
    assertEquals(1_250L, tracker.elapsedMillis())

    tracker.update(isInferring = false, inferenceSequence = 1)
    now = 10_000L
    tracker.update(isInferring = true, inferenceSequence = 2)
    now = 10_750L
    assertEquals(750L, tracker.elapsedMillis())

    tracker.dispose()
    assertNull(tracker.elapsedMillis())
  }

  @Test
  fun `new request sequence resets elapsed even when false transition is conflated`() {
    var now = 20_000L
    val tracker = ProcessingElapsedTracker { now }

    tracker.update(isInferring = true, inferenceSequence = 10)
    now = 24_000L
    assertEquals(4_000L, tracker.elapsedMillis())

    tracker.update(isInferring = true, inferenceSequence = 11)
    assertEquals(0L, tracker.elapsedMillis())
    now = 24_750L
    assertEquals(750L, tracker.elapsedMillis())
  }
}
