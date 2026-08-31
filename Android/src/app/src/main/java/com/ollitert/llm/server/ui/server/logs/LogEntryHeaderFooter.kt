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

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.ui.server.CancelledColor
import com.ollitert.llm.server.ui.server.ThinkingColor
import com.ollitert.llm.server.ui.theme.OlliteRTOnSurfaceVariant
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay

/** The client IP address pill shown in the log entry card header. */
@Composable
internal fun EntryIpPill(entry: RequestLogEntry, searchQuery: String) {
  if (entry.clientIp == null) return
  if (searchQuery.isNotEmpty()) {
    val highlighted = remember(entry.clientIp, searchQuery) {
      buildHighlightedString(entry.clientIp, searchQuery, baseColor = OlliteRTOnSurfaceVariant)
    }
    Text(
      text = highlighted,
      style = MaterialTheme.typography.labelSmall,
      fontFamily = SpaceGroteskFontFamily,
      maxLines = 1,
      modifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .padding(horizontal = 8.dp, vertical = 3.dp),
    )
  } else {
    Text(
      text = entry.clientIp,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontFamily = SpaceGroteskFontFamily,
      maxLines = 1,
      modifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

/**
 * The ⓘ and copy action buttons shown in the log entry card header.
 * These always stay in the top-right corner regardless of whether the path wraps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryActionButtons(
  entry: RequestLogEntry,
  hasInfoButton: Boolean,
  hasCopyButton: Boolean,
  onInfoClick: () -> Unit,
) {
  val context = LocalContext.current
  if (hasInfoButton) {
    Spacer(modifier = Modifier.width(2.dp))
    TooltipBox(
      positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
      tooltip = { PlainTooltip { Text(stringResource(R.string.logs_tooltip_request_metrics)) } },
      state = rememberTooltipState(),
    ) {
      IconButton(onClick = onInfoClick, modifier = Modifier.size(32.dp)) {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = stringResource(R.string.logs_tooltip_request_metrics),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
  if (hasCopyButton) {
    Spacer(modifier = Modifier.width(2.dp))
    TooltipBox(
      positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
      tooltip = { PlainTooltip { Text(stringResource(R.string.logs_tooltip_copy_entry)) } },
      state = rememberTooltipState(),
    ) {
      IconButton(
        onClick = { copyEntryToClipboard(context, entry) },
        modifier = Modifier.size(32.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.ContentCopy,
          contentDescription = stringResource(R.string.logs_tooltip_copy_entry),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

/**
 * The badge items that appear in the log entry card footer.
 */
@Composable
internal fun FooterBadges(entry: RequestLogEntry, contextOverflow: Boolean) {
  StatusBadge(statusCode = entry.statusCode, contextOverflow = contextOverflow, errorKind = entry.errorKind)
  FooterDot()
  Text(
    text = "${entry.latencyMs}ms",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
  if (entry.isStreaming) {
    FooterDot()
    Text(
      text = stringResource(R.string.logs_badge_sse),
      style = MaterialTheme.typography.labelSmall,
      color = OlliteRTPrimary,
      fontWeight = FontWeight.SemiBold,
    )
  }
  if (entry.hasToolCalls) {
    FooterDot()
    Icon(
      imageVector = Icons.Outlined.Construction,
      contentDescription = stringResource(R.string.logs_badge_tool_call),
      tint = OlliteRTPrimary,
      modifier = Modifier.size(14.dp),
    )
  }
  if (entry.isThinking) {
    FooterDot()
    Text(
      text = stringResource(R.string.logs_badge_thinking),
      style = MaterialTheme.typography.labelSmall,
      color = ThinkingColor,
      fontWeight = FontWeight.SemiBold,
    )
  }
  if (entry.isCancelled) {
    FooterDot()
    Text(
      text = stringResource(R.string.logs_badge_cancelled),
      style = MaterialTheme.typography.labelSmall,
      color = CancelledColor,
      fontWeight = FontWeight.SemiBold,
    )
  }
  if (entry.inputTokenEstimate > 0 && entry.maxContextTokens > 0) {
    val utilRatio = entry.inputTokenEstimate.toDouble() / entry.maxContextTokens.toDouble()
    FooterDot()
    Text(
      text = stringResource(R.string.logs_badge_ctx_format, if (entry.isExactTokenCount) "" else "~", entry.inputTokenEstimate, entry.maxContextTokens),
      style = MaterialTheme.typography.labelSmall,
      color = contextUtilizationColor(utilRatio),
    )
  }
}

/**
 * Uses the request's persisted start timestamp while the entry remains pending.
 * Leaving the pending branch removes this composable and cancels its ticker, so
 * completed cards immediately return to their normal status and latency badges.
 */
@Composable
internal fun PendingLogFooter(startTimestampMs: Long, modelTimeText: String) {
  var currentTimeMs by remember(startTimestampMs) { mutableLongStateOf(System.currentTimeMillis()) }

  LaunchedEffect(startTimestampMs) {
    while (true) {
      currentTimeMs = System.currentTimeMillis()
      delay(1_000L)
    }
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(
        R.string.logs_processing_elapsed_seconds,
        elapsedProcessingSeconds(startTimestampMs, currentTimeMs),
      ),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.weight(1f))
    FooterModelTime(modelTimeText)
  }
}

internal fun elapsedProcessingSeconds(startTimestampMs: Long, currentTimeMs: Long): Long =
  ((currentTimeMs - startTimestampMs).coerceAtLeast(0L)) / 1_000L

/**
 * Keeps model and timestamp metadata pinned while badges fit, then scrolls the
 * complete footer as one row when the badge region no longer fits.
 */
@Composable
internal fun ResponsiveLogFooter(
  modelTimeText: String,
  badges: @Composable RowScope.() -> Unit,
) {
  val scrollState = rememberScrollState()
  val isOverflowing = scrollState.maxValue > 0

  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (!isOverflowing) {
      Row(
        modifier = Modifier
          .weight(1f)
          .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = badges,
      )
      Spacer(modifier = Modifier.width(6.dp))
      FooterModelTime(modelTimeText)
    } else {
      Row(
        modifier = Modifier.horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        badges()
        FooterDot()
        FooterModelTime(modelTimeText)
      }
    }
  }
}

@Composable
private fun FooterModelTime(modelTimeText: String) {
  Text(
    text = modelTimeText,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
  )
}

internal fun buildAnnotatedRequestLabel(
  baseLabel: String,
  toolCallLabel: String,
  baseColor: Color,
  toolCallColor: Color,
): AnnotatedString {
  return buildAnnotatedString {
    pushStyle(SpanStyle(color = baseColor))
    append("$baseLabel · ")
    pop()
    pushStyle(SpanStyle(color = toolCallColor))
    append(toolCallLabel)
    pop()
  }
}
