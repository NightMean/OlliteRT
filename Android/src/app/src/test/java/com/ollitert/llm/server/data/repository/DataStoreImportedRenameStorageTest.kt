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
import com.ollitert.llm.server.proto.BenchmarkResults
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.PendingImportedCopy
import com.ollitert.llm.server.proto.Settings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// File-backed test store: the production adapter and real protobuf/filesystem are exercised,
// while Android's DataStore implementation is verified by instrumented tests.
private class DiskSettingsStore(
  private val file: File,
  private val beforePersist: (Settings) -> Unit = {},
  private val afterPersist: (Settings) -> Unit = {},
) : DataStore<Settings> {
  private val mutex = Mutex()
  private val state = MutableStateFlow(if (file.exists()) Settings.parseFrom(file.readBytes()) else Settings.getDefaultInstance())
  override val data: Flow<Settings> = state
  override suspend fun updateData(transform: suspend (Settings) -> Settings): Settings = mutex.withLock {
    transform(state.value).also { value ->
      beforePersist(value)
      file.writeBytes(value.toByteArray())
      state.value = value
      afterPersist(value)
    }
  }
}

private class EmptyBenchmarks : DataStore<BenchmarkResults> {
  private val state = MutableStateFlow(BenchmarkResults.getDefaultInstance())
  override val data: Flow<BenchmarkResults> = state
  override suspend fun updateData(transform: suspend (BenchmarkResults) -> BenchmarkResults): BenchmarkResults =
    transform(state.value).also { state.value = it }
}

class DataStoreImportedRenameStorageTest {
  @get:Rule val temporary = TemporaryFolder()

  @Test fun interruptedRenameSurvivesClosingAndReopeningDataStore() = runBlocking {
    for (phase in listOf("prepared", "file", "registry", "preferences")) {
      val dir = temporary.newFolder()
      val imports = File(dir, "imports").apply { mkdirs() }
      val settingsFile = File(dir, "settings.pb")
      val favoritesFile = File(dir, "favorite").apply { writeText("old.litertlm") }
      val original = ImportedModel.newBuilder().setFileName("old.litertlm").setFileSize(7).build()
      val rename = ImportedRename(original, "new.litertlm", "New")
      File(imports, rename.oldName).writeText("content")
      fun storage(ds: DataStore<Settings>) = DataStoreImportedRenameStorage(ds, { imports }, { pending ->
        if (favoritesFile.readText() == pending.oldName) favoritesFile.writeText(pending.newName)
        true
      }, { true })
      val firstStore = DiskSettingsStore(settingsFile)
      firstStore.updateData { it.toBuilder().addImportedModel(original).build() }
      val first = storage(firstStore)
      first.prepare(rename)
      if (phase != "prepared") first.moveFile(rename.oldName, rename.newName)
      if (phase == "registry" || phase == "preferences") first.moveRegistry(rename)
      if (phase == "preferences") first.movePreferences(rename)
      run {
        val secondStore = DiskSettingsStore(settingsFile)
        val repo = DefaultProtoDataStoreRepository(secondStore, EmptyBenchmarks(), storage(secondStore))
        val recovered = repo.readExistingImportedModels()
        val expected = if (phase == "prepared") rename.original else rename.renamed
        assertEquals(listOf(expected), recovered)
        assertEquals(expected.fileName, favoritesFile.readText())
        assertEquals("content", File(imports, expected.fileName).readText())
        assertFalse(File(imports, if (phase == "prepared") rename.newName else rename.oldName).exists())
        assertTrue(repo.readImportedModels().single() == expected)
      }
    }
  }
  @Test fun deleteDoesNotRemoveAnotherModelWithSamePrefix() = runBlocking {
    val dir = temporary.newFolder()
    val imports = File(dir, "imports").apply { mkdirs() }
    val first = ImportedModel.newBuilder().setFileName("a.litertlm").build()
    val second = ImportedModel.newBuilder().setFileName("a.litertlm.backup.litertlm").build()
    File(imports, first.fileName).writeText("first")
    File(imports, second.fileName).writeText("second")
    val ds = DiskSettingsStore(File(dir, "settings.pb"))
    ds.updateData { it.toBuilder().addImportedModel(first).addImportedModel(second).build() }
    val storage = DataStoreImportedRenameStorage(ds, { imports }, { true }, { true })
    val repo = DefaultProtoDataStoreRepository(ds, EmptyBenchmarks(), storage)
    repo.removeImportedModel(first.fileName)
    assertFalse(File(imports, first.fileName).exists())
    assertEquals("second", File(imports, second.fileName).readText())
    assertEquals(listOf(second), repo.readImportedModels())
  }

  @Test fun preferenceFailureRetainsJournalAndRecoveryCanRetry() = runBlocking {
    val dir = temporary.newFolder()
    val imports = File(dir, "imports").apply { mkdirs() }
    val model = ImportedModel.newBuilder().setFileName("old.litertlm").build()
    val rename = ImportedRename(model, "new.litertlm", "New")
    File(imports, model.fileName).writeText("content")
    val ds = DiskSettingsStore(File(dir, "settings.pb"))
    ds.updateData { it.toBuilder().addImportedModel(model).build() }
    var allowPreferences = false
    var favorite = model.fileName
    val storage = DataStoreImportedRenameStorage(ds, { imports }, { pending ->
      if (allowPreferences && favorite == pending.oldName) favorite = pending.newName
      allowPreferences
    }, { true })
    val repo = DefaultProtoDataStoreRepository(ds, EmptyBenchmarks(), storage)
    try { repo.renameImportedModel(rename.oldName, rename.newName, rename.displayName) } catch (_: IOException) { }
    assertEquals(rename, storage.pending())
    assertEquals(rename.oldName, favorite)
    allowPreferences = true
    assertEquals(listOf(rename.renamed), repo.readImportedModels())
    assertEquals(rename.newName, favorite)
    assertEquals(null, storage.pending())
  }

  @Test fun inaccessibleStorageNeverPurgesPendingModel() = runBlocking {
    val dir = temporary.newFolder()
    val imports = File(dir, "imports").apply { mkdirs() }
    val model = ImportedModel.newBuilder().setFileName("old.litertlm").build()
    val rename = ImportedRename(model, "new.litertlm", "New")
    File(imports, model.fileName).writeText("content")
    val ds = DiskSettingsStore(File(dir, "settings.pb"))
    ds.updateData { it.toBuilder().addImportedModel(model).build() }
    val originalStorage = DataStoreImportedRenameStorage(ds, { imports }, { true }, { true })
    originalStorage.prepare(rename)
    val unavailable = DataStoreImportedRenameStorage(ds, { null }, { true }, { true })
    val repo = DefaultProtoDataStoreRepository(ds, EmptyBenchmarks(), unavailable)
    var failed = false
    try { repo.readExistingImportedModels() } catch (_: IOException) { failed = true }
    assertTrue(failed)
    assertEquals(rename, originalStorage.pending())
    assertEquals(model, ds.data.first().importedModelList.single())
  }

  @Test fun importCannotReplaceModelThatChangedWhileCopying() = runBlocking {
    val dir = temporary.newFolder()
    val imports = File(dir, "imports").apply { mkdirs() }
    val model = ImportedModel.newBuilder().setFileName("old.litertlm").build()
    File(imports, model.fileName).writeText("original")
    val ds = DiskSettingsStore(File(dir, "settings.pb"))
    ds.updateData { it.toBuilder().addImportedModel(model).build() }
    val storage = DataStoreImportedRenameStorage(ds, { imports }, { true }, { true })
    val repo = DefaultProtoDataStoreRepository(ds, EmptyBenchmarks(), storage, DataStoreImportedCopyStorage(ds) { imports })
    val expected = repo.readImportedModels().single()
    repo.renameImportedModel("old.litertlm", "new.litertlm", "New")
    assertTrue(repo.reserveImportedModel(model.fileName))
    val stage = File.createTempFile(IMPORT_STAGE_PREFIX, IMPORT_STAGE_SUFFIX, imports).apply { writeText("updated") }
    val updated = model.toBuilder().setFileSize(stage.length()).build()
    try { repo.finalizeImportedModel(updated, expected, stage) } catch (_: IllegalStateException) { }
    assertFalse(stage.exists())
    assertEquals("original", File(imports, "new.litertlm").readText())
    assertFalse(File(imports, "old.litertlm").exists())
  }

  @Test fun interruptedCopyPublishesFileAndRecordTogetherOnRecovery() = runBlocking {
    for (replacement in listOf(false, true)) {
      val root = temporary.newFolder()
      val imports = File(root, "imports").apply { mkdirs() }
      val settingsFile = File(root, "settings.pb")
      val filename = "model.litertlm"
      val finalFile = File(imports, filename)
      val previous = if (replacement) {
        finalFile.writeText("old")
        ImportedModel.newBuilder().setFileName(filename).setFileSize(3).build()
      } else null
      val imported = ImportedModel.newBuilder().setFileName(filename).setFileSize(3).setDisplayName("New").build()
      val initial = DiskSettingsStore(settingsFile)
      if (previous != null) initial.updateData { it.toBuilder().addImportedModel(previous).build() }
      val interrupted = DiskSettingsStore(settingsFile, beforePersist = { state ->
        if (imported in state.importedModelList) throw IOException("Injected registry write failure")
      })
      fun repository(store: DataStore<Settings>) = DefaultProtoDataStoreRepository(store, EmptyBenchmarks(),
        DataStoreImportedRenameStorage(store, { imports }, { true }, { true }),
        DataStoreImportedCopyStorage(store) { imports })
      val first = repository(interrupted)
      assertTrue(first.reserveImportedModel(filename))
      val stage = File.createTempFile(IMPORT_STAGE_PREFIX, IMPORT_STAGE_SUFFIX, imports).apply { writeText("new") }
      var failed = false
      try { first.finalizeImportedModel(imported, previous, stage) } catch (_: IOException) { failed = true }
      assertTrue(failed)
      assertTrue(interrupted.data.first().hasPendingImportedCopy())
      assertFalse(stage.exists())
      assertEquals("new", finalFile.readText())

      val reopened = DiskSettingsStore(settingsFile)
      assertEquals(listOf(imported), repository(reopened).readImportedModels())
      assertFalse(reopened.data.first().hasPendingImportedCopy())
      assertEquals("new", finalFile.readText())
    }
  }

  @Test fun recoveryConsumesJournalStageBeforeSweepingAbandonedCopies() = runBlocking {
    val root = temporary.newFolder()
    val imports = File(root, "imports").apply { mkdirs() }
    val stage = File.createTempFile(IMPORT_STAGE_PREFIX, IMPORT_STAGE_SUFFIX, imports).apply { writeText("data") }
    val abandoned = File.createTempFile(IMPORT_STAGE_PREFIX, IMPORT_STAGE_SUFFIX, imports)
    val model = ImportedModel.newBuilder().setFileName("model.litertlm").setFileSize(stage.length()).build()
    val settings = DiskSettingsStore(File(root, "settings.pb"))
    val pending = PendingImportedCopy.newBuilder()
      .setModel(model).setStagedFileName(stage.name).setStagedLastModifiedMs(stage.lastModified()).build()
    settings.updateData { it.toBuilder().setPendingImportedCopy(pending).build() }

    val storage = DataStoreImportedCopyStorage(settings) { imports }
    storage.recover()

    assertEquals(listOf(model), settings.data.first().importedModelList)
    assertFalse(settings.data.first().hasPendingImportedCopy())
    assertEquals("data", File(imports, model.fileName).readText())
    assertFalse(stage.exists())
    assertFalse(abandoned.exists())
  }

  @Test fun failedJournalWriteDiscardsStageToAllowImmediateRetry() = runBlocking {
    val root = temporary.newFolder()
    val imports = File(root, "imports").apply { mkdirs() }
    val store = DiskSettingsStore(File(root, "settings.pb"), beforePersist = { state ->
      if (state.hasPendingImportedCopy()) throw IOException("Injected journal failure")
    })
    val stage = File.createTempFile(IMPORT_STAGE_PREFIX, IMPORT_STAGE_SUFFIX, imports).apply { writeText("data") }
    val model = ImportedModel.newBuilder().setFileName("model.litertlm").setFileSize(stage.length()).build()
    val storage = DataStoreImportedCopyStorage(store) { imports }

    var failed = false
    try { storage.commit(model, null, stage) } catch (_: IOException) { failed = true }

    assertTrue(failed)
    assertFalse(stage.exists())
    assertFalse(store.data.first().hasPendingImportedCopy())
    assertFalse(File(imports, model.fileName).exists())
  }

  @Test fun registryCommitWithLostAcknowledgementReturnsSuccess() = runBlocking {
    val root = temporary.newFolder()
    val imports = File(root, "imports").apply { mkdirs() }
    val stage = File.createTempFile(IMPORT_STAGE_PREFIX, IMPORT_STAGE_SUFFIX, imports).apply { writeText("data") }
    val model = ImportedModel.newBuilder().setFileName("model.litertlm").setFileSize(stage.length()).build()
    val store = DiskSettingsStore(File(root, "settings.pb"), afterPersist = { state ->
      if (model in state.importedModelList) throw IOException("Injected lost acknowledgement")
    })
    val storage = DataStoreImportedCopyStorage(store) { imports }

    storage.commit(model, null, stage)

    assertEquals(listOf(model), store.data.first().importedModelList)
    assertFalse(store.data.first().hasPendingImportedCopy())
    assertEquals("data", File(imports, model.fileName).readText())
  }

  @Test fun renameReportsSuccessIfPreferenceRetryFinishesTheTransaction() = runBlocking {
    val dir = temporary.newFolder()
    val imports = File(dir, "imports").apply { mkdirs() }
    val model = ImportedModel.newBuilder().setFileName("old.litertlm").build()
    val rename = ImportedRename(model, "new.litertlm", "New")
    File(imports, model.fileName).writeText("content")
    val ds = DiskSettingsStore(File(dir, "settings.pb"))
    ds.updateData { it.toBuilder().addImportedModel(model).build() }
    var attempts = 0
    var favorite = model.fileName
    val storage = DataStoreImportedRenameStorage(ds, { imports }, { pending ->
      attempts++
      if (attempts == 1) false else { favorite = pending.newName; true }
    }, { true })
    val repo = DefaultProtoDataStoreRepository(ds, EmptyBenchmarks(), storage)
    assertEquals(rename.renamed, repo.renameImportedModel(rename.oldName, rename.newName, rename.displayName))
    assertEquals(rename.newName, favorite)
    assertEquals(null, storage.pending())
  }

  @Test fun staleSettingsUpdateCannotRecreateRenamedModel() = runBlocking {
    val dir = temporary.newFolder()
    val imports = File(dir, "imports").apply { mkdirs() }
    val model = ImportedModel.newBuilder().setFileName("old.litertlm").build()
    File(imports, model.fileName).writeText("content")
    val ds = DiskSettingsStore(File(dir, "settings.pb"))
    ds.updateData { it.toBuilder().addImportedModel(model).build() }
    val storage = DataStoreImportedRenameStorage(ds, { imports }, { true }, { true })
    val repo = DefaultProtoDataStoreRepository(ds, EmptyBenchmarks(), storage)
    val renamed = repo.renameImportedModel(model.fileName, "new.litertlm", "New")!!
    var failed = false
    try { repo.updateImportedModel(model.fileName, model) } catch (_: IOException) { failed = true }
    assertTrue(failed)
    assertEquals(listOf(renamed), repo.readImportedModels())
  }

  @Test fun staleSettingsCannotOverwriteReplacementWithSameFileName() = runBlocking {
    val dir = temporary.newFolder()
    val imports = File(dir, "imports").apply { mkdirs() }
    val original = ImportedModel.newBuilder().setFileName("model.litertlm").setFileSize(10).build()
    val replacement = original.toBuilder().setFileSize(20).build()
    File(imports, original.fileName).writeText("replacement")
    val ds = DiskSettingsStore(File(dir, "settings.pb"))
    ds.updateData { it.toBuilder().addImportedModel(replacement).build() }
    val repo = DefaultProtoDataStoreRepository(ds, EmptyBenchmarks(),
      DataStoreImportedRenameStorage(ds, { imports }, { true }, { true }))
    var cleared = false
    var failed = false
    try { repo.replaceImportedModel(original, original.toBuilder().setDisplayName("Edited").build()) { cleared = true } }
    catch (_: IllegalStateException) { failed = true }
    assertTrue(failed)
    assertFalse(cleared)
    assertEquals(listOf(replacement), repo.readImportedModels())
  }

}
