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
import kotlinx.coroutines.runBlocking

interface DataStoreRepository {
  fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long)
  fun clearAccessTokenData()
  fun readAccessTokenData(): AccessTokenData?

  fun saveImportedModels(importedModels: List<ImportedModel>)
  fun readImportedModels(): List<ImportedModel>
  fun updateImportedModel(fileName: String, updatedModel: ImportedModel)

  fun setHasSeenBenchmarkComparisonHelp(seen: Boolean)
  fun getHasSeenBenchmarkComparisonHelp(): Boolean

  fun addBenchmarkResult(result: BenchmarkResult)
  fun getAllBenchmarkResults(): List<BenchmarkResult>
  fun deleteBenchmarkResult(index: Int)
  fun setBenchmarkResults(results: List<BenchmarkResult>)

  fun isOnboardingCompleted(): Boolean
  fun setOnboardingCompleted()
}

class DefaultDataStoreRepository(
  private val dataStore: DataStore<Settings>,
  private val userDataDataStore: DataStore<UserData>,
  private val benchmarkResultsDataStore: DataStore<BenchmarkResults>,
) : DataStoreRepository {

  override fun saveAccessTokenData(accessToken: String, refreshToken: String, expiresAt: Long) {
    runBlocking {
      dataStore.updateData { settings ->
        settings.toBuilder().setAccessTokenData(AccessTokenData.getDefaultInstance()).build()
      }
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
  }

  override fun clearAccessTokenData() {
    runBlocking {
      dataStore.updateData { settings -> settings.toBuilder().clearAccessTokenData().build() }
      userDataDataStore.updateData { userData ->
        userData.toBuilder().clearAccessTokenData().build()
      }
    }
  }

  override fun readAccessTokenData(): AccessTokenData? {
    return runBlocking {
      val userData = userDataDataStore.data.first()
      userData.accessTokenData
    }
  }

  override fun saveImportedModels(importedModels: List<ImportedModel>) {
    runBlocking {
      dataStore.updateData { settings ->
        settings.toBuilder().clearImportedModel().addAllImportedModel(importedModels).build()
      }
    }
  }

  override fun readImportedModels(): List<ImportedModel> {
    return runBlocking {
      val settings = dataStore.data.first()
      settings.importedModelList
    }
  }

  override fun updateImportedModel(fileName: String, updatedModel: ImportedModel) {
    runBlocking {
      dataStore.updateData { settings ->
        val models = settings.importedModelList.toMutableList()
        val index = models.indexOfFirst { it.fileName == fileName }
        if (index >= 0) models[index] = updatedModel else models.add(updatedModel)
        settings.toBuilder().clearImportedModel().addAllImportedModel(models).build()
      }
    }
  }

  override fun setHasSeenBenchmarkComparisonHelp(seen: Boolean) {
    runBlocking {
      dataStore.updateData { settings ->
        settings.toBuilder().setHasSeenBenchmarkComparisonHelp(seen).build()
      }
    }
  }

  override fun getHasSeenBenchmarkComparisonHelp(): Boolean {
    return runBlocking {
      val settings = dataStore.data.first()
      settings.hasSeenBenchmarkComparisonHelp
    }
  }

  override fun addBenchmarkResult(result: BenchmarkResult) {
    runBlocking {
      benchmarkResultsDataStore.updateData { results ->
        results.toBuilder().addResult(0, result).build()
      }
    }
  }

  override fun getAllBenchmarkResults(): List<BenchmarkResult> {
    return runBlocking { benchmarkResultsDataStore.data.first().resultList }
  }

  override fun deleteBenchmarkResult(index: Int) {
    runBlocking {
      benchmarkResultsDataStore.updateData { results ->
        results.toBuilder().removeResult(index).build()
      }
    }
  }

  override fun setBenchmarkResults(results: List<BenchmarkResult>) {
    runBlocking {
      benchmarkResultsDataStore.updateData { existing ->
        existing.toBuilder().clearResult().addAllResult(results).build()
      }
    }
  }

  override fun isOnboardingCompleted(): Boolean {
    return runBlocking {
      val settings = dataStore.data.first()
      settings.isTosAccepted // reuse existing proto field for onboarding gate
    }
  }

  override fun setOnboardingCompleted() {
    runBlocking {
      dataStore.updateData { settings -> settings.toBuilder().setIsTosAccepted(true).build() }
    }
  }
}
