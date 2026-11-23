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

import com.ollitert.llm.server.data.allowlist.DiscoveredModelEntry
import com.ollitert.llm.server.data.allowlist.DiscoveredModels
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.allowlist.ModelScopeSite
import com.ollitert.llm.server.data.storage.DiscoveredModelRemoval
import com.ollitert.llm.server.proto.ImportedModel
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DiscoveredCatalogSnapshot(
  val entries: List<DiscoveredModelEntry>,
  val failure: Exception? = null,
)

internal interface DiscoveredModelsRepository {
  val revisions: StateFlow<Map<DiscoveredProvider, Long>>

  /** An authoritative read suitable for decisions about model files. */
  fun read(provider: DiscoveredProvider): List<DiscoveredModelEntry>

  /** Caches the last readable catalog for display and reports refresh failures. */
  fun load(provider: DiscoveredProvider): DiscoveredCatalogSnapshot

  fun add(
    provider: DiscoveredProvider,
    repository: String,
    path: String,
    revision: String,
    size: Long,
    configuration: ImportedModel,
    site: ModelScopeSite = ModelScopeSite.CHINA,
  ): String?

  /** The caller must hold the model lock before removing its files and import record. */
  fun remove(provider: DiscoveredProvider, modelName: String, modelDirectory: String, beforeRemoveRecord: () -> Unit): Boolean
}

internal class DefaultDiscoveredModelsRepository(
  private val externalFilesDirectory: () -> File?,
) : DiscoveredModelsRepository {
  private val lastReadable = mutableMapOf<DiscoveredProvider, List<DiscoveredModelEntry>>()
  private val _revisions = MutableStateFlow<Map<DiscoveredProvider, Long>>(emptyMap())
  override val revisions = _revisions.asStateFlow()

  @Synchronized
  override fun read(provider: DiscoveredProvider): List<DiscoveredModelEntry> =
    DiscoveredModels(requireDirectory(), provider).readEntries().also { lastReadable[provider] = it }

  @Synchronized
  override fun load(provider: DiscoveredProvider): DiscoveredCatalogSnapshot = try {
    DiscoveredCatalogSnapshot(read(provider))
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    DiscoveredCatalogSnapshot(lastReadable[provider].orEmpty(), e)
  }

  @Synchronized
  override fun add(
    provider: DiscoveredProvider, repository: String, path: String, revision: String, size: Long,
    configuration: ImportedModel, site: ModelScopeSite,
  ): String? = DiscoveredModels(requireDirectory(), provider)
    .addConfiguredFile(repository, path, revision, size, configuration, site)
    ?.also { invalidate(provider) }

  @Synchronized
  override fun remove(
    provider: DiscoveredProvider, modelName: String, modelDirectory: String, beforeRemoveRecord: () -> Unit,
  ): Boolean {
    val root = requireDirectory()
    val removed = DiscoveredModelRemoval(root, DiscoveredModels(root, provider))
      .remove(modelName, modelDirectory, beforeRemoveRecord)
    if (removed) invalidate(provider)
    return removed
  }

  private fun invalidate(provider: DiscoveredProvider) {
    // A failed read after a recognized write must not publish a pre-write snapshot.
    lastReadable.remove(provider)
    _revisions.value = _revisions.value + (provider to ((_revisions.value[provider] ?: 0L) + 1L))
  }

  private fun requireDirectory(): File =
    checkNotNull(externalFilesDirectory()) { "Model storage unavailable" }
}
