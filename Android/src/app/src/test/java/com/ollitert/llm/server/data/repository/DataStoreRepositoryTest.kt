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

package com.ollitert.llm.server.data.repository

import androidx.datastore.core.DataStore
import com.ollitert.llm.server.proto.BenchmarkResult
import com.ollitert.llm.server.proto.BenchmarkResults
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic unit-test DataStore that exercises repository transforms without filesystem
 * semantics. Android's real file-backed DataStore is covered by the instrumented counterpart.
 */
private class InMemoryDataStore<T>(initialValue: T) : DataStore<T> {
  private val mutex = Mutex()
  private val state = MutableStateFlow(initialValue)

  override val data: Flow<T> = state

  override suspend fun updateData(transform: suspend (t: T) -> T): T = mutex.withLock {
    transform(state.value).also { state.value = it }
  }
}

class DataStoreRepositoryTest {

  private lateinit var repository: DefaultDataStoreRepository

  @Before
  fun setUp() {
    repository = DefaultDataStoreRepository(
      dataStore = InMemoryDataStore(Settings.getDefaultInstance()),
      benchmarkResultsDataStore = InMemoryDataStore(BenchmarkResults.getDefaultInstance()),
    )
  }

  @Test
  fun importedModelWritesRemainReadableFromSnapshots() = runTest {
    val importedModel =
      ImportedModel.newBuilder().setFileName("demo.litertlm").setFileSize(42L).build()

    repository.saveImportedModels(listOf(importedModel))
    assertEquals(listOf(importedModel), repository.readImportedModels())
  }

  @Test
  fun benchmarkWritesAndDeletesUpdateSnapshotList() = runTest {
    val firstResult = BenchmarkResult.newBuilder().setId("first").build()
    val secondResult = BenchmarkResult.newBuilder().setId("second").build()

    repository.addBenchmarkResult(firstResult)
    repository.addBenchmarkResult(secondResult)

    assertEquals(listOf(secondResult, firstResult), repository.getAllBenchmarkResults())

    repository.deleteBenchmarkResult(id = "second")

    assertEquals(listOf(firstResult), repository.getAllBenchmarkResults())

    repository.setBenchmarkResults(listOf(secondResult))

    assertEquals(listOf(secondResult), repository.getAllBenchmarkResults())
  }

  @Test
  fun legacyBenchmarkResultsReceiveStableIdsOnRead() = runTest {
    val freshRepo = DefaultDataStoreRepository(
      dataStore = InMemoryDataStore(Settings.getDefaultInstance()),
      benchmarkResultsDataStore = InMemoryDataStore(
        BenchmarkResults.newBuilder().addResult(BenchmarkResult.newBuilder().build()).build()
      ),
    )

    val firstRead = freshRepo.getAllBenchmarkResults().single()
    val secondRead = freshRepo.getAllBenchmarkResults().single()

    assertTrue(firstRead.id.isNotBlank())
    assertEquals(firstRead.id, secondRead.id)
  }

  @Test
  fun readsSeededSnapshotsBeforeAnyCollectorStarts() = runTest {
    val freshRepo = DefaultDataStoreRepository(
      dataStore = InMemoryDataStore(
        Settings.newBuilder().setIsTosAccepted(true).build()
      ),
      benchmarkResultsDataStore = InMemoryDataStore(
        BenchmarkResults.newBuilder().addResult(BenchmarkResult.newBuilder().build()).build()
      ),
    )

    assertTrue(freshRepo.isOnboardingCompleted())
    assertEquals(1, freshRepo.getAllBenchmarkResults().size)
  }

  @Test
  fun onboardingCompletedRoundTrips() = runTest {
    assertFalse(repository.isOnboardingCompleted())
    repository.setOnboardingCompleted()
    assertTrue(repository.isOnboardingCompleted())
  }
}
