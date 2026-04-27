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
import com.ollitert.llm.server.data.DataStoreRepository
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService

private const val TAG = "OlliteRT.HFToken"

/**
 * Manages HuggingFace OAuth token lifecycle: status checks, authorization
 * requests, token exchange, storage, and cleanup.
 *
 * Separated from ModelManagerViewModel to isolate OAuth/auth concerns
 * from model management, download orchestration, and UI state.
 */
class HuggingFaceTokenManager(
  private val dataStoreRepository: DataStoreRepository,
  context: Context,
) {
  val authService = AuthorizationService(context)
  @Volatile var curAccessToken: String = ""

  fun dispose() {
    authService.dispose()
  }

  suspend fun getTokenStatusAndData(): TokenStatusAndData {
    var tokenStatus = TokenStatus.NOT_STORED
    Log.d(TAG, "Reading token data from data store...")
    val tokenData = dataStoreRepository.readAccessTokenData()

    if (tokenData != null && tokenData.accessToken.isNotEmpty()) {
      Log.d(TAG, "Token exists and loaded.")
      val curTs = System.currentTimeMillis()
      val expirationTs = tokenData.expiresAtMs - 5 * 60
      Log.d(TAG, "Checking whether token has expired or not. Current ts: $curTs, expires at: $expirationTs")
      if (curTs >= expirationTs) {
        Log.d(TAG, "Token expired!")
        tokenStatus = TokenStatus.EXPIRED
      } else {
        Log.d(TAG, "Token not expired.")
        tokenStatus = TokenStatus.NOT_EXPIRED
        curAccessToken = tokenData.accessToken
      }
    } else {
      Log.d(TAG, "Token doesn't exists.")
    }

    return TokenStatusAndData(status = tokenStatus, data = tokenData)
  }

  fun handleAuthResult(result: ActivityResult, onTokenRequested: (TokenRequestResult) -> Unit) {
    val dataIntent = result.data
    if (dataIntent == null) {
      onTokenRequested(
        TokenRequestResult(
          status = TokenRequestResultType.FAILED,
          errorMessage = "Empty auth result",
        )
      )
      return
    }

    val response = AuthorizationResponse.fromIntent(dataIntent)
    val exception = AuthorizationException.fromIntent(dataIntent)

    when {
      response?.authorizationCode != null -> {
        var errorMessage: String? = null
        authService.performTokenRequest(response.createTokenExchangeRequest()) {
          tokenResponse,
          tokenEx ->
          if (tokenResponse != null) {
            val accessToken = tokenResponse.accessToken
            val refreshToken = tokenResponse.refreshToken
            val expiresAt = tokenResponse.accessTokenExpirationTime
            if (accessToken == null) {
              errorMessage = "Empty access token"
            } else if (refreshToken == null) {
              errorMessage = "Empty refresh token"
            } else if (expiresAt == null) {
              errorMessage = "Empty expiration time"
            } else {
              Log.d(TAG, "Token exchange successful. Storing tokens...")
              saveAccessToken(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAt = expiresAt,
              )
              curAccessToken = accessToken
              Log.d(TAG, "Token successfully saved.")
            }
          } else if (tokenEx != null) {
            errorMessage = "Token exchange failed: ${tokenEx.message}"
          } else {
            errorMessage = "Token exchange failed"
          }
          if (errorMessage == null) {
            onTokenRequested(TokenRequestResult(status = TokenRequestResultType.SUCCEEDED))
          } else {
            onTokenRequested(
              TokenRequestResult(
                status = TokenRequestResultType.FAILED,
                errorMessage = errorMessage,
              )
            )
          }
        }
      }

      exception != null -> {
        onTokenRequested(
          TokenRequestResult(
            status =
              if (exception.message == "User cancelled flow") TokenRequestResultType.USER_CANCELLED
              else TokenRequestResultType.FAILED,
            errorMessage = exception.message,
          )
        )
      }

      else -> {
        onTokenRequested(TokenRequestResult(status = TokenRequestResultType.USER_CANCELLED))
      }
    }
  }

  // Called from AppAuth performTokenRequest callback (non-coroutine, background thread).
  // runBlocking is acceptable here — narrow boundary on a background thread.
  fun saveAccessToken(accessToken: String, refreshToken: String, expiresAt: Long) {
    kotlinx.coroutines.runBlocking {
      dataStoreRepository.saveAccessTokenData(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = expiresAt,
      )
    }
  }

}
