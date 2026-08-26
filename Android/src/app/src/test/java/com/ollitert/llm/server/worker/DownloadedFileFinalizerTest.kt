/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ollitert.llm.server.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.createTempDirectory

class DownloadedFileFinalizerTest {
  @Test
  fun atomicallyReplacesAnExistingValidArtifact() {
    val directory = createTempDirectory(prefix = "download-finalize").toFile()
    try {
      val stagingFile = directory.resolve("model.bin.tmp").apply { writeText("new") }
      val finalFile = directory.resolve("model.bin").apply { writeText("old") }

      finalizeDownloadedFile(stagingFile, finalFile)

      assertFalse(stagingFile.exists())
      assertEquals("new", finalFile.readText())
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun failedReplacementPreservesBothValidArtifactAndStagingFile() {
    val directory = createTempDirectory(prefix = "download-finalize-failure").toFile()
    try {
      val stagingFile = directory.resolve("model.bin.tmp").apply { writeText("new") }
      val finalFile = directory.resolve("model.bin").apply { writeText("old") }

      try {
        finalizeDownloadedFile(stagingFile, finalFile) { _, _ ->
          throw IOException("simulated move rejection")
        }
      } catch (_: DownloadFinalizationException) {
        // Expected: the caller receives a distinct, retryable finalization failure.
      }

      assertTrue(stagingFile.exists())
      assertEquals("new", stagingFile.readText())
      assertEquals("old", finalFile.readText())
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun failedDirectoryReplacementRestoresThePreviousExtractedModel() {
    val directory = createTempDirectory(prefix = "directory-finalize-failure").toFile()
    try {
      val stagingDirectory = directory.resolve("model.extracting").apply { mkdirs() }
      stagingDirectory.resolve("weights.bin").writeText("new")
      val finalDirectory = directory.resolve("model").apply { mkdirs() }
      finalDirectory.resolve("weights.bin").writeText("old")
      var moveCount = 0

      try {
        finalizeExtractedDirectory(stagingDirectory, finalDirectory) { source, destination ->
          moveCount++
          if (moveCount == 2) throw IOException("simulated replacement rejection")
          Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        }
      } catch (_: DownloadFinalizationException) {
        // Expected: the old directory is restored before failure is reported.
      }

      assertEquals("old", finalDirectory.resolve("weights.bin").readText())
      assertEquals("new", stagingDirectory.resolve("weights.bin").readText())
    } finally {
      directory.deleteRecursively()
    }
  }

  @Test
  fun retryRecoversBackupLeftByProcessDeathBeforeReplacingIt() {
    val directory = createTempDirectory(prefix = "directory-finalize-recovery").toFile()
    try {
      val stagingDirectory = directory.resolve("model.extracting").apply { mkdirs() }
      stagingDirectory.resolve("weights.bin").writeText("new")
      val finalDirectory = directory.resolve("model")
      val backupDirectory = directory.resolve("model.replacement-backup").apply { mkdirs() }
      backupDirectory.resolve("weights.bin").writeText("old")
      var moveCount = 0

      try {
        finalizeExtractedDirectory(stagingDirectory, finalDirectory) { source, destination ->
          moveCount++
          if (moveCount == 3) throw IOException("simulated failure after recovery")
          Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE)
        }
      } catch (_: DownloadFinalizationException) {
        // Expected: the recovered old directory is restored after the new swap fails.
      }

      assertEquals("old", finalDirectory.resolve("weights.bin").readText())
      assertEquals("new", stagingDirectory.resolve("weights.bin").readText())
      assertFalse(backupDirectory.exists())
    } finally {
      directory.deleteRecursively()
    }
  }
}
