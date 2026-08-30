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
import android.util.Log
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.data.repository.ProtoDataStoreRepository
import com.ollitert.llm.server.data.repository.DefaultPreferencesRepository
import com.ollitert.llm.server.data.repository.PreferencesRepository
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.allowlist.ModelFactory
import com.ollitert.llm.server.data.repository.DefaultModelStorageRepository
import com.ollitert.llm.server.data.repository.ModelStorageRepository
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.proto.ImportedModel

private const val TAG = "OlliteRT.ImportedModelCoord"

/**
 * Handles operations on user-imported models:
 * creating/updating DataStore records, rebuilding Model objects,
 * renaming files, and logging diagnostic events.
 */
class ImportedModelCoordinator(
  private val context: Context,
  private val protoDataStoreRepository: ProtoDataStoreRepository,
  private val modelStorageRepository: ModelStorageRepository = DefaultModelStorageRepository(context),
  private val preferencesRepository: PreferencesRepository = DefaultPreferencesRepository(context),
) {
  fun buildAndRestoreImportedModel(info: ImportedModel): Model {
    val model = ModelFactory.buildImportedModel(info)
    ModelFactory.restoreInferenceConfig(preferencesRepository, model)
    return model
  }

  /** True when the imported model's file still exists in the imports directory. */
  fun importedFileExists(info: ImportedModel): Boolean {
    val externalDir = context.getExternalFilesDir(null) ?: return false
    return java.io.File(externalDir, com.ollitert.llm.server.data.model.IMPORTS_DIR + "/" + info.fileName).exists()
  }

  suspend fun saveImportedModel(info: ImportedModel) {
    val importedModels = protoDataStoreRepository.readImportedModels().toMutableList()
    val importedModelIndex = importedModels.indexOfFirst { info.fileName == it.fileName }
    if (importedModelIndex >= 0) {
      Log.d(TAG, "Duplicated imported model found in data store. Removing old entry first")
      importedModels.removeAt(importedModelIndex)
    }
    importedModels.add(info)
    protoDataStoreRepository.saveImportedModels(importedModels = importedModels)

    if (preferencesRepository.isVerboseDebugEnabled()) {
      RequestLogStore.addEvent(
        "Model imported: ${info.fileName} (${info.fileSize.humanReadableSize()})",
        level = LogLevel.DEBUG,
        modelName = ModelFactory.importedModelId(info.fileName),
        category = EventCategory.MODEL,
      )
    }
  }

  suspend fun updateDefaults(updatedInfo: ImportedModel): Model {
    Log.d(TAG, "Updating imported model defaults: ${updatedInfo.fileName}")
    protoDataStoreRepository.updateImportedModel(updatedInfo.fileName, updatedInfo)
    preferencesRepository.clearInferenceConfig(updatedInfo.fileName)

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
    val importedModels = protoDataStoreRepository.readImportedModels().toMutableList()
    val importedModelIndex = importedModels.indexOfFirst { it.fileName == modelName }
    if (importedModelIndex >= 0) {
      importedModels.removeAt(importedModelIndex)
    }
    protoDataStoreRepository.saveImportedModels(importedModels = importedModels)
  }

  suspend fun renameOnlyDisplayName(oldFileName: String, displayName: String): Model? {
    val importedModels = protoDataStoreRepository.readImportedModels().toMutableList()
    val index = importedModels.indexOfFirst { it.fileName == oldFileName }
    if (index >= 0) {
      val updated = importedModels[index].toBuilder().setDisplayName(displayName).build()
      importedModels[index] = updated
      protoDataStoreRepository.saveImportedModels(importedModels)
      return buildAndRestoreImportedModel(updated)
    }
    return null
  }

  suspend fun renameFileAndRecord(oldFileName: String, newFileName: String, displayName: String): Model? {
    if (!modelStorageRepository.renameImportedFile(oldFileName, newFileName)) return null

    val importedModels = protoDataStoreRepository.readImportedModels().toMutableList()
    val index = importedModels.indexOfFirst { it.fileName == oldFileName }
    var resultModel: Model? = null
    if (index >= 0) {
      val updated = importedModels[index].toBuilder()
        .setFileName(newFileName)
        .setDisplayName(displayName)
        .build()
      importedModels[index] = updated
      protoDataStoreRepository.saveImportedModels(importedModels)
      resultModel = buildAndRestoreImportedModel(updated)
    }

    preferencesRepository.renameModelPrefsKey(oldFileName, newFileName)
    return resultModel
  }
}
