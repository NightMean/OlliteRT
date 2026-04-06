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
    val trimmed =
      if (body.length <= maxLogChars) body else body.take(maxLogChars) + "...(truncated)"
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
      } catch (_: Exception) {}
    }
  }
}
