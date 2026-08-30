/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.data.allowlist.RepositoryManager
import com.ollitert.llm.server.data.repository.DownloadRepository
import com.ollitert.llm.server.data.repository.FakePreferencesRepository
import com.ollitert.llm.server.data.repository.ModelStorageRepository
import com.ollitert.llm.server.data.repository.ProtoDataStoreRepository
import com.ollitert.llm.server.data.repository.ServerStateRepository
import io.mockk.coEvery
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagerViewModelRenameTest {
  private val testDispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(testDispatcher)
    mockkStatic(Log::class)
    every { Log.e(any(), any(), any()) } returns 0
  }

  @After
  fun tearDown() {
    unmockkStatic(Log::class)
    Dispatchers.resetMain()
  }

  @Test
  fun failedDiskRenameIsReportedAfterTheOperationCompletes() = runTest(testDispatcher) {
    val context = mockk<Context>(relaxed = true)
    val protoRepository = mockk<ProtoDataStoreRepository>(relaxed = true)
    val storageRepository = mockk<ModelStorageRepository>(relaxed = true)
    every { storageRepository.renameImportedFile("old.litertlm", "new.litertlm") } returns false
    coEvery { protoRepository.isOnboardingCompleted() } returns true

    val viewModel = ModelManagerViewModel(
      downloadRepository = mockk<DownloadRepository>(relaxed = true),
      protoDataStoreRepository = protoRepository,
      lifecycleProvider = mockk<OlliteRTLifecycleProvider>(relaxed = true),
      repositoryManager = mockk<RepositoryManager>(relaxed = true),
      context = context,
      preferencesRepository = FakePreferencesRepository(),
      serverStateRepository = mockk<ServerStateRepository>(relaxed = true),
      modelStorageRepository = storageRepository,
      ioDispatcher = testDispatcher,
      mainDispatcher = testDispatcher,
    )

    val outcomes = mutableListOf<Boolean>()
    viewModel.renameImportedModel("old.litertlm", "new.litertlm", "new") {
      outcomes += it
    }

    assertEquals(emptyList<Boolean>(), outcomes)
    advanceUntilIdle()
    assertEquals(listOf(false), outcomes)
  }

  @Test
  fun renameExceptionStillCompletesWithFailure() = runTest(testDispatcher) {
    val context = mockk<Context>(relaxed = true)
    val protoRepository = mockk<ProtoDataStoreRepository>(relaxed = true)
    val storageRepository = mockk<ModelStorageRepository>(relaxed = true)
    every {
      storageRepository.renameImportedFile("old.litertlm", "new.litertlm")
    } throws IllegalStateException("storage unavailable")
    coEvery { protoRepository.isOnboardingCompleted() } returns true

    val viewModel = ModelManagerViewModel(
      downloadRepository = mockk<DownloadRepository>(relaxed = true),
      protoDataStoreRepository = protoRepository,
      lifecycleProvider = mockk<OlliteRTLifecycleProvider>(relaxed = true),
      repositoryManager = mockk<RepositoryManager>(relaxed = true),
      context = context,
      preferencesRepository = FakePreferencesRepository(),
      serverStateRepository = mockk<ServerStateRepository>(relaxed = true),
      modelStorageRepository = storageRepository,
      ioDispatcher = testDispatcher,
      mainDispatcher = testDispatcher,
    )

    val outcomes = mutableListOf<Boolean>()
    viewModel.renameImportedModel("old.litertlm", "new.litertlm", "new") {
      outcomes += it
    }
    advanceUntilIdle()

    assertEquals(listOf(false), outcomes)
  }
}
