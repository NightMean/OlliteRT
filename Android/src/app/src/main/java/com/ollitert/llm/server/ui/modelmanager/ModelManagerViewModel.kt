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
import androidx.work.Operation
import androidx.work.await
import com.ollitert.llm.server.OlliteRTLifecycleProvider
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.data.allowlist.AllowedModel
import com.ollitert.llm.server.data.allowlist.DiscoveredProvider
import com.ollitert.llm.server.data.allowlist.isDiscoveredSource
import com.ollitert.llm.server.data.repository.DefaultDiscoveredModelsRepository
import com.ollitert.llm.server.data.repository.DiscoveredModelsRepository
import com.ollitert.llm.server.data.storage.ModelDownloadLocks
import com.ollitert.llm.server.data.storage.canInspectDiscoveredRemoval
import com.ollitert.llm.server.data.prefs.DefaultModelGuard
import com.ollitert.llm.server.data.storage.canRemoveDiscoveredModel
import com.ollitert.llm.server.data.repository.ImportedModelRecoveryException
import com.ollitert.llm.server.data.repository.UnregisteredImportFileException
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.util.UUID

private const val TAG = "OlliteRT.ModelVM"
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

internal data class DiscoveredRemovalInspection(val diskStatus: ModelDownloadStatusType, val isDefault: Boolean)

internal fun preserveActiveDownloadStatuses(
  loaded: Map<String, ModelDownloadStatus>,
  previous: Map<String, ModelDownloadStatus>,
  revisionsAtLoadStart: Map<String, Long>,
  currentRevisions: Map<String, Long>,
  inspectedActiveNames: Set<String>?,
): Map<String, ModelDownloadStatus> = loaded.mapValues { (name, diskStatus) ->
  previous[name]?.takeIf {
    val active = it.status == ModelDownloadStatusType.IN_PROGRESS || it.status == ModelDownloadStatusType.UNZIPPING
    (active && (inspectedActiveNames == null || name in inspectedActiveNames)) ||
      (it.status == ModelDownloadStatusType.FAILED && it.modelScopeAccessDeniedSite.isNotEmpty() &&
        diskStatus.status != ModelDownloadStatusType.SUCCEEDED) ||
      (!active && (currentRevisions[name] ?: 0L) != (revisionsAtLoadStart[name] ?: 0L))
  } ?: diskStatus
}

/**
 * ViewModel responsible for managing models, their download status, and initialization.
 */
@HiltViewModel
open class ModelManagerViewModel
@Inject
internal constructor(
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
  private val discoveredModelsRepository: DiscoveredModelsRepository =
    DefaultDiscoveredModelsRepository { context.getExternalFilesDir(null) },
) : ViewModel() {
  // Onboarding flag loads asynchronously — a runBlocking DataStore read here would
  // block ViewModel construction (main thread) on disk I/O. Null means "still
  // loading"; the nav graph waits for a concrete value before picking the
  // start destination so first-run users never skip onboarding.
  private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)
  val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted.asStateFlow()

  protected val _uiState = MutableStateFlow(createEmptyUiState())
  val uiState = _uiState.asStateFlow()

  private val repositoryImportRetries = DiscoveredProvider.entries.associateWith { MutableStateFlow(0L) }
  private val importedRepositoryState = DiscoveredProvider.entries.associateWith { provider ->
    val revisions = discoveredModelsRepository.revisions.map { it[provider] }.distinctUntilChanged()
    val snapshots = combine(revisions, repositoryImportRetries.getValue(provider)) { _, _ ->
      discoveredModelsRepository.load(provider).also { snapshot ->
        snapshot.failure?.let { Log.e(TAG, "Could not read ${provider.displayName} imports", it) }
      }
    }
    combine(snapshots, uiState, ::repositoryImportsState)
      .flowOn(ioDispatcher)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), RepositoryImportsState())
  }

  internal fun repositoryImports(provider: DiscoveredProvider): StateFlow<RepositoryImportsState> =
    importedRepositoryState.getValue(provider)

  internal fun retryRepositoryImports(provider: DiscoveredProvider) {
    repositoryImportRetries.getValue(provider).update { it + 1 }
  }

  private val favoritesLock = Any()
  private val _renamingFavoriteIds = MutableStateFlow<Set<String>>(emptySet())
  val renamingFavoriteIds: StateFlow<Set<String>> = _renamingFavoriteIds.asStateFlow()
  private val _favoriteModels = MutableStateFlow(preferencesRepository.getFavoriteModels().toSet())
  val favoriteModels: StateFlow<Set<String>> = _favoriteModels.asStateFlow()

  fun toggleFavorite(name: String) = synchronized(favoritesLock) {
    if (name in _renamingFavoriteIds.value) return@synchronized
    _favoriteModels.value = preferencesRepository.setModelFavorite(name, name !in _favoriteModels.value)
  }

  private fun removeFavorite(name: String) = synchronized(favoritesLock) {
    _favoriteModels.value = preferencesRepository.setModelFavorite(name, false)
  }

  fun refreshFavoriteModels() = synchronized(favoritesLock) {
    _favoriteModels.value = preferencesRepository.getFavoriteModels().toSet()
  }

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
  private val importedModelCoordinator = ImportedModelCoordinator(context, protoDataStoreRepository, preferencesRepository)

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

  fun processModels() = processModels(uiState.value.models)

  private fun processModels(models: List<Model>) {
    val nameToPrefsKey = models
      .filter { !it.imported && it.name != it.downloadFileName }
      .associate { it.name to it.downloadFileName }
    if (nameToPrefsKey.isNotEmpty()) {
      preferencesRepository.migratePerModelKeys(nameToPrefsKey)
    }

    // Imported models historically persisted their complete filename as the default model.
    // Resolve that legacy identity once and persist the new logical ID for future launches.
    val configuredDefault = preferencesRepository.getDefaultModelName()
    val migratedDefault = models.firstOrNull {
      it.imported && it.storageFileName.equals(configuredDefault, ignoreCase = true)
    }
    if (migratedDefault != null) {
      preferencesRepository.setDefaultModelName(migratedDefault.name)
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

  internal fun hasModelScopeToken(international: Boolean): Boolean =
    preferencesRepository.getModelScopeToken(international).isNotBlank()

  internal fun acknowledgeModelScopeFailure(id: String) {
    _uiState.update { state -> state.copy(modelDownloadStatus = state.modelDownloadStatus.mapValues { (_, status) ->
      if (status.failureId == id) status.copy(modelScopeAccessDeniedSite = "") else status
    }) }
  }

  private fun notifyStorageChanged() {
    _uiState.update { it.copy(storageUpdateTrigger = System.currentTimeMillis()) }
  }

  fun selectModel(model: Model) {
    if (_uiState.value.selectedModel.name != model.name) {
      _uiState.update { it.copy(selectedModel = model) }
    }
  }

  /** Shared by initial and retry commands until WorkManager reports a terminal state. */
  private val downloadStartGate = DownloadStartGate()
  private val downloadCallbackGate = DownloadCallbackGate()
  private val downloadObservers = mutableMapOf<String, Job>()
  private val downloadStatusRevisions = mutableMapOf<String, Long>()
  private fun markDownloadStatusChanged(name: String) = synchronized(downloadStatusRevisions) {
    downloadStatusRevisions[name] = (downloadStatusRevisions[name] ?: 0L) + 1L
  }
  private fun downloadStatusRevisionSnapshot(): Map<String, Long> = synchronized(downloadStatusRevisions) {
    downloadStatusRevisions.toMap()
  }
  private val removingModelNames = mutableSetOf<String>()
  private var recoveringDownloads = false
  private var recoveringModelNames: Set<String> = emptySet()
  private var downloadInspectionAvailable = false

  private fun enqueueModelDownload(model: Model) {
    val generation = invalidateDownloadObservation(model.name)
    val request = downloadRepository.downloadModel(model)
    observeDownload(model, request.id, generation, request.enqueue)
  }

  private fun invalidateDownloadObservation(name: String): Long {
    val generation = downloadCallbackGate.next(name)
    downloadObservers.remove(name)?.cancel()
    return generation
  }

  private fun observeDownload(model: Model, workerId: UUID, generation: Long, enqueue: Operation? = null) {
    val name = model.name
    val observer = viewModelScope.launch(mainDispatcher, start = CoroutineStart.LAZY) {
      try {
        enqueue?.await()
        downloadRepository.observeWorkerProgress(workerId, model.totalBytes).collect { status ->
          if (downloadCallbackGate.isCurrent(name, generation)) setDownloadStatus(model, status)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Could not observe download for '$name'", e)
        if (downloadCallbackGate.isCurrent(name, generation)) {
          // A failed status read cannot tell us whether WorkManager is still writing the file.
          downloadInspectionAvailable = false
          downloadStartGate.release(name)
          val message = context.getString(R.string.model_download_inspection_failed)
          _uiState.update { it.copy(loadingModelAllowlistError = message) }
        }
      } finally {
        if (downloadObservers[name] === currentCoroutineContext()[Job]) downloadObservers.remove(name)
      }
    }
    downloadObservers.put(name, observer)?.cancel()
    observer.start()
  }

  internal fun inspectDiscoveredModelRemoval(model: Model, onResult: (DiscoveredRemovalInspection?) -> Unit) {
    if (!downloadInspectionAvailable || !isDiscoveredSource(model.sourceRepositoryId) ||
      !canInspectDiscoveredRemoval(uiState.value.modelDownloadStatus[model.name]?.status)
    ) {
      onResult(null)
      return
    }
    viewModelScope.launch(mainDispatcher) {
      val inspection = try {
        withContext(ioDispatcher) {
          ModelDownloadLocks.tryWithModel(model.name) {
            val provider = DiscoveredProvider.entries.first { it.sourceId == model.sourceRepositoryId }
            val exists = discoveredModelsRepository.read(provider).any { it.allowed.name == model.name }
            val status = modelStorageRepository.getModelDownloadStatus(model).status
            if (!exists || !canRemoveDiscoveredModel(model.name, status, serverStateRepository.activeModelName.value,
                serverStateRepository.status.value, ModelDownloadLocks.isCleanupPending(),
                downloadRepository.hasUnfinishedDownload(model.name), status)) null
            else DiscoveredRemovalInspection(status, preferencesRepository.getDefaultModelName().equals(model.name, ignoreCase = true))
          }
        }
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { Log.w(TAG, "Could not inspect discovered model removal", e); null }
      onResult(inspection)
    }
  }

  fun removeDiscoveredModel(model: Model, confirmedStatus: ModelDownloadStatusType = ModelDownloadStatusType.NOT_DOWNLOADED,
    onResult: (Boolean) -> Unit) {
    if (!downloadInspectionAvailable || model.name in recoveringModelNames || !isDiscoveredSource(model.sourceRepositoryId) || !downloadStartGate.tryAcquire(model.name)) {
      onResult(false)
      return
    }
    val status = _uiState.value.modelDownloadStatus[model.name]?.status
    if (!canInspectDiscoveredRemoval(status)) {
      downloadStartGate.release(model.name)
      onResult(false)
      return
    }
    removingModelNames.add(model.name)
    allowlistLoadJob?.cancel()
    var removalGeneration = allowlistLoadGenerationGate.beginLoad()
    viewModelScope.launch(mainDispatcher) {
      try {
        val removed = withContext(ioDispatcher) {
          ModelDownloadLocks.tryWithModel(model.name) {
            val statusOnDisk = modelStorageRepository.getModelDownloadStatus(model).status
            if (!canRemoveDiscoveredModel(model.name, statusOnDisk, serverStateRepository.activeModelName.value,
                serverStateRepository.status.value, ModelDownloadLocks.isCleanupPending(),
                downloadRepository.hasUnfinishedDownload(model.name), confirmedStatus)) false
            else {
              DefaultModelGuard.beginRemoval(model.name)
              var removed = false
              val provider = DiscoveredProvider.entries.first { it.sourceId == model.sourceRepositoryId }
              try {
                removed = discoveredModelsRepository.remove(provider, model.name, model.normalizedName) {
                  preferencesRepository.clearDefaultModelIfMatches(model.name)
                }
                if (removed) {
                  try { preferencesRepository.clearInferenceConfig(model.prefsKey) }
                  catch (e: Exception) { Log.w(TAG, "Could not clear removed model settings", e) }
                }
                removed
              } finally {
                val usable = !removed && runCatching {
                  discoveredModelsRepository.read(provider).any { it.allowed.name == model.name } &&
                    modelStorageRepository.getModelDownloadStatus(model).status == ModelDownloadStatusType.SUCCEEDED
                }.getOrDefault(false)
                DefaultModelGuard.finishRemoval(model.name, usable)
              }
            }
          }
        }
        if (removed == true) {
          removeFavorite(model.name)
          notifyStorageChanged()
          allowlistLoadJob?.cancel()
          removalGeneration = allowlistLoadGenerationGate.beginLoad()
          _uiState.update { current ->
            current.copy(
              models = current.models.filterNot { it.name == model.name },
              modelDownloadStatus = current.modelDownloadStatus - model.name,
            )
          }
        } else {
          // File removal can fail partway; reload the authoritative disk status.
          notifyStorageChanged()
          loadModelAllowlist()
        }
        onResult(removed == true)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to remove discovered model", e)
        notifyStorageChanged()
        loadModelAllowlist()
        onResult(false)
      } finally {
        allowlistLoadGenerationGate.publishIfCurrent(removalGeneration) {
          _uiState.update { it.copy(loadingModelAllowlist = false) }
        }
        removingModelNames.remove(model.name)
        downloadStartGate.release(model.name)
      }
    }
  }

  fun downloadModel(model: Model): Boolean {
    if (!downloadInspectionAvailable) {
      _toastErrorChannel.trySend(context.getString(R.string.model_download_inspection_failed))
      return false
    }
    if (model.name in recoveringModelNames) {
      _toastErrorChannel.trySend(context.getString(R.string.model_download_recovering))
      return false
    }
    if (model.name in removingModelNames) return false
    if (!downloadStartGate.tryAcquire(model.name)) {
      Log.d(TAG, "Ignoring duplicate download start for '${model.name}'")
      return false
    }
    if (isDiscoveredSource(model.sourceRepositoryId) && _uiState.value.models.none { it.name == model.name }) {
      downloadStartGate.release(model.name)
      return false
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

    try {
      enqueueModelDownload(model)
      return true
    } catch (failure: Throwable) {
      downloadStartGate.release(model.name)
      throw failure
    }
  }

  fun cancelDownloadModel(model: Model) {
    invalidateDownloadObservation(model.name)
    downloadStartGate.release(model.name)
    downloadRepository.cancelDownloadModel(model)
    deleteModel(model = model)
    processPendingDownloads()
  }

  fun retryDownloadModel(model: Model): Boolean {
    if (!downloadInspectionAvailable) {
      _toastErrorChannel.trySend(context.getString(R.string.model_download_inspection_failed))
      return false
    }
    if (model.name in recoveringModelNames) {
      _toastErrorChannel.trySend(context.getString(R.string.model_download_recovering))
      return false
    }
    if (model.name in removingModelNames) return false
    if (!downloadStartGate.tryAcquire(model.name)) {
      Log.d(TAG, "Ignoring duplicate retry for '${model.name}'")
      return false
    }
    if (isDiscoveredSource(model.sourceRepositoryId) && _uiState.value.models.none { it.name == model.name }) {
      downloadStartGate.release(model.name)
      return false
    }
    setDownloadStatus(
      curModel = model,
      status = ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS),
    )
    try {
      enqueueModelDownload(model)
      return true
    } catch (failure: Throwable) {
      downloadStartGate.release(model.name)
      throw failure
    }
  }

  fun cancelModelDownloadByName(modelName: String) {
    val model = getAllModels().find { it.name == modelName } ?: return
    cancelDownloadModel(model)
  }

  fun deleteModel(model: Model) {
    if (model.imported && model.name in _renamingFavoriteIds.value) return
    downloadRepository.cancelDownloadModel(model)
    serverStateRepository.clearErrorIfModel(model.name)
    if (model.imported) {
      allowlistLoadJob?.cancel()
      allowlistLoadGenerationGate.beginLoad()
      _uiState.update { it.copy(loadingModelAllowlist = false) }
      viewModelScope.launch(ioDispatcher) {
        try {
          importedModelCoordinator.deleteImportedModelRecord(model.storageFileName)
          removeFavorite(model.name)
          markDownloadStatusChanged(model.name)
          _uiState.update { current -> current.copy(
            models = current.models.filterNot { it.name == model.name },
            modelDownloadStatus = current.modelDownloadStatus - model.name,
          ) }
          notifyStorageChanged()
          loadModelAllowlist()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Could not remove imported model", e)
          _toastErrorChannel.trySend(context.getString(R.string.imported_model_recovery_error))
          loadModelAllowlist()
        }
      }
      return
    }

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

    deleteDirFromExternalFilesDir(model.normalizedName)

    val action = if (model.imported) "Imported model deleted" else "Model deleted"
    if (preferencesRepository.isVerboseDebugEnabled()) {
      RequestLogStore.addEvent(
        "$action: ${model.name} (${model.sizeInBytes.humanReadableSize()})",
        level = LogLevel.DEBUG,
        modelName = model.name,
        category = EventCategory.MODEL,
      )
    }

    markDownloadStatusChanged(model.name)
    _uiState.update { current ->
      val statusMap = current.modelDownloadStatus.toMutableMap()
      val models = if (model.imported) {
        statusMap.remove(model.name)
        current.models.filter { it.name != model.name }
      } else {
        statusMap[model.name] = ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)
        if (current.allReposDisabled && !isDiscoveredSource(model.sourceRepositoryId)) {
          current.models.filter { it.name != model.name }
        } else {
          current.models
        }
      }
      current.copy(modelDownloadStatus = statusMap, models = models)
    }
  }

  fun deleteModelAndRefreshStorage(model: Model) {
    invalidateDownloadObservation(model.name)
    downloadStartGate.release(model.name)
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
    if (curModel.name in removingModelNames) return
    if (isDiscoveredSource(curModel.sourceRepositoryId) && _uiState.value.models.none { it.name == curModel.name }) return
    markDownloadStatusChanged(curModel.name)
    if (
      status.status == ModelDownloadStatusType.SUCCEEDED ||
        status.status == ModelDownloadStatusType.FAILED ||
        status.status == ModelDownloadStatusType.NOT_DOWNLOADED
    ) {
      downloadStartGate.release(curModel.name)
    }
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
    if (status.status == ModelDownloadStatusType.SUCCEEDED || status.status == ModelDownloadStatusType.FAILED ||
      status.status == ModelDownloadStatusType.NOT_DOWNLOADED) processPendingDownloads()
  }

  fun getModelUrlResponse(model: Model, accessToken: String? = null): ModelUrlResult {
    val result = probeModelUrl(model.url, accessToken)
    if (result is ModelUrlResult.Error) {
      Log.e(TAG, result.message)
    }
    return result
  }

  private val _localModelImport = MutableStateFlow<LocalModelImportState>(LocalModelImportState.Idle)
  internal val localModelImport = _localModelImport.asStateFlow()

  internal fun importLocalModel(uri: Uri, info: ImportedModel) {
    if (!_localModelImport.compareAndSet(LocalModelImportState.Idle, LocalModelImportState.Copying(info))) return
    viewModelScope.launch(mainDispatcher) {
      try {
        val imported = withContext(ioDispatcher) {
          importedModelCoordinator.importFile(uri, info) { progress ->
            _localModelImport.value = LocalModelImportState.Copying(info, progress)
          }
        }
        // A load started during the file copy may still contain the old registry snapshot.
        allowlistLoadJob?.cancel()
        allowlistLoadGenerationGate.beginLoad()
        addImportedLlmModel(imported)
        loadModelAllowlist()
        _localModelImport.value = LocalModelImportState.Complete
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to import local model", e)
        val message = when (e) {
          is UnregisteredImportFileException -> R.string.error_import_existing_file
          is ImportedModelRecoveryException -> R.string.imported_model_recovery_error
          else -> R.string.error_import_failed
        }
        _localModelImport.value = LocalModelImportState.Failed(context.getString(message))
        loadModelAllowlist()
      }
    }
  }

  internal fun dismissLocalModelImport() {
    _localModelImport.update { if (it is LocalModelImportState.Copying) it else LocalModelImportState.Idle }
  }

  private fun addImportedLlmModel(info: ImportedModel) {
    Log.d(TAG, "adding imported llm model: $info")
    val model = importedModelCoordinator.buildAndRestoreImportedModel(info)
    if (preferencesRepository.isVerboseDebugEnabled()) {
      RequestLogStore.addEvent(
        "Model imported: ${info.fileName} (${info.fileSize.humanReadableSize()})",
        level = LogLevel.DEBUG,
        modelName = model.name,
        category = EventCategory.MODEL,
      )
    }

    val now = System.currentTimeMillis()
    _uiState.update { current ->
      val updatedModels = current.models
        .filter { !(it.imported && it.storageFileName == info.fileName) }
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

  }

  fun updateImportedModelDefaults(expected: ImportedModel, updatedInfo: ImportedModel) {
    viewModelScope.launch(ioDispatcher) {
      try {
        val updatedModel = importedModelCoordinator.updateDefaults(expected, updatedInfo)
        _uiState.update { current ->
          val updatedModels = current.models.map { m ->
            if (m.imported && m.storageFileName == updatedInfo.fileName) updatedModel else m
          }
          current.copy(models = updatedModels)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Could not update imported model settings", e)
        _toastErrorChannel.trySend(context.getString(R.string.error_model_settings_update_failed))
        loadModelAllowlist()
      }
    }
  }

  fun renameImportedModel(
    oldFileName: String,
    newFileName: String,
    displayName: String,
    onResult: (Boolean) -> Unit,
  ) {
    val oldId = ModelFactory.importedModelId(oldFileName)
    val accepted = synchronized(favoritesLock) {
      if (oldId in _renamingFavoriteIds.value) false else {
        _renamingFavoriteIds.value = _renamingFavoriteIds.value + oldId
        true
      }
    }
    if (!accepted) { onResult(false); return }
    // Prevent a load started before the rename from publishing the old file name.
    allowlistLoadJob?.cancel()
    allowlistLoadGenerationGate.beginLoad()
    _uiState.update { it.copy(loadingModelAllowlist = false) }
    viewModelScope.launch(ioDispatcher) {
      try {
        var originalRecord: ImportedModel? = null
        val updatedModel = try {
          originalRecord = protoDataStoreRepository.readImportedModels().firstOrNull { it.fileName == oldFileName }
          if (oldFileName == newFileName) {
            importedModelCoordinator.renameOnlyDisplayName(oldFileName, displayName)
          } else {
            importedModelCoordinator.renameFileAndRecord(oldFileName, newFileName, displayName)
          }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          Log.e(TAG, "Imported model rename did not complete; checking recovery", e)
          // The file may already have moved, so do not leave the old card actionable.
          _uiState.update { state -> state.copy(models = state.models.filterNot { it.name == oldId }) }
          try {
            val records = protoDataStoreRepository.readImportedModels()
            refreshFavoriteModels()
            loadModelAllowlist()
            val expected = originalRecord?.toBuilder()?.setFileName(newFileName)?.setDisplayName(displayName)?.build()
            records.firstOrNull { it == expected }?.let(importedModelCoordinator::buildAndRestoreImportedModel)
          } catch (recoveryError: CancellationException) {
            throw recoveryError
          } catch (recoveryError: Exception) {
            Log.e(TAG, "Imported model rename recovery failed", recoveryError)
            _uiState.update { state -> state.copy(
              models = state.models.filterNot { it.name == oldId },
              loadingModelAllowlist = false,
              loadingModelAllowlistError = context.getString(R.string.imported_model_recovery_error),
            ) }
            null
          }
        }
        if (updatedModel == null) {
          withContext(mainDispatcher) { onResult(false) }
          return@launch
        }

        if (oldFileName != newFileName) refreshFavoriteModels()
        _uiState.update { current ->
          val oldModel = current.models.firstOrNull { it.imported && it.storageFileName == oldFileName }
          val oldModelName = oldModel?.name
          val updatedModels = current.models.map { model ->
            if (model.imported && model.storageFileName == oldFileName) updatedModel else model
          }
          val downloadStatus = current.modelDownloadStatus.toMutableMap()
          val initializationStatus = current.modelInitializationStatus.toMutableMap()
          if (oldModelName != null) {
            downloadStatus.remove(oldModelName)?.let { downloadStatus[updatedModel.name] = it }
            initializationStatus.remove(oldModelName)?.let { initializationStatus[updatedModel.name] = it }
          }
          current.copy(
            models = updatedModels,
            modelDownloadStatus = downloadStatus,
            modelInitializationStatus = initializationStatus,
          )
        }
        loadModelAllowlist()
        withContext(mainDispatcher) { onResult(true) }
      } finally {
        synchronized(favoritesLock) { _renamingFavoriteIds.value = _renamingFavoriteIds.value - oldId }
      }
    }
  }

  private fun processPendingDownloads() {
    val statuses = uiState.value.modelDownloadStatus
    if (!downloadInspectionAvailable || recoveringDownloads || statuses.values.any {
        it.status == ModelDownloadStatusType.IN_PROGRESS || it.status == ModelDownloadStatusType.UNZIPPING
      }) return
    val pending = uiState.value.models.filter {
      statuses[it.name]?.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED && it.name !in removingModelNames
    }
    if (pending.isEmpty()) return
    recoveringDownloads = true
    recoveringModelNames = pending.mapTo(mutableSetOf()) { it.name }
    pending.forEach { invalidateDownloadObservation(it.name) }
    downloadRepository.cancelDownloads(pending.map { it.name }) { cancelled ->
      viewModelScope.launch(mainDispatcher) {
        try {
          if (!cancelled) {
            _toastErrorChannel.trySend(context.getString(R.string.model_download_recovery_failed))
            pending.forEach { model ->
              val previous = uiState.value.modelDownloadStatus[model.name]
              if (previous?.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED) {
                setDownloadStatus(model, previous.copy(status = ModelDownloadStatusType.FAILED))
              }
            }
            return@launch
          }
          val configuredToken = configuredHfTokenOrNull(preferencesRepository.getHfToken())
          for (model in pending) {
            val partialStatus = uiState.value.modelDownloadStatus[model.name]
            if (uiState.value.models.none { it.name == model.name } ||
              partialStatus?.status != ModelDownloadStatusType.PARTIALLY_DOWNLOADED ||
              model.name in removingModelNames || !downloadStartGate.tryAcquire(model.name)) continue
            model.accessToken = if (com.ollitert.llm.server.data.allowlist.modelScopeSiteForUrl(model.url) != null) null else configuredToken
            setDownloadStatus(model, ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS))
            try {
              enqueueModelDownload(model)
            } catch (e: Exception) {
              downloadStartGate.release(model.name)
              Log.e(TAG, "Could not resume model download", e)
              setDownloadStatus(model, partialStatus.copy(status = ModelDownloadStatusType.FAILED))
            }
          }
        } finally {
          recoveringDownloads = false
          recoveringModelNames = emptySet()
          if (cancelled && uiState.value.modelDownloadStatus.any { (name, status) ->
              status.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED && pending.none { it.name == name }
            }) processPendingDownloads()
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
    discoveredModelsRepository = discoveredModelsRepository,
  )

  fun isModelSupportedOnDevice(allowedModel: AllowedModel): Boolean {
    return allowlistLoadCoordinator.isModelSupportedOnDevice(allowedModel)
  }

  /**
   * Active allowlist load. Job ownership is changed only on [mainDispatcher],
   * while the generation gate makes the freshness check and publication one
   * indivisible operation.
   */
  private var allowlistLoadJob: Job? = null
  private val allowlistLoadGenerationGate = AllowlistLoadGenerationGate()

  private data class PreparedAllowlistLoad(
    val result: AllowlistLoadResult,
    val loadedUiState: ModelManagerUiState?,
    val toastError: String?,
    val downloadRevisionsAtStart: Map<String, Long>,
    val unfinishedDownloads: Map<String, UUID>?,
  )

  fun loadModelAllowlist(isManualRetry: Boolean = false) {
    viewModelScope.launch(mainDispatcher) {
      startModelAllowlistLoad(isManualRetry)
    }
  }

  private fun startModelAllowlistLoad(isManualRetry: Boolean) {
    _uiState.update {
      it.copy(loadingModelAllowlist = true, loadingModelAllowlistError = "", allReposDisabled = false)
    }

    allowlistLoadJob?.cancel()
    val generation = allowlistLoadGenerationGate.beginLoad()
    allowlistLoadJob = viewModelScope.launch(mainDispatcher) {
      val downloadRevisionsAtStart = downloadStatusRevisionSnapshot()
      val preparedLoad = try {
        withContext(ioDispatcher) {
          var toastError: String? = null
          val result = allowlistLoadCoordinator.loadAllowlist(
            isManualRetry = isManualRetry,
            onToastError = { message -> toastError = message },
          )
          val unfinished = try {
            downloadRepository.getUnfinishedModelDownloads()
          } catch (e: Exception) {
            Log.e(TAG, "Could not inspect pending downloads", e)
            toastError = context.getString(R.string.model_download_inspection_failed)
            null
          }
          // Read files after the WorkManager snapshot: an unobserved worker may
          // finish between the two reads, and its final file must win over a stale partial.
          val loadedUiState = prepareLoadedUiState(result)
          PreparedAllowlistLoad(
            result = result,
            loadedUiState = loadedUiState,
            toastError = toastError,
            downloadRevisionsAtStart = downloadRevisionsAtStart,
            unfinishedDownloads = unfinished,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Could not load imported models", e)
        allowlistLoadGenerationGate.publishIfCurrent(generation) {
          val message = if (e is ImportedModelRecoveryException) R.string.imported_model_recovery_error
            else R.string.models_failed_to_load
          _uiState.update { it.copy(loadingModelAllowlist = false,
            loadingModelAllowlistError = context.getString(message)) }
        }
        return@launch
      }

      val wasPublished = allowlistLoadGenerationGate.publishIfCurrent(generation) {
        publishAllowlistLoad(preparedLoad)
      }
      if (!wasPublished) {
        Log.d(TAG, "Allowlist load superseded (gen $generation) — discarding result")
      }
    }
  }

  private suspend fun prepareLoadedUiState(result: AllowlistLoadResult): ModelManagerUiState? {
    if (result.models.isEmpty() && result.errorMessage.isNotEmpty() && !result.isRawAllowlist) {
      return null
    }

    processModels(result.models)
    val loadedState = createUiState(result.models)
    return if (result.isRawAllowlist) {
      loadedState.copy(loadingModelAllowlist = false, loadingModelAllowlistError = result.errorMessage)
    } else {
      loadedState.copy(
        loadingModelAllowlist = false,
        allReposDisabled = result.allReposDisabled,
        loadingModelAllowlistError = result.errorMessage,
        emptyReason = result.emptyReason,
        requiredVersion = result.requiredVersion,
        droppedByVersionFilter = result.droppedByVersionFilter,
        totalBeforeFilters = result.totalBeforeFilters,
      )
    }
  }

  private fun publishAllowlistLoad(preparedLoad: PreparedAllowlistLoad) {
    val result = preparedLoad.result
    preparedLoad.toastError?.let(_toastErrorChannel::trySend)
    downloadInspectionAvailable = preparedLoad.unfinishedDownloads != null

    when {
      result.models.isEmpty() && result.errorMessage.isNotEmpty() && !result.isRawAllowlist -> {
        _uiState.update {
          it.copy(
            loadingModelAllowlist = false,
            loadingModelAllowlistError = result.errorMessage,
          )
        }
      }

      else -> {
        val loaded = checkNotNull(preparedLoad.loadedUiState)
        val inspectedActiveNames = preparedLoad.unfinishedDownloads?.let { workers ->
          workers.keys + downloadObservers.filterValues { it.isActive }.keys
        }
        _uiState.value = loaded.copy(
          loadingModelAllowlistError = if (downloadInspectionAvailable) loaded.loadingModelAllowlistError
            else context.getString(R.string.model_download_inspection_failed),
          modelDownloadStatus = preserveActiveDownloadStatuses(
            loaded.modelDownloadStatus, _uiState.value.modelDownloadStatus,
            preparedLoad.downloadRevisionsAtStart, downloadStatusRevisionSnapshot(),
            inspectedActiveNames,
          ),
        )
        val currentRevisions = downloadStatusRevisionSnapshot()
        for ((name, workerId) in preparedLoad.unfinishedDownloads.orEmpty()) {
          if ((currentRevisions[name] ?: 0L) != (preparedLoad.downloadRevisionsAtStart[name] ?: 0L)) continue
          val model = _uiState.value.models.firstOrNull { it.name == name } ?: continue
          if (name in removingModelNames || !downloadStartGate.tryAcquire(name)) continue
          val generation = invalidateDownloadObservation(name)
          setDownloadStatus(model, ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS))
          observeDownload(model, workerId, generation)
        }
        notifyStorageChanged()
        if (preparedLoad.unfinishedDownloads != null) processPendingDownloads()
      }
    }
  }

  fun clearLoadModelAllowlistError() {
    viewModelScope.launch(mainDispatcher) {
      _uiState.update {
        it.copy(loadingModelAllowlistError = "")
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

  private suspend fun createUiState(models: List<Model>): ModelManagerUiState {
    val modelDownloadStatus: MutableMap<String, ModelDownloadStatus> = mutableMapOf()
    val modelInstances: MutableMap<String, ModelInitializationStatus> = mutableMapOf()
    for (model in models) {
      modelDownloadStatus[model.name] = getModelDownloadStatus(model = model)
      modelInstances[model.name] =
        ModelInitializationStatus(status = ModelInitializationStatusType.NOT_INITIALIZED)
    }

    val importedModels = mutableListOf<Model>()
    val importedRecords = protoDataStoreRepository.readExistingImportedModels()
    refreshFavoriteModels()
    for (importedModel in importedRecords) {
      Log.d(TAG, "stored imported model: $importedModel")
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
      models = models + importedModels,
      modelDownloadStatus = modelDownloadStatus,
      modelInitializationStatus = modelInstances,
    )
  }

  private fun getModelDownloadStatus(model: Model) = modelStorageRepository.getModelDownloadStatus(model)

  private fun deleteFileFromExternalFilesDir(fileName: String) = modelStorageRepository.deleteFileFromExternalFilesDir(fileName)
  private fun deleteDirFromExternalFilesDir(dir: String) = modelStorageRepository.deleteDirFromExternalFilesDir(dir)
}
