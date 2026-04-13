package com.ollitert.llm.server.ui.modelmanager

import androidx.activity.result.ActivityResult
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelAllowlist
import com.ollitert.llm.server.data.ModelDownloadStatus
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService

/**
 * Contracts for the three managers extracted from ModelManagerViewModel.
 * Defined as interfaces so the ViewModel can be tested with fakes that
 * don't hit real OAuth endpoints, network, or file system.
 */

/** Manages HuggingFace OAuth token lifecycle. */
interface TokenManager {
  val authService: AuthorizationService
  var curAccessToken: String
  fun dispose()
  fun getTokenStatusAndData(): TokenStatusAndData
  fun getAuthorizationRequest(): AuthorizationRequest
  fun handleAuthResult(result: ActivityResult, onTokenRequested: (TokenRequestResult) -> Unit)
  fun saveAccessToken(accessToken: String, refreshToken: String, expiresAt: Long)
  fun clearAccessToken()
}

/** Loads the model allowlist from network, disk cache, or bundled assets. */
interface AllowlistLoader {
  data class LoadResult(
    val allowlist: ModelAllowlist?,
    val source: AllowlistSource?,
    val rawJson: String? = null,
  )
  fun readTestAllowlist(): ModelAllowlist?
  fun fetchFromNetwork(version: String): LoadResult
  fun saveToDisk(content: String)
  fun readFromDiskCache(): ModelAllowlist?
  fun readFromAssets(): ModelAllowlist?
}

/** Manages model file operations on the local file system. */
interface ModelFileOps {
  fun isFileInExternalFilesDir(fileName: String): Boolean
  fun isFileInDataLocalTmpDir(fileName: String): Boolean
  fun deleteFileFromExternalFilesDir(fileName: String)
  fun deleteFilesFromImportDir(fileName: String)
  fun deleteDirFromExternalFilesDir(dir: String)
  fun isModelPartiallyDownloaded(model: Model): Boolean
  fun isModelDownloaded(model: Model): Boolean
  fun getModelDownloadStatus(model: Model): ModelDownloadStatus
}
