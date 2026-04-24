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

package com.ollitert.llm.server.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ollitert.llm.server.MainActivity
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.fetchBounded
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.MAX_MODELS_PER_REPO
import com.ollitert.llm.server.data.MAX_REPO_ERROR_LENGTH
import com.ollitert.llm.server.data.UNKNOWN_ERROR_FALLBACK
import com.ollitert.llm.server.data.ModelAllowlistJson
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.modelmanager.ModelAllowlistLoader
import com.ollitert.llm.server.ui.modelmanager.ModelFileManager
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that fetches the model allowlist, detects stale model
 * versions on disk, and fires per-model notifications when updates are available.
 *
 * Scheduled every 24 hours with network + battery constraints via [scheduleAllowlistRefresh].
 * On a phone in Doze mode (primary use case: phone in a drawer), the job may stretch
 * to 36-72h — acceptable since model updates are not time-sensitive.
 */
@HiltWorker
class AllowlistRefreshWorker @AssistedInject constructor(
  @Assisted appContext: Context,
  @Assisted workerParams: WorkerParameters,
  private val dataStoreRepository: DataStoreRepository,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val context = applicationContext
    val externalFilesDir = context.getExternalFilesDir(null)
    if (externalFilesDir == null) {
      Log.w(TAG, "External files dir unavailable — skipping")
      return Result.success()
    }

    val repos = dataStoreRepository.readRepositories()
    val allowlistLoader = ModelAllowlistLoader(context, externalFilesDir)
    val fileManager = ModelFileManager(context, externalFilesDir)
    val allUpdatableModels = mutableListOf<UpdatableInfo>()
    val nonUpdatableDownloaded = mutableListOf<String>()
    val seenModelIds = mutableSetOf<String>()
    var enabledRepoCount = 0
    var failedRepoCount = 0

    for (repo in repos) {
      if (!repo.enabled || repo.url.isBlank()) continue
      enabledRepoCount++
      try {
        val rawJson = fetchBounded(repo.url, userAgent = "OlliteRT-AllowlistRefresh") ?: continue

        val allowlist = ModelAllowlistJson.decode(rawJson)
        if (allowlist.models.isEmpty()) {
          Log.w(TAG, "Repo '${repo.id}': fetched allowlist is empty — skipping")
          continue
        }

        var minVersion = repo.contentVersion
        if (repo.isBuiltIn) {
          val bundled = allowlistLoader.readFromAssets()
          if (bundled != null && bundled.contentVersion > minVersion) {
            minVersion = bundled.contentVersion
          }
        }
        if (allowlist.contentVersion <= minVersion) {
          Log.d(TAG, "Repo '${repo.id}': v${allowlist.contentVersion} <= min v$minVersion — skipping")
          if (repo.lastError.isNotEmpty()) {
            dataStoreRepository.updateRepository(repo.copy(lastRefreshMs = System.currentTimeMillis(), lastError = ""))
          }
          continue
        }

        allowlistLoader.saveToDisk(rawJson, repo.cacheFilename)
        if (allowlistLoader.readFromDiskCache(repo.cacheFilename) == null) {
          Log.w(TAG, "Disk cache write failed for '${repo.id}' — skipping DataStore update")
          continue
        }
        dataStoreRepository.updateRepository(
          repo.copy(
            contentVersion = allowlist.contentVersion,
            lastRefreshMs = System.currentTimeMillis(),
            lastError = "",
          )
        )
        Log.d(TAG, "Repo '${repo.id}' refreshed: v${allowlist.contentVersion}")

        for (allowedModel in allowlist.models.take(MAX_MODELS_PER_REPO)) {
          if (allowedModel.modelId in seenModelIds) continue
          seenModelIds.add(allowedModel.modelId)
          val model = allowedModel.toModel()
          if (fileManager.isModelDownloaded(model)) {
            if (model.updatable) {
              allUpdatableModels.add(UpdatableInfo(
                name = model.name,
                displayName = model.displayName.ifEmpty { model.name },
                latestVersion = allowedModel.commitHash,
              ))
            } else {
              nonUpdatableDownloaded.add(model.name)
            }
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to refresh repo '${repo.id}'", e)
        failedRepoCount++
        dataStoreRepository.updateRepository(repo.copy(lastError = e.message?.take(MAX_REPO_ERROR_LENGTH) ?: UNKNOWN_ERROR_FALLBACK))
      }
    }

    if (enabledRepoCount > 0 && failedRepoCount == enabledRepoCount) {
      Log.w(TAG, "All $enabledRepoCount enabled repos failed — requesting retry")
      return Result.retry()
    }

    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    for (name in nonUpdatableDownloaded) {
      mgr?.cancel(modelUpdateNotificationId(name))
    }

    val ignoredSet = LlmHttpPrefs.getIgnoredModelUpdates(context)
    for (entry in ignoredSet) {
      val entryName = entry.substringBefore(":")
      if (allUpdatableModels.none { it.name == entryName }) {
        LlmHttpPrefs.removeIgnoredModelUpdate(context, entry)
      }
    }

    if (allUpdatableModels.isNotEmpty() && canPostModelUpdateNotification(context)) {
      val refreshedIgnored = LlmHttpPrefs.getIgnoredModelUpdates(context)
      for (info in allUpdatableModels) {
        val dedupKey = "${info.name}:${info.latestVersion}"
        if (dedupKey in refreshedIgnored) continue
        postModelUpdateNotification(context, info.name, info.displayName)
      }
    }

    if (allUpdatableModels.isNotEmpty()) {
      val message = if (allUpdatableModels.size == 1) {
        "Model update available: ${allUpdatableModels[0].displayName}"
      } else {
        "Model updates available: ${allUpdatableModels.joinToString(", ") { it.displayName }}"
      }
      RequestLogStore.addEvent(message, level = LogLevel.INFO, category = EventCategory.MODEL)
    }

    return Result.success()
  }

  private fun postModelUpdateNotification(context: Context, modelName: String, displayName: String) {
    val tapIntent = Intent(context, MainActivity::class.java).apply {
      putExtra(EXTRA_MODEL_UPDATE_NAME, modelName)
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      modelUpdateNotificationId(modelName),
      tapIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val notification = NotificationCompat.Builder(context, MODEL_UPDATE_CHANNEL_ID)
      .setContentTitle(context.getString(R.string.notif_model_update_title))
      .setContentText(context.getString(R.string.notif_model_update_text, displayName))
      .setSmallIcon(R.mipmap.ic_launcher_monochrome)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (mgr == null) {
      Log.e(TAG, "NotificationManager unavailable — cannot post model update notification")
      return
    }
    mgr.notify(modelUpdateNotificationId(modelName), notification)
  }

  private data class UpdatableInfo(
    val name: String,
    val displayName: String,
    val latestVersion: String,
  )

  companion object {
    private const val TAG = "AllowlistRefresh"
    private const val WORK_NAME = "allowlist_refresh_work"
    const val MODEL_UPDATE_CHANNEL_ID = "ollitert-model-update"
    const val EXTRA_MODEL_UPDATE_NAME = "model_update_name"
    private const val MODEL_UPDATE_BASE_NOTIFICATION_ID = 1000
    private const val MODEL_UPDATE_ID_RANGE = 10_000

    fun modelUpdateNotificationId(modelName: String): Int =
      MODEL_UPDATE_BASE_NOTIFICATION_ID + (modelName.hashCode() and 0x7FFFFFFF) % MODEL_UPDATE_ID_RANGE

    fun canPostModelUpdateNotification(context: Context): Boolean {
      if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return false
      val channel = mgr.getNotificationChannel(MODEL_UPDATE_CHANNEL_ID)
      return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun createNotificationChannel(context: Context) {
      val channel = NotificationChannel(
        MODEL_UPDATE_CHANNEL_ID,
        context.getString(R.string.notif_channel_model_update_name),
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = context.getString(R.string.notif_channel_model_update_desc)
      }
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      if (mgr == null) {
        Log.e(TAG, "NotificationManager unavailable — cannot create model update channel")
        return
      }
      mgr.createNotificationChannel(channel)
    }

    fun scheduleAllowlistRefresh(context: Context) {
      val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .build()

      val request = PeriodicWorkRequestBuilder<AllowlistRefreshWorker>(24, TimeUnit.HOURS)
        .setConstraints(constraints)
        .setInitialDelay(1, TimeUnit.HOURS)
        .build()

      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    }
  }
}
