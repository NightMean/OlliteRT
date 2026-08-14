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

package com.ollitert.llm.server.ui.server.logs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.RequestLogStore
import com.ollitert.llm.server.ui.theme.OlliteRTCancelledAmber
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTWarningYellow
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

/** Toggle an element in a set — add if absent, remove if present. */
fun <T> Set<T>.toggle(element: T): Set<T> =
  if (element in this) this - element else this + element

/**
 * A connected toggle bar — segments share a common rounded container with
 * [surfaceContainerHighest] background. Individual segments highlight with the
 * accent color when selected. Uses [segmentCount] to give each segment
 * position-aware corner rounding: only outer ends are rounded, inner
 * boundaries are flat so adjacent selected segments look seamless.
 */
@Composable
fun SegmentedToggleGroup(
  segmentCount: Int,
  modifier: Modifier = Modifier,
  content: @Composable RowScope.(segmentShape: (index: Int) -> Shape) -> Unit,
) {
  val r = 12.dp
  val shapeFor: (Int) -> Shape = { index ->
    when {
      segmentCount == 1 -> RoundedCornerShape(r)
      index == 0 -> RoundedCornerShape(topStart = r, bottomStart = r)
      index == segmentCount - 1 -> RoundedCornerShape(topEnd = r, bottomEnd = r)
      else -> RoundedCornerShape(0.dp)
    }
  }
  Row(
    modifier = modifier
      .height(32.dp)
      .clip(RoundedCornerShape(r))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    content(shapeFor)
  }
}

/**
 * A single segment within a [SegmentedToggleGroup]. Must be called inside a [RowScope].
 * Highlights with [accentColor] (defaults to [OlliteRTPrimary]) when [selected].
 */
@Composable
fun RowScope.SegmentItem(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  shape: Shape = RoundedCornerShape(0.dp),
  accentColor: Color? = null,
) {
  val selectedColor = accentColor ?: OlliteRTPrimary
  val bgColor by animateColorAsState(
    targetValue = if (selected) selectedColor else Color.Transparent,
    animationSpec = tween(150),
    label = "seg_bg",
  )

  Box(
    modifier = modifier
      .fillMaxHeight()
      .clip(shape)
      .background(bgColor)
      .toggleable(
        value = selected,
        role = Role.Switch,
        onValueChange = { onClick() },
      )
      .padding(horizontal = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = if (selected) {
        if (accentColor == OlliteRTWarningYellow) Color.Black else MaterialTheme.colorScheme.surface
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      fontFamily = SpaceGroteskFontFamily,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Pending response section — separated from [LogEntryCard] so that partial text changes
 * during streaming don't force recomposition of the entire card.
 */
@Composable
fun PendingResponseSection(
  entryId: String,
  partialText: String?,
  isGenerating: Boolean,
  onCancelRequest: (String) -> Unit = { RequestLogStore.cancelRequest(it) },
) {
  val pendingPartial by RequestLogStore.pendingPartialText.collectAsStateWithLifecycle()
  val liveText = if (pendingPartial.first == entryId) pendingPartial.second else partialText

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLowest)
      .padding(horizontal = 12.dp, vertical = 14.dp),
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(end = 34.dp)) {
      if (isGenerating) {
        val displayText = remember(liveText) {
          liveText?.replace("<think>", "")?.replace("</think>", "")?.trimStart()
        }
        if (!displayText.isNullOrEmpty()) {
          Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(10.dp))
        }
        GeneratingStatusRow(entryId = entryId)
      } else {
        QueuedStatusRow()
      }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    Box(modifier = Modifier.align(Alignment.TopEnd)) {
      TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(stringResource(R.string.logs_tooltip_stop_generation)) } },
        state = rememberTooltipState(),
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(OlliteRTCancelledAmber.copy(alpha = 0.15f))
            .clickable { onCancelRequest(entryId) },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.StopCircle,
            contentDescription = stringResource(R.string.logs_tooltip_stop_generation),
            tint = OlliteRTCancelledAmber,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

/** "Generating..." text + bouncing dots. */
@Composable
fun GeneratingStatusRow(entryId: String) {
  val generatingText = remember(entryId) { GeneratingMessages.pick() }
  Row(
    modifier = Modifier.defaultMinSize(minHeight = 28.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = generatingText,
      style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = SpaceGroteskFontFamily,
        fontSize = 11.sp,
      ),
      color = OlliteRTPrimary,
      fontWeight = FontWeight.SemiBold,
    )
    BouncingDots()
  }
}

@Composable
fun QueuedStatusRow() {
  Row(
    modifier = Modifier.defaultMinSize(minHeight = 28.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.HourglassEmpty,
      contentDescription = null,
      modifier = Modifier.size(14.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = stringResource(R.string.logs_entry_pending_in_queue),
      style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = SpaceGroteskFontFamily,
        fontSize = 11.sp,
      ),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
fun BouncingDots() {
  val transition = rememberInfiniteTransition(label = "dots")
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    repeat(3) { index ->
      val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
          animation = tween(durationMillis = 400, delayMillis = index * 150),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "dot$index",
      )
      Box(
        modifier = Modifier
          .size(6.dp)
          .offset { IntOffset(0, offsetY.dp.roundToPx()) }
          .background(OlliteRTPrimary, CircleShape),
      )
    }
  }
}
