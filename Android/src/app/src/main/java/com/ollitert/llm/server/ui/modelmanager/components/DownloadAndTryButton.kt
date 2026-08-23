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

package com.ollitert.llm.server.ui.modelmanager.components

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.common.isWifiConnected
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.ui.common.ErrorAlertDialog
import com.ollitert.llm.server.ui.common.LoadingBlockingOverlay
import com.ollitert.llm.server.ui.common.MemoryWarningAlert
import com.ollitert.llm.server.ui.common.WifiWarningAlert
import com.ollitert.llm.server.ui.common.checkNotificationPermissionAndStartDownload
import com.ollitert.llm.server.ui.common.isMemoryLow
import com.ollitert.llm.server.ui.common.isMemoryWarningSuppressed
import com.ollitert.llm.server.ui.common.suppressMemoryWarning
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.data.ModelUrlResult
import com.ollitert.llm.server.data.configuredHfTokenOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

private const val TAG = "OlliteRT.DownloadBtn"

internal enum class HfTokenDialogReason { MISSING, INVALID }

/**
 * 3 GB reserved for system stability. Downloads are blocked unless
 * availableBytes > modelSize + this reserve, preventing the device from
 * running out of space for OS operations after a large model download.
 * The storage bar on the Models screen subtracts this from the displayed
 * "available" space so the user sees what's actually usable for models.
 */
internal const val SYSTEM_RESERVED_STORAGE_IN_BYTES = 3 * (1L shl 30)

/**
 * Handles the "Download & Try it" button click, managing the model download process.
 *
 * For HuggingFace URLs that require authentication, uses the stored HF token from Settings.
 * If the token is missing, prompts the user to set one; if invalid, shows an error dialog.
 * For gated models (HTTP 403), displays an agreement acknowledgement sheet.
 *
 * For non-HuggingFace URLs, the download starts directly. If the model is already downloaded,
 * the [onClicked] callback is executed instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadAndTryButton(
  model: Model,
  enabled: Boolean,
  downloadStatus: ModelDownloadStatus?,
  modelManagerViewModel: ModelManagerViewModel,
  onClicked: () -> Unit,
  modifier: Modifier = Modifier,
  onNavigateToSettings: () -> Unit = {},
  modifierWhenExpanded: Modifier = Modifier,
  compact: Boolean = false,
  canShowTryIt: Boolean = true,
  serverStatus: ServerStatus = ServerStatus.STOPPED,
  activeModelName: String? = null,
  onStopServer: () -> Unit = {},
) {
  val isThisModelActive = activeModelName != null &&
    activeModelName.equals(model.name, ignoreCase = true)
  val isThisModelLoading = isThisModelActive && serverStatus == ServerStatus.LOADING
  val isThisModelRunning = isThisModelActive && serverStatus == ServerStatus.RUNNING
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var checkingToken by remember { mutableStateOf(false) }
  var showAgreementAckSheet by remember { mutableStateOf(false) }
  var showErrorDialog by remember { mutableStateOf(false) }
  var showModelNotFoundDialog by remember { mutableStateOf(false) }
  var showStopActiveDialog by remember { mutableStateOf(false) }
  var hfTokenDialogReason by remember { mutableStateOf<HfTokenDialogReason?>(null) }
  var showMemoryWarning by remember { mutableStateOf(false) }
  var showStorageWarning by remember { mutableStateOf(false) }
  var showWifiWarning by remember { mutableStateOf(false) }
  var downloadStarted by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState()

  val isFailed = downloadStatus?.status == ModelDownloadStatusType.FAILED
  val needToDownloadFirst =
    (downloadStatus?.status == ModelDownloadStatusType.NOT_DOWNLOADED || isFailed) &&
      model.localFileRelativeDirPathOverride.isEmpty()
  val inProgress = downloadStatus?.status == ModelDownloadStatusType.IN_PROGRESS
  val downloadSucceeded = downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
  val isPartiallyDownloaded = downloadStatus?.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED
  val failedWithProgress =
    isFailed && downloadStatus.receivedBytes > 0L && downloadStatus.totalBytes > 0L
  if (downloadStatus?.status == ModelDownloadStatusType.NOT_DOWNLOADED && !checkingToken) {
    downloadStarted = false
  }
  val showDownloadProgress =
    !downloadSucceeded &&
      (downloadStarted || checkingToken || inProgress || isPartiallyDownloaded || failedWithProgress)
  var curDownloadProgress: Float

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      modelManagerViewModel.downloadModel(model = model)
    }

  val startDownload: (accessToken: String?) -> Unit = { accessToken ->
    model.accessToken = accessToken
    checkNotificationPermissionAndStartDownload(
      context = context,
      launcher = permissionLauncher,
      modelManagerViewModel = modelManagerViewModel,
      model = model,
    )
    checkingToken = false
  }

  val agreementAckLauncher: ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) {
      Log.d(TAG, "User closes the browser tab. Verifying access before downloading.")
      scope.launch(Dispatchers.IO) {
        val token = configuredHfTokenOrNull(ServerPrefs.getHfToken(context))
        val urlResult = modelManagerViewModel.getModelUrlResponse(model = model, accessToken = token)
        withContext(Dispatchers.Main) {
          if (urlResult is ModelUrlResult.Success && urlResult.code == HttpURLConnection.HTTP_OK) {
            Log.d(TAG, "Agreement accepted. Starting download.")
            startDownload(token)
          } else {
            Log.d(TAG, "Agreement not accepted. Resetting.")
            downloadStarted = false
            checkingToken = false
          }
        }
      }
    }

  val handleClickButton = {
    scope.launch(Dispatchers.IO) {
      if (needToDownloadFirst) {
        downloadStarted = true
        if (model.url.startsWith(GitHubConfig.HUGGINGFACE_BASE_URL)) {
          checkingToken = true
          Log.d(TAG, "Model '${model.name}' is from HuggingFace. Checking if auth is required")
          val firstResult = modelManagerViewModel.getModelUrlResponse(model = model)
          when (firstResult) {
            is ModelUrlResult.Error -> {
              checkingToken = false
              downloadStarted = false
              Log.e(TAG, "Network error: ${firstResult.message}")
              showErrorDialog = true
              return@launch
            }
            is ModelUrlResult.Success -> {
              if (firstResult.code == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Model '${model.name}' doesn't need auth. Start downloading...")
                withContext(Dispatchers.Main) { startDownload(null) }
                return@launch
              }
              if (firstResult.code == HttpURLConnection.HTTP_NOT_FOUND) {
                Log.d(TAG, "Model '${model.name}' returned 404 — model not found.")
                checkingToken = false
                downloadStarted = false
                showModelNotFoundDialog = true
                return@launch
              }
            }
          }
          Log.d(TAG, "Model '${model.name}' needs auth.")

          val storedHfToken = configuredHfTokenOrNull(ServerPrefs.getHfToken(context))
          if (storedHfToken != null) {
            Log.d(TAG, "Trying stored HF token from Settings...")
            val hfResult = modelManagerViewModel.getModelUrlResponse(
              model = model,
              accessToken = storedHfToken,
            )
            when (hfResult) {
              is ModelUrlResult.Error -> {
                Log.e(TAG, "Network error checking HF token: ${hfResult.message}")
                checkingToken = false
                downloadStarted = false
                showErrorDialog = true
                return@launch
              }
              is ModelUrlResult.Success -> {
                if (hfResult.code == HttpURLConnection.HTTP_OK) {
                  Log.d(TAG, "Stored HF token works. Start downloading...")
                  withContext(Dispatchers.Main) { startDownload(storedHfToken) }
                  return@launch
                } else if (hfResult.code == HttpURLConnection.HTTP_NOT_FOUND) {
                  Log.d(TAG, "Model '${model.name}' returned 404 with token — model not found.")
                  checkingToken = false
                  downloadStarted = false
                  showModelNotFoundDialog = true
                  return@launch
                } else if (hfResult.code == HttpURLConnection.HTTP_FORBIDDEN) {
                  Log.d(TAG, "Model needs license agreement. Opening agreement page...")
                  checkingToken = false
                  showAgreementAckSheet = true
                  return@launch
                }
                Log.d(TAG, "Stored HF token is invalid (response=${hfResult.code}).")
              }
            }
            checkingToken = false
            downloadStarted = false
            hfTokenDialogReason = HfTokenDialogReason.INVALID
            return@launch
          } else {
            Log.d(TAG, "No HF token stored. Prompting user to set one in Settings.")
            checkingToken = false
            downloadStarted = false
            hfTokenDialogReason = HfTokenDialogReason.MISSING
            return@launch
          }
        } else {
          Log.d(TAG, "Model '${model.name}' is not from HuggingFace. Start downloading...")
          withContext(Dispatchers.Main) { startDownload(null) }
        }
      } else {
        withContext(Dispatchers.Main) {
          if (!isWifiConnected(context)) {
            showWifiWarning = true
          } else {
            onClicked()
          }
        }
      }
    }
  }

  val checkMemoryAndClickDownloadButton = {
    if (needToDownloadFirst && isStorageLow(model)) {
      showStorageWarning = true
    } else if (isMemoryLow(context = context, model = model) && !isMemoryWarningSuppressed(context, model.name)) {
      showMemoryWarning = true
    } else {
      handleClickButton()
    }
  }

  if (!showDownloadProgress) {
    var buttonModifier: Modifier = modifier.defaultMinSize(minHeight = 42.dp)
    if (!compact) {
      buttonModifier = buttonModifier.then(modifierWhenExpanded)
    }

    if (isThisModelLoading) {
      Button(
        modifier = buttonModifier,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        enabled = false,
        onClick = {},
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            strokeWidth = 2.dp,
          )
          if (!compact) {
            Text(
              stringResource(R.string.label_loading_model),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp, stepSize = 1.sp),
            )
          }
        }
      }
    } else if (isThisModelRunning) {
      Button(
        modifier = buttonModifier,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
        onClick = {
          if (RequestLogStore.entries.value.any { it.isPending }) {
            showStopActiveDialog = true
          } else {
            onStopServer()
          }
        },
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            Icons.Outlined.StopCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
          if (!compact) {
            Text(
              stringResource(R.string.button_stop_server),
              color = MaterialTheme.colorScheme.error,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp, stepSize = 1.sp),
            )
          }
        }
      }
    } else {
      val isAnyModelLoading = serverStatus == ServerStatus.LOADING
      val isStartDisabled = isAnyModelLoading && downloadSucceeded
      val effectiveEnabled = enabled && !isStartDisabled
      Box(modifier = buttonModifier) {
        Button(
          modifier = Modifier.defaultMinSize(minHeight = 42.dp).fillMaxWidth(),
          enabled = effectiveEnabled,
          colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            containerColor = if ((!downloadSucceeded || !canShowTryIt) && model.localFileRelativeDirPathOverride.isEmpty()) {
              MaterialTheme.colorScheme.surfaceContainer
            } else {
              MaterialTheme.colorScheme.primary
            }
          ),
          contentPadding = PaddingValues(horizontal = 12.dp),
          onClick = {
            if (!checkingToken) {
              checkMemoryAndClickDownloadButton()
            }
          },
        ) {
          val textColor = if (!effectiveEnabled) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
          } else if (!downloadSucceeded && model.localFileRelativeDirPathOverride.isEmpty()) {
            MaterialTheme.colorScheme.onSurface
          } else {
            MaterialTheme.colorScheme.onPrimary
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              if (needToDownloadFirst) Icons.Outlined.FileDownload else Icons.AutoMirrored.Rounded.ArrowForward,
              contentDescription = null,
              tint = textColor,
            )
            if (!compact) {
              if (needToDownloadFirst) {
                Text(
                  stringResource(R.string.download),
                  color = textColor,
                  style = MaterialTheme.typography.titleMedium,
                )
              } else if (canShowTryIt) {
                Text(
                  stringResource(R.string.try_it),
                  color = textColor,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp, stepSize = 1.sp),
                )
              }
            }
          }
        }
        if (isStartDisabled) {
          LoadingBlockingOverlay(stringResource(R.string.model_loading_hint_wait))
        }
      }
    }
  } else {
    curDownloadProgress = if (downloadStatus != null && downloadStatus.totalBytes > 0) {
      downloadStatus.receivedBytes.toFloat() / downloadStatus.totalBytes.toFloat()
    } else 0f
    if (curDownloadProgress.isNaN()) {
      curDownloadProgress = 0f
    }
    val animatedProgress = remember { Animatable(curDownloadProgress) }

    var downloadProgressModifier: Modifier = modifier
    if (!compact) {
      downloadProgressModifier = downloadProgressModifier.fillMaxWidth()
    }
    downloadProgressModifier = downloadProgressModifier
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceContainer)
      .padding(horizontal = 8.dp)
      .height(42.dp)
    Row(modifier = downloadProgressModifier, verticalAlignment = Alignment.CenterVertically) {
      if (checkingToken) {
        Text(
          stringResource(R.string.checking_access),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = if (!compact) Modifier.fillMaxWidth() else Modifier.padding(horizontal = 4.dp),
        )
      } else {
        val percentColor = if (failedWithProgress) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        Text(
          "${(curDownloadProgress * 100).toInt()}%",
          style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
          color = percentColor,
          modifier = Modifier.padding(start = 12.dp).width(if (compact) 32.dp else 44.dp),
        )
        if (!compact) {
          val barColor = if (failedWithProgress) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
          LinearProgressIndicator(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            progress = { animatedProgress.value },
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          )
        }
        if (failedWithProgress) {
          val cbRetry = stringResource(R.string.cd_retry_download_icon)
          IconButton(
            onClick = {
              downloadStarted = true
              modelManagerViewModel.retryDownloadModel(model = model)
            },
            colors = IconButtonDefaults.iconButtonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.semantics { contentDescription = cbRetry },
          ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
          }
        }
        val cbStop = stringResource(R.string.cd_stop_icon)
        IconButton(
          onClick = {
            downloadStarted = false
            modelManagerViewModel.cancelDownloadModel(model = model)
          },
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
          ),
          modifier = Modifier.semantics { contentDescription = cbStop },
        ) {
          Icon(Icons.Outlined.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
      }
    }
    LaunchedEffect(curDownloadProgress) {
      animatedProgress.animateTo(curDownloadProgress, animationSpec = tween(150))
    }
  }

  if (showAgreementAckSheet) {
    GatedAgreementSheet(
      model = model,
      sheetState = sheetState,
      agreementAckLauncher = agreementAckLauncher,
      onDismiss = {
        showAgreementAckSheet = false
        checkingToken = false
        downloadStarted = false
      },
    )
  }

  if (showErrorDialog) {
    ErrorAlertDialog(
      title = stringResource(R.string.dialog_network_error_title),
      text = stringResource(R.string.dialog_network_error_body),
      onDismiss = { showErrorDialog = false },
      confirmLabel = stringResource(R.string.close),
    )
  }

  if (showModelNotFoundDialog) {
    ErrorAlertDialog(
      title = stringResource(R.string.dialog_model_not_found_title),
      text = stringResource(R.string.dialog_model_not_found_body),
      onDismiss = { showModelNotFoundDialog = false },
      confirmLabel = stringResource(R.string.close),
    )
  }

  if (showStopActiveDialog) {
    StopActiveRequestsDialog(
      onConfirmStop = {
        showStopActiveDialog = false
        onStopServer()
      },
      onDismiss = { showStopActiveDialog = false },
    )
  }

  hfTokenDialogReason?.let { reason ->
    HfTokenRequiredDialog(
      reason = reason,
      onNavigateToSettings = onNavigateToSettings,
      onDismiss = { hfTokenDialogReason = null },
    )
  }

  if (showMemoryWarning) {
    MemoryWarningAlert(
      modelName = model.name,
      onProceeded = { dontAskAgain ->
        if (dontAskAgain) {
          suppressMemoryWarning(context, model.name)
        }
        handleClickButton()
        showMemoryWarning = false
      },
      onDismissed = { showMemoryWarning = false },
    )
  }

  if (showStorageWarning) {
    StorageWarningDialog(
      model = model,
      onProceedAnyway = { handleClickButton() },
      onDismiss = { showStorageWarning = false },
    )
  }

  if (showWifiWarning) {
    WifiWarningAlert(
      port = ServerPrefs.getPort(context),
      onStartAnyway = {
        showWifiWarning = false
        onClicked()
      },
      onDismissed = { showWifiWarning = false },
    )
  }
}

/** Returns true when available storage is less than the given size plus the system reserve. */
internal fun isStorageLow(sizeInBytes: Long): Boolean {
  if (sizeInBytes <= 0) return false
  return try {
    val stat = StatFs(Environment.getDataDirectory().path)
    val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
    availableBytes < sizeInBytes + SYSTEM_RESERVED_STORAGE_IN_BYTES
  } catch (e: Exception) {
    Log.w(TAG, "Failed to check storage availability", e)
    false
  }
}

/** Returns true when available storage is less than the model's total download size. */
private fun isStorageLow(model: Model): Boolean = isStorageLow(model.totalBytes)
