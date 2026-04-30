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

package com.ollitert.llm.server.ui.common

import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatus
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.bytesToGb
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.ui.modelmanager.ModelUrlResult
import com.ollitert.llm.server.ui.modelmanager.TokenRequestResultType
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection

private const val TAG = "OlliteRT.DownloadBtn"

private enum class HfTokenDialogReason { MISSING, INVALID }
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

  val needToDownloadFirst =
    (downloadStatus?.status == ModelDownloadStatusType.NOT_DOWNLOADED ||
      downloadStatus?.status == ModelDownloadStatusType.FAILED) &&
      model.localFileRelativeDirPathOverride.isEmpty()
  val inProgress = downloadStatus?.status == ModelDownloadStatusType.IN_PROGRESS
  val downloadSucceeded = downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED
  val isPartiallyDownloaded = downloadStatus?.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED
  if (downloadStatus?.status == ModelDownloadStatusType.NOT_DOWNLOADED && !checkingToken) {
    downloadStarted = false
  }
  val showDownloadProgress =
    !downloadSucceeded && (downloadStarted || checkingToken || inProgress || isPartiallyDownloaded)
  var curDownloadProgress: Float

  // A launcher for requesting notification permission.
  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      modelManagerViewModel.downloadModel(model = model)
    }

  // Function to kick off download.
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

  // A launcher for opening the custom tabs intent for requesting user agreement ack.
  // Once the tab is closed, verify access before starting the download — the user may
  // have closed the browser without accepting the terms.
  val agreementAckLauncher: ActivityResultLauncher<Intent> =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      Log.d(TAG, "User closes the browser tab. Verifying access before downloading.")
      scope.launch(Dispatchers.IO) {
        val token = modelManagerViewModel.curAccessToken
        val urlResult = modelManagerViewModel.getModelUrlResponse(model = model, accessToken = token)
        withContext(Dispatchers.Main) {
          if (urlResult is ModelUrlResult.Success && urlResult.code == HttpURLConnection.HTTP_OK) {
            Log.d(TAG, "Agreement accepted. Starting download.")
            startDownload(token)
          } else {
            Log.d(TAG, "Agreement not accepted (code=${(urlResult as? ModelUrlResult.Success)?.code}). Resetting.")
            downloadStarted = false
            checkingToken = false
          }
        }
      }
    }

  // A launcher for handling the authentication flow.
  // It processes the result of the authentication activity and then checks if a user agreement
  // acknowledgement is needed before proceeding with the model download.
  val authResultLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      modelManagerViewModel.handleAuthResult(
        result,
        onTokenRequested = { tokenRequestResult ->
          when (tokenRequestResult.status) {
            TokenRequestResultType.SUCCEEDED -> {
              Log.d(TAG, "Token request succeeded. Checking if we need user to ack user agreement")
              scope.launch(Dispatchers.IO) {
                // Check if we can use the current token to access model. If not, we might need to
                // acknowledge the user agreement.
                val urlResult = modelManagerViewModel.getModelUrlResponse(
                  model = model,
                  accessToken = modelManagerViewModel.curAccessToken,
                )
                if (
                  urlResult is ModelUrlResult.Success &&
                    urlResult.code == HttpURLConnection.HTTP_FORBIDDEN
                ) {
                  Log.d(TAG, "Model '${model.name}' needs user agreement ack.")
                  showAgreementAckSheet = true
                } else {
                  Log.d(
                    TAG,
                    "Model '${model.name}' does NOT need user agreement ack. Start downloading...",
                  )
                  withContext(Dispatchers.Main) {
                    startDownload(modelManagerViewModel.curAccessToken)
                  }
                }
              }
            }

            TokenRequestResultType.FAILED -> {
              Log.d(
                TAG,
                "Token request done. Error message: ${tokenRequestResult.errorMessage ?: ""}",
              )
              checkingToken = false
              downloadStarted = false
            }

            TokenRequestResultType.USER_CANCELLED -> {
              Log.d(TAG, "User cancelled. Do nothing")
              checkingToken = false
              downloadStarted = false
            }
          }
        },
      )
    }

  // Launches a coroutine to handle the initial check and potential authentication flow
  // before downloading the model. It checks if the model needs to be downloaded first,
  // handles HuggingFace URLs by verifying the need for authentication, and initiates
  // the token exchange process if required or proceeds with the download if no auth is needed
  // or a valid token is available.
  val handleClickButton = {
    scope.launch(Dispatchers.IO) {
      if (needToDownloadFirst) {
        downloadStarted = true
        // For HuggingFace urls
        if (model.url.startsWith(GitHubConfig.HUGGINGFACE_BASE_URL)) {
          checkingToken = true

          // Check if the url needs auth.
          Log.d(
            TAG,
            "Model '${model.name}' is from HuggingFace. Checking if the url needs auth to download",
          )
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
                Log.d(TAG, "Model '${model.name}' doesn't need auth. Start downloading the model...")
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

          // First, try with the HuggingFace token from Settings.
          val storedHfToken = com.ollitert.llm.server.data.ServerPrefs.getHfToken(context)
          if (storedHfToken.isNotBlank()) {
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
            // No HF token stored — prompt user to set one in Settings.
            Log.d(TAG, "No HF token stored. Prompting user to set one in Settings.")
            checkingToken = false
            downloadStarted = false
            hfTokenDialogReason = HfTokenDialogReason.MISSING
            return@launch
          }
        }
        // For other urls, just download the model.
        else {
          Log.d(
            TAG,
            "Model '${model.name}' is not from huggingface. Start downloading the model...",
          )
          withContext(Dispatchers.Main) { startDownload(null) }
        }
      }
      // No need to download. Check WiFi before starting server.
      else {
        withContext(Dispatchers.Main) {
          if (!com.ollitert.llm.server.common.isWifiConnected(context)) {
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

    // Show loading state when this model is being loaded
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
              autoSize =
                TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp, stepSize = 1.sp),
            )
          }
        }
      }
    }
    // Show stop button when this model is running
    else if (isThisModelRunning) {
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
              autoSize =
                TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp, stepSize = 1.sp),
            )
          }
        }
      }
    }
    // Normal state: download or start server
    else {
    // Disable "Start Server" on all models while any model is loading
    val isAnyModelLoading = serverStatus == ServerStatus.LOADING
    val isStartDisabled = isAnyModelLoading && downloadSucceeded
    val effectiveEnabled = enabled && !isStartDisabled
    Box(modifier = buttonModifier) {
    Button(
      modifier = Modifier.defaultMinSize(minHeight = 42.dp).fillMaxWidth(),
      enabled = effectiveEnabled,
      colors =
        ButtonDefaults.buttonColors(
          disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          containerColor =
            if (
              (!downloadSucceeded || !canShowTryIt) &&
                model.localFileRelativeDirPathOverride.isEmpty()
            ) {
              MaterialTheme.colorScheme.surfaceContainer
            } else {
              MaterialTheme.colorScheme.primary
            }
        ),
      contentPadding = PaddingValues(horizontal = 12.dp),
      onClick = {
        if (checkingToken) {
          return@Button
        }

        checkMemoryAndClickDownloadButton()
      },
    ) {
      val textColor =
        if (!effectiveEnabled) {
          // Define the color for disabled button.
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
          if (needToDownloadFirst) {
            Icons.Outlined.FileDownload
          } else {
            Icons.AutoMirrored.Rounded.ArrowForward
          },
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
              autoSize =
                TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 16.sp, stepSize = 1.sp),
            )
          }
        }
      }
    }
    // Invisible overlay to show toast when disabled
    if (isStartDisabled) {
      LoadingBlockingOverlay(stringResource(R.string.model_loading_hint_wait))
    }
    }
    }
  }
  // Download progress.
  else {
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
    downloadProgressModifier =
      downloadProgressModifier
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
        Text(
          "${(curDownloadProgress * 100).toInt()}%",
          style =
            MaterialTheme.typography.bodyMedium.copy(
              // This stops numbers from "jumping around" when being updated.
              fontFeatureSettings = "tnum"
            ),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(start = 12.dp).width(if (compact) 32.dp else 44.dp),
        )
        if (!compact) {
          val color = MaterialTheme.colorScheme.primary
          LinearProgressIndicator(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            progress = { animatedProgress.value },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          )
        }
        val cbStop = stringResource(R.string.cd_stop_icon)
        IconButton(
          onClick = {
            downloadStarted = false
            modelManagerViewModel.cancelDownloadModel(model = model)
          },
          colors =
            IconButtonDefaults.iconButtonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
          modifier = Modifier.semantics { contentDescription = cbStop },
        ) {
          Icon(
            Icons.Outlined.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
    LaunchedEffect(curDownloadProgress) {
      animatedProgress.animateTo(curDownloadProgress, animationSpec = tween(150))
    }
  }

  // A ModalBottomSheet composable that displays information about the user agreement
  // for a gated model and provides a button to open the agreement in a custom tab.
  // Upon clicking the button, it constructs the agreement URL, launches it using a
  // custom tab, and then dismisses the bottom sheet.
  if (showAgreementAckSheet) {
    ModalBottomSheet(
      onDismissRequest = {
        showAgreementAckSheet = false
        checkingToken = false
        downloadStarted = false
      },
      sheetState = sheetState,
      sheetMaxWidth = SHEET_MAX_WIDTH,
      modifier = Modifier.wrapContentHeight(),
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp),
      ) {
        Text(stringResource(R.string.dialog_user_agreement_title), style = MaterialTheme.typography.titleLarge)
        Text(
          stringResource(R.string.dialog_user_agreement_body),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(
          onClick = {
            // Get agreement url from model url.
            val index = model.url.indexOf("/resolve/")
            // Show it in a tab.
            if (index >= 0) {
              val agreementUrl = model.url.substring(0, index)

              val customTabsIntent = CustomTabsIntent.Builder().build()
              customTabsIntent.intent.setData(agreementUrl.toUri())
              agreementAckLauncher.launch(customTabsIntent.intent)
            }
            // Dismiss the sheet — keep downloadStarted true because agreementAckLauncher
            // will proceed with the download when the user returns from the browser.
            showAgreementAckSheet = false
          }
        ) {
          Text(stringResource(R.string.button_open_user_agreement))
        }
      }
    }
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
    val entries by RequestLogStore.entries.collectAsStateWithLifecycle()
    val pendingCount = entries.count { it.isPending }
    AlertDialog(
      onDismissRequest = { showStopActiveDialog = false },
      title = {
        Text(
          text = stringResource(R.string.logs_dialog_stop_active_title),
          style = MaterialTheme.typography.titleMedium,
        )
      },
      text = {
        Text(
          text = pluralStringResource(R.plurals.logs_dialog_stop_active_body, pendingCount, pendingCount),
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showStopActiveDialog = false
            onStopServer()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
          ),
        ) {
          Text(stringResource(R.string.logs_dialog_clear_active_stop))
        }
      },
      dismissButton = {
        TextButton(onClick = { showStopActiveDialog = false }) {
          Text(stringResource(R.string.logs_dialog_clear_cancel))
        }
      },
    )
  }

  hfTokenDialogReason?.let { reason ->
    val isInvalid = reason == HfTokenDialogReason.INVALID
    val titleRes = if (isInvalid) R.string.dialog_hf_token_invalid_title else R.string.dialog_hf_token_required_title
    val bodyRes = if (isInvalid) R.string.dialog_hf_token_invalid_body else R.string.dialog_hf_token_required_body
    val icon = if (isInvalid) Icons.Outlined.ErrorOutline else Icons.Outlined.Key
    val iconTint = if (isInvalid) MaterialTheme.colorScheme.error else OlliteRTPrimary
    val dismiss = { hfTokenDialogReason = null }

    AlertDialog(
      icon = { Icon(icon, contentDescription = null, tint = iconTint) },
      title = { Text(stringResource(titleRes)) },
      text = { Text(stringResource(bodyRes)) },
      onDismissRequest = dismiss,
      confirmButton = {
        TextButton(onClick = { dismiss(); onNavigateToSettings() }) {
          Text(stringResource(R.string.button_go_to_settings))
        }
      },
      dismissButton = {
        TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) }
      },
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
    // Build a detailed breakdown so the user understands why the download is
    // blocked even though raw free space may appear sufficient. The 3 GB system
    // reserve (SYSTEM_RESERVED_STORAGE_IN_BYTES) keeps the device stable after
    // downloading large models\.
    val modelSizeGb = model.totalBytes.bytesToGb()
    val reserveGb = SYSTEM_RESERVED_STORAGE_IN_BYTES.bytesToGb()
    val totalRequiredGb = modelSizeGb + reserveGb
    val availableBytes = try {
      val stat = StatFs(Environment.getDataDirectory().path)
      stat.availableBlocksLong * stat.blockSizeLong
    } catch (_: Exception) { 0L }
    val availableGb = availableBytes.bytesToGb()

    AlertDialog(
      icon = {
        Icon(
          Icons.Rounded.Error,
          contentDescription = stringResource(R.string.cd_error),
          tint = MaterialTheme.colorScheme.error,
        )
      },
      title = { Text(stringResource(R.string.dialog_storage_warning_title)) },
      text = {
        Text(
          stringResource(
            R.string.dialog_storage_warning_body,
            totalRequiredGb,
            modelSizeGb,
            reserveGb,
            availableGb,
            (totalRequiredGb - availableGb).coerceAtLeast(0f),
          )
        )
      },
      onDismissRequest = { showStorageWarning = false },
      // Cancel is the confirm (right) button — more prominent position — so the
      // user's natural tap lands on the safe action. "Download Anyway" is the
      // less prominent dismiss (left) button for power users who understand the risk.
      confirmButton = {
        TextButton(onClick = { showStorageWarning = false }) { Text(stringResource(R.string.cancel)) }
      },
      dismissButton = {
        TextButton(onClick = {
          showStorageWarning = false
          handleClickButton()
        }) { Text(stringResource(R.string.button_download_anyway)) }
      },
    )
  }

  if (showWifiWarning) {
    WifiWarningAlert(
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
    android.util.Log.w(TAG, "Failed to check storage availability", e)
    false
  }
}

/** Returns true when available storage is less than the model's total download size. */
private fun isStorageLow(model: Model): Boolean = isStorageLow(model.totalBytes)
