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

package com.ollitert.llm.server.data

import androidx.datastore.core.DataStore
import com.ollitert.llm.server.proto.AccessTokenData
import com.ollitert.llm.server.proto.BenchmarkResult
import com.ollitert.llm.server.proto.BenchmarkResults
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.Settings
import com.ollitert.llm.server.proto.UserData
import kotlinx.coroutines.flow.first

interface DataStoreRepository {
  suspend fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long)
  suspend fun clearAccessTokenData()
  suspend fun readAccessTokenData(): AccessTokenData?

  suspend fun saveImportedModels(importedModels: List<ImportedModel>)
  suspend fun readImportedModels(): List<ImportedModel>
  suspend fun updateImportedModel(fileName: String, updatedModel: ImportedModel)

  suspend fun setHasSeenBenchmarkComparisonHelp(seen: Boolean)
  suspend fun getHasSeenBenchmarkComparisonHelp(): Boolean

  suspend fun addBenchmarkResult(result: BenchmarkResult)
  suspend fun getAllBenchmarkResults(): List<BenchmarkResult>
  suspend fun deleteBenchmarkResult(index: Int)
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
  private val userDataDataStore: DataStore<UserData>,
  private val benchmarkResultsDataStore: DataStore<BenchmarkResults>,
) : DataStoreRepository {

  override suspend fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long) {
    userDataDataStore.updateData { userData ->
      userData
        .toBuilder()
        .setAccessTokenData(
          AccessTokenData.newBuilder()
            .setAccessToken(accessToken)
            .setRefreshToken(refreshToken)
            .setExpiresAtMs(expiresAt)
            .build()
        )
        .build()
    }
  }

  override suspend fun clearAccessTokenData() {
    userDataDataStore.updateData { userData ->
      userData.toBuilder().clearAccessTokenData().build()
    }
  }

  override suspend fun readAccessTokenData(): AccessTokenData? {
    val userData = userDataDataStore.data.first()
    return userData.accessTokenData
  }

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
      results.toBuilder().addResult(0, result).build()
    }
  }

  override suspend fun getAllBenchmarkResults(): List<BenchmarkResult> {
    return benchmarkResultsDataStore.data.first().resultList
  }

  override suspend fun deleteBenchmarkResult(index: Int) {
    benchmarkResultsDataStore.updateData { results ->
      results.toBuilder().removeResult(index).build()
    }
  }

  override suspend fun setBenchmarkResults(results: List<BenchmarkResult>) {
    benchmarkResultsDataStore.updateData { existing ->
      existing.toBuilder().clearResult().addAllResult(results).build()
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
      val filtered = settings.repositoriesList.filter { it.id != id }
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
