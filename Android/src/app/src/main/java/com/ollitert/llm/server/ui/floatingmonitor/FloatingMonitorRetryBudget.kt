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

internal class FloatingMonitorRetryBudget(
  private val maxConsecutiveFailures: Int,
) {
  private var consecutiveFailures = 0

  init {
    require(maxConsecutiveFailures > 0)
  }

  fun record(success: Boolean): Boolean {
    if (success) {
      consecutiveFailures = 0
      return true
    }

    consecutiveFailures += 1
    return consecutiveFailures < maxConsecutiveFailures
  }
}

internal fun shouldContinueFloatingMonitorReconciliation(
  modelVisible: Boolean,
  reconciled: Boolean,
  retryAllowed: Boolean,
): Boolean = retryAllowed && (modelVisible || !reconciled)
