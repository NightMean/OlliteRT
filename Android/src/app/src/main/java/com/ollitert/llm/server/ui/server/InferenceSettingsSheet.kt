package com.ollitert.llm.server.ui.server

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.data.ConfigKeys
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InferenceSettingsSheet(
  model: Model,
  onDismiss: () -> Unit,
  onApply: (Map<String, Any>) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  // Read current values from model configValues
  val configValues = model.configValues

  var temperature by remember {
    mutableFloatStateOf(
      (configValues[ConfigKeys.TEMPERATURE.label] as? Number)?.toFloat() ?: 1.0f
    )
  }
  var maxTokens by remember {
    mutableIntStateOf(
      (configValues[ConfigKeys.MAX_TOKENS.label] as? Number)?.toInt() ?: 1024
    )
  }
  var topK by remember {
    mutableIntStateOf(
      (configValues[ConfigKeys.TOPK.label] as? Number)?.toInt() ?: 40
    )
  }
  var topP by remember {
    mutableFloatStateOf(
      (configValues[ConfigKeys.TOPP.label] as? Number)?.toFloat() ?: 0.95f
    )
  }
  var enableThinking by remember {
    mutableStateOf(
      (configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean) ?: false
    )
  }
  var useGpu by remember {
    mutableStateOf(
      configValues[ConfigKeys.ACCELERATOR.label]?.toString()?.contains("GPU", ignoreCase = true) ?: true
    )
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      // Header
      Text(
        text = "Inference Settings",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = "Parameters for ${model.name}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Temperature slider
      ParameterRow(label = "Temperature", value = "%.1f".format(temperature)) {
        Slider(
          value = temperature,
          onValueChange = { temperature = it },
          valueRange = 0f..2f,
          steps = 19,
          colors = SliderDefaults.colors(
            thumbColor = OlliteRTPrimary,
            activeTrackColor = OlliteRTPrimary,
          ),
        )
      }

      // Max Tokens slider
      ParameterRow(label = "Max Tokens", value = maxTokens.toString()) {
        Slider(
          value = maxTokens.toFloat(),
          onValueChange = { maxTokens = it.toInt() },
          valueRange = 64f..4096f,
          steps = 62,
          colors = SliderDefaults.colors(
            thumbColor = OlliteRTPrimary,
            activeTrackColor = OlliteRTPrimary,
          ),
        )
      }

      // Top-K slider
      ParameterRow(label = "Top-K", value = topK.toString()) {
        Slider(
          value = topK.toFloat(),
          onValueChange = { topK = it.toInt() },
          valueRange = 1f..100f,
          steps = 98,
          colors = SliderDefaults.colors(
            thumbColor = OlliteRTPrimary,
            activeTrackColor = OlliteRTPrimary,
          ),
        )
      }

      // Top-P slider
      ParameterRow(label = "Top-P", value = "%.2f".format(topP)) {
        Slider(
          value = topP,
          onValueChange = { topP = it },
          valueRange = 0f..1f,
          steps = 19,
          colors = SliderDefaults.colors(
            thumbColor = OlliteRTPrimary,
            activeTrackColor = OlliteRTPrimary,
          ),
        )
      }

      // Enable Thinking toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text(
            text = "Enable Thinking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Allow the model to reason step-by-step",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = enableThinking,
          onCheckedChange = { enableThinking = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }

      // Accelerator selector
      Column {
        Text(
          text = "Accelerator",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AcceleratorChip(
            label = "GPU",
            selected = useGpu,
            onClick = { useGpu = true },
            modifier = Modifier.weight(1f),
          )
          AcceleratorChip(
            label = "CPU",
            selected = !useGpu,
            onClick = { useGpu = false },
            modifier = Modifier.weight(1f),
          )
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Apply button
      Button(
        onClick = {
          val newValues = mutableMapOf<String, Any>()
          newValues.putAll(configValues)
          newValues[ConfigKeys.TEMPERATURE.label] = temperature
          newValues[ConfigKeys.MAX_TOKENS.label] = maxTokens
          newValues[ConfigKeys.TOPK.label] = topK
          newValues[ConfigKeys.TOPP.label] = topP
          newValues[ConfigKeys.ENABLE_THINKING.label] = enableThinking
          newValues[ConfigKeys.ACCELERATOR.label] = if (useGpu) "GPU" else "CPU"
          onApply(newValues)
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = OlliteRTPrimary),
      ) {
        Text(
          text = "Save & Apply Configuration",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun ParameterRow(
  label: String,
  value: String,
  slider: @Composable () -> Unit,
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = OlliteRTPrimary,
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
      )
    }
    slider()
  }
}

@Composable
private fun AcceleratorChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val bgColor = if (selected) OlliteRTPrimary.copy(alpha = 0.15f)
  else MaterialTheme.colorScheme.surfaceContainerHighest
  val textColor = if (selected) OlliteRTPrimary
  else MaterialTheme.colorScheme.onSurfaceVariant

  Button(
    onClick = onClick,
    modifier = modifier.height(44.dp),
    shape = RoundedCornerShape(50),
    colors = ButtonDefaults.buttonColors(containerColor = bgColor),
  ) {
    Text(
      text = label,
      color = textColor,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
    )
  }
}
