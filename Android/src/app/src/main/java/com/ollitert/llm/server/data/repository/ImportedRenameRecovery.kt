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

import com.ollitert.llm.server.proto.ImportedModel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Durable intent written before the imported file is renamed. */
internal data class ImportedRename(val original: ImportedModel, val newName: String, val displayName: String) {
  val oldName: String get() = original.fileName
  val renamed: ImportedModel get() = original.toBuilder().setFileName(newName).setDisplayName(displayName).build()
}

/**
 * Each write must be durable before returning. `prepare` and `clear` compare the journal
 * atomically. `moveRegistry` compares the original record and replaces it atomically;
 * `movePreferences` is an atomic, idempotent transaction. An unknown file state must throw.
 */
internal interface ImportedRenameStorage {
  suspend fun pending(): ImportedRename?
  suspend fun prepare(rename: ImportedRename)
  suspend fun clear(rename: ImportedRename)
  suspend fun fileExists(name: String): Boolean
  suspend fun registryRecord(name: String): ImportedModel?
  suspend fun deleteFile(name: String)
  suspend fun moveFile(oldName: String, newName: String): Boolean
  suspend fun moveRegistry(rename: ImportedRename)
  suspend fun movePreferences(rename: ImportedRename)
}

internal class ImportedModelRecoveryException(cause: Throwable) : IOException("Imported model recovery failed", cause)

/** Serializes a rename with recovery. Ambiguous states retain the journal for inspection. */
internal class ImportedRenameRecovery(
  private val storage: ImportedRenameStorage,
  private val recoverImportCopy: suspend () -> Unit = {},
) {
  private val mutex = Mutex()
  private val importsInProgress = mutableSetOf<String>()

  suspend fun recover() { mutex.withLock { checkedRecover() } }

  /** Runs a registry operation only after recovery, under the same lock as rename. */
  suspend fun <T> withConsistentState(operation: suspend () -> T): T = mutex.withLock {
    checkedRecover()
    operation()
  }

  suspend fun reserveImport(name: String): Boolean = mutex.withLock {
    checkedRecover()
    require(validName(name))
    importsInProgress.add(name)
  }

  suspend fun releaseImport(name: String) { mutex.withLock { importsInProgress.remove(name) } }

  /** Only call while holding the consistent-state lock. */
  fun isImportReserved(name: String): Boolean = name in importsInProgress

  suspend fun rename(rename: ImportedRename): Boolean = mutex.withLock {
    checkedRecover()
    require(validName(rename.oldName) && validName(rename.newName) && rename.oldName != rename.newName)
    if (rename.oldName in importsInProgress || rename.newName in importsInProgress) return@withLock false
    if (storage.registryRecord(rename.oldName) != rename.original || storage.registryRecord(rename.newName) != null ||
      !storage.fileExists(rename.oldName) || storage.fileExists(rename.newName)) return@withLock false
    storage.prepare(rename)
    val moved = try {
      storage.moveFile(rename.oldName, rename.newName)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // The filesystem may have completed the move before reporting an error.
      if (checkedRecover() == RecoveryResult.COMPLETED) return@withLock true
      throw e
    }
    if (!moved) return@withLock checkedRecover() == RecoveryResult.COMPLETED
    checkedRecover() == RecoveryResult.COMPLETED
  }

  private suspend fun checkedRecover(): RecoveryResult = try {
    recoverLocked().also { recoverImportCopy() }
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    throw ImportedModelRecoveryException(e)
  }

  private enum class RecoveryResult { NONE, ROLLED_BACK, COMPLETED }

  private suspend fun recoverLocked(): RecoveryResult {
    val rename = storage.pending() ?: return RecoveryResult.NONE
    if (!validName(rename.oldName) || !validName(rename.newName) || rename.oldName == rename.newName) {
      throw IOException("Invalid imported model rename journal")
    }
    val oldFile = storage.fileExists(rename.oldName)
    val newFile = storage.fileExists(rename.newName)
    val oldRecord = storage.registryRecord(rename.oldName)
    val newRecord = storage.registryRecord(rename.newName)
    if (oldFile && !newFile && oldRecord == rename.original && newRecord == null) {
      storage.clear(rename)
      return RecoveryResult.ROLLED_BACK
    }
    if (!oldFile && newFile &&
      ((oldRecord == rename.original && newRecord == null) || (oldRecord == null && newRecord == rename.renamed))) {
      if (oldRecord != null) storage.moveRegistry(rename)
      storage.movePreferences(rename)
      storage.clear(rename)
      return RecoveryResult.COMPLETED
    }
    throw IOException("Imported model rename cannot be recovered safely")
  }

  private fun validName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')
}
