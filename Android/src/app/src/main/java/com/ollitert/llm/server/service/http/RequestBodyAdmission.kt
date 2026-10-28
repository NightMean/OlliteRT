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

package com.ollitert.llm.server.service.http

import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

internal const val MAX_IN_FLIGHT_INFERENCE_REQUESTS = 2
internal const val MAX_IN_FLIGHT_CONTROL_REQUESTS = 1
internal const val REQUEST_BODY_READ_TIMEOUT_MS = 120_000L

/** Bounds request bodies held during parsing, queueing, inference and streaming. */
internal class RequestBodyAdmission(maxRequests: Int = MAX_IN_FLIGHT_INFERENCE_REQUESTS) {
  private val permits = Semaphore(maxRequests)

  fun tryAcquire(): AutoCloseable? {
    if (!permits.tryAcquire()) return null
    val released = AtomicBoolean(false)
    return AutoCloseable {
      if (released.compareAndSet(false, true)) permits.release()
    }
  }
}
