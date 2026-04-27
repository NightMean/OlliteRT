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

package com.ollitert.llm.server.ui.server.logs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.copyToClipboard
import com.ollitert.llm.server.service.RequestLogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Convert a single log entry to a JsonElement for structured export. */
internal fun entryToJson(entry: RequestLogEntry): JsonElement = buildJsonObject {
  put("id", entry.id)
  put("timestamp", formatTimestamp(entry.timestamp))
  put("timestamp_ms", entry.timestamp)
  put("type", if (entry.method == "EVENT") "event" else "request")

  if (entry.method == "EVENT") {
    put("message", entry.path)
    put("category", entry.eventCategory.name.lowercase())
    put("level", entry.level.name.lowercase())
    if (!entry.requestBody.isNullOrBlank()) {
      put("data", tryParseJson(entry.requestBody))
    }
  } else {
    put("method", entry.method)
    put("path", entry.path)
    put("status_code", entry.statusCode)
    put("latency_ms", entry.latencyMs)
    put("tokens", entry.tokens)
    put("streaming", entry.isStreaming)
    if (entry.isThinking) put("thinking", true)
    if (entry.isCompacted) {
      put("compacted", true)
      if (!entry.compactionDetails.isNullOrBlank()) put("compaction_details", entry.compactionDetails)
    }
    if (entry.isCancelled) {
      put("cancelled", true)
      if (entry.cancelledByUser) put("cancelled_by_user", true)
    }
    if (entry.inputTokenEstimate > 0) {
      put("input_token_estimate", entry.inputTokenEstimate)
      put("is_exact_token_count", entry.isExactTokenCount)
    }
    if (entry.maxContextTokens > 0) put("max_context_tokens", entry.maxContextTokens)
    if (entry.ignoredClientParams != null) put("ignored_client_params", entry.ignoredClientParams)
    if (entry.ttfbMs > 0) put("ttfb_ms", entry.ttfbMs)
    if (entry.decodeSpeed > 0) put("decode_speed_tps", entry.decodeSpeed)
    if (entry.prefillSpeed > 0) put("prefill_speed_tps", entry.prefillSpeed)
    if (entry.itlMs > 0) put("itl_ms", entry.itlMs)
    if (entry.clientIp != null) put("client_ip", entry.clientIp)

    if (!entry.requestBody.isNullOrBlank()) {
      put("request_body", tryParseJson(entry.requestBody))
    }
    if (!entry.compactedPrompt.isNullOrBlank()) {
      put("compacted_prompt", entry.compactedPrompt)
    }
    if (!entry.responseBody.isNullOrBlank()) {
      put("response_body", tryParseJson(entry.responseBody))
    }
  }

  if (entry.modelName != null) put("model", entry.modelName)
}

private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

/** Build the full JSON export as a formatted string. */
internal fun buildLogsJson(entries: List<RequestLogEntry>): String {
  val root = buildJsonObject {
    put("exported_at", formatTimestamp(System.currentTimeMillis()))
    put("app", "OlliteRT")
    put("entry_count", entries.size)
    put("entries", buildJsonArray {
      for (entry in entries) {
        add(entryToJson(entry))
      }
    })
  }
  return prettyJson.encodeToString(JsonElement.serializer(), root)
}

/** Build JSON on a background thread to avoid UI jank with large log sets (2500+ entries). */
internal suspend fun copyAllLogsToClipboard(context: Context, entries: List<RequestLogEntry>) {
  try {
    val json = withContext(Dispatchers.Default) { buildLogsJson(entries) }
    copyToClipboard(
      context, "OlliteRT Logs", json,
      toastOverride = context.getString(R.string.toast_copied_entries_json, entries.size)
    )
  } catch (_: RuntimeException) {
    // TransactionTooLargeException (Binder ~1MB limit) or other clipboard failures.
    Toast.makeText(
      context,
      context.getString(R.string.toast_clipboard_too_large, entries.size),
      Toast.LENGTH_LONG,
    ).show()
  }
}

/** Build JSON and write file on a background thread to avoid UI jank with large log sets. */
internal suspend fun exportLogsAsJson(context: Context, entries: List<RequestLogEntry>) {
  try {
    val file = withContext(Dispatchers.IO) {
      val json = buildLogsJson(entries)
      val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val cacheDir = File(context.cacheDir, "log_exports")
      cacheDir.mkdirs()
      val f = File(cacheDir, "ollitert_logs_$timestamp.json")
      f.writeText(json, Charsets.UTF_8)
      f
    }

    val uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.provider",
      file,
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/json"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.logs_export_chooser_title)))
  } catch (e: Exception) {
    Toast.makeText(context, context.getString(R.string.toast_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
  }
}

internal fun copyEntryToClipboard(context: Context, entry: RequestLogEntry) {
  val json = prettyJson.encodeToString(JsonElement.serializer(), entryToJson(entry))
  copyToClipboard(context, "OlliteRT Log Entry", json, formatSuffix = "JSON")
}

/** Dump the app's logcat buffer to a file and open the system share sheet. */
internal suspend fun exportLogcat(context: Context) {
  try {
    val file = withContext(Dispatchers.IO) {
      val cacheDir = File(context.cacheDir, "log_exports")
      cacheDir.mkdirs()
      // Clean up old logcat exports (keep at most 3)
      cacheDir.listFiles { f -> f.name.startsWith("ollitert_logcat_") }
        ?.sortedByDescending { it.lastModified() }
        ?.drop(3)
        ?.forEach { it.delete() }

      val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val f = File(cacheDir, "ollitert_logcat_$timestamp.txt")
      // Redirect stderr to stdout so the process can't deadlock on a full stderr buffer
      val process = ProcessBuilder("logcat", "-d", "-v", "threadtime")
        .redirectErrorStream(true)
        .start()
      // Stream directly from process to file to avoid holding the entire buffer in memory
      process.inputStream.use { input ->
        f.outputStream().use { output -> input.copyTo(output) }
      }
      process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
      f
    }

    val uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.provider",
      file,
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.logcat_export_chooser_title)))
  } catch (e: Exception) {
    Toast.makeText(context, context.getString(R.string.toast_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
  }
}
