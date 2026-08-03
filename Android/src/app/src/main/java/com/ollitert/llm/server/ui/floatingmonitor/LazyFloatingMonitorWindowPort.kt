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

/** Defers Android WindowManager/View allocation until the first visible attach. */
internal class LazyFloatingMonitorWindowPort(
  private val factory: () -> FloatingMonitorWindowPort,
) : FloatingMonitorWindowPort {
  private var delegate: FloatingMonitorWindowPort? = null

  override val isAttached: Boolean
    get() = delegate?.isAttached == true

  override fun attach(model: FloatingMonitorRenderModel) {
    instance().attach(model)
  }

  override fun update(model: FloatingMonitorRenderModel) {
    instance().update(model)
  }

  override fun detach() {
    delegate?.detach()
  }

  override fun deactivate() {
    delegate?.deactivate()
  }

  private fun instance(): FloatingMonitorWindowPort =
    delegate ?: factory().also { delegate = it }
}
