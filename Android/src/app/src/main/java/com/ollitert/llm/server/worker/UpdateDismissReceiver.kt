package com.ollitert.llm.server.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore

/**
 * Records which update version the user dismissed by swiping the notification away.
 * Prevents re-posting the same notification on the next WorkManager cycle.
 * Registered as a deleteIntent on the update notification.
 */
class UpdateDismissReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val version = intent.getStringExtra(EXTRA_DISMISSED_VERSION) ?: return
    LlmHttpPrefs.setLastDismissedUpdateVersion(context, version)
    Log.d("UpdateDismiss", "User dismissed update notification for $version")
    if (LlmHttpPrefs.isVerboseDebugEnabled(context)) {
      RequestLogStore.addEvent(
        "Notification dismissed for $version",
        level = LogLevel.DEBUG,
        category = EventCategory.UPDATE,
      )
    }
  }

  companion object {
    const val EXTRA_DISMISSED_VERSION = "dismissed_version"
  }
}
