/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.AppLifecycleProvider
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.DownloadRepository
import com.ollitert.llm.server.data.EMPTY_MODEL
import com.ollitert.llm.server.data.fetchBounded
import com.ollitert.llm.server.data.MAX_ALLOWLIST_RESPONSE_BYTES
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_FILENAME
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_OFFICIAL_FILENAME
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelAllowlistJson
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.Repository
import com.ollitert.llm.server.data.RepositoryManager
import com.ollitert.llm.server.data.SOC
import com.ollitert.llm.server.data.repoCacheFilename
import com.ollitert.llm.server.proto.AccessTokenData
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.ModelFactory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.worker.AllowlistRefreshWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject

private const val TAG = "OlliteRT.ModelVM"
private const val TEST_MODEL_ALLOW_LIST = ""

data class ModelInitializationStatus(
  val status: ModelInitializationStatusType,
  var error: String = "",
  var initializedBackends: Set<String> = setOf(),
)

enum class ModelInitializationStatusType {
  NOT_INITIALIZED,
  INITIALIZING,
  INITIALIZED,
  ERROR,
}

enum class TokenStatus {
  NOT_STORED,
  EXPIRED,
  NOT_EXPIRED,
}

enum class TokenRequestResultType {
  FAILED,
  SUCCEEDED,
  USER_CANCELLED,
}

data class TokenStatusAndData(val status: TokenStatus, val data: AccessTokenData?)

data class TokenRequestResult(val status: TokenRequestResultType, val errorMessage: String? = null)

enum class ModelEmptyReason {
  NONE,
  VERSION_TOO_OLD,
  UNKNOWN,
}

data class ModelManagerUiState(
  /** Flat list of all models (built-in + imported). */
  val models: List<Model> = listOf(),

  /** A map that tracks the download status of each model, indexed by model name. */
  val modelDownloadStatus: Map<String, ModelDownloadStatus> = mapOf(),

  /** A map that tracks the initialization status of each model, indexed by model name. */
  val modelInitializationStatus: Map<String, ModelInitializationStatus> = mapOf(),

  /** Whether the app is loading and processing the model allowlist. */
  val loadingModelAllowlist: Boolean = true,

  /** The error message when loading the model allowlist. */
  val loadingModelAllowlistError: String = "",

  /** True when every repository is disabled — shows "No repositories enabled" empty state. */
  val allReposDisabled: Boolean = false,

  /** The currently selected model. */
  val selectedModel: Model = EMPTY_MODEL,

  val configValuesUpdateTrigger: Long = 0L,
  // Bumped when storage changes (download complete, model deleted).
  val storageUpdateTrigger: Long = 0L,

  val emptyReason: ModelEmptyReason = ModelEmptyReason.NONE,
  val requiredVersion: String? = null,
  val droppedByVersionFilter: Int = 0,
  val totalBeforeFilters: Int = 0,
)

/**
 * ViewModel responsible for managing models, their download status, and initialization.
 *
 * This ViewModel handles model-related operations such as downloading, deleting, initializing, and
 * cleaning up models. It also manages the UI state for model management, including the list of
 * models, download statuses, and initialization statuses.
 */
@HiltViewModel
open class ModelManagerViewModel
@Inject
constructor(
  private val downloadRepository: DownloadRepository,
  val dataStoreRepository: DataStoreRepository,
  private val lifecycleProvider: AppLifecycleProvider,
  private val repositoryManager: RepositoryManager,
  @param:ApplicationContext private val context: Context,
) : ViewModel() {
  // Synchronous one-time read — NavHost requires startDestination during first composition.
  // This is the only acceptable runBlocking survivor in the codebase.
  val onboardingCompleted: Boolean = runBlocking { dataStoreRepository.isOnboardingCompleted() }

  private val externalFilesDir = context.getExternalFilesDir(null)
  protected val _uiState = MutableStateFlow(createEmptyUiState())
  val uiState = _uiState.asStateFlow()

  private val _showModelRecommendations = MutableStateFlow(
    ServerPrefs.isShowModelRecommendations(context)
  )
  val showModelRecommendations: StateFlow<Boolean> = _showModelRecommendations.asStateFlow()

  fun refreshShowModelRecommendations() {
    _showModelRecommendations.value = ServerPrefs.isShowModelRecommendations(context)
  }

  // One-shot error toast events for manual user actions (e.g. Retry on allowlist banner).
  // Only emits on error — success produces no toast.
  private val _toastErrorChannel = Channel<String>(Channel.BUFFERED)
  val toastErrorEvents = _toastErrorChannel.receiveAsFlow()

  // Extracted managers — isolate token, file, and allowlist concerns
  val tokenManager = HuggingFaceTokenManager(dataStoreRepository, context)
  val fileManager = ModelFileManager(context, externalFilesDir)
  private val allowlistLoader = ModelAllowlistLoader(context, externalFilesDir)

  // Delegated token state — kept as top-level for backward compatibility with UI code
  val authService get() = tokenManager.authService
  var curAccessToken: String
    get() = tokenManager.curAccessToken
    set(value) { tokenManager.curAccessToken = value }

  override fun onCleared() {
    tokenManager.dispose()
  }

  fun completeOnboarding() {
    viewModelScope.launch(Dispatchers.IO) { dataStoreRepository.setOnboardingCompleted() }
  }

  fun getModelByName(name: String): Model? {
    return uiState.value.models.find { it.name == name }
  }

  private fun getAllModels(): List<Model> {
    return uiState.value.models.sortedBy { it.displayName.ifEmpty { it.name } }
  }

  fun getAllDownloadedModels(): List<Model> {
    return getAllModels().filter {
      uiState.value.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED &&
        it.isLlm
    }
  }

  fun getActivelyDownloadingModelNames(): Set<String> {
    val activeStatuses = setOf(ModelDownloadStatusType.IN_PROGRESS, ModelDownloadStatusType.UNZIPPING)
    return uiState.value.modelDownloadStatus
      .filter { (_, status) -> status.status in activeStatuses }
      .keys
  }


  fun processModels() {
    val models = uiState.value.models

    // TODO: Remove after 1.0.0 — migrates 0.9.0-beta per-model prefs from model.name to
    // model.downloadFileName keys. Must run before restoreInferenceConfig.
    val nameToPrefsKey = models
      .filter { !it.imported && it.name != it.downloadFileName }
      .associate { it.name to it.downloadFileName }
    if (nameToPrefsKey.isNotEmpty()) {
      ServerPrefs.migratePerModelKeys(context, nameToPrefsKey)
    }

    for (model in models) {
      model.preProcess()
      // Restore persisted inference config (temperature, max tokens, etc.) so settings
      // survive app restarts. Overlays saved values on top of model defaults.
      ModelFactory.restoreInferenceConfig(context, model)
    }
  }

  fun updateConfigValuesUpdateTrigger() {
    _uiState.update { it.copy(configValuesUpdateTrigger = System.currentTimeMillis()) }
  }

  private fun notifyStorageChanged() {
    _uiState.update { it.copy(storageUpdateTrigger = System.currentTimeMillis()) }
  }

  fun selectModel(model: Model) {
    if (_uiState.value.selectedModel.name != model.name) {
      _uiState.update { it.copy(selectedModel = model) }
    }
  }

  fun downloadModel(model: Model) {
    if (model.updatable) {
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      mgr?.cancel(AllowlistRefreshWorker.modelUpdateNotificationId(model.name))
    }

    // Update status.
    setDownloadStatus(
      curModel = model,
      status = ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS),
    )

    // Delete the model files first.
    deleteModel(model = model)

    // Start to send download request.
    downloadRepository.downloadModel(
      model = model,
      onStatusUpdated = this::setDownloadStatus,
    )
  }

  fun cancelDownloadModel(model: Model) {
    downloadRepository.cancelDownloadModel(model)
    deleteModel(model = model)
  }

  fun cancelModelDownloadByName(modelName: String) {
    val model = getAllModels().find { it.name == modelName } ?: return
    cancelDownloadModel(model)
  }

  fun deleteModel(model: Model) {
    // Cancel any in-progress download before deleting files — prevents the WorkManager
    // worker from writing to deleted paths and reporting false success.
    downloadRepository.cancelDownloadModel(model)

    // If the downloaded version is stale (updatable), reset to the latest version so
    // re-downloading picks up the newest file.
    if (model.updatable) {
      model.updatable = false
      model.latestModelFile?.let {
        model.version = it.commitHash
        model.downloadFileName = it.fileName
      }
    }

    if (model.imported) {
      deleteFilesFromImportDir(model.downloadFileName)
    } else {
      deleteDirFromExternalFilesDir(model.normalizedName)
    }

    val action = if (model.imported) "Imported model deleted" else "Model deleted"
    RequestLogStore.addEvent(
      "$action: ${model.name} (${model.sizeInBytes.humanReadableSize()})",
      level = LogLevel.DEBUG,
      modelName = model.name,
      category = EventCategory.MODEL,
    )

    if (model.imported) {
      viewModelScope.launch(Dispatchers.IO) {
        val importedModels = dataStoreRepository.readImportedModels().toMutableList()
        val importedModelIndex = importedModels.indexOfFirst { it.fileName == model.name }
        if (importedModelIndex >= 0) {
          importedModels.removeAt(importedModelIndex)
        }
        dataStoreRepository.saveImportedModels(importedModels = importedModels)
      }
    }
    _uiState.update { current ->
      val statusMap = current.modelDownloadStatus.toMutableMap()
      val models = if (model.imported) {
        statusMap.remove(model.name)
        current.models.filter { it.name != model.name }
      } else {
        statusMap[model.name] = ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)
        current.models
      }
      current.copy(modelDownloadStatus = statusMap, models = models)
    }
  }

  /** Delete model and notify storage change (for user-initiated deletions). */
  fun deleteModelAndRefreshStorage(model: Model) {
    deleteModel(model = model)
    notifyStorageChanged()
  }

  fun setDownloadStatus(curModel: Model, status: ModelDownloadStatus) {
    // Delete downloaded file if status is failed or not_downloaded.
    if (
      status.status == ModelDownloadStatusType.FAILED ||
        status.status == ModelDownloadStatusType.NOT_DOWNLOADED
    ) {
      deleteFileFromExternalFilesDir(curModel.downloadFileName)
    }

    val now = if (status.status == ModelDownloadStatusType.SUCCEEDED) System.currentTimeMillis() else null
    _uiState.update { current ->
      val statusMap = current.modelDownloadStatus.toMutableMap()
      statusMap[curModel.name] = status
      var updated = current.copy(modelDownloadStatus = statusMap)
      if (now != null) {
        updated = updated.copy(storageUpdateTrigger = now)
      }
      updated
    }
  }

  fun getModelUrlResponse(model: Model, accessToken: String? = null): Int {
    var connection: HttpURLConnection? = null
    var redirectConn: HttpURLConnection? = null
    try {
      val url = URL(model.url)
      connection = url.openConnection() as HttpURLConnection
      connection.requestMethod = "HEAD"
      // Disable auto-redirect so we can distinguish a valid CDN redirect (3xx with
      // binary content) from an auth failure redirect (3xx to HTML login page).
      // HuggingFace returns 302 to login page for invalid/expired tokens, which
      // with followRedirects=true would appear as 200 — masking the auth failure.
      connection.instanceFollowRedirects = false
      if (accessToken != null) {
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
      }
      connection.connect()

      val responseCode = connection.responseCode
      // HuggingFace CDN uses 302 redirects for both valid downloads (→ CDN binary)
      // and auth failures (→ HTML login page). Follow one level and check content type.
      if (responseCode in 300..399) {
        val redirectUrl = connection.getHeaderField("Location")
        if (redirectUrl == null) {
          return if (accessToken != null) HttpURLConnection.HTTP_UNAUTHORIZED else HttpURLConnection.HTTP_FORBIDDEN
        }
        redirectConn = URL(redirectUrl).openConnection() as HttpURLConnection
        redirectConn.requestMethod = "HEAD"
        redirectConn.instanceFollowRedirects = true
        redirectConn.connect()
        val contentType = redirectConn.contentType ?: ""
        // HTML page = login/error page, not a valid model file.
        if (contentType.contains("text/html", ignoreCase = true)) {
          Log.d(TAG, "Redirect landed on HTML page — auth required or token invalid")
          return if (accessToken != null) HttpURLConnection.HTTP_UNAUTHORIZED else HttpURLConnection.HTTP_FORBIDDEN
        }
        return redirectConn.responseCode
      }
      return responseCode
    } catch (e: Exception) {
      Log.e(TAG, "$e")
      return -1
    } finally {
      connection?.disconnect()
      redirectConn?.disconnect()
    }
  }

  fun addImportedLlmModel(info: ImportedModel) {
    Log.d(TAG, "adding imported llm model: $info")

    // Create model.
    val model = ModelFactory.buildImportedModel(info)
    ModelFactory.restoreInferenceConfig(context, model)

    val now = System.currentTimeMillis()
    _uiState.update { current ->
      val updatedModels = current.models
        .filter { !(it.name == info.fileName && it.imported) }
        .plus(model)
      val statusMap = current.modelDownloadStatus.toMutableMap()
      val initMap = current.modelInitializationStatus.toMutableMap()
      statusMap[model.name] = ModelDownloadStatus(
        status = ModelDownloadStatusType.SUCCEEDED,
        receivedBytes = info.fileSize,
        totalBytes = info.fileSize,
      )
      initMap[model.name] = ModelInitializationStatus(
        status = ModelInitializationStatusType.NOT_INITIALIZED,
      )
      current.copy(
        models = updatedModels,
        modelDownloadStatus = statusMap,
        modelInitializationStatus = initMap,
        storageUpdateTrigger = now,
      )
    }

    viewModelScope.launch(Dispatchers.IO) {
      val importedModels = dataStoreRepository.readImportedModels().toMutableList()
      val importedModelIndex = importedModels.indexOfFirst { info.fileName == it.fileName }
      if (importedModelIndex >= 0) {
        Log.d(TAG, "duplicated imported model found in data store. Removing it first")
        importedModels.removeAt(importedModelIndex)
      }
      importedModels.add(info)
      dataStoreRepository.saveImportedModels(importedModels = importedModels)
    }

    RequestLogStore.addEvent(
      "Model imported: ${info.fileName} (${info.fileSize.humanReadableSize()})",
      level = LogLevel.DEBUG,
      modelName = info.fileName,
      category = EventCategory.MODEL,
    )
  }

  /**
   * Updates the stored defaults for an imported model (capabilities, inference params).
   * Clears any saved inference config overrides so the user starts fresh from the new defaults.
   * The in-memory Model object is rebuilt and the model list is refreshed.
   */
  fun updateImportedModelDefaults(updatedInfo: ImportedModel) {
    Log.d(TAG, "updating imported model defaults: ${updatedInfo.fileName}")

    viewModelScope.launch(Dispatchers.IO) {
      dataStoreRepository.updateImportedModel(updatedInfo.fileName, updatedInfo)
    }

    // Clear inference config overrides so saved values don't conflict with new defaults
    ServerPrefs.clearInferenceConfig(context, updatedInfo.fileName)

    // Rebuild the Model object from updated proto
    val updatedModel = ModelFactory.buildImportedModel(updatedInfo)

    // Replace the model in the flat list
    _uiState.update { current ->
      val updatedModels = current.models.map { m ->
        if (m.name == updatedInfo.fileName && m.imported) updatedModel else m
      }
      current.copy(models = updatedModels)
    }

    RequestLogStore.addEvent(
      "Imported model defaults updated: ${updatedInfo.fileName}",
      level = LogLevel.DEBUG,
      modelName = updatedInfo.fileName,
      category = EventCategory.MODEL,
    )
  }

  // Token management — delegated to HuggingFaceTokenManager
  suspend fun getTokenStatusAndData() = tokenManager.getTokenStatusAndData()
  fun handleAuthResult(result: ActivityResult, onTokenRequested: (TokenRequestResult) -> Unit) =
    tokenManager.handleAuthResult(result, onTokenRequested)
  fun saveAccessToken(accessToken: String, refreshToken: String, expiresAt: Long) =
    tokenManager.saveAccessToken(accessToken, refreshToken, expiresAt)

  private fun processPendingDownloads() {
    // Cancel all pending downloads for the retrieved models.
    downloadRepository.cancelAll {
      Log.d(TAG, "All workers are cancelled.")

      viewModelScope.launch(Dispatchers.Main) {
        val tokenStatusAndData = getTokenStatusAndData()
        for (model in uiState.value.models) {
          // Start download for partially downloaded models.
          val downloadStatus = uiState.value.modelDownloadStatus[model.name]?.status
          if (downloadStatus == ModelDownloadStatusType.PARTIALLY_DOWNLOADED) {
            if (
              tokenStatusAndData.status == TokenStatus.NOT_EXPIRED &&
                tokenStatusAndData.data != null
            ) {
              model.accessToken = tokenStatusAndData.data.accessToken
            }
            Log.d(TAG, "Sending a new download request for '${model.name}'")
            downloadRepository.downloadModel(
              model = model,
              onStatusUpdated = this@ModelManagerViewModel::setDownloadStatus,
            )
          }
        }
      }
    }
  }

  fun loadModelAllowlist(isManualRetry: Boolean = false) {
    _uiState.update {
      it.copy(loadingModelAllowlist = true, loadingModelAllowlistError = "", allReposDisabled = false)
    }

    viewModelScope.launch(Dispatchers.IO) {
      fileManager.cleanupStaleImportTmpFiles()
      try {
        // Test allowlist override — development-only path
        val testAllowlist = allowlistLoader.readTestAllowlist()
        if (testAllowlist != null || TEST_MODEL_ALLOW_LIST.isNotEmpty()) {
          val allowlist = if (TEST_MODEL_ALLOW_LIST.isNotEmpty()) {
            try { ModelAllowlistJson.decode(TEST_MODEL_ALLOW_LIST) } catch (e: Exception) {
              Log.e(TAG, "Failed to parse local test json", e)
              null
            }
          } else testAllowlist

          if (allowlist != null) {
            loadFromAllowlist(allowlist)
            return@launch
          }
        }

        // Migrate legacy single-file cache → per-repo filename
        migrateDiskCacheIfNeeded()

        // Sync Official repo URL in case the app was updated with a new default
        syncOfficialRepoUrl()

        // Fetch fresh data from all enabled repos (network → disk cache).
        // Failures are per-repo — loadAll() still picks up existing cache as fallback.
        val refreshResult = repositoryManager.refreshAll(allowlistLoader)

        val appVersion = SemVer.parse(BuildConfig.VERSION_NAME)
        val loadResult = repositoryManager.loadAll(appVersion, allowlistLoader) { allowedModel ->
          // NPU/SoC filtering — skip NPU-only models unsupported on this device
          val accelerators = allowedModel.defaultConfig.accelerators?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
          if (accelerators.size == 1 && accelerators[0] == "npu") {
            val supported = allowedModel.socToModelFiles?.containsKey(SOC) == true
            if (!supported) Log.d(TAG, "Ignoring model '${allowedModel.name}' because it's NPU-only and not supported on SOC: $SOC")
            supported
          } else true
        }

        // Check if all repos are disabled
        val allDisabled = loadResult.repositories.isNotEmpty() &&
          loadResult.repositories.all { !it.enabled }
        if (allDisabled) {
          val allModelsResult = repositoryManager.loadAll(
            appVersion, allowlistLoader, ignoreDisabled = true,
          ) { allowedModel ->
            val accelerators = allowedModel.defaultConfig.accelerators?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            if (accelerators.size == 1 && accelerators[0] == "npu") {
              allowedModel.socToModelFiles?.containsKey(SOC) == true
            } else true
          }
          _uiState.update {
            val statusMap = it.modelDownloadStatus.toMutableMap()
            for (model in allModelsResult.models) {
              if (model.name !in statusMap) {
                statusMap[model.name] = getModelDownloadStatus(model = model)
              }
            }
            val downloadedOnly = allModelsResult.models.filter { model ->
              statusMap[model.name]?.status == ModelDownloadStatusType.SUCCEEDED
            }
            it.copy(
              loadingModelAllowlist = false,
              allReposDisabled = true,
              models = downloadedOnly,
              modelDownloadStatus = statusMap.toMap(),
            )
          }
          return@launch
        }

        // Determine offline banner message by cross-referencing refresh failures
        // with load results: a repo that failed to refresh but still loaded from
        // cache is "stale" (has models), not "offline" (no models).
        val enabledRepos = loadResult.repositories.filter { it.enabled }
        // modelCount == null means the allowlist couldn't be read at all (truly no cache);
        // modelCount == 0 means the allowlist loaded but all models were filtered out (e.g. version).
        val failedWithNoCache = enabledRepos.filter {
          it.id in refreshResult.failedRepoIds && it.modelCount == null
        }
        val failedWithCache = enabledRepos.filter {
          it.id in refreshResult.failedRepoIds && it.modelCount != null && it.modelCount > 0
        }
        val errorMessage = when {
          refreshResult.failedRepoIds.isEmpty() -> ""
          failedWithNoCache.size == enabledRepos.size ->
            context.getString(R.string.error_all_repos_offline)
          failedWithNoCache.isNotEmpty() ->
            context.getString(R.string.error_some_repos_unavailable, failedWithNoCache.size, enabledRepos.size)
          failedWithCache.isNotEmpty() ->
            context.getString(R.string.error_showing_cached_list)
          else -> ""
        }
        // Log per-repo failures so they're visible in the Logs tab
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

        if (errorMessage.isNotEmpty() && isManualRetry) {
          _toastErrorChannel.trySend(context.getString(R.string.error_model_server_unreachable))
        }

        if (loadResult.models.isEmpty() && failedWithNoCache.isNotEmpty()) {
          _uiState.update {
            it.copy(
              loadingModelAllowlist = false,
              loadingModelAllowlistError = context.getString(R.string.error_all_repos_offline),
            )
          }
          return@launch
        }

        val models = loadResult.models

        val emptyReason = when {
          models.isNotEmpty() -> ModelEmptyReason.NONE
          loadResult.droppedByVersionFilter > 0 -> ModelEmptyReason.VERSION_TOO_OLD
          failedWithNoCache.isEmpty() && enabledRepos.isNotEmpty() -> ModelEmptyReason.UNKNOWN
          else -> ModelEmptyReason.NONE
        }

        if (ServerPrefs.isVerboseDebugEnabled(context)) {
          RequestLogStore.addEvent(
            "Model list loaded (${models.size} ${if (models.size == 1) "model" else "models"} from ${enabledRepos.size} ${if (enabledRepos.size == 1) "repo" else "repos"})",
            level = LogLevel.DEBUG,
            category = EventCategory.MODEL,
          )
        }

        _uiState.update { it.copy(models = models) }
        processModels()
        _uiState.update {
          createUiState()
            .copy(
              loadingModelAllowlist = false,
              loadingModelAllowlistError = errorMessage,
              emptyReason = emptyReason,
              requiredVersion = loadResult.lowestRequiredVersion,
              droppedByVersionFilter = loadResult.droppedByVersionFilter,
              totalBeforeFilters = loadResult.totalBeforeVersionFilter,
            )
        }
        notifyStorageChanged()
        processPendingDownloads()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load model allowlist", e)
        _uiState.update {
          it.copy(
            loadingModelAllowlist = false,
            loadingModelAllowlistError = context.getString(R.string.error_model_list_load_failed_detail, e.message?.take(80) ?: context.getString(R.string.error_unknown)),
          )
        }
      }
    }
  }

  private suspend fun loadFromAllowlist(allowlist: ModelAllowlist) {
    val appVersion = SemVer.parse(BuildConfig.VERSION_NAME)
    val models = allowlist.models
      .filter { allowedModel ->
        val accelerators = allowedModel.defaultConfig.accelerators?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        if (accelerators.size == 1 && accelerators[0] == "npu") {
          allowedModel.socToModelFiles?.containsKey(SOC) == true
        } else true
      }
      .map { it.toModel(appVersion = appVersion) }

    _uiState.update { it.copy(models = models) }
    processModels()
    _uiState.update {
      createUiState().copy(loadingModelAllowlist = false)
    }
    processPendingDownloads()
  }

  private suspend fun syncOfficialRepoUrl() {
    try {
      val repos = dataStoreRepository.readRepositories()
      val official = repos.find { it.isBuiltIn }
      if (official != null && official.url != GitHubConfig.ALLOWLIST_URL) {
        dataStoreRepository.updateRepository(official.copy(url = GitHubConfig.ALLOWLIST_URL))
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to sync Official repo URL", e)
    }
  }

  fun clearLoadModelAllowlistError() {
    processModels()
    viewModelScope.launch(Dispatchers.IO) {
      _uiState.update {
        createUiState()
          .copy(
            loadingModelAllowlist = false,
            loadingModelAllowlistError = "",
          )
      }
    }
  }

  fun setAppInForeground(foreground: Boolean) {
    lifecycleProvider.isAppInForeground = foreground
  }

  fun importModelListFromUrl(url: String, onResult: (String?) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val error = importModelListFromUrlInternal(url)
      if (error == null) loadModelAllowlist()
      withContext(Dispatchers.Main) { onResult(error) }
    }
  }

  fun importModelList(uri: Uri, onResult: (String?) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      val error = importModelListFromUri(uri)
      if (error == null) loadModelAllowlist()
      withContext(Dispatchers.Main) { onResult(error) }
    }
  }

  private suspend fun importModelListFromUri(uri: Uri): String? {
    val fileSize = try {
      context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    } catch (_: Exception) { 0L }
    if (fileSize > MAX_ALLOWLIST_RESPONSE_BYTES) {
      return context.getString(
        R.string.import_model_list_error_too_large,
        "${MAX_ALLOWLIST_RESPONSE_BYTES / 1024 / 1024}MB",
      )
    }
    val body = try {
      context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to read model list file", e)
      return context.getString(R.string.import_model_list_error_read)
    }
    if (body.isNullOrBlank()) return context.getString(R.string.import_model_list_error_empty)
    return validateAndSaveModelList(body, repoUrl = "")
  }

  private suspend fun importModelListFromUrlInternal(url: String): String? {
    val normalizedUrl = url.trim().trimEnd('/')
    val parsed = try { java.net.URL(normalizedUrl) } catch (_: Exception) {
      return context.getString(R.string.import_model_list_error_invalid_url)
    }
    if (parsed.protocol != "https" && parsed.protocol != "http") {
      return context.getString(R.string.import_model_list_error_invalid_url)
    }
    val body = try {
      fetchBounded(normalizedUrl, userAgent = "OlliteRT-ModelListImport")
    } catch (e: Exception) {
      Log.e(TAG, "Failed to fetch model list from URL", e)
      null
    }
    if (body.isNullOrBlank()) return context.getString(R.string.import_model_list_error_fetch)
    return validateAndSaveModelList(body, repoUrl = normalizedUrl)
  }

  private suspend fun validateAndSaveModelList(body: String, repoUrl: String): String? {
    val allowlist: ModelAllowlist
    try {
      allowlist = ModelAllowlistJson.decode(body)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to parse model list JSON", e)
      return context.getString(R.string.import_model_list_error_invalid_json)
    }

    if (allowlist.models.isEmpty()) return context.getString(R.string.import_model_list_error_no_models)

    if (allowlist.schemaVersion > ModelAllowlist.SUPPORTED_SCHEMA_VERSION) {
      return context.getString(R.string.import_model_list_error_newer_version)
    }

    val repoId = UUID.randomUUID().toString()
    val cacheFilename = repoCacheFilename(repoId)
    allowlistLoader.saveToDisk(body, cacheFilename)
    if (allowlistLoader.readFromDiskCache(cacheFilename) == null) {
      Log.w(TAG, "Disk cache write failed for imported model list")
      return context.getString(R.string.import_model_list_error_read)
    }
    val repoName = allowlist.sourceName.ifEmpty {
      context.getString(R.string.import_model_list_default_name)
    }
    dataStoreRepository.addRepository(
      Repository(
        id = repoId,
        url = repoUrl,
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
    )
    Log.d(TAG, "Imported model list as repo '$repoName' ($repoId) with ${allowlist.models.size} models")
    return null
  }

  private fun migrateDiskCacheIfNeeded() {
    val dir = context.getExternalFilesDir(null) ?: return
    val officialFile = File(dir, MODEL_ALLOWLIST_OFFICIAL_FILENAME)
    val legacyFile = File(dir, MODEL_ALLOWLIST_FILENAME)
    if (!officialFile.exists() && legacyFile.exists()) {
      if (legacyFile.renameTo(officialFile)) {
        Log.d(TAG, "Migrated disk cache: $MODEL_ALLOWLIST_FILENAME → $MODEL_ALLOWLIST_OFFICIAL_FILENAME")
      } else {
        Log.e(TAG, "Failed to migrate disk cache")
      }
    }
  }

  private fun createEmptyUiState(): ModelManagerUiState {
    return ModelManagerUiState()
  }

  private suspend fun createUiState(): ModelManagerUiState {
    val modelDownloadStatus: MutableMap<String, ModelDownloadStatus> = mutableMapOf()
    val modelInstances: MutableMap<String, ModelInitializationStatus> = mutableMapOf()
    for (model in uiState.value.models) {
      modelDownloadStatus[model.name] = getModelDownloadStatus(model = model)
      modelInstances[model.name] =
        ModelInitializationStatus(status = ModelInitializationStatusType.NOT_INITIALIZED)
    }

    // Load imported models.
    val importedModels = mutableListOf<Model>()
    for (importedModel in dataStoreRepository.readImportedModels()) {
      Log.d(TAG, "stored imported model: $importedModel")

      val model = ModelFactory.buildImportedModel(importedModel)
      ModelFactory.restoreInferenceConfig(context, model)
      importedModels.add(model)

      modelDownloadStatus[model.name] =
        ModelDownloadStatus(
          status = ModelDownloadStatusType.SUCCEEDED,
          receivedBytes = importedModel.fileSize,
          totalBytes = importedModel.fileSize,
        )
    }

    Log.d(TAG, "model download status: $modelDownloadStatus")
    return ModelManagerUiState(
      models = uiState.value.models + importedModels,
      modelDownloadStatus = modelDownloadStatus,
      modelInitializationStatus = modelInstances,
    )
  }


  /**
   * Retrieves the download status of a model.
   *
   * This function determines the download status of a given model by checking if it's fully
   * downloaded, partially downloaded, or not downloaded at all. It also retrieves the received and
   * total bytes for partially downloaded models.
   */
  private fun getModelDownloadStatus(model: Model) = fileManager.getModelDownloadStatus(model)

  // File management — delegated to ModelFileManager
  private fun deleteFileFromExternalFilesDir(fileName: String) = fileManager.deleteFileFromExternalFilesDir(fileName)
  private fun deleteFilesFromImportDir(fileName: String) = fileManager.deleteFilesFromImportDir(fileName)
  private fun deleteDirFromExternalFilesDir(dir: String) = fileManager.deleteDirFromExternalFilesDir(dir)

}

