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
import com.ollitert.llm.server.data.LOG_FILE_MAX_BYTES
import com.ollitert.llm.server.data.MAX_PAYLOAD_LOG_CHARS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LlmHttpLoggerTest {

  @Test
  fun logEventWritesToFileWhenEnabled() {
    val dir = createTempDirectory("logger-test").toFile()
    try {
      val logger = FileLogger(logDir = { dir }, isEnabled = { true })
      logger.logEvent("test_event key=value")
      logger.shutdown()

      val logFile = File(dir, "llm_http.log")
      assertTrue("log file should exist", logFile.exists())
      assertTrue("log file should contain event", logFile.readText().contains("LLM_HTTP test_event key=value"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun logEventSkipsFileWhenDisabled() {
    val dir = createTempDirectory("logger-test").toFile()
    try {
      val logger = FileLogger(logDir = { dir }, isEnabled = { false })
      logger.logEvent("should_not_appear")
      logger.shutdown()

      assertFalse("log file should not exist when disabled", File(dir, "llm_http.log").exists())
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun logPayloadTruncatesLongBody() {
    val dir = createTempDirectory("logger-test").toFile()
    try {
      val logger = FileLogger(logDir = { dir }, isEnabled = { true })
      val longBody = "x".repeat(MAX_PAYLOAD_LOG_CHARS + 1000)
      logger.logPayload("test", longBody, "r1")
      logger.shutdown()

      val content = File(dir, "llm_http.log").readText()
      assertTrue("truncated marker should appear", content.contains("...(truncated)"))
      assertFalse("full body should not appear", content.contains("x".repeat(MAX_PAYLOAD_LOG_CHARS + 1000)))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun logPayloadPreservesShortBody() {
    val dir = createTempDirectory("logger-test").toFile()
    try {
      val logger = FileLogger(logDir = { dir }, isEnabled = { true })
      val body = "short body"
      logger.logPayload("label", body, "r1")
      logger.shutdown()

      val content = File(dir, "llm_http.log").readText()
      assertTrue("short body should appear verbatim", content.contains(body))
      assertFalse("truncated marker should not appear", content.contains("...(truncated)"))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun logFileRotatesWhenExceedingMaxSize() {
    val dir = createTempDirectory("logger-test").toFile()
    try {
      // Pre-fill the log file beyond 512 KB
      val logFile = File(dir, "llm_http.log")
      logFile.writeText("x".repeat((LOG_FILE_MAX_BYTES + 1024).toInt()))

      val logger = FileLogger(logDir = { dir }, isEnabled = { true })
      logger.logEvent("trigger_rotation")
      logger.shutdown()

      assertTrue("rotated file should exist", File(dir, "llm_http.log.1").exists())
      val newContent = logFile.readText()
      assertTrue("new log should contain the trigger event", newContent.contains("trigger_rotation"))
      assertFalse("new log should not contain old content", newContent.contains("x".repeat(100)))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun logsDirIsCreatedIfMissing() {
    val base = createTempDirectory("logger-base").toFile()
    val dir = File(base, "subdir/logs")
    try {
      val logger = FileLogger(logDir = { dir }, isEnabled = { true })
      logger.logEvent("dir_creation_test")
      logger.shutdown()

      assertTrue("log dir should be created", dir.exists())
      assertTrue("log file should exist", File(dir, "llm_http.log").exists())
    } finally {
      base.deleteRecursively()
    }
  }

  @Test
  fun nullLogDirIsHandledGracefully() {
    val logger = FileLogger(logDir = { null }, isEnabled = { true })
    logger.logEvent("null_dir_event")
    logger.shutdown()
    // No exception — test passes
  }

  @Test
  fun verboseDebugSkipsTruncation() {
    val dir = createTempDirectory("logger-test").toFile()
    try {
      val logger = FileLogger(
        logDir = { dir },
        isEnabled = { true },
        isVerboseDebug = { true },
      )
      val longBody = "x".repeat(MAX_PAYLOAD_LOG_CHARS + 1000)
      logger.logPayload("verbose", longBody, "r1")
      logger.shutdown()

      val content = File(dir, "llm_http.log").readText()
      assertFalse("truncated marker should NOT appear in verbose mode", content.contains("...(truncated)"))
      assertTrue("full body should appear in verbose mode", content.contains(longBody))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun shutdownIsIdempotent() {
    val logger = FileLogger(logDir = { null }, isEnabled = { false })
    logger.shutdown()
    logger.shutdown() // second call must not throw
  }

  @Test
  fun circuitOpensAfterConsecutiveFailures() {
    // Use a regular file as the "directory" — mkdirs() and FileWriter both fail
    val base = createTempDirectory("logger-test").toFile()
    try {
      val blocker = File(base, "blocker")
      blocker.writeText("occupied")
      val brokenDir = File(blocker, "subdir")

      val logger = FileLogger(logDir = { brokenDir }, isEnabled = { true })

      repeat(FILE_LOGGER_CIRCUIT_BREAKER_THRESHOLD + 5) {
        logger.logEvent("fail_$it")
      }
      logger.shutdown()

      assertTrue("circuit should be open after repeated failures", logger.isCircuitOpen)
    } finally {
      base.deleteRecursively()
    }
  }

  @Test
  fun circuitResetsOnSuccessfulWrite() {
    val base = createTempDirectory("logger-test").toFile()
    try {
      val blocker = File(base, "blocker")
      blocker.writeText("occupied")
      val brokenDir = File(blocker, "subdir")

      val logger = FileLogger(logDir = { brokenDir }, isEnabled = { true })

      repeat(FILE_LOGGER_CIRCUIT_BREAKER_THRESHOLD + 1) {
        logger.logEvent("fail_$it")
      }
      logger.shutdown()
      assertTrue("circuit should be open", logger.isCircuitOpen)

      // New logger pointing at a valid directory proves writes work after disk frees up
      val goodDir = File(base, "recovered")
      goodDir.mkdirs()
      val logger2 = FileLogger(logDir = { goodDir }, isEnabled = { true })
      logger2.logEvent("recovery_event")
      logger2.shutdown()

      val logFile = File(goodDir, "llm_http.log")
      assertTrue("log file should exist after recovery", logFile.exists())
      assertTrue("recovery event should be logged", logFile.readText().contains("recovery_event"))
    } finally {
      base.deleteRecursively()
    }
  }

  @Test
  fun circuitDoesNotOpenOnIntermittentFailure() {
    val dir = createTempDirectory("logger-test").toFile()
    try {
      val logger = FileLogger(logDir = { dir }, isEnabled = { true })

      logger.logEvent("success_1")
      logger.shutdown()

      assertFalse("circuit should not be open after successful write", logger.isCircuitOpen)
    } finally {
      dir.deleteRecursively()
    }
  }
}
