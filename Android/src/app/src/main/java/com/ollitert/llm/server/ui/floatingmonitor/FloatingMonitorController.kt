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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.ollitert.llm.server.MainActivity
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.service.ServerMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingMonitorController(
  context: Context,
  private val lifecycleProvider: OlliteRTLifecycleProvider,
  private val permissionCoordinator: FloatingMonitorPermissionCoordinator,
  private val settingEnabled: Flow<Boolean>,
) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val elapsedTracker = ProcessingElapsedTracker(SystemClock::elapsedRealtime)
  private val previousSuccessfulLatencyLatch = PreviousSuccessfulLatencyLatch()
  private val window = LazyFloatingMonitorWindowPort {
    AndroidFloatingMonitorWindow(
      context = appContext,
      onTap = { handleTap() },
      onWindowOperationFailure = { handleWindowOperationFailure(it) },
    )
  }
  private val reconciler = FloatingMonitorWindowReconciler(window) { failure ->
    Log.w(TAG, "Floating monitor window operation failed", failure)
  }
  private val tapCoordinator = FloatingMonitorTapCoordinator(
    scope = scope,
    timeoutMillis = TAP_SUPPRESSION_TIMEOUT_MILLIS,
    detach = { reconcileWindow(null) },
    launch = { openMainActivity() },
    reconcile = { reconcileAfterTapSuppression() },
    activateSuppressionInput = { activateRetryBudgetForTapSuppression() },
    onLaunchFailed = { ignoreNextLaunchSuppressionReleaseActivation = true },
  )
  private var monitorJob: Job? = null
  private var latestInput: CoreInput? = null
  private var retryActivationKey: FloatingMonitorRetryActivationKey? = null
  private var retryBudget = FloatingMonitorRetryBudget(MAX_CONSECUTIVE_WINDOW_FAILURES)
  private var retryAllowed = true
  private var ignoreNextLaunchSuppressionReleaseActivation = false
  private var visibleLastCycle = false
  private var lastVisibleRenderAtMillis = Long.MIN_VALUE
  private var disposed = false

  fun start() {
    if (disposed || monitorJob != null) return
    permissionCoordinator.refreshObservedPermission(appContext)
    monitorJob = scope.launch {
      try {
        val metricInputs = combine(
          ServerMetrics.status,
          ServerMetrics.isInferring,
          ServerMetrics.inferenceSequence,
          lifecycleProvider.isAppInForeground,
          settingEnabled,
        ) { status, isInferring, inferenceSequence, appIsForeground, settingIsEnabled ->
          MetricInput(
            status = status,
            isInferring = isInferring,
            inferenceSequence = inferenceSequence,
            appIsForeground = appIsForeground,
            settingEnabled = settingIsEnabled,
          )
        }
        val visibilityInputs = combine(
          permissionCoordinator.permissionFlowInProgress,
          permissionCoordinator.overlayPermissionGranted,
          tapCoordinator.suppressionActive,
        ) { permissionFlowInProgress, overlayPermissionGranted, launchSuppressionActive ->
          VisibilityInput(
            permissionFlowInProgress = permissionFlowInProgress,
            overlayPermissionGranted = overlayPermissionGranted,
            launchSuppressionActive = launchSuppressionActive,
          )
        }
        combine(metricInputs, visibilityInputs) { metrics, visibility ->
          CoreInput(
            status = metrics.status,
            isInferring = metrics.isInferring,
            inferenceSequence = metrics.inferenceSequence,
            appIsForeground = metrics.appIsForeground,
            settingEnabled = metrics.settingEnabled,
            permissionFlowInProgress = visibility.permissionFlowInProgress,
            overlayPermissionGranted = visibility.overlayPermissionGranted,
            launchSuppressionActive = visibility.launchSuppressionActive,
          )
        }.collectLatest { input ->
          latestInput = input
          activateRetryBudgetIfNeeded(input)
          if (input.appIsForeground) tapCoordinator.onAppForegrounded()
          elapsedTracker.update(input.isInferring, input.inferenceSequence)
          val visible = isVisible(input)
          if (!visible) {
            visibleLastCycle = false
            lastVisibleRenderAtMillis = Long.MIN_VALUE
          } else if (visibleLastCycle) {
            val remainingDelay = floatingMonitorVisibleRenderDelayMillis(
              wasVisible = true,
              lastRenderAtMillis = lastVisibleRenderAtMillis,
              nowMillis = SystemClock.elapsedRealtime(),
            )
            if (remainingDelay > 0L) delay(remainingDelay)
          }
          if (!render(input)) return@collectLatest
          if (visible) {
            visibleLastCycle = true
            lastVisibleRenderAtMillis = SystemClock.elapsedRealtime()
          }

          while (currentCoroutineContext().isActive) {
            delay(FLOATING_MONITOR_REFRESH_MILLIS)
            val observedPermission = permissionCoordinator.refreshObservedPermission(appContext)
            if (observedPermission != input.overlayPermissionGranted) return@collectLatest
            if (!render(input)) return@collectLatest
            if (visible) lastVisibleRenderAtMillis = SystemClock.elapsedRealtime()
          }
        }
      } catch (exception: CancellationException) {
        throw exception
      } catch (exception: RuntimeException) {
        Log.w(TAG, "Floating monitor observer stopped after a contained failure", exception)
        reconciler.dispose()
      }
    }
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    monitorJob?.cancel()
    monitorJob = null
    tapCoordinator.dispose()
    scope.cancel()
    elapsedTracker.dispose()
    reconciler.dispose()
  }

  private fun activateRetryBudgetIfNeeded(input: CoreInput) {
    val key = input.retryActivationKey()
    val shouldReactivate = shouldReactivateFloatingMonitorRetryBudget(
      previous = retryActivationKey,
      next = key,
      ignoreLaunchFailureRelease = ignoreNextLaunchSuppressionReleaseActivation,
    )
    retryActivationKey = key
    if (input.launchSuppressionActive.not()) {
      ignoreNextLaunchSuppressionReleaseActivation = false
    }
    if (!shouldReactivate) return
    retryBudget = FloatingMonitorRetryBudget(MAX_CONSECUTIVE_WINDOW_FAILURES)
    retryAllowed = true
  }

  private fun CoreInput.retryActivationKey() = FloatingMonitorRetryActivationKey(
    status = status,
    isInferring = isInferring,
    inferenceSequence = inferenceSequence,
    appIsForeground = appIsForeground,
    settingEnabled = settingEnabled,
    permissionFlowInProgress = permissionFlowInProgress,
    overlayPermissionGranted = overlayPermissionGranted,
    launchSuppressionActive = launchSuppressionActive,
  )

  private fun activateRetryBudgetForTapSuppression() {
    val input = latestInput ?: return
    // A launch-failure release exception belongs only to the tap that created it. StateFlow may
    // conflate that tap's true -> false transition, so a new tap must explicitly discard it.
    ignoreNextLaunchSuppressionReleaseActivation = false
    val key = input.copy(launchSuppressionActive = true).retryActivationKey()
    retryActivationKey = key
    retryBudget = FloatingMonitorRetryBudget(MAX_CONSECUTIVE_WINDOW_FAILURES)
    retryAllowed = true
    visibleLastCycle = false
    lastVisibleRenderAtMillis = Long.MIN_VALUE
  }

  private fun isVisible(input: CoreInput): Boolean = shouldShowFloatingMonitor(
    settingEnabled = input.settingEnabled,
    overlayPermissionGranted = input.overlayPermissionGranted,
    permissionFlowInProgress = input.permissionFlowInProgress,
    appIsForeground = input.appIsForeground,
    launchSuppressionActive = input.launchSuppressionActive,
    serviceIsAlive = !disposed,
    visualState = deriveFloatingMonitorVisualState(input.status, input.isInferring),
  )

  private fun render(input: CoreInput): Boolean {
    val visualState = deriveFloatingMonitorVisualState(
      status = input.status,
      isInferring = input.isInferring,
    )
    val previousSuccessfulLatencyMs = previousSuccessfulLatencyLatch.valueFor(
      isProcessing = visualState == FloatingMonitorVisualState.Processing,
      inferenceSequence = input.inferenceSequence,
      liveLatencyMs = ServerMetrics.lastLatencyMs.value,
    )
    val visible = shouldShowFloatingMonitor(
      settingEnabled = input.settingEnabled,
      overlayPermissionGranted = input.overlayPermissionGranted,
      permissionFlowInProgress = input.permissionFlowInProgress,
      appIsForeground = input.appIsForeground,
      launchSuppressionActive = input.launchSuppressionActive,
      serviceIsAlive = !disposed,
      visualState = visualState,
    )

    val model = if (visible) {
      deriveFloatingMonitorRenderModel(
        visualState = visualState,
        requestCount = ServerMetrics.requestCount.value,
        errorCount = ServerMetrics.errorCount.value,
        processingElapsedMillis = elapsedTracker.elapsedMillis(),
        previousSuccessfulLatencyMs = previousSuccessfulLatencyMs,
      )
    } else {
      null
    }
    val reconciled = reconcileWindow(model)
    return shouldContinueFloatingMonitorReconciliation(
      modelVisible = model != null,
      reconciled = reconciled,
      retryAllowed = retryAllowed,
    )
  }

  private fun reconcileWindow(model: FloatingMonitorRenderModel?): Boolean {
    if (!retryAllowed) return false
    val reconciled = reconciler.reconcile(model)
    retryAllowed = retryBudget.record(reconciled)
    if (!reconciled && !retryAllowed) {
      Log.w(TAG, "Floating monitor retry budget exhausted; waiting for a relevant input change")
    }
    return reconciled
  }

  private fun handleWindowOperationFailure(failure: RuntimeException) {
    Log.w(TAG, "Floating monitor interaction window operation failed", failure)
    retryAllowed = retryBudget.record(success = false)
    if (!retryAllowed) {
      Log.w(TAG, "Floating monitor retry budget exhausted after interaction failures")
    }
  }

  private fun handleTap() {
    if (disposed) return
    tapCoordinator.handleTap()
  }

  private fun reconcileAfterTapSuppression() {
    if (disposed) return
    val input = latestInput ?: return
    val recoveryInput = input.copy(launchSuppressionActive = false)
    render(recoveryInput)
    if (isVisible(recoveryInput)) {
      visibleLastCycle = true
      lastVisibleRenderAtMillis = SystemClock.elapsedRealtime()
    }
  }

  private fun openMainActivity(): Boolean {
    val intent = Intent(appContext, MainActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    try {
      PendingIntent.getActivity(
        appContext,
        OPEN_ACTIVITY_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      ).send()
      return true
    } catch (exception: PendingIntent.CanceledException) {
      Log.w(TAG, "Floating monitor PendingIntent was cancelled; using direct fallback", exception)
    } catch (exception: RuntimeException) {
      Log.w(TAG, "Unable to create or send floating monitor PendingIntent", exception)
    }

    return try {
      appContext.startActivity(intent)
      true
    } catch (exception: RuntimeException) {
      Log.w(TAG, "Unable to open MainActivity from floating monitor", exception)
      false
    }
  }

  private data class MetricInput(
    val status: ServerStatus,
    val isInferring: Boolean,
    val inferenceSequence: Long,
    val appIsForeground: Boolean,
    val settingEnabled: Boolean,
  )

  private data class VisibilityInput(
    val permissionFlowInProgress: Boolean,
    val overlayPermissionGranted: Boolean,
    val launchSuppressionActive: Boolean,
  )

  private data class CoreInput(
    val status: ServerStatus,
    val isInferring: Boolean,
    val inferenceSequence: Long,
    val appIsForeground: Boolean,
    val settingEnabled: Boolean,
    val permissionFlowInProgress: Boolean,
    val overlayPermissionGranted: Boolean,
    val launchSuppressionActive: Boolean,
  )


  private companion object {
    const val TAG = "OlliteRT.FloatMonitor"
    const val TAP_SUPPRESSION_TIMEOUT_MILLIS = 3_000L
    const val MAX_CONSECUTIVE_WINDOW_FAILURES = 3
    const val OPEN_ACTIVITY_REQUEST_CODE = 72
  }
}

internal data class FloatingMonitorRetryActivationKey(
  val status: ServerStatus,
  val isInferring: Boolean,
  val inferenceSequence: Long,
  val appIsForeground: Boolean,
  val settingEnabled: Boolean,
  val permissionFlowInProgress: Boolean,
  val overlayPermissionGranted: Boolean,
  val launchSuppressionActive: Boolean,
)

internal const val FLOATING_MONITOR_REFRESH_MILLIS = 1_000L

internal fun floatingMonitorVisibleRenderDelayMillis(
  wasVisible: Boolean,
  lastRenderAtMillis: Long,
  nowMillis: Long,
): Long {
  if (!wasVisible || lastRenderAtMillis == Long.MIN_VALUE) return 0L
  val elapsedMillis = (nowMillis - lastRenderAtMillis).coerceAtLeast(0L)
  return (FLOATING_MONITOR_REFRESH_MILLIS - elapsedMillis).coerceAtLeast(0L)
}

internal fun shouldReactivateFloatingMonitorRetryBudget(
  previous: FloatingMonitorRetryActivationKey?,
  next: FloatingMonitorRetryActivationKey,
  ignoreLaunchFailureRelease: Boolean,
): Boolean {
  if (previous == next) return false
  if (
    ignoreLaunchFailureRelease &&
    previous?.launchSuppressionActive == true &&
    !next.launchSuppressionActive &&
    previous.copy(launchSuppressionActive = false) == next
  ) {
    return false
  }
  return true
}
