package com.ollitert.llm.server.ui.server.logs

import com.ollitert.llm.server.R
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.ui.common.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Convert a single log entry to a JSONObject for structured export. */
internal fun entryToJson(entry: RequestLogEntry): JSONObject {
  val obj = JSONObject()
  obj.put("id", entry.id)
  obj.put("timestamp", formatTimestamp(entry.timestamp))
  obj.put("timestamp_ms", entry.timestamp)
  obj.put("type", if (entry.method == "EVENT") "event" else "request")

  if (entry.method == "EVENT") {
    obj.put("message", entry.path)
    obj.put("category", entry.eventCategory.name.lowercase())
    obj.put("level", entry.level.name.lowercase())
    // Include structured event body (inference settings, prompt active, etc.)
    if (!entry.requestBody.isNullOrBlank()) {
      obj.put("data", tryParseJson(entry.requestBody))
    }
  } else {
    obj.put("method", entry.method)
    obj.put("path", entry.path)
    obj.put("status_code", entry.statusCode)
    obj.put("latency_ms", entry.latencyMs)
    obj.put("tokens", entry.tokens)
    obj.put("streaming", entry.isStreaming)
    if (entry.isThinking) obj.put("thinking", true)
    if (entry.isCompacted) {
      obj.put("compacted", true)
      if (!entry.compactionDetails.isNullOrBlank()) obj.put("compaction_details", entry.compactionDetails)
    }
    if (entry.isCancelled) {
      obj.put("cancelled", true)
      if (entry.cancelledByUser) obj.put("cancelled_by_user", true)
    }
    if (entry.inputTokenEstimate > 0) {
      obj.put("input_token_estimate", entry.inputTokenEstimate)
      obj.put("is_exact_token_count", entry.isExactTokenCount)
    }
    if (entry.maxContextTokens > 0) obj.put("max_context_tokens", entry.maxContextTokens)
    if (entry.ignoredClientParams != null) obj.put("ignored_client_params", entry.ignoredClientParams)
    if (entry.ttfbMs > 0) obj.put("ttfb_ms", entry.ttfbMs)
    if (entry.decodeSpeed > 0) obj.put("decode_speed_tps", entry.decodeSpeed)
    if (entry.prefillSpeed > 0) obj.put("prefill_speed_tps", entry.prefillSpeed)
    if (entry.itlMs > 0) obj.put("itl_ms", entry.itlMs)
    if (entry.clientIp != null) obj.put("client_ip", entry.clientIp)

    // Parse request/response bodies as JSON if possible, otherwise keep as string
    if (!entry.requestBody.isNullOrBlank()) {
      obj.put("request_body", tryParseJson(entry.requestBody))
    }
    if (!entry.compactedPrompt.isNullOrBlank()) {
      obj.put("compacted_prompt", entry.compactedPrompt)
    }
    if (!entry.responseBody.isNullOrBlank()) {
      obj.put("response_body", tryParseJson(entry.responseBody))
    }
  }

  if (entry.modelName != null) obj.put("model", entry.modelName)
  return obj
}

/** Try to parse a string as JSON (object or array); return the string as-is on failure. */

/** Build the full JSON export as a formatted string. */
internal fun buildLogsJson(entries: List<RequestLogEntry>): String {
  val root = JSONObject()
  root.put("exported_at", formatTimestamp(System.currentTimeMillis()))
  root.put("app", "OlliteRT")
  root.put("entry_count", entries.size)
  val array = JSONArray()
  for (entry in entries) {
    array.put(entryToJson(entry))
  }
  root.put("entries", array)
  return root.toString(2)
}

/** Build JSON on a background thread to avoid UI jank with large log sets (2500+ entries). */
internal suspend fun copyAllLogsToClipboard(context: Context, entries: List<RequestLogEntry>) {
  try {
    val json = withContext(Dispatchers.Default) { buildLogsJson(entries) }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    if (clipboard == null) {
      Toast.makeText(context, context.getString(R.string.toast_clipboard_unavailable), Toast.LENGTH_SHORT).show()
      return
    }
    clipboard.setPrimaryClip(ClipData.newPlainText("OlliteRT Logs", json))
    Toast.makeText(context, context.getString(R.string.toast_copied_entries_json, entries.size), Toast.LENGTH_SHORT).show()
  } catch (_: Exception) {
    // TransactionTooLargeException (or similar) — clipboard has a ~1MB Binder limit.
    // With many entries and large request/response bodies, the JSON can exceed this.
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
    context.startActivity(Intent.createChooser(shareIntent, "Export OlliteRT Logs"))
  } catch (e: Exception) {
    Toast.makeText(context, context.getString(R.string.toast_export_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
  }
}

internal fun copyEntryToClipboard(context: Context, entry: RequestLogEntry) {
  val json = entryToJson(entry).toString(2)
  copyToClipboard(context, "OlliteRT Log Entry", json, formatSuffix = "JSON")
}

