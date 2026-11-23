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
  @Test fun updatePublishesTheWholeVersionAndCanResumeAfterPublication() {
    val root = createTempDirectory(prefix = "update-publish").toFile()
    try {
      val oldWeights = root.resolve("old").apply { mkdirs() }.resolve("weights.bin").apply { writeText("installed") }
      val update = ModelUpdateFiles(root, "latest")
      update.stagingDirectory.mkdirs()
      update.stagingDirectory.resolve("weights.bin").writeText("replacement")
      assertFalse(root.resolve("latest").exists())
      assertFalse(update.isPublished("weights.bin", null, listOf("vision.bin")))
      assertEquals("installed", oldWeights.readText())

      update.stagingDirectory.resolve("vision.bin").writeText("vision data")
      update.publish("weights.bin", listOf("vision.bin"))

      assertFalse(update.stagingDirectory.exists())
      assertEquals("replacement", root.resolve("latest/weights.bin").readText())
      assertEquals("vision data", root.resolve("latest/vision.bin").readText())
      assertEquals("installed", oldWeights.readText())
      val recovered = ModelUpdateFiles(root, "latest")
      assertTrue(recovered.isPublished("weights.bin", null, listOf("vision.bin")))
      assertTrue(root.resolve("latest/vision.bin").delete())
      assertFalse(recovered.isPublished("weights.bin", null, listOf("vision.bin")))
    } finally {
      root.deleteRecursively()
    }
  }

  @Test fun variantUpdateInTheSameRevisionKeepsTheInstalledArtifact() {
    val root = createTempDirectory(prefix = "variant-update").toFile()
    try {
      val installed = root.resolve("revision").apply { mkdirs() }
      installed.resolve("original.bin").writeText("original")
      installed.resolve("vision.bin").writeText("shared vision")
      val update = ModelUpdateFiles(root, "revision", inPlace = true)
      update.stagingDirectory.mkdirs()
      update.stagingDirectory.resolve("replacement.bin").writeText("new variant")
      update.stagingDirectory.resolve("vision.bin").writeText("staged vision")
      update.publish("replacement.bin", listOf("vision.bin"))
      assertEquals("original", installed.resolve("original.bin").readText())
      assertEquals("new variant", installed.resolve("replacement.bin").readText())
      assertEquals("shared vision", installed.resolve("vision.bin").readText())
    } finally {
      root.deleteRecursively()
    }
  }

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
        finalizeDownloadedDirectory(stagingDirectory, finalDirectory) { source, destination ->
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
        finalizeDownloadedDirectory(stagingDirectory, finalDirectory) { source, destination ->
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
