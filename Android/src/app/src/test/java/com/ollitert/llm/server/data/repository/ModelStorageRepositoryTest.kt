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

package com.ollitert.llm.server.data.repository

import android.content.Context
import com.ollitert.llm.server.data.model.IMPORTS_DIR
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelStorageRepositoryTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private lateinit var mockContext: Context
  private lateinit var externalFilesDir: File
  private lateinit var repository: ModelStorageRepository

  @Before
  fun setUp() {
    externalFilesDir = tempFolder.newFolder("external_files")
    mockContext = mockk(relaxed = true)
    every { mockContext.getExternalFilesDir(null) } returns externalFilesDir

    repository = DefaultModelStorageRepository(mockContext)
  }

  @Test
  fun externalFilesDirMatchesContext() {
    assertEquals(externalFilesDir, repository.getExternalFilesDir())
  }

  @Test
  fun isFileInExternalFilesDirDetectsExistingFile() {
    val file = File(externalFilesDir, "test-model.bin")
    assertFalse(repository.isFileInExternalFilesDir("test-model.bin"))
    file.writeText("sample data")
    assertTrue(repository.isFileInExternalFilesDir("test-model.bin"))
  }

  @Test
  fun deleteFileFromExternalFilesDirDeletesFile() {
    val file = File(externalFilesDir, "test-model.bin")
    file.writeText("sample data")
    assertTrue(file.exists())
    repository.deleteFileFromExternalFilesDir("test-model.bin")
    assertFalse(file.exists())
  }

  @Test
  fun deleteFilesFromImportDirDeletesMatchingPrefixFiles() {
    val importsDir = File(externalFilesDir, IMPORTS_DIR)
    importsDir.mkdirs()
    val targetFile = File(importsDir, "my_model.bin")
    targetFile.writeText("weights")
    val unrelatedFile = File(importsDir, "other_model.bin")
    unrelatedFile.writeText("other weights")

    repository.deleteFilesFromImportDir("$IMPORTS_DIR/my_model.bin")
    assertFalse(targetFile.exists())
    assertTrue(unrelatedFile.exists())
  }

  @Test
  fun renameImportedFileRenamesSuccessfully() {
    val importsDir = File(externalFilesDir, IMPORTS_DIR)
    importsDir.mkdirs()
    val oldFile = File(importsDir, "old_name.bin")
    oldFile.writeText("content")

    val success = repository.renameImportedFile("old_name.bin", "new_name.bin")
    assertTrue(success)
    assertFalse(oldFile.exists())
    assertTrue(File(importsDir, "new_name.bin").exists())
  }

  @Test
  fun saveAndReadAllowlistFromDiskCache() {
    val jsonContent = """{"schemaVersion": 1, "contentVersion": 2, "models": []}"""
    repository.saveToDisk(jsonContent, "custom_repo.json")

    val loaded = repository.readFromDiskCache("custom_repo.json")
    assertNotNull(loaded)
    assertEquals(2, loaded?.contentVersion)
  }

  @Test
  fun getModelDownloadStatusReturnsNotDownloadedWhenAbsent() {
    val model = Model(name = "test-llm", downloadFileName = "model.bin")
    val status = repository.getModelDownloadStatus(model)
    assertEquals(ModelDownloadStatusType.NOT_DOWNLOADED, status.status)
  }
}
