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
import java.io.File
import java.util.UUID

internal fun BenchmarkResult.withStableBenchmarkId(): BenchmarkResult =
  if (id.isBlank()) toBuilder().setId(UUID.randomUUID().toString()).build() else this

/**
 * Repository for structured user data persisted in Proto DataStore:
 * imported models, benchmark results, model sources, and the onboarding flag.
 *
 * Distinct from [PreferencesRepository], which wraps the SharedPreferences-backed
 * static ServerPrefs used for server and inference settings. The two boundaries
 * intentionally do not overlap.
 */
interface ProtoDataStoreRepository {
  suspend fun saveImportedModels(importedModels: List<ImportedModel>)
  suspend fun readImportedModels(): List<ImportedModel>
  suspend fun replaceImportedModel(expected: ImportedModel, updated: ImportedModel, afterSave: () -> Unit)
  suspend fun updateImportedModel(fileName: String, updatedModel: ImportedModel)
  suspend fun reserveImportedModel(fileName: String): Boolean
  suspend fun releaseImportedModel(fileName: String)
  /** Takes ownership of the staged file, including cleanup or recovery after failure. */
  suspend fun finalizeImportedModel(info: ImportedModel, expected: ImportedModel?, stagedFile: File)
  suspend fun readExistingImportedModels(): List<ImportedModel>
  suspend fun renameImportedDisplayName(fileName: String, displayName: String): ImportedModel?
  suspend fun removeImportedModel(fileName: String)
  suspend fun renameImportedModel(oldFileName: String, newFileName: String, displayName: String): ImportedModel?

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

internal class DefaultProtoDataStoreRepository(
  private val dataStore: DataStore<Settings>,
  private val benchmarkResultsDataStore: DataStore<BenchmarkResults>,
  private val renameStorage: ImportedRenameStorage? = null,
  private val copyStorage: DataStoreImportedCopyStorage? = null,
) : ProtoDataStoreRepository {
  private val importedRenameRecovery = renameStorage?.let { storage ->
    ImportedRenameRecovery(storage) { copyStorage?.recover() }
  }

  private suspend fun <T> withConsistentImportedModels(operation: suspend () -> T): T {
    val recovery = importedRenameRecovery
    if (recovery != null) return recovery.withConsistentState(operation)
    val settings = dataStore.data.first()
    check(!settings.hasPendingImportedRename() && !settings.hasPendingImportedCopy()) {
      "Imported model recovery is unavailable"
    }
    return operation()
  }

  override suspend fun saveImportedModels(importedModels: List<ImportedModel>) {
    withConsistentImportedModels {
      dataStore.updateData { it.toBuilder().clearImportedModel().addAllImportedModel(importedModels).build() }
    }
  }

  override suspend fun readImportedModels(): List<ImportedModel> = withConsistentImportedModels {
    dataStore.data.first().importedModelList
  }

  override suspend fun reserveImportedModel(fileName: String): Boolean =
    (importedRenameRecovery ?: error("Imported model recovery is unavailable")).reserveImport(fileName)

  override suspend fun releaseImportedModel(fileName: String) {
    (importedRenameRecovery ?: error("Imported model recovery is unavailable")).releaseImport(fileName)
  }

  override suspend fun finalizeImportedModel(info: ImportedModel, expected: ImportedModel?, stagedFile: File) {
    var handedToStorage = false
    try {
      withConsistentImportedModels {
        check(importedRenameRecovery?.isImportReserved(info.fileName) == true) { "Model import is not reserved" }
        val storage = checkNotNull(copyStorage) { "Imported model storage is unavailable" }
        handedToStorage = true
        storage.commit(info, expected, stagedFile)
      }
    } finally {
      if (!handedToStorage) stagedFile.delete()
    }
  }

  override suspend fun readExistingImportedModels(): List<ImportedModel> = withConsistentImportedModels {
    val storage = renameStorage ?: error("Imported model storage is unavailable")
    val snapshot = dataStore.data.first().importedModelList
    val missing = snapshot.filterNot {
      importedRenameRecovery?.isImportReserved(it.fileName) == true || storage.fileExists(it.fileName)
    }.toSet()
    if (missing.isEmpty()) snapshot else {
      dataStore.updateData { settings ->
        settings.toBuilder().clearImportedModel()
          .addAllImportedModel(settings.importedModelList.filterNot { it in missing }).build()
      }.importedModelList
    }
  }

  override suspend fun replaceImportedModel(expected: ImportedModel, updated: ImportedModel, afterSave: () -> Unit) {
    withConsistentImportedModels {
      check(expected.fileName == updated.fileName) { "Model settings cannot change the file name" }
      val storage = renameStorage ?: error("Imported model storage is unavailable")
      if (!storage.fileExists(expected.fileName)) throw java.io.IOException("Imported model file is missing")
      dataStore.updateData { settings ->
        val models = settings.importedModelList.toMutableList()
        val index = models.indexOfFirst { it.fileName == expected.fileName }
        check(index >= 0 && models[index] == expected) { "Imported model changed while editing settings" }
        models[index] = updated
        settings.toBuilder().clearImportedModel().addAllImportedModel(models).build()
      }
      afterSave()
    }
  }

  override suspend fun updateImportedModel(fileName: String, updatedModel: ImportedModel) {
    withConsistentImportedModels {
      if (renameStorage != null && !renameStorage.fileExists(fileName)) {
        throw java.io.IOException("Imported model file is missing")
      }
      dataStore.updateData { settings ->
        val models = settings.importedModelList.toMutableList()
        val index = models.indexOfFirst { it.fileName == fileName }
        if (index >= 0) models[index] = updatedModel else models.add(updatedModel)
        settings.toBuilder().clearImportedModel().addAllImportedModel(models).build()
      }
    }
  }

  override suspend fun renameImportedDisplayName(fileName: String, displayName: String): ImportedModel? =
    withConsistentImportedModels {
      val updated = dataStore.updateData { settings ->
        val models = settings.importedModelList.toMutableList()
        val index = models.indexOfFirst { it.fileName == fileName }
        if (index >= 0) models[index] = models[index].toBuilder().setDisplayName(displayName).build()
        settings.toBuilder().clearImportedModel().addAllImportedModel(models).build()
      }
      updated.importedModelList.firstOrNull { it.fileName == fileName }
    }

  override suspend fun removeImportedModel(fileName: String) {
    withConsistentImportedModels {
      val storage = renameStorage ?: error("Imported model storage is unavailable")
      check(importedRenameRecovery?.isImportReserved(fileName) != true) { "Model import is in progress" }
      if (dataStore.data.first().importedModelList.none { it.fileName == fileName }) return@withConsistentImportedModels
      storage.deleteFile(fileName)
      dataStore.updateData { settings ->
        settings.toBuilder().clearImportedModel()
          .addAllImportedModel(settings.importedModelList.filterNot { it.fileName == fileName }).build()
      }
    }
  }

  override suspend fun renameImportedModel(oldFileName: String, newFileName: String, displayName: String): ImportedModel? {
    val recovery = importedRenameRecovery ?: error("Imported model recovery is unavailable")
    val original = readImportedModels().firstOrNull { it.fileName == oldFileName } ?: return null
    val rename = ImportedRename(original, newFileName, displayName)
    return try {
      if (recovery.rename(rename)) rename.renamed else null
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Exception) {
      // A durable step can succeed before its acknowledgement is lost.
      val completed = try { readImportedModels().any { it == rename.renamed } }
        catch (recoveryError: kotlinx.coroutines.CancellationException) { throw recoveryError }
        catch (recoveryError: Exception) { e.addSuppressed(recoveryError); false }
      if (completed) rename.renamed else throw e
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
