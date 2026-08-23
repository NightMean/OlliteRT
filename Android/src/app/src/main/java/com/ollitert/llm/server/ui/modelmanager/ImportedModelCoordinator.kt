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
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.EventCategory
import com.ollitert.llm.server.data.LogLevel
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelFactory
import com.ollitert.llm.server.data.ModelFileManager
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.proto.ImportedModel

private const val TAG = "OlliteRT.ImportedModelCoord"

/**
 * Handles operations on user-imported models:
 * creating/updating DataStore records, rebuilding Model objects,
 * renaming files, and logging diagnostic events.
 */
class ImportedModelCoordinator(
  private val context: Context,
  private val dataStoreRepository: DataStoreRepository,
  private val fileManager: ModelFileManager,
) {
  fun buildAndRestoreImportedModel(info: ImportedModel): Model {
    val model = ModelFactory.buildImportedModel(info)
    ModelFactory.restoreInferenceConfig(context, model)
    return model
  }

  suspend fun saveImportedModel(info: ImportedModel) {
    val importedModels = dataStoreRepository.readImportedModels().toMutableList()
    val importedModelIndex = importedModels.indexOfFirst { info.fileName == it.fileName }
    if (importedModelIndex >= 0) {
      Log.d(TAG, "Duplicated imported model found in data store. Removing old entry first")
      importedModels.removeAt(importedModelIndex)
    }
    importedModels.add(info)
    dataStoreRepository.saveImportedModels(importedModels = importedModels)

    if (ServerPrefs.isVerboseDebugEnabled(context)) {
      RequestLogStore.addEvent(
        "Model imported: ${info.fileName} (${info.fileSize.humanReadableSize()})",
        level = LogLevel.DEBUG,
        modelName = info.fileName,
        category = EventCategory.MODEL,
      )
    }
  }

  suspend fun updateDefaults(updatedInfo: ImportedModel): Model {
    Log.d(TAG, "Updating imported model defaults: ${updatedInfo.fileName}")
    dataStoreRepository.updateImportedModel(updatedInfo.fileName, updatedInfo)
    ServerPrefs.clearInferenceConfig(context, updatedInfo.fileName)

    val updatedModel = ModelFactory.buildImportedModel(updatedInfo)

    if (ServerPrefs.isVerboseDebugEnabled(context)) {
      RequestLogStore.addEvent(
        "Imported model defaults updated: ${updatedInfo.fileName}",
        level = LogLevel.DEBUG,
        modelName = updatedInfo.fileName,
        category = EventCategory.MODEL,
      )
    }
    return updatedModel
  }

  suspend fun deleteImportedModelRecord(modelName: String) {
    val importedModels = dataStoreRepository.readImportedModels().toMutableList()
    val importedModelIndex = importedModels.indexOfFirst { it.fileName == modelName }
    if (importedModelIndex >= 0) {
      importedModels.removeAt(importedModelIndex)
    }
    dataStoreRepository.saveImportedModels(importedModels = importedModels)
  }

  suspend fun renameOnlyDisplayName(oldFileName: String, displayName: String): Model? {
    val importedModels = dataStoreRepository.readImportedModels().toMutableList()
    val index = importedModels.indexOfFirst { it.fileName == oldFileName }
    if (index >= 0) {
      val updated = importedModels[index].toBuilder().setDisplayName(displayName).build()
      importedModels[index] = updated
      dataStoreRepository.saveImportedModels(importedModels)
      return buildAndRestoreImportedModel(updated)
    }
    return null
  }

  suspend fun renameFileAndRecord(oldFileName: String, newFileName: String, displayName: String): Model? {
    if (!fileManager.renameImportedFile(oldFileName, newFileName)) return null

    val importedModels = dataStoreRepository.readImportedModels().toMutableList()
    val index = importedModels.indexOfFirst { it.fileName == oldFileName }
    var resultModel: Model? = null
    if (index >= 0) {
      val updated = importedModels[index].toBuilder()
        .setFileName(newFileName)
        .setDisplayName(displayName)
        .build()
      importedModels[index] = updated
      dataStoreRepository.saveImportedModels(importedModels)
      resultModel = buildAndRestoreImportedModel(updated)
    }

    ServerPrefs.renameModelPrefsKey(context, oldFileName, newFileName)
    return resultModel
  }
}
