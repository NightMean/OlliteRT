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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportedRenameRecoveryTest {
  private val rename = ImportedRename(ImportedModel.newBuilder().setFileName("old.litertlm").build(), "new.litertlm", "New model")

  private class Store : ImportedRenameStorage {
    var journal: ImportedRename? = null
    val files = mutableSetOf("old.litertlm")
    val records = mutableMapOf("old.litertlm" to ImportedModel.newBuilder().setFileName("old.litertlm").build())
    val favorites = mutableSetOf("old.litertlm")
    var crashAfter: String? = null
    var falseAfterMove = false
    var falseBeforeMove = false
    var failBeforePreferences = false
    var cancelBeforePreferences = false
    var moveGate: CompletableDeferred<Unit>? = null
    private fun crash(point: String) { if (crashAfter == point) throw IOException("interrupted") }
    override suspend fun pending() = journal
    override suspend fun prepare(rename: ImportedRename) { check(journal == null); journal = rename; crash("prepare") }
    override suspend fun clear(rename: ImportedRename) { check(journal == rename); journal = null; crash("clear") }
    override suspend fun fileExists(name: String) = name in files
    override suspend fun registryRecord(name: String) = records[name]
    override suspend fun moveFile(oldName: String, newName: String): Boolean {
      moveGate?.await()
      if (falseBeforeMove || oldName !in files || newName in files) return false
      files.remove(oldName); files.add(newName); crash("file"); return !falseAfterMove
    }
    override suspend fun moveRegistry(rename: ImportedRename) {
      check(records.remove(rename.oldName) == rename.original); records[rename.newName] = rename.renamed; crash("registry")
    }
    override suspend fun movePreferences(rename: ImportedRename) {
      if (cancelBeforePreferences) throw CancellationException("interrupted")
      if (failBeforePreferences) throw IOException("preferences unavailable")
      if (favorites.remove(rename.oldName)) favorites.add(rename.newName)
      crash("preferences")
    }
  }

  @Test fun interruptedOperationsRecoverIdempotently() = runTest {
    for (point in listOf("prepare", "file", "registry", "preferences", "clear")) {
      val store = Store().apply { crashAfter = point }
      try { ImportedRenameRecovery(store).rename(rename) } catch (_: IOException) { }
      store.crashAfter = null
      val recovered = ImportedRenameRecovery(store)
      recovered.recover()
      recovered.recover()
      assertNull(store.journal)
      val expected = if (point == "prepare") "old.litertlm" else "new.litertlm"
      assertEquals(setOf(expected), store.files)
      assertEquals(setOf(expected), store.records.keys)
      assertEquals(setOf(expected), store.favorites)
    }
  }

  @Test fun ambiguousFilesKeepJournalAndNeverChangeRecords() = runTest {
    for (files in listOf(emptySet(), setOf(rename.oldName, rename.newName))) {
      val store = Store().apply { journal = rename; this.files.clear(); this.files.addAll(files) }
      var failed = false
      try { ImportedRenameRecovery(store).recover() } catch (_: IOException) { failed = true }
      assertTrue(failed)
      assertEquals(rename, store.journal)
      assertEquals(setOf(rename.oldName), store.records.keys)
      assertEquals(setOf(rename.oldName), store.favorites)
    }
  }

  @Test fun refusesConflictingDestinationWithoutCreatingJournal() = runTest {
    val store = Store().apply { files.add(rename.newName) }
    assertFalse(ImportedRenameRecovery(store).rename(rename))
    assertNull(store.journal)
    assertEquals(setOf(rename.oldName), store.records.keys)
  }
  @Test fun reportsSuccessWhenMoveCompletedDespiteFalseResult() = runTest {
    val store = Store().apply { falseAfterMove = true }
    assertTrue(ImportedRenameRecovery(store).rename(rename))
    assertEquals(setOf(rename.newName), store.files)
    assertEquals(setOf(rename.newName), store.records.keys)
    assertEquals(setOf(rename.newName), store.favorites)
  }

  @Test fun unrelatedDestinationRecordCannotReceiveOldPreferences() = runTest {
    val store = Store().apply {
      journal = rename
      files.clear(); files.add(rename.newName)
      records.clear(); records[rename.newName] = rename.renamed.toBuilder().setFileSize(999).build()
    }
    var failed = false
    try { ImportedRenameRecovery(store).recover() } catch (_: IOException) { failed = true }
    assertTrue(failed)
    assertEquals(rename, store.journal)
    assertEquals(setOf(rename.oldName), store.favorites)
  }

  @Test fun failedMoveRollsBackWithoutChangingFavorite() = runTest {
    val store = Store().apply { falseBeforeMove = true }
    assertFalse(ImportedRenameRecovery(store).rename(rename))
    assertNull(store.journal)
    assertEquals(setOf(rename.oldName), store.files)
    assertEquals(setOf(rename.oldName), store.favorites)
  }

  @Test fun preferenceFailureAndCancellationRetainIntentForRetry() = runTest {
    for (cancel in listOf(false, true)) {
      val store = Store().apply { failBeforePreferences = !cancel; cancelBeforePreferences = cancel }
      try { ImportedRenameRecovery(store).rename(rename) } catch (_: IOException) { } catch (_: CancellationException) { }
      assertEquals(rename, store.journal)
      assertEquals(setOf(rename.oldName), store.favorites)
      store.failBeforePreferences = false
      store.cancelBeforePreferences = false
      ImportedRenameRecovery(store).recover()
      assertNull(store.journal)
      assertEquals(setOf(rename.newName), store.favorites)
    }
  }

  @Test fun consistentOperationWaitsForRename() = runTest {
    val store = Store().apply { moveGate = CompletableDeferred() }
    val recovery = ImportedRenameRecovery(store)
    val renameJob = async { recovery.rename(rename) }
    runCurrent()
    var invoked = false
    val readJob = async { recovery.withConsistentState { invoked = true } }
    runCurrent()
    assertFalse(invoked)
    store.moveGate!!.complete(Unit)
    assertTrue(renameJob.await())
    readJob.await()
    assertTrue(invoked)
  }

  @Test fun consistentOperationDoesNotRunIfRecoveryIsAmbiguous() = runTest {
    val store = Store().apply { journal = rename; files.add(rename.newName) }
    var invoked = false
    try { ImportedRenameRecovery(store).withConsistentState { invoked = true } } catch (_: IOException) { }
    assertFalse(invoked)
    assertEquals(rename, store.journal)
  }

  @Test fun cancellingBlockedRenameLeavesRecoverableIntent() = runTest {
    val store = Store().apply { moveGate = CompletableDeferred() }
    val job = async { ImportedRenameRecovery(store).rename(rename) }
    runCurrent()
    assertEquals(rename, store.journal)
    job.cancel()
    try { job.await() } catch (_: CancellationException) { }
    store.moveGate = null
    ImportedRenameRecovery(store).recover()
    assertNull(store.journal)
    assertEquals(setOf(rename.oldName), store.favorites)
  }

}
