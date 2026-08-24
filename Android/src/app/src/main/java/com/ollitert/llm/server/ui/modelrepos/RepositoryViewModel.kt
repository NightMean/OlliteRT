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

package com.ollitert.llm.server.ui.modelrepos

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.data.repository.ProtoDataStoreRepository
import com.ollitert.llm.server.data.allowlist.FetchResult
import com.ollitert.llm.server.data.allowlist.fetchBoundedResult
import com.ollitert.llm.server.data.allowlist.ModelAllowlist
import com.ollitert.llm.server.data.allowlist.ModelAllowlistJson
import com.ollitert.llm.server.data.allowlist.REPO_LIMIT_WARNING_THRESHOLD
import com.ollitert.llm.server.data.model.Repository
import com.ollitert.llm.server.data.model.repoCacheFilename
import com.ollitert.llm.server.data.allowlist.deriveRepositoryName
import com.ollitert.llm.server.data.repository.DefaultModelStorageRepository
import com.ollitert.llm.server.data.repository.ModelStorageRepository
import com.ollitert.llm.server.data.allowlist.ModelListImportManager
import com.ollitert.llm.server.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
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
  val isIncompatible: Boolean = false,
  val incompatibilityReason: String = "",
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
  private val ProtoDataStoreRepository: ProtoDataStoreRepository,
  @param:ApplicationContext private val context: Context,
  private val modelStorageRepository: ModelStorageRepository = DefaultModelStorageRepository(context),
  @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val importManager = ModelListImportManager(context, ProtoDataStoreRepository, modelStorageRepository)
  private val _uiState = MutableStateFlow(RepositoryUiState())
  val uiState: StateFlow<RepositoryUiState> = _uiState.asStateFlow()

  fun loadRepositories() {
    viewModelScope.launch(ioDispatcher) {
      _uiState.update { it.copy(isLoading = true) }
      try {
        val repos = ProtoDataStoreRepository.readRepositories()
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
    val allowlist = readAllowlistForRepo(repo)
    val needsName = repo.name.isEmpty()
    val name = if (needsName) {
      allowlist?.sourceName?.ifEmpty { null } ?: deriveRepositoryName(repo.url)
    } else repo.name
    val count = allowlist?.models?.size ?: repo.modelCount
    val version = if (allowlist != null) maxOf(repo.contentVersion, allowlist.contentVersion) else repo.contentVersion
    val hidden = if (allowlist != null) {
      val appVersion = SemVer.parse(BuildConfig.VERSION_NAME) ?: return repo.copy(name = name, modelCount = count, contentVersion = version)
      allowlist.models.count { !it.isCompatibleWith(appVersion) }
    } else 0
    return repo.copy(name = name, modelCount = count, contentVersion = version, hiddenModelCount = hidden)
  }

  private fun readAllowlistForRepo(repo: Repository): ModelAllowlist? {
    var allowlist = modelStorageRepository.readFromDiskCache(repo.cacheFilename)
      ?: if (repo.isBuiltIn) modelStorageRepository.readFromAssets() else null
    if (repo.isBuiltIn && allowlist != null) {
      val bundled = modelStorageRepository.readFromAssets()
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
    viewModelScope.launch(ioDispatcher) {
      ProtoDataStoreRepository.toggleRepositoryEnabled(id, enabled)
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
    viewModelScope.launch(ioDispatcher) {
      ProtoDataStoreRepository.removeRepository(id)
      val dir = context.getExternalFilesDir(null)
      if (dir != null) {
        java.io.File(dir, repoCacheFilename(id)).delete()
      }
      loadRepositories()
    }
  }

  fun loadRepoDetail(repoId: String) {
    viewModelScope.launch(ioDispatcher) {
      _uiState.update { it.copy(isLoading = true) }
      val repos = try {
        ProtoDataStoreRepository.readRepositories()
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
          val appVersion = SemVer.parse(BuildConfig.VERSION_NAME) ?: SemVer(0, 0, 0)
          val enriched = repo.copy(
            name = allowlist.sourceName.ifEmpty { repo.name },
            description = allowlist.sourceDescription.ifEmpty { repo.description },
            iconUrl = allowlist.sourceIconUrl.ifEmpty { repo.iconUrl },
            contentVersion = maxOf(repo.contentVersion, allowlist.contentVersion),
            modelCount = allowlist.models.size,
          )
          val detailModels = allowlist.models.map { m ->
            val compatible = m.isCompatibleWith(appVersion)
            RepoDetailModel(
              name = m.name,
              description = m.description,
              sizeInBytes = m.sizeInBytes,
              isIncompatible = !compatible,
              incompatibilityReason = when {
                !compatible && m.minAppVersion != null -> "Requires v${m.minAppVersion}"
                !compatible && m.maxAppVersion != null -> "Not available after v${m.maxAppVersion}"
                else -> ""
              },
            )
          }.sortedBy { it.isIncompatible }
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
      val result = withContext(ioDispatcher) { addRepositoryInternal(url) }
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
    val existingRepos = ProtoDataStoreRepository.readRepositories()

    val normalizedUrl = url.trim().trimEnd('/')

    val parsed = try { URL(normalizedUrl) } catch (_: Exception) {
      return AddRepoResult.Error(context.getString(R.string.repo_error_invalid_url))
    }
    if (parsed.protocol != "https" && parsed.protocol != "http") {
      return AddRepoResult.Error(context.getString(R.string.repo_error_unsupported_protocol))
    }

    if (existingRepos.any { it.url.trimEnd('/') == normalizedUrl }) {
      return AddRepoResult.Error(context.getString(R.string.repo_error_already_added))
    }

    val body = when (val result = fetchBoundedResult(normalizedUrl, "OlliteRT-AddRepo")) {
      is FetchResult.Success -> result.body
      is FetchResult.HttpError -> return AddRepoResult.Error(when (result.code) {
        401, 403 -> context.getString(R.string.repo_error_access_denied)
        404 -> context.getString(R.string.repo_error_not_found)
        else -> context.getString(R.string.repo_error_http, result.code)
      })
      is FetchResult.NetworkError -> return AddRepoResult.Error(result.message)
    }

    val allowlist: ModelAllowlist
    try {
      allowlist = ModelAllowlistJson.decode(body)
    } catch (e: Exception) {
      return AddRepoResult.Error(context.getString(R.string.repo_error_invalid_model_list))
    }

    if (allowlist.models.isEmpty()) {
      return AddRepoResult.Error(context.getString(R.string.repo_error_empty_model_list))
    }

    if (allowlist.schemaVersion > ModelAllowlist.SUPPORTED_SCHEMA_VERSION) {
      return AddRepoResult.Error(context.getString(R.string.repo_error_newer_version))
    }

    val repoId = UUID.randomUUID().toString()
    modelStorageRepository.saveToDisk(body, repoCacheFilename(repoId))
    if (modelStorageRepository.readFromDiskCache(repoCacheFilename(repoId)) == null) {
      return AddRepoResult.Error(context.getString(R.string.repo_error_save_failed))
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
    ProtoDataStoreRepository.addRepository(newRepo)
    loadRepositories()
    return AddRepoResult.Success
  }

  fun addRepositoryFromFile(uri: Uri, onResult: (AddRepoResult) -> Unit) {
    viewModelScope.launch {
      _uiState.update { it.copy(isAdding = true, addDialogError = null) }
      val error = withContext(ioDispatcher) { importManager.importFromUri(uri) }
      _uiState.update { it.copy(isAdding = false) }
      if (error != null) {
        onResult(AddRepoResult.Error(error))
      } else {
        loadRepositories()
        onResult(AddRepoResult.Success)
      }
    }
  }
}
