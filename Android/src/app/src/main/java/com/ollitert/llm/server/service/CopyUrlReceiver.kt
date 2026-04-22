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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.copyToClipboard

/**
 * BroadcastReceiver that copies the server endpoint URL to the clipboard.
 * Triggered from the "Copy URL" action in the foreground notification.
 */
class CopyUrlReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    try {
      val url = intent.getStringExtra(EXTRA_URL) ?: return
      copyToClipboard(context, context.getString(R.string.clipboard_label_endpoint), url)
    } catch (_: Exception) {
      // Unhandled exceptions in onReceive() crash the app. Some OEMs throw
      // SecurityException from ClipboardManager when called from a notification action.
    }
  }

  companion object {
    const val EXTRA_URL = "extra_url"
  }
}
