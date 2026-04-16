package com.ollitert.llm.server.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ollitert.llm.server.data.LlmHttpPrefs

/**
 * Starts the LLM server on device boot if the user has enabled auto-start
 * and a default model is configured.
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

    // Wrap everything in try-catch — an uncaught exception here crashes on every boot
    // with no recovery if SharedPreferences are corrupted.
    try {
      if (!LlmHttpPrefs.isAutoStartOnBoot(context)) return

      val modelName = LlmHttpPrefs.getDefaultModelName(context)
      if (modelName.isNullOrBlank()) {
        Log.w(TAG, "Auto-start on boot enabled but no default model configured — skipping")
        return
      }

      val port = LlmHttpPrefs.getPort(context)
      if (port !in 1024..65535) {
        Log.w(TAG, "Invalid port $port from preferences — skipping auto-start")
        return
      }

      Log.i(TAG, "Auto-starting server on boot: model=$modelName, port=$port")
      LlmHttpService.start(context, port, modelName, source = LlmHttpService.SOURCE_BOOT)
    } catch (e: Exception) {
      Log.e(TAG, "Auto-start on boot failed: ${e.message}", e)
    }
  }

  companion object {
    private const val TAG = "OlliteRTBoot"
  }
}
