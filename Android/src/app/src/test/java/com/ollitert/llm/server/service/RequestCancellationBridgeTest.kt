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

package com.ollitert.llm.server.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestCancellationBridgeTest {

  @Test
  fun requestBeforeAttachIsAppliedWhenControlArrives() {
    val bridge = RequestCancellationBridge()
    val control = FakeInferenceControl(acceptCancellation = true)

    assertEquals(CancellationRequestStatus.PENDING, bridge.request())
    bridge.attach(control)

    assertTrue(bridge.cancellationWasAccepted())
    assertEquals(1, control.cancelCount)
  }

  @Test
  fun lateRequestCannotRewriteCompletedInferenceAsCancelled() {
    val bridge = RequestCancellationBridge()
    val control = FakeInferenceControl(acceptCancellation = false)
    bridge.attach(control)

    assertEquals(CancellationRequestStatus.REJECTED, bridge.request())

    assertFalse(bridge.cancellationWasAccepted())
    assertEquals(1, control.cancelCount)
  }

  @Test
  fun repeatedRequestAfterAcceptanceDoesNotSignalControlTwice() {
    val bridge = RequestCancellationBridge()
    val control = FakeInferenceControl(acceptCancellation = true)
    bridge.attach(control)

    assertEquals(CancellationRequestStatus.ACCEPTED, bridge.request())
    assertEquals(CancellationRequestStatus.ACCEPTED, bridge.request())

    assertTrue(bridge.cancellationWasAccepted())
    assertEquals(1, control.cancelCount)
  }

  private class FakeInferenceControl(
    private val acceptCancellation: Boolean,
  ) : InferenceGateway.InferenceControl {
    var cancelCount = 0

    override fun cancel(reason: InferenceGateway.CancellationReason): Boolean {
      cancelCount++
      return acceptCancellation
    }

    override fun stopSuccessfully(): Boolean = false
  }
}
