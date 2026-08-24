/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.data.repository
import com.ollitert.llm.server.data.model.Repository

import androidx.datastore.core.DataStore
import com.ollitert.llm.server.proto.BenchmarkResult
import com.ollitert.llm.server.proto.BenchmarkResults
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.Settings
import kotlinx.coroutines.flow.first
import java.util.UUID

internal fun BenchmarkResult.withStableBenchmarkId(): BenchmarkResult =
  if (id.isBlank()) toBuilder().setId(UUID.randomUUID().toString()).build() else this

interface DataStoreRepository {
  suspend fun saveImportedModels(importedModels: List<ImportedModel>)
  suspend fun readImportedModels(): List<ImportedModel>
  suspend fun updateImportedModel(fileName: String, updatedModel: ImportedModel)

  suspend fun setHasSeenBenchmarkComparisonHelp(seen: Boolean)
  suspend fun getHasSeenBenchmarkComparisonHelp(): Boolean

  suspend fun addBenchmarkResult(result: BenchmarkResult)
  suspend fun getAllBenchmarkResults(): List<BenchmarkResult>
  suspend fun deleteBenchmarkResult(id: String)
  suspend fun setBenchmarkResults(results: List<BenchmarkResult>)

  suspend fun isOnboardingCompleted(): Boolean
  suspend fun setOnboardingCompleted()

  suspend fun readRepositories(): List<Repository>
  suspend fun addRepository(repo: Repository)
  suspend fun seedRepositoryIfAbsent(repo: Repository)
  suspend fun updateRepository(repo: Repository)
  suspend fun toggleRepositoryEnabled(id: String, enabled: Boolean)
  suspend fun removeRepository(id: String)
  suspend fun resetRepositories()
}

class DefaultDataStoreRepository(
  private val dataStore: DataStore<Settings>,
  private val benchmarkResultsDataStore: DataStore<BenchmarkResults>,
) : DataStoreRepository {

  override suspend fun saveImportedModels(importedModels: List<ImportedModel>) {
    dataStore.updateData { settings ->
      settings.toBuilder().clearImportedModel().addAllImportedModel(importedModels).build()
    }
  }

  override suspend fun readImportedModels(): List<ImportedModel> {
    val settings = dataStore.data.first()
    return settings.importedModelList
  }

  override suspend fun updateImportedModel(fileName: String, updatedModel: ImportedModel) {
    dataStore.updateData { settings ->
      val models = settings.importedModelList.toMutableList()
      val index = models.indexOfFirst { it.fileName == fileName }
      if (index >= 0) models[index] = updatedModel else models.add(updatedModel)
      settings.toBuilder().clearImportedModel().addAllImportedModel(models).build()
    }
  }

  override suspend fun setHasSeenBenchmarkComparisonHelp(seen: Boolean) {
    dataStore.updateData { settings ->
      settings.toBuilder().setHasSeenBenchmarkComparisonHelp(seen).build()
    }
  }

  override suspend fun getHasSeenBenchmarkComparisonHelp(): Boolean {
    val settings = dataStore.data.first()
    return settings.hasSeenBenchmarkComparisonHelp
  }

  override suspend fun addBenchmarkResult(result: BenchmarkResult) {
    benchmarkResultsDataStore.updateData { results ->
      results.toBuilder().addResult(0, result.withStableBenchmarkId()).build()
    }
  }

  override suspend fun getAllBenchmarkResults(): List<BenchmarkResult> {
    val stored = benchmarkResultsDataStore.data.first()
    if (stored.resultList.none { it.id.isBlank() }) return stored.resultList

    return benchmarkResultsDataStore.updateData { current ->
      current.toBuilder()
        .clearResult()
        .addAllResult(current.resultList.map { it.withStableBenchmarkId() })
        .build()
    }.resultList
  }

  override suspend fun deleteBenchmarkResult(id: String) {
    benchmarkResultsDataStore.updateData { results ->
      val index = results.resultList.indexOfFirst { it.id == id }
      if (index >= 0) results.toBuilder().removeResult(index).build() else results
    }
  }

  override suspend fun setBenchmarkResults(results: List<BenchmarkResult>) {
    benchmarkResultsDataStore.updateData { existing ->
      existing.toBuilder().clearResult().addAllResult(results.map { it.withStableBenchmarkId() }).build()
    }
  }

  override suspend fun isOnboardingCompleted(): Boolean {
    val settings = dataStore.data.first()
    return settings.isTosAccepted // reuse existing proto field for onboarding gate
  }

  override suspend fun setOnboardingCompleted() {
    dataStore.updateData { settings -> settings.toBuilder().setIsTosAccepted(true).build() }
  }

  override suspend fun readRepositories(): List<Repository> {
    val settings = dataStore.data.first()
    return settings.repositoriesList.map { Repository.fromProto(it) }
  }

  override suspend fun addRepository(repo: Repository) {
    dataStore.updateData { settings ->
      settings.toBuilder()
        .addRepositories(repo.toProto())
        .build()
    }
  }

  override suspend fun seedRepositoryIfAbsent(repo: Repository) {
    dataStore.updateData { settings ->
      if (settings.repositoriesList.any { it.id == repo.id }) {
        settings
      } else {
        settings.toBuilder()
          .addRepositories(repo.toProto())
          .build()
      }
    }
  }

  override suspend fun updateRepository(repo: Repository) {
    dataStore.updateData { settings ->
      val index = settings.repositoriesList.indexOfFirst { it.id == repo.id }
      if (index >= 0) {
        settings.toBuilder()
          .setRepositories(index, repo.toProto())
          .build()
      } else {
        settings
      }
    }
  }

  override suspend fun toggleRepositoryEnabled(id: String, enabled: Boolean) {
    dataStore.updateData { settings ->
      val index = settings.repositoriesList.indexOfFirst { it.id == id }
      if (index >= 0) {
        val current = settings.repositoriesList[index]
        settings.toBuilder()
          .setRepositories(index, current.toBuilder().setEnabled(enabled).build())
          .build()
      } else {
        settings
      }
    }
  }

  override suspend fun removeRepository(id: String) {
    dataStore.updateData { settings ->
      val filtered = settings.repositoriesList.filter { it.id != id || it.isBuiltIn }
      settings.toBuilder()
        .clearRepositories()
        .addAllRepositories(filtered)
        .build()
    }
  }

  override suspend fun resetRepositories() {
    dataStore.updateData { settings ->
      val official = settings.repositoriesList.find { it.isBuiltIn }
      settings.toBuilder()
        .clearRepositories()
        .apply {
          if (official != null) {
            addRepositories(
              official.toBuilder().setEnabled(true).setLastError("").build()
            )
          }
        }
        .build()
    }
  }
}
