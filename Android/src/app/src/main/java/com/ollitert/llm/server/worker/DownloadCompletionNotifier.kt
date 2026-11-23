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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val CHANNEL_ID = "download_notification"
private const val NOTIFICATION_ID = 1
private const val TAG = "OlliteRT.Download"

internal class DownloadCompletionNotifier @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val lifecycleProvider: OlliteRTLifecycleProvider,
) {
  fun post(modelName: String, succeeded: Boolean) {
    if (lifecycleProvider.isAppInForeground) return
    try {
      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
      ) return

      val channel = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.notif_channel_download_name),
        NotificationManager.IMPORTANCE_HIGH,
      )
      val manager = NotificationManagerCompat.from(context)
      manager.createNotificationChannel(channel)
      val intent = if (succeeded) {
        Intent(Intent.ACTION_VIEW, "com.ollitert.llm.server://global_model_manager".toUri())
          .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
      } else {
        context.packageManager.getLaunchIntentForPackage(context.packageName)
          ?: Intent(context, context.javaClass)
      }
      val pendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      val title = if (succeeded) R.string.notification_title_success else R.string.notification_title_fail
      val text = if (succeeded) R.string.notification_content_success else R.string.notification_content_fail
      val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher_monochrome)
        .setContentTitle(context.getString(title))
        .setContentText(context.getString(text, modelName))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
      manager.notify(NOTIFICATION_ID, notification)
    } catch (e: Exception) {
      // Notifications are best-effort and cannot change the recorded download result.
      Log.w(TAG, "Could not post download completion notification", e)
    }
  }
}
