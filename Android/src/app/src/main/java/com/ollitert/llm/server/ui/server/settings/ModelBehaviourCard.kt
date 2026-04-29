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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Token
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.common.olliteTextFieldColors
import com.ollitert.llm.server.ui.server.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
internal fun ModelBehaviourCard(vm: SettingsViewModel) {
  SettingsCard(
    icon = Icons.Outlined.Token,
    title = stringResource(R.string.settings_card_model_behaviour),
    searchQuery = vm.searchQuery,
  ) {
    ToggleCardContent(cardId = CardId.MODEL_BEHAVIOUR, vm = vm)

    // Expandable prompt text editor (visible only when STT Transcription Prompt toggle is ON)
    AnimatedVisibility(
      visible = vm.sttTranscriptionPromptEntry.current,
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
      var promptExpanded by remember { mutableStateOf(false) }

      Column {
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
          onClick = { promptExpanded = !promptExpanded },
        ) {
          Icon(
            imageVector = Icons.Outlined.Edit,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = OlliteRTPrimary,
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.settings_ha_edit_prompt),
            style = MaterialTheme.typography.labelMedium,
            color = OlliteRTPrimary,
          )
        }

        AnimatedVisibility(
          visible = promptExpanded,
          enter = expandVertically(),
          exit = shrinkVertically(),
        ) {
          Column {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = vm.sttTranscriptionPromptTextEntry.current,
              onValueChange = { vm.sttTranscriptionPromptTextEntry.update(it) },
              placeholder = {
                Text(
                  stringResource(R.string.settings_ha_transcription_prompt_placeholder),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
              },
              trailingIcon = {
                if (vm.sttTranscriptionPromptTextEntry.current.isNotBlank()) {
                  IconButton(onClick = { vm.sttTranscriptionPromptTextEntry.update("") }) {
                    Icon(
                      imageVector = Icons.Outlined.Close,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              },
              minLines = 2,
              maxLines = 5,
              colors = olliteTextFieldColors(),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = stringResource(R.string.settings_stt_transcription_prompt_text_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
