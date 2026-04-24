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

package com.ollitert.llm.server.data

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.ui.modelmanager.AllowlistLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RepositoryManager"

data class RefreshResult(
  val failedRepoIds: Set<String>,
)

data class LoadResult(
  val models: List<Model>,
  val repositories: List<Repository>,
)

@Singleton
class RepositoryManager @Inject constructor(
  private val dataStoreRepository: DataStoreRepository,
  @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {

  suspend fun loadAll(
    appVersion: SemVer?,
    allowlistLoader: AllowlistLoader,
    ignoreDisabled: Boolean = false,
    modelFilter: (AllowedModel) -> Boolean = { true },
  ): LoadResult = withContext(Dispatchers.IO) {
    var repoEntries = dataStoreRepository.readRepositories()
      .sortedByDescending { it.isBuiltIn }

    if (repoEntries.isEmpty()) {
      val legacyVersion = LlmHttpPrefs.getAllowlistContentVersion(context)
      try {
        seedOfficialRepo(legacyContentVersion = legacyVersion)
      } catch (e: Exception) {
        Log.e(TAG, "Failed to seed Official repo", e)
        return@withContext LoadResult(models = emptyList(), repositories = emptyList())
      }
      repoEntries = dataStoreRepository.readRepositories()
        .sortedByDescending { it.isBuiltIn }
      if (repoEntries.isEmpty()) {
        Log.e(TAG, "Seeding succeeded but repo list is still empty")
        return@withContext LoadResult(models = emptyList(), repositories = emptyList())
      }
    }

    data class RepoModels(val models: List<AllowedModel>, val name: String, val id: String)

    val repoModelsList = mutableListOf<RepoModels>()
    val repositories = mutableListOf<Repository>()

    for (entry in repoEntries) {
      if (!entry.enabled && !ignoreDisabled) {
        repositories.add(entry.copy(modelCount = 0))
        continue
      }

      val cacheFilename = entry.cacheFilename
      var allowlist = allowlistLoader.readFromDiskCache(cacheFilename)
        ?: if (entry.isBuiltIn) allowlistLoader.readFromAssets() else null

      // App update may bundle a newer allowlist than the disk cache (e.g. remote JSON regression).
      // Always prefer the higher contentVersion to prevent stale remote data from shadowing updates.
      if (entry.isBuiltIn && allowlist != null) {
        val bundled = allowlistLoader.readFromAssets()
        if (bundled != null && bundled.contentVersion > allowlist.contentVersion) {
          allowlist = bundled
        }
      }

      if (allowlist == null) {
        val derivedName = if (entry.name.isEmpty()) deriveRepositoryName(entry.url) else entry.name
        repositories.add(entry.copy(name = derivedName, modelCount = null))
        repoModelsList.add(RepoModels(emptyList(), derivedName, entry.id))
        continue
      }

      val filtered = if (appVersion != null) allowlist.filterCompatible(appVersion) else allowlist
      val repoName = allowlist.sourceName.ifEmpty { deriveRepositoryName(entry.url) }
        .let { truncateMetadata(it, MAX_REPO_NAME_LENGTH) }
      val repoDesc = truncateMetadata(allowlist.sourceDescription, MAX_REPO_DESCRIPTION_LENGTH)
      val repoIcon = truncateMetadata(allowlist.sourceIconUrl, MAX_REPO_ICON_URL_LENGTH)

      val validModels = filtered.models
        .filter { isValidDownloadFileName(it.modelFile) && modelFilter(it) }
        .take(MAX_MODELS_PER_REPO)

      repositories.add(
        entry.copy(
          name = repoName,
          description = repoDesc,
          iconUrl = repoIcon,
          modelCount = validModels.size,
        )
      )
      repoModelsList.add(RepoModels(validModels, repoName, entry.id))
    }

    val deduped = deduplicateAllowedModels(
      repoModelsList.map { it.models },
      repoModelsList.map { it.name },
      repoModelsList.map { it.id },
    )
    val models = deduped.map { (allowedModel, repoName, repoId) ->
      allowedModel.toModel(appVersion, repositoryName = repoName, repositoryId = repoId)
    }
    val disambiguated = disambiguateDisplayNames(models)
    LoadResult(models = disambiguated, repositories = repositories)
  }

  private suspend fun seedOfficialRepo(legacyContentVersion: Int = 0) {
    dataStoreRepository.seedRepositoryIfAbsent(
      Repository(
        id = OFFICIAL_REPO_ID,
        url = com.ollitert.llm.server.common.GitHubConfig.ALLOWLIST_URL,
        enabled = true,
        isBuiltIn = true,
        contentVersion = legacyContentVersion,
        lastRefreshMs = 0,
        lastError = "",
      )
    )
  }

  suspend fun refreshAll(allowlistLoader: AllowlistLoader): RefreshResult = withContext(Dispatchers.IO) {
    val repos = dataStoreRepository.readRepositories()
    val failedIds = mutableSetOf<String>()
    for (repo in repos) {
      if (!repo.enabled || repo.url.isBlank()) continue
      try {
        val rawJson = fetchBoundedJson(repo.url)
        if (rawJson == null) {
          failedIds.add(repo.id)
          continue
        }
        val allowlist = ModelAllowlistJson.decode(rawJson)
        if (allowlist.models.isEmpty()) {
          Log.w(TAG, "Fetched allowlist for '${repo.id}' is empty — skipping disk write")
          continue
        }

        // Compare against both stored contentVersion and bundled asset (for Official repo)
        // to prevent a regressed remote JSON from overwriting a newer bundled version.
        var minVersion = repo.contentVersion
        if (repo.isBuiltIn) {
          val bundled = allowlistLoader.readFromAssets()
          if (bundled != null && bundled.contentVersion > minVersion) {
            minVersion = bundled.contentVersion
          }
        }
        if (allowlist.contentVersion <= minVersion) {
          Log.d(TAG, "Repo '${repo.id}': fetched v${allowlist.contentVersion} <= min v$minVersion — skipping")
          if (repo.lastError.isNotEmpty()) {
            dataStoreRepository.updateRepository(repo.copy(lastRefreshMs = System.currentTimeMillis(), lastError = ""))
          }
          continue
        }

        allowlistLoader.saveToDisk(rawJson, repo.cacheFilename)
        if (allowlistLoader.readFromDiskCache(repo.cacheFilename) == null) {
          Log.w(TAG, "Disk cache write failed for '${repo.id}' — skipping DataStore update")
          failedIds.add(repo.id)
          continue
        }
        dataStoreRepository.updateRepository(
          repo.copy(
            contentVersion = allowlist.contentVersion,
            lastRefreshMs = System.currentTimeMillis(),
            lastError = "",
          )
        )
        Log.d(TAG, "Repo '${repo.id}' refreshed: v${allowlist.contentVersion}")
      } catch (e: Exception) {
        Log.w(TAG, "Failed to refresh repo '${repo.id}'", e)
        failedIds.add(repo.id)
        dataStoreRepository.updateRepository(repo.copy(lastError = e.message?.take(MAX_REPO_ERROR_LENGTH) ?: UNKNOWN_ERROR_FALLBACK))
      }
    }
    RefreshResult(failedRepoIds = failedIds)
  }

  private fun fetchBoundedJson(url: String): String? =
    fetchBounded(url, userAgent = "OlliteRT-RepoRefresh")

  companion object {
    /** Dedup by AllowedModel.modelId — first repo wins. Returns (AllowedModel, repoName, repoId) triples. */
    fun deduplicateAllowedModels(
      repoModels: List<List<AllowedModel>>,
      repoNames: List<String>,
      repoIds: List<String> = emptyList(),
    ): List<Triple<AllowedModel, String, String>> {
      val seenModelIds = mutableSetOf<String>()
      val result = mutableListOf<Triple<AllowedModel, String, String>>()

      for ((repoIndex, models) in repoModels.withIndex()) {
        val repoName = repoNames.getOrElse(repoIndex) { "" }
        val repoId = repoIds.getOrElse(repoIndex) { "" }
        for (model in models) {
          if (model.modelId in seenModelIds) continue
          seenModelIds.add(model.modelId)
          result.add(Triple(model, repoName, repoId))
        }
      }
      return result
    }

    /**
     * Append (repoName) to displayName for all models that share a name with another.
     *
     * IMPORTANT: Mutates displayName, NOT name. Model.name is the identity key used
     * throughout the codebase (model cache, download status, prefs, notifications).
     */
    fun disambiguateDisplayNames(models: List<Model>): List<Model> {
      val nameCount = mutableMapOf<String, Int>()
      for (model in models) {
        nameCount[model.name] = (nameCount[model.name] ?: 0) + 1
      }
      val duplicateNames = nameCount.filter { it.value > 1 }.keys
      if (duplicateNames.isEmpty()) return models

      return models.map { model ->
        if (model.name in duplicateNames) {
          val label = model.displayName.ifEmpty { model.name }
          val repoLabel = model.sourceRepository.ifEmpty { UNKNOWN_REPO_LABEL }
          model.copy(displayName = "$label ($repoLabel)")
        } else {
          model
        }
      }
    }

    fun isValidDownloadFileName(filename: String): Boolean =
      filename.isNotEmpty() && !filename.contains('/') && !filename.contains('\\')
        && filename != "." && filename != ".."

    fun truncateMetadata(value: String, maxLength: Int): String = value.take(maxLength)
  }
}
