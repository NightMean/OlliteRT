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

package com.ollitert.llm.server.data.allowlist
import com.ollitert.llm.server.data.repository.DataStoreRepository

import com.ollitert.llm.server.proto.ImportedModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelCatalogRepositoryTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private lateinit var modelCatalogMerger: ModelCatalogMerger
  private lateinit var dataStoreRepository: DataStoreRepository
  private lateinit var repository: DefaultModelCatalogRepository

  private val sampleAllowedModel = AllowedModel(
    name = "gemma-2b",
    modelId = "google/gemma-2b-it",
    modelFile = "gemma-2b.litertlm",
    description = "Gemma 2B test model",
    sizeInBytes = 1_500_000_000L,
    defaultConfig = DefaultConfig(),
  )

  @Before
  fun setUp() {
    modelCatalogMerger = mockk(relaxed = true)
    dataStoreRepository = mockk(relaxed = true)
    repository = DefaultModelCatalogRepository(modelCatalogMerger, dataStoreRepository)
  }

  @Test
  fun `loadAllowedModels delegates to modelCatalogMerger`() {
    val allowed = listOf(sampleAllowedModel)
    every { modelCatalogMerger.load() } returns allowed

    val result = repository.loadAllowedModels()
    assertEquals(allowed, result)
  }

  @Test
  fun `loadCatalogModels builds models with factory and imports override`() {
    val allowed = listOf(sampleAllowedModel)
    every { modelCatalogMerger.load() } returns allowed

    val importsDir = tempFolder.newFolder("imports")
    val result = repository.loadCatalogModels(importsDir)

    assertEquals(1, result.size)
    assertEquals("gemma-2b", result[0].name)
  }

  @Test
  fun `getImportedModels delegates to dataStoreRepository`() = runTest {
    val importedList = listOf(ImportedModel.newBuilder().setFileName("custom.litertlm").build())
    coEvery { dataStoreRepository.readImportedModels() } returns importedList

    val result = repository.getImportedModels()
    assertEquals(importedList, result)
  }

  @Test
  fun `metadata getters delegate to merger properties`() {
    every { modelCatalogMerger.lastContentVersion } returns 42
    every { modelCatalogMerger.lastSource } returns "official"

    assertEquals(42, repository.getAllowlistContentVersion())
    assertEquals("official", repository.getAllowlistSource())
  }
}
