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
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.ui.server.logs.StatusRange
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private val mockContext: Context = mockk(relaxed = true)
  private lateinit var vm: LogsViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    RequestLogStore.clear()
    vm = LogsViewModel(mockContext)
  }

  @After
  fun tearDown() {
    RequestLogStore.clear()
    Dispatchers.resetMain()
    unmockkAll()
  }

  @Test
  fun searchDraftAndCommitSearch_updatesFilterQuery() = runTest(testDispatcher) {
    vm.setSearchDraft("hello")
    assertEquals("hello", vm.searchDraft.value)

    vm.commitSearch()
    assertEquals("hello", vm.filter.value.query)

    vm.clearSearch()
    assertEquals("", vm.searchDraft.value)
    assertEquals("", vm.filter.value.query)
  }

  @Test
  fun toggleFilters_updatesStateCorrectly() = runTest(testDispatcher) {
    vm.toggleMethod("POST")
    assertTrue(vm.filter.value.methods.contains("POST"))

    vm.toggleMethod("POST")
    assertFalse(vm.filter.value.methods.contains("POST"))

    vm.toggleStatusRange(StatusRange.SUCCESS)
    assertTrue(vm.filter.value.statusRanges.contains(StatusRange.SUCCESS))

    vm.toggleLevel(LogLevel.ERROR)
    assertTrue(vm.filter.value.levels.contains(LogLevel.ERROR))

    vm.clearAllFilters()
    assertFalse(vm.filter.value.isActive)
  }

  @Test
  fun searchBarVisibility_togglesAndHides() {
    assertFalse(vm.searchBarVisible.value)

    vm.toggleSearchBar()
    assertTrue(vm.searchBarVisible.value)

    vm.hideSearchBar()
    assertFalse(vm.searchBarVisible.value)
  }

  @Test
  fun dialogStateFlows_updateCorrectly() {
    assertFalse(vm.showClearConfirmDialog.value)
    vm.setShowClearConfirmDialog(true)
    assertTrue(vm.showClearConfirmDialog.value)

    assertFalse(vm.showClearActiveDialog.value)
    vm.setShowClearActiveDialog(true)
    assertTrue(vm.showClearActiveDialog.value)
  }

  @Test
  fun clearLogs_emptiesStoreAndDismissesConfirmDialog() {
    RequestLogStore.addEvent("test event")
    assertEquals(1, RequestLogStore.entries.value.size)

    vm.setShowClearConfirmDialog(true)
    vm.clearLogs()

    assertEquals(0, RequestLogStore.entries.value.size)
    assertFalse(vm.showClearConfirmDialog.value)
  }
}
