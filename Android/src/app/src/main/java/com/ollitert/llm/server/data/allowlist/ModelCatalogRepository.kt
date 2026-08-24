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
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.Repository
import com.ollitert.llm.server.data.repository.DataStoreRepository

import com.ollitert.llm.server.proto.ImportedModel
import java.io.File

/**
 * Repository providing unified access to available model catalogs across
 * bundled assets, cached allowlists, and imported proto models.
 */
interface ModelCatalogRepository {
  /** Returns the current list of allowed models across all active allowlists. */
  fun loadAllowedModels(): List<AllowedModel>

  /** Returns all models from the allowlists built into [Model] instances with import overrides. */
  fun loadCatalogModels(importsDir: File): List<Model>

  /** Returns the list of user-imported models stored in DataStore. */
  suspend fun getImportedModels(): List<ImportedModel>

  /** Returns the content version of the active allowlist. */
  fun getAllowlistContentVersion(): Int

  /** Returns the source indicator of the active allowlist. */
  fun getAllowlistSource(): String
}

class DefaultModelCatalogRepository(
  private val modelCatalogMerger: ModelCatalogMerger,
  private val dataStoreRepository: DataStoreRepository,
) : ModelCatalogRepository {
  override fun loadAllowedModels(): List<AllowedModel> = modelCatalogMerger.load()

  override fun loadCatalogModels(importsDir: File): List<Model> {
    return modelCatalogMerger.load().map { ModelFactory.buildAllowedModel(it, importsDir) }
  }

  override suspend fun getImportedModels(): List<ImportedModel> {
    return dataStoreRepository.readImportedModels()
  }

  override fun getAllowlistContentVersion(): Int = modelCatalogMerger.lastContentVersion

  override fun getAllowlistSource(): String = modelCatalogMerger.lastSource
}
