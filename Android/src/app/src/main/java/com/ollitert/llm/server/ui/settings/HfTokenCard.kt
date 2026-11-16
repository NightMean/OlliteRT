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
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.olliteTextFieldColors
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
internal fun HfTokenCard(state: ProviderTokensState, onChange: (ProviderToken, String) -> Unit, onTest: (ProviderToken) -> Unit, context: Context) {
  val hfTokenLinkText = stringResource(R.string.settings_hf_token_link_text)

  SettingsCard(
    icon = Icons.Outlined.Key,
    title = stringResource(R.string.settings_card_hf_token),
    searchQuery = "",
  ) {
    Text(stringResource(R.string.settings_huggingface_token_label), style = MaterialTheme.typography.titleMedium)
    Text(
      text = stringResource(R.string.settings_hf_token_desc),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = buildAnnotatedString {
        withLink(
          LinkAnnotation.Url(
            url = "${GitHubConfig.HUGGINGFACE_BASE_URL}/settings/tokens",
            styles = TextLinkStyles(
              style = SpanStyle(
                color = OlliteRTPrimary,
                textDecoration = TextDecoration.Underline,
              ),
            ),
          ),
        ) {
          append(hfTokenLinkText)
        }
      },
      style = MaterialTheme.typography.bodySmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    ProviderTokenField(state.current.getValue(ProviderToken.HUGGING_FACE), { onChange(ProviderToken.HUGGING_FACE, it) }, context, stringResource(R.string.settings_hf_token_placeholder), stringResource(R.string.settings_huggingface_token_label), state.checks[ProviderToken.HUGGING_FACE], { onTest(ProviderToken.HUGGING_FACE) })
    SettingDivider()
    ModelScopeTokenField(state.current.getValue(ProviderToken.MODELSCOPE_AI), { onChange(ProviderToken.MODELSCOPE_AI, it) }, context, stringResource(R.string.settings_modelscope_ai_token),
      "https://www.modelscope.ai/my/access/token", state.checks[ProviderToken.MODELSCOPE_AI], { onTest(ProviderToken.MODELSCOPE_AI) })
    SettingDivider()
    ModelScopeTokenField(state.current.getValue(ProviderToken.MODELSCOPE_CN), { onChange(ProviderToken.MODELSCOPE_CN, it) }, context, stringResource(R.string.settings_modelscope_cn_token),
      "https://modelscope.cn/my/access/token", state.checks[ProviderToken.MODELSCOPE_CN], { onTest(ProviderToken.MODELSCOPE_CN) })
  }
}

@Composable
private fun ModelScopeTokenField(value: String, onChange: (String) -> Unit, context: Context, label: String, tokenUrl: String, check: TokenCheck?, onTest: () -> Unit) {
  Text(label, style = MaterialTheme.typography.titleMedium)
  Text(stringResource(R.string.settings_modelscope_token_desc), style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant)
  Text(buildAnnotatedString {
    withLink(LinkAnnotation.Url(tokenUrl, styles = TextLinkStyles(style = SpanStyle(
      color = OlliteRTPrimary, textDecoration = TextDecoration.Underline)))) {
      append(tokenUrl.removePrefix("https://").removePrefix("www."))
    }
  }, style = MaterialTheme.typography.bodySmall)
  Spacer(Modifier.height(8.dp))
  ProviderTokenField(value, onChange, context, stringResource(R.string.settings_modelscope_token_placeholder), label, check, onTest)
}

@Composable
private fun ProviderTokenField(value: String, onChange: (String) -> Unit, context: Context, placeholder: String, label: String, check: TokenCheck?, onTest: () -> Unit) {
  var visible by remember { mutableStateOf(false) }
  val tokenClearedText = stringResource(R.string.toast_token_cleared)
  OutlinedTextField(value = value, onValueChange = onChange, singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
    trailingIcon = {
      Row {
        TooltipIconButton(
          icon = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
          tooltip = stringResource(if (visible) R.string.settings_hf_token_hide else R.string.settings_hf_token_show),
          onClick = { visible = !visible },
          backgroundColor = Color.Transparent,
        )
        if (value.isNotEmpty()) {
          TooltipIconButton(
            icon = Icons.Outlined.Close,
            tooltip = stringResource(R.string.settings_hf_token_clear),
            onClick = {
              onChange("")
              Toast.makeText(context, tokenClearedText, Toast.LENGTH_SHORT).show()
            },
            backgroundColor = Color.Transparent,
          )
        }
      }
    }, colors = olliteTextFieldColors(), modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
  val testDescription = stringResource(R.string.provider_token_test_description, label)
  TextButton(onClick = onTest, enabled = value.isNotBlank() && check != TokenCheck.TESTING,
    modifier = Modifier.semantics { contentDescription = testDescription }) {
    Text(stringResource(R.string.provider_token_test))
  }
  if (check == TokenCheck.TESTING) LinearProgressIndicator(Modifier.fillMaxWidth())
  else if (check != null) Row(
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (check == TokenCheck.ACCEPTED) {
      Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = OlliteRTPrimary, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
    }
    Text(
      stringResource(when (check) {
        TokenCheck.ACCEPTED -> R.string.provider_token_accepted
        TokenCheck.REJECTED -> R.string.provider_token_rejected
        TokenCheck.DENIED -> R.string.provider_token_denied
        TokenCheck.RATE_LIMITED -> R.string.provider_token_rate_limited
        else -> R.string.provider_token_unavailable
      }),
      style = MaterialTheme.typography.bodySmall,
      color = if (check == TokenCheck.ACCEPTED) OlliteRTPrimary else MaterialTheme.colorScheme.error,
    )
  }
}
