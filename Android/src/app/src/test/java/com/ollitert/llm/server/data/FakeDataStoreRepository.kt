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

import com.ollitert.llm.server.proto.AccessTokenData
import com.ollitert.llm.server.proto.BenchmarkResult
import com.ollitert.llm.server.proto.ImportedModel

class FakeDataStoreRepository : DataStoreRepository {

  private val repos = mutableListOf<Repository>()

  override suspend fun readRepositories(): List<Repository> = repos.toList()

  override suspend fun addRepository(repo: Repository) { repos.add(repo) }

  override suspend fun seedRepositoryIfAbsent(repo: Repository) {
    if (repos.none { it.id == repo.id }) repos.add(repo)
  }

  override suspend fun updateRepository(repo: Repository) {
    val index = repos.indexOfFirst { it.id == repo.id }
    if (index >= 0) repos[index] = repo
  }

  override suspend fun toggleRepositoryEnabled(id: String, enabled: Boolean) {
    val index = repos.indexOfFirst { it.id == id }
    if (index >= 0) repos[index] = repos[index].copy(enabled = enabled)
  }

  override suspend fun removeRepository(id: String) { repos.removeAll { it.id == id } }

  override suspend fun resetRepositories() {
    val builtIn = repos.find { it.isBuiltIn }
    repos.clear()
    if (builtIn != null) repos.add(builtIn.copy(enabled = true, lastError = ""))
  }

  // Unused in RepositoryManager tests — stub implementations.
  override suspend fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long) = Unit
  override suspend fun clearAccessTokenData() = Unit
  override suspend fun readAccessTokenData(): AccessTokenData? = null
  override suspend fun saveImportedModels(importedModels: List<ImportedModel>) = Unit
  override suspend fun readImportedModels(): List<ImportedModel> = emptyList()
  override suspend fun updateImportedModel(fileName: String, updatedModel: ImportedModel) = Unit
  override suspend fun setHasSeenBenchmarkComparisonHelp(seen: Boolean) = Unit
  override suspend fun getHasSeenBenchmarkComparisonHelp(): Boolean = false
  override suspend fun addBenchmarkResult(result: BenchmarkResult) = Unit
  override suspend fun getAllBenchmarkResults(): List<BenchmarkResult> = emptyList()
  override suspend fun deleteBenchmarkResult(index: Int) = Unit
  override suspend fun setBenchmarkResults(results: List<BenchmarkResult>) = Unit
  override suspend fun isOnboardingCompleted(): Boolean = true
  override suspend fun setOnboardingCompleted() = Unit
}
