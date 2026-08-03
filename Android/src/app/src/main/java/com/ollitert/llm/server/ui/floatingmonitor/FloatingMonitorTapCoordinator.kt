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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal class FloatingMonitorTapCoordinator(
  scope: CoroutineScope,
  timeoutMillis: Long,
  private val detach: () -> Unit,
  private val launch: () -> Boolean,
  reconcile: () -> Unit,
) {
  private val suppression = FloatingMonitorTapSuppression(
    scope = scope,
    timeoutMillis = timeoutMillis,
    onReleased = reconcile,
  )

  val suppressionActive: StateFlow<Boolean> = suppression.active

  fun handleTap() {
    suppression.suppress()
    detach()
    if (!launch()) suppression.clear()
  }

  fun onAppForegrounded() {
    suppression.clear()
  }

  fun dispose() {
    suppression.dispose()
  }
}
