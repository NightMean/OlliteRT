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

import android.content.Context
import android.provider.Settings
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class FloatingMonitorPermissionCoordinator @Inject constructor() {
  private val _permissionFlowInProgress = MutableStateFlow(false)
  val permissionFlowInProgress: StateFlow<Boolean> = _permissionFlowInProgress.asStateFlow()

  private val _overlayPermissionGranted = MutableStateFlow(false)
  val overlayPermissionGranted: StateFlow<Boolean> = _overlayPermissionGranted.asStateFlow()

  fun beginPermissionFlow() {
    _permissionFlowInProgress.value = true
  }

  fun endPermissionFlow() {
    _permissionFlowInProgress.value = false
  }

  fun updateObservedPermission(granted: Boolean) {
    _overlayPermissionGranted.value = granted
  }

  fun refreshObservedPermission(context: Context): Boolean {
    val granted = try {
      Settings.canDrawOverlays(context.applicationContext)
    } catch (exception: RuntimeException) {
      Log.w(TAG, "Unable to read overlay permission", exception)
      false
    }
    updateObservedPermission(granted)
    return granted
  }

  private companion object {
    const val TAG = "OlliteRT.FloatPermission"
  }
}
