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

package com.ollitert.llm.server.common

import androidx.core.net.toUri
import net.openid.appauth.AuthorizationServiceConfiguration

/**
 * HuggingFace OAuth configuration for the "Sign in with HuggingFace" flow.
 *
 * Not required for most users — users can enter their own HF API token directly
 * in Settings to download gated models. This OAuth setup is for developers who
 * want to bake their own HF app credentials into a custom build.
 *
 * Setup: see docs/BUILDING.md § "HuggingFace OAuth".
 */
object ProjectConfig {
  // Hugging Face Client ID.
  //
  const val clientId = "REPLACE_WITH_YOUR_CLIENT_ID_IN_HUGGINGFACE_APP"

  // Registered redirect URI.
  //
  // The scheme needs to match the
  // "android.defaultConfig.manifestPlaceholders["appAuthRedirectScheme"]" field in
  // "build.gradle.kts".
  const val redirectUri = "REPLACE_WITH_YOUR_REDIRECT_URI_IN_HUGGINGFACE_APP"

  // OAuth 2.0 Endpoints (Authorization + Token Exchange)
  private const val authEndpoint = "https://huggingface.co/oauth/authorize"
  private const val tokenEndpoint = "https://huggingface.co/oauth/token"

  // OAuth service configuration (AppAuth library requires this)
  val authServiceConfig =
    AuthorizationServiceConfiguration(
      authEndpoint.toUri(), // Authorization endpoint
      tokenEndpoint.toUri(), // Token exchange endpoint
    )
}
