/*
 * Copyright 2025 Google LLC
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
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.AppLifecycleProvider
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.getJsonResponse
import com.ollitert.llm.server.data.Accelerator
import com.ollitert.llm.server.data.Config
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.DownloadRepository
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.EMPTY_MODEL
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.SOC
import com.ollitert.llm.server.data.TMP_FILE_EXT
import com.ollitert.llm.server.data.Task
import com.ollitert.llm.server.data.createBuiltInTasks
import com.ollitert.llm.server.proto.AccessTokenData
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LlmHttpModelFactory
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.common.humanReadableSize
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "OlliteRTModelManagerVM"
private const val TEXT_INPUT_HISTORY_MAX_SIZE = 50
private const val MODEL_ALLOWLIST_FILENAME = "model_allowlist.json"
private const val MODEL_ALLOWLIST_TEST_FILENAME = "model_allowlist_test.json"
private const val ALLOWLIST_BASE_URL = GitHubConfig.ALLOWLIST_BASE_URL

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

/** Where the model allowlist was loaded from — used to show an info banner when offline. */
enum class AllowlistSource {
  /** Successfully fetched from GitHub (fresh list). */
  NETWORK,
  /** Network failed, loaded from a previously cached file on disk. */
  DISK_CACHE,
  /** Network failed and no cache, loaded from the APK's bundled asset. */
  BUNDLED_ASSET,
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

data class ModelManagerUiState(
  /** A list of tasks available in the application. */
  val tasks: List<Task>,

  /** Tasks grouped by category. */
  val tasksByCategory: Map<String, List<Task>>,

  /** A map that tracks the download status of each model, indexed by model name. */
  val modelDownloadStatus: Map<String, ModelDownloadStatus>,

  /** A map that tracks the initialization status of each model, indexed by model name. */
  val modelInitializationStatus: Map<String, ModelInitializationStatus>,

  /** Whether the app is loading and processing the model allowlist. */
  val loadingModelAllowlist: Boolean = true,

  /** The error message when loading the model allowlist. */
  val loadingModelAllowlistError: String = "",

  /** Where the allowlist was loaded from (network, disk cache, or bundled asset). */
  val allowlistSource: AllowlistSource? = null,

  /** The currently selected model. */
  val selectedModel: Model = EMPTY_MODEL,

  /** The history of text inputs entered by the user. */
  val textInputHistory: List<String> = listOf(),
  val configValuesUpdateTrigger: Long = 0L,
  // Updated when model is imported of an imported model is deleted.
  val modelImportingUpdateTrigger: Long = 0L,
  // Bumped when storage changes (download complete, model deleted).
  val storageUpdateTrigger: Long = 0L,
) {
  fun isModelInitialized(model: Model): Boolean {
    return modelInitializationStatus[model.name]?.status ==
      ModelInitializationStatusType.INITIALIZED
  }

  fun isModelInitializing(model: Model): Boolean {
    return modelInitializationStatus[model.name]?.status ==
      ModelInitializationStatusType.INITIALIZING
  }
}

/**
 * ViewModel responsible for managing models, their download status, and initialization.
 *
 * This ViewModel handles model-related operations such as downloading, deleting, initializing, and
 * cleaning up models. It also manages the UI state for model management, including the list of
 * tasks, models, download statuses, and initialization statuses.
 */
@HiltViewModel
open class ModelManagerViewModel
@Inject
constructor(
  private val downloadRepository: DownloadRepository,
  val dataStoreRepository: DataStoreRepository,
  private val lifecycleProvider: AppLifecycleProvider,
  @param:ApplicationContext private val context: Context,
) : ViewModel() {
  private val externalFilesDir = context.getExternalFilesDir(null)
  protected val _uiState = MutableStateFlow(createEmptyUiState())
  val uiState = _uiState.asStateFlow()

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

  fun getModelByName(name: String): Model? {
    for (task in uiState.value.tasks) {
      for (model in task.models) {
        if (model.name == name) {
          return model
        }
      }
    }
    return null
  }

  fun getAllModels(): List<Model> {
    val allModels = mutableSetOf<Model>()
    for (task in uiState.value.tasks) {
      for (model in task.models) {
        allModels.add(model)
      }
    }
    return allModels.toList().sortedBy { it.displayName.ifEmpty { it.name } }
  }

  fun getAllDownloadedModels(): List<Model> {
    return getAllModels().filter {
      uiState.value.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED &&
        it.isLlm
    }
  }

  fun processTasks() {
    for (task in uiState.value.tasks) {
      for (model in task.models) {
        model.preProcess()
        // Restore persisted inference config (temperature, max tokens, etc.) so settings
        // survive app restarts. Overlays saved values on top of model defaults.
        LlmHttpModelFactory.restoreInferenceConfig(context, model)
      }
      // Move the model that is best for this task to the front.
      val bestModel = task.models.find { it.bestForTaskIds.contains(task.id) }
      if (bestModel != null) {
        task.models.remove(bestModel)
        task.models.add(0, bestModel)
      }
    }
  }

  fun updateConfigValuesUpdateTrigger() {
    _uiState.update { _uiState.value.copy(configValuesUpdateTrigger = System.currentTimeMillis()) }
  }

  fun notifyStorageChanged() {
    _uiState.update { _uiState.value.copy(storageUpdateTrigger = System.currentTimeMillis()) }
  }

  fun selectModel(model: Model) {
    if (_uiState.value.selectedModel.name != model.name) {
      _uiState.update { _uiState.value.copy(selectedModel = model) }
    }
  }

  fun downloadModel(task: Task?, model: Model) {
    // Update status.
    setDownloadStatus(
      curModel = model,
      status = ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS),
    )

    // Delete the model files first.
    deleteModel(model = model)

    // Start to send download request.
    downloadRepository.downloadModel(
      task = task,
      model = model,
      onStatusUpdated = this::setDownloadStatus,
    )
  }

  fun cancelDownloadModel(model: Model) {
    downloadRepository.cancelDownloadModel(model)
    deleteModel(model = model)
  }

  fun deleteModel(model: Model) {
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

    // Update model download status to NotDownloaded.
    val curModelDownloadStatus = uiState.value.modelDownloadStatus.toMutableMap()
    curModelDownloadStatus[model.name] =
      ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)

    // Delete model from the list if model is imported as a local model.
    if (model.imported) {
      for (curTask in uiState.value.tasks) {
        val index = curTask.models.indexOf(model)
        if (index >= 0) {
          curTask.models.removeAt(index)
        }
        curTask.updateTrigger.value = System.currentTimeMillis()
      }
      curModelDownloadStatus.remove(model.name)

      // Update data store.
      val importedModels = dataStoreRepository.readImportedModels().toMutableList()
      val importedModelIndex = importedModels.indexOfFirst { it.fileName == model.name }
      if (importedModelIndex >= 0) {
        importedModels.removeAt(importedModelIndex)
      }
      dataStoreRepository.saveImportedModels(importedModels = importedModels)
    }
    val newUiState =
      uiState.value.copy(
        modelDownloadStatus = curModelDownloadStatus,
        tasks = uiState.value.tasks.toList(),
        modelImportingUpdateTrigger = System.currentTimeMillis(),
      )
    _uiState.update { newUiState }
  }

  /** Delete model and notify storage change (for user-initiated deletions). */
  fun deleteModelAndRefreshStorage(model: Model) {
    deleteModel(model = model)
    notifyStorageChanged()
  }

  fun initializeModel(
    context: Context,
    task: Task,
    model: Model,
    force: Boolean = false,
    onDone: () -> Unit = {},
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      // Skip if initialized already.
      if (
        !force &&
          uiState.value.modelInitializationStatus[model.name]?.status ==
            ModelInitializationStatusType.INITIALIZED
      ) {
        Log.d(TAG, "Model '${model.name}' has been initialized. Skipping.")
        return@launch
      }

      // Skip if initialization is in progress.
      if (model.initializing) {
        model.cleanUpAfterInit = false
        Log.d(TAG, "Model '${model.name}' is being initialized. Skipping.")
        return@launch
      }

      // Clean up.
      cleanupModel(context = context, task = task, model = model)

      // Start initialization.
      Log.d(TAG, "Initializing model '${model.name}'...")
      model.initializing = true
      updateModelInitializationStatus(
        model = model,
        status = ModelInitializationStatusType.INITIALIZING,
      )

      val onDoneFn: (error: String) -> Unit = { error ->
        model.initializing = false
        if (model.instance != null) {
          Log.d(TAG, "Model '${model.name}' initialized successfully")
          updateModelInitializationStatus(
            model = model,
            status = ModelInitializationStatusType.INITIALIZED,
          )
          if (model.cleanUpAfterInit) {
            Log.d(TAG, "Model '${model.name}' needs cleaning up after init.")
            cleanupModel(context = context, task = task, model = model)
          }
          onDone()
        } else if (error.isNotEmpty()) {
          Log.d(TAG, "Model '${model.name}' failed to initialize")
          updateModelInitializationStatus(
            model = model,
            status = ModelInitializationStatusType.ERROR,
            error = error,
          )
        }
      }

      Log.d(TAG, "Benchmark requested for model '${model.name}'")
      onDoneFn("")
    }
  }

  fun cleanupModel(
    context: Context,
    task: Task,
    model: Model,
    instanceToCleanUp: Any? = model.instance,
    onDone: () -> Unit = {},
  ) {
    if (instanceToCleanUp != null && instanceToCleanUp !== model.instance) {
      Log.d(TAG, "Stale cleanup request for ${model.name}. Aborting.")
      onDone()
      return
    }

    if (model.instance != null) {
      model.cleanUpAfterInit = false
      Log.d(TAG, "Cleaning up model '${model.name}'...")
      val onDoneFn: () -> Unit = {
        model.instance = null
        model.initializing = false
        updateModelInitializationStatus(
          model = model,
          status = ModelInitializationStatusType.NOT_INITIALIZED,
        )
        Log.d(TAG, "Clean up model '${model.name}' done")
        onDone()
      }
      onDoneFn()
    } else {
      // When model is being initialized and we are trying to clean it up at same time, we mark it
      // to clean up and it will be cleaned up after initialization is done.
      if (model.initializing) {
        Log.d(
          TAG,
          "Model '${model.name}' is still initializing.. Will clean up after it is done initializing",
        )
        model.cleanUpAfterInit = true
      }
    }
  }

  fun setDownloadStatus(curModel: Model, status: ModelDownloadStatus) {
    // Update model download progress.
    val curModelDownloadStatus = uiState.value.modelDownloadStatus.toMutableMap()
    curModelDownloadStatus[curModel.name] = status
    var newUiState = uiState.value.copy(modelDownloadStatus = curModelDownloadStatus)

    // Delete downloaded file if status is failed or not_downloaded.
    if (
      status.status == ModelDownloadStatusType.FAILED ||
        status.status == ModelDownloadStatusType.NOT_DOWNLOADED
    ) {
      deleteFileFromExternalFilesDir(curModel.downloadFileName)
    }

    // Trigger storage refresh on download completion.
    if (status.status == ModelDownloadStatusType.SUCCEEDED) {
      newUiState = newUiState.copy(storageUpdateTrigger = System.currentTimeMillis())
    }

    _uiState.update { newUiState }
  }

  fun addTextInputHistory(text: String) {
    if (uiState.value.textInputHistory.indexOf(text) < 0) {
      val newHistory = uiState.value.textInputHistory.toMutableList()
      newHistory.add(0, text)
      if (newHistory.size > TEXT_INPUT_HISTORY_MAX_SIZE) {
        newHistory.removeAt(newHistory.size - 1)
      }
      _uiState.update { _uiState.value.copy(textInputHistory = newHistory) }
      dataStoreRepository.saveTextInputHistory(_uiState.value.textInputHistory)
    } else {
      promoteTextInputHistoryItem(text)
    }
  }

  fun promoteTextInputHistoryItem(text: String) {
    val index = uiState.value.textInputHistory.indexOf(text)
    if (index >= 0) {
      val newHistory = uiState.value.textInputHistory.toMutableList()
      newHistory.removeAt(index)
      newHistory.add(0, text)
      _uiState.update { _uiState.value.copy(textInputHistory = newHistory) }
      dataStoreRepository.saveTextInputHistory(_uiState.value.textInputHistory)
    }
  }

  fun clearTextInputHistory() {
    _uiState.update { _uiState.value.copy(textInputHistory = mutableListOf()) }
    dataStoreRepository.saveTextInputHistory(_uiState.value.textInputHistory)
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
    val model = LlmHttpModelFactory.buildImportedModel(info)
    LlmHttpModelFactory.restoreInferenceConfig(context, model)

    for (task in uiState.value.tasks) {
      // Remove duplicated imported model if existed.
      val modelIndex = task.models.indexOfFirst { info.fileName == it.name && it.imported }
      if (modelIndex >= 0) {
        Log.d(TAG, "duplicated imported model found in task. Removing it first")
        task.models.removeAt(modelIndex)
      }
      task.models.add(model)
      task.updateTrigger.value = System.currentTimeMillis()
    }

    // Add initial status and states.
    val modelDownloadStatus = uiState.value.modelDownloadStatus.toMutableMap()
    val modelInstances = uiState.value.modelInitializationStatus.toMutableMap()
    modelDownloadStatus[model.name] =
      ModelDownloadStatus(
        status = ModelDownloadStatusType.SUCCEEDED,
        receivedBytes = info.fileSize,
        totalBytes = info.fileSize,
      )
    modelInstances[model.name] =
      ModelInitializationStatus(status = ModelInitializationStatusType.NOT_INITIALIZED)

    // Update ui state — bump both import trigger and storage trigger so the
    // storage bar reflects the space consumed by the newly imported model.
    val now = System.currentTimeMillis()
    _uiState.update {
      uiState.value.copy(
        tasks = uiState.value.tasks.toList(),
        modelDownloadStatus = modelDownloadStatus,
        modelInitializationStatus = modelInstances,
        modelImportingUpdateTrigger = now,
        storageUpdateTrigger = now,
      )
    }

    // Add to data store.
    val importedModels = dataStoreRepository.readImportedModels().toMutableList()
    val importedModelIndex = importedModels.indexOfFirst { info.fileName == it.fileName }
    if (importedModelIndex >= 0) {
      Log.d(TAG, "duplicated imported model found in data store. Removing it first")
      importedModels.removeAt(importedModelIndex)
    }
    importedModels.add(info)
    dataStoreRepository.saveImportedModels(importedModels = importedModels)

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

    // Persist updated proto entry
    dataStoreRepository.updateImportedModel(updatedInfo.fileName, updatedInfo)

    // Clear inference config overrides so saved values don't conflict with new defaults
    LlmHttpPrefs.clearInferenceConfig(context, updatedInfo.fileName)

    // Rebuild the Model object from updated proto
    val updatedModel = LlmHttpModelFactory.buildImportedModel(updatedInfo)

    // Replace the model in all task lists
    for (task in uiState.value.tasks) {
      val modelIndex = task.models.indexOfFirst { it.name == updatedInfo.fileName && it.imported }
      if (modelIndex >= 0) {
        task.models[modelIndex] = updatedModel
        task.updateTrigger.value = System.currentTimeMillis()
      }
    }

    // Bump modelImportingUpdateTrigger to force StateFlow emission. Without this,
    // _uiState.update produces a structurally equal value (same Task references with
    // in-place mutated MutableList) and MutableStateFlow drops the update.
    _uiState.update {
      it.copy(
        tasks = uiState.value.tasks.toList(),
        modelImportingUpdateTrigger = System.currentTimeMillis(),
      )
    }

    RequestLogStore.addEvent(
      "Imported model defaults updated: ${updatedInfo.fileName}",
      level = LogLevel.DEBUG,
      modelName = updatedInfo.fileName,
      category = EventCategory.MODEL,
    )
  }

  // Token management — delegated to HuggingFaceTokenManager
  fun getTokenStatusAndData() = tokenManager.getTokenStatusAndData()
  fun getAuthorizationRequest() = tokenManager.getAuthorizationRequest()
  fun handleAuthResult(result: ActivityResult, onTokenRequested: (TokenRequestResult) -> Unit) =
    tokenManager.handleAuthResult(result, onTokenRequested)
  fun saveAccessToken(accessToken: String, refreshToken: String, expiresAt: Long) =
    tokenManager.saveAccessToken(accessToken, refreshToken, expiresAt)

  private fun processPendingDownloads() {
    // Cancel all pending downloads for the retrieved models.
    downloadRepository.cancelAll {
      Log.d(TAG, "All workers are cancelled.")

      viewModelScope.launch(Dispatchers.Main) {
        val checkedModelNames = mutableSetOf<String>()
        val tokenStatusAndData = getTokenStatusAndData()
        for (task in uiState.value.tasks) {
          for (model in task.models) {
            if (checkedModelNames.contains(model.name)) {
              continue
            }

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
                task = task,
                model = model,
                onStatusUpdated = this@ModelManagerViewModel::setDownloadStatus,
              )
            }

            checkedModelNames.add(model.name)
          }
        }
      }
    }
  }

  fun loadModelAllowlist(isManualRetry: Boolean = false) {
    _uiState.update {
      uiState.value.copy(loadingModelAllowlist = true, loadingModelAllowlistError = "")
    }

    viewModelScope.launch(Dispatchers.IO) {
      // Clean up stale .tmp files from interrupted model imports on startup
      fileManager.cleanupStaleImportTmpFiles()
      try {
        // Load model allowlist json.
        var modelAllowlist: ModelAllowlist? = null
        var allowlistSource: AllowlistSource = AllowlistSource.NETWORK

        // Try to read the test allowlist first.
        Log.d(TAG, "Loading test model allowlist.")
        modelAllowlist = readModelAllowlistFromDisk(fileName = MODEL_ALLOWLIST_TEST_FILENAME)

        // Local test only.
        if (TEST_MODEL_ALLOW_LIST.isNotEmpty()) {
          Log.d(TAG, "Loading local model allowlist for testing.")
          val gson = Gson()
          try {
            modelAllowlist = gson.fromJson(TEST_MODEL_ALLOW_LIST, ModelAllowlist::class.java)
          } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse local test json", e)
          }
        }

        if (modelAllowlist == null) {
          // Load from github.
          // Strip flavor suffixes (-dev, -beta) so the version maps to the remote
          // allowlist file (e.g. "0.8.0-beta" → "0_8_0" → "v1/0_8_0.json").
          // When beta needs different models, it will have a bumped version number
          // (e.g. 0.9.0) with its own allowlist — the suffix is not part of the
          // allowlist versioning scheme.
          //
          // IMPORTANT: Every new app version release MUST have a corresponding
          // allowlist file in model_allowlists/v1/ (e.g. v1/0_9_0.json for
          // version 0.9.0). Without it, the app falls back to the disk cache or
          // the bundled asset, which may be outdated. The file can be a copy of
          // the previous version's allowlist if models haven't changed.
          // See: model_allowlists/v1/ in the repo root and GitHubConfig.ALLOWLIST_BASE_URL.
          var version = BuildConfig.VERSION_NAME
            .replace(Regex("-(dev|beta)$"), "")
            .replace(".", "_")
          val url = getAllowlistUrl(version)
          Log.d(TAG, "Loading model allowlist from internet. Url: $url")
          val data = getJsonResponse<ModelAllowlist>(url = url)
          modelAllowlist = data?.jsonObj

          if (modelAllowlist != null) {
            // Network fetch succeeded — save to disk cache for future offline use.
            allowlistSource = AllowlistSource.NETWORK
            Log.d(TAG, "Done: loading model allowlist from internet")
            saveModelAllowlistToDisk(modelAllowlistContent = data.textContent)
          } else {
            // Network failed — try disk cache. If this was a manual retry, notify the user.
            Log.w(TAG, "Failed to load model allowlist from internet. Trying to load it from disk")
            if (isManualRetry) _toastErrorChannel.trySend("Could not reach model server")
            modelAllowlist = readModelAllowlistFromDisk()

            if (modelAllowlist != null) {
              allowlistSource = AllowlistSource.DISK_CACHE
              Log.d(TAG, "Loaded model allowlist from disk cache")
            } else {
              // Disk cache empty — fall back to bundled asset (guarantees models on fresh install).
              Log.w(TAG, "Disk cache empty. Falling back to bundled asset allowlist")
              modelAllowlist = readModelAllowlistFromAssets()
              allowlistSource = AllowlistSource.BUNDLED_ASSET
            }
          }
        }

        if (modelAllowlist == null) {
          _uiState.update {
            uiState.value.copy(
              loadingModelAllowlist = false,
              loadingModelAllowlistError = "Failed to load model list",
              allowlistSource = null,
            )
          }
          return@launch
        }

        Log.d(TAG, "Allowlist: $modelAllowlist")

        // Convert models in the allowlist.
        // Clear existing models to avoid duplicates on retry (mutable list accumulates).
        val curTasks = uiState.value.tasks
        for (task in curTasks) {
          task.models.clear()
        }
        val nameToModel = mutableMapOf<String, Model>()
        for (allowedModel in modelAllowlist.models) {
          if (allowedModel.disabled == true) {
            continue
          }

          // Ignore the allowedModel if its accelerator is only npu and this device's soc is not in
          // its socToModelFiles.
          val accelerators = allowedModel.defaultConfig.accelerators ?: ""
          val acceleratorList = accelerators.split(",").map { it.trim() }.filter { it.isNotEmpty() }
          if (acceleratorList.size == 1 && acceleratorList[0] == "npu") {
            val socToModelFiles = allowedModel.socToModelFiles
            if (socToModelFiles != null && !socToModelFiles.containsKey(SOC)) {
              Log.d(
                TAG,
                "Ignoring model '${allowedModel.name}' because it's NPU-only and not supported on SOC: $SOC",
              )
              continue
            }
          }

          val model = allowedModel.toModel()
          nameToModel.put(model.name, model)
          for (taskType in allowedModel.taskTypes) {
            val task = curTasks.find { it.id == taskType }
            task?.models?.add(model)
          }
        }

        // Find models from allowlist if a task's `modelNames` field is not empty.
        for (task in curTasks) {
          if (task.modelNames.isNotEmpty()) {
            for (modelName in task.modelNames) {
              val model = nameToModel[modelName]
              if (model == null) {
                Log.w(TAG, "Model '${modelName}' in task '${task.label}' not found in allowlist.")
                continue
              }
              task.models.add(model)
            }
          }
        }

        // Process all tasks.
        processTasks()

        // Update UI state.
        _uiState.update {
          createUiState()
            .copy(
              loadingModelAllowlist = false,
              tasks = curTasks,
              tasksByCategory = groupTasksByCategory(),
              allowlistSource = allowlistSource,
            )
        }

        // Process pending downloads.
        processPendingDownloads()
      } catch (e: Exception) {
        Log.e(TAG, "Failed to load model allowlist", e)
        _uiState.update {
          uiState.value.copy(
            loadingModelAllowlist = false,
            loadingModelAllowlistError = "Failed to load model list: ${e.message?.take(80) ?: "Unknown error"}",
          )
        }
      }
    }
  }

  fun clearLoadModelAllowlistError() {
    processTasks()
    _uiState.update {
      createUiState()
        .copy(
          loadingModelAllowlist = false,
          tasks = uiState.value.tasks,
          loadingModelAllowlistError = "",
          tasksByCategory = groupTasksByCategory(),
        )
    }
  }

  fun setAppInForeground(foreground: Boolean) {
    lifecycleProvider.isAppInForeground = foreground
  }

  // Allowlist I/O — delegated to ModelAllowlistLoader
  private fun saveModelAllowlistToDisk(modelAllowlistContent: String) = allowlistLoader.saveToDisk(modelAllowlistContent)
  private fun readModelAllowlistFromDisk(fileName: String = MODEL_ALLOWLIST_FILENAME) =
    if (fileName == MODEL_ALLOWLIST_TEST_FILENAME) allowlistLoader.readTestAllowlist()
    else allowlistLoader.readFromDiskCache()
  private fun readModelAllowlistFromAssets() = allowlistLoader.readFromAssets()

  private fun createEmptyUiState(): ModelManagerUiState {
    return ModelManagerUiState(
      tasks = createBuiltInTasks(),
      tasksByCategory = mapOf(),
      modelDownloadStatus = mapOf(),
      modelInitializationStatus = mapOf(),
    )
  }

  private fun createUiState(): ModelManagerUiState {
    val modelDownloadStatus: MutableMap<String, ModelDownloadStatus> = mutableMapOf()
    val modelInstances: MutableMap<String, ModelInitializationStatus> = mutableMapOf()
    val checkedModelNames = mutableSetOf<String>()
    for (task in uiState.value.tasks) {
      for (model in task.models) {
        if (checkedModelNames.contains(model.name)) {
          continue
        }
        modelDownloadStatus[model.name] = getModelDownloadStatus(model = model)
        modelInstances[model.name] =
          ModelInitializationStatus(status = ModelInitializationStatusType.NOT_INITIALIZED)
        checkedModelNames.add(model.name)
      }
    }

    // Load imported models.
    for (importedModel in dataStoreRepository.readImportedModels()) {
      Log.d(TAG, "stored imported model: $importedModel")

      // Create model.
      val model = LlmHttpModelFactory.buildImportedModel(importedModel)
      LlmHttpModelFactory.restoreInferenceConfig(context, model)

      // Add to all tasks.
      for (task in uiState.value.tasks) {
        task.models.add(model)
      }

      // Update status.
      modelDownloadStatus[model.name] =
        ModelDownloadStatus(
          status = ModelDownloadStatusType.SUCCEEDED,
          receivedBytes = importedModel.fileSize,
          totalBytes = importedModel.fileSize,
        )
    }

    val textInputHistory = dataStoreRepository.readTextInputHistory()
    Log.d(TAG, "text input history: $textInputHistory")

    Log.d(TAG, "model download status: $modelDownloadStatus")
    return ModelManagerUiState(
      tasks = uiState.value.tasks,
      tasksByCategory = mapOf(),
      modelDownloadStatus = modelDownloadStatus,
      modelInitializationStatus = modelInstances,
      textInputHistory = textInputHistory,
    )
  }


  private fun groupTasksByCategory(): Map<String, List<Task>> {
    val tasks = uiState.value.tasks

    val groupedTasks = tasks.groupBy { it.category.id }
    val groupedSortedTasks: MutableMap<String, List<Task>> = mutableMapOf()
    // Sort tasks by label.
    for (categoryId in groupedTasks.keys) {
      val sortedTasks = (groupedTasks[categoryId] ?: continue).sortedBy { it.label }
      for ((index, task) in sortedTasks.withIndex()) {
        task.index = index
      }
      groupedSortedTasks[categoryId] = sortedTasks
    }

    return groupedSortedTasks
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

  private fun updateModelInitializationStatus(
    model: Model,
    status: ModelInitializationStatusType,
    error: String = "",
  ) {
    val curModelInstance = uiState.value.modelInitializationStatus.toMutableMap()
    val initializedBackends = curModelInstance[model.name]?.initializedBackends ?: setOf()
    val backend =
      model.getStringConfigValue(key = ConfigKeys.ACCELERATOR, defaultValue = Accelerator.GPU.label)
    val newInitializedBackends =
      if (status == ModelInitializationStatusType.INITIALIZED) {
        initializedBackends + backend
      } else {
        initializedBackends
      }
    curModelInstance[model.name] =
      ModelInitializationStatus(
        status = status,
        error = error,
        initializedBackends = newInitializedBackends,
      )
    val newUiState = uiState.value.copy(modelInitializationStatus = curModelInstance)
    _uiState.update { newUiState }
  }

}

/** Builds the remote URL for a version-specific model allowlist file (e.g. "0_8_0" → "v1/0_8_0.json"). */
private fun getAllowlistUrl(version: String): String {
  return "$ALLOWLIST_BASE_URL/${version}.json"
}
