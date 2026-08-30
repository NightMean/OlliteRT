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

package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.data.allowlist.LoadResult
import com.ollitert.llm.server.data.allowlist.RefreshResult
import com.ollitert.llm.server.data.allowlist.RepositoryManager
import com.ollitert.llm.server.data.model.IMPORTS_DIR
import com.ollitert.llm.server.data.model.Repository
import com.ollitert.llm.server.data.repository.DownloadRepository
import com.ollitert.llm.server.data.repository.FakePreferencesRepository
import com.ollitert.llm.server.data.repository.ModelStorageRepository
import com.ollitert.llm.server.data.repository.ProtoDataStoreRepository
import com.ollitert.llm.server.data.repository.ServerStateRepository
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagerViewModelDisabledSourcesTest {
  private val testDispatcher = StandardTestDispatcher()
  private lateinit var externalFilesDir: File

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    externalFilesDir = createTempDirectory(prefix = "disabled-sources").toFile()
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
    externalFilesDir.deleteRecursively()
  }

  @Test
  fun importedModelsRemainVisibleWhenEveryRepositoryIsDisabled() = runTest(testDispatcher) {
    val imported = ImportedModel.newBuilder()
      .setFileName("Local-Model.litertlm")
      .setFileSize(4L)
      .setLlmConfig(
        LlmConfig.newBuilder()
          .addCompatibleAccelerators("CPU")
          .setDefaultMaxTokens(1_024)
          .build()
      )
      .build()
    File(externalFilesDir, "$IMPORTS_DIR/${imported.fileName}").apply {
      parentFile?.mkdirs()
      writeBytes(byteArrayOf(1, 2, 3, 4))
    }

    val context = mockk<Context>(relaxed = true)
    val protoRepository = mockk<ProtoDataStoreRepository>(relaxed = true)
    val repositoryManager = mockk<RepositoryManager>(relaxed = true)
    val storageRepository = mockk<ModelStorageRepository>(relaxed = true)
    val disabledOfficial = Repository(
      id = "official",
      url = "https://example.invalid/official.json",
      enabled = false,
      isBuiltIn = true,
      contentVersion = 1,
      lastRefreshMs = 0L,
      lastError = "",
      name = "Official",
      modelCount = 0,
    )
    every { context.getExternalFilesDir(null) } returns externalFilesDir
    every { storageRepository.readTestAllowlist() } returns null
    coEvery { protoRepository.isOnboardingCompleted() } returns true
    coEvery { protoRepository.readRepositories() } returns listOf(disabledOfficial)
    coEvery { protoRepository.readImportedModels() } returns listOf(imported)
    coEvery { repositoryManager.refreshAll(storageRepository) } returns RefreshResult(emptySet())
    coEvery {
      repositoryManager.loadAll(any(), storageRepository, ignoreDisabled = false, modelFilter = any())
    } returns LoadResult(models = emptyList(), repositories = listOf(disabledOfficial))
    coEvery {
      repositoryManager.loadAll(any(), storageRepository, ignoreDisabled = true, modelFilter = any())
    } returns LoadResult(models = emptyList(), repositories = listOf(disabledOfficial))

    val viewModel = ModelManagerViewModel(
      downloadRepository = mockk<DownloadRepository>(relaxed = true),
      protoDataStoreRepository = protoRepository,
      lifecycleProvider = mockk<OlliteRTLifecycleProvider>(relaxed = true),
      repositoryManager = repositoryManager,
      context = context,
      preferencesRepository = FakePreferencesRepository(),
      serverStateRepository = mockk<ServerStateRepository>(relaxed = true),
      modelStorageRepository = storageRepository,
      ioDispatcher = testDispatcher,
      mainDispatcher = testDispatcher,
    )

    viewModel.loadModelAllowlist()
    advanceUntilIdle()

    assertEquals(listOf("Local-Model"), viewModel.uiState.value.models.map { it.name })
    assertTrue(viewModel.uiState.value.allReposDisabled)
  }
}
