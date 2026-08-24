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
import com.ollitert.llm.server.data.allowlist.AllowlistLoader
import com.ollitert.llm.server.data.allowlist.MODEL_ALLOWLIST_OFFICIAL_FILENAME
import com.ollitert.llm.server.data.allowlist.ModelAllowlist
import com.ollitert.llm.server.data.allowlist.ModelAllowlistLoader
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.storage.ModelFileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface abstracting filesystem storage operations, model file lifecycle,
 * import directory management, and allowlist disk caching.
 */
interface ModelStorageRepository : AllowlistLoader {
  fun cleanupStaleImportTmpFiles()
  fun isFileInExternalFilesDir(fileName: String): Boolean
  fun deleteFileFromExternalFilesDir(fileName: String)
  fun deleteFilesFromImportDir(fileName: String)
  fun renameImportedFile(oldFileName: String, newFileName: String): Boolean
  fun deleteDirFromExternalFilesDir(dir: String)
  fun isModelPartiallyDownloaded(model: Model): Boolean
  fun isModelDownloaded(model: Model): Boolean
  fun getModelDownloadStatus(model: Model): ModelDownloadStatus
  fun getExternalFilesDir(): File?
}

/**
 * Default implementation of [ModelStorageRepository] managing device external files directory,
 * model file lifecycle via [ModelFileManager], and allowlist caching via [ModelAllowlistLoader].
 */
@Singleton
class DefaultModelStorageRepository @Inject constructor(
  @param:ApplicationContext private val context: Context,
) : ModelStorageRepository {

  private val baseFilesDir: File? get() = context.getExternalFilesDir(null)
  private val fileManager = ModelFileManager(context, baseFilesDir)
  private val allowlistLoader = ModelAllowlistLoader(context, baseFilesDir)

  override fun cleanupStaleImportTmpFiles() = fileManager.cleanupStaleImportTmpFiles()
  override fun isFileInExternalFilesDir(fileName: String): Boolean = fileManager.isFileInExternalFilesDir(fileName)
  override fun deleteFileFromExternalFilesDir(fileName: String) = fileManager.deleteFileFromExternalFilesDir(fileName)
  override fun deleteFilesFromImportDir(fileName: String) = fileManager.deleteFilesFromImportDir(fileName)
  override fun renameImportedFile(oldFileName: String, newFileName: String): Boolean = fileManager.renameImportedFile(oldFileName, newFileName)
  override fun deleteDirFromExternalFilesDir(dir: String) = fileManager.deleteDirFromExternalFilesDir(dir)
  override fun isModelPartiallyDownloaded(model: Model): Boolean = fileManager.isModelPartiallyDownloaded(model)
  override fun isModelDownloaded(model: Model): Boolean = fileManager.isModelDownloaded(model)
  override fun getModelDownloadStatus(model: Model): ModelDownloadStatus = fileManager.getModelDownloadStatus(model)
  override fun getExternalFilesDir(): File? = baseFilesDir

  override fun readTestAllowlist(): ModelAllowlist? = allowlistLoader.readTestAllowlist()
  override fun saveToDisk(content: String, filename: String) = allowlistLoader.saveToDisk(content, filename)
  override fun readFromDiskCache(filename: String): ModelAllowlist? = allowlistLoader.readFromDiskCache(filename)
  override fun readFromAssets(): ModelAllowlist? = allowlistLoader.readFromAssets()
}
