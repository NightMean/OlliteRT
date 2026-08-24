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

import com.ollitert.llm.server.common.ServerMetrics
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.ServerPrefs

internal object UpdateNotificationHelper {
  private const val TAG = "OlliteRT.UpdateNotif"
  const val UPDATE_CHANNEL_ID = "ollitert-app-update"
  const val UPDATE_NOTIFICATION_ID = 43
  private const val UPDATE_REQUEST_CODE = 100
  private const val UPDATE_DISMISS_REQUEST_CODE = 101
  const val BETA_RELEASE_CHANNEL_ID = "ollitert-beta-release"
  const val DEV_RELEASE_CHANNEL_ID = "ollitert-dev-release"
  const val CROSS_CHANNEL_NOTIFICATION_ID = 44
  private const val CROSS_CHANNEL_REQUEST_CODE = 102
  private const val CROSS_CHANNEL_DISMISS_REQUEST_CODE = 103

  fun buildUpdateIntent(context: Context, releaseHtmlUrl: String): Intent {
    val uri = if (isPlayStoreBuild(context)) {
      "market://details?id=${context.packageName}".toUri()
    } else {
      releaseHtmlUrl.toUri()
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    if (isPlayStoreBuild(context)) {
      try {
        if (intent.resolveActivity(context.packageManager) == null) {
          return Intent(Intent.ACTION_VIEW, releaseHtmlUrl.toUri())
        }
      } catch (_: Exception) {
        return Intent(Intent.ACTION_VIEW, releaseHtmlUrl.toUri())
      }
    }
    return intent
  }

  fun isPlayStoreBuild(context: Context): Boolean {
    return try {
      val info = context.packageManager.getInstallSourceInfo(context.packageName)
      info.installingPackageName == "com.android.vending"
    } catch (_: Exception) {
      false
    }
  }

  fun canPostUpdateNotification(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      ?: return false
    val channel = mgr.getNotificationChannel(UPDATE_CHANNEL_ID)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
  }

  fun isUpdateChannelMuted(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      ?: return false
    val channel = mgr.getNotificationChannel(UPDATE_CHANNEL_ID) ?: return false
    return channel.importance == NotificationManager.IMPORTANCE_NONE
  }

  fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(
      UPDATE_CHANNEL_ID,
      context.getString(R.string.notif_channel_app_update_name),
      NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
      description = context.getString(R.string.notif_channel_app_update_desc)
    }
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (mgr == null) {
      Log.e(TAG, "NotificationManager unavailable — cannot create update channel")
      return
    }
    mgr.createNotificationChannel(channel)
  }

  fun createCrossChannelNotificationChannels(context: Context) {
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (mgr == null) {
      Log.e(TAG, "NotificationManager unavailable — cannot create cross-channel notification channels")
      return
    }
    val betaChannel = NotificationChannel(
      BETA_RELEASE_CHANNEL_ID,
      context.getString(R.string.notif_channel_beta_release_name),
      NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
      description = context.getString(R.string.notif_channel_beta_release_desc)
    }
    val devChannel = NotificationChannel(
      DEV_RELEASE_CHANNEL_ID,
      context.getString(R.string.notif_channel_dev_release_name),
      NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
      description = context.getString(R.string.notif_channel_dev_release_desc)
    }
    mgr.createNotificationChannel(betaChannel)
    mgr.createNotificationChannel(devChannel)
  }

  fun canPostCrossChannelNotification(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      ?: return false
    val beta = mgr.getNotificationChannel(BETA_RELEASE_CHANNEL_ID)
    val dev = mgr.getNotificationChannel(DEV_RELEASE_CHANNEL_ID)
    val betaActive = beta == null || beta.importance != NotificationManager.IMPORTANCE_NONE
    val devActive = dev == null || dev.importance != NotificationManager.IMPORTANCE_NONE
    return betaActive || devActive
  }

  fun areCrossChannelChannelsMuted(context: Context): Boolean {
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      ?: return false
    val beta = mgr.getNotificationChannel(BETA_RELEASE_CHANNEL_ID)
    val dev = mgr.getNotificationChannel(DEV_RELEASE_CHANNEL_ID)
    val betaMuted = beta != null && beta.importance == NotificationManager.IMPORTANCE_NONE
    val devMuted = dev != null && dev.importance == NotificationManager.IMPORTANCE_NONE
    return betaMuted && devMuted
  }

  fun crossChannelNotificationChannelId(tag: String): String {
    return when {
      tag.contains("-dev.") -> DEV_RELEASE_CHANNEL_ID
      tag.contains("-beta.") -> BETA_RELEASE_CHANNEL_ID
      else -> UPDATE_CHANNEL_ID
    }
  }

  fun postUpdateNotification(context: Context, release: ReleaseInfo) {
    if (!canPostUpdateNotification(context)) {
      if (ServerPrefs.isVerboseDebugEnabled(context)) {
        RequestLogStore.addEvent(
          "Update check skipped — notification permission not granted",
          level = LogLevel.DEBUG,
          category = EventCategory.UPDATE,
        )
      }
      return
    }

    val tapIntent = buildUpdateIntent(context, release.htmlUrl)
    val contentIntent = PendingIntent.getActivity(
      context, UPDATE_REQUEST_CODE, tapIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val dismissIntent = PendingIntent.getBroadcast(
      context, UPDATE_DISMISS_REQUEST_CODE,
      Intent(context, UpdateDismissReceiver::class.java)
        .putExtra(UpdateDismissReceiver.EXTRA_DISMISSED_VERSION, release.tagName),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val versionDisplay = release.tagName.removePrefix("v")
    val notification = NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
      .setContentTitle(context.getString(R.string.notif_update_available_title))
      .setContentText(context.getString(R.string.notif_update_available_body, versionDisplay))
      .setSmallIcon(R.mipmap.ic_launcher_monochrome)
      .setContentIntent(contentIntent)
      .setDeleteIntent(dismissIntent)
      .setAutoCancel(true)
      .build()

    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (mgr == null) {
      Log.e(TAG, "NotificationManager unavailable — cannot post update notification")
      return
    }
    mgr.notify(UPDATE_NOTIFICATION_ID, notification)
  }

  fun postCrossChannelNotification(context: Context, release: ReleaseInfo) {
    val channelId = crossChannelNotificationChannelId(release.tagName)

    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    val channel = mgr.getNotificationChannel(channelId)
    if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

    val tapIntent = buildUpdateIntent(context, release.htmlUrl)
    val contentIntent = PendingIntent.getActivity(
      context, CROSS_CHANNEL_REQUEST_CODE, tapIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val dismissIntent = PendingIntent.getBroadcast(
      context, CROSS_CHANNEL_DISMISS_REQUEST_CODE,
      Intent(context, UpdateDismissReceiver::class.java)
        .putExtra(UpdateDismissReceiver.EXTRA_DISMISSED_VERSION, release.tagName)
        .putExtra(UpdateDismissReceiver.EXTRA_IS_CROSS_CHANNEL, true),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val versionDisplay = release.tagName.removePrefix("v")
    val titleRes = when {
      release.tagName.contains("-dev.") -> R.string.notif_cross_channel_title_dev
      release.tagName.contains("-beta.") -> R.string.notif_cross_channel_title_beta
      else -> R.string.notif_cross_channel_title_stable
    }

    val notification = NotificationCompat.Builder(context, channelId)
      .setContentTitle(context.getString(titleRes))
      .setContentText(context.getString(R.string.notif_cross_channel_body, versionDisplay))
      .setSmallIcon(R.mipmap.ic_launcher_monochrome)
      .setContentIntent(contentIntent)
      .setDeleteIntent(dismissIntent)
      .setAutoCancel(true)
      .build()

    mgr.notify(CROSS_CHANNEL_NOTIFICATION_ID, notification)
  }

  fun clearStaleNotification(context: Context) {
    val cached = ServerPrefs.getCachedLatestVersion(context) ?: return
    if (!SemVer.isNewer(BuildConfig.VERSION_NAME, cached)) {
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      mgr?.cancel(UPDATE_NOTIFICATION_ID)
      ServerPrefs.clearUpdateState(context)
      ServerMetrics.setAvailableUpdate(null, null)
      if (ServerPrefs.isVerboseDebugEnabled(context)) {
        RequestLogStore.addEvent(
          "Stale update notification cleared",
          level = LogLevel.DEBUG,
          category = EventCategory.UPDATE,
          body = "App updated to ${BuildConfig.VERSION_NAME}, cached latest was $cached",
        )
      }
    } else {
      val url = ServerPrefs.getCachedReleaseHtmlUrl(context)
      ServerMetrics.setAvailableUpdate(cached, url)
    }
  }
}
