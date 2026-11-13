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

package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import android.net.Uri
import com.ollitert.llm.server.R
import android.util.Log
import com.ollitert.llm.server.data.model.IMPORTS_DIR
import com.ollitert.llm.server.data.repository.IMPORT_STAGE_PREFIX
import com.ollitert.llm.server.data.repository.IMPORT_STAGE_SUFFIX
import com.ollitert.llm.server.data.repository.ProtoDataStoreRepository
import com.ollitert.llm.server.data.repository.DefaultPreferencesRepository
import com.ollitert.llm.server.data.repository.PreferencesRepository
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.allowlist.ModelFactory
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.proto.ImportedModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val TAG = "OlliteRT.ImportedModelCoord"

internal sealed interface LocalModelImportState {
  data object Idle : LocalModelImportState
  data class Copying(val model: ImportedModel, val progress: Float = 0f) : LocalModelImportState
  data object Complete : LocalModelImportState
  data class Failed(val message: String) : LocalModelImportState
}

/**
 * Handles operations on user-imported models:
 * creating/updating DataStore records, rebuilding Model objects,
 * renaming files, and logging diagnostic events.
 */
class ImportedModelCoordinator(
  private val context: Context,
  private val protoDataStoreRepository: ProtoDataStoreRepository,
  private val preferencesRepository: PreferencesRepository = DefaultPreferencesRepository(context),
) {
  suspend fun importFile(uri: Uri, info: ImportedModel, onProgress: (Float) -> Unit): ImportedModel {
    val name = info.fileName
    check(protoDataStoreRepository.reserveImportedModel(name)) { "An import for this file is already running" }
    try {
      val previous = protoDataStoreRepository.readImportedModels().firstOrNull { it.fileName == name }
      val root = context.getExternalFilesDir(null) ?: throw IOException("Imported model storage is unavailable")
      val directory = File(root, IMPORTS_DIR)
      if (!directory.mkdirs() && !directory.isDirectory) throw IOException("Could not open the import directory")
      val stagedFile = File.createTempFile(IMPORT_STAGE_PREFIX, IMPORT_STAGE_SUFFIX, directory)
      var handedToRepository = false
      try {
        val copied = copyToStage(uri, stagedFile, info.fileSize, onProgress)
        val saved = info.toBuilder().setFileSize(copied).build()
        currentCoroutineContext().ensureActive()
        // Complete the handoff even if the screen closes. The repository owns cleanup from here.
        withContext(NonCancellable) {
          handedToRepository = true
          protoDataStoreRepository.finalizeImportedModel(saved, previous, stagedFile)
        }
        return saved
      } finally {
        if (!handedToRepository && !stagedFile.delete() && stagedFile.exists()) {
          Log.w(TAG, "Could not discard uncommitted import stage")
        }
      }
    } finally {
      withContext(NonCancellable) { protoDataStoreRepository.releaseImportedModel(name) }
    }
  }

  private suspend fun copyToStage(uri: Uri, stage: File, expectedSize: Long, onProgress: (Float) -> Unit): Long {
    val input = context.contentResolver.openInputStream(uri)
      ?: throw IOException(context.getString(R.string.error_import_failed))
    val cancellation = currentCoroutineContext()
    var copied = 0L
    var nextProgressAt = 0L
    input.use {
      FileOutputStream(stage).use { output ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
          cancellation.ensureActive()
          val read = input.read(buffer)
          if (read == -1) break
          output.write(buffer, 0, read)
          copied += read
          val now = android.os.SystemClock.elapsedRealtime()
          if (expectedSize > 0 && now >= nextProgressAt) {
            onProgress((copied.toDouble() / expectedSize).coerceIn(0.0, 1.0).toFloat())
            nextProgressAt = now + 200
          }
        }
        // Persist the staged contents before DataStore can commit the intent that refers to them.
        output.fd.sync()
      }
    }
    return copied
  }

  fun buildAndRestoreImportedModel(info: ImportedModel): Model {
    val model = ModelFactory.buildImportedModel(info)
    ModelFactory.restoreInferenceConfig(preferencesRepository, model)
    return model
  }

  suspend fun updateDefaults(expected: ImportedModel, updatedInfo: ImportedModel): Model {
    Log.d(TAG, "Updating imported model defaults: ${updatedInfo.fileName}")
    protoDataStoreRepository.replaceImportedModel(expected, updatedInfo) {
      preferencesRepository.clearInferenceConfig(updatedInfo.fileName)
    }

    val updatedModel = ModelFactory.buildImportedModel(updatedInfo)

    if (preferencesRepository.isVerboseDebugEnabled()) {
      RequestLogStore.addEvent(
        "Imported model defaults updated: ${updatedInfo.fileName}",
        level = LogLevel.DEBUG,
        modelName = ModelFactory.importedModelId(updatedInfo.fileName),
        category = EventCategory.MODEL,
      )
    }
    return updatedModel
  }

  suspend fun deleteImportedModelRecord(modelName: String) {
    protoDataStoreRepository.removeImportedModel(modelName)
  }

  suspend fun renameOnlyDisplayName(oldFileName: String, displayName: String): Model? =
    protoDataStoreRepository.renameImportedDisplayName(oldFileName, displayName)?.let(::buildAndRestoreImportedModel)

  suspend fun renameFileAndRecord(oldFileName: String, newFileName: String, displayName: String): Model? =
    protoDataStoreRepository.renameImportedModel(oldFileName, newFileName, displayName)?.let(::buildAndRestoreImportedModel)
}
