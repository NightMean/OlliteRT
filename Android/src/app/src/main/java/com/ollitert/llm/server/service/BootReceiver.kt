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
import android.util.Log
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.MAX_VALID_PORT
import com.ollitert.llm.server.data.MIN_VALID_PORT

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
      if (port !in MIN_VALID_PORT..MAX_VALID_PORT) {
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
