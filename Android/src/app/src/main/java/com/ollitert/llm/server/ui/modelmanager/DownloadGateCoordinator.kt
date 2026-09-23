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

import android.util.Log
import com.ollitert.llm.server.data.allowlist.ModelUrlResult
import com.ollitert.llm.server.data.allowlist.configuredHfTokenOrNull
import com.ollitert.llm.server.data.allowlist.isHuggingFaceUrl
import com.ollitert.llm.server.data.download.isTransientDownloadStatus
import com.ollitert.llm.server.data.download.modelScopeFallback
import com.ollitert.llm.server.data.model.Model
import com.ollitert.llm.server.ui.modelmanager.components.HfTokenDialogReason
import java.net.HttpURLConnection

/**
 * Decision produced by [DownloadGateCoordinator.resolveDownloadAccess]. The composable
 * maps each outcome to its dialog/action; all probing and branching lives here.
 */
internal sealed class DownloadGateOutcome {
  /** Access confirmed — start the download with this token (null = anonymous). */
  data class StartDownload(
    val accessToken: String?,
    val modelScopePrimaryError: String? = null,
  ) : DownloadGateOutcome()

  data class NeedsModelScopeConsent(val primaryError: String) : DownloadGateOutcome()

  /** Network failure while probing access; message is safe for the error dialog. */
  data class NetworkError(val message: String) : DownloadGateOutcome()

  /** Remote URL resolves to nothing — model removed or renamed upstream. */
  data object ModelNotFound : DownloadGateOutcome()

  /** Gated model whose license agreement has not been accepted yet. */
  data object NeedsAgreement : DownloadGateOutcome()

  /** No usable HuggingFace token — either none configured or the stored one is rejected. */
  data class NeedsHfToken(val reason: HfTokenDialogReason) : DownloadGateOutcome()
}

/**
 * Encapsulates the pre-download access decision chain for the "Download" action:
 * whether a model URL needs authentication, whether the stored HF token works, and
 * which user-facing gate (agreement sheet / token prompt / not-found / network error)
 * applies. Extracted from DownloadAndTryButton so the decision tree is unit-testable
 * and the composable stays presentation-only.
 *
 * @param probeUrl performs the HEAD-like access check against the model URL
 * @param storedHfToken resolves the token configured in Settings (already normalized)
 */
internal class DownloadGateCoordinator(
  private val probeUrl: suspend (model: Model, accessToken: String?) -> ModelUrlResult,
  private val storedHfToken: () -> String?,
  private val fallbackEnabled: () -> Boolean = { false },
) {
  /**
   * Resolves what should happen when the user requests downloading [model].
   * Performs network probes — call from a background dispatcher.
   */
  suspend fun resolveDownloadAccess(model: Model): DownloadGateOutcome {
    if (!isHuggingFaceUrl(model.url)) {
      Log.d(TAG, "Model '${model.name}' is not from HuggingFace. Start downloading...")
      return DownloadGateOutcome.StartDownload(null)
    }

    Log.d(TAG, "Model '${model.name}' is from HuggingFace. Checking if auth is required")
    return when (val firstResult = probeUrl(model, null)) {
      is ModelUrlResult.Error -> {
        Log.e(TAG, "Network error: ${firstResult.message}")
        networkFailure(model, firstResult.message, firstResult.retryable)
      }
      is ModelUrlResult.Success -> when {
        isTransientDownloadStatus(firstResult.code) ->
          networkFailure(model, "Hugging Face returned HTTP ${firstResult.code}", true)
        firstResult.code == HttpURLConnection.HTTP_OK -> {
          Log.d(TAG, "Model '${model.name}' doesn't need auth. Start downloading...")
          DownloadGateOutcome.StartDownload(null)
        }
        firstResult.code == HttpURLConnection.HTTP_NOT_FOUND -> {
          Log.d(TAG, "Model '${model.name}' returned 404 — model not found.")
          DownloadGateOutcome.ModelNotFound
        }
        else -> resolveWithStoredToken(model)
      }
    }
  }

  private suspend fun resolveWithStoredToken(model: Model): DownloadGateOutcome {
    val token = storedHfToken()
    if (token == null) {
      Log.d(TAG, "No HF token stored. Prompting user to set one in Settings.")
      return DownloadGateOutcome.NeedsHfToken(HfTokenDialogReason.MISSING)
    }

    Log.d(TAG, "Trying stored HF token from Settings...")
    return when (val hfResult = probeUrl(model, token)) {
      is ModelUrlResult.Error -> {
        Log.e(TAG, "Network error checking HF token: ${hfResult.message}")
        DownloadGateOutcome.NetworkError(hfResult.message)
      }
      is ModelUrlResult.Success -> when {
        isTransientDownloadStatus(hfResult.code) ->
          DownloadGateOutcome.NetworkError("Hugging Face returned HTTP ${hfResult.code}")
        hfResult.code == HttpURLConnection.HTTP_OK -> {
          Log.d(TAG, "Stored HF token works. Start downloading...")
          DownloadGateOutcome.StartDownload(token)
        }
        hfResult.code == HttpURLConnection.HTTP_NOT_FOUND -> {
          Log.d(TAG, "Model '${model.name}' returned 404 with token — model not found.")
          DownloadGateOutcome.ModelNotFound
        }
        hfResult.code == HttpURLConnection.HTTP_FORBIDDEN -> {
          Log.d(TAG, "Model needs license agreement. Opening agreement page...")
          DownloadGateOutcome.NeedsAgreement
        }
        else -> {
          Log.d(TAG, "Stored HF token is invalid (response=${hfResult.code}).")
          DownloadGateOutcome.NeedsHfToken(HfTokenDialogReason.INVALID)
        }
      }
    }
  }

  private fun networkFailure(model: Model, message: String, retryable: Boolean): DownloadGateOutcome {
    val fallback = modelScopeFallback(model.url)
    if (!retryable || model.isZip || model.extraDataFiles.isNotEmpty() ||
      fallback == null || fallback.requiresAccessConfirmation
    ) return DownloadGateOutcome.NetworkError(message)
    return if (fallbackEnabled()) {
      DownloadGateOutcome.StartDownload(null, modelScopePrimaryError = message)
    } else {
      DownloadGateOutcome.NeedsModelScopeConsent(message)
    }
  }

  companion object {
    private const val TAG = "OlliteRT.DownloadBtn"

    /** Convenience factory wiring the coordinator to the ViewModel's access-check calls. */
    fun forViewModel(viewModel: ModelManagerViewModel): DownloadGateCoordinator =
      DownloadGateCoordinator(
        probeUrl = { model, accessToken -> viewModel.getModelUrlResponse(model = model, accessToken = accessToken) },
        storedHfToken = { configuredHfTokenOrNull(viewModel.getHfToken()) },
        fallbackEnabled = { viewModel.isModelScopeFallbackEnabled() },
      )
  }
}
