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
import android.os.Build
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
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.data.HTTP_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.HTTP_READ_TIMEOUT_MS
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_FILENAME
import com.ollitert.llm.server.data.ModelAllowlistJson
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.modelmanager.ModelFileManager
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that fetches the model allowlist, detects stale model
 * versions on disk, and fires per-model notifications when updates are available.
 *
 * Scheduled every 24 hours with network + battery constraints via [scheduleAllowlistRefresh].
 * On a phone in Doze mode (primary use case: phone in a drawer), the job may stretch
 * to 36-72h — acceptable since model updates are not time-sensitive.
 */
class AllowlistRefreshWorker(
  appContext: Context,
  workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

  override suspend fun doWork(): Result {
    val context = applicationContext
    try {
      val rawJson = fetchAllowlistJson() ?: return Result.success()

      val allowlist = ModelAllowlistJson.decode(rawJson)
      if (allowlist.models.isEmpty()) {
        Log.w(TAG, "Fetched allowlist is empty — skipping disk write to preserve cache")
        return Result.success()
      }

      val cachedVersion = LlmHttpPrefs.getAllowlistContentVersion(context)
      if (allowlist.contentVersion <= cachedVersion) {
        Log.d(TAG, "Fetched contentVersion ${allowlist.contentVersion} <= cached $cachedVersion — skipping")
        return Result.success()
      }

      val externalFilesDir = context.getExternalFilesDir(null)
      if (externalFilesDir == null) {
        Log.w(TAG, "External files dir unavailable — skipping")
        return Result.success()
      }

      val fileManager = ModelFileManager(context, externalFilesDir)
      // Collect models that are downloaded but have a newer version available.
      // isModelDownloaded() mutates the Model (sets updatable, version, downloadFileName)
      // but these are throwaway instances — the ViewModel's models are not affected.
      val updatableModels = mutableListOf<UpdatableInfo>()

      for (allowedModel in allowlist.models) {
        val model = allowedModel.toModel()
        if (fileManager.isModelDownloaded(model) && model.updatable) {
          updatableModels.add(UpdatableInfo(
            name = model.name,
            displayName = model.displayName.ifEmpty { model.name },
            latestVersion = allowedModel.commitHash,
          ))
        }
      }

      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

      // Clean up ignored entries for models that are no longer updatable (user downloaded the update)
      val ignoredSet = LlmHttpPrefs.getIgnoredModelUpdates(context)
      for (entry in ignoredSet) {
        val entryName = entry.substringBefore(":")
        if (updatableModels.none { it.name == entryName }) {
          LlmHttpPrefs.removeIgnoredModelUpdate(context, entry)
        }
      }

      // Cancel notifications for models that are no longer updatable
      for (allowedModel in allowlist.models) {
        val model = allowedModel.toModel()
        if (fileManager.isModelDownloaded(model) && !model.updatable) {
          mgr?.cancel(modelUpdateNotificationId(model.name))
        }
      }

      // Fire per-model notifications
      if (updatableModels.isNotEmpty() && canPostModelUpdateNotification(context)) {
        val refreshedIgnored = LlmHttpPrefs.getIgnoredModelUpdates(context)
        for (info in updatableModels) {
          val dedupKey = "${info.name}:${info.latestVersion}"
          if (dedupKey in refreshedIgnored) continue
          postModelUpdateNotification(context, info.name, info.displayName)
        }
      }

      if (updatableModels.isNotEmpty()) {
        val message = if (updatableModels.size == 1) {
          "Model update available: ${updatableModels[0].displayName}"
        } else {
          "Model updates available: ${updatableModels.joinToString(", ") { it.displayName }}"
        }
        RequestLogStore.addEvent(message, level = LogLevel.INFO, category = EventCategory.MODEL)
      }

      saveToDisk(externalFilesDir, rawJson)
      LlmHttpPrefs.setAllowlistContentVersion(context, allowlist.contentVersion)

    } catch (e: Exception) {
      Log.w(TAG, "Allowlist refresh failed", e)
    }
    return Result.success()
  }

  private fun fetchAllowlistJson(): String? {
    val connection = URL(GitHubConfig.ALLOWLIST_URL).openConnection() as HttpURLConnection
    try {
      connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
      connection.readTimeout = HTTP_READ_TIMEOUT_MS
      connection.setRequestProperty("User-Agent", "OlliteRT-AllowlistRefresh")
      val code = connection.responseCode
      if (code !in 200..299) {
        Log.w(TAG, "Allowlist fetch failed: HTTP $code")
        return null
      }
      return connection.inputStream.bufferedReader().readText()
    } finally {
      connection.disconnect()
    }
  }

  private fun saveToDisk(externalFilesDir: File, content: String) {
    try {
      val file = File(externalFilesDir, MODEL_ALLOWLIST_FILENAME)
      val tmpFile = File(externalFilesDir, "$MODEL_ALLOWLIST_FILENAME.tmp")
      tmpFile.writeText(content)
      tmpFile.renameTo(file)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save allowlist to disk", e)
    }
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
    private const val MODEL_UPDATE_BASE_NOTIFICATION_ID = 45

    fun modelUpdateNotificationId(modelName: String): Int =
      MODEL_UPDATE_BASE_NOTIFICATION_ID + (modelName.hashCode() and 0xFFFF)

    fun canPostModelUpdateNotification(context: Context): Boolean {
      if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return false
      val channel = mgr.getNotificationChannel(MODEL_UPDATE_CHANNEL_ID)
      return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun createNotificationChannel(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
