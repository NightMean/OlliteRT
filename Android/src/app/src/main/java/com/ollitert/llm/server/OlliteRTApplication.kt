/*
 * Copyright 2025 Google LLC
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

package com.ollitert.llm.server

import android.app.Application
import android.util.Log
import com.ollitert.llm.server.data.db.RequestLogPersistence
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class OlliteRTApplication : Application() {

  /**
   * Entry point for accessing Hilt-managed singletons from [Application.onCreate].
   * Needed because [RequestLogPersistence] is Hilt-provided but [RequestLogStore]
   * is a plain singleton object — this bridges the two worlds.
   */
  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface PersistenceEntryPoint {
    fun requestLogPersistence(): RequestLogPersistence
  }

  override fun onCreate() {
    super.onCreate()

    // Initialize log persistence (registers callback on RequestLogStore, loads from DB if enabled).
    // Wrapped in try-catch so a persistence failure doesn't crash the entire app on startup.
    try {
      val entryPoint = EntryPointAccessors.fromApplication(this, PersistenceEntryPoint::class.java)
      entryPoint.requestLogPersistence().initialize()
    } catch (e: Exception) {
      Log.e("OlliteRTApp", "Failed to initialize log persistence — logs will be in-memory only", e)
    }
  }
}
