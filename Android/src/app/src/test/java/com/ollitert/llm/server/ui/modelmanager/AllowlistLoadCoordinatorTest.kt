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
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.allowlist.AllowedModel
import com.ollitert.llm.server.data.repository.DataStoreRepository
import com.ollitert.llm.server.data.allowlist.DefaultConfig
import com.ollitert.llm.server.data.allowlist.ModelAllowlistLoader
import com.ollitert.llm.server.data.storage.ModelFileManager
import com.ollitert.llm.server.data.allowlist.ModelListImportManager
import com.ollitert.llm.server.data.allowlist.RefreshResult
import com.ollitert.llm.server.data.model.Repository
import com.ollitert.llm.server.data.allowlist.RepositoryManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AllowlistLoadCoordinatorTest {

  private lateinit var context: Context
  private lateinit var dataStoreRepository: DataStoreRepository
  private lateinit var repositoryManager: RepositoryManager
  private lateinit var allowlistLoader: ModelAllowlistLoader
  private lateinit var fileManager: ModelFileManager
  private lateinit var importManager: ModelListImportManager
  private lateinit var coordinator: AllowlistLoadCoordinator

  @Before
  fun setUp() {
    context = mockk(relaxed = true)
    dataStoreRepository = mockk(relaxed = true)
    repositoryManager = mockk(relaxed = true)
    allowlistLoader = mockk(relaxed = true)
    fileManager = mockk(relaxed = true)
    importManager = mockk(relaxed = true)

    every { context.getString(R.string.error_all_repos_offline) } returns "All repositories are offline"
    every { context.getString(R.string.error_showing_cached_list) } returns "Showing cached list"
    every { context.getString(R.string.error_some_repos_unavailable, any(), any()) } answers {
      "${args[1]} of ${args[2]} repositories unavailable"
    }

    coordinator = AllowlistLoadCoordinator(
      context = context,
      dataStoreRepository = dataStoreRepository,
      repositoryManager = repositoryManager,
      allowlistLoader = allowlistLoader,
      fileManager = fileManager,
      importManager = importManager,
      preferencesRepository = com.ollitert.llm.server.data.repository.FakePreferencesRepository(),
    )
  }

  private fun createTestRepo(id: String, modelCount: Int? = null, enabled: Boolean = true): Repository {
    return Repository(
      id = id,
      url = "http://test/$id",
      enabled = enabled,
      isBuiltIn = false,
      contentVersion = 1,
      lastRefreshMs = 0L,
      lastError = "",
      name = "Repo $id",
      modelCount = modelCount,
    )
  }

  @Test
  fun computeRefreshErrorMessage_returnsEmptyWhenNoFailures() {
    val result = RefreshResult(failedRepoIds = emptySet())
    val repos = listOf(createTestRepo("r1"))

    val msg = coordinator.computeRefreshErrorMessage(result, repos)
    assertEquals("", msg)
  }

  @Test
  fun computeRefreshErrorMessage_returnsAllOfflineWhenAllEnabledFailWithoutCache() {
    val result = RefreshResult(failedRepoIds = setOf("r1", "r2"))
    val repos = listOf(
      createTestRepo("r1", modelCount = null),
      createTestRepo("r2", modelCount = null),
    )

    val msg = coordinator.computeRefreshErrorMessage(result, repos)
    assertEquals("All repositories are offline", msg)
  }

  @Test
  fun computeRefreshErrorMessage_returnsCachedWarningWhenFailedWithCache() {
    val result = RefreshResult(failedRepoIds = setOf("r1"))
    val repos = listOf(
      createTestRepo("r1", modelCount = 5),
    )

    val msg = coordinator.computeRefreshErrorMessage(result, repos)
    assertEquals("Showing cached list", msg)
  }

  @Test
  fun isModelSupportedOnDevice_returnsTrueForCpuGpuModels() {
    val allowedModel = AllowedModel(
      name = "test-model",
      modelId = "test/model",
      modelFile = "model.bin",
      description = "Test model",
      sizeInBytes = 1000L,
      defaultConfig = DefaultConfig(
        accelerators = "gpu,cpu",
      ),
    )

    assertTrue(coordinator.isModelSupportedOnDevice(allowedModel))
  }
}
