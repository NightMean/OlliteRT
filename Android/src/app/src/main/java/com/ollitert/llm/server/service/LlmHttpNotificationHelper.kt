package com.ollitert.llm.server.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ollitert.llm.server.R

/**
 * Builds and updates the foreground service notification for LlmHttpService.
 * Extracted to isolate notification construction from service lifecycle and
 * inference concerns.
 *
 * The server notification supports three states:
 * - **Loading:** indeterminate progress bar, no action buttons
 * - **Running:** model name, request count, API URL, Stop/Copy actions
 * - **Error:** error message, Stop action only
 */
object LlmHttpNotificationHelper {

  const val CHANNEL_ID = "ollitert-server"
  const val NOTIFICATION_ID = 42

  /** Creates the notification channel (required on Android O+). Safe to call multiple times. */
  fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      if (mgr == null) {
        android.util.Log.e("LlmHttpNotification", "NotificationManager unavailable — cannot create channel")
        return
      }
      val ch = NotificationChannel(
        CHANNEL_ID,
        context.getString(R.string.llm_http_channel_name),
        NotificationManager.IMPORTANCE_LOW,
      )
      mgr.createNotificationChannel(ch)
    }
  }

  /**
   * Builds a foreground notification. Does not post it — the caller must
   * pass it to [android.app.Service.startForeground] or [NotificationManager.notify].
   */
  fun build(
    context: Context,
    title: String,
    text: String,
    contentIntent: PendingIntent,
    stopIntent: PendingIntent? = null,
    copyIntent: PendingIntent? = null,
    showProgress: Boolean = false,
  ): Notification {
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setSmallIcon(R.mipmap.ic_launcher_monochrome)
      .setContentIntent(contentIntent)
      .setOngoing(true)
    if (stopIntent != null) {
      builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.notif_action_stop_server), stopIntent)
    }
    if (copyIntent != null) {
      builder.addAction(android.R.drawable.ic_menu_share, context.getString(R.string.notif_action_copy_url), copyIntent)
    }
    if (showProgress) {
      builder.setProgress(0, 0, true) // indeterminate progress bar
    }
    return builder.build()
  }

  /**
   * Builds and immediately posts a notification update via [NotificationManager].
   * Used after the initial [android.app.Service.startForeground] call to update
   * the notification content (loading → running, running → error, etc.).
   */
  fun update(
    context: Context,
    title: String,
    text: String,
    contentIntent: PendingIntent,
    stopIntent: PendingIntent? = null,
    copyIntent: PendingIntent? = null,
    showProgress: Boolean = false,
  ) {
    val notification = build(context, title, text, contentIntent, stopIntent, copyIntent, showProgress)
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    if (mgr == null) {
      android.util.Log.e("LlmHttpNotification", "NotificationManager unavailable — cannot update notification")
      return
    }
    mgr.notify(NOTIFICATION_ID, notification)
  }

  /**
   * Refreshes the "Running" notification with the current request count and
   * optional update-available badge. Called after each inference request when
   * the "Show Request Count" preference is enabled.
   *
   * @param cachedUpdateVersion The version string from the background update checker,
   *   or null if no update is available. Appended as a subtle extra line.
   */
  fun refreshRunning(
    context: Context,
    modelName: String,
    endpointUrl: String,
    contentIntent: PendingIntent,
    stopIntent: PendingIntent?,
    copyIntent: PendingIntent?,
    cachedUpdateVersion: String?,
  ) {
    val count = ServerMetrics.requestCount.value
    val reqLabel = if (count == 1L) context.getString(R.string.notif_server_body_requests_one)
      else context.getString(R.string.notif_server_body_requests_many, count)
    // Append subtle "update available" line if a newer version was found by the background checker
    val updateLine = if (cachedUpdateVersion != null) "\n${context.getString(R.string.notif_server_body_update, cachedUpdateVersion.removePrefix("v"))}" else ""
    update(
      context = context,
      title = context.getString(R.string.notif_server_running_title),
      text = "${context.getString(R.string.notif_server_body_model, modelName)}\n$reqLabel\n${context.getString(R.string.notif_server_body_url, endpointUrl)}$updateLine",
      contentIntent = contentIntent,
      stopIntent = stopIntent,
      copyIntent = copyIntent,
    )
  }
}
