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

package com.ollitert.llm.server.ui.repositories

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.FetchResult
import com.ollitert.llm.server.data.fetchBoundedResult
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelAllowlistJson
import com.ollitert.llm.server.data.REPO_LIMIT_WARNING_THRESHOLD
import com.ollitert.llm.server.data.Repository
import com.ollitert.llm.server.data.repoCacheFilename
import com.ollitert.llm.server.data.deriveRepositoryName
import com.ollitert.llm.server.ui.modelmanager.ModelAllowlistLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.UUID
import javax.inject.Inject

private const val TAG = "OlliteRT.RepoVM"

data class RepoDetailModel(
  val name: String,
  val description: String,
  val sizeInBytes: Long,
)

data class RepositoryUiState(
  val repositories: List<Repository> = emptyList(),
  val isLoading: Boolean = false,
  val addDialogError: String? = null,
  val isAdding: Boolean = false,
  val repoCountWarning: Boolean = false,
  val selectedRepo: Repository? = null,
  val detailModels: List<RepoDetailModel> = emptyList(),
)

sealed class AddRepoResult {
  data object Success : AddRepoResult()
  data class Error(val message: String) : AddRepoResult()
}

@HiltViewModel
class RepositoryViewModel @Inject constructor(
  private val dataStoreRepository: DataStoreRepository,
  @param:ApplicationContext private val context: Context,
) : ViewModel() {

  private val allowlistLoader = ModelAllowlistLoader(context, context.getExternalFilesDir(null))
  private val _uiState = MutableStateFlow(RepositoryUiState())
  val uiState: StateFlow<RepositoryUiState> = _uiState.asStateFlow()

  fun loadRepositories() {
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isLoading = true) }
      try {
        val repos = dataStoreRepository.readRepositories()
        val enriched = repos.map { repo -> enrichRepo(repo) }
        val userRepoCount = enriched.count { !it.isBuiltIn }
        _uiState.update { it.copy(
          repositories = enriched,
          isLoading = false,
          repoCountWarning = userRepoCount >= REPO_LIMIT_WARNING_THRESHOLD,
        ) }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load repositories", e)
        _uiState.update { it.copy(isLoading = false) }
      }
    }
  }

  private fun enrichRepo(repo: Repository): Repository {
    val needsName = repo.name.isEmpty()
    val needsCount = repo.modelCount == null
    if (!needsName && !needsCount) return repo
    val allowlist = readAllowlistForRepo(repo)
    val name = if (needsName) {
      allowlist?.sourceName?.ifEmpty { null } ?: deriveRepositoryName(repo.url)
    } else repo.name
    val count = if (needsCount) allowlist?.models?.size else repo.modelCount
    val version = if (allowlist != null) maxOf(repo.contentVersion, allowlist.contentVersion) else repo.contentVersion
    return repo.copy(name = name, modelCount = count, contentVersion = version)
  }

  private fun readAllowlistForRepo(repo: Repository): ModelAllowlist? {
    var allowlist = allowlistLoader.readFromDiskCache(repo.cacheFilename)
      ?: if (repo.isBuiltIn) allowlistLoader.readFromAssets() else null
    if (repo.isBuiltIn && allowlist != null) {
      val bundled = allowlistLoader.readFromAssets()
      if (bundled != null && bundled.contentVersion > allowlist.contentVersion) {
        allowlist = bundled
      }
    }
    return allowlist
  }

  fun toggleRepo(id: String, enabled: Boolean) {
    _uiState.update { state ->
      state.copy(
        selectedRepo = state.selectedRepo?.let { if (it.id == id) it.copy(enabled = enabled) else it },
      )
    }
    viewModelScope.launch(Dispatchers.IO) {
      dataStoreRepository.toggleRepositoryEnabled(id, enabled)
      loadRepositories()
    }
  }

  fun getDownloadedModelCountForRepo(repoId: String, downloadedModelRepoIds: Map<String, String>): Int {
    return downloadedModelRepoIds.count { (_, ownerRepoId) -> ownerRepoId == repoId }
  }

  fun getDownloadingModelNamesForRepo(repoId: String, downloadingModelRepoIds: Map<String, String>): List<String> {
    return downloadingModelRepoIds.filter { (_, ownerRepoId) -> ownerRepoId == repoId }.keys.toList()
  }

  fun deleteRepo(id: String) {
    viewModelScope.launch(Dispatchers.IO) {
      dataStoreRepository.removeRepository(id)
      val dir = context.getExternalFilesDir(null)
      if (dir != null) {
        java.io.File(dir, repoCacheFilename(id)).delete()
      }
      loadRepositories()
    }
  }

  fun loadRepoDetail(repoId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update { it.copy(isLoading = true) }
      val repos = try {
        dataStoreRepository.readRepositories()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to read repositories for detail", e)
        _uiState.update { it.copy(isLoading = false) }
        return@launch
      }
      val repo = repos.find { it.id == repoId }
      if (repo == null) {
        _uiState.update { it.copy(isLoading = false, selectedRepo = null, detailModels = emptyList()) }
        return@launch
      }

      val (enrichedRepo, models) = try {
        val allowlist = readAllowlistForRepo(repo)
        if (allowlist != null) {
          val enriched = repo.copy(
            name = allowlist.sourceName.ifEmpty { repo.name },
            description = allowlist.sourceDescription.ifEmpty { repo.description },
            iconUrl = allowlist.sourceIconUrl.ifEmpty { repo.iconUrl },
            contentVersion = maxOf(repo.contentVersion, allowlist.contentVersion),
            modelCount = allowlist.models.size,
          )
          val detailModels = allowlist.models.map { m ->
            RepoDetailModel(
              name = m.name,
              description = m.description,
              sizeInBytes = m.sizeInBytes,
            )
          }
          enriched to detailModels
        } else {
          repo to emptyList()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to read disk cache for repo '$repoId'", e)
        repo to emptyList()
      }

      _uiState.update { it.copy(isLoading = false, selectedRepo = enrichedRepo, detailModels = models) }
    }
  }

  fun addRepository(url: String, onResult: (AddRepoResult) -> Unit) {
    viewModelScope.launch {
      _uiState.update { it.copy(isAdding = true, addDialogError = null) }
      val result = withContext(Dispatchers.IO) { addRepositoryInternal(url) }
      _uiState.update {
        it.copy(
          isAdding = false,
          addDialogError = (result as? AddRepoResult.Error)?.message,
        )
      }
      onResult(result)
    }
  }

  private suspend fun addRepositoryInternal(url: String): AddRepoResult {
    val existingRepos = dataStoreRepository.readRepositories()

    val normalizedUrl = url.trim().trimEnd('/')

    val parsed = try { URL(normalizedUrl) } catch (_: Exception) {
      return AddRepoResult.Error("Invalid URL")
    }
    if (parsed.protocol != "https" && parsed.protocol != "http") {
      return AddRepoResult.Error("Only HTTP and HTTPS URLs are supported")
    }

    if (existingRepos.any { it.url.trimEnd('/') == normalizedUrl }) {
      return AddRepoResult.Error("This repository is already added")
    }

    val body = when (val result = fetchBoundedResult(normalizedUrl, "OlliteRT-AddRepo")) {
      is FetchResult.Success -> result.body
      is FetchResult.HttpError -> return AddRepoResult.Error(when (result.code) {
        401, 403 -> "Access denied — private repositories are not supported"
        404 -> "No file found at this URL"
        else -> "Server returned an error (HTTP ${result.code})"
      })
      is FetchResult.NetworkError -> return AddRepoResult.Error(result.message)
    }

    val allowlist: ModelAllowlist
    try {
      allowlist = ModelAllowlistJson.decode(body)
    } catch (e: Exception) {
      return AddRepoResult.Error("Could not load a valid model list from this URL")
    }

    if (allowlist.models.isEmpty()) {
      return AddRepoResult.Error("This URL does not contain a valid model list")
    }

    if (allowlist.schemaVersion > ModelAllowlist.SUPPORTED_SCHEMA_VERSION) {
      return AddRepoResult.Error("This repository requires a newer version of OlliteRT")
    }

    val repoId = UUID.randomUUID().toString()
    allowlistLoader.saveToDisk(body, repoCacheFilename(repoId))
    if (allowlistLoader.readFromDiskCache(repoCacheFilename(repoId)) == null) {
      return AddRepoResult.Error("Failed to save repository data to disk")
    }

    val repoName = allowlist.sourceName.ifEmpty { deriveRepositoryName(normalizedUrl) }
    val newRepo = Repository(
      id = repoId,
      url = normalizedUrl,
      enabled = true,
      isBuiltIn = false,
      contentVersion = allowlist.contentVersion,
      lastRefreshMs = System.currentTimeMillis(),
      lastError = "",
      name = repoName,
      description = allowlist.sourceDescription,
      iconUrl = allowlist.sourceIconUrl,
      modelCount = allowlist.models.size,
    )
    dataStoreRepository.addRepository(newRepo)
    loadRepositories()
    return AddRepoResult.Success
  }
}
