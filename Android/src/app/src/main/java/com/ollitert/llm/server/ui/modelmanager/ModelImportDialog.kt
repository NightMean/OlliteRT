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
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.isPixel10
import com.ollitert.llm.server.data.Accelerator
import com.ollitert.llm.server.data.BooleanSwitchConfig
import com.ollitert.llm.server.data.Config
import com.ollitert.llm.server.data.ConfigKey
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.DEFAULT_MAX_TOKEN
import com.ollitert.llm.server.data.DEFAULT_TEMPERATURE
import com.ollitert.llm.server.data.DEFAULT_TOPK
import com.ollitert.llm.server.data.DEFAULT_TOPP
import com.ollitert.llm.server.data.EditableTextConfig
import com.ollitert.llm.server.data.IMPORTS_DIR
import com.ollitert.llm.server.data.LabelConfig
import com.ollitert.llm.server.data.MAX_MAX_TOKENS
import com.ollitert.llm.server.data.MAX_TEMPERATURE
import com.ollitert.llm.server.data.MAX_TOPK
import com.ollitert.llm.server.data.MAX_TOPP
import com.ollitert.llm.server.data.MIN_MAX_TOKENS
import com.ollitert.llm.server.data.MIN_TEMPERATURE
import com.ollitert.llm.server.data.MIN_TOPK
import com.ollitert.llm.server.data.MIN_TOPP
import com.ollitert.llm.server.data.NumberSliderConfig
import com.ollitert.llm.server.data.SegmentedButtonConfig
import com.ollitert.llm.server.data.ValueType
import com.ollitert.llm.server.data.bytesToGb
import com.ollitert.llm.server.data.convertValueToTargetType
import com.ollitert.llm.server.proto.ImportedModel
import com.ollitert.llm.server.proto.LlmConfig
import com.ollitert.llm.server.ui.common.ConfigEditorsPanel
import com.ollitert.llm.server.ui.common.SYSTEM_RESERVED_MEMORY_IN_BYTES
import com.ollitert.llm.server.ui.common.ensureValidFileName
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.ui.common.isStorageLow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val TAG = "OlliteRT.Import"

// NPU requires SoC-specific model files and vendor native libraries (Qualcomm QAIRT / MediaTek
// NeuroPilot). No models currently ship with NPU support. Re-enable Accelerator.NPU here when
// NPU-compiled models become available in the allowlist.
private inline fun <reified T> safeConfigValue(
  values: Map<String, Any>,
  key: ConfigKey,
  valueType: ValueType,
  default: T,
): T {
  val raw = values[key.label] ?: return default
  val converted = convertValueToTargetType(raw, valueType)
  return converted as? T ?: default
}

private val SUPPORTED_ACCELERATORS: List<Accelerator> =
  if (isPixel10()) {
    listOf(Accelerator.CPU)
  } else {
    listOf(Accelerator.CPU, Accelerator.GPU)
  }

/**
 * Builds the import config list with the file extension passed to the editable name field,
 * so the extension is shown as a read-only suffix next to the text input.
 */
private fun buildImportConfigsLlm(context: Context, fileExtension: String): List<Config> =
  listOf(
    EditableTextConfig(key = ConfigKeys.NAME, suffix = fileExtension),
    LabelConfig(key = ConfigKeys.MODEL_TYPE),
    NumberSliderConfig(
      key = ConfigKeys.DEFAULT_MAX_TOKENS,
      sliderMin = MIN_MAX_TOKENS.toFloat(),
      sliderMax = MAX_MAX_TOKENS.toFloat(),
      defaultValue = DEFAULT_MAX_TOKEN.toFloat(),
      valueType = ValueType.INT,
    ),
    NumberSliderConfig(
      key = ConfigKeys.DEFAULT_TOPK,
      sliderMin = MIN_TOPK.toFloat(),
      sliderMax = MAX_TOPK.toFloat(),
      defaultValue = DEFAULT_TOPK.toFloat(),
      valueType = ValueType.INT,
    ),
    NumberSliderConfig(
      key = ConfigKeys.DEFAULT_TOPP,
      sliderMin = MIN_TOPP,
      sliderMax = MAX_TOPP,
      defaultValue = DEFAULT_TOPP,
      valueType = ValueType.FLOAT,
    ),
    NumberSliderConfig(
      key = ConfigKeys.DEFAULT_TEMPERATURE,
      sliderMin = MIN_TEMPERATURE,
      sliderMax = MAX_TEMPERATURE,
      defaultValue = DEFAULT_TEMPERATURE,
      valueType = ValueType.FLOAT,
    ),
    BooleanSwitchConfig(key = ConfigKeys.SUPPORT_IMAGE, defaultValue = false),
    BooleanSwitchConfig(key = ConfigKeys.SUPPORT_AUDIO, defaultValue = false),
    BooleanSwitchConfig(key = ConfigKeys.SUPPORT_THINKING, defaultValue = false),
    SegmentedButtonConfig(
      key = ConfigKeys.COMPATIBLE_ACCELERATORS,
      defaultValue = SUPPORTED_ACCELERATORS[0].label,
      options = SUPPORTED_ACCELERATORS.map { it.label },
      allowMultiple = true,
      description = context.getString(R.string.import_compatible_accelerators_description),
    ),
  )

@Composable
fun ModelImportDialog(
  uri: Uri,
  onDismiss: () -> Unit,
  onDone: (ImportedModel) -> Unit,
  defaultValues: Map<ConfigKey, Any> = emptyMap(),
  /** Names of already-imported models — used to show a replace confirmation dialog. */
  existingImportedModelNames: Set<String> = emptySet(),
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

  val importConfigs = remember { buildImportConfigsLlm(context, fileExtension) }

  val initialValues: Map<String, Any> = remember {
    mutableMapOf<String, Any>().apply {
      for (config in importConfigs) {
        put(config.key.label, config.defaultValue)
      }
      // Only the stem is editable; the extension is appended on import.
      put(ConfigKeys.NAME.label, fileStem)
      // Hardcoded to LLM -- when non-LLM model types are supported, make this selectable
      put(ConfigKeys.MODEL_TYPE.label, "LLM")

      for ((key, value) in defaultValues) {
        put(key.label, value)
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

        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // Default configs for users to set.
          ConfigEditorsPanel(configs = importConfigs, values = values)
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
              // Rejoin the user-edited stem with the original extension and sanitize.
              val editedStem = ensureValidFileName(
                (values[ConfigKeys.NAME.label] as? String) ?: fileStem
              )
              val editedName = editedStem + fileExtension
              val importedModel: ImportedModel =
                ImportedModel.newBuilder()
                  .setFileName(editedName)
                  .setFileSize(fileSize)
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
    val reserveGb = SYSTEM_RESERVED_MEMORY_IN_BYTES.bytesToGb()
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
          // Proceed despite low storage — still check for duplicate name.
          if (storageModel.fileName in existingImportedModelNames) {
            pendingReplaceModel = storageModel
          } else {
            onDone(storageModel)
          }
        }) { Text(stringResource(R.string.button_import_anyway)) }
      },
    )
  }
}

/**
 * Edit-mode variant of [ModelImportDialog] for updating an already-imported model's defaults
 * (capabilities, inference params, accelerators). Does not re-copy the file — only updates
 * the stored metadata. Shows a warning if the model is currently active on the server.
 */
@Composable
fun EditImportedModelDialog(
  existingModel: ImportedModel,
  isCurrentlyActive: Boolean,
  onDismiss: () -> Unit,
  onDone: (ImportedModel) -> Unit,
) {
  val fileExtension = remember {
    val dotIndex = existingModel.fileName.lastIndexOf('.')
    if (dotIndex > 0) existingModel.fileName.substring(dotIndex) else ""
  }

  val context = LocalContext.current
  // Build configs without the name field — file name is fixed and cannot be changed here
  val editConfigs = remember {
    buildImportConfigsLlm(context, fileExtension).filter { it.key != ConfigKeys.NAME && it.key != ConfigKeys.MODEL_TYPE }
  }

  // Pre-populate from existing proto values
  val initialValues: Map<String, Any> = remember {
    mutableMapOf<String, Any>().apply {
      for (config in editConfigs) put(config.key.label, config.defaultValue)
      existingModel.llmConfig?.let { cfg ->
        // NumberSliderConfig stores values as Float — cast Int proto fields accordingly
        put(ConfigKeys.DEFAULT_MAX_TOKENS.label, cfg.defaultMaxTokens.toFloat())
        put(ConfigKeys.DEFAULT_TOPK.label, cfg.defaultTopk.toFloat())
        put(ConfigKeys.DEFAULT_TOPP.label, cfg.defaultTopp)
        put(ConfigKeys.DEFAULT_TEMPERATURE.label, cfg.defaultTemperature)
        put(ConfigKeys.SUPPORT_IMAGE.label, cfg.supportImage)
        put(ConfigKeys.SUPPORT_AUDIO.label, cfg.supportAudio)
        put(ConfigKeys.SUPPORT_THINKING.label, cfg.supportThinking)
        if (cfg.compatibleAcceleratorsList.isNotEmpty()) {
          put(ConfigKeys.COMPATIBLE_ACCELERATORS.label, cfg.compatibleAcceleratorsList.joinToString(","))
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

        // Warn if this model is currently loaded on the server — changes take effect on reload
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
          ConfigEditorsPanel(configs = editConfigs, values = values)
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
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
              val updated = ImportedModel.newBuilder()
                .setFileName(existingModel.fileName)
                .setFileSize(existingModel.fileSize)
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

@Composable
fun ModelImportingDialog(
  uri: Uri,
  info: ImportedModel,
  onDismiss: () -> Unit,
  onDone: (ImportedModel) -> Unit,
) {
  var error by remember { mutableStateOf("") }
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var progress by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    // Import.
    importModel(
      context = context,
      coroutineScope = coroutineScope,
      fileName = info.fileName,
      fileSize = info.fileSize,
      uri = uri,
      onDone = { onDone(info) },
      onProgress = { progress = it },
      onError = { error = it },
    )
  }

  Dialog(
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    onDismissRequest = onDismiss,
  ) {
    Card(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
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

        // No error.
        if (error.isEmpty()) {
          // Progress bar.
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              "${info.fileName} (${info.fileSize.humanReadableSize()})",
              style = MaterialTheme.typography.labelSmall,
            )
            val animatedProgress = remember { Animatable(0f) }
            LinearProgressIndicator(
              progress = { animatedProgress.value },
              modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            LaunchedEffect(progress) {
              animatedProgress.animateTo(progress, animationSpec = tween(150))
            }
          }
        }
        // Has error.
        else {
          Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              Icons.Rounded.Error,
              contentDescription = stringResource(R.string.cd_error),
              tint = MaterialTheme.colorScheme.error,
            )
            Text(
              error,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(top = 4.dp),
            )
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { onDismiss() }) { Text(stringResource(R.string.close)) }
          }
        }
      }
    }
  }
}

private fun importModel(
  context: Context,
  coroutineScope: CoroutineScope,
  fileName: String,
  fileSize: Long,
  uri: Uri,
  onDone: () -> Unit,
  onProgress: (Float) -> Unit,
  onError: (String) -> Unit,
) {
  coroutineScope.launch(Dispatchers.IO) {
    // Get the last component of the uri path as the imported file name.
    val decodedUri = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8.name())
    Log.d(TAG, "importing model from $decodedUri. File name: $fileName. File size: $fileSize")

    // Create <app_external_dir>/imports if not exist.
    val externalDir = context.getExternalFilesDir(null)
      ?: throw IOException("External storage unavailable — cannot import model")
    val importsDir = File(externalDir, IMPORTS_DIR)
    if (!importsDir.exists()) {
      importsDir.mkdirs()
    }

    // Import by copying to a .tmp file first, then rename on success.
    // If the app is killed mid-copy, the .tmp file is cleaned up on next launch.
    val finalFile = File(externalDir, "$IMPORTS_DIR/$fileName")
    val tmpFile = File(externalDir, "$IMPORTS_DIR/${fileName}.tmp")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytesRead: Int
    var lastSetProgressTs: Long = 0
    var importedBytes = 0L
    try {
      val inputStream = context.contentResolver.openInputStream(uri)
      if (inputStream == null) {
        if (!tmpFile.delete()) Log.w(TAG, "Failed to delete temp file: ${tmpFile.name}")
        onError(context.getString(R.string.error_import_failed))
        return@launch
      }
      inputStream.use { input ->
        FileOutputStream(tmpFile).use { outputStream ->
          while (input.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            importedBytes += bytesRead

            // Report progress every 200 ms.
            val curTs = System.currentTimeMillis()
            if (curTs - lastSetProgressTs > 200) {
              Log.d(TAG, "importing progress: $importedBytes, $fileSize")
              lastSetProgressTs = curTs
              if (fileSize != 0L) {
                onProgress(importedBytes.toFloat() / fileSize.toFloat())
              }
            }
          }
        } }
    } catch (e: Exception) {
      Log.e(TAG, "Import failed during file copy", e)
      // Clean up partial .tmp file on failure
      if (!tmpFile.delete()) Log.w(TAG, "Failed to delete temp file: ${tmpFile.name}")
      onError(e.message ?: context.getString(R.string.error_import_failed))
      return@launch
    }
    // Atomic rename: only creates the final file if the copy completed successfully.
    // Delete existing file first — renameTo won't overwrite on most Android filesystems,
    // and a previous import of the same model name may have left a file here.
    if (finalFile.exists()) {
      if (!finalFile.delete()) Log.w(TAG, "Failed to delete existing file before rename: ${finalFile.name}")
    }
    if (!tmpFile.renameTo(finalFile)) {
      // renameTo can fail on some filesystems — fall back to copy + delete
      try {
        tmpFile.copyTo(finalFile, overwrite = true)
        if (!tmpFile.delete()) Log.w(TAG, "Failed to delete temp file after copy: ${tmpFile.name}")
      } catch (e: Exception) {
        if (!tmpFile.delete()) Log.w(TAG, "Failed to delete temp file: ${tmpFile.name}")
        onError(context.getString(R.string.error_import_finalize_failed, e.message ?: ""))
        return@launch
      }
    }
    Log.d(TAG, "import done")
    onProgress(1f)
    onDone()
  }
}

private fun getFileSizeAndDisplayNameFromUri(context: Context, uri: Uri): Pair<Long, String> {
  val contentResolver = context.contentResolver
  var fileSize = 0L
  var displayName = ""

  try {
    contentResolver
      .query(uri, arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        if (cursor.moveToFirst()) {
          val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
          fileSize = cursor.getLong(sizeIndex)

          val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
          displayName = cursor.getString(nameIndex)
        }
      }
  } catch (e: Exception) {
    Log.e(TAG, "Failed to query file size/name from URI", e)
    return Pair(0L, "")
  }

  return Pair(fileSize, displayName)
}
