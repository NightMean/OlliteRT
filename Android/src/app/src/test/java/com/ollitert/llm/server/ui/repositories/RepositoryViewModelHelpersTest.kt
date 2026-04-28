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

package com.ollitert.llm.server.ui.repositories

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.data.FakeDataStoreRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryViewModelHelpersTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var vm: RepositoryViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.e(any(), any()) } returns 0
    every { Log.e(any(), any(), any()) } returns 0
    val mockContext: Context = mockk(relaxed = true)
    every { mockContext.getExternalFilesDir(null) } returns java.io.File(System.getProperty("java.io.tmpdir") ?: "/tmp")
    vm = RepositoryViewModel(dataStoreRepository = FakeDataStoreRepository(), context = mockContext)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkStatic(Log::class)
  }

  // --- getDownloadedModelCountForRepo ---

  @Test
  fun countReturnsZeroForEmptyMap() {
    assertEquals(0, vm.getDownloadedModelCountForRepo("repo-1", emptyMap()))
  }

  @Test
  fun countReturnsZeroWhenNoModelsMatchRepo() {
    val map = mapOf("model-a" to "repo-2", "model-b" to "repo-3")
    assertEquals(0, vm.getDownloadedModelCountForRepo("repo-1", map))
  }

  @Test
  fun countReturnsMatchingModelsForRepo() {
    val map = mapOf("model-a" to "repo-1", "model-b" to "repo-2", "model-c" to "repo-1")
    assertEquals(2, vm.getDownloadedModelCountForRepo("repo-1", map))
  }

  @Test
  fun countReturnsAllWhenAllBelongToRepo() {
    val map = mapOf("model-a" to "repo-1", "model-b" to "repo-1")
    assertEquals(2, vm.getDownloadedModelCountForRepo("repo-1", map))
  }

  // --- getDownloadingModelNamesForRepo ---

  @Test
  fun namesReturnsEmptyForEmptyMap() {
    assertTrue(vm.getDownloadingModelNamesForRepo("repo-1", emptyMap()).isEmpty())
  }

  @Test
  fun namesReturnsEmptyWhenNoModelsMatchRepo() {
    val map = mapOf("model-a" to "repo-2")
    assertTrue(vm.getDownloadingModelNamesForRepo("repo-1", map).isEmpty())
  }

  @Test
  fun namesReturnsMatchingModelNames() {
    val map = mapOf("model-a" to "repo-1", "model-b" to "repo-2", "model-c" to "repo-1")
    val result = vm.getDownloadingModelNamesForRepo("repo-1", map)
    assertEquals(2, result.size)
    assertTrue(result.contains("model-a"))
    assertTrue(result.contains("model-c"))
  }
}
