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

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.AppLifecycleProvider
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.SemVer
import com.ollitert.llm.server.common.getJsonResponse
import com.ollitert.llm.server.data.Config
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.DownloadRepository
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.EMPTY_MODEL
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelAllowlistJson
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.SOC
import com.ollitert.llm.server.data.TMP_FILE_EXT
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
private const val MODEL_ALLOWLIST_FILENAME = "model_allowlist.json"
private const val MODEL_ALLOWLIST_TEST_FILENAME = "model_allowlist_test.json"
private const val ALLOWLIST_URL = GitHubConfig.ALLOWLIST_URL

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

  /** Where the allowlist was loaded from (network, disk cache, or bundled asset). */
  val allowlistSource: AllowlistSource? = null,

  /** The currently selected model. */
  val selectedModel: Model = EMPTY_MODEL,

  val configValuesUpdateTrigger: Long = 0L,
  // Bumped when storage changes (download complete, model deleted).
  val storageUpdateTrigger: Long = 0L,
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
    return uiState.value.models.find { it.name == name }
  }

  fun getAllModels(): List<Model> {
    return uiState.value.models.sortedBy { it.displayName.ifEmpty { it.name } }
  }

  fun getAllDownloadedModels(): List<Model> {
    return getAllModels().filter {
      uiState.value.modelDownloadStatus[it.name]?.status == ModelDownloadStatusType.SUCCEEDED &&
        it.isLlm
    }
  }

  fun processModels() {
    for (model in uiState.value.models) {
      model.preProcess()
      // Restore persisted inference config (temperature, max tokens, etc.) so settings
      // survive app restarts. Overlays saved values on top of model defaults.
      LlmHttpModelFactory.restoreInferenceConfig(context, model)
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

  fun downloadModel(model: Model) {
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
    var updatedModels = uiState.value.models
    if (model.imported) {
      updatedModels = updatedModels.filter { it.name != model.name }
      curModelDownloadStatus.remove(model.name)

      // Update data store.
      val importedModels = dataStoreRepository.readImportedModels().toMutableList()
      val importedModelIndex = importedModels.indexOfFirst { it.fileName == model.name }
      if (importedModelIndex >= 0) {
        importedModels.removeAt(importedModelIndex)
      }
      dataStoreRepository.saveImportedModels(importedModels = importedModels)
    }
    _uiState.update {
      uiState.value.copy(
        modelDownloadStatus = curModelDownloadStatus,
        models = updatedModels,
      )
    }
  }

  /** Delete model and notify storage change (for user-initiated deletions). */
  fun deleteModelAndRefreshStorage(model: Model) {
    deleteModel(model = model)
    notifyStorageChanged()
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

    // Remove duplicate imported model if it exists, then add the new one.
    val updatedModels = uiState.value.models
      .filter { !(it.name == info.fileName && it.imported) }
      .plus(model)

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

    val now = System.currentTimeMillis()
    _uiState.update {
      uiState.value.copy(
        models = updatedModels,
        modelDownloadStatus = modelDownloadStatus,
        modelInitializationStatus = modelInstances,
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

    // Replace the model in the flat list
    val updatedModels = uiState.value.models.map { m ->
      if (m.name == updatedInfo.fileName && m.imported) updatedModel else m
    }

    _uiState.update {
      it.copy(models = updatedModels)
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
          // Load from the single master allowlist on GitHub.
          // Version filtering is handled by minAppVersion/maxAppVersion in the JSON.
          val url = ALLOWLIST_URL
          Log.d(TAG, "Loading model allowlist from internet. Url: $url")
          val data = getJsonResponse<ModelAllowlist>(url = url)
          modelAllowlist = data?.let { ModelAllowlistJson.decode(it.textContent) }

          if (modelAllowlist != null && data != null) {
            // Network fetch succeeded — save to disk cache for future offline use.
            allowlistSource = AllowlistSource.NETWORK
            Log.d(TAG, "Done: loading model allowlist from internet")
            saveModelAllowlistToDisk(modelAllowlistContent = data.textContent)
          } else {
            // Network failed — try disk cache. If this was a manual retry, notify the user.
            Log.w(TAG, "Failed to load model allowlist from internet. Trying to load it from disk")
            if (isManualRetry) _toastErrorChannel.trySend(context.getString(R.string.error_model_server_unreachable))
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
              loadingModelAllowlistError = context.getString(R.string.error_model_list_load_failed),
              allowlistSource = null,
            )
          }
          return@launch
        }

        Log.d(TAG, "Allowlist: $modelAllowlist")

        val appVersion = SemVer.parse(BuildConfig.VERSION_NAME)

        if (modelAllowlist.schemaVersion > ModelAllowlist.SUPPORTED_SCHEMA_VERSION) {
          Log.w(TAG, "Schema version ${modelAllowlist.schemaVersion} unsupported — clearing model list")
          modelAllowlist = ModelAllowlist(schemaVersion = modelAllowlist.schemaVersion, models = emptyList())
        }

        // Convert models in the allowlist into a flat list.
        val models = mutableListOf<Model>()
        for (allowedModel in modelAllowlist.models) {
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

          models.add(allowedModel.toModel(appVersion = appVersion))
        }

        // Store models in state so processModels and createUiState can read them.
        _uiState.update { it.copy(models = models) }

        // Process all models.
        processModels()

        // Update UI state.
        _uiState.update {
          createUiState()
            .copy(
              loadingModelAllowlist = false,
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
            loadingModelAllowlistError = context.getString(R.string.error_model_list_load_failed_detail, e.message?.take(80) ?: context.getString(R.string.error_unknown)),
          )
        }
      }
    }
  }

  fun clearLoadModelAllowlistError() {
    processModels()
    _uiState.update {
      createUiState()
        .copy(
          loadingModelAllowlist = false,
          loadingModelAllowlistError = "",
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
    return ModelManagerUiState()
  }

  private fun createUiState(): ModelManagerUiState {
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

      val model = LlmHttpModelFactory.buildImportedModel(importedModel)
      LlmHttpModelFactory.restoreInferenceConfig(context, model)
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

