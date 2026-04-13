package com.ollitert.llm.server.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ollitert.llm.server.ui.common.copyToClipboard

/**
 * BroadcastReceiver that copies the server endpoint URL to the clipboard.
 * Triggered from the "Copy URL" action in the foreground notification.
 */
class CopyUrlReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val url = intent.getStringExtra(EXTRA_URL) ?: return
    copyToClipboard(context, "OlliteRT Endpoint", url)
  }

  companion object {
    const val EXTRA_URL = "extra_url"
  }
}
