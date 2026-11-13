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

import android.util.Log
import androidx.datastore.core.DataStore
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.PendingImportedCopy
import com.ollitert.llm.server.proto.Settings
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal const val IMPORT_STAGE_PREFIX = ".ollitert-import-"
internal const val IMPORT_STAGE_SUFFIX = ".part"

internal class UnregisteredImportFileException : IOException("Import destination has no registry record")

/** Called under the same lock as imported-model registry reads, writes, and renames. */
internal class DataStoreImportedCopyStorage(
  private val dataStore: DataStore<Settings>,
  private val importsDirectory: () -> File?,
) {
  private var checkedForAbandonedCopies = false

  suspend fun recover() {
    val settings = dataStore.data.first()
    if (settings.hasPendingImportedCopy()) complete(settings.pendingImportedCopy)
    // Only the first access can sweep the directory, before any copy can reserve a name.
    if (!checkedForAbandonedCopies) {
      importsDirectory()?.let { directory ->
        directory.listFiles { _, name -> isStageName(name) }?.forEach { stage ->
          if (!stage.delete() && stage.exists()) Log.w("OlliteRT.Import", "Could not remove abandoned import stage")
        }
        checkedForAbandonedCopies = true
      }
    }
  }

  suspend fun commit(model: ImportedModel, previous: ImportedModel?, stagedFile: File) {
    var stageMayBeJournaled = false
    try {
      val stage = path(stagedFile.name)
      check(isStageName(stagedFile.name) && stage == stagedFile.toPath().toAbsolutePath().normalize()) {
        "Invalid import stage"
      }
      val staged = attributes(stage) ?: throw IOException("Import stage is missing")
      check(staged.size() == model.fileSize) { "Import stage has the wrong size" }
      val finalPath = path(model.fileName)
      val replace = attributes(finalPath) != null
      if (replace && previous == null) throw UnregisteredImportFileException()
      val journal = PendingImportedCopy.newBuilder()
        .setModel(model)
        .setStagedFileName(stagedFile.name)
        .setStagedLastModifiedMs(staged.lastModifiedTime().toMillis())
        .setReplaceExistingFile(replace)
        .apply { previous?.let(::setPreviousModel) }
        .build()
      val current = dataStore.data.first()
      checkPreviousRecord(current, journal)
      // updateData may persist the journal even if its acknowledgement is lost.
      stageMayBeJournaled = true
      try {
        dataStore.updateData { settings ->
          check(!settings.hasPendingImportedRename() && !settings.hasPendingImportedCopy())
          checkPreviousRecord(settings, journal)
          settings.toBuilder().setPendingImportedCopy(journal).build()
        }
        complete(journal)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // Resolve lost acknowledgements or retry a transient write while the lock is held.
        val recovered = try {
          val settings = dataStore.data.first()
          if (settings.hasPendingImportedCopy()) {
            if (settings.pendingImportedCopy != journal) false else {
              complete(journal)
              true
            }
          } else {
            stageMayBeJournaled = false
            if (journal.model !in settings.importedModelList || attributes(stage) != null) false else {
              checkAttributes(attributes(finalPath), journal)
              true
            }
          }
        } catch (recoveryError: CancellationException) {
          throw recoveryError
        } catch (recoveryError: Exception) {
          if (recoveryError !== e) e.addSuppressed(recoveryError)
          false
        }
        if (!recovered) throw e
      }
    } finally {
      if (!stageMayBeJournaled) stagedFile.delete()
    }
  }

  private suspend fun complete(journal: PendingImportedCopy) {
    check(journal.hasModel() && isStageName(journal.stagedFileName)) { "Invalid import journal" }
    val beforeMove = dataStore.data.first()
    check(beforeMove.hasPendingImportedCopy() && beforeMove.pendingImportedCopy == journal)
    checkPreviousRecord(beforeMove, journal)
    val stage = path(journal.stagedFileName)
    val finalPath = path(journal.model.fileName)
    check(stage != finalPath) { "Import stage and destination are identical" }
    val staged = attributes(stage)
    if (staged != null) {
      checkAttributes(staged, journal)
      check((attributes(finalPath) != null) == journal.replaceExistingFile) { "Import destination changed" }
      if (journal.replaceExistingFile) {
        Files.move(stage, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      } else {
        Files.move(stage, finalPath, StandardCopyOption.ATOMIC_MOVE)
      }
    } else {
      // The journal owns the stage. Only this atomic move can remove it before the journal clears.
      checkAttributes(attributes(finalPath), journal)
    }
    dataStore.updateData { settings ->
      check(settings.hasPendingImportedCopy() && settings.pendingImportedCopy == journal)
      checkPreviousRecord(settings, journal)
      val models = settings.importedModelList.toMutableList()
      val index = models.indexOfFirst { it.fileName == journal.model.fileName }
      if (index >= 0) models[index] = journal.model else models.add(journal.model)
      settings.toBuilder().clearImportedModel().addAllImportedModel(models).clearPendingImportedCopy().build()
    }
  }

  private fun checkPreviousRecord(settings: Settings, journal: PendingImportedCopy) {
    val records = settings.importedModelList.filter { it.fileName == journal.model.fileName }
    val expected = if (journal.hasPreviousModel()) listOf(journal.previousModel) else emptyList()
    check(records == expected) { "The imported model changed while the file was copied" }
  }

  private fun checkAttributes(attributes: BasicFileAttributes?, journal: PendingImportedCopy) {
    if (attributes == null || attributes.size() != journal.model.fileSize ||
      attributes.lastModifiedTime().toMillis() != journal.stagedLastModifiedMs
    ) throw IOException("Cannot identify the published import file")
  }

  private fun path(name: String): Path {
    check(name.isNotBlank() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it.isISOControl() })
    val directory = importsDirectory() ?: throw IOException("Imported model storage is unavailable")
    return directory.toPath().toAbsolutePath().normalize().resolve(name)
  }

  private fun attributes(path: Path): BasicFileAttributes? = try {
    Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).also {
      if (!it.isRegularFile) throw IOException("Imported model path is not a regular file")
    }
  } catch (_: NoSuchFileException) {
    null
  }

  private fun isStageName(name: String): Boolean =
    name.startsWith(IMPORT_STAGE_PREFIX) && name.endsWith(IMPORT_STAGE_SUFFIX)
}
