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

package com.ollitert.llm.server.data.allowlist

import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ModelScopeRetryTest {
  @Test fun transientRateLimitSucceedsOnFourthAttempt() = runTest {
    var attempts = 0
    val result = async { retryModelScopeRateLimit { if (++attempts < 4) throw ModelScopeRateLimitException() else "ok" } }
    advanceUntilIdle()
    assertEquals("ok", result.await())
    assertEquals(4, attempts)
    assertEquals(7_000L, currentTime)
  }

  @Test fun respectsRetryAfterAndDoesNotWaitPastAutomaticBudget() = runTest {
    var attempts = 0
    val result = async { retryModelScopeRateLimit { if (++attempts == 1) throw ModelScopeRateLimitException(3_000) else "ok" } }
    advanceUntilIdle()
    assertEquals("ok", result.await())
    assertEquals(3_000L, currentTime)
    val longLimit = ModelScopeRateLimitException(9_000)
    var longAttempts = 0
    val failure = runCatching { retryModelScopeRateLimit<Unit> { longAttempts++; throw longLimit } }.exceptionOrNull()
    assertSame(longLimit, failure)
    assertEquals(1, longAttempts)
  }

  @Test fun cancellationStopsPendingRetryAndOtherErrorsAreNotRetried() = runTest {
    var attempts = 0
    val job = async { retryModelScopeRateLimit<Unit> { attempts++; throw ModelScopeRateLimitException() } }
    runCurrent()
    job.cancelAndJoin()
    advanceUntilIdle()
    assertEquals(1, attempts)
    val error = IOException("denied")
    val failure = runCatching { retryModelScopeRateLimit<Unit> { attempts++; throw error } }.exceptionOrNull()
    assertSame(error, failure)
    assertEquals(2, attempts)
  }

  @Test fun exhaustedRetriesPreserveTheLastProviderError() = runTest {
    val error = ModelScopeRateLimitException()
    var attempts = 0
    val failure = runCatching { retryModelScopeRateLimit<Unit> { attempts++; throw error } }.exceptionOrNull()
    assertSame(error, failure)
    assertEquals(4, attempts)
  }
}
