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

package com.ollitert.llm.server.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.ollitert.llm.server.R
import kotlin.math.ln
import kotlin.math.pow

fun cleanUpLiteRtErrorMessage(message: String): String {
  val index = message.indexOf("=== Source Location Trace")
  if (index >= 0) {
    return message.substring(0, index)
  }
  return message
}

fun isPixel10(): Boolean {
  return Build.MODEL != null && Build.MODEL.lowercase().contains("pixel 10")
}

/**
 * Copy text to the system clipboard with a standardized toast notification.
 *
 * @param label Clipboard metadata label (prefix with "OlliteRT", e.g. "OlliteRT Endpoint").
 *              Visible in clipboard manager apps, not shown to the user directly.
 * @param text The content to copy.
 * @param formatSuffix Optional format hint appended to the toast (e.g. "JSON", "CSV").
 *                     Omit for simple values like URLs or tokens.
 */
fun copyToClipboard(context: Context, label: String, text: String, formatSuffix: String? = null, toastOverride: String? = null) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
  if (clipboard == null) {
    Toast.makeText(context, context.getString(R.string.toast_clipboard_unavailable), Toast.LENGTH_SHORT).show()
    return
  }
  clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
  val toast = toastOverride
    ?: if (formatSuffix != null) context.getString(R.string.toast_copied_to_clipboard_format, formatSuffix)
    else context.getString(R.string.toast_copied_to_clipboard)
  Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

/** Format a byte count as a human-readable string (e.g. "1.5 kB", "3.20 GB"). */
fun Long.humanReadableSize(si: Boolean = true, extraDecimalForGbAndAbove: Boolean = false): String {
  val bytes = this
  val unit = if (si) 1000 else 1024
  if (bytes < unit) return "$bytes B"
  val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
  val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
  val formatString = if (extraDecimalForGbAndAbove && pre.lowercase() != "k" && pre != "M") "%.2f %sB" else "%.1f %sB"
  return String.format(java.util.Locale.US, formatString, bytes / unit.toDouble().pow(exp.toDouble()), pre)
}

/** Int overload for contexts where sizes come as Int (e.g. String.length). */
fun Int.humanReadableSize(si: Boolean = true, extraDecimalForGbAndAbove: Boolean = false): String =
  toLong().humanReadableSize(si, extraDecimalForGbAndAbove)
