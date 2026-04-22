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

package com.ollitert.llm.server.ui.server.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.server.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
internal fun HfTokenCard(vm: SettingsViewModel, context: Context) {
  val hfTokenLinkText = stringResource(R.string.settings_hf_token_link_text)
  val tokenClearedText = stringResource(R.string.toast_token_cleared)

  SettingsCard(
    icon = Icons.Outlined.Key,
    title = stringResource(R.string.settings_card_hf_token),
    searchQuery = vm.searchQuery,
  ) {
    Text(
      text = stringResource(R.string.settings_hf_token_desc),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = buildAnnotatedString {
        withLink(
          LinkAnnotation.Url(
            url = "https://huggingface.co/settings/tokens",
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
    OutlinedTextField(
      value = vm.hfTokenEntry.current,
      onValueChange = { vm.hfTokenEntry.update(it.trim()) },
      singleLine = true,
      visualTransformation = if (vm.hfTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
      placeholder = {
        Text(
          stringResource(R.string.settings_hf_token_placeholder),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
      },
      trailingIcon = {
        Row {
          IconButton(onClick = { vm.hfTokenVisible = !vm.hfTokenVisible }) {
            Icon(
              imageVector = if (vm.hfTokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
              contentDescription = stringResource(if (vm.hfTokenVisible) R.string.settings_hf_token_hide else R.string.settings_hf_token_show),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (vm.hfTokenEntry.current.isNotBlank()) {
            IconButton(onClick = {
              vm.hfTokenEntry.update("")
              Toast.makeText(context, tokenClearedText, Toast.LENGTH_SHORT).show()
            }) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.settings_hf_token_clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      },
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = OlliteRTPrimary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
      ),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
