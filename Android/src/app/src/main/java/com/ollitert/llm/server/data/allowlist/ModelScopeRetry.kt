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

import kotlinx.coroutines.delay

private val RETRY_DELAYS_MILLIS = longArrayOf(1_000, 2_000, 4_000)
private const val MAX_AUTOMATIC_RETRY_WAIT_MILLIS = 8_000L

/** Retries only confirmed throttling; long provider cooldowns are left to the user. */
internal suspend fun <T> retryModelScopeRateLimit(operation: () -> T): T {
  for (backoff in RETRY_DELAYS_MILLIS) {
    try {
      return operation()
    } catch (limited: ModelScopeRateLimitException) {
      val wait = maxOf(backoff, limited.retryAfterMillis ?: 0)
      // Never retry before the provider's requested cooldown or wait indefinitely in a modal flow.
      if (wait > MAX_AUTOMATIC_RETRY_WAIT_MILLIS) throw limited
      delay(wait)
    }
  }
  return operation()
}
