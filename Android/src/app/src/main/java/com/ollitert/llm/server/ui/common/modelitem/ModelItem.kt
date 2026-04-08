/*
 * Copyright 2025 Google LLC
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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.ModelDownloadStatusType
import com.ollitert.llm.server.data.Task
import com.ollitert.llm.server.ui.common.MarkdownText
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import com.ollitert.llm.server.ui.navigation.ServerStatus
import androidx.compose.ui.platform.LocalContext
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.service.LlmHttpService
import android.widget.Toast
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.server.InferenceSettingsSheet
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.customColors

/**
 * Composable function to display a model item in the model manager list.
 *
 * This function renders a card representing a model, displaying its task icon, name, download
 * status, and providing action buttons including download/try and settings (for active models).
 */
@Composable
fun ModelItem(
  model: Model,
  task: Task?,
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
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val downloadStatus by remember {
    derivedStateOf { modelManagerUiState.modelDownloadStatus[model.name] }
  }

  val isServerRunning = serverStatus == ServerStatus.RUNNING
  val isModelLoading = serverStatus == ServerStatus.LOADING && activeModelName == model.name
  val isModelError = serverStatus == ServerStatus.ERROR && activeModelName == model.name
  val isActiveModel = isServerRunning && activeModelName == model.name

  var showInferenceSettings by remember { mutableStateOf(false) }

  var boxModifier =
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(size = 12.dp))
      .background(color = MaterialTheme.customColors.taskCardBgColor)

  // Imported models are clickable to select them
  if (model.imported && !showBenchmarkButton) {
    boxModifier = boxModifier.clickable { onModelClicked(model) }
  }

  Box(modifier = boxModifier) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      // Model name and status
      ModelNameAndStatus(
        model = model,
        task = task,
        downloadStatus = downloadStatus,
        modifier = Modifier.fillMaxWidth(),
      )

      // Description
      if (!model.imported && model.info.isNotEmpty()) {
        MarkdownText(
          model.info,
          smallFontSize = true,
          textColor = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      // Download / action panel
      DownloadModelPanel(
        task = task,
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
        val errorText = if (!lastError.isNullOrBlank()) {
          if (lastError.length > 80) "Failed to load model — check Logs for details"
          else "Failed to load: $lastError"
        } else {
          "Failed to load model"
        }
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
          text = "Sit tight — this may take a couple of minutes depending on your device",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
      }
    }

    // Action icons overlaid at top-right of card
    Row(
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
      // Settings cog - only for running model
      if (isActiveModel) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { showInferenceSettings = true },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Outlined.Settings,
            contentDescription = "Inference settings",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
          )
        }
      }
    }
  }

  // Inference Settings bottom sheet
  if (showInferenceSettings) {
    val context = LocalContext.current
    InferenceSettingsSheet(
      model = model,
      onDismiss = { showInferenceSettings = false },
      onApply = { newConfigValues, systemPrompt, chatTemplate ->
        // Persist system prompt and chat template for this model
        val oldSystemPrompt = LlmHttpPrefs.getSystemPrompt(context, model.name)
        val oldChatTemplate = LlmHttpPrefs.getChatTemplate(context, model.name)
        LlmHttpPrefs.setSystemPrompt(context, model.name, systemPrompt)
        LlmHttpPrefs.setChatTemplate(context, model.name, chatTemplate)
        val promptsChanged = systemPrompt != oldSystemPrompt || chatTemplate != oldChatTemplate

        // Detect changed configs and whether reinitialization is needed.
        // Normalize both sides to the config's target type before comparing,
        // so that e.g. String "4096" vs Int 4096 are not flagged as phantom changes.
        var needReinitialization = false
        val changes = mutableListOf<String>()
        for (config in model.configs) {
          val key = config.key.label
          val oldValue = model.configValues[key]?.let {
            com.ollitert.llm.server.data.convertValueToTargetType(it, config.valueType)
          }
          val newValue = newConfigValues[key]?.let {
            com.ollitert.llm.server.data.convertValueToTargetType(it, config.valueType)
          }
          if (oldValue != newValue) {
            changes.add("${config.key.label}: $oldValue → $newValue")
            if (config.needReinitialization) {
              needReinitialization = true
            }
          }
        }
        // System prompt change requires conversation reset (treated as reinitialization)
        if (promptsChanged) {
          if (systemPrompt != oldSystemPrompt) changes.add("system_prompt: changed")
          if (chatTemplate != oldChatTemplate) changes.add("chat_template: changed")
          needReinitialization = true
        }

        model.prevConfigValues = model.configValues
        model.configValues = newConfigValues
        modelManagerViewModel.updateConfigValuesUpdateTrigger()

        // Log config changes and trigger model reload if needed.
        // Treat the model as "active" if it's running, loading, or in error state —
        // in all cases the server has this model loaded (or is loading it) and needs a reload.
        val isServerActiveForModel = activeModelName == model.name &&
          serverStatus != ServerStatus.STOPPED
        if (changes.isNotEmpty() && isServerActiveForModel) {
          val changesSummary = changes.joinToString(", ")
          RequestLogStore.addEvent(
            "Inference settings changed: $changesSummary" +
              if (needReinitialization) " — reloading model" else "",
            modelName = model.name,
          )
          if (needReinitialization) {
            val port = LlmHttpPrefs.getPort(context)
            LlmHttpService.reload(context, port, model.name, configValues = newConfigValues)
            Toast.makeText(context, "Settings saved — model is reloading", Toast.LENGTH_SHORT).show()
          } else {
            // Push config changes to the running service model without reloading
            LlmHttpService.updateConfigValues(newConfigValues)
            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
          }
        } else if (changes.isNotEmpty()) {
          Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
        }

        showInferenceSettings = false
      },
    )
  }
}
