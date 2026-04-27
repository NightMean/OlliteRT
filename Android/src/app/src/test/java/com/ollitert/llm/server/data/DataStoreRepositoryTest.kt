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

import androidx.datastore.core.DataStoreFactory
import com.ollitert.llm.server.proto.BenchmarkResult
import com.ollitert.llm.server.proto.ImportedModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlin.io.path.createTempDirectory

class DataStoreRepositoryTest {

  private lateinit var repository: DefaultDataStoreRepository
  private lateinit var tempDir: File
  private lateinit var testScope: TestScope

  @Before
  fun setUp() {
    testScope = TestScope()
    tempDir = createTempDirectory(prefix = "datastore-repo-test").toFile()

    repository = DefaultDataStoreRepository(
      dataStore = DataStoreFactory.create(
        serializer = SettingsSerializer,
        scope = testScope.backgroundScope,
      ) { File(tempDir, "settings.pb") },
      userDataDataStore = DataStoreFactory.create(
        serializer = UserDataSerializer,
        scope = testScope.backgroundScope,
      ) { File(tempDir, "user-data.pb") },
      benchmarkResultsDataStore = DataStoreFactory.create(
        serializer = BenchmarkResultsSerializer,
        scope = testScope.backgroundScope,
      ) { File(tempDir, "benchmark-results.pb") },
    )
  }

  @After
  fun tearDown() {
    testScope.cancel()
    tempDir.deleteRecursively()
  }

  @Test
  fun importedModelsAndTokenWritesRemainReadableFromSnapshots() = testScope.runTest {
    val importedModel =
      ImportedModel.newBuilder().setFileName("demo.litertlm").setFileSize(42L).build()

    repository.saveImportedModels(listOf(importedModel))
    repository.saveAccessTokenData(
      accessToken = "access",
      refreshToken = "refresh",
      expiresAt = 1234L,
    )

    assertEquals(listOf(importedModel), repository.readImportedModels())
    assertEquals("access", repository.readAccessTokenData()?.accessToken)

    repository.clearAccessTokenData()

    assertTrue(repository.readAccessTokenData()?.accessToken.orEmpty().isEmpty())
  }

  @Test
  fun benchmarkWritesAndDeletesUpdateSnapshotList() = testScope.runTest {
    val firstResult = BenchmarkResult.newBuilder().build()
    val secondResult = BenchmarkResult.newBuilder().build()

    repository.addBenchmarkResult(firstResult)
    repository.addBenchmarkResult(secondResult)

    assertEquals(listOf(secondResult, firstResult), repository.getAllBenchmarkResults())

    repository.deleteBenchmarkResult(index = 0)

    assertEquals(listOf(firstResult), repository.getAllBenchmarkResults())

    repository.setBenchmarkResults(listOf(secondResult))

    assertEquals(listOf(secondResult), repository.getAllBenchmarkResults())
  }

  @Test
  fun readsPersistedSettingsBeforeCollectorWarmsSnapshots() = runBlocking {
    // This test creates pre-seeded proto files and reads them with a fresh DataStore,
    // verifying that snapshot reads work before any Flow collector warms the cache.
    val freshDir = createTempDirectory(prefix = "datastore-repo-pre-seed").toFile()
    try {
      FileOutputStream(File(freshDir, "settings.pb")).use { output ->
        SettingsSerializer.writeTo(
          SettingsSerializer.defaultValue
            .toBuilder()
            .setIsTosAccepted(true)
            .build(),
          output,
        )
      }
      FileOutputStream(File(freshDir, "user-data.pb")).use { output ->
        UserDataSerializer.writeTo(
          UserDataSerializer.defaultValue
            .toBuilder()
            .setAccessTokenData(
              com.ollitert.llm.server.proto.AccessTokenData.newBuilder()
                .setAccessToken("stored")
                .build()
            )
            .build(),
          output,
        )
      }
      FileOutputStream(File(freshDir, "benchmark-results.pb")).use { output ->
        BenchmarkResultsSerializer.writeTo(
          BenchmarkResultsSerializer.defaultValue
            .toBuilder()
            .addResult(BenchmarkResult.newBuilder().build())
            .build(),
          output,
        )
      }

      val freshScope = TestScope()
      val freshRepo = DefaultDataStoreRepository(
        dataStore = DataStoreFactory.create(
          serializer = SettingsSerializer,
          scope = freshScope.backgroundScope,
        ) { File(freshDir, "settings.pb") },
        userDataDataStore = DataStoreFactory.create(
          serializer = UserDataSerializer,
          scope = freshScope.backgroundScope,
        ) { File(freshDir, "user-data.pb") },
        benchmarkResultsDataStore = DataStoreFactory.create(
          serializer = BenchmarkResultsSerializer,
          scope = freshScope.backgroundScope,
        ) { File(freshDir, "benchmark-results.pb") },
      )

      freshScope.runTest {
        assertTrue(freshRepo.isOnboardingCompleted())
        assertEquals("stored", freshRepo.readAccessTokenData()?.accessToken)
        assertEquals(1, freshRepo.getAllBenchmarkResults().size)
      }

      freshScope.cancel()
    } finally {
      freshDir.deleteRecursively()
    }
  }

  @Test
  fun onboardingCompletedPersists() = testScope.runTest {
    assertFalse(repository.isOnboardingCompleted())
    repository.setOnboardingCompleted()
    assertTrue(repository.isOnboardingCompleted())
  }
}
