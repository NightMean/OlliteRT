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

internal enum class CancellationRequestStatus {
  PENDING,
  ACCEPTED,
  REJECTED,
}

/**
 * Bridges a UI cancellation request with a control handle that is published just before
 * executor submission. Synchronization closes the small attach/request race without
 * exposing the native model globally or allowing a late cancel to rewrite a success.
 */
internal class RequestCancellationBridge {
  private var wasRequested = false
  private var wasAccepted = false
  private var control: InferenceGateway.InferenceControl? = null

  @Synchronized
  fun request(): CancellationRequestStatus {
    wasRequested = true
    val currentControl = control ?: return CancellationRequestStatus.PENDING
    return if (accept(currentControl)) {
      CancellationRequestStatus.ACCEPTED
    } else {
      CancellationRequestStatus.REJECTED
    }
  }

  @Synchronized
  fun attach(newControl: InferenceGateway.InferenceControl) {
    check(control == null) { "Inference control already attached" }
    control = newControl
    if (wasRequested) accept(newControl)
  }

  @Synchronized
  fun cancellationWasAccepted(): Boolean = wasAccepted

  private fun accept(currentControl: InferenceGateway.InferenceControl): Boolean {
    if (wasAccepted) return true
    wasAccepted = currentControl.cancel(InferenceGateway.CancellationReason.EXTERNAL)
    return wasAccepted
  }
}
