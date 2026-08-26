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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.data.allowlist.AllowedModel
import com.ollitert.llm.server.data.repository.ProtoDataStoreRepository
import com.ollitert.llm.server.data.repository.DownloadRepository
import com.ollitert.llm.server.data.model.EMPTY_MODEL
import com.ollitert.llm.server.data.allowlist.ModelUrlResult
import com.ollitert.llm.server.data.allowlist.configuredHfTokenOrNull
import com.ollitert.llm.server.data.allowlist.probeModelUrl
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_SHORT_CHARS
import com.ollitert.llm.server.data.allowlist.LoadResult
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.data.allowlist.ModelAllowlist
import com.ollitert.llm.server.data.allowlist.ModelAllowlistJson
import com.ollitert.llm.server.data.model.ModelDownloadStatus
import com.ollitert.llm.server.data.model.ModelDownloadStatusType
import com.ollitert.llm.server.data.allowlist.ModelListImportManager
import com.ollitert.llm.server.data.allowlist.RefreshResult
import com.ollitert.llm.server.data.model.Repository
import com.ollitert.llm.server.data.allowlist.RepositoryManager
import com.ollitert.llm.server.data.prefs.SOC
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.allowlist.ModelFactory
import com.ollitert.llm.server.data.repository.DefaultModelStorageRepository
import com.ollitert.llm.server.data.repository.ModelStorageRepository
import com.ollitert.llm.server.data.repository.DefaultPreferencesRepository
import com.ollitert.llm.server.data.repository.PreferencesRepository
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.data.repository.RequestLogStore
import com.ollitert.llm.server.data.repository.DefaultServerStateRepository
import com.ollitert.llm.server.data.repository.ServerStateRepository
import com.ollitert.llm.server.worker.AllowlistRefreshWorker
import com.ollitert.llm.server.di.DefaultDispatcher
import com.ollitert.llm.server.di.IoDispatcher
import com.ollitert.llm.server.di.MainDispatcher
import com.ollitert.llm.server.runtime.GpuAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "OlliteRT.ModelVM"

/** Minimum spacing between download starts for the same model — absorbs the
 *  double-tap window before WorkManager reports the first IN_PROGRESS state. */
private const val DOWNLOAD_START_DEBOUNCE_MS = 2_000L

data class ModelInitializationStatus(
  val status: ModelInitializationStatusType,
  val error: String = "",
  val initializedBackends: Set<String> = setOf(),
)

enum class ModelInitializationStatusType {
  NOT_INITIALIZED,
  INITIALIZING,
  INITIALIZED,
  ERROR,
}

enum class ModelEmptyReason {
  NONE,
  VERSION_TOO_OLD,
  UNKNOWN,
}

data class ModelManagerUiState(
  val models: List<Model> = listOf(),
  val modelDownloadStatus: Map<String, ModelDownloadStatus> = mapOf(),
  val modelInitializationStatus: Map<String, ModelInitializationStatus> = mapOf(),
  val loadingModelAllowlist: Boolean = true,
  val loadingModelAllowlistError: String = "",
  val allReposDisabled: Boolean = false,
  val selectedModel: Model = EMPTY_MODEL,
  val configValuesUpdateTrigger: Long = 0L,
  val storageUpdateTrigger: Long = 0L,
  val emptyReason: ModelEmptyReason = ModelEmptyReason.NONE,
  val requiredVersion: String? = null,
  val droppedByVersionFilter: Int = 0,
  val totalBeforeFilters: Int = 0,
)

/**
 * ViewModel responsible for managing models, their download status, and initialization.
 */
@HiltViewModel
open class ModelManagerViewModel
@Inject
constructor(
  private val downloadRepository: DownloadRepository,
  val protoDataStoreRepository: ProtoDataStoreRepository,
  private val lifecycleProvider: OlliteRTLifecycleProvider,
  private val repositoryManager: RepositoryManager,
  @param:ApplicationContext private val context: Context,
  private val preferencesRepository: PreferencesRepository = DefaultPreferencesRepository(context),
  private val serverStateRepository: ServerStateRepository = DefaultServerStateRepository(),
  private val modelStorageRepository: ModelStorageRepository = DefaultModelStorageRepository(context),
  @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : ViewModel() {
  // Onboarding flag loads asynchronously — a runBlocking DataStore read here would
  // block ViewModel construction (main thread) on disk I/O. Null means "still
  // loading"; the nav graph waits for a concrete value before picking the
  // start destination so first-run users never skip onboarding.
  private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)
  val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted.asStateFlow()

  private val externalFilesDir = context.getExternalFilesDir(null)
  protected val _uiState = MutableStateFlow(createEmptyUiState())
  val uiState = _uiState.asStateFlow()

  private val _showModelRecommendations = MutableStateFlow(
    preferencesRepository.isShowModelRecommendations()
  )
  val showModelRecommendations: StateFlow<Boolean> = _showModelRecommendations.asStateFlow()

  fun refreshShowModelRecommendations() {
    _showModelRecommendations.value = preferencesRepository.isShowModelRecommendations()
  }

  private val _toastErrorChannel = Channel<String>(Channel.BUFFERED)
  val toastErrorEvents = _toastErrorChannel.receiveAsFlow()

  init {
    viewModelScope.launch(ioDispatcher) {
      _onboardingCompleted.value = try {
        protoDataStoreRepository.isOnboardingCompleted()
      } catch (e: Exception) {
        // Degrade to completed — failing closed would trap returning users in onboarding.
        Log.w(TAG, "Failed to read onboarding flag, defaulting to completed", e)
        true
      }
    }
  }

  private val importManager = ModelListImportManager(context, protoDataStoreRepository, modelStorageRepository)
  private val importedModelCoordinator = ImportedModelCoordinator(context, protoDataStoreRepository, modelStorageRepository, preferencesRepository)

  fun completeOnboarding() {
    viewModelScope.launch(ioDispatcher) { protoDataStoreRepository.setOnboardingCompleted() }
  }

  /**
   * True when onboarding should warn that GPU acceleration is unavailable.
   * The warning shows once per device unless the user re-enables it from Settings.
   */
  fun shouldShowGpuUnavailableDialog(): Boolean =
    !GpuAvailability.isOpenClAccessible && !preferencesRepository.isGpuUnavailableDialogShown()

  /** Persists the user's acknowledgment of the GPU-unavailable warning. */
  fun onGpuUnavailableDialogConfirmed() {
    preferencesRepository.setGpuUnavailableDialogShown(true)
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

  fun processModels() {
    val models = uiState.value.models

    val nameToPrefsKey = models
      .filter { !it.imported && it.name != it.downloadFileName }
      .associate { it.name to it.downloadFileName }
    if (nameToPrefsKey.isNotEmpty()) {
      preferencesRepository.migratePerModelKeys(nameToPrefsKey)
    }

    for (model in models) {
      model.preProcess()
      ModelFactory.restoreInferenceConfig(preferencesRepository, model)
    }
  }

  fun updateConfigValuesUpdateTrigger() {
    _uiState.update { it.copy(configValuesUpdateTrigger = System.currentTimeMillis()) }
  }

  fun getSystemPrompt(prefsKey: String): String = preferencesRepository.getSystemPrompt(prefsKey)
  fun setSystemPrompt(prefsKey: String, prompt: String) = preferencesRepository.setSystemPrompt(prefsKey, prompt)
  fun isCustomPromptsEnabled(): Boolean = preferencesRepository.isCustomPromptsEnabled()
  fun setInferenceConfig(prefsKey: String, configValues: Map<String, Any>) = preferencesRepository.setInferenceConfig(prefsKey, configValues)
  fun getPort(): Int = preferencesRepository.getPort()
  fun getHfToken(): String = preferencesRepository.getHfToken()

  private fun notifyStorageChanged() {
    _uiState.update { it.copy(storageUpdateTrigger = System.currentTimeMillis()) }
  }

  fun selectModel(model: Model) {
    if (_uiState.value.selectedModel.name != model.name) {
      _uiState.update { it.copy(selectedModel = model) }
    }
  }

  /** Model name -> last download-start timestamp (single-flight for double taps). */
  private val recentDownloadStartsMs = mutableMapOf<String, Long>()

  fun downloadModel(model: Model) {
    // Single-flight: a second tap during the start window is a duplicate, not
    // a retry. Legitimate retries after a visible failure arrive later than
    // the debounce window.
    val now = android.os.SystemClock.elapsedRealtime()
    synchronized(recentDownloadStartsMs) {
      recentDownloadStartsMs.entries.removeAll { now - it.value > DOWNLOAD_START_DEBOUNCE_MS }
      val last = recentDownloadStartsMs[model.name]
      if (last != null && now - last < DOWNLOAD_START_DEBOUNCE_MS) {
        Log.d(TAG, "Ignoring duplicate download tap for '${model.name}' (debounce)")
        return
      }
      recentDownloadStartsMs[model.name] = now
    }
    if (model.updatable) {
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      mgr?.cancel(AllowlistRefreshWorker.modelUpdateNotificationId(model.name))
    }

    deleteModel(model = model)

    setDownloadStatus(
      curModel = model,
      status = ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS),
    )

    downloadRepository.downloadModel(
      model = model,
      onStatusUpdated = this::setDownloadStatus,
    )
  }

  fun cancelDownloadModel(model: Model) {
    downloadRepository.cancelDownloadModel(model)
    deleteModel(model = model)
  }

  fun retryDownloadModel(model: Model) {
    setDownloadStatus(
      curModel = model,
      status = ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS),
    )
    downloadRepository.downloadModel(
      model = model,
      onStatusUpdated = this::setDownloadStatus,
    )
  }

  fun cancelModelDownloadByName(modelName: String) {
    val model = getAllModels().find { it.name == modelName } ?: return
    cancelDownloadModel(model)
  }

  fun deleteModel(model: Model) {
    downloadRepository.cancelDownloadModel(model)
    serverStateRepository.clearErrorIfModel(model.name)

    if (model.updatable) {
      model.updatable = false
      model.latestModelFile?.let {
        model.version = it.commitHash
        model.downloadFileName = it.fileName
      }
      for (config in model.configs) {
        if (config.requiresModelUpdate) config.subtitle = null
      }
    }

    if (model.imported) {
      deleteFilesFromImportDir(model.downloadFileName)
    } else {
      deleteDirFromExternalFilesDir(model.normalizedName)
    }

    val action = if (model.imported) "Imported model deleted" else "Model deleted"
    if (preferencesRepository.isVerboseDebugEnabled()) {
      RequestLogStore.addEvent(
        "$action: ${model.name} (${model.sizeInBytes.humanReadableSize()})",
        level = LogLevel.DEBUG,
        modelName = model.name,
        category = EventCategory.MODEL,
      )
    }

    if (model.imported) {
      viewModelScope.launch(ioDispatcher) {
        importedModelCoordinator.deleteImportedModelRecord(model.name)
      }
    }
    _uiState.update { current ->
      val statusMap = current.modelDownloadStatus.toMutableMap()
      val models = if (model.imported) {
        statusMap.remove(model.name)
        current.models.filter { it.name != model.name }
      } else {
        statusMap[model.name] = ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)
        if (current.allReposDisabled) {
          current.models.filter { it.name != model.name }
        } else {
          current.models
        }
      }
      current.copy(modelDownloadStatus = statusMap, models = models)
    }
  }

  fun deleteModelAndRefreshStorage(model: Model) {
    deleteModel(model = model)
    notifyStorageChanged()
    viewModelScope.launch {
      for (delaySec in listOf(2L, 5L, 10L)) {
        kotlinx.coroutines.delay(delaySec * 1000)
        notifyStorageChanged()
      }
    }
  }

  fun setDownloadStatus(curModel: Model, status: ModelDownloadStatus) {
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

  fun getModelUrlResponse(model: Model, accessToken: String? = null): ModelUrlResult {
    val result = probeModelUrl(model.url, accessToken)
    if (result is ModelUrlResult.Error) {
      Log.e(TAG, result.message)
    }
    return result
  }

  fun addImportedLlmModel(info: ImportedModel) {
    Log.d(TAG, "adding imported llm model: $info")
    val model = importedModelCoordinator.buildAndRestoreImportedModel(info)

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

    viewModelScope.launch(ioDispatcher) {
      importedModelCoordinator.saveImportedModel(info)
    }
  }

  fun updateImportedModelDefaults(updatedInfo: ImportedModel) {
    viewModelScope.launch(ioDispatcher) {
      val updatedModel = importedModelCoordinator.updateDefaults(updatedInfo)
      _uiState.update { current ->
        val updatedModels = current.models.map { m ->
          if (m.name == updatedInfo.fileName && m.imported) updatedModel else m
        }
        current.copy(models = updatedModels)
      }
    }
  }

  fun renameImportedModel(oldFileName: String, newFileName: String, displayName: String): Boolean {
    if (oldFileName == newFileName) {
      viewModelScope.launch(ioDispatcher) {
        val updatedModel = importedModelCoordinator.renameOnlyDisplayName(oldFileName, displayName) ?: return@launch
        _uiState.update { current ->
          val updatedModels = current.models.map { m ->
            if (m.imported && m.name == oldFileName) updatedModel else m
          }
          current.copy(models = updatedModels)
        }
      }
      return true
    }

    viewModelScope.launch(ioDispatcher) {
      val updatedModel = importedModelCoordinator.renameFileAndRecord(oldFileName, newFileName, displayName) ?: return@launch
      _uiState.update { current ->
        val updatedModels = current.models.map { m ->
          if (m.imported && m.name == oldFileName) updatedModel else m
        }
        current.copy(models = updatedModels)
      }
    }
    return true
  }

  private fun processPendingDownloads() {
    downloadRepository.cancelAll {
      Log.d(TAG, "All workers are cancelled.")
      viewModelScope.launch(mainDispatcher) {
        val configuredToken = configuredHfTokenOrNull(preferencesRepository.getHfToken())
        for (model in uiState.value.models) {
          val downloadStatus = uiState.value.modelDownloadStatus[model.name]?.status
          if (downloadStatus == ModelDownloadStatusType.PARTIALLY_DOWNLOADED) {
            model.accessToken = configuredToken
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

  private val allowlistLoadCoordinator = AllowlistLoadCoordinator(
    context = context,
    protoDataStoreRepository = protoDataStoreRepository,
    repositoryManager = repositoryManager,
    modelStorageRepository = modelStorageRepository,
    importManager = importManager,
    preferencesRepository = preferencesRepository,
  )

  fun isModelSupportedOnDevice(allowedModel: AllowedModel): Boolean {
    return allowlistLoadCoordinator.isModelSupportedOnDevice(allowedModel)
  }

  /** Active allowlist load; a new load supersedes (cancels) any previous one so
   *  pull-to-refresh, retry, and navigation-triggered reloads cannot interleave
   *  and land their final uiState updates out of order. */
  private var allowlistLoadJob: kotlinx.coroutines.Job? = null

  fun loadModelAllowlist(isManualRetry: Boolean = false) {
    _uiState.update {
      it.copy(loadingModelAllowlist = true, loadingModelAllowlistError = "", allReposDisabled = false)
    }

    allowlistLoadJob?.cancel()
    allowlistLoadJob = viewModelScope.launch(ioDispatcher) {
      val result = allowlistLoadCoordinator.loadAllowlist(
        isManualRetry = isManualRetry,
        onToastError = { msg -> _toastErrorChannel.trySend(msg) },
      )

      if (result.allReposDisabled) {
        _uiState.update {
          it.copy(
            loadingModelAllowlist = false,
            allReposDisabled = true,
            models = result.models,
            modelDownloadStatus = result.disabledReposStatusMap,
          )
        }
        return@launch
      }

      if (result.models.isEmpty() && result.errorMessage.isNotEmpty() && !result.isRawAllowlist) {
        _uiState.update {
          it.copy(
            loadingModelAllowlist = false,
            loadingModelAllowlistError = result.errorMessage,
          )
        }
        return@launch
      }

      _uiState.update { it.copy(models = result.models) }
      processModels()

      if (result.isRawAllowlist) {
        _uiState.update {
          createUiState().copy(loadingModelAllowlist = false)
        }
      } else {
        _uiState.update {
          createUiState()
            .copy(
              loadingModelAllowlist = false,
              loadingModelAllowlistError = result.errorMessage,
              emptyReason = result.emptyReason,
              requiredVersion = result.requiredVersion,
              droppedByVersionFilter = result.droppedByVersionFilter,
              totalBeforeFilters = result.totalBeforeFilters,
            )
        }
      }
      notifyStorageChanged()
      processPendingDownloads()
    }
  }

  fun clearLoadModelAllowlistError() {
    processModels()
    viewModelScope.launch(ioDispatcher) {
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
    viewModelScope.launch(ioDispatcher) {
      val error = importManager.importFromUrl(url)
      if (error == null) loadModelAllowlist()
      withContext(mainDispatcher) { onResult(error) }
    }
  }

  fun importModelList(uri: Uri, onResult: (String?) -> Unit) {
    viewModelScope.launch(ioDispatcher) {
      val error = importManager.importFromUri(uri)
      if (error == null) loadModelAllowlist()
      withContext(mainDispatcher) { onResult(error) }
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

    val importedModels = mutableListOf<Model>()
    for (importedModel in protoDataStoreRepository.readImportedModels()) {
      Log.d(TAG, "stored imported model: $importedModel")
      // Reconcile registry with disk: a process death between file deletion and
      // record removal (or manual deletion via MTP/another app) leaves ghost
      // entries that report SUCCEEDED and fail opaquely at init. Purge them.
      if (!importedModelCoordinator.importedFileExists(importedModel)) {
        Log.w(TAG, "Imported model '${importedModel.fileName}' missing on disk — purging orphaned registry entry")
        importedModelCoordinator.deleteImportedModelRecord(importedModel.fileName)
        continue
      }
      val model = importedModelCoordinator.buildAndRestoreImportedModel(importedModel)
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

  private fun getModelDownloadStatus(model: Model) = modelStorageRepository.getModelDownloadStatus(model)

  private fun deleteFileFromExternalFilesDir(fileName: String) = modelStorageRepository.deleteFileFromExternalFilesDir(fileName)
  private fun deleteFilesFromImportDir(fileName: String) = modelStorageRepository.deleteFilesFromImportDir(fileName)
  private fun deleteDirFromExternalFilesDir(dir: String) = modelStorageRepository.deleteDirFromExternalFilesDir(dir)
}
