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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.Accelerator
import com.ollitert.llm.server.data.prefs.ConfigKeys
import com.ollitert.llm.server.data.prefs.configSpeculativeDecodingEnabled
import com.ollitert.llm.server.data.prefs.configTemperature
import com.ollitert.llm.server.data.prefs.configThinkingEnabled
import com.ollitert.llm.server.data.prefs.configTopK
import com.ollitert.llm.server.data.prefs.configTopP
import com.ollitert.llm.server.data.prefs.maxTokensInt

@Composable
internal fun InferenceResetConfirmDialog(
  defaults: Map<String, Any>,
  configValues: Map<String, Any>,
  availableAccelerators: List<Accelerator>,
  gpuAccessible: Boolean,
  onDismiss: () -> Unit,
  onResetConfirmed: (
    temperature: Float,
    maxTokens: Int,
    topK: Int,
    topP: Float,
    enableThinking: Boolean,
    enableSpeculativeDecoding: Boolean,
    selectedAccelerator: Accelerator,
    newValues: Map<String, Any>,
  ) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.dialog_reset_inference_title)) },
    text = { Text(stringResource(R.string.dialog_reset_inference_body)) },
    confirmButton = {
      Button(onClick = {
        val defTemp = defaults.configTemperature() ?: 1.0f
        val defMaxTokens = defaults.maxTokensInt() ?: 1024
        val defTopK = defaults.configTopK() ?: 40
        val defTopP = defaults.configTopP() ?: 0.95f
        val defThinking = defaults.configThinkingEnabled() ?: false
        val defSpecDec = defaults.configSpeculativeDecodingEnabled() ?: false
        val defaultAcc = defaults[ConfigKeys.ACCELERATOR.id]?.toString() ?: ""
        val defAccelerator = availableAccelerators.find { it.label.equals(defaultAcc, ignoreCase = true) }
          ?: availableAccelerators.first()
        val effectiveAcc = if (defAccelerator == Accelerator.GPU && !gpuAccessible) {
          availableAccelerators.find { it == Accelerator.CPU } ?: defAccelerator
        } else {
          defAccelerator
        }
        val newValues = mutableMapOf<String, Any>()
        newValues.putAll(configValues)
        newValues[ConfigKeys.TEMPERATURE.id] = defTemp
        newValues[ConfigKeys.MAX_TOKENS.id] = defMaxTokens
        newValues[ConfigKeys.TOPK.id] = defTopK
        newValues[ConfigKeys.TOPP.id] = defTopP
        newValues[ConfigKeys.ENABLE_THINKING.id] = defThinking
        newValues[ConfigKeys.ENABLE_SPECULATIVE_DECODING.id] = defSpecDec
        newValues[ConfigKeys.ACCELERATOR.id] = defAccelerator.label

        onResetConfirmed(
          defTemp,
          defMaxTokens,
          defTopK,
          defTopP,
          defThinking,
          defSpecDec,
          effectiveAcc,
          newValues,
        )
      }) {
        Text(stringResource(R.string.button_reset))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.cancel))
      }
    },
  )
}
