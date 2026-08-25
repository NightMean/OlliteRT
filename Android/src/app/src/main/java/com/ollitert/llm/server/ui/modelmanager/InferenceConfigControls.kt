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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.Accelerator
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import java.util.Locale

@Composable
internal fun ParameterInputBox(
  label: String,
  value: String,
  onValueChange: (Number) -> Unit,
  min: Float,
  max: Float,
  isFloat: Boolean,
  keyboardType: KeyboardType,
  modifier: Modifier = Modifier,
  forceError: Boolean = false,
  onErrorStateChange: (Boolean) -> Unit = {},
  // Override the field fill when the box sits on a same-colored card — without
  // contrast the field boundary disappears into its container.
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
  // Draws a subtle outline so the field reads as an input inside a same-colored
  // card. Off by default: standalone boxes on the sheet already stand out.
  showBorder: Boolean = false,
) {
  val focusRequester = remember { FocusRequester() }
  val focusManager = LocalFocusManager.current
  var textValue by remember { mutableStateOf(value) }
  var isFocused by remember { mutableStateOf(false) }
  if (!isFocused && textValue != value) {
    textValue = value
  }

  // Flag values above max during typing — gives immediate feedback for clearly invalid input.
  // Below-min is not flagged live (user may still be typing digits), but is caught on Apply.
  val isAboveMax = remember(textValue, max, isFloat) {
    if (isFloat) {
      val parsed = textValue.toFloatOrNull()
      parsed != null && parsed > max
    } else {
      val parsed = textValue.toLongOrNull()
      parsed != null && parsed > max.toLong()
    }
  }

  val showError = isAboveMax || forceError

  // Notify parent of above-max error state changes
  val previousError = remember { mutableStateOf(false) }
  if (previousError.value != isAboveMax) {
    previousError.value = isAboveMax
    onErrorStateChange(isAboveMax)
  }

  fun commitValue() {
    val raw = textValue
    if (isFloat) {
      val parsed = raw.toFloatOrNull() ?: return
      val clamped = parsed.coerceIn(min, max)
      val formatted = if (clamped == clamped.toInt().toFloat()) clamped.toInt().toString()
        else String.format(Locale.US, "%.2f", clamped).trimEnd('0').trimEnd('.')
      textValue = formatted
      onValueChange(clamped)
      onErrorStateChange(false)
    } else {
      val parsed = raw.toLongOrNull() ?: return
      val clamped = parsed.coerceIn(min.toLong(), max.toLong()).toInt()
      textValue = clamped.toString()
      onValueChange(clamped)
      onErrorStateChange(false)
    }
  }

  val hint = if (isFloat) {
    "${if (min == min.toInt().toFloat()) min.toInt() else min}–${if (max == max.toInt().toFloat()) max.toInt() else max}"
  } else {
    "${min.toInt()}–${max.toInt()}"
  }

  val errorColor = MaterialTheme.colorScheme.error

  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (showError) errorColor else OlliteRTPrimary,
        letterSpacing = 1.sp,
      )
      Text(
        text = hint,
        style = MaterialTheme.typography.labelSmall,
        color = if (showError) errorColor else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .then(
          if (showError) Modifier.border(1.5.dp, errorColor, RoundedCornerShape(12.dp))
          else if (showBorder) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
          else Modifier
        )
        .background(containerColor)
        .clickable { focusRequester.requestFocus() }
        .padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      BasicTextField(
        value = textValue,
        onValueChange = { raw ->
          val allowed = if (isFloat) {
            val normalized = raw.replace(',', '.')
            val filtered = normalized.filter { it.isDigit() || it == '.' || it == '-' }
            val dotIndex = filtered.indexOf('.')
            if (dotIndex >= 0) {
              val afterDot = filtered.substring(dotIndex + 1).replace(".", "")
              filtered.substring(0, dotIndex + 1) + afterDot.take(2)
            } else filtered
          } else {
            raw.filter { it.isDigit() }
          }
          textValue = allowed
          if (isFloat) {
            allowed.toFloatOrNull()?.let { parsed ->
              if (parsed <= max) onValueChange(parsed)
            }
          } else {
            allowed.toLongOrNull()?.let { parsed ->
              if (parsed <= max.toLong()) onValueChange(parsed.toInt())
            }
          }
        },
        singleLine = true,
        textStyle = TextStyle(
          color = if (showError) errorColor else MaterialTheme.colorScheme.onSurface,
          fontSize = 14.sp,
          fontWeight = FontWeight.SemiBold,
          fontFamily = SpaceGroteskFontFamily,
        ),
        cursorBrush = SolidColor(if (showError) errorColor else OlliteRTPrimary),
        keyboardOptions = KeyboardOptions(
          keyboardType = keyboardType,
          imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
          onDone = {
            commitValue()
            focusManager.clearFocus()
          },
        ),
        modifier = Modifier
          .weight(1f)
          .focusRequester(focusRequester)
          .onFocusChanged { state ->
            isFocused = state.isFocused
            if (!state.isFocused) commitValue()
          },
      )
      Icon(
        Icons.Outlined.Edit,
        contentDescription = stringResource(R.string.cd_edit_field, label),
        tint = if (showError) errorColor else OlliteRTPrimary,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

@Composable
internal fun AcceleratorToggle(
  options: List<Accelerator>,
  selected: Accelerator,
  onSelect: (Accelerator) -> Unit,
  disabledOptions: Set<Accelerator> = emptySet(),
) {
  val segmentWidth = 70.dp
  val toggleWidth = segmentWidth * options.size
  val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
  val singleOption = options.size == 1

  val density = LocalDensity.current
  val offsetX by animateDpAsState(
    targetValue = segmentWidth * selectedIndex,
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
        .offset { IntOffset(with(density) { offsetX.roundToPx() }, 0) }
        .width(segmentWidth)
        .height(36.dp)
        .clip(RoundedCornerShape(50))
        .background(OlliteRTPrimary),
    )

    // Labels
    Row(modifier = Modifier.matchParentSize().selectableGroup()) {
      options.forEach { accelerator ->
        val isSelected = accelerator == selected
        val isDisabled = accelerator in disabledOptions
        Box(
          modifier = Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .then(
              if (singleOption || isDisabled) Modifier
              else Modifier.selectable(
                selected = isSelected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
              ) { onSelect(accelerator) }
            ),
          contentAlignment = Alignment.Center,
        ) {
          val textColor by animateColorAsState(
            targetValue = when {
              isDisabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
              isSelected -> Color.Black
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = tween(200),
            label = "accel_text_${accelerator.label}",
          )
          Text(
            text = accelerator.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor,
          )
        }
      }
    }
  }
}

@Composable
internal fun PromptTextArea(
  label: String,
  hint: String,
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = OlliteRTPrimary,
      letterSpacing = 1.sp,
    )
    Text(
      text = hint,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    BasicTextField(
      value = value,
      onValueChange = onValueChange,
      textStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        fontFamily = SpaceGroteskFontFamily,
      ),
      cursorBrush = SolidColor(OlliteRTPrimary),
      modifier = Modifier
        .fillMaxWidth()
        .height(100.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(12.dp),
      decorationBox = { innerTextField ->
        Box {
          if (value.isEmpty()) {
            Text(
              text = placeholder,
              style = TextStyle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontSize = 13.sp,
                fontFamily = SpaceGroteskFontFamily,
              ),
            )
          }
          innerTextField()
          // Clear button inside the text box — no container background to blend in
          if (value.isNotEmpty()) {
            IconButton(
              onClick = { onValueChange("") },
              modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp),
            ) {
              Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_clear),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      },
    )
  }
}
