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
// Serializers now in same package (data) — no explicit import needed
import com.ollitert.llm.server.proto.BenchmarkResult
import com.ollitert.llm.server.proto.ImportedModel
import java.io.FileOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

class DataStoreRepositoryTest {

  /** DataStore atomic rename fails on Windows — skip multi-write tests there. */
  private fun assumeNotWindows() {
    assumeFalse(
      "DataStore File.renameTo() fails on Windows (NTFS file locking)",
      System.getProperty("os.name")?.lowercase()?.contains("win") == true,
    )
  }

  @Test
  fun writeOperationsUpdateSnapshotsImmediately() = runBlocking {
    val tempDir = createTempDirectory(prefix = "datastore-repo-test")
    try {
      val repository = createRepository(tempDir.toString())

      repository.saveTextInputHistory(listOf("hello", "world"))

      assertEquals(listOf("hello", "world"), repository.readTextInputHistory())
    } finally {
      tempDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun importedModelsAndTokenWritesRemainReadableFromSnapshots() {
    assumeNotWindows()
    runBlocking {
      val tempDir = createTempDirectory(prefix = "datastore-repo-test")
      try {
        val repository = createRepository(tempDir.toString())
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
      } finally {
        tempDir.toFile().deleteRecursively()
      }
    }
  }

  @Test
  fun benchmarkWritesAndDeletesUpdateSnapshotList() {
    assumeNotWindows()
    runBlocking {
      val tempDir = createTempDirectory(prefix = "datastore-repo-test")
      try {
        val repository = createRepository(tempDir.toString())
        val firstResult = BenchmarkResult.newBuilder().build()
        val secondResult = BenchmarkResult.newBuilder().build()

        repository.addBenchmarkResult(firstResult)
        repository.addBenchmarkResult(secondResult)

        assertEquals(listOf(secondResult, firstResult), repository.getAllBenchmarkResults())

        repository.deleteBenchmarkResult(index = 0)

        assertEquals(listOf(firstResult), repository.getAllBenchmarkResults())

        repository.setBenchmarkResults(listOf(secondResult))

        assertEquals(listOf(secondResult), repository.getAllBenchmarkResults())
      } finally {
        tempDir.toFile().deleteRecursively()
      }
    }
  }

  @Test
  fun readsPersistedSettingsBeforeCollectorWarmsSnapshots() = runBlocking {
    val tempDir = createTempDirectory(prefix = "datastore-repo-test")
    try {
      val basePath = Path.of(tempDir.toString())
      FileOutputStream(basePath.resolve("settings.pb").toFile()).use { output ->
        SettingsSerializer.writeTo(
          SettingsSerializer.defaultValue
            .toBuilder()
            .setIsTosAccepted(true)
            .addTextInputHistory("persisted")
            .build(),
          output,
        )
      }
      FileOutputStream(basePath.resolve("user-data.pb").toFile()).use { output ->
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
      FileOutputStream(basePath.resolve("benchmark-results.pb").toFile()).use { output ->
        BenchmarkResultsSerializer.writeTo(
          BenchmarkResultsSerializer.defaultValue
            .toBuilder()
            .addResult(BenchmarkResult.newBuilder().build())
            .build(),
          output,
        )
      }

      val repository = createRepository(tempDir.toString())

      assertTrue(repository.isOnboardingCompleted()) // reuses isTosAccepted
      assertEquals(listOf("persisted"), repository.readTextInputHistory())
      assertEquals("stored", repository.readAccessTokenData()?.accessToken)
      assertEquals(1, repository.getAllBenchmarkResults().size)
    } finally {
      tempDir.toFile().deleteRecursively()
    }
  }

  @Test
  fun onboardingCompletedPersists() = runBlocking {
    val tempDir = createTempDirectory(prefix = "datastore-repo-test")
    try {
      val repository = createRepository(tempDir.toString())

      assertFalse(repository.isOnboardingCompleted())
      repository.setOnboardingCompleted()
      assertTrue(repository.isOnboardingCompleted())
    } finally {
      tempDir.toFile().deleteRecursively()
    }
  }

  private fun createRepository(tempDir: String): DefaultDataStoreRepository {
    val basePath = Path.of(tempDir)
    val settingsPath = basePath.resolve("settings.pb")
    val userDataPath = basePath.resolve("user-data.pb")
    val benchmarkResultsPath = basePath.resolve("benchmark-results.pb")

    return DefaultDataStoreRepository(
      dataStore = DataStoreFactory.create(serializer = SettingsSerializer) { settingsPath.toFile() },
      userDataDataStore =
        DataStoreFactory.create(serializer = UserDataSerializer) { userDataPath.toFile() },
      benchmarkResultsDataStore =
        DataStoreFactory.create(serializer = BenchmarkResultsSerializer) {
          benchmarkResultsPath.toFile()
        },
    )
  }
}
