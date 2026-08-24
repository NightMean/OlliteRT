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

package com.ollitert.llm.server.ui.settings

import android.content.Context
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.prefs.ClientIpAccessPolicy
import com.ollitert.llm.server.data.prefs.ClientIpPolicyCompileResult
import com.ollitert.llm.server.data.prefs.ClientIpPolicyConfig
import com.ollitert.llm.server.data.prefs.ClientIpPolicyMode
import com.ollitert.llm.server.data.prefs.ServerBindConfig
import com.ollitert.llm.server.data.prefs.ServerBindMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Builds and validates the server's network configuration (bind address +
 * client IP access policy) from Settings UI preference values.
 */
class NetworkPolicyCoordinator @Inject constructor(
  @param:ApplicationContext private val context: Context,
) {

  /** Setting keys owned by this coordinator — persisted/applied outside the generic SettingDef loop. */
  val settingKeys: Set<String> = setOf(
    "server_bind_mode",
    "custom_bind_address",
    "client_ip_policy_mode",
    "client_ip_rules",
  )

  /** Fully compiled network configuration ready to persist and apply to a running server. */
  data class NetworkConfig(
    val bindConfig: ServerBindConfig,
    val policyConfig: ClientIpPolicyConfig,
    val compiledPolicy: ClientIpPolicyCompileResult.Success,
  )

  sealed interface BuildResult {
    data class Success(val config: NetworkConfig) : BuildResult
    data class Invalid(val message: String) : BuildResult
  }

  /**
   * Compiles bind mode + IP rules text into an atomically-applicable configuration.
   * Returns [BuildResult.Invalid] with a user-facing message when rules fail to parse.
   */
  fun buildConfig(
    bindModePref: String?,
    customAddress: String,
    policyModePref: String?,
    rulesText: String,
  ): BuildResult {
    val bindConfig = ServerBindConfig(ServerBindMode.fromPreference(bindModePref), customAddress)
    val policyConfig = ClientIpPolicyConfig(
      mode = ClientIpPolicyMode.fromPreference(policyModePref),
      rulesText = rulesText,
    )
    return when (val result = ClientIpAccessPolicy.compile(policyConfig)) {
      is ClientIpPolicyCompileResult.Success ->
        BuildResult.Success(NetworkConfig(bindConfig, policyConfig.copy(rulesText = result.normalizedRulesText), result))
      else -> BuildResult.Invalid(validationError(result))
    }
  }

  /** Validates IP rules text while editing; null when the rules compile cleanly. */
  fun validateRulesText(policyModePref: String?, rulesText: String): String? =
    when (
      val result = ClientIpAccessPolicy.compile(
        ClientIpPolicyConfig(
          mode = ClientIpPolicyMode.fromPreference(policyModePref),
          rulesText = rulesText,
        )
      )
    ) {
      is ClientIpPolicyCompileResult.Success -> null
      else -> validationError(result)
    }

  /** Maps a failed policy compilation to a localized, user-facing error message. */
  fun validationError(result: ClientIpPolicyCompileResult): String = when (result) {
    ClientIpPolicyCompileResult.EmptyRules -> context.getString(R.string.validation_client_ip_rules_required)
    is ClientIpPolicyCompileResult.InvalidRule -> context.getString(
      R.string.validation_client_ip_rule_invalid,
      result.position,
      result.input,
    )
    is ClientIpPolicyCompileResult.Success -> error("A valid client IP policy has no validation error")
  }
}
