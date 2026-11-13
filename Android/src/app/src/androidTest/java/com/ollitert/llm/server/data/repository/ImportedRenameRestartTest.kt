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
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.core.DataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ollitert.llm.server.data.allowlist.ModelCatalogMerger
import com.ollitert.llm.server.data.model.IMPORTS_DIR
import com.ollitert.llm.server.service.inference.ModelLifecycle
import com.ollitert.llm.server.data.prefs.BenchmarkResultsSerializer
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.prefs.SettingsSerializer
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run twice with the same id and checkpoint, force-stopping the target between runs. */
@RunWith(AndroidJUnit4::class)
class ImportedRenameRestartTest {
  @Test fun recoveryAcrossProcessRestart() = runBlocking {
    val args = InstrumentationRegistry.getArguments()
    val phase = args.getString("rename_recovery_phase")
    val checkpoint = args.getString("rename_recovery_checkpoint")
    val id = args.getString("rename_recovery_id")
    assumeTrue(phase in setOf("prepare", "verify") && checkpoint in setOf("prepared", "file", "registry", "preferences") &&
      id != null && id.matches(Regex("[a-zA-Z0-9]+")))
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val dir = File(context.cacheDir, "rename-recovery-$id")
    val imports = File(dir, IMPORTS_DIR)
    val prefsName = "rename-recovery-$id"
    val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    val job = SupervisorJob()
    val scope = CoroutineScope(job + Dispatchers.IO)
    val settings = DataStoreFactory.create(serializer = SettingsSerializer, scope = scope) { File(dir, "settings.pb") }
    val benchmarks = DataStoreFactory.create(serializer = BenchmarkResultsSerializer, scope = scope) { File(dir, "benchmarks.pb") }
    val original = ImportedModel.newBuilder().setFileName("old.litertlm").setFileSize(7)
      .setLlmConfig(LlmConfig.newBuilder().addCompatibleAccelerators("CPU").setDefaultMaxTokens(1024)).build()
    val rename = ImportedRename(original, "new.litertlm", "New")
    val storage = DataStoreImportedRenameStorage(settings, { imports }, { entry ->
      ServerPrefs.completeImportedRename(prefs, entry.oldName, entry.newName, "old", "new")
    }, { ServerPrefs.syncModelPreferences(prefs) })
    try {
      if (phase == "prepare") {
        check(!dir.exists())
        check(imports.mkdirs())
        check(prefs.edit().putStringSet("favorite_models", setOf("old"))
          .putString("default_model_name", "old")
          .putString("system_prompt_old.litertlm", "original prompt")
          .putString("system_prompt_new.litertlm", "stale prompt")
          .putString("inference_config_new.litertlm", "stale config").commit())
        File(imports, rename.oldName).writeText("content")
        settings.updateData { it.toBuilder().addImportedModel(original).build() }
        storage.prepare(rename)
        if (checkpoint != "prepared") storage.moveFile(rename.oldName, rename.newName)
        if (checkpoint == "registry" || checkpoint == "preferences") storage.moveRegistry(rename)
        if (checkpoint == "preferences") storage.movePreferences(rename)
      } else {
        val repo = DefaultProtoDataStoreRepository(settings, benchmarks, storage)
        val expected = if (checkpoint == "prepared") original else rename.renamed
        // These are the same repository entry points used by the server and Models.
        assertEquals(listOf(expected), repo.readImportedModels())
        assertEquals(listOf(expected), repo.readExistingImportedModels())
        val expectedId = if (checkpoint == "prepared") "old" else "new"
        assertEquals(setOf(expectedId), prefs.getStringSet("favorite_models", emptySet()))
        assertEquals(expectedId, prefs.getString("default_model_name", null))
        if (checkpoint == "prepared") {
          assertEquals("original prompt", prefs.getString("system_prompt_old.litertlm", null))
          assertEquals("stale prompt", prefs.getString("system_prompt_new.litertlm", null))
        } else {
          assertEquals("original prompt", prefs.getString("system_prompt_new.litertlm", null))
          assertFalse(prefs.contains("inference_config_new.litertlm"))
          assertFalse(prefs.contains("system_prompt_old.litertlm"))
          assertFalse(prefs.contains("inference_config_old.litertlm"))
        }
        assertEquals("content", File(imports, expected.fileName).readText())
        val isolatedContext = object : ContextWrapper(context) {
          override fun getExternalFilesDir(type: String?): File = dir
          override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        }
        val lifecycle = ModelLifecycle(isolatedContext, ModelCatalogMerger(dir)) {
          runBlocking { repo.readImportedModels() }
        }
        val selected = lifecycle.pickModelByName(expectedId)
        assertEquals(expectedId, selected?.name)
        assertEquals(expected.fileName, selected?.storageFileName)
        assertFalse(File(imports, if (checkpoint == "prepared") rename.newName else rename.oldName).exists())
        assertEquals(null, storage.pending())
      }
    } finally {
      job.cancelAndJoin()
      if (phase == "verify") {
        assertTrue(dir.deleteRecursively())
        assertTrue(context.deleteSharedPreferences(prefsName))
      }
    }
  }
  @Test fun atomicReplacementWorksOnAppStorage() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val dir = File(context.cacheDir, "import-atomic-${System.nanoTime()}")
    check(dir.mkdirs())
    try {
      val destination = File(dir, "model.litertlm").apply { writeText("old") }
      val staged = File(dir, "staged.part").apply { writeText("new") }
      java.nio.file.Files.move(staged.toPath(), destination.toPath(),
        java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      assertEquals("new", destination.readText())
      assertFalse(staged.exists())
    } finally {
      assertTrue(dir.deleteRecursively())
    }
  }

}
