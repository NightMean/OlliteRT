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
import android.util.Log
import android.widget.Toast
import com.google.gson.Gson
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.HTTP_CONNECT_TIMEOUT_MS
import com.ollitert.llm.server.data.HTTP_READ_TIMEOUT_MS
import java.net.HttpURLConnection
import java.net.URL

fun cleanUpMediapipeTaskErrorMessage(message: String): String {
  val index = message.indexOf("=== Source Location Trace")
  if (index >= 0) {
    return message.substring(0, index)
  }
  return message
}

inline fun <reified T> getJsonResponse(url: String): JsonObjAndTextContent<T>? {
  var connection: HttpURLConnection? = null
  try {
    connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
    connection.readTimeout = HTTP_READ_TIMEOUT_MS
    connection.connect()

    val responseCode = connection.responseCode
    if (responseCode == HttpURLConnection.HTTP_OK) {
      val response = connection.inputStream.bufferedReader().use { it.readText() }

      val jsonObj = parseJson<T>(response)
      return if (jsonObj != null) {
        JsonObjAndTextContent(jsonObj = jsonObj, textContent = response)
      } else {
        null
      }
    } else {
      Log.e("AGUtils", "HTTP error: $responseCode")
    }
  } catch (e: Exception) {
    Log.e("AGUtils", "Error when getting or parsing json response", e)
  } finally {
    connection?.disconnect()
  }

  return null
}

/** Parses a JSON string into an object of type [T] using Gson. */
inline fun <reified T> parseJson(response: String): T? {
  return try {
    val gson = Gson()
    gson.fromJson(response, T::class.java)
  } catch (e: Exception) {
    Log.e("AGUtils", "Error parsing JSON string", e)
    null
  }
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

/** Format a byte count as a human-readable string (B/KB/MB), using binary 1024 thresholds. */
fun formatByteSize(bytes: Long): String = when {
  bytes < 1024L -> "$bytes B"
  bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
  else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Int overload for Compose contexts where sizes come as Int (e.g. String.length). */
fun formatByteSize(bytes: Int): String = formatByteSize(bytes.toLong())
