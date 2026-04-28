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

package com.ollitert.llm.server.ui.server

import android.content.Context
import com.ollitert.llm.server.data.ACTION_IN_FLIGHT_DEBOUNCE_MS
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.service.ServerService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

// Manual construction (no Hilt test rules) — these are pure unit tests that mock the
// companion-object service layer. Hilt DI adds no value since the only injected dep is Context.
@OptIn(ExperimentalCoroutinesApi::class)
class ServerViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val mockContext: Context = mockk(relaxed = true)
  private lateinit var vm: ServerViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkObject(ServerService)
    mockkObject(ServerPrefs)
    every { ServerService.start(any(), any(), any(), source = any()) } returns true
    every { ServerService.stop(any()) } returns Unit
    every { ServerService.reload(any(), any(), any(), any()) } returns true
    every { ServerPrefs.getPort(any()) } returns 8000
    vm = ServerViewModel(mockContext)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkAll()
  }

  // --- Start ---

  @Test
  fun startServerCallsService() = runTest(testDispatcher) {
    vm.startServer(port = 9090, modelName = "gemma", source = "ui")
    advanceUntilIdle()
    verify(exactly = 1) { ServerService.start(mockContext, 9090, "gemma", source = "ui") }
  }

  @Test
  fun startServerUsesDefaultPort() = runTest(testDispatcher) {
    every { ServerPrefs.getPort(mockContext) } returns 4000
    vm.startServer()
    advanceUntilIdle()
    verify { ServerService.start(mockContext, 4000, null, source = null) }
  }

  // --- Stop ---

  @Test
  fun stopServerCallsService() = runTest(testDispatcher) {
    vm.stopServer()
    advanceUntilIdle()
    verify(exactly = 1) { ServerService.stop(mockContext) }
  }

  // --- Reload ---

  @Test
  fun reloadServerCallsServiceWithCurrentModel() = runTest(testDispatcher) {
    vm.reloadServer(port = 8080)
    advanceUntilIdle()
    verify(exactly = 1) { ServerService.reload(mockContext, 8080, any(), any()) }
  }

  // --- Switch Model ---

  @Test
  fun switchModelSendsReloadNotStopStart() = runTest(testDispatcher) {
    vm.switchModel("gemma-4-12b", port = 8000)
    advanceUntilIdle()
    verify(exactly = 1) { ServerService.reload(mockContext, 8000, "gemma-4-12b", any()) }
    verify(exactly = 0) { ServerService.stop(any()) }
    verify(exactly = 0) { ServerService.start(any(), any(), any(), source = any()) }
  }

  // --- Failure Paths ---

  @Test
  fun startServerWhenServiceReturnsFalseStillDebounces() = runTest(testDispatcher) {
    every { ServerService.start(any(), any(), any(), source = any()) } returns false
    vm.startServer(port = 8000)
    vm.startServer(port = 8000)
    advanceUntilIdle()
    verify(exactly = 1) { ServerService.start(any(), any(), any(), source = any()) }
  }

  @Test
  fun startServerWhenServiceReturnsFalseDebounceExpiresNormally() = runTest(testDispatcher) {
    every { ServerService.start(any(), any(), any(), source = any()) } returns false
    vm.startServer(port = 8000)
    advanceTimeBy(ACTION_IN_FLIGHT_DEBOUNCE_MS + 1)
    every { ServerService.start(any(), any(), any(), source = any()) } returns true
    vm.startServer(port = 8000)
    advanceUntilIdle()
    verify(exactly = 2) { ServerService.start(any(), any(), any(), source = any()) }
  }

  @Test
  fun reloadServerWhenServiceReturnsFalseStillDebounces() = runTest(testDispatcher) {
    every { ServerService.reload(any(), any(), any(), any()) } returns false
    vm.reloadServer(port = 8080)
    vm.reloadServer(port = 8080)
    advanceUntilIdle()
    verify(exactly = 1) { ServerService.reload(any(), any(), any(), any()) }
  }

  // --- Debounce Guard ---

  @Test
  fun debounceBlocksSecondCallWithinWindow() = runTest(testDispatcher) {
    vm.startServer(port = 8000)
    vm.startServer(port = 8000)
    advanceUntilIdle()
    verify(exactly = 1) { ServerService.start(any(), any(), any(), source = any()) }
  }

  @Test
  fun debounceAllowsCallAfterWindowExpires() = runTest(testDispatcher) {
    vm.startServer(port = 8000)
    advanceTimeBy(ACTION_IN_FLIGHT_DEBOUNCE_MS + 1)
    vm.startServer(port = 8000)
    advanceUntilIdle()
    verify(exactly = 2) { ServerService.start(any(), any(), any(), source = any()) }
  }

  @Test
  fun debounceBlocksDifferentActionsWithinWindow() = runTest(testDispatcher) {
    vm.startServer(port = 8000)
    vm.stopServer()
    advanceUntilIdle()
    verify(exactly = 1) { ServerService.start(any(), any(), any(), source = any()) }
    verify(exactly = 0) { ServerService.stop(any()) }
  }

  @Test
  fun debounceResetsAfterEachAction() = runTest(testDispatcher) {
    vm.startServer(port = 8000)
    advanceTimeBy(ACTION_IN_FLIGHT_DEBOUNCE_MS + 1)
    vm.stopServer()
    advanceTimeBy(ACTION_IN_FLIGHT_DEBOUNCE_MS + 1)
    vm.reloadServer(port = 8000)
    advanceUntilIdle()

    verify(exactly = 1) { ServerService.start(any(), any(), any(), source = any()) }
    verify(exactly = 1) { ServerService.stop(any()) }
    verify(exactly = 1) { ServerService.reload(any(), any(), any(), any()) }
  }
}
