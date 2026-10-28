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

import com.ollitert.llm.server.service.http.RequestBodyAdmission
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RequestBodyAdmissionTest {
  @Test
  fun concurrentAttemptsNeverExceedTheLimit() {
    val admission = RequestBodyAdmission(2)
    val pool = Executors.newFixedThreadPool(8)
    val start = CountDownLatch(1)
    val attempted = CountDownLatch(8)
    val release = CountDownLatch(1)
    try {
      val results = (1..8).map {
        pool.submit<Boolean> {
          start.await()
          val lease = admission.tryAcquire()
          attempted.countDown()
          try {
            release.await()
            lease != null
          } finally {
            lease?.close()
          }
        }
      }
      start.countDown()
      check(attempted.await(5, TimeUnit.SECONDS))
      release.countDown()
      org.junit.Assert.assertEquals(2, results.count { it.get(5, TimeUnit.SECONDS) })
      assertNotNull(admission.tryAcquire())
    } finally {
      release.countDown()
      pool.shutdownNow()
    }
  }

  @Test
  fun rejectsExcessAndRecoversAfterRelease() {
    val admission = RequestBodyAdmission(2)
    val first = admission.tryAcquire()!!
    val second = admission.tryAcquire()!!
    assertNull(admission.tryAcquire())
    first.close()
    val replacement = admission.tryAcquire()
    assertNotNull(replacement)
    first.close() // Closing an old lease twice must not admit extra requests.
    assertNull(admission.tryAcquire())
    replacement!!.close()
    second.close()
    assertNotNull(admission.tryAcquire())
  }
}
