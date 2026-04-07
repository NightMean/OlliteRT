package com.ollitert.llm.server.ui.server

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
  val configValues = model.configValues
  val focusManager = LocalFocusManager.current

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
      (configValues[ConfigKeys.ENABLE_THINKING.label] as? Boolean) ?: true
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
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Inference Settings",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
          Icons.Outlined.Tune,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(24.dp),
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Temperature & Max Tokens row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ParameterInputBox(
          label = "TEMPERATURE",
          value = "%.1f".format(temperature),
          onValueChange = { text ->
            text.toFloatOrNull()?.let { v ->
              temperature = v.coerceIn(0f, 2f)
            }
          },
          keyboardType = KeyboardType.Decimal,
          modifier = Modifier.weight(1f),
        )
        ParameterInputBox(
          label = "MAX TOKENS",
          value = maxTokens.toString(),
          onValueChange = { text ->
            text.toIntOrNull()?.let { v ->
              maxTokens = v.coerceIn(1, 8192)
            }
          },
          keyboardType = KeyboardType.Number,
          modifier = Modifier.weight(1f),
        )
      }

      // Top-K & Top-P row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ParameterInputBox(
          label = "TOP-K",
          value = topK.toString(),
          onValueChange = { text ->
            text.toIntOrNull()?.let { v ->
              topK = v.coerceIn(1, 100)
            }
          },
          keyboardType = KeyboardType.Number,
          modifier = Modifier.weight(1f),
        )
        ParameterInputBox(
          label = "TOP-P",
          value = "%.1f".format(topP),
          onValueChange = { text ->
            text.toFloatOrNull()?.let { v ->
              topP = v.coerceIn(0f, 1f)
            }
          },
          keyboardType = KeyboardType.Decimal,
          modifier = Modifier.weight(1f),
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Enable Thinking toggle in a container
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Outlined.Psychology,
          contentDescription = null,
          tint = OlliteRTPrimary,
          modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Allow Reasoning",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "When off, the API reports reasoning as unavailable and clients won't use it",
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

      // Accelerator toggle in a container
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Accelerator",
          style = MaterialTheme.typography.bodyLarge,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
        // Segmented toggle: GPU | CPU
        AcceleratorToggle(
          useGpu = useGpu,
          onToggle = { useGpu = it },
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Apply button
      Button(
        onClick = {
          focusManager.clearFocus()
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
private fun ParameterInputBox(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  keyboardType: KeyboardType,
  modifier: Modifier = Modifier,
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  var textValue by remember(value) { mutableStateOf(value) }

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = OlliteRTPrimary,
      letterSpacing = 1.sp,
    )
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .clickable { focusRequester.requestFocus() }
        .padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BasicTextField(
        value = textValue,
        onValueChange = { newText ->
          textValue = newText
          onValueChange(newText)
        },
        singleLine = true,
        textStyle = TextStyle(
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 18.sp,
          fontWeight = FontWeight.SemiBold,
          fontFamily = SpaceGroteskFontFamily,
        ),
        cursorBrush = SolidColor(OlliteRTPrimary),
        keyboardOptions = KeyboardOptions(
          keyboardType = keyboardType,
          imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
          onDone = { focusManager.clearFocus() },
        ),
        modifier = Modifier
          .weight(1f)
          .focusRequester(focusRequester),
      )
      Icon(
        Icons.Outlined.Edit,
        contentDescription = "Edit $label",
        tint = OlliteRTPrimary,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
private fun AcceleratorToggle(
  useGpu: Boolean,
  onToggle: (Boolean) -> Unit,
) {
  val toggleWidth = 140.dp
  val halfWidth = toggleWidth / 2

  val offsetX by animateDpAsState(
    targetValue = if (useGpu) 0.dp else halfWidth,
    animationSpec = tween(200),
    label = "toggle_offset",
  )

  Box(
    modifier = Modifier
      .width(toggleWidth)
      .height(36.dp)
      .clip(RoundedCornerShape(50))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest),
  ) {
    // Sliding indicator
    Box(
      modifier = Modifier
        .offset(x = offsetX)
        .width(halfWidth)
        .height(36.dp)
        .clip(RoundedCornerShape(50))
        .background(OlliteRTPrimary),
    )

    // Labels
    Row(modifier = Modifier.matchParentSize()) {
      Box(
        modifier = Modifier
          .weight(1f)
          .height(36.dp)
          .clip(RoundedCornerShape(50))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) { onToggle(true) },
        contentAlignment = Alignment.Center,
      ) {
        val gpuTextColor by animateColorAsState(
          targetValue = if (useGpu) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
          animationSpec = tween(200),
          label = "gpu_text",
        )
        Text(
          text = "GPU",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = gpuTextColor,
        )
      }
      Box(
        modifier = Modifier
          .weight(1f)
          .height(36.dp)
          .clip(RoundedCornerShape(50))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
          ) { onToggle(false) },
        contentAlignment = Alignment.Center,
      ) {
        val cpuTextColor by animateColorAsState(
          targetValue = if (!useGpu) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
          animationSpec = tween(200),
          label = "cpu_text",
        )
        Text(
          text = "CPU",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = cpuTextColor,
        )
      }
    }
  }
}
