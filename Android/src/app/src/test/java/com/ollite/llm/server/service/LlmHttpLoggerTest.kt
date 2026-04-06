package com.ollite.llm.server.service

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmHttpLoggerTest {

  @Test
  fun logEventWritesToFileWhenEnabled() {
    val dir = createTempDirectory("logger-test").toFile()
    try {
      val logger = LlmHttpLogger(logDir = { dir }, isEnabled = { true })
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
      val logger = LlmHttpLogger(logDir = { dir }, isEnabled = { false })
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
      val logger = LlmHttpLogger(logDir = { dir }, isEnabled = { true })
      val longBody = "x".repeat(3000)
      logger.logPayload("test", longBody, "r1")
      logger.shutdown()

      val content = File(dir, "llm_http.log").readText()
      assertTrue("truncated marker should appear", content.contains("...(truncated)"))
      assertFalse("full body should not appear", content.contains("x".repeat(3000)))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test
  fun logPayloadPreservesShortBody() {
    val dir = createTempDirectory("logger-test").toFile()
    try {
      val logger = LlmHttpLogger(logDir = { dir }, isEnabled = { true })
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
      logFile.writeText("x".repeat(600 * 1024))

      val logger = LlmHttpLogger(logDir = { dir }, isEnabled = { true })
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
      val logger = LlmHttpLogger(logDir = { dir }, isEnabled = { true })
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
    val logger = LlmHttpLogger(logDir = { null }, isEnabled = { true })
    logger.logEvent("null_dir_event")
    logger.shutdown()
    // No exception — test passes
  }

  @Test
  fun shutdownIsIdempotent() {
    val logger = LlmHttpLogger(logDir = { null }, isEnabled = { false })
    logger.shutdown()
    logger.shutdown() // second call must not throw
  }
}
