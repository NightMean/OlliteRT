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

import androidx.datastore.core.DataStore
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.PendingImportedRename
import com.ollitert.llm.server.proto.Settings
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.flow.first

/** The DataStore intent is cleared only after the model record and preferences are durable. */
internal class DataStoreImportedRenameStorage(
  private val dataStore: DataStore<Settings>,
  private val importsDirectory: () -> File?,
  private val completePreferences: (ImportedRename) -> Boolean,
  private val syncPreferences: () -> Boolean,
) : ImportedRenameStorage {
  private fun ImportedRename.toProto(): PendingImportedRename = PendingImportedRename.newBuilder()
    .setOriginal(original).setNewFileName(newName).setDisplayName(displayName).build()

  override suspend fun pending(): ImportedRename? {
    val settings = dataStore.data.first()
    if (!settings.hasPendingImportedRename()) return null
    val journal = settings.pendingImportedRename
    if (!journal.hasOriginal()) throw IOException("Invalid imported model rename journal")
    return ImportedRename(journal.original, journal.newFileName, journal.displayName)
  }

  override suspend fun prepare(rename: ImportedRename) {
    // Flush pending preference writes before a process can stop between intent and move.
    if (!syncPreferences()) throw IOException("Could not persist model preferences before rename")
    dataStore.updateData { settings ->
      check(!settings.hasPendingImportedRename()) { "Another imported model rename is pending" }
      check(settings.importedModelList.count { it.fileName == rename.oldName } == 1 &&
        settings.importedModelList.any { it == rename.original } &&
        settings.importedModelList.none { it.fileName == rename.newName }) { "Imported model changed before rename" }
      settings.toBuilder().setPendingImportedRename(rename.toProto()).build()
    }
  }

  override suspend fun clear(rename: ImportedRename) {
    dataStore.updateData { settings ->
      check(settings.hasPendingImportedRename() && settings.pendingImportedRename == rename.toProto()) {
        "Imported model rename journal changed"
      }
      settings.toBuilder().clearPendingImportedRename().build()
    }
  }

  private fun path(name: String): java.nio.file.Path {
    if (name.isBlank() || name == "." || name == ".." || name.any { it == '/' || it == '\\' || it.isISOControl() }) {
      throw IOException("Invalid imported model file name")
    }
    val directory = importsDirectory() ?: throw IOException("Imported model storage is unavailable")
    if (!directory.isDirectory) throw IOException("Imported model directory is unavailable")
    return directory.toPath().resolve(name)
  }

  override suspend fun fileExists(name: String): Boolean {
    val path = path(name)
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return true
    if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return false
    throw IOException("Cannot inspect imported model file")
  }

  override suspend fun registryRecord(name: String): ImportedModel? {
    val records = dataStore.data.first().importedModelList.filter { it.fileName == name }
    if (records.size > 1) throw IOException("Duplicate imported model records")
    return records.firstOrNull()
  }

  override suspend fun deleteFile(name: String) {
    require(name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\'))
    Files.deleteIfExists(path(name))
  }

  override suspend fun moveFile(oldName: String, newName: String): Boolean {
    Files.move(path(oldName), path(newName))
    return true
  }

  override suspend fun moveRegistry(rename: ImportedRename) {
    dataStore.updateData { settings ->
      check(settings.hasPendingImportedRename() && settings.pendingImportedRename == rename.toProto())
      val models = settings.importedModelList.toMutableList()
      val index = models.indexOfFirst { it == rename.original }
      check(index >= 0 && models.count { it.fileName == rename.oldName } == 1 &&
        models.none { it.fileName == rename.newName }) { "Imported model changed during rename" }
      models[index] = rename.renamed
      settings.toBuilder().clearImportedModel().addAllImportedModel(models).build()
    }
  }

  override suspend fun movePreferences(rename: ImportedRename) {
    if (!completePreferences(rename)) throw IOException("Could not save imported model preferences")
  }
}
