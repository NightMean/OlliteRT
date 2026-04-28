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

package com.ollitert.llm.server.ui.common.modelitem

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.ServerService
import com.ollitert.llm.server.service.RequestLogStore
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.ollitert.llm.server.ui.common.MarkdownText
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.formatModelError
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.ui.server.InferenceSettingsSheet
import com.ollitert.llm.server.ui.theme.customColors

/**
 * Composable function to display a model item in the model manager list.
 *
 * This function renders a card representing a model, displaying its name, download
 * status, and providing action buttons including download/try and settings (for active models).
 */
@Composable
fun ModelItem(
  model: Model,
  modelManagerViewModel: ModelManagerViewModel,
  onModelClicked: (Model) -> Unit,
  onBenchmarkClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
  showDeleteButton: Boolean = true,
  showBenchmarkButton: Boolean = false,
  serverStatus: ServerStatus = ServerStatus.STOPPED,
  activeModelName: String? = null,
  lastError: String? = null,
  onStopServer: () -> Unit = {},
  onNavigateToSettings: () -> Unit = {},
  searchQuery: String = "",
  showRecommendations: Boolean = true,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsStateWithLifecycle()
  val downloadStatus by remember {
    derivedStateOf { modelManagerUiState.modelDownloadStatus[model.name] }
  }

  val isIncompatible = model.incompatibilityReason != null
  val isServerRunning = serverStatus == ServerStatus.RUNNING
  val isModelLoading = serverStatus == ServerStatus.LOADING && activeModelName == model.name
  val isModelError = serverStatus == ServerStatus.ERROR && activeModelName == model.name
  val isActiveModel = isServerRunning && activeModelName == model.name

  var showInferenceSettings by remember { mutableStateOf(false) }
  var showEditDefaults by remember { mutableStateOf(false) }

  var boxModifier =
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(size = 12.dp))
      .background(color = MaterialTheme.customColors.modelCardBgColor)

  // Imported models are clickable to select them
  if (model.imported && !showBenchmarkButton && !isIncompatible) {
    boxModifier = boxModifier.clickable { onModelClicked(model) }
  }

  Box(modifier = boxModifier.graphicsLayer { alpha = if (isIncompatible) 0.45f else 1f }) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // Model name and status
      ModelNameAndStatus(
        model = model,
        downloadStatus = downloadStatus,
        searchQuery = searchQuery,
        showRecommendations = showRecommendations,
        modifier = Modifier.fillMaxWidth(),
      )

      if (!model.imported && model.info.isNotEmpty()) {
        MarkdownText(
          model.info,
          smallFontSize = true,
          textColor = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
          searchQuery = searchQuery,
        )
      }

      // Download / action panel (hidden for incompatible models)
      if (isIncompatible) {
        Text(
          model.incompatibilityReason.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
      } else DownloadModelPanel(
        model = model,
        downloadStatus = downloadStatus,
        modifier = Modifier.padding(top = 4.dp),
        modelManagerViewModel = modelManagerViewModel,
        onTryItClicked = { onModelClicked(model) },
        onBenchmarkClicked = { onBenchmarkClicked(model) },
        onNavigateToSettings = onNavigateToSettings,
        showBenchmarkButton = showBenchmarkButton,
        serverStatus = serverStatus,
        activeModelName = activeModelName,
        onStopServer = onStopServer,
      )

      // Loading hint / error text
      if (isModelError) {
        val errorText = formatModelError(LocalContext.current, lastError)
        Text(
          text = errorText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          textAlign = TextAlign.Center,
          maxLines = 2,
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
      } else if (isModelLoading) {
        Text(
          text = stringResource(R.string.model_loading_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
      }
    }

    // Action icons overlaid at top-right of card (hidden for incompatible models)
    if (!isIncompatible) Row(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (model.localFileRelativeDirPathOverride.isEmpty()) {
        DeleteModelButton(
          model = model,
          modelManagerViewModel = modelManagerViewModel,
          downloadStatus = downloadStatus,
          showDeleteButton = showDeleteButton,
          isModelInUse = isActiveModel || isModelLoading,
        )
      }
      // Settings cog - available for any downloaded model (not just running)
      if (isActiveModel || isModelLoading || downloadStatus?.status == ModelDownloadStatusType.SUCCEEDED || model.imported) {
        TooltipIconButton(
          icon = Icons.Outlined.Settings,
          tooltip = stringResource(R.string.model_tooltip_inference_settings),
          onClick = { showInferenceSettings = true },
        )
      }
    }
  }

  // Edit imported model defaults dialog
  if (showEditDefaults && model.imported) {
    var existingInfo by remember { mutableStateOf<com.ollitert.llm.server.proto.ImportedModel?>(null) }
    LaunchedEffect(model.name) {
      existingInfo = modelManagerViewModel.dataStoreRepository.readImportedModels()
        .firstOrNull { it.fileName == model.name }
    }
    val loadedInfo = existingInfo
    if (loadedInfo != null) {
      com.ollitert.llm.server.ui.modelmanager.EditImportedModelDialog(
        existingModel = loadedInfo,
        isCurrentlyActive = activeModelName == model.name && serverStatus != ServerStatus.STOPPED,
        onDismiss = { showEditDefaults = false },
        onDone = { updated ->
          modelManagerViewModel.updateImportedModelDefaults(updated)
          showEditDefaults = false
          showInferenceSettings = false
        },
      )
    }
  }

  // Inference Settings bottom sheet
  if (showInferenceSettings) {
    val context = LocalContext.current
    val settingsSavedReloadPendingText = stringResource(R.string.toast_settings_saved_reload_pending)
    val settingsSavedReloadingText = stringResource(R.string.toast_settings_saved_reloading)
    val settingsSavedText = stringResource(R.string.toast_settings_saved)
    val configDisplayLabels = model.configs.associate { it.key.id to stringResource(it.key.labelResId) }
    InferenceSettingsSheet(
      model = model,
      onDismiss = { showInferenceSettings = false },
      onEditDefaults = if (model.imported) {
        { showEditDefaults = true }
      } else null,
      onApply = { newConfigValues, systemPrompt ->
        // Persist system prompt for this model
        val oldSystemPrompt = ServerPrefs.getSystemPrompt(context, model.prefsKey)
        ServerPrefs.setSystemPrompt(context, model.prefsKey, systemPrompt)
        val promptsChanged = systemPrompt != oldSystemPrompt

        // Detect changed configs and whether reinitialization is needed.
        // Normalize both sides to the config's target type before comparing,
        // so that e.g. String "4096" vs Int 4096 are not flagged as phantom changes.
        var needReinitialization = false
        val changes = mutableListOf<String>()
        for (config in model.configs) {
          val key = config.key.id
          val oldValue = model.configValues[key]?.let {
            com.ollitert.llm.server.data.convertValueToTargetType(it, config.valueType)
          }
          val newValue = newConfigValues[key]?.let {
            com.ollitert.llm.server.data.convertValueToTargetType(it, config.valueType)
          }
          if (oldValue != newValue) {
            changes.add("${configDisplayLabels[config.key.id] ?: config.key.label}: $oldValue → $newValue")
            if (config.needReinitialization) {
              needReinitialization = true
            }
          }
        }
        // System prompt changes are picked up automatically via buildSystemInstruction()
        // which reads from prefs on every resetConversation() call. No model reload needed.
        if (promptsChanged) {
          changes.add("system_prompt: changed")
        }

        val prevConfigValues = model.configValues
        model.configValues = newConfigValues.toMap()
        // Persist inference config so it survives app restarts
        ServerPrefs.setInferenceConfig(context, model.prefsKey, newConfigValues)
        modelManagerViewModel.updateConfigValuesUpdateTrigger()

        // Log config changes and trigger model reload if needed.
        // Treat the model as "active" if it's running, loading, or in error state —
        // in all cases the server has this model loaded (or is loading it) and needs a reload.
        val isServerActiveForModel = activeModelName == model.name &&
          serverStatus != ServerStatus.STOPPED
        if (changes.isNotEmpty() && isServerActiveForModel) {
          val changesSummary = changes.joinToString(", ")
          val isLoading = serverStatus == ServerStatus.LOADING
          // Build structured JSON body for the log card.
          // Schema: {"type":"inference_settings","changes":[{"param":"TopK","old":"14","new":"15"},...],
          //          "prompt_diffs":{"system_prompt":{"old":"...","new":"..."},...},
          //          "status":"reloading model"}
          val eventBody = buildJsonObject {
            put("type", "inference_settings")
            put("changes", buildJsonArray {
              for (config in model.configs) {
                val key = config.key.id
                val oldValue = prevConfigValues[key]?.let {
                  com.ollitert.llm.server.data.convertValueToTargetType(it, config.valueType)
                }
                val newValue = newConfigValues[key]?.let {
                  com.ollitert.llm.server.data.convertValueToTargetType(it, config.valueType)
                }
                if (oldValue != newValue) {
                  add(buildJsonObject {
                    put("param", config.key.label)
                    put("old", oldValue?.toString() ?: "")
                    put("new", newValue?.toString() ?: "")
                  })
                }
              }
            })
            val promptDiffs = buildJsonObject {
              if (systemPrompt != oldSystemPrompt) {
                put("system_prompt", buildJsonObject {
                  put("old", oldSystemPrompt)
                  put("new", systemPrompt)
                })
              }
            }
            if (promptDiffs.isNotEmpty()) put("prompt_diffs", promptDiffs)
            val status = when {
              needReinitialization && isLoading -> "reload queued after loading"
              needReinitialization -> "reloading model"
              else -> null
            }
            if (status != null) put("status", status)
          }
          RequestLogStore.addEvent(
            "Inference settings changed: $changesSummary" +
              if (needReinitialization && isLoading) " — reload queued after loading"
              else if (needReinitialization) " — reloading model"
              else "",
            modelName = model.name,
            category = EventCategory.MODEL,
            body = eventBody.toString(),
          )
          if (needReinitialization) {
            val port = ServerPrefs.getPort(context)
            if (isLoading) {
              ServerService.queueReloadAfterLoad(port, model.name, newConfigValues)
              Toast.makeText(context, settingsSavedReloadPendingText, Toast.LENGTH_SHORT).show()
            } else {
              ServerService.reload(context, port, model.name, configValues = newConfigValues)
              Toast.makeText(context, settingsSavedReloadingText, Toast.LENGTH_SHORT).show()
            }
          } else {
            // Push config changes to the running service model without reloading
            ServerService.updateConfigValues(newConfigValues)
            Toast.makeText(context, settingsSavedText, Toast.LENGTH_SHORT).show()
          }
        } else if (changes.isNotEmpty()) {
          Toast.makeText(context, settingsSavedText, Toast.LENGTH_SHORT).show()
        }

        showInferenceSettings = false
      },
    )
  }
}
