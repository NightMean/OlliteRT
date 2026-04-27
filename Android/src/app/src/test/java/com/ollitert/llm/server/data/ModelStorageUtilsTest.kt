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

package com.ollitert.llm.server.data

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelStorageUtilsTest {

  private lateinit var tempDir: File

  @Before
  fun setUp() {
    mockkStatic(Log::class)
    every { Log.i(any(), any()) } returns 0
    every { Log.w(any<String>(), any<String>()) } returns 0
    tempDir = createTempDirectory("model_storage_test").toFile()
  }

  @After
  fun tearDown() {
    unmockkStatic(Log::class)
    tempDir.deleteRecursively()
  }

  @Test
  fun `cleanupStaleImportTmpFiles - null externalFilesDir is a no-op`() {
    cleanupStaleImportTmpFiles(null)
  }

  @Test
  fun `cleanupStaleImportTmpFiles - missing imports directory is a no-op`() {
    cleanupStaleImportTmpFiles(tempDir)
  }

  @Test
  fun `cleanupStaleImportTmpFiles - deletes tmp files in imports directory`() {
    val importsDir = File(tempDir, IMPORTS_DIR).also { it.mkdirs() }
    val tmpFile1 = File(importsDir, "model_a.tmp").also { it.writeText("data") }
    val tmpFile2 = File(importsDir, "model_b.tmp").also { it.writeText("more data") }

    cleanupStaleImportTmpFiles(tempDir)

    assertFalse(tmpFile1.exists())
    assertFalse(tmpFile2.exists())
  }

  @Test
  fun `cleanupStaleImportTmpFiles - preserves non-tmp files`() {
    val importsDir = File(tempDir, IMPORTS_DIR).also { it.mkdirs() }
    val keepFile = File(importsDir, "model.litertlm").also { it.writeText("model data") }
    val tmpFile = File(importsDir, "stale.tmp").also { it.writeText("stale") }

    cleanupStaleImportTmpFiles(tempDir)

    assertTrue(keepFile.exists())
    assertFalse(tmpFile.exists())
  }

  @Test
  fun `cleanupStaleImportTmpFiles - empty imports directory is a no-op`() {
    File(tempDir, IMPORTS_DIR).mkdirs()
    cleanupStaleImportTmpFiles(tempDir)
  }

  @Test
  fun `cleanupStaleImportTmpFiles - safe to call multiple times`() {
    val importsDir = File(tempDir, IMPORTS_DIR).also { it.mkdirs() }
    File(importsDir, "file.tmp").also { it.writeText("data") }

    cleanupStaleImportTmpFiles(tempDir)
    cleanupStaleImportTmpFiles(tempDir)
  }
}
