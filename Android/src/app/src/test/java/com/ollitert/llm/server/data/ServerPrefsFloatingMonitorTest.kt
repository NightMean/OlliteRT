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

package com.ollitert.llm.server.data

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerPrefsFloatingMonitorTest {
  private val context: Context = mockk(relaxed = true)
  private val preferences: SharedPreferences = mockk(relaxed = true)
  private val editor: SharedPreferences.Editor = mockk(relaxed = true)

  @Before
  fun setUp() {
    every { context.getSharedPreferences(any(), any()) } returns preferences
    every { preferences.edit() } returns editor
    every { editor.clear() } returns editor
    every { editor.putBoolean(any(), any()) } returns editor
    every { editor.apply() } returns Unit
    ServerPrefs.resetToDefaults(context)
  }

  @After
  fun tearDown() {
    ServerPrefs.resetToDefaults(context)
  }

  @Test
  fun `enabled intent defaults off and setter uses dedicated preference`() {
    every { preferences.getBoolean("floating_monitor_enabled", false) } returns false

    assertEquals(false, ServerPrefs.isFloatingMonitorEnabled(context))
    ServerPrefs.setFloatingMonitorEnabled(context, true)

    verify(exactly = 1) { editor.putBoolean("floating_monitor_enabled", true) }
  }

  @Test
  fun `enabled flow emits preference changes while service stays alive`() = runTest {
    var enabled = false
    val listenerSlot = slot<SharedPreferences.OnSharedPreferenceChangeListener>()
    every { preferences.getBoolean("floating_monitor_enabled", false) } answers { enabled }
    every { preferences.registerOnSharedPreferenceChangeListener(capture(listenerSlot)) } returns Unit
    every { preferences.unregisterOnSharedPreferenceChangeListener(any()) } returns Unit

    val values = async {
      ServerPrefs.floatingMonitorEnabledFlow(context).take(2).toList()
    }
    runCurrent()

    enabled = true
    listenerSlot.captured.onSharedPreferenceChanged(preferences, "floating_monitor_enabled")
    runCurrent()

    assertEquals(listOf(false, true), values.await())
    verify(exactly = 1) { preferences.unregisterOnSharedPreferenceChangeListener(listenerSlot.captured) }
  }

  @Test
  fun `enabled flow emits disabled after whole preference file is cleared`() = runTest {
    var enabled = true
    val listenerSlot = slot<SharedPreferences.OnSharedPreferenceChangeListener>()
    every { preferences.getBoolean("floating_monitor_enabled", false) } answers { enabled }
    every { preferences.registerOnSharedPreferenceChangeListener(capture(listenerSlot)) } returns Unit
    every { preferences.unregisterOnSharedPreferenceChangeListener(any()) } returns Unit

    val values = async {
      ServerPrefs.floatingMonitorEnabledFlow(context).take(2).toList()
    }
    runCurrent()

    enabled = false
    listenerSlot.captured.onSharedPreferenceChanged(preferences, null)
    runCurrent()

    assertEquals(listOf(true, false), values.await())
  }
}
