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

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class ServerWakeLockHelperTest {

  private val context: Context = mockk(relaxed = true)
  private val powerManager: PowerManager = mockk(relaxed = true)
  private val wifiManager: WifiManager = mockk(relaxed = true)
  private val wakeLock: PowerManager.WakeLock = mockk(relaxed = true)
  private val wifiLock: WifiManager.WifiLock = mockk(relaxed = true)

  @Before
  fun setUp() {
    mockkStatic(Log::class)
    every { Log.w(any<String>(), any<String>()) } returns 0
    every { Log.w(any<String>(), any<String>(), any()) } returns 0

    every { context.getSystemService(Context.POWER_SERVICE) } returns powerManager
    every { context.getSystemService(Context.WIFI_SERVICE) } returns wifiManager
    every { powerManager.newWakeLock(any(), any()) } returns wakeLock
    @Suppress("DEPRECATION")
    every { wifiManager.createWifiLock(any<Int>(), any()) } returns wifiLock
  }

  @After
  fun tearDown() {
    unmockkStatic(Log::class)
  }

  @Test
  fun acquiresLocksWhenNotHeld() {
    every { wakeLock.isHeld } returns false
    every { wifiLock.isHeld } returns false

    val helper = ServerWakeLockHelper(context)
    helper.acquire()

    verify(exactly = 1) { wakeLock.acquire() }
    verify(exactly = 1) { wifiLock.acquire() }
  }

  @Test
  fun doesNotReacquireWhenAlreadyHeld() {
    every { wakeLock.isHeld } returns true
    every { wifiLock.isHeld } returns true

    val helper = ServerWakeLockHelper(context)
    helper.acquire()

    verify(exactly = 0) { wakeLock.acquire() }
    verify(exactly = 0) { wifiLock.acquire() }
  }

  @Test
  fun releasesLocksWhenHeld() {
    every { wakeLock.isHeld } returns true
    every { wifiLock.isHeld } returns true

    val helper = ServerWakeLockHelper(context)
    helper.release()

    verify(exactly = 1) { wakeLock.release() }
    verify(exactly = 1) { wifiLock.release() }
  }

  @Test
  fun handlesNullSystemServicesGracefully() {
    every { context.getSystemService(Context.POWER_SERVICE) } returns null
    every { context.getSystemService(Context.WIFI_SERVICE) } returns null

    val helper = ServerWakeLockHelper(context)
    helper.acquire()
    helper.release()
  }
}
