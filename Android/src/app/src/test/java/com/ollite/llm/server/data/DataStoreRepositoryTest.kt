package com.ollite.llm.server.data

import androidx.datastore.core.DataStoreFactory
import com.ollite.llm.server.BenchmarkResultsSerializer
import com.ollite.llm.server.SettingsSerializer
import com.ollite.llm.server.UserDataSerializer
import com.ollite.llm.server.proto.BenchmarkResult
import com.ollite.llm.server.proto.ImportedModel
import java.io.FileOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreRepositoryTest {
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
  fun importedModelsAndTokenWritesRemainReadableFromSnapshots() = runBlocking {
    val tempDir = createTempDirectory(prefix = "datastore-repo-test")
    try {
      val repository = createRepository(tempDir.toString())
      val importedModel = ImportedModel.newBuilder().setFileName("demo.task").setFileSize(42L).build()

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

  @Test
  fun benchmarkWritesAndDeletesUpdateSnapshotList() = runBlocking {
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
              com.ollite.llm.server.proto.AccessTokenData.newBuilder()
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
