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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.prefs.ConfigKeys
import com.ollitert.llm.server.data.prefs.DEFAULT_MAX_TOKEN
import com.ollitert.llm.server.data.prefs.DEFAULT_TEMPERATURE
import com.ollitert.llm.server.data.prefs.DEFAULT_TOPK
import com.ollitert.llm.server.data.prefs.DEFAULT_TOPP
import com.ollitert.llm.server.data.prefs.ValueType
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmConfig
import com.ollitert.llm.server.ui.common.ConfigEditorsPanel

/**
 * Edit-mode variant of ModelImportDialog for updating an already-imported model's defaults
 * (capabilities, inference params, accelerators). Does not re-copy the file — only updates
 * the stored metadata. Shows a warning if the model is currently active on the server.
 */
@Composable
fun EditImportedModelDialog(
  existingModel: ImportedModel,
  isCurrentlyActive: Boolean,
  existingImportedModelNames: Set<String>,
  allowlistModelNames: Set<String>,
  onDismiss: () -> Unit,
  onDone: (ImportedModel) -> Unit,
  onRename: (oldFileName: String, newFileName: String, displayName: String) -> Boolean,
) {
  val context = LocalContext.current
  val fileExtension = remember {
    val dotIndex = existingModel.fileName.lastIndexOf('.')
    if (dotIndex > 0) existingModel.fileName.substring(dotIndex) else ""
  }
  val fileStem = remember {
    existingModel.displayName.ifEmpty {
      val dotIndex = existingModel.fileName.lastIndexOf('.')
      if (dotIndex > 0) existingModel.fileName.substring(0, dotIndex) else existingModel.fileName
    }
  }
  var editedStem by remember { mutableStateOf(fileStem) }
  var nameError by remember { mutableStateOf("") }
  val nameValidationRegex = remember { Regex("^[a-zA-Z0-9._-]+$") }

  val errorNameEmpty = stringResource(R.string.error_model_name_empty)
  val errorNameInvalidChars = stringResource(R.string.error_model_name_invalid_chars)
  val errorNameTakenImported = stringResource(R.string.error_model_name_taken_imported)
  val errorNameTakenAllowlist = stringResource(R.string.error_model_name_taken_allowlist)
  val errorRenameFailed = stringResource(R.string.error_model_rename_failed)

  fun validateName(stem: String): String {
    if (stem.isBlank()) return errorNameEmpty
    if (!nameValidationRegex.matches(stem)) return errorNameInvalidChars
    val fullName = stem + fileExtension
    if (fullName == existingModel.fileName && stem == existingModel.displayName.ifEmpty { fileStem }) return ""
    if (fullName != existingModel.fileName && fullName in existingImportedModelNames) return errorNameTakenImported
    if (fullName in allowlistModelNames || stem in allowlistModelNames) return errorNameTakenAllowlist
    return ""
  }

  val editConfigs = remember {
    buildImportConfigsLlm(context, fileExtension).filter { it.key != ConfigKeys.NAME && it.key != ConfigKeys.MODEL_TYPE }
  }

  val initialValues: Map<String, Any> = remember {
    mutableMapOf<String, Any>().apply {
      for (config in editConfigs) put(config.key.id, config.defaultValue)
      existingModel.llmConfig?.let { cfg ->
        put(ConfigKeys.DEFAULT_MAX_TOKENS.id, cfg.defaultMaxTokens.toFloat())
        put(ConfigKeys.DEFAULT_TOPK.id, cfg.defaultTopk.toFloat())
        put(ConfigKeys.DEFAULT_TOPP.id, cfg.defaultTopp)
        put(ConfigKeys.DEFAULT_TEMPERATURE.id, cfg.defaultTemperature)
        put(ConfigKeys.SUPPORT_IMAGE.id, cfg.supportImage)
        put(ConfigKeys.SUPPORT_AUDIO.id, cfg.supportAudio)
        put(ConfigKeys.SUPPORT_THINKING.id, cfg.supportThinking)
        put(ConfigKeys.SUPPORT_TOOLS.id, cfg.supportTools)
        put(ConfigKeys.SUPPORT_SPECULATIVE_DECODING.id, cfg.supportSpeculativeDecoding)
        if (cfg.compatibleAcceleratorsList.isNotEmpty()) {
          put(ConfigKeys.COMPATIBLE_ACCELERATORS.id, cfg.compatibleAcceleratorsList.joinToString(","))
        }
      }
    }
  }
  val values: SnapshotStateMap<String, Any> = remember {
    mutableStateMapOf<String, Any>().apply { putAll(initialValues) }
  }
  val interactionSource = remember { MutableInteractionSource() }

  Dialog(onDismissRequest = onDismiss) {
    val focusManager = LocalFocusManager.current
    Card(
      modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().clickable(
        interactionSource = interactionSource,
        indication = null,
      ) { focusManager.clearFocus() },
      shape = RoundedCornerShape(16.dp),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          stringResource(R.string.dialog_edit_model_defaults_title),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(bottom = 4.dp),
        )

        if (isCurrentlyActive) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Rounded.Error,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp),
            )
            Text(
              stringResource(R.string.dialog_edit_model_active_warning),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          Text(
            stringResource(R.string.dialog_edit_model_defaults_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
          )
        }

        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          OutlinedTextField(
            value = editedStem,
            onValueChange = { newValue ->
              editedStem = newValue
              nameError = validateName(newValue)
            },
            label = { Text(stringResource(R.string.config_label_name)) },
            suffix = { Text(fileExtension, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            isError = nameError.isNotEmpty(),
            supportingText = if (nameError.isNotEmpty()) {
              { Text(nameError) }
            } else null,
            singleLine = true,
            enabled = !isCurrentlyActive,
            modifier = Modifier.fillMaxWidth(),
          )

          ConfigEditorsPanel(configs = editConfigs, values = values)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            CompactToggle(
              label = stringResource(ConfigKeys.SUPPORT_IMAGE.labelResId),
              checked = values[ConfigKeys.SUPPORT_IMAGE.id] as? Boolean ?: false,
              onCheckedChange = { values[ConfigKeys.SUPPORT_IMAGE.id] = it },
              modifier = Modifier.weight(1f),
            )
            CompactToggle(
              label = stringResource(ConfigKeys.SUPPORT_AUDIO.labelResId),
              checked = values[ConfigKeys.SUPPORT_AUDIO.id] as? Boolean ?: false,
              onCheckedChange = { values[ConfigKeys.SUPPORT_AUDIO.id] = it },
              modifier = Modifier.weight(1f),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            CompactToggle(
              label = stringResource(ConfigKeys.SUPPORT_THINKING.labelResId),
              checked = values[ConfigKeys.SUPPORT_THINKING.id] as? Boolean ?: false,
              onCheckedChange = { values[ConfigKeys.SUPPORT_THINKING.id] = it },
              modifier = Modifier.weight(1f),
            )
            CompactToggle(
              label = stringResource(ConfigKeys.SUPPORT_TOOLS.labelResId),
              checked = values[ConfigKeys.SUPPORT_TOOLS.id] as? Boolean ?: false,
              onCheckedChange = { values[ConfigKeys.SUPPORT_TOOLS.id] = it },
              modifier = Modifier.weight(1f),
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            CompactToggle(
              label = stringResource(ConfigKeys.SUPPORT_SPECULATIVE_DECODING.labelResId),
              checked = values[ConfigKeys.SUPPORT_SPECULATIVE_DECODING.id] as? Boolean ?: false,
              onCheckedChange = { values[ConfigKeys.SUPPORT_SPECULATIVE_DECODING.id] = it },
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.weight(1f))
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
          Button(
            onClick = {
              val error = validateName(editedStem)
              if (error.isNotEmpty()) {
                nameError = error
                return@Button
              }

              val newFileName = editedStem + fileExtension
              val renamed = onRename(existingModel.fileName, newFileName, editedStem)
              if (!renamed) {
                nameError = errorRenameFailed
                return@Button
              }

              val supportedAccelerators = safeConfigValue(
                values, ConfigKeys.COMPATIBLE_ACCELERATORS, ValueType.STRING, SUPPORTED_ACCELERATORS[0].label
              ).split(",")
              val defaultMaxTokens = safeConfigValue(values, ConfigKeys.DEFAULT_MAX_TOKENS, ValueType.INT, DEFAULT_MAX_TOKEN)
              val defaultTopk = safeConfigValue(values, ConfigKeys.DEFAULT_TOPK, ValueType.INT, DEFAULT_TOPK)
              val defaultTopp = safeConfigValue(values, ConfigKeys.DEFAULT_TOPP, ValueType.FLOAT, DEFAULT_TOPP)
              val defaultTemperature = safeConfigValue(values, ConfigKeys.DEFAULT_TEMPERATURE, ValueType.FLOAT, DEFAULT_TEMPERATURE)
              val supportImage = safeConfigValue(values, ConfigKeys.SUPPORT_IMAGE, ValueType.BOOLEAN, false)
              val supportAudio = safeConfigValue(values, ConfigKeys.SUPPORT_AUDIO, ValueType.BOOLEAN, false)
              val supportThinking = safeConfigValue(values, ConfigKeys.SUPPORT_THINKING, ValueType.BOOLEAN, false)
              val supportTools = safeConfigValue(values, ConfigKeys.SUPPORT_TOOLS, ValueType.BOOLEAN, false)
              val supportSpeculativeDecoding = safeConfigValue(values, ConfigKeys.SUPPORT_SPECULATIVE_DECODING, ValueType.BOOLEAN, false)
              val updated = ImportedModel.newBuilder()
                .setFileName(newFileName)
                .setFileSize(existingModel.fileSize)
                .setDisplayName(editedStem)
                .setLlmConfig(
                  LlmConfig.newBuilder()
                    .addAllCompatibleAccelerators(supportedAccelerators)
                    .setDefaultMaxTokens(defaultMaxTokens)
                    .setDefaultTopk(defaultTopk)
                    .setDefaultTopp(defaultTopp)
                    .setDefaultTemperature(defaultTemperature)
                    .setSupportImage(supportImage)
                    .setSupportAudio(supportAudio)
                    .setSupportThinking(supportThinking)
                    .setSupportTools(supportTools)
                    .setSupportSpeculativeDecoding(supportSpeculativeDecoding)
                    .build()
                )
                .build()
              onDone(updated)
            }
          ) {
            Text(stringResource(R.string.button_save))
          }
        }
      }
    }
  }
}
