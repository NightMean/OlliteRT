package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.data.allowlist.RepositoryManager
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.repository.*
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagerFallbackTest {
  private val dispatcher = StandardTestDispatcher()
  private val storage = mockk<ModelStorageRepository>(relaxed = true)
  private val downloads = mockk<DownloadRepository>(relaxed = true)

  @Before fun setUp() { Dispatchers.setMain(dispatcher) }
  @After fun tearDown() { Dispatchers.resetMain() }

  private fun viewModel(): ModelManagerViewModel {
    val proto = mockk<ProtoDataStoreRepository>(relaxed = true)
    coEvery { proto.isOnboardingCompleted() } returns true
    return ModelManagerViewModel(
      downloadRepository = downloads,
      protoDataStoreRepository = proto,
      lifecycleProvider = mockk<OlliteRTLifecycleProvider>(relaxed = true),
      repositoryManager = mockk<RepositoryManager>(relaxed = true),
      context = mockk<Context>(relaxed = true),
      preferencesRepository = FakePreferencesRepository(),
      serverStateRepository = mockk<ServerStateRepository>(relaxed = true),
      modelStorageRepository = storage,
      ioDispatcher = dispatcher,
      mainDispatcher = dispatcher,
    )
  }

  @Test
  fun rejectingConsentLeavesTheUserAbleToRetryThePrimary() = runTest(dispatcher) {
    val vm = viewModel()
    val model = Model("model")
    vm.setDownloadStatus(model, ModelDownloadStatus(
      ModelDownloadStatusType.FAILED, modelScopeConsentError = "timeout",
    ))

    vm.dismissModelScopeConsent(model)

    assertFalse(vm.isModelScopeFallbackEnabled())
    val status = requireNotNull(vm.uiState.value.modelDownloadStatus[model.name])
    assertNull(status.modelScopeConsentError)
    assertEquals("timeout", status.errorMessage)
    vm.downloadModel(model)
    assertEquals(ModelDownloadStatusType.IN_PROGRESS, vm.uiState.value.modelDownloadStatus[model.name]?.status)
    advanceUntilIdle()
  }

  @Test
  fun retryAfterConsentPreservesExistingProviderPartials() = runTest(dispatcher) {
    val vm = viewModel()
    val model = Model("model")
    vm.setDownloadStatus(model, ModelDownloadStatus(
      ModelDownloadStatusType.FAILED, receivedBytes = 20, totalBytes = 100,
      modelScopeConsentError = "timeout",
    ))
    vm.setModelScopeFallbackEnabled(true)
    model.modelScopePrimaryError = "timeout"

    vm.downloadModel(model)

    verify(exactly = 0) { storage.deleteDirFromExternalFilesDir(any()) }
    verify(exactly = 1) { downloads.downloadModel(model, any()) }
    assertTrue(vm.isModelScopeFallbackEnabled())
    advanceUntilIdle()
  }
}
