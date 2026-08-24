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

package com.ollitert.llm.server.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.data.prefs.BenchmarkResultsSerializer
import com.ollitert.llm.server.data.repository.DataStoreRepository
import com.ollitert.llm.server.data.repository.DefaultDataStoreRepository
import com.ollitert.llm.server.data.allowlist.DefaultModelCatalogRepository
import com.ollitert.llm.server.data.allowlist.MODEL_ALLOWLIST_FILENAME
import com.ollitert.llm.server.data.allowlist.ModelCatalogMerger
import com.ollitert.llm.server.data.allowlist.ModelCatalogRepository
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.prefs.SettingsSerializer
import com.ollitert.llm.server.proto.BenchmarkResults
import com.ollitert.llm.server.proto.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val TAG = "OlliteRT.DataStore"

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {

  @Provides
  @Singleton
  fun provideSettingsDataStore(
    @ApplicationContext context: Context,
  ): DataStore<Settings> {
    return DataStoreFactory.create(
      serializer = SettingsSerializer,
      corruptionHandler = ReplaceFileCorruptionHandler {
        Log.e(TAG, "settings.pb corrupted — resetting to defaults")
        try { ServerPrefs.addCorruptedDataStore(context, "settings") }
        catch (e: Exception) { Log.e(TAG, "Failed to flag corruption", e) }
        Settings.getDefaultInstance()
      },
      produceFile = { context.dataStoreFile("settings.pb") },
    )
  }

  @Provides
  @Singleton
  fun provideBenchmarkResultsDataStore(
    @ApplicationContext context: Context,
  ): DataStore<BenchmarkResults> {
    return DataStoreFactory.create(
      serializer = BenchmarkResultsSerializer,
      corruptionHandler = ReplaceFileCorruptionHandler {
        Log.e(TAG, "benchmark_results.pb corrupted — resetting to defaults")
        try { ServerPrefs.addCorruptedDataStore(context, "benchmark_results") }
        catch (e: Exception) { Log.e(TAG, "Failed to flag corruption", e) }
        BenchmarkResults.getDefaultInstance()
      },
      produceFile = { context.dataStoreFile("benchmark_results.pb") },
    )
  }

  @Provides
  @Singleton
  fun provideDataStoreRepository(
    dataStore: DataStore<Settings>,
    benchmarkResultsStore: DataStore<BenchmarkResults>,
  ): DataStoreRepository {
    return DefaultDataStoreRepository(
      dataStore,
      benchmarkResultsStore,
    )
  }

  @Provides
  @Singleton
  fun provideModelCatalogRepository(
    @ApplicationContext context: Context,
    dataStoreRepository: DataStoreRepository,
  ): ModelCatalogRepository {
    val merger = ModelCatalogMerger(
      externalFilesDir = context.getExternalFilesDir(null),
      appVersionName = BuildConfig.VERSION_NAME,
      assetReader = {
        try {
          context.assets.open(MODEL_ALLOWLIST_FILENAME).bufferedReader().use { it.readText() }
        } catch (_: Exception) { null }
      },
    )
    return DefaultModelCatalogRepository(merger, dataStoreRepository)
  }

  @Provides
  @Singleton
  fun provideServerStateRepository(): com.ollitert.llm.server.data.repository.ServerStateRepository {
    return com.ollitert.llm.server.data.repository.DefaultServerStateRepository()
  }
}
