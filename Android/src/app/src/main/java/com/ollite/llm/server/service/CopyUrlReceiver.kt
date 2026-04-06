package com.ollite.llm.server.service

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * BroadcastReceiver that copies the server endpoint URL to the clipboard.
 * Triggered from the "Copy URL" action in the foreground notification.
 */
class CopyUrlReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val url = intent.getStringExtra(EXTRA_URL) ?: return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Ollite Endpoint", url))
    Toast.makeText(context, "Copied: $url", Toast.LENGTH_SHORT).show()
  }

  companion object {
    const val EXTRA_URL = "extra_url"
  }
}
