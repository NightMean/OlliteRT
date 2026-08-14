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

package com.ollitert.llm.server.service

import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

private const val TAG = "OlliteRT.WakeLock"

/**
 * Manages CPU Partial WakeLock and WiFi High-Performance Lock for 24/7 server operation.
 *
 * - **Partial wake lock:** Prevents Doze mode from suspending the CPU on locked/idle devices,
 *   keeping the HTTP server reachable between requests in "closet server" setups.
 * - **WiFi lock:** Prevents aggressive OEM power-saving modes (Samsung, Xiaomi, Huawei)
 *   from throttling or disabling the WiFi radio while the screen is off.
 */
class ServerWakeLockHelper(context: Context) {

  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null

  init {
    try {
      val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
      wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OlliteRT::Server")?.apply {
        setReferenceCounted(false)
      }
      val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      @Suppress("DEPRECATION") // WIFI_MODE_FULL_HIGH_PERF deprecated in API 34, no equivalent for keeping WiFi at full power
      wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "OlliteRT::Server")?.apply {
        setReferenceCounted(false)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to initialize wake/wifi locks", e)
    }
  }

  /** Acquires CPU + WiFi wake locks for continuous background serving. */
  fun acquire() {
    try {
      if (wakeLock?.isHeld == false) wakeLock?.acquire()
      if (wifiLock?.isHeld == false) wifiLock?.acquire()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to acquire wake/wifi locks", e)
    }
  }

  /** Releases CPU + WiFi wake locks on server shutdown. */
  fun release() {
    try {
      if (wifiLock?.isHeld == true) wifiLock?.release()
      if (wakeLock?.isHeld == true) wakeLock?.release()
    } catch (e: Exception) {
      Log.w(TAG, "Failed to release wake/wifi locks", e)
    } finally {
      wifiLock = null
      wakeLock = null
    }
  }
}
