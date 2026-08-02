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

package com.ollitert.llm.server.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelLoadDiagnosticStateTest {

  @Test
  fun gpuSamplerWarningCanOnlyBeClaimedOncePerLoad() {
    val diagnostics = ModelLoadDiagnosticState()

    assertTrue(diagnostics.claimGpuSamplerWarning())
    assertFalse(diagnostics.claimGpuSamplerWarning())
  }

  @Test
  fun newLoadGetsFreshGpuSamplerWarningClaim() {
    val firstLoad = ModelLoadDiagnosticState()
    val secondLoad = ModelLoadDiagnosticState()

    assertTrue(firstLoad.claimGpuSamplerWarning())
    assertFalse(firstLoad.claimGpuSamplerWarning())
    assertTrue(secondLoad.claimGpuSamplerWarning())
  }

  @Test
  fun concurrentRequestsProduceOneGpuSamplerWarningClaim() {
    val diagnostics = ModelLoadDiagnosticState()
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(8)

    try {
      val claims = (1..32).map {
        executor.submit<Boolean> {
          start.await()
          diagnostics.claimGpuSamplerWarning()
        }
      }
      start.countDown()

      assertEquals(1, claims.count { it.get() })
    } finally {
      executor.shutdownNow()
    }
  }
}
