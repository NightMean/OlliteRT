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
import android.content.Intent
import android.util.Log
import com.ollitert.llm.server.data.MIN_VALID_PORT
import com.ollitert.llm.server.data.ServerPrefs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

class BootReceiverTest {

  private val receiver = BootReceiver()
  private val context: Context = mockk(relaxed = true)
  private val intent: Intent = mockk(relaxed = true)

  @Before
  fun setUp() {
    mockkStatic(Log::class)
    every { Log.i(any(), any()) } returns 0
    every { Log.w(any<String>(), any<String>()) } returns 0
    every { Log.e(any(), any(), any()) } returns 0

    mockkObject(ServerPrefs)
    mockkObject(ServerService)
    every { ServerService.start(any(), any(), any(), source = any()) } returns true
  }

  @After
  fun tearDown() {
    unmockkObject(ServerService)
    unmockkObject(ServerPrefs)
    unmockkStatic(Log::class)
  }

  @Test
  fun `ignores non-BOOT_COMPLETED intents`() {
    every { intent.action } returns Intent.ACTION_POWER_CONNECTED
    receiver.onReceive(context, intent)
    verify(exactly = 0) { ServerPrefs.isAutoStartOnBoot(any()) }
  }

  @Test
  fun `skips when auto-start disabled`() {
    every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
    every { ServerPrefs.isAutoStartOnBoot(context) } returns false
    receiver.onReceive(context, intent)
    verify(exactly = 0) { ServerPrefs.getDefaultModelName(any()) }
  }

  @Test
  fun `skips when no default model configured`() {
    every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
    every { ServerPrefs.isAutoStartOnBoot(context) } returns true
    every { ServerPrefs.getDefaultModelName(context) } returns null
    receiver.onReceive(context, intent)
    verify(exactly = 0) { ServerService.start(any(), any(), any(), source = any()) }
  }

  @Test
  fun `skips when default model is blank`() {
    every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
    every { ServerPrefs.isAutoStartOnBoot(context) } returns true
    every { ServerPrefs.getDefaultModelName(context) } returns "  "
    receiver.onReceive(context, intent)
    verify(exactly = 0) { ServerService.start(any(), any(), any(), source = any()) }
  }

  @Test
  fun `skips when port is out of valid range`() {
    every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
    every { ServerPrefs.isAutoStartOnBoot(context) } returns true
    every { ServerPrefs.getDefaultModelName(context) } returns "Gemma-4-E2B-it"
    every { ServerPrefs.getPort(context) } returns MIN_VALID_PORT - 1
    receiver.onReceive(context, intent)
    verify(exactly = 0) { ServerService.start(any(), any(), any(), source = any()) }
  }

  @Test
  fun `starts server on boot with valid config`() {
    every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
    every { ServerPrefs.isAutoStartOnBoot(context) } returns true
    every { ServerPrefs.getDefaultModelName(context) } returns "Gemma-4-E2B-it"
    every { ServerPrefs.getPort(context) } returns 8000
    receiver.onReceive(context, intent)
    verify(exactly = 1) {
      ServerService.start(context, 8000, "Gemma-4-E2B-it", source = ServerService.SOURCE_BOOT)
    }
  }

  @Test
  fun `catches exceptions without crashing`() {
    every { intent.action } returns Intent.ACTION_BOOT_COMPLETED
    every { ServerPrefs.isAutoStartOnBoot(context) } throws RuntimeException("corrupt prefs")
    receiver.onReceive(context, intent)
    verify { Log.e(any(), any(), any()) }
  }
}
