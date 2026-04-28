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

package com.ollitert.llm.server.ui.benchmark

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.Repository
import com.ollitert.llm.server.proto.AccessTokenData
import com.ollitert.llm.server.proto.BenchmarkResult
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmBenchmarkBasicInfo
import com.ollitert.llm.server.proto.LlmBenchmarkResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BenchmarkViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var fakeRepo: FakeDataStoreRepository
  private lateinit var vm: BenchmarkViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    fakeRepo = FakeDataStoreRepository()
    mockkStatic(Log::class)
    every { Log.d(any(), any()) } returns 0
    every { Log.e(any(), any()) } returns 0
    every { Log.e(any(), any(), any()) } returns 0
    every { Log.w(any<String>(), any<String>()) } returns 0
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    unmockkStatic(Log::class)
  }

  private fun createVm() {
    vm = BenchmarkViewModel(
      appContext = mockk<Context>(relaxed = true),
      dataStoreRepository = fakeRepo,
      ioDispatcher = testDispatcher,
    )
  }

  private fun createVmWithResults(vararg modelNames: String) {
    createVm()
    vm.setBenchmarkResults(modelNames.map { makeResult(it) })
  }

  // --- Init ---

  @Test
  fun initLoadsResultsFromRepository() = runTest(testDispatcher) {
    fakeRepo.storedResults.addAll(listOf(makeResult("a"), makeResult("b")))
    createVm()
    advanceUntilIdle()

    val state = vm.uiState.value
    assertEquals(2, state.results.size)
    assertFalse(state.results[0].expanded)
    assertFalse(state.results[1].expanded)
  }

  @Test
  fun initWithEmptyRepositoryStartsClean() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    assertTrue(vm.uiState.value.results.isEmpty())
    assertFalse(vm.uiState.value.running)
    assertFalse(vm.uiState.value.showResultsViewer)
  }

  // --- Add ---

  @Test
  fun addBenchmarkResultInsertsAtFront() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    val result = makeResult("new-model")
    val id = vm.addBenchmarkResult(result)

    val state = vm.uiState.value
    assertEquals(1, state.results.size)
    assertEquals(id, state.results[0].id)
    assertEquals("new-model", state.results[0].benchmarkResult.llmResult.basicInfo.modelName)
  }

  @Test
  fun addMultipleResultsPrependsEach() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    vm.addBenchmarkResult(makeResult("first"))
    vm.addBenchmarkResult(makeResult("second"))

    val names = vm.uiState.value.results.map { it.benchmarkResult.llmResult.basicInfo.modelName }
    assertEquals(listOf("second", "first"), names)
  }

  // --- Delete ---

  @Test
  fun deleteBenchmarkResultRemovesById() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    val id1 = vm.addBenchmarkResult(makeResult("a"))
    val id2 = vm.addBenchmarkResult(makeResult("b"))

    vm.deleteBenchmarkResult(id1)

    assertEquals(1, vm.uiState.value.results.size)
    assertEquals(id2, vm.uiState.value.results[0].id)
  }

  @Test
  fun deleteBaselineResultClearsBaseline() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    val id = vm.addBenchmarkResult(makeResult("baseline"))
    vm.setBaseline(id)
    assertEquals(id, vm.uiState.value.baselineResult?.id)

    vm.deleteBenchmarkResult(id)
    assertNull(vm.uiState.value.baselineResult)
  }

  @Test
  fun deleteNonExistentIdIsNoOp() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    vm.addBenchmarkResult(makeResult("keep"))
    vm.deleteBenchmarkResult("nonexistent")

    assertEquals(1, vm.uiState.value.results.size)
  }

  // --- Baseline ---

  @Test
  fun setBaselineToggles() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    val id = vm.addBenchmarkResult(makeResult("model"))

    vm.setBaseline(id)
    assertEquals(id, vm.uiState.value.baselineResult?.id)

    vm.setBaseline(id)
    assertNull(vm.uiState.value.baselineResult)
  }

  @Test
  fun setBaselineSwitchesToNewResult() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    val id1 = vm.addBenchmarkResult(makeResult("a"))
    val id2 = vm.addBenchmarkResult(makeResult("b"))

    vm.setBaseline(id1)
    assertEquals(id1, vm.uiState.value.baselineResult?.id)

    vm.setBaseline(id2)
    assertEquals(id2, vm.uiState.value.baselineResult?.id)
  }

  @Test
  fun clearBaselineRemoves() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    val id = vm.addBenchmarkResult(makeResult("m"))

    vm.setBaseline(id)
    vm.clearBaseline()
    assertNull(vm.uiState.value.baselineResult)
  }

  // --- Expand / Collapse ---

  @Test
  fun expandAllExpandsEveryResult() = runTest(testDispatcher) {
    createVmWithResults("a", "b")

    vm.expandAll()
    assertTrue(vm.uiState.value.results.all { it.expanded && it.basicInfoExpanded && it.statsExpanded })
  }

  @Test
  fun collapseAllCollapsesEveryResult() = runTest(testDispatcher) {
    createVmWithResults("a")

    vm.expandAll()
    vm.collapseAll()
    assertTrue(vm.uiState.value.results.all { !it.expanded && !it.basicInfoExpanded && !it.statsExpanded })
  }

  @Test
  fun setExpandedTogglesOneResult() = runTest(testDispatcher) {
    createVmWithResults("a", "b")

    val id = vm.uiState.value.results[0].id
    vm.setExpanded(id, true)

    assertTrue(vm.uiState.value.results[0].expanded)
    assertFalse(vm.uiState.value.results[1].expanded)
  }

  @Test
  fun setBasicInfoExpandedUpdatesOnly() = runTest(testDispatcher) {
    createVmWithResults("a")

    val id = vm.uiState.value.results[0].id
    vm.setBasicInfoExpanded(id, true)
    assertTrue(vm.uiState.value.results[0].basicInfoExpanded)

    vm.setBasicInfoExpanded(id, false)
    assertFalse(vm.uiState.value.results[0].basicInfoExpanded)
  }

  // --- Aggregation ---

  @Test
  fun setAggregationUpdatesResult() = runTest(testDispatcher) {
    createVmWithResults("a")

    val id = vm.uiState.value.results[0].id
    vm.setAggregation(id, Aggregation.MEDIAN)
    assertEquals(Aggregation.MEDIAN, vm.uiState.value.results[0].aggregation)
  }

  @Test
  fun setAggregationAlsoUpdatesBaseline() = runTest(testDispatcher) {
    createVmWithResults("a")

    val id = vm.uiState.value.results[0].id
    vm.setBaseline(id)
    vm.setAggregation(id, Aggregation.MAX)

    assertEquals(Aggregation.MAX, vm.uiState.value.baselineResult?.aggregation)
  }

  // --- UI State ---

  @Test
  fun setRunningUpdatesState() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    vm.setRunning(true)
    assertTrue(vm.uiState.value.running)

    vm.setRunning(false)
    assertFalse(vm.uiState.value.running)
  }

  @Test
  fun runProgressUpdates() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    vm.setTotalRunCount(5)
    vm.setRunProgress(3)
    assertEquals(5, vm.uiState.value.totalRunCount)
    assertEquals(3, vm.uiState.value.completedRunCount)
  }

  @Test
  fun dismissServerConflictWarning() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    vm.dismissServerConflictWarning()
    assertFalse(vm.uiState.value.serverConflictWarning)
  }

  @Test
  fun dismissErrorClearsMessage() = runTest(testDispatcher) {
    createVm()
    advanceUntilIdle()

    vm.dismissError()
    assertNull(vm.uiState.value.errorMessage)
  }

  // --- Helpers ---

  private fun makeResult(modelName: String): BenchmarkResult =
    BenchmarkResult.newBuilder()
      .setLlmResult(
        LlmBenchmarkResult.newBuilder()
          .setBasicInfo(
            LlmBenchmarkBasicInfo.newBuilder()
              .setModelName(modelName)
              .setStartMs(1000)
              .setEndMs(2000)
              .setAccelerator("GPU")
              .setPrefillTokens(128)
              .setDecodeTokens(256)
              .setNumberOfRuns(3)
              .setAppVersion("1.0.0")
              .build()
          )
          .build()
      )
      .build()
}

class FakeDataStoreRepository : DataStoreRepository {
  val storedResults = mutableListOf<BenchmarkResult>()
  val addedResults = mutableListOf<BenchmarkResult>()
  val deletedIndices = mutableListOf<Int>()

  override suspend fun getAllBenchmarkResults() = storedResults.toList()
  override suspend fun addBenchmarkResult(result: BenchmarkResult) { addedResults.add(result) }
  override suspend fun deleteBenchmarkResult(index: Int) { deletedIndices.add(index) }
  override suspend fun setBenchmarkResults(results: List<BenchmarkResult>) { storedResults.clear(); storedResults.addAll(results) }
  override suspend fun setHasSeenBenchmarkComparisonHelp(seen: Boolean) {}
  override suspend fun getHasSeenBenchmarkComparisonHelp() = false
  override suspend fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long) {}
  override suspend fun clearAccessTokenData() {}
  override suspend fun readAccessTokenData(): AccessTokenData? = null
  override suspend fun saveImportedModels(importedModels: List<ImportedModel>) {}
  override suspend fun readImportedModels(): List<ImportedModel> = emptyList()
  override suspend fun updateImportedModel(fileName: String, updatedModel: ImportedModel) {}
  override suspend fun isOnboardingCompleted() = true
  override suspend fun setOnboardingCompleted() {}
  override suspend fun readRepositories(): List<Repository> = emptyList()
  override suspend fun addRepository(repo: Repository) {}
  override suspend fun seedRepositoryIfAbsent(repo: Repository) {}
  override suspend fun updateRepository(repo: Repository) {}
  override suspend fun toggleRepositoryEnabled(id: String, enabled: Boolean) {}
  override suspend fun removeRepository(id: String) {}
  override suspend fun resetRepositories() {}
}
