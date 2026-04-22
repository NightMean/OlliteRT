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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ollitert.llm.server.proto.BenchmarkResult
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmBenchmarkBasicInfo
import com.ollitert.llm.server.proto.LlmBenchmarkResult
import com.ollitert.llm.server.proto.LlmConfig
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DataStoreRepositoryTest {

  private lateinit var repo: DefaultDataStoreRepository
  private lateinit var testDir: File
  private lateinit var testScope: TestScope

  @Before
  fun setUp() {
    testScope = TestScope()
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    testDir = File(context.filesDir, "datastore-test-${System.nanoTime()}")
    testDir.mkdirs()

    val settingsStore = DataStoreFactory.create(
      serializer = SettingsSerializer,
      scope = testScope.backgroundScope,
    ) { File(testDir, "test_settings.pb") }

    val userDataStore = DataStoreFactory.create(
      serializer = UserDataSerializer,
      scope = testScope.backgroundScope,
    ) { File(testDir, "test_user_data.pb") }

    val benchmarkStore = DataStoreFactory.create(
      serializer = BenchmarkResultsSerializer,
      scope = testScope.backgroundScope,
    ) { File(testDir, "test_benchmarks.pb") }

    repo = DefaultDataStoreRepository(settingsStore, userDataStore, benchmarkStore)
  }

  @After
  fun tearDown() {
    testScope.cancel()
    testDir.deleteRecursively()
  }

  // --- Onboarding ---

  @Test
  fun onboardingDefaultsToIncomplete() = testScope.runTest {
    assertFalse(repo.isOnboardingCompleted())
  }

  @Test
  fun onboardingRoundTrip() = testScope.runTest {
    repo.setOnboardingCompleted()
    assertTrue(repo.isOnboardingCompleted())
  }

  // --- Imported Models ---

  @Test
  fun importedModelsDefaultsToEmpty() = testScope.runTest {
    assertTrue(repo.readImportedModels().isEmpty())
  }

  @Test
  fun saveAndReadImportedModels() = testScope.runTest {
    val models = listOf(
      ImportedModel.newBuilder()
        .setFileName("gemma-3-4b.litertlm")
        .setFileSize(2_500_000_000L)
        .setLlmConfig(
          LlmConfig.newBuilder()
            .addCompatibleAccelerators("GPU")
            .setDefaultMaxTokens(1024)
            .setDefaultTopk(40)
            .setDefaultTopp(0.95f)
            .setDefaultTemperature(0.7f)
            .setSupportImage(true)
            .setSupportAudio(false)
            .setSupportThinking(false)
            .build()
        )
        .build(),
      ImportedModel.newBuilder()
        .setFileName("gemma-4-12b.litertlm")
        .setFileSize(8_000_000_000L)
        .build(),
    )

    repo.saveImportedModels(models)
    val loaded = repo.readImportedModels()

    assertEquals(2, loaded.size)
    assertEquals("gemma-3-4b.litertlm", loaded[0].fileName)
    assertEquals(2_500_000_000L, loaded[0].fileSize)
    assertTrue(loaded[0].hasLlmConfig())
    assertEquals(1024, loaded[0].llmConfig.defaultMaxTokens)
    assertTrue(loaded[0].llmConfig.supportImage)
    assertEquals("gemma-4-12b.litertlm", loaded[1].fileName)
  }

  @Test
  fun updateImportedModelReplacesExisting() = testScope.runTest {
    val original = ImportedModel.newBuilder()
      .setFileName("model.litertlm")
      .setFileSize(1000)
      .build()
    repo.saveImportedModels(listOf(original))

    val updated = ImportedModel.newBuilder()
      .setFileName("model.litertlm")
      .setFileSize(2000)
      .setLlmConfig(LlmConfig.newBuilder().setDefaultMaxTokens(512).build())
      .build()
    repo.updateImportedModel("model.litertlm", updated)

    val loaded = repo.readImportedModels()
    assertEquals(1, loaded.size)
    assertEquals(2000, loaded[0].fileSize)
    assertEquals(512, loaded[0].llmConfig.defaultMaxTokens)
  }

  @Test
  fun updateImportedModelAddsIfNotFound() = testScope.runTest {
    repo.saveImportedModels(emptyList())
    val newModel = ImportedModel.newBuilder()
      .setFileName("new.litertlm")
      .setFileSize(500)
      .build()
    repo.updateImportedModel("new.litertlm", newModel)

    val loaded = repo.readImportedModels()
    assertEquals(1, loaded.size)
    assertEquals("new.litertlm", loaded[0].fileName)
  }

  // --- Access Token ---

  @Test
  fun accessTokenDefaultsToEmpty() = testScope.runTest {
    val token = repo.readAccessTokenData()
    // Proto3: unset message field returns default instance, not null
    assertTrue(token == null || token.accessToken.isEmpty())
  }

  @Test
  fun accessTokenRoundTrip() = testScope.runTest {
    repo.saveAccessTokenData("access-abc", "refresh-xyz", 1700000000000L)
    val token = repo.readAccessTokenData()!!

    assertEquals("access-abc", token.accessToken)
    assertEquals("refresh-xyz", token.refreshToken)
    assertEquals(1700000000000L, token.expiresAtMs)
  }

  @Test
  fun clearAccessTokenRemovesData() = testScope.runTest {
    repo.saveAccessTokenData("a", "r", 999)
    repo.clearAccessTokenData()
    val token = repo.readAccessTokenData()

    // Proto default: hasAccessTokenData() is false after clear, but the getter
    // still returns a default instance. Check the token string is empty.
    assertTrue(token == null || token.accessToken.isEmpty())
  }

  // --- Benchmark Results ---

  @Test
  fun benchmarkResultsDefaultsToEmpty() = testScope.runTest {
    assertTrue(repo.getAllBenchmarkResults().isEmpty())
  }

  @Test
  fun addBenchmarkResultInsertsAtFront() = testScope.runTest {
    val result1 = makeBenchmarkResult("model-a")
    val result2 = makeBenchmarkResult("model-b")

    repo.addBenchmarkResult(result1)
    repo.addBenchmarkResult(result2)

    val all = repo.getAllBenchmarkResults()
    assertEquals(2, all.size)
    assertEquals("model-b", all[0].llmResult.baiscInfo.modelName)
    assertEquals("model-a", all[1].llmResult.baiscInfo.modelName)
  }

  @Test
  fun deleteBenchmarkResultByIndex() = testScope.runTest {
    repo.addBenchmarkResult(makeBenchmarkResult("a"))
    repo.addBenchmarkResult(makeBenchmarkResult("b"))
    repo.addBenchmarkResult(makeBenchmarkResult("c"))

    repo.deleteBenchmarkResult(1) // delete "b" (index 1 after c=0, b=1, a=2)

    val remaining = repo.getAllBenchmarkResults()
    assertEquals(2, remaining.size)
    assertEquals("c", remaining[0].llmResult.baiscInfo.modelName)
    assertEquals("a", remaining[1].llmResult.baiscInfo.modelName)
  }

  @Test
  fun setBenchmarkResultsReplacesAll() = testScope.runTest {
    repo.addBenchmarkResult(makeBenchmarkResult("old"))
    repo.setBenchmarkResults(listOf(makeBenchmarkResult("new")))

    val all = repo.getAllBenchmarkResults()
    assertEquals(1, all.size)
    assertEquals("new", all[0].llmResult.baiscInfo.modelName)
  }

  // --- Benchmark Comparison Help ---

  @Test
  fun benchmarkComparisonHelpDefaultsToFalse() = testScope.runTest {
    assertFalse(repo.getHasSeenBenchmarkComparisonHelp())
  }

  @Test
  fun benchmarkComparisonHelpRoundTrip() = testScope.runTest {
    repo.setHasSeenBenchmarkComparisonHelp(true)
    assertTrue(repo.getHasSeenBenchmarkComparisonHelp())
    repo.setHasSeenBenchmarkComparisonHelp(false)
    assertFalse(repo.getHasSeenBenchmarkComparisonHelp())
  }

  private fun makeBenchmarkResult(modelName: String): BenchmarkResult =
    BenchmarkResult.newBuilder()
      .setLlmResult(
        LlmBenchmarkResult.newBuilder()
          .setBaiscInfo(
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
