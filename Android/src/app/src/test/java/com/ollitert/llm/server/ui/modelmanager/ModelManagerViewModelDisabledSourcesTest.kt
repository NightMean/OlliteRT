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
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.allowlist.LoadResult
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.allowlist.RefreshResult
import com.ollitert.llm.server.data.allowlist.RepositoryManager
import com.ollitert.llm.server.data.model.IMPORTS_DIR
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.model.Repository
import com.ollitert.llm.server.data.repository.DefaultModelStorageRepository
import com.ollitert.llm.server.data.repository.DownloadRepository
import com.ollitert.llm.server.data.repository.FakePreferencesRepository
import com.ollitert.llm.server.data.repository.ImportedModelRecoveryException
import com.ollitert.llm.server.data.repository.ModelDownloadWork
import com.ollitert.llm.server.data.repository.ModelStorageRepository
import com.ollitert.llm.server.data.repository.ProtoDataStoreRepository
import com.ollitert.llm.server.data.repository.ServerStateRepository
import com.ollitert.llm.server.data.storage.TMP_FILE_EXT
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmConfig
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
  fun catalogRemainsUsableAndRetryRestoresImportsAfterRecoveryFailure() = runTest(testDispatcher) {
    val model = Model(name = "Catalog model")
    val local = ImportedModel.newBuilder()
      .setFileName("Local.litertlm")
      .setLlmConfig(LlmConfig.newBuilder().addCompatibleAccelerators("CPU"))
      .build()
    val recoveryMessage = "Could not recover local imports"
    val catalogStatus = ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)
    val context = mockk<Context>(relaxed = true) {
      every { getExternalFilesDir(null) } returns externalFilesDir
      every { getString(R.string.imported_model_recovery_error) } returns recoveryMessage
    }
    val protoRepository = mockk<ProtoDataStoreRepository>(relaxed = true) {
      coEvery { readExistingImportedModels() } throws ImportedModelRecoveryException(IOException("Unavailable journal"))
    }
    val storage = mockk<ModelStorageRepository>(relaxed = true) {
      every { readTestAllowlist() } returns null
      every { getModelDownloadStatus(model) } returns catalogStatus
    }
    val manager = mockk<RepositoryManager>(relaxed = true) {
      coEvery { refreshAll(storage) } returns RefreshResult(emptySet())
      coEvery { loadAll(any(), storage, ignoreDisabled = false, modelFilter = any()) } returns
        LoadResult(models = listOf(model), repositories = emptyList())
    }
    val viewModel = ModelManagerViewModel(
      downloadRepository = mockk(relaxed = true) {
        every { getModelDownloadWork() } returns ModelDownloadWork()
      },
      protoDataStoreRepository = protoRepository,
      lifecycleProvider = mockk(relaxed = true),
      repositoryManager = manager,
      context = context,
      preferencesRepository = FakePreferencesRepository(),
      serverStateRepository = mockk(relaxed = true),
      modelStorageRepository = storage,
      ioDispatcher = testDispatcher,
      mainDispatcher = testDispatcher,
    )

    viewModel.loadModelAllowlist()
    advanceUntilIdle()
    assertEquals(listOf(model.name), viewModel.uiState.value.models.map { it.name })
    assertEquals(catalogStatus, viewModel.uiState.value.modelDownloadStatus[model.name])
    assertEquals(recoveryMessage, viewModel.uiState.value.loadingModelAllowlistError)

    coEvery { protoRepository.readExistingImportedModels() } returns listOf(local)
    viewModel.loadModelAllowlist(isManualRetry = true)
    advanceUntilIdle()
    assertEquals(listOf(model.name, "Local"), viewModel.uiState.value.models.map { it.name })
    assertEquals("", viewModel.uiState.value.loadingModelAllowlistError)
  }

  @Test
  fun restoresTheModelScopeAccessFailureBeforeResumingPartialFiles() = runTest(testDispatcher) {
    val model = Model(name = "Catalog model")
    val partial = ModelDownloadStatus(ModelDownloadStatusType.PARTIALLY_DOWNLOADED, receivedBytes = 40L, totalBytes = 100L)
    val failure = ModelDownloadStatus(ModelDownloadStatusType.FAILED, errorMessage = "Access denied",
      modelScopeAccessDeniedSite = ModelScopeSite.CHINA.name, failureId = "work-id")
    val downloads = mockk<DownloadRepository>(relaxed = true) {
      every { getModelDownloadWork() } returns ModelDownloadWork(accessDenied = mapOf(model.name to failure))
    }
    val context = mockk<Context>(relaxed = true) {
      every { getExternalFilesDir(null) } returns externalFilesDir
    }
    val storage = mockk<ModelStorageRepository>(relaxed = true) {
      every { readTestAllowlist() } returns null
      every { getModelDownloadStatus(model) } returns partial
    }
    val manager = mockk<RepositoryManager>(relaxed = true) {
      coEvery { refreshAll(storage) } returns RefreshResult(emptySet())
      coEvery { loadAll(any(), storage, ignoreDisabled = false, modelFilter = any()) } returns
        LoadResult(models = listOf(model), repositories = emptyList())
    }
    val preferences = FakePreferencesRepository()
    fun newOwner() = ModelManagerViewModel(
      downloadRepository = downloads,
      protoDataStoreRepository = mockk(relaxed = true),
      lifecycleProvider = mockk(relaxed = true),
      repositoryManager = manager,
      context = context,
      preferencesRepository = preferences,
      serverStateRepository = mockk(relaxed = true),
      modelStorageRepository = storage,
      ioDispatcher = testDispatcher,
      mainDispatcher = testDispatcher,
    )
    val owner = newOwner()
    owner.loadModelAllowlist()
    advanceUntilIdle()
    val expected = failure.copy(receivedBytes = 40L, totalBytes = 100L)
    assertEquals(expected, owner.uiState.value.modelDownloadStatus[model.name])
    assertEquals(expected, nextModelScopeAccessFailure(owner.uiState.value.modelDownloadStatus.values, emptyList()))
    owner.acknowledgeModelScopeFailure(failure.failureId)
    val recreated = newOwner()
    recreated.loadModelAllowlist()
    advanceUntilIdle()
    assertEquals(expected, recreated.uiState.value.modelDownloadStatus[model.name])
    assertEquals(null, nextModelScopeAccessFailure(
      recreated.uiState.value.modelDownloadStatus.values, recreated.acknowledgedModelScopeFailures.value,
    ))

    val complete = ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)
    every { storage.getModelDownloadStatus(model) } returns complete
    recreated.loadModelAllowlist()
    advanceUntilIdle()
    assertEquals(complete, recreated.uiState.value.modelDownloadStatus[model.name])

    every { storage.getModelDownloadStatus(model) } returns partial
    every { downloads.getModelDownloadWork() } returns ModelDownloadWork(canceledModels = setOf(model.name))
    val afterCancellation = newOwner()
    afterCancellation.loadModelAllowlist()
    advanceUntilIdle()
    assertEquals(ModelDownloadStatusType.FAILED, afterCancellation.uiState.value.modelDownloadStatus[model.name]?.status)
    assertEquals(partial.receivedBytes, afterCancellation.uiState.value.modelDownloadStatus[model.name]?.receivedBytes)

    val workerId = UUID.randomUUID()
    every { downloads.getModelDownloadWork() } returns ModelDownloadWork(activeWorkers = mapOf(model.name to workerId))
    every { downloads.observeWorkerProgress(workerId, any()) } returns flow {
      every { downloads.getModelDownloadWork() } returns ModelDownloadWork(canceledModels = setOf(model.name))
      emit(ModelDownloadStatus(ModelDownloadStatusType.NOT_DOWNLOADED))
    }
    afterCancellation.loadModelAllowlist()
    advanceUntilIdle()
    assertEquals(ModelDownloadStatusType.FAILED, afterCancellation.uiState.value.modelDownloadStatus[model.name]?.status)
    assertEquals(partial.receivedBytes, afterCancellation.uiState.value.modelDownloadStatus[model.name]?.receivedBytes)
    verify(exactly = 0) { storage.deleteFileFromExternalFilesDir(any()) }
    verify(exactly = 0) { downloads.cancelDownloads(any(), any()) }
  }

  @Test
  fun confirmedFileDeletionRechecksLiveServerStateAndKeepsCatalogSettings() = runTest(testDispatcher) {
    val model = Model(name = "Catalog model", downloadFileName = "model.litertlm", version = "revision")
    val context = mockk<Context>(relaxed = true) {
      every { getExternalFilesDir(null) } returns externalFilesDir
    }
    val artifact = File(model.getPath(context)).apply { parentFile?.mkdirs(); writeText("model") }
    val realStorage = DefaultModelStorageRepository(context)
    val storage = mockk<ModelStorageRepository>(relaxed = true) {
      every { readTestAllowlist() } returns null
      every { getModelDownloadStatus(any()) } answers { realStorage.getModelDownloadStatus(firstArg()) }
      every { deleteDirFromExternalFilesDir(any()) } answers { realStorage.deleteDirFromExternalFilesDir(firstArg()) }
    }
    val manager = mockk<RepositoryManager>(relaxed = true) {
      coEvery { refreshAll(storage) } returns RefreshResult(emptySet())
      coEvery { loadAll(any(), storage, ignoreDisabled = false, modelFilter = any()) } returns
        LoadResult(models = listOf(model), repositories = emptyList())
    }
    val liveStatus = MutableStateFlow(ServerStatus.STOPPED)
    val liveModel = MutableStateFlow<String?>(null)
    val server = mockk<ServerStateRepository>(relaxed = true) {
      every { status } returns liveStatus
      every { activeModelName } returns liveModel
    }
    val downloads = mockk<DownloadRepository>(relaxed = true) {
      every { getModelDownloadWork() } returns ModelDownloadWork()
      every { hasUnfinishedDownload(model.name) } returns false
    }
    val preferences = FakePreferencesRepository().apply {
      setDefaultModelName(model.name)
      setModelFavorite(model.name, true)
    }
    val owner = ModelManagerViewModel(
      downloadRepository = downloads,
      protoDataStoreRepository = mockk(relaxed = true),
      lifecycleProvider = mockk(relaxed = true),
      repositoryManager = manager,
      context = context,
      preferencesRepository = preferences,
      serverStateRepository = server,
      modelStorageRepository = storage,
      ioDispatcher = testDispatcher,
      mainDispatcher = testDispatcher,
    )
    owner.loadModelAllowlist()
    advanceUntilIdle()
    assertEquals(ModelDownloadStatusType.SUCCEEDED, owner.uiState.value.modelDownloadStatus[model.name]?.status)

    // The user can have opened the dialog while the server was still stopped.
    liveModel.value = model.name
    liveStatus.value = ServerStatus.LOADING
    owner.deleteModelAndRefreshStorage(model)
    advanceUntilIdle()
    assertTrue(artifact.isFile)

    model.updatable = true
    owner.downloadModel(model)
    advanceUntilIdle()
    assertTrue(artifact.isFile)
    model.updatable = false

    liveStatus.value = ServerStatus.STOPPED
    owner.cancelDownloadModel(model)
    advanceUntilIdle()
    assertTrue(artifact.isFile)

    every { downloads.hasUnfinishedDownload(model.name) } returns true
    owner.deleteModelAndRefreshStorage(model)
    advanceUntilIdle()
    assertTrue(artifact.isFile)

    every { downloads.hasUnfinishedDownload(model.name) } returns false
    every { storage.deleteDirFromExternalFilesDir(model.normalizedName) } returns false
    owner.deleteModelAndRefreshStorage(model)
    advanceUntilIdle()
    assertTrue(artifact.isFile)
    assertEquals(ModelDownloadStatusType.SUCCEEDED, owner.uiState.value.modelDownloadStatus[model.name]?.status)

    every { storage.deleteDirFromExternalFilesDir(model.normalizedName) } answers {
      realStorage.deleteDirFromExternalFilesDir(firstArg())
    }
    owner.deleteModelAndRefreshStorage(model)
    advanceUntilIdle()
    assertFalse(artifact.exists())
    assertEquals(ModelDownloadStatusType.NOT_DOWNLOADED, owner.uiState.value.modelDownloadStatus[model.name]?.status)
    assertEquals(listOf(model.name), owner.uiState.value.models.map { it.name })
    assertEquals(setOf(model.name), preferences.getFavoriteModels())
    assertEquals(model.name, preferences.getDefaultModelName())
    val temporary = File(model.getPath(context, "${model.downloadFileName}.$TMP_FILE_EXT"))
      .apply { parentFile?.mkdirs(); writeText("partial") }
    owner.cancelDownloadModel(model)
    advanceUntilIdle()
    assertFalse(temporary.exists())
    verify(exactly = 2) { downloads.cancelUnfinishedModelDownload(model.name) }
  }

  @Test
  fun localModelsRemainVisibleWhenTheCatalogIsUnavailable() = runTest(testDispatcher) {
    val local = ImportedModel.newBuilder()
      .setFileName("Local.litertlm")
      .setLlmConfig(LlmConfig.newBuilder().addCompatibleAccelerators("CPU"))
      .build()
    val warning = "Could not load the catalog"
    val context = mockk<Context>(relaxed = true) {
      every { getExternalFilesDir(null) } returns externalFilesDir
      every { getString(R.string.error_model_list_load_failed_detail, any()) } returns warning
    }
    val storage = mockk<ModelStorageRepository>(relaxed = true) {
      every { readTestAllowlist() } returns null
    }
    val catalog = mockk<RepositoryManager>(relaxed = true) {
      coEvery { refreshAll(storage) } throws IOException("Catalog unavailable")
    }
    val owner = ModelManagerViewModel(
      downloadRepository = mockk(relaxed = true) { every { getModelDownloadWork() } returns ModelDownloadWork() },
      protoDataStoreRepository = mockk(relaxed = true) {
        coEvery { readExistingImportedModels() } returns listOf(local)
      },
      lifecycleProvider = mockk(relaxed = true),
      repositoryManager = catalog,
      context = context,
      preferencesRepository = FakePreferencesRepository(),
      serverStateRepository = mockk(relaxed = true),
      modelStorageRepository = storage,
      ioDispatcher = testDispatcher,
      mainDispatcher = testDispatcher,
    )
    owner.loadModelAllowlist()
    advanceUntilIdle()
    assertEquals(listOf("Local"), owner.uiState.value.models.map { it.name })
    assertEquals(warning, owner.uiState.value.loadingModelAllowlistError)

    val availableModel = Model(name = "Online catalog")
    every { storage.getModelDownloadStatus(availableModel) } returns ModelDownloadStatus(ModelDownloadStatusType.SUCCEEDED)
    coEvery { catalog.refreshAll(storage) } returns RefreshResult(emptySet())
    coEvery { catalog.loadAll(any(), storage, ignoreDisabled = false, modelFilter = any()) } returns
      LoadResult(models = listOf(availableModel), repositories = emptyList())
    owner.loadModelAllowlist()
    advanceUntilIdle()
    assertEquals(listOf(availableModel.name, "Local"), owner.uiState.value.models.map { it.name })

    coEvery { catalog.refreshAll(storage) } throws IOException("Catalog unavailable again")
    every { storage.getModelDownloadStatus(availableModel) } returns ModelDownloadStatus(ModelDownloadStatusType.NOT_DOWNLOADED)
    owner.loadModelAllowlist()
    advanceUntilIdle()
    assertEquals(listOf(availableModel.name, "Local"), owner.uiState.value.models.map { it.name })
    assertEquals(ModelDownloadStatusType.NOT_DOWNLOADED, owner.uiState.value.modelDownloadStatus[availableModel.name]?.status)
    assertEquals(warning, owner.uiState.value.loadingModelAllowlistError)
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
    coEvery { protoRepository.readExistingImportedModels() } returns listOf(imported)
    coEvery { repositoryManager.refreshAll(storageRepository) } returns RefreshResult(emptySet())
    coEvery {
      repositoryManager.loadAll(any(), storageRepository, ignoreDisabled = false, modelFilter = any())
    } returns LoadResult(models = emptyList(), repositories = listOf(disabledOfficial))
    coEvery {
      repositoryManager.loadAll(any(), storageRepository, ignoreDisabled = true, modelFilter = any())
    } returns LoadResult(models = emptyList(), repositories = listOf(disabledOfficial))

    val viewModel = ModelManagerViewModel(
      downloadRepository = mockk<DownloadRepository>(relaxed = true) {
        every { getModelDownloadWork() } returns ModelDownloadWork()
      },
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
