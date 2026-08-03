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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingMonitorRetryBudgetTest {

  @Test
  fun `third consecutive failure exhausts budget while success resets it`() {
    val budget = FloatingMonitorRetryBudget(maxConsecutiveFailures = 3)

    assertTrue(budget.record(success = false))
    assertTrue(budget.record(success = true))
    assertTrue(budget.record(success = false))
    assertTrue(budget.record(success = false))
    assertFalse(budget.record(success = false))
  }

  @Test
  fun `hidden detach failure retries only while budget remains`() {
    val budget = FloatingMonitorRetryBudget(maxConsecutiveFailures = 3)

    assertTrue(
      shouldContinueFloatingMonitorReconciliation(
        modelVisible = false,
        reconciled = false,
        retryAllowed = budget.record(success = false),
      )
    )
    assertTrue(
      shouldContinueFloatingMonitorReconciliation(
        modelVisible = false,
        reconciled = false,
        retryAllowed = budget.record(success = false),
      )
    )
    assertFalse(
      shouldContinueFloatingMonitorReconciliation(
        modelVisible = false,
        reconciled = false,
        retryAllowed = budget.record(success = false),
      )
    )
  }
}
