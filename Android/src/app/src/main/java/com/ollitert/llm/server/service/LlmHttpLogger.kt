package com.ollitert.llm.server.service

import java.io.File
import java.io.FileWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * File-based logger for the HTTP bridge. Writes timestamped lines to llm_http.log,
 * rotates to llm_http.log.1 when the file exceeds [logFileMaxBytes]. All writes are
 * dispatched to a single-thread executor to avoid blocking request threads.
 *
 * Only writes when [isEnabled] returns true. Logcat calls remain in LlmHttpService.
 */
class LlmHttpLogger(
  private val logDir: () -> File?,
  private val isEnabled: () -> Boolean,
  /** When true, payload truncation is skipped (verbose debug mode). */
  private val isVerboseDebug: () -> Boolean = { false },
) {
  private val maxLogChars = 2000
  private val logFileMaxBytes = 512 * 1024L
  private val executor = Executors.newSingleThreadExecutor()

  fun logEvent(message: String) {
    if (!isEnabled()) return
    append("LLM_HTTP $message")
  }

  fun logPayload(label: String, body: String, requestId: String) {
    if (!isEnabled()) return
    // In verbose debug mode, log the full payload without truncation
    val limit = if (isVerboseDebug()) Int.MAX_VALUE else maxLogChars
    val trimmed =
      if (body.length <= limit) body else body.take(limit) + "...(truncated)"
    append(
      "LLM_HTTP payload id=$requestId label=\"$label\" bytes=${body.length} data=\"$trimmed\""
    )
  }

  fun shutdown() {
    executor.shutdown()
    executor.awaitTermination(5, TimeUnit.SECONDS)
  }

  private fun append(line: String) {
    val dir = logDir() ?: return
    val logFile = File(dir, "llm_http.log")
    val stampedLine = "${System.currentTimeMillis()} $line\n"
    executor.execute {
      try {
        if (!dir.exists()) dir.mkdirs()
        if (logFile.exists() && logFile.length() > logFileMaxBytes) {
          val rotated = File(dir, "llm_http.log.1")
          if (rotated.exists()) rotated.delete()
          logFile.renameTo(rotated)
        }
        FileWriter(logFile, true).use { it.append(stampedLine) }
      } catch (e: Exception) {
        android.util.Log.w("LlmHttpLogger", "Failed to write log", e)
      }
    }
  }
}
