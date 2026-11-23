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

import com.ollitert.llm.server.data.allowlist.DiscoveredModels
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.common.ServerStatus
import java.io.File

/** Called while holding the model lock before a queued download starts. */
internal fun discoveredDownloadIsRegistered(directory: File, sourceId: String?, name: String): Boolean {
  val provider = DiscoveredProvider.entries.firstOrNull { it.sourceId == sourceId } ?: return true
  return DiscoveredModels(directory, provider).read().any { it.toAllowedModel().name == name }
}

internal fun canInspectDiscoveredRemoval(status: ModelDownloadStatusType?): Boolean = when (status) {
  ModelDownloadStatusType.NOT_DOWNLOADED,
  ModelDownloadStatusType.PARTIALLY_DOWNLOADED,
  ModelDownloadStatusType.SUCCEEDED,
  ModelDownloadStatusType.FAILED -> true
  else -> false
}

internal fun canRemoveDiscoveredModel(
  modelName: String, diskStatus: ModelDownloadStatusType?, activeModelName: String?,
  serverStatus: ServerStatus, cleanupPending: Boolean, unfinishedDownload: Boolean,
  confirmedStatus: ModelDownloadStatusType,
): Boolean = diskStatus in setOf(ModelDownloadStatusType.NOT_DOWNLOADED, ModelDownloadStatusType.PARTIALLY_DOWNLOADED,
  ModelDownloadStatusType.SUCCEEDED) && diskStatus == confirmedStatus &&
  !cleanupPending && !unfinishedDownload &&
  !(activeModelName.equals(modelName, ignoreCase = true) && serverStatus != ServerStatus.STOPPED)

/** Removes files before the registry entry so interrupted removals remain retryable. */
internal class DiscoveredModelRemoval(
  private val root: File,
  private val store: DiscoveredModels,
  private val deleteDirectory: (File) -> Boolean = { it.deleteRecursively() },
) {
  fun remove(modelName: String, relativeDirectory: String, beforeRemoveRecord: () -> Unit = {}): Boolean {
    val rootPath = root.canonicalFile.toPath()
    val target = File(root, relativeDirectory).canonicalFile
    val targetPath = target.toPath()
    require(targetPath != rootPath && targetPath.startsWith(rootPath)) { "Invalid model directory" }
    if (store.read().none { it.toAllowedModel().name == modelName }) return false
    if (target.exists() && !deleteDirectory(target)) return false
    // Complete dependent durable state first; after a crash, an existing record can be retried.
    beforeRemoveRecord()
    return store.remove(modelName)
  }
}
