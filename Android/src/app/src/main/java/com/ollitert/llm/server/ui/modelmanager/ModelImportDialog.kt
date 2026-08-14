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

import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.ConfigKey
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.DEFAULT_MAX_TOKEN
import com.ollitert.llm.server.data.DEFAULT_TEMPERATURE
import com.ollitert.llm.server.data.DEFAULT_TOPK
import com.ollitert.llm.server.data.DEFAULT_TOPP
import com.ollitert.llm.server.data.ValueType
import com.ollitert.llm.server.data.bytesToGb
import com.ollitert.llm.server.data.ensureValidFileName
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmConfig
import com.ollitert.llm.server.ui.common.ConfigEditorsPanel
import com.ollitert.llm.server.ui.common.modelitem.SYSTEM_RESERVED_STORAGE_IN_BYTES
import com.ollitert.llm.server.ui.common.modelitem.isStorageLow
import com.ollitert.llm.server.ui.theme.OlliteRTOnPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
fun ModelImportDialog(
  uri: Uri,
  onDismiss: () -> Unit,
  onDone: (ImportedModel) -> Unit,
  defaultValues: Map<ConfigKey, Any> = emptyMap(),
  /** Names of already-imported models — used to show a replace confirmation dialog. */
  existingImportedModelNames: Set<String> = emptySet(),
  /** Names of allowlist models — used to prevent importing with conflicting names. */
  allowlistModelNames: Set<String> = emptySet(),
) {
  val context = LocalContext.current
  val info = remember { getFileSizeAndDisplayNameFromUri(context = context, uri = uri) }
  val fileSize by remember { mutableLongStateOf(info.first) }
  val fileName by remember { mutableStateOf(ensureValidFileName(info.second)) }

  // Split into editable stem and read-only extension so the user can rename
  // the model without accidentally changing or removing the file extension.
  val fileExtension = remember {
    val dotIndex = fileName.lastIndexOf('.')
    if (dotIndex > 0) fileName.substring(dotIndex) else ""
  }
  val fileStem = remember {
    val dotIndex = fileName.lastIndexOf('.')
    if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
  }

  // Pending model to import — set when the user taps Import on a duplicate name.
  // The confirmation dialog reads this and either proceeds or cancels.
  var pendingReplaceModel by remember { mutableStateOf<ImportedModel?>(null) }

  // Pending model to import when storage is low — shows a warning before proceeding.
  var pendingStorageModel by remember { mutableStateOf<ImportedModel?>(null) }

  // Shown when imported model name conflicts with an allowlist model.
  var showAllowlistConflictError by remember { mutableStateOf(false) }

  val importConfigs = remember { buildImportConfigsLlm(context, fileExtension) }

  val initialValues: Map<String, Any> = remember {
    mutableMapOf<String, Any>().apply {
      for (config in importConfigs) {
        put(config.key.id, config.defaultValue)
      }
      // Only the stem is editable; the extension is appended on import.
      put(ConfigKeys.NAME.id, fileStem)
      // Hardcoded to LLM -- when non-LLM model types are supported, make this selectable
      put(ConfigKeys.MODEL_TYPE.id, "LLM")
      // Capability toggles rendered inline as rows, not via ConfigEditorsPanel
      put(ConfigKeys.SUPPORT_IMAGE.id, false)
      put(ConfigKeys.SUPPORT_AUDIO.id, false)
      put(ConfigKeys.SUPPORT_THINKING.id, false)
      put(ConfigKeys.SUPPORT_TOOLS.id, false)
      put(ConfigKeys.SUPPORT_SPECULATIVE_DECODING.id, false)

      for ((key, value) in defaultValues) {
        put(key.id, value)
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
      modifier =
        Modifier.widthIn(max = 560.dp).fillMaxWidth().clickable(
          interactionSource = interactionSource,
          indication = null, // Disable the ripple effect
        ) {
          focusManager.clearFocus()
        },
      shape = RoundedCornerShape(16.dp),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Title.
        Text(
          stringResource(R.string.dialog_import_model_title),
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OlliteRTPrimary)
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = OlliteRTOnPrimary,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = stringResource(R.string.import_defaults_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = OlliteRTOnPrimary,
          )
        }

        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          ConfigEditorsPanel(configs = importConfigs, values = values)

          // Capability toggles — compact two-per-row layout
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

        // Button row.
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          // Cancel button.
          TextButton(onClick = { onDismiss() }) { Text(stringResource(R.string.cancel)) }

          // Import button
          Button(
            onClick = {
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
              // Rejoin the user-edited stem with the original extension and sanitize.
              val editedStem = ensureValidFileName(
                (values[ConfigKeys.NAME.id] as? String) ?: fileStem
              )
              val editedName = editedStem + fileExtension
              val importedModel: ImportedModel =
                ImportedModel.newBuilder()
                  .setFileName(editedName)
                  .setFileSize(fileSize)
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
              // Check available storage before proceeding.
              if (isStorageLow(fileSize)) {
                pendingStorageModel = importedModel
              }
              // Check if a model with this name already exists.
              else if (editedName in existingImportedModelNames) {
                pendingReplaceModel = importedModel
              }
              // Check if name conflicts with an allowlist model.
              else if (editedName in allowlistModelNames || editedStem in allowlistModelNames) {
                showAllowlistConflictError = true
              } else {
                onDone(importedModel)
              }
            }
          ) {
            Text(stringResource(R.string.button_import))
          }
        }
      }
    }
  }

  // Confirmation dialog when re-importing a model that already exists
  val replaceModel = pendingReplaceModel
  if (replaceModel != null) {
    AlertDialog(
      onDismissRequest = { pendingReplaceModel = null },
      title = { Text(stringResource(R.string.dialog_replace_model_title)) },
      text = {
        Text(stringResource(R.string.dialog_replace_model_body, replaceModel.fileName))
      },
      confirmButton = {
        Button(onClick = {
          pendingReplaceModel = null
          onDone(replaceModel)
        }) {
          Text(stringResource(R.string.button_replace))
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingReplaceModel = null }) {
          Text(stringResource(R.string.cancel))
        }
      },
    )
  }

  // Storage warning dialog — shown when there isn't enough space to import.
  val storageModel = pendingStorageModel
  if (storageModel != null) {
    val modelSizeGb = fileSize.bytesToGb()
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
            R.string.dialog_storage_warning_import_body,
            totalRequiredGb,
            modelSizeGb,
            reserveGb,
            availableGb,
            (totalRequiredGb - availableGb).coerceAtLeast(0f),
          )
        )
      },
      onDismissRequest = { pendingStorageModel = null },
      confirmButton = {
        TextButton(onClick = { pendingStorageModel = null }) { Text(stringResource(R.string.cancel)) }
      },
      dismissButton = {
        TextButton(onClick = {
          pendingStorageModel = null
          // Proceed despite low storage — still check for duplicate name and allowlist conflict.
          if (storageModel.fileName in existingImportedModelNames) {
            pendingReplaceModel = storageModel
          } else if (storageModel.fileName in allowlistModelNames || storageModel.fileName.substringBeforeLast('.') in allowlistModelNames) {
            showAllowlistConflictError = true
          } else {
            onDone(storageModel)
          }
        }) { Text(stringResource(R.string.button_import_anyway)) }
      },
    )
  }

  if (showAllowlistConflictError) {
    AlertDialog(
      onDismissRequest = { showAllowlistConflictError = false },
      title = { Text(stringResource(R.string.dialog_allowlist_conflict_title)) },
      text = { Text(stringResource(R.string.dialog_allowlist_conflict_body)) },
      confirmButton = {
        TextButton(onClick = { showAllowlistConflictError = false }) {
          Text(stringResource(R.string.ok))
        }
      },
    )
  }
}
