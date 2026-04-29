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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore

/**
 * Records which update version the user dismissed by swiping the notification away.
 * Prevents re-posting the same notification on the next WorkManager cycle.
 * Registered as a deleteIntent on the update notification.
 */
private const val TAG = "OlliteRT.UpdateDismiss"

class UpdateDismissReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val version = intent.getStringExtra(EXTRA_DISMISSED_VERSION) ?: return
    val isCrossChannel = intent.getBooleanExtra(EXTRA_IS_CROSS_CHANNEL, false)

    if (isCrossChannel) {
      ServerPrefs.setLastDismissedCrossChannelVersion(context, version)
      Log.d(TAG, "User dismissed cross-channel notification for $version")
    } else {
      ServerPrefs.setLastDismissedUpdateVersion(context, version)
      Log.d(TAG, "User dismissed update notification for $version")
    }

    if (ServerPrefs.isVerboseDebugEnabled(context)) {
      val prefix = if (isCrossChannel) "Cross-channel notification" else "Notification"
      RequestLogStore.addEvent(
        "$prefix dismissed for $version",
        level = LogLevel.DEBUG,
        category = EventCategory.UPDATE,
      )
    }
  }

  companion object {
    const val EXTRA_DISMISSED_VERSION = "dismissed_version"
    const val EXTRA_IS_CROSS_CHANNEL = "is_cross_channel"
  }
}
