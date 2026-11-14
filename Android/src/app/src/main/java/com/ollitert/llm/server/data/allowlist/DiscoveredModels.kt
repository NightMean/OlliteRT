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

package com.ollitert.llm.server.data.allowlist

import com.ollitert.llm.server.data.prefs.MIN_MAX_TOKENS
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val DISCOVERED_SOURCE_ID = "huggingface-discovered"
internal const val MODELSCOPE_SOURCE_ID = "modelscope-discovered"

internal enum class DiscoveredProvider(val sourceId: String, val catalogFile: String, val filePrefix: String) {
  HUGGING_FACE(DISCOVERED_SOURCE_ID, "huggingface_models.json", "hf"),
  MODELSCOPE(MODELSCOPE_SOURCE_ID, "modelscope_models.json", "ms"),
}

internal fun isDiscoveredSource(sourceId: String): Boolean =
  DiscoveredProvider.entries.any { it.sourceId == sourceId }

@Serializable
internal data class DiscoveredModel(
  val repository: String,
  val path: String,
  val revision: String,
  val size: Long,
  val vision: Boolean = false,
  val audio: Boolean = false,
  val thinking: Boolean = false,
  val tools: Boolean = false,
  val accelerators: List<String> = listOf("cpu"),
  val localId: String = UUID.randomUUID().toString(),
  val displayName: String? = null,
  val maxTokens: Int? = null,
  val topK: Int? = null,
  val topP: Float? = null,
  val temperature: Float? = null,
  val speculativeDecoding: Boolean = false,
  val provider: DiscoveredProvider = DiscoveredProvider.HUGGING_FACE,
  // Records written before site selection always came from the China site.
  val modelScopeSite: ModelScopeSite = ModelScopeSite.CHINA,
) {
  fun toAllowedModel(): AllowedModel {
    require(isValidHuggingFaceModelId(repository))
    require(isSupportedHuggingFaceFile(path))
    require(revision.matches(Regex("[a-fA-F0-9]{40}")))
    require(size > 0)
    require(runCatching { UUID.fromString(localId) }.isSuccess)
    require(accelerators.isNotEmpty() && accelerators.all { it in setOf("cpu", "gpu", "npu") })
    // Keep existing Hugging Face IDs stable while separating ModelScope identities.
    val identity = "$repository\u0000$path\u0000$localId" +
      if (provider == DiscoveredProvider.MODELSCOPE) "\u0000modelscope" else ""
    val digest = MessageDigest.getInstance("SHA-256")
      .digest(identity.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return AllowedModel(
      name = "${repository.substringAfter('/')}-${digest.take(12)}",
      modelId = repository,
      modelFile = "${provider.filePrefix}-$digest.litertlm",
      commitHash = revision,
      description = "$repository — $path",
      sizeInBytes = size,
      displayName = displayName,
      defaultConfig = DefaultConfig(
        accelerators = accelerators.distinct().joinToString(","),
        maxTokens = maxTokens,
        maxContextLength = maxTokens?.takeIf { it > MIN_MAX_TOKENS },
        topK = topK, topP = topP, temperature = temperature,
      ),
      url = when (provider) {
        DiscoveredProvider.HUGGING_FACE -> huggingFaceFileUrl(repository, revision, path)
        DiscoveredProvider.MODELSCOPE -> modelScopeFileUrl(repository, revision, path, modelScopeSite)
      },
      llmSupportImage = vision,
      llmSupportAudio = audio,
      llmSupportThinking = thinking,
      llmSupportTools = tools,
      llmSupportSpeculativeDecoding = speculativeDecoding,
    )
  }
}

/** Owns downloaded-model metadata independently of user-managed model sources. */
internal class DiscoveredModels(
  private val directory: File,
  private val provider: DiscoveredProvider = DiscoveredProvider.HUGGING_FACE,
) {
  private val json = Json { ignoreUnknownKeys = true }
  private val file = File(directory, provider.catalogFile)

  fun read(): List<DiscoveredModel> = synchronized(lock) { readLocked() }

  /** A selection remains pinned to its original revision when added again. */
  fun add(model: DiscoveredModel): Boolean = synchronized(lock) {
    require(model.provider == provider)
    model.toAllowedModel()
    val entries = readLocked()
    if (entries.any { it.repository == model.repository && it.path == model.path && it.modelScopeSite == model.modelScopeSite }) return false
    writeLocked(entries + model)
    true
  }

  fun remove(modelName: String): Boolean = synchronized(lock) {
    val entries = readLocked()
    val remaining = entries.filterNot { it.toAllowedModel().name == modelName }
    if (remaining.size == entries.size) return false
    writeLocked(remaining)
    true
  }

  private fun writeLocked(entries: List<DiscoveredModel>) {
    val encoded = json.encodeToString(entries).toByteArray(Charsets.UTF_8)
    require(encoded.size <= MAX_ALLOWLIST_RESPONSE_BYTES) { "${provider.sourceId} catalog is too large" }
    val temporary = File.createTempFile("${provider.filePrefix}-catalog-", ".tmp", directory)
    try {
      temporary.writeBytes(encoded)
      Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
      temporary.delete()
    }
  }

  private fun readLocked(): List<DiscoveredModel> {
    if (!file.exists()) return emptyList()
    require(file.length() <= MAX_ALLOWLIST_RESPONSE_BYTES) { "${provider.sourceId} catalog is too large" }
    return json.decodeFromString<List<DiscoveredModel>>(file.readText()).also { entries ->
      entries.forEach { require(it.provider == provider); it.toAllowedModel() }
    }
  }

  companion object { private val lock = Any() }
}
