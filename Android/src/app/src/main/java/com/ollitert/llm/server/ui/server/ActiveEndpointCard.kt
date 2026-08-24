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

package com.ollitert.llm.server.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.copyToClipboard
import com.ollitert.llm.server.data.prefs.ClientIpPolicyConfig
import com.ollitert.llm.server.data.prefs.ClientIpPolicyMode
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTWarningYellow
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

@Composable
internal fun ActiveEndpointCard(
  endpointUrl: String?,
  isStopped: Boolean,
  corsOrigins: String,
  authOn: Boolean,
  clientIpPolicy: ClientIpPolicyConfig,
  isLoopbackOnly: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(20.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Outlined.Lan,
          contentDescription = null,
          tint = if (isStopped) MaterialTheme.colorScheme.onSurfaceVariant else OlliteRTPrimary,
          modifier = Modifier.size(26.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.status_active_api_endpoint),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = endpointUrl ?: stringResource(R.string.status_endpoint_placeholder),
          style = MaterialTheme.typography.bodyMedium,
          color = if (endpointUrl != null) OlliteRTPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          fontFamily = SpaceGroteskFontFamily,
          textDecoration = if (endpointUrl != null) TextDecoration.Underline else TextDecoration.None,
          modifier = if (endpointUrl != null) Modifier.clickable(
            onClickLabel = stringResource(R.string.status_open_endpoint),
          ) { uriHandler.openUri(endpointUrl) } else Modifier,
        )
        val corsLabel = when {
          corsOrigins.isBlank() -> stringResource(R.string.status_cors_disabled)
          corsOrigins == "*" -> stringResource(R.string.status_cors_all_origins)
          else -> stringResource(R.string.status_cors_restricted)
        }
        val authLabel = if (authOn) stringResource(R.string.status_auth_on) else stringResource(R.string.status_auth_off)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = stringResource(R.string.status_auth_cors, authLabel, corsLabel),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        val clientAccessLabel = when (clientIpPolicy.mode) {
          ClientIpPolicyMode.ALLOW_ALL -> stringResource(R.string.status_client_access_all)
          ClientIpPolicyMode.ALLOW_ONLY -> stringResource(
            R.string.status_client_access_allow_only,
            clientIpPolicy.rulesText.countIpRules(),
          )
          ClientIpPolicyMode.BLOCK_LISTED -> stringResource(
            R.string.status_client_access_block_listed,
            clientIpPolicy.rulesText.countIpRules(),
          )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = stringResource(R.string.status_client_access, clientAccessLabel),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        if (endpointUrl != null && isLoopbackOnly) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = stringResource(R.string.status_endpoint_loopback_only),
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTWarningYellow.copy(alpha = 0.8f),
          )
        }
      }
      if (endpointUrl != null) {
        TooltipIconButton(
          icon = Icons.Outlined.ContentCopy,
          tooltip = stringResource(R.string.status_copy_endpoint_tooltip),
          onClick = { copyToClipboard(context, "OlliteRT Endpoint", endpointUrl) },
          tint = OlliteRTPrimary,
        )
      }
    }
  }
}

internal fun String.countIpRules(): Int = split(Regex("[,\\r\\n]+")).count { it.isNotBlank() }
