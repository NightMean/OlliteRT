/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.ui.common

import com.ollitert.llm.server.R
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import kotlin.math.ln
import kotlin.math.pow

// ── Shared UI dimension constants ──────────────────────────────────────────────
/** Max width for ModalBottomSheet content across the app. */
val SHEET_MAX_WIDTH = 640.dp
/** Max width for screen-level scrollable content (Models, Status, Logs, Settings). */
val SCREEN_CONTENT_MAX_WIDTH = 840.dp

/** Consistent error text for model loading failures across all screens. */
fun formatModelError(context: Context, error: String?): String = when {
  error.isNullOrBlank() -> context.getString(R.string.error_model_load_failed)
  error.length > 80 -> context.getString(R.string.error_model_load_failed_short)
  else -> context.getString(R.string.error_model_load_failed_detail, error)
}

val SMALL_BUTTON_CONTENT_PADDING =
  PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)

/** Format the bytes into a human-readable format. */
fun Long.humanReadableSize(si: Boolean = true, extraDecimalForGbAndAbove: Boolean = false): String {
  val bytes = this

  val unit = if (si) 1000 else 1024
  if (bytes < unit) return "$bytes B"
  val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
  val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
  var formatString = "%.1f %sB"
  if (extraDecimalForGbAndAbove && pre.lowercase() != "k" && pre != "M") {
    formatString = "%.2f %sB"
  }
  return formatString.format(bytes / unit.toDouble().pow(exp.toDouble()), pre)
}

fun checkNotificationPermissionAndStartDownload(
  context: Context,
  launcher: ManagedActivityResultLauncher<String, Boolean>,
  modelManagerViewModel: ModelManagerViewModel,
  model: Model,
) {
  // Check permission
  when (PackageManager.PERMISSION_GRANTED) {
    // Already got permission. Call the lambda.
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) -> {
      modelManagerViewModel.downloadModel(model = model)
    }

    // Otherwise, ask for permission
    else -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }
}

fun ensureValidFileName(fileName: String): String {
  return fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}

