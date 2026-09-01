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

package com.ollitert.llm.server.floatingmonitor

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.common.FloatingInferenceSettings
import com.ollitert.llm.server.common.FloatingMonitorBounds
import com.ollitert.llm.server.common.FloatingMonitorPlacement
import com.ollitert.llm.server.common.ModelLoadPhase
import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.prefs.ServerPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OlliteRT.FloatingMonitor"

internal data class FloatingMonitorSnapshot(
  val status: ServerStatus = ServerStatus.STOPPED,
  val modelLoadPhase: ModelLoadPhase = ModelLoadPhase.STARTING,
  val isInferring: Boolean = false,
  val modelName: String? = null,
  val accelerator: String? = null,
  val thinkingEnabled: Boolean = false,
  val mtpEnabled: Boolean = false,
  val requestCount: Long = 0L,
  val decodeSpeed: Double = 0.0,
  val ttfbMs: Long = 0L,
  val errorCount: Long = 0L,
  val startedAtMs: Long = 0L,
  val inferenceSettings: FloatingInferenceSettings = FloatingInferenceSettings(),
)

@Singleton
class FloatingMonitorController @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val lifecycleProvider: OlliteRTLifecycleProvider,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val appContext = context.applicationContext
  // Match the proven PR pattern: WindowManager owns the physical-display bounds. Do not ask
  // an application context for Context.display, which is unsupported after backgrounding.
  private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
  private var snapshot = FloatingMonitorSnapshot()
  private var monitorView: FloatingMonitorView? = null
  private var uptimeTicker: Job? = null

  fun start() {
    observe(ServerMetrics.status) { snapshot = snapshot.copy(status = it) }
    observe(ServerMetrics.modelLoadPhase) { snapshot = snapshot.copy(modelLoadPhase = it) }
    observe(ServerMetrics.isInferring) { snapshot = snapshot.copy(isInferring = it) }
    observe(ServerMetrics.activeModelName) { snapshot = snapshot.copy(modelName = it) }
    observe(ServerMetrics.activeAccelerator) { snapshot = snapshot.copy(accelerator = it) }
    observe(ServerMetrics.thinkingEnabled) { snapshot = snapshot.copy(thinkingEnabled = it) }
    observe(ServerMetrics.speculativeDecodingEnabled) { snapshot = snapshot.copy(mtpEnabled = it) }
    observe(ServerMetrics.requestCount) { snapshot = snapshot.copy(requestCount = it) }
    observe(ServerMetrics.lastDecodeSpeed) { snapshot = snapshot.copy(decodeSpeed = it) }
    observe(ServerMetrics.lastTtfbMs) { snapshot = snapshot.copy(ttfbMs = it) }
    observe(ServerMetrics.errorCount) { snapshot = snapshot.copy(errorCount = it) }
    observe(ServerMetrics.startedAtMs) { snapshot = snapshot.copy(startedAtMs = it) }
    observe(ServerMetrics.inferenceSettings) { snapshot = snapshot.copy(inferenceSettings = it) }
    observe(lifecycleProvider.appInForeground) { reconcile() }
    reconcile()
  }

  fun reconcile() {
    if (windowManager == null) {
      Log.w(TAG, "WindowManager unavailable; floating monitor cannot be shown")
      return
    }
    if (!shouldShowFloatingMonitor(
        enabled = ServerPrefs.isFloatingMonitorEnabled(context),
        hasOverlayPermission = Settings.canDrawOverlays(context),
        isAppInForeground = lifecycleProvider.isAppInForeground,
        status = snapshot.status,
      )) {
      removeMonitor()
      return
    }
    if (monitorView == null) addMonitor() else render()
  }

  private fun <T> observe(flow: kotlinx.coroutines.flow.StateFlow<T>, update: (T) -> Unit) {
    scope.launch {
      flow.collect {
        update(it)
        reconcile()
      }
    }
  }

  private fun addMonitor() {
    val placementPair = ServerPrefs.getFloatingMonitorPlacement(context)
    val placement = FloatingMonitorPlacement(placementPair.first, placementPair.second).clamped()
    val view = FloatingMonitorView(
      context = appContext,
      onPlacementChanged = { x, y ->
        updatePosition(x, y, persist = true)
      },
      onPositionChanged = { x, y -> updatePosition(x, y, persist = false) },
      onSizeChanged = { updateLayout() },
      onOpenApp = ::openApp,
    )
    monitorView = view
    try {
      windowManager?.addView(view, layoutParams(view, placement))
      render()
      uptimeTicker = scope.launch {
        while (true) {
          delay(1_000L)
          monitorView?.render(snapshot, System.currentTimeMillis())
        }
      }
    } catch (e: RuntimeException) {
      monitorView = null
      Log.w(TAG, "Unable to attach floating monitor", e)
    }
  }

  private fun render() {
    monitorView?.render(snapshot, System.currentTimeMillis())
  }

  private fun updateLayout() {
    val view = monitorView ?: return
    val manager = windowManager ?: return
    try {
      val current = view.layoutParams as? WindowManager.LayoutParams ?: return
      current.height = view.desiredHeightPx
      manager.updateViewLayout(view, current)
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unable to update floating monitor layout", e)
    }
  }

  fun resetPosition() {
    val defaultPlacement = FloatingMonitorPlacement.DEFAULT
    ServerPrefs.setFloatingMonitorPlacement(context, defaultPlacement.xFraction, defaultPlacement.yFraction)
    val view = monitorView ?: return
    val manager = windowManager ?: return
    try {
      manager.updateViewLayout(view, layoutParams(view, defaultPlacement))
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unable to reset floating monitor position", e)
    }
  }

  private fun openApp() {
    val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName) ?: return
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    try {
      appContext.startActivity(launchIntent)
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unable to open app from floating monitor", e)
    }
  }

  private fun removeMonitor() {
    uptimeTicker?.cancel()
    uptimeTicker = null
    val view = monitorView ?: return
    monitorView = null
    try {
      windowManager?.removeViewImmediate(view)
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unable to remove floating monitor", e)
    }
  }

  private fun layoutParams(view: FloatingMonitorView, placement: FloatingMonitorPlacement): WindowManager.LayoutParams {
    val bounds = displayBounds()
    val (x, y) = placement.toWindowPosition(bounds, view.desiredWidthPx, view.desiredHeightPx)
    return WindowManager.LayoutParams(
      view.desiredWidthPx,
      view.desiredHeightPx,
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      this.x = x
      this.y = y
    }
  }

  private fun updatePosition(x: Int, y: Int, persist: Boolean) {
    val view = monitorView ?: return
    val manager = windowManager ?: return
    try {
      val current = view.layoutParams as? WindowManager.LayoutParams ?: return
      current.x = x
      current.y = y
      manager.updateViewLayout(view, current)
      if (persist) {
        val placement = FloatingMonitorPlacement.fromWindowPosition(
          x, y, displayBounds(), view.desiredWidthPx, view.desiredHeightPx,
        )
        ServerPrefs.setFloatingMonitorPlacement(context, placement.xFraction, placement.yFraction)
      }
    } catch (e: RuntimeException) {
      Log.w(TAG, "Unable to update floating monitor position", e)
    }
  }

  private fun displayBounds(): FloatingMonitorBounds {
    val bounds = requireNotNull(windowManager).currentWindowMetrics.bounds
    return FloatingMonitorBounds(bounds.width(), bounds.height())
  }
}
