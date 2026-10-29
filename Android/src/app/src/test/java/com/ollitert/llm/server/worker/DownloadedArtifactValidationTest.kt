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

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedArtifactValidationTest {
  @Test fun rejectsHtmlBeforeSizeMismatch() {
    val file = Files.createTempFile("model", ".tmp").toFile()
    try {
      file.writeText("\uFEFF \n<!DOCTYPE html><html>Mirror</html>")
      assertEquals(ArtifactProblem.HTML, validateDownloadedArtifact(file, 5000))
    } finally { file.delete() }
  }

  @Test fun checksKnownSizeAndAllowsUnknownSize() {
    val file = Files.createTempFile("model", ".tmp").toFile()
    try {
      file.writeBytes(byteArrayOf(0, 1, 2, 3))
      assertEquals(ArtifactProblem.SIZE, validateDownloadedArtifact(file, 5))
      assertEquals(null, validateDownloadedArtifact(file, 4))
      assertEquals(null, validateDownloadedArtifact(file, 0))
      file.writeBytes(byteArrayOf())
      assertEquals(ArtifactProblem.SIZE, validateDownloadedArtifact(file, 0))
    } finally { file.delete() }
  }
}
