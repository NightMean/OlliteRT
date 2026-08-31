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

import com.ollitert.llm.server.common.ServerMetrics
import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.ollitert.llm.server.data.repository.ProtoDataStoreRepository
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.storage.cleanupStaleImportTmpFiles
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
import com.ollitert.llm.server.data.prefs.isUpdateCheckEnabled
import com.ollitert.llm.server.floatingmonitor.FloatingMonitorController
private const val TAG = "OlliteRT.App"

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

  @EarlyEntryPoint
  @InstallIn(SingletonComponent::class)
  interface WorkerFactoryEntryPoint {
    fun workerFactory(): HiltWorkerFactory
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface FloatingMonitorEntryPoint {
    fun floatingMonitorController(): FloatingMonitorController
    fun lifecycleProvider(): OlliteRTLifecycleProvider
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
        // ForwardingSource enforces byte limit on the actual stream — contentLength can be
        // missing (-1) or spoofed, so we must also verify bytes as they arrive.
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

    val floatingMonitorEntryPoint = EntryPointAccessors.fromApplication(this, FloatingMonitorEntryPoint::class.java)
    registerActivityLifecycleCallbacks(ForegroundLifecycleCallbacks(floatingMonitorEntryPoint.lifecycleProvider()))
    floatingMonitorEntryPoint.floatingMonitorController().start()

    // Initialize log persistence (registers callback on RequestLogStore, loads from DB if enabled).
    // Wrapped in try-catch so a persistence failure doesn't crash the entire app on startup.
    try {
      val entryPoint = EntryPointAccessors.fromApplication(this, PersistenceEntryPoint::class.java)
      entryPoint.requestLogPersistence().initialize()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize log persistence — logs will be in-memory only", e)
    }

    // Clean up stale .tmp files from interrupted model imports to reclaim storage.
    // Runs early in startup so disk space is freed before the server or UI tries to load models.
    // Wrapped in try-catch so file system errors don't crash the app on startup.
    try {
      cleanupStaleImportTmpFiles(getExternalFilesDir(null))
    } catch (e: Exception) {
      Log.e(TAG, "Failed to clean up stale import temp files", e)
    }

    // Migrate ha_stt_* prefs keys to stt_* (setting is not HA-specific).
    // TODO: Remove after 1.0.0 — one-time migration introduced in 0.9.0.
    try {
      ServerPrefs.migrateSttKeys(this)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to migrate STT prefs keys", e)
    }

    // Migrate legacy per-model prefs keys (system_prompt_<name> /
    // inference_config_<name>) before anything reads them. This previously ran
    // only when the Models screen opened, so a device that rebooted after an app
    // update silently used default per-model settings (empty system prompt!) for
    // auto-started inference until someone opened the Models screen once.
    try {
      val merger = com.ollitert.llm.server.data.allowlist.ModelCatalogMerger(
        externalFilesDir = getExternalFilesDir(null),
        appVersionName = BuildConfig.VERSION_NAME,
        assetReader = {
          try {
            assets.open(com.ollitert.llm.server.data.allowlist.MODEL_ALLOWLIST_FILENAME)
              .bufferedReader().use { it.readText() }
          } catch (_: Exception) { null }
        },
      )
      val nameToPrefsKey = merger.load()
        .map { it.toModel(appVersion = com.ollitert.llm.server.common.SemVer.parse(BuildConfig.VERSION_NAME)) }
        .filter { it.name != it.downloadFileName }
        .associate { it.name to it.downloadFileName }
      if (nameToPrefsKey.isNotEmpty()) {
        ServerPrefs.migratePerModelKeys(this, nameToPrefsKey)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to migrate per-model prefs keys", e)
    }

    // Create notification channels (safe to call on every start — no-ops if they exist).
    // Wrapped in try-catch: corrupted NotificationManager can throw, and this was the only
    // pair of calls in onCreate() not already protected.
    try {
      UpdateCheckWorker.createNotificationChannel(this)
      UpdateCheckWorker.createCrossChannelNotificationChannels(this)
      AllowlistRefreshWorker.createNotificationChannel(this)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to create notification channels — notifications may not work", e)
    }

    // Clear stale update notification if the app was auto-updated since the last check.
    // Also restores cached update info to ServerMetrics if an update is still pending.
    // Wrapped in try-catch so a failure doesn't crash the app on startup.
    try {
      UpdateCheckWorker.clearStaleNotification(this)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to clear stale update notification", e)
    }

    // Schedule periodic update checks if enabled.
    // Wrapped in try-catch so WorkManager failures don't crash the app on startup.
    try {
      if (ServerPrefs.isUpdateCheckEnabled(this)) {
        UpdateCheckWorker.scheduleUpdateCheck(this)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to schedule update check", e)
    }

    // Schedule periodic allowlist refresh for model update detection.
    try {
      AllowlistRefreshWorker.scheduleAllowlistRefresh(this)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to schedule allowlist refresh", e)
    }
  }

  private class ForegroundLifecycleCallbacks(
    private val lifecycleProvider: OlliteRTLifecycleProvider,
  ) : ActivityLifecycleCallbacks {
    private var startedActivityCount = 0

    override fun onActivityStarted(activity: Activity) {
      startedActivityCount++
      lifecycleProvider.isAppInForeground = true
    }

    override fun onActivityStopped(activity: Activity) {
      startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
      lifecycleProvider.isAppInForeground = startedActivityCount > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
  }
}
