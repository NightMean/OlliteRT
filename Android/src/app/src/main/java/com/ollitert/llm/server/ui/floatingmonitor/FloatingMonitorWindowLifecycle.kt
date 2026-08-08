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

private const val MAX_DISPOSE_DETACH_ATTEMPTS = 2

data class FloatingMonitorRenderModel(
  val visualState: FloatingMonitorVisualState,
  val requestValue: String,
  val secondaryValue: String,
  val secondaryLabel: String,
  val secondaryUnit: String? = null,
  val lastLatency: FloatingMonitorLatencyText? = null,
)

fun deriveFloatingMonitorRenderModel(
  visualState: FloatingMonitorVisualState,
  requestCount: Long,
  errorCount: Long,
  processingElapsedMillis: Long?,
  lastLatencyMs: Long = 0L,
): FloatingMonitorRenderModel? {
  if (visualState == FloatingMonitorVisualState.Hidden) return null
  val processingElapsed = formatProcessingElapsed(processingElapsedMillis ?: 0)

  return FloatingMonitorRenderModel(
    visualState = visualState,
    requestValue = formatFloatingMonitorCount(requestCount),
    secondaryValue = when (visualState) {
      FloatingMonitorVisualState.Running -> formatFloatingMonitorCount(errorCount)
      FloatingMonitorVisualState.Processing -> processingElapsed.value
      FloatingMonitorVisualState.Hidden -> error("Hidden was handled above")
    },
    secondaryLabel = when (visualState) {
      FloatingMonitorVisualState.Running -> "err"
      FloatingMonitorVisualState.Processing -> "proc"
      FloatingMonitorVisualState.Hidden -> error("Hidden was handled above")
    },
    secondaryUnit = when (visualState) {
      FloatingMonitorVisualState.Running -> null
      FloatingMonitorVisualState.Processing -> processingElapsed.inlineUnit
      FloatingMonitorVisualState.Hidden -> error("Hidden was handled above")
    },
    lastLatency = when (visualState) {
      FloatingMonitorVisualState.Running -> null
      FloatingMonitorVisualState.Processing ->
        formatLastLatency(lastLatencyMs)
      FloatingMonitorVisualState.Hidden -> error("Hidden was handled above")
    },
  )
}

fun floatingMonitorContentDescription(model: FloatingMonitorRenderModel): String =
  when (model.visualState) {
    FloatingMonitorVisualState.Running ->
      "Running, requests ${model.requestValue}, errors ${model.secondaryValue}"
    FloatingMonitorVisualState.Processing -> {
      val lastLatency = model.lastLatency
      val lastDescription = when (lastLatency?.unit) {
        "ms" -> "${lastLatency.value} milliseconds"
        "s" -> "${lastLatency.value} seconds"
        else -> "unavailable"
      }
      "Processing, requests ${model.requestValue}, " +
        "current processing ${model.secondaryValue} seconds, " +
        "last successful latency $lastDescription"
    }
    FloatingMonitorVisualState.Hidden -> error("Hidden render models are not created")
  }

interface FloatingMonitorWindowPort {
  val isAttached: Boolean

  fun attach(model: FloatingMonitorRenderModel)

  fun update(model: FloatingMonitorRenderModel)

  fun detach()

  fun deactivate()
}

class FloatingMonitorWindowReconciler(
  private val window: FloatingMonitorWindowPort,
  private val onFailure: (RuntimeException) -> Unit = {},
) {
  private var disposed = false

  fun reconcile(model: FloatingMonitorRenderModel?): Boolean {
    if (disposed) return model == null && !window.isAttached
    if (model == null) {
      return detachIfAttached()
    }

    if (!window.isAttached) {
      return try {
        window.attach(model)
        window.isAttached
      } catch (exception: RuntimeException) {
        reportFailure(exception)
        false
      }
    }

    return try {
      window.update(model)
      window.isAttached
    } catch (exception: RuntimeException) {
      reportFailure(exception)
      detachIfAttached()
      false
    }
  }

  fun dispose() {
    if (disposed) return
    var attempts = 0
    while (window.isAttached && attempts < MAX_DISPOSE_DETACH_ATTEMPTS) {
      detachIfAttached()
      attempts += 1
    }
    if (window.isAttached) {
      try {
        window.deactivate()
      } catch (exception: RuntimeException) {
        reportFailure(exception)
      }
    }
    disposed = true
  }

  private fun detachIfAttached(): Boolean {
    if (!window.isAttached) return true
    return try {
      window.detach()
      !window.isAttached
    } catch (exception: RuntimeException) {
      reportFailure(exception)
      false
    }
  }

  private fun reportFailure(failure: RuntimeException) {
    try {
      onFailure(failure)
    } catch (_: RuntimeException) {
      // Diagnostics must not turn a disposable overlay failure into a server failure.
    }
  }
}
