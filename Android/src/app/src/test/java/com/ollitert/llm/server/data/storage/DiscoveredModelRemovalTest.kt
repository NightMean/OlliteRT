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

package com.ollitert.llm.server.data.storage

import com.ollitert.llm.server.data.allowlist.DiscoveredModel
import com.ollitert.llm.server.data.allowlist.DiscoveredModels
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.repository.DefaultDiscoveredModelsRepository
import com.ollitert.llm.server.data.repository.isDownloadRegistered
import com.ollitert.llm.server.common.ServerStatus
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiscoveredModelRemovalTest {
  @get:Rule val temp = TemporaryFolder()

  @Test fun queuedDownloadChecksTheRegistryAfterRemoval() {
    val root = temp.newFolder()
    val store = DiscoveredModels(root)
    val repository = DefaultDiscoveredModelsRepository { root }
    val model = DiscoveredModel("owner/model", "model.litertlm", "a".repeat(40), 123)
    store.add(model)
    val name = model.toAllowedModel().name
    assertTrue(repository.isDownloadRegistered(DiscoveredProvider.HUGGING_FACE.sourceId, name))
    assertTrue(DiscoveredModelRemoval(root, store).remove(name, "model-dir"))
    assertFalse(repository.isDownloadRegistered(DiscoveredProvider.HUGGING_FACE.sourceId, name))
    assertTrue(repository.isDownloadRegistered(null, name))
  }

  @Test fun preferenceUpdatesRequireTheModelToStillBeRegistered() {
    val root = temp.newFolder()
    val repository = DefaultDiscoveredModelsRepository { root }
    val model = DiscoveredModel("owner/model", "model.litertlm", "a".repeat(40), 123)
    val provider = DiscoveredProvider.HUGGING_FACE
    DiscoveredModels(root, provider).add(model)
    val name = model.toAllowedModel().name
    var updates = 0
    assertTrue(repository.runIfRegistered(provider, name) { updates++ })
    assertTrue(repository.remove(provider, name, "model-dir") {})
    assertFalse(repository.runIfRegistered(provider, name) { updates++ })
    assertEquals(1, updates)
  }

  @Test fun removalRequiresKnownIdleStateAndNoNativeCleanup() {
    fun allowed(status: ModelDownloadStatusType?, active: String? = null, server: ServerStatus = ServerStatus.STOPPED,
      cleanup: Boolean = false, unfinished: Boolean = false, confirmed: ModelDownloadStatusType = status ?: ModelDownloadStatusType.NOT_DOWNLOADED) =
      canRemoveModelFiles("model", status, active, server, cleanup, unfinished, confirmed)
    assertTrue(allowed(ModelDownloadStatusType.SUCCEEDED))
    assertTrue(allowed(ModelDownloadStatusType.NOT_DOWNLOADED))
    assertFalse(allowed(null))
    assertFalse(allowed(ModelDownloadStatusType.IN_PROGRESS))
    assertTrue(allowed(ModelDownloadStatusType.PARTIALLY_DOWNLOADED))
    assertFalse(allowed(ModelDownloadStatusType.PARTIALLY_DOWNLOADED, unfinished = true))
    assertFalse(allowed(ModelDownloadStatusType.SUCCEEDED, active = "MODEL", server = ServerStatus.RUNNING))
    assertFalse(allowed(ModelDownloadStatusType.SUCCEEDED, active = "model", server = ServerStatus.LOADING))
    assertFalse(allowed(ModelDownloadStatusType.SUCCEEDED, cleanup = true))
    assertFalse(allowed(ModelDownloadStatusType.SUCCEEDED, unfinished = true))
    assertFalse(allowed(ModelDownloadStatusType.SUCCEEDED, confirmed = ModelDownloadStatusType.NOT_DOWNLOADED))
  }

  @Test fun downloadedFilesAndRecordAreRemovedTogether() {
    val root = temp.newFolder()
    val store = DiscoveredModels(root)
    val model = DiscoveredModel("owner/model", "model.litertlm", "a".repeat(40), 123)
    store.add(model)
    val directory = root.resolve("model-dir").apply { mkdirs(); resolve("file").writeText("data") }
    assertTrue(DiscoveredModelRemoval(root, store).remove(model.toAllowedModel().name, "model-dir"))
    assertFalse(directory.exists())
    assertTrue(DiscoveredModels(root).read().isEmpty())
  }

  @Test fun failedFileRemovalLeavesTheImportRecord() {
    val root = temp.newFolder()
    val store = DiscoveredModels(root)
    val model = DiscoveredModel("owner/model", "model.litertlm", "a".repeat(40), 123)
    store.add(model)
    root.resolve("model-dir").mkdirs()
    assertFalse(DiscoveredModelRemoval(root, store) { false }.remove(model.toAllowedModel().name, "model-dir"))
    assertEquals(listOf(model), DiscoveredModels(root).read())
  }

  @Test fun interruptionAfterFileRemovalCanBeRetried() {
    val root = temp.newFolder()
    val store = DiscoveredModels(root)
    val model = DiscoveredModel("owner/model", "model.litertlm", "a".repeat(40), 123)
    store.add(model)
    root.resolve("model-dir").mkdirs()
    assertThrows(IOException::class.java) {
      DiscoveredModelRemoval(root, store) { directory -> directory.deleteRecursively(); throw IOException("interrupted") }
        .remove(model.toAllowedModel().name, "model-dir")
    }
    assertEquals(listOf(model), DiscoveredModels(root).read())
    assertTrue(DiscoveredModelRemoval(root, store).remove(model.toAllowedModel().name, "model-dir"))
    assertTrue(DiscoveredModels(root).read().isEmpty())
  }

  @Test fun dependentStateMustBeClearedBeforeTheRecordIsRemoved() {
    val root = temp.newFolder()
    val store = DiscoveredModels(root)
    val model = DiscoveredModel("owner/model", "model.litertlm", "a".repeat(40), 123)
    store.add(model)
    assertThrows(IOException::class.java) {
      DiscoveredModelRemoval(root, store).remove(model.toAllowedModel().name, "model-dir") {
        throw IOException("preference write failed")
      }
    }
    assertEquals(listOf(model), DiscoveredModels(root).read())
    assertTrue(DiscoveredModelRemoval(root, store).remove(model.toAllowedModel().name, "model-dir"))
  }

  @Test fun refusesDirectoriesOutsideTheModelRoot() {
    val root = temp.newFolder()
    val store = DiscoveredModels(root)
    val model = DiscoveredModel("owner/model", "model.litertlm", "a".repeat(40), 123)
    store.add(model)
    assertThrows(IllegalArgumentException::class.java) {
      DiscoveredModelRemoval(root, store).remove(model.toAllowedModel().name, "../outside")
    }
    assertEquals(listOf(model), store.read())
  }
}
