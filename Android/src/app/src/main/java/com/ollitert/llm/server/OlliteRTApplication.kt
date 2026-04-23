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

package com.ollitert.llm.server

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.cleanupStaleImportTmpFiles
import com.ollitert.llm.server.data.db.RequestLogPersistence
import com.ollitert.llm.server.worker.AllowlistRefreshWorker
import com.ollitert.llm.server.worker.UpdateCheckWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EarlyEntryPoint
import dagger.hilt.android.EarlyEntryPoints
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

@HiltAndroidApp
class OlliteRTApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

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

  /** Entry point for accessing [DataStoreRepository] from non-Hilt components (e.g. Service). */
  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface DataStoreEntryPoint {
    fun dataStoreRepository(): DataStoreRepository
  }

  @EarlyEntryPoint
  @InstallIn(SingletonComponent::class)
  interface WorkerFactoryEntryPoint {
    fun workerFactory(): HiltWorkerFactory
  }

  override val workManagerConfiguration: Configuration
    get() {
      val workerFactory = EarlyEntryPoints.get(this, WorkerFactoryEntryPoint::class.java).workerFactory()
      return Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()
    }

  override fun newImageLoader(context: PlatformContext): ImageLoader {
    val maxImageBytes = 5L * 1024 * 1024
    val secureClient = OkHttpClient.Builder()
      .addNetworkInterceptor(Interceptor { chain ->
        val url = chain.request().url
        if (url.scheme != "https") {
          throw SecurityException("Only HTTPS image URLs are allowed")
        }
        val response = chain.proceed(chain.request())
        val contentLength = response.body?.contentLength() ?: -1
        if (contentLength > maxImageBytes) {
          response.close()
          throw SecurityException("Image response exceeds 5MB limit")
        }
        val originalBody = response.body ?: return@Interceptor response
        val limitedSource = object : ForwardingSource(originalBody.source()) {
          var bytesRead = 0L
          override fun read(sink: Buffer, byteCount: Long): Long {
            val read = super.read(sink, byteCount)
            if (read > 0) bytesRead += read
            if (bytesRead > maxImageBytes) {
              throw SecurityException("Image response exceeds 5MB streaming limit")
            }
            return read
          }
        }
        val limitedBody = limitedSource.buffer()
          .asResponseBody(originalBody.contentType(), originalBody.contentLength())
        response.newBuilder().body(limitedBody).build()
      })
      .build()
    return ImageLoader.Builder(context)
      .components { add(OkHttpNetworkFetcherFactory(callFactory = { secureClient })) }
      .build()
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

    // Clean up stale .tmp files from interrupted model imports to reclaim storage.
    // Runs early in startup so disk space is freed before the server or UI tries to load models.
    // Wrapped in try-catch so file system errors don't crash the app on startup.
    try {
      cleanupStaleImportTmpFiles(getExternalFilesDir(null))
    } catch (e: Exception) {
      Log.e("OlliteRTApp", "Failed to clean up stale import temp files", e)
    }

    // Create notification channels (safe to call on every start — no-ops if they exist).
    UpdateCheckWorker.createNotificationChannel(this)
    AllowlistRefreshWorker.createNotificationChannel(this)

    // Clear stale update notification if the app was auto-updated since the last check.
    // Also restores cached update info to ServerMetrics if an update is still pending.
    // Wrapped in try-catch so a failure doesn't crash the app on startup.
    try {
      UpdateCheckWorker.clearStaleNotification(this)
    } catch (e: Exception) {
      Log.e("OlliteRTApp", "Failed to clear stale update notification", e)
    }

    // Schedule periodic update checks if enabled.
    // Wrapped in try-catch so WorkManager failures don't crash the app on startup.
    try {
      if (LlmHttpPrefs.isUpdateCheckEnabled(this)) {
        UpdateCheckWorker.scheduleUpdateCheck(this)
      }
    } catch (e: Exception) {
      Log.e("OlliteRTApp", "Failed to schedule update check", e)
    }

    // Schedule periodic allowlist refresh for model update detection.
    try {
      AllowlistRefreshWorker.scheduleAllowlistRefresh(this)
    } catch (e: Exception) {
      Log.e("OlliteRTApp", "Failed to schedule allowlist refresh", e)
    }
  }
}
