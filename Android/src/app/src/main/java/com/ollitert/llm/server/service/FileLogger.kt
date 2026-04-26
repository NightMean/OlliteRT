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

import com.ollitert.llm.server.data.FILE_LOGGER_CIRCUIT_BREAKER_THRESHOLD
import com.ollitert.llm.server.data.FILE_LOGGER_PROBE_INTERVAL
import com.ollitert.llm.server.data.LOG_FILE_MAX_BYTES
import com.ollitert.llm.server.data.MAX_PAYLOAD_LOG_CHARS
import java.io.File
import java.io.FileWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * File-based logger for the HTTP bridge. Writes timestamped lines to llm_http.log,
 * rotates to llm_http.log.1 when the file exceeds [logFileMaxBytes]. All writes are
 * dispatched to a single-thread executor to avoid blocking request threads.
 *
 * Only writes when [isEnabled] returns true. Logcat calls remain in ServerService.
 */
class FileLogger(
  private val logDir: () -> File?,
  private val isEnabled: () -> Boolean,
  /** When true, payload truncation is skipped (verbose debug mode). */
  private val isVerboseDebug: () -> Boolean = { false },
) {
  private val maxLogChars = MAX_PAYLOAD_LOG_CHARS
  private val logFileMaxBytes = LOG_FILE_MAX_BYTES

  private val executor = Executors.newSingleThreadExecutor()
  private val consecutiveFailures = AtomicInteger(0)
  private val totalCallsSinceCircuitOpened = AtomicInteger(0)
  @Volatile var isCircuitOpen = false
    private set

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
    if (isCircuitOpen) {
      val calls = totalCallsSinceCircuitOpened.incrementAndGet()
      if (calls % FILE_LOGGER_PROBE_INTERVAL != 0) return
    }
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
        if (consecutiveFailures.getAndSet(0) > 0) {
          isCircuitOpen = false
          totalCallsSinceCircuitOpened.set(0)
          android.util.Log.i("FileLogger", "Disk write recovered, circuit closed")
        }
      } catch (e: Exception) {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures == FILE_LOGGER_CIRCUIT_BREAKER_THRESHOLD) {
          isCircuitOpen = true
          android.util.Log.e(
            "FileLogger",
            "Circuit opened after $failures consecutive write failures — disk full?",
            e,
          )
        } else if (!isCircuitOpen) {
          android.util.Log.w(
            "FileLogger",
            "Failed to write log ($failures/$FILE_LOGGER_CIRCUIT_BREAKER_THRESHOLD)",
            e,
          )
        }
      }
    }
  }
}
