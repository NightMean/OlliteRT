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

package com.ollitert.llm.server.service

import com.ollitert.llm.server.common.ServerMetrics
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.data.prefs.isNotifShowRequestCount
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ollitert.llm.server.MainActivity
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_LONG_CHARS
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.prefs.formatHostForUrl

/** Notification intents and URL metadata passed to the model load thread. */
data class LoadNotificationState(
  val advertisedHost: String,
  val isLoopbackOnly: Boolean,
  val contentIntent: PendingIntent,
  val stopIntent: PendingIntent,
  val copyIntent: PendingIntent,
  val resetPositionIntent: PendingIntent,
  val endpointUrl: String,
)

private data class RunningNotificationState(
  val modelName: String,
  val contentIntent: PendingIntent,
  val stopIntent: PendingIntent,
  val copyIntent: PendingIntent,
  val resetPositionIntent: PendingIntent,
  val endpointUrl: String,
)

/**
 * Encapsulates notification creation, update, and intent building for [ServerService].
 * Isolates Android system notification orchestration from service lifecycle and inference.
 */
class ServerNotificationManager(private val context: Context) {

  // Model loading publishes this snapshot from a service coroutine while Ktor
  // request threads read it to refresh live notification metrics.
  @Volatile private var runningNotificationState: RunningNotificationState? = null

  /** Clears cached notification intent references on service shutdown. */
  fun clear() {
    runningNotificationState = null
  }

  /** Starts the foreground service with a minimal placeholder notification to meet the Android 10s deadline. */
  fun startForegroundPlaceholder(service: Service) {
    val placeholderIntent = Intent(context, MainActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    val placeholderContentIntent = PendingIntent.getActivity(
      context, 0, placeholderIntent, PendingIntent.FLAG_IMMUTABLE,
    )
    val placeholderNotification = NotificationHelper.build(
      context = context,
      title = context.getString(R.string.notif_starting_title),
      text = context.getString(R.string.notif_starting_body),
      contentIntent = placeholderContentIntent,
      showProgress = true,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      service.startForeground(
        NotificationHelper.NOTIFICATION_ID,
        placeholderNotification,
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
      )
    } else {
      service.startForeground(NotificationHelper.NOTIFICATION_ID, placeholderNotification)
    }
  }

  /** Builds notification PendingIntents (content, stop, copy URL) for the running server. */
  fun buildNotificationIntents(
    displayAddress: String,
    isLoopbackOnly: Boolean,
    port: Int,
  ): LoadNotificationState {
    val contentIntent = PendingIntent.getActivity(
      context, 0,
      Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
      PendingIntent.FLAG_IMMUTABLE,
    )
    val stopIntent = PendingIntent.getService(
      context, 1,
      Intent(context, ServerService::class.java).apply { action = ServerService.ACTION_STOP },
      PendingIntent.FLAG_IMMUTABLE,
    )
    val endpointUrl = "http://${formatHostForUrl(displayAddress)}:$port/v1"
    val copyIntent = PendingIntent.getBroadcast(
      context, 2,
      Intent(context, CopyUrlReceiver::class.java).apply {
        putExtra(CopyUrlReceiver.EXTRA_URL, endpointUrl)
      },
      PendingIntent.FLAG_IMMUTABLE,
    )
    val resetPositionIntent = PendingIntent.getService(
      context, 3,
      Intent(context, ServerService::class.java).apply {
        action = ServerService.ACTION_RESET_FLOATING_MONITOR_POSITION
      },
      PendingIntent.FLAG_IMMUTABLE,
    )
    return LoadNotificationState(
      advertisedHost = displayAddress,
      isLoopbackOnly = isLoopbackOnly,
      contentIntent = contentIntent,
      stopIntent = stopIntent,
      copyIntent = copyIntent,
      resetPositionIntent = resetPositionIntent,
      endpointUrl = endpointUrl,
    )
  }

  /** Updates notification intents and displays the running notification. */
  fun updateToRunning(model: Model, notifState: LoadNotificationState) {
    runningNotificationState = RunningNotificationState(
      modelName = model.name,
      contentIntent = notifState.contentIntent,
      stopIntent = notifState.stopIntent,
      copyIntent = notifState.copyIntent,
      resetPositionIntent = notifState.resetPositionIntent,
      endpointUrl = notifState.endpointUrl,
    )
    val initialText = buildString {
      if (ServerPrefs.isNotifShowRequestCount(context)) {
        append(context.resources.getQuantityString(R.plurals.notif_server_body_requests, 0, 0))
        append("\n")
      }
      append(context.getString(R.string.notif_server_body_model, model.name))
      append("\n")
      append(context.getString(R.string.notif_server_body_url, notifState.endpointUrl))
    }
    NotificationHelper.update(
      context = context,
      title = context.getString(R.string.notif_server_running_title),
      text = initialText,
      contentIntent = notifState.contentIntent,
      stopIntent = notifState.stopIntent,
      copyIntent = notifState.copyIntent,
      resetPositionIntent = notifState.resetPositionIntent,
    )
  }

  /** Handles model load failure: error reporting and notification update. */
  fun updateToLoadFailed(t: Throwable, model: Model, notifState: LoadNotificationState) {
    val msg = t.message?.take(LOG_ERROR_PREVIEW_LONG_CHARS) ?: context.getString(R.string.error_model_init_unknown)
    NotificationHelper.update(
      context = context,
      title = context.getString(R.string.notif_model_load_failed_title),
      text = msg,
      contentIntent = notifState.contentIntent,
      stopIntent = notifState.stopIntent,
    )
  }

  /** Update the foreground notification with the current request count and optional update badge. */
  fun refreshRunning() {
    val state = runningNotificationState ?: return
    NotificationHelper.refreshRunning(
      context = context,
      modelName = state.modelName,
      endpointUrl = state.endpointUrl,
      contentIntent = state.contentIntent,
      stopIntent = state.stopIntent,
      copyIntent = state.copyIntent,
      resetPositionIntent = state.resetPositionIntent,
      cachedUpdateVersion = ServerMetrics.availableUpdateVersion.value,
    )
  }

  /**
   * Checks for DataStore corruption that was detected during lazy initialization
   * (flagged via SharedPreferences by the ReplaceFileCorruptionHandler in AppModule).
   * Logs a WARNING event to the in-app log and posts a system notification so the
   * user knows their settings/data were reset.
   */
  fun checkCorruptedDataStores() {
    val corrupted = ServerPrefs.getCorruptedDataStores(context)
    if (corrupted.isEmpty()) return

    val names = corrupted.sorted().joinToString(", ")
    Log.w(TAG, "DataStore corruption recovered on previous run: $names")
    RequestLogStore.addEvent(
      context.getString(R.string.log_corruption_recovered, names),
      level = LogLevel.WARNING,
      category = EventCategory.SERVER,
    )

    val channelId = "ollitert-corruption"
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (mgr != null) {
      mgr.createNotificationChannel(
        android.app.NotificationChannel(
          channelId,
          context.getString(R.string.notif_channel_corruption_name),
          NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notif_channel_corruption_desc) }
      )
      val openIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_IMMUTABLE,
      )
      val text = if (corrupted.size == 1)
        context.getString(R.string.notif_corruption_text_one, corrupted.first())
      else
        context.getString(R.string.notif_corruption_text_many, corrupted.size, names)
      val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.mipmap.ic_launcher_monochrome)
        .setContentTitle(context.getString(R.string.notif_corruption_title))
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .build()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "POST_NOTIFICATIONS not granted — corruption notification suppressed")
      } else {
        mgr.notify(NOTIFICATION_ID_CORRUPTION, notification)
      }
    }

    ServerPrefs.clearCorruptedDataStores(context)
  }

  companion object {
    private const val TAG = "OlliteRT.NotifyMgr"
    private const val NOTIFICATION_ID_CORRUPTION = 44
  }
}
