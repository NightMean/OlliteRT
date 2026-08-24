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

package com.ollitert.llm.server.ui.modelmanager

import android.content.Context
import android.util.Log
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.data.allowlist.AllowedModel
import com.ollitert.llm.server.data.repository.ProtoDataStoreRepository
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_SHORT_CHARS
import com.ollitert.llm.server.data.allowlist.LoadResult
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.allowlist.ModelAllowlist
import com.ollitert.llm.server.data.allowlist.ModelAllowlistJson
import com.ollitert.llm.server.data.repository.DefaultModelStorageRepository
import com.ollitert.llm.server.data.repository.ModelStorageRepository
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.allowlist.ModelListImportManager
import com.ollitert.llm.server.data.allowlist.RefreshResult
import com.ollitert.llm.server.data.model.Repository
import com.ollitert.llm.server.data.allowlist.RepositoryManager
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.prefs.SOC
import com.ollitert.llm.server.data.repository.DefaultPreferencesRepository
import com.ollitert.llm.server.data.repository.PreferencesRepository

private const val TAG = "OlliteRT.AllowlistCoord"

/**
 * Result data holder representing the outcome of an allowlist load / repository refresh operation.
 */
data class AllowlistLoadResult(
  val models: List<Model> = emptyList(),
  val allReposDisabled: Boolean = false,
  val disabledReposStatusMap: Map<String, ModelDownloadStatus> = emptyMap(),
  val errorMessage: String = "",
  val emptyReason: ModelEmptyReason = ModelEmptyReason.NONE,
  val requiredVersion: String? = null,
  val droppedByVersionFilter: Int = 0,
  val totalBeforeFilters: Int = 0,
  val isRawAllowlist: Boolean = false,
)

/**
 * Coordinator responsible for allowlist network retrieval, repository synchronization,
 * device/version compatibility filtering, and error diagnostic calculation.
 */
class AllowlistLoadCoordinator(
  private val context: Context,
  private val ProtoDataStoreRepository: ProtoDataStoreRepository,
  private val repositoryManager: RepositoryManager,
  private val modelStorageRepository: ModelStorageRepository = DefaultModelStorageRepository(context),
  private val importManager: ModelListImportManager,
  private val preferencesRepository: PreferencesRepository = DefaultPreferencesRepository(context),
) {

  fun isModelSupportedOnDevice(allowedModel: AllowedModel): Boolean {
    val accelerators = allowedModel.defaultConfig.accelerators
      ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
      ?: emptyList()
    if (accelerators.size == 1 && accelerators[0] == "npu") {
      val supported = allowedModel.socToModelFiles?.containsKey(SOC) == true
      if (!supported) Log.d(TAG, "Ignoring model '${allowedModel.name}' because it's NPU-only and not supported on SOC: $SOC")
      return supported
    }
    return true
  }

  suspend fun loadAllowlist(
    testAllowlistOverride: String = "",
    isManualRetry: Boolean = false,
    onToastError: (String) -> Unit = {},
  ): AllowlistLoadResult {
    modelStorageRepository.cleanupStaleImportTmpFiles()
    try {
      val testAllowlist = modelStorageRepository.readTestAllowlist()
      if (testAllowlist != null || testAllowlistOverride.isNotEmpty()) {
        val allowlist = if (testAllowlistOverride.isNotEmpty()) {
          try {
            ModelAllowlistJson.decode(testAllowlistOverride)
          } catch (e: Exception) {
            Log.e(TAG, "Failed to parse local test json", e)
            null
          }
        } else {
          testAllowlist
        }

        if (allowlist != null) {
          return loadFromRawAllowlist(allowlist)
        }
      }

      importManager.migrateDiskCacheIfNeeded()
      syncOfficialRepoUrl()

      val refreshResult = repositoryManager.refreshAll(modelStorageRepository)
      val appVersion = SemVer.parse(BuildConfig.VERSION_NAME)
      val loadResult = repositoryManager.loadAll(appVersion, modelStorageRepository, modelFilter = ::isModelSupportedOnDevice)

      val disabledResult = checkAllReposDisabled(loadResult, appVersion)
      if (disabledResult != null) {
        return disabledResult
      }

      val enabledRepos = loadResult.repositories.filter { it.enabled }
      val errorMessage = computeRefreshErrorMessage(refreshResult, enabledRepos)
      logRepoRefreshFailures(enabledRepos, refreshResult)

      if (errorMessage.isNotEmpty() && isManualRetry) {
        onToastError(context.getString(R.string.error_model_server_unreachable))
      }

      val hasOfflineRepos = enabledRepos.any { it.id in refreshResult.failedRepoIds && it.modelCount == null }
      if (loadResult.models.isEmpty() && hasOfflineRepos) {
        return AllowlistLoadResult(
          models = emptyList(),
          errorMessage = context.getString(R.string.error_all_repos_offline),
        )
      }

      val models = loadResult.models
      val emptyReason = when {
        models.isNotEmpty() -> ModelEmptyReason.NONE
        loadResult.droppedByVersionFilter > 0 -> ModelEmptyReason.VERSION_TOO_OLD
        !hasOfflineRepos && enabledRepos.isNotEmpty() -> ModelEmptyReason.UNKNOWN
        else -> ModelEmptyReason.NONE
      }

      if (preferencesRepository.isVerboseDebugEnabled()) {
        RequestLogStore.addEvent(
          "Model list loaded (${models.size} ${if (models.size == 1) "model" else "models"} from ${enabledRepos.size} ${if (enabledRepos.size == 1) "repo" else "repos"})",
          level = LogLevel.DEBUG,
          category = EventCategory.MODEL,
        )
      }

      return AllowlistLoadResult(
        models = models,
        errorMessage = errorMessage,
        emptyReason = emptyReason,
        requiredVersion = loadResult.lowestRequiredVersion,
        droppedByVersionFilter = loadResult.droppedByVersionFilter,
        totalBeforeFilters = loadResult.totalBeforeVersionFilter,
      )
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load model allowlist", e)
      val detail = e.message?.take(LOG_ERROR_PREVIEW_SHORT_CHARS) ?: context.getString(R.string.error_unknown)
      return AllowlistLoadResult(
        errorMessage = context.getString(R.string.error_model_list_load_failed_detail, detail),
      )
    }
  }

  fun loadFromRawAllowlist(allowlist: ModelAllowlist): AllowlistLoadResult {
    val appVersion = SemVer.parse(BuildConfig.VERSION_NAME)
    val models = allowlist.models
      .filter(::isModelSupportedOnDevice)
      .map { it.toModel(appVersion = appVersion) }
    return AllowlistLoadResult(models = models, isRawAllowlist = true)
  }

  suspend fun checkAllReposDisabled(loadResult: LoadResult, appVersion: SemVer?): AllowlistLoadResult? {
    val allDisabled = loadResult.repositories.isNotEmpty() &&
      loadResult.repositories.all { !it.enabled }
    if (!allDisabled) return null

    val allModelsResult = repositoryManager.loadAll(
      appVersion, modelStorageRepository, ignoreDisabled = true, modelFilter = ::isModelSupportedOnDevice,
    )
    val statusMap = mutableMapOf<String, ModelDownloadStatus>()
    for (model in allModelsResult.models) {
      statusMap[model.name] = modelStorageRepository.getModelDownloadStatus(model)
    }
    val downloadedOnly = allModelsResult.models.filter { model ->
      statusMap[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
    }
    return AllowlistLoadResult(
      models = downloadedOnly,
      allReposDisabled = true,
      disabledReposStatusMap = statusMap,
    )
  }

  fun computeRefreshErrorMessage(
    refreshResult: RefreshResult,
    enabledRepos: List<Repository>,
  ): String {
    val failedWithNoCache = enabledRepos.filter {
      it.id in refreshResult.failedRepoIds && it.modelCount == null
    }
    val failedWithCache = enabledRepos.filter {
      it.id in refreshResult.failedRepoIds && it.modelCount != null && it.modelCount > 0
    }
    return when {
      refreshResult.failedRepoIds.isEmpty() -> ""
      failedWithNoCache.size == enabledRepos.size ->
        context.getString(R.string.error_all_repos_offline)
      failedWithNoCache.isNotEmpty() ->
        context.getString(R.string.error_some_repos_unavailable, failedWithNoCache.size, enabledRepos.size)
      failedWithCache.isNotEmpty() ->
        context.getString(R.string.error_showing_cached_list)
      else -> ""
    }
  }

  fun logRepoRefreshFailures(enabledRepos: List<Repository>, refreshResult: RefreshResult) {
    if (!preferencesRepository.isVerboseDebugEnabled()) return
    for (repo in enabledRepos) {
      if (repo.id in refreshResult.failedRepoIds) {
        val name = repo.name.ifEmpty { repo.id }
        val detail = repo.lastError.ifEmpty { "unreachable" }
        RequestLogStore.addEvent(
          "Model source refresh failed: $name ($detail)",
          level = LogLevel.DEBUG,
          category = EventCategory.UPDATE,
        )
      }
    }
  }

  suspend fun syncOfficialRepoUrl() {
    try {
      val repos = ProtoDataStoreRepository.readRepositories()
      val official = repos.find { it.isBuiltIn }
      if (official != null && official.url != GitHubConfig.ALLOWLIST_URL) {
        ProtoDataStoreRepository.updateRepository(official.copy(url = GitHubConfig.ALLOWLIST_URL))
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to sync Official repo URL", e)
    }
  }
}
