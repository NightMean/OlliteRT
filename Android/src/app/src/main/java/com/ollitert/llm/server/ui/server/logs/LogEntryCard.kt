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

import com.ollitert.llm.server.common.formatByteSize
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.common.copyToClipboard
import com.ollitert.llm.server.ui.server.ContextOverflowColor
import com.ollitert.llm.server.ui.server.TruncatedColor
import com.ollitert.llm.server.ui.server.CancelledColor
import com.ollitert.llm.server.ui.server.ThinkingColor
import com.ollitert.llm.server.ui.server.WarningColor
import com.ollitert.llm.server.ui.server.PendingResponseSection
import com.ollitert.llm.server.ui.server.isContextOverflowError
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val COLLAPSED_MAX_LINES = 8
internal const val COLLAPSED_MAX_CHARS = 600
/**
 * Bodies above this size get highlighted asynchronously (off main thread) to avoid jank.
 * At 1KB, regex-based JSON highlighting can take 10-30ms on mid-range devices — enough
 * to drop frames during scrolling. Larger bodies (2-4KB) can take 50-100ms.
 */
internal const val ASYNC_HIGHLIGHT_THRESHOLD = 1_000

@Composable
internal fun LogEntryCard(entry: RequestLogEntry, autoExpand: Boolean = false, searchQuery: String = "") {
  val context = LocalContext.current
  val isError = entry.level == LogLevel.ERROR
  val isWarning = entry.level == LogLevel.WARNING
  val cardBg = when {
    entry.isCancelled -> CancelledColor.copy(alpha = 0.06f)
    isError -> OlliteRTDeleteRed.copy(alpha = 0.06f)
    isWarning -> WarningColor.copy(alpha = 0.06f)
    else -> MaterialTheme.colorScheme.surfaceContainerLow
  }

  var requestExpanded by remember { mutableStateOf(autoExpand) }
  var compactedExpanded by remember { mutableStateOf(autoExpand) }
  var responseExpanded by remember { mutableStateOf(autoExpand) }
  var showMetricsDialog by remember { mutableStateOf(false) }
  // Hoisted here (not inline in the footer Row) so isOverflowing can observe maxValue and
  // trigger recomposition when badges overflow the weight(1f) area.
  val footerScrollState = rememberScrollState()

  // Latch: pathIsMultiLine only ever goes false→true (never resets). This prevents the
  // layout flip-flop where IP inline narrows the path → wraps → IP below → path widens
  // → single line → IP inline → repeat. Once wrapping is detected, layout stays stable.
  var pathIsMultiLine by remember { mutableStateOf(false) }
  val hasInfoButton = entry.ttfbMs > 0 || entry.decodeSpeed > 0 || entry.latencyMs > 0
  val hasCopyButton = !entry.isPending

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(cardBg)
      .padding(16.dp),
  ) {
    // Row 1: [METHOD] [path] [IP — inline only when path fits] [ⓘ] [copy]
    // ⓘ and copy always stay in the right corner. Only IP moves below when path wraps.
    Row(
      verticalAlignment = if (pathIsMultiLine) Alignment.Top else Alignment.CenterVertically,
    ) {
      MethodBadge(method = entry.method)
      Spacer(modifier = Modifier.width(8.dp))
      // Path text — fully visible, no truncation. Latch detects wrap to reposition IP.
      if (searchQuery.isNotEmpty()) {
        val highlighted = remember(entry.path, searchQuery) {
          buildHighlightedString(entry.path, searchQuery, baseColor = Color(0xFFE5E2E3))
        }
        Text(
          text = highlighted,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = SpaceGroteskFontFamily,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.weight(1f),
          onTextLayout = { if (it.lineCount > 1) pathIsMultiLine = true },
        )
      } else {
        Text(
          text = entry.path,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          fontFamily = SpaceGroteskFontFamily,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.weight(1f),
          onTextLayout = { if (it.lineCount > 1) pathIsMultiLine = true },
        )
      }
      // IP inline only when path fits on one line
      if (!pathIsMultiLine && entry.clientIp != null) {
        Spacer(modifier = Modifier.width(4.dp))
        EntryIpPill(entry, searchQuery)
      }
      // Buttons always stay in the top-right corner regardless of path length
      EntryActionButtons(entry, hasInfoButton, hasCopyButton) { showMetricsDialog = true }
    }

    // Row 2: IP right-aligned below the buttons when path wraps to multiple lines
    if (pathIsMultiLine && entry.clientIp != null) {
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        EntryIpPill(entry, searchQuery)
      }
    }

    // Request body preview (if present)
    if (!entry.requestBody.isNullOrBlank()) {
      val formatted = remember(entry.requestBody) { prettyPrintJson(entry.requestBody) }
      val isLong = remember(formatted) { formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES }
      // Use the original body size (before base64 compaction) when available,
      // so the badge reflects the true request size, not the smaller compacted version.
      val requestSize = remember(entry.requestBody, entry.originalRequestBodySize) {
        val sizeChars = if (entry.originalRequestBodySize > 0) entry.originalRequestBodySize else entry.requestBody.length
        formatByteSize(sizeChars)
      }
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = stringResource(R.string.logs_entry_request_label, requestSize),
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        body = formatted,
        expanded = requestExpanded,
        showToggle = isLong,
        onToggle = { requestExpanded = !requestExpanded },
        searchQuery = searchQuery,
      )
    }

    // Compacted prompt preview — shown between Request and Response when compaction was applied.
    // This is the actual prompt that was sent to inference after compaction strategies were applied.
    if (!entry.compactedPrompt.isNullOrBlank()) {
      val compactedSize = remember(entry.compactedPrompt) { formatByteSize(entry.compactedPrompt.length) }
      val isLong = remember(entry.compactedPrompt) { entry.compactedPrompt.length > COLLAPSED_MAX_CHARS || entry.compactedPrompt.count { it == '\n' } > COLLAPSED_MAX_LINES }
      val badges = remember(entry.compactionDetails) { parseCompactionBadges(entry.compactionDetails) }
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = stringResource(R.string.logs_entry_compacted_prompt_label, compactedSize),
        labelColor = WarningColor,
        body = entry.compactedPrompt,
        expanded = compactedExpanded,
        showToggle = isLong,
        onToggle = { compactedExpanded = !compactedExpanded },
        searchQuery = searchQuery,
      )
      // Strategy badges below the text box, above the Response section
      if (badges.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          badges.forEachIndexed { index, (badgeLabel, badgeColor) ->
            if (index > 0) FooterDot()
            Text(
              text = badgeLabel,
              style = MaterialTheme.typography.labelSmall,
              color = badgeColor,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    // Response area: streaming partial text while pending, full JSON when done
    // NOTE: Compaction is instant (string manipulation before inference), so the pending state
    // always shows normal generating text. If model-based compaction is added, this should
    // show CompactingIcon + "Compacting prompt" text while the model summarizes.
    if (entry.isPending) {
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = stringResource(R.string.logs_entry_response),
        style = MaterialTheme.typography.labelSmall,
        color = OlliteRTPrimary,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      PendingResponseSection(
        entryId = entry.id,
        partialText = entry.partialText,
      )
    } else if (entry.isCancelled) {
      Spacer(modifier = Modifier.height(10.dp))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(CancelledColor.copy(alpha = 0.08f))
          .padding(horizontal = 12.dp, vertical = 14.dp),
      ) {
        val cancelledDisplay = remember(entry.partialText) {
          entry.partialText?.replace("<think>", "")?.replace("</think>", "")?.trimStart()
        }
        if (!cancelledDisplay.isNullOrEmpty()) {
          Text(
            text = cancelledDisplay,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
          text = if (entry.cancelledByUser) stringResource(R.string.logs_entry_stopped_by_user)
                 else stringResource(R.string.logs_entry_client_disconnected),
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 11.sp,
          ),
          color = CancelledColor,
          fontWeight = FontWeight.SemiBold,
        )
      }
    } else if (!entry.responseBody.isNullOrBlank()) {
      val formatted = remember(entry.responseBody) { prettyPrintJson(entry.responseBody) }
      val isLong = remember(formatted) { formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES }
      val responseSize = remember(entry.responseBody) { formatByteSize(entry.responseBody.length) }
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = stringResource(R.string.logs_entry_response_label, responseSize),
        labelColor = OlliteRTPrimary,
        body = formatted,
        expanded = responseExpanded,
        showToggle = isLong,
        onToggle = { responseExpanded = !responseExpanded },
        searchQuery = searchQuery,
      )
    }

    // Show which client-supplied sampler params were ignored
    if (entry.ignoredClientParams != null) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = stringResource(R.string.logs_entry_ignored_params, entry.ignoredClientParams),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = WarningColor,
      )
    }

    // Footer area: badges (compaction/overflow) are placed inline when they fit,
    // or on a separate row above when they'd overflow. Measured dynamically via
    // SubcomposeLayout so the layout scales correctly across phones and tablets.
    if (!entry.isPending) {
      val contextOverflow = remember(entry.responseBody, entry.statusCode) { isContextOverflowError(entry.responseBody, entry.statusCode) }

      Spacer(modifier = Modifier.height(10.dp))

      // Footer: [scrollable badges] ··· [model · time right-aligned]
      // Status badges are horizontally scrollable so they remain visible at large font scaling.
      // Context overflow is shown in the StatusBadge as "Context Exceeded" instead of "400 Bad Request".
      // Compaction badges (Compacted, Truncated, Trimmed) are shown below the
      // Compacted Prompt text box instead of here.
      //
      // Two-branch layout driven by overflow detection (footerScrollState.maxValue > 0):
      //   Non-overflow: badges scroll in weight(1f) area; model·time pinned to the right.
      //   Overflow: everything in one wide scrollable row — model·time visible by scrolling right,
      //             with overflow indicated by partial clipping at the right edge.
      val footerOverflowing = footerScrollState.maxValue > 0
      val modelTimeText = listOfNotNull(entry.modelName, formatTimestamp(entry.timestamp)).joinToString(" · ")

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!footerOverflowing) {
          // Normal: badges scroll within weight(1f), model·time pinned to the right
          Row(
            modifier = Modifier
              .weight(1f)
              .horizontalScroll(footerScrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            FooterBadges(entry = entry, contextOverflow = contextOverflow)
          }
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = modelTimeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        } else {
          // Overflow: badges + model·time in one scrollable row so everything is reachable.
          // The right edge is visibly clipped, signalling to the user that content continues.
          Row(
            modifier = Modifier.horizontalScroll(footerScrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            FooterBadges(entry = entry, contextOverflow = contextOverflow)
            FooterDot()
            Text(
              text = modelTimeText,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
        }
      }
    }
  }

  // Per-request performance metrics popup
  if (showMetricsDialog) {
    RequestMetricsDialog(entry = entry, onDismiss = { showMetricsDialog = false })
  }
}

/**
 * Dialog showing per-request performance metrics for a single log entry.
 * Displays TTFB, decode speed, prefill speed, inter-token latency, token counts,
 * context utilization, and total latency.
 */
@Composable
internal fun RequestMetricsDialog(entry: RequestLogEntry, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.logs_metrics_title),
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (entry.ttfbMs > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_ttfb), stringResource(R.string.logs_metrics_value_ms, entry.ttfbMs))
        }
        if (entry.decodeSpeed > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_decode_speed), "%.1f t/s".format(entry.decodeSpeed))
        }
        if (entry.prefillSpeed > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_prefill_speed), "%.1f t/s".format(entry.prefillSpeed))
        }
        if (entry.itlMs > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_itl), "%.1fms".format(entry.itlMs))
        }
        if (entry.latencyMs > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_total_latency), stringResource(R.string.logs_metrics_value_ms, entry.latencyMs))
        }
        if (entry.inputTokenEstimate > 0) {
          val prefix = if (entry.isExactTokenCount) "" else "~"
          MetricsRow(stringResource(R.string.logs_metrics_input_tokens), "$prefix${entry.inputTokenEstimate}")
        }
        if (entry.tokens > 0) {
          MetricsRow(stringResource(R.string.logs_metrics_output_tokens), "~${entry.tokens}")
        }
        if (entry.inputTokenEstimate > 0 && entry.maxContextTokens > 0) {
          val utilPct = (entry.inputTokenEstimate.toDouble() / entry.maxContextTokens.toDouble()) * 100.0
          val utilColor = when {
            utilPct > 80 -> MaterialTheme.colorScheme.error
            utilPct > 50 -> WarningColor
            else -> MaterialTheme.colorScheme.onSurface
          }
          MetricsRow(
            label = stringResource(R.string.logs_metrics_context_util),
            value = "%.1f%%".format(utilPct),
            valueColor = utilColor,
            detail = stringResource(R.string.logs_metrics_ctx_detail, entry.inputTokenEstimate, entry.maxContextTokens),
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.logs_metrics_close))
      }
    },
  )
}

@Composable
internal fun MetricsRow(
  label: String,
  value: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
  detail: String? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = valueColor,
        fontWeight = FontWeight.SemiBold,
        fontFamily = SpaceGroteskFontFamily,
      )
      if (detail != null) {
        Text(
          text = detail,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
internal fun ExpandableBodySection(
  label: String,
  labelColor: Color,
  body: String,
  expanded: Boolean,
  showToggle: Boolean,
  onToggle: () -> Unit,
  /** Optional annotated label — takes precedence over plain [label] when provided. */
  annotatedLabel: AnnotatedString? = null,
  /** Active search query — overlays yellow highlight on matches within JSON-highlighted text. */
  searchQuery: String = "",
) {
  if (annotatedLabel != null) {
    Text(
      text = annotatedLabel,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
    )
  } else if (label.isNotBlank()) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = labelColor,
      fontWeight = FontWeight.SemiBold,
    )
  }
  Spacer(modifier = Modifier.height(4.dp))

  // Collapsed: highlight only the visible prefix (cheap — max ~600 chars).
  // Expanded: compute full highlight asynchronously to avoid main-thread jank on large bodies
  // (50KB+ JSON with regex highlighting can take 100-200ms). Show plain text instantly,
  // then swap in highlighted version when ready.
  val collapsedHighlighted = remember(body, searchQuery) {
    val preview = if (body.length > COLLAPSED_MAX_CHARS) body.substring(0, COLLAPSED_MAX_CHARS) else body
    val jsonStyled = highlightJson(preview)
    if (searchQuery.isNotEmpty()) overlaySearchHighlights(jsonStyled, searchQuery) else jsonStyled
  }

  if (expanded) {
    // For large bodies, compute JSON + search highlighting off the main thread.
    // Small bodies (<4KB) are fast enough to highlight synchronously.
    val fullHighlighted by produceState(
      initialValue = if (body.length <= ASYNC_HIGHLIGHT_THRESHOLD) {
        val jsonStyled = highlightJson(body)
        if (searchQuery.isNotEmpty()) overlaySearchHighlights(jsonStyled, searchQuery) else jsonStyled
      } else {
        null // show plain text while computing
      },
      key1 = body,
      key2 = searchQuery,
    ) {
      if (body.length > ASYNC_HIGHLIGHT_THRESHOLD) {
        value = withContext(Dispatchers.Default) {
          val jsonStyled = highlightJson(body)
          if (searchQuery.isNotEmpty()) overlaySearchHighlights(jsonStyled, searchQuery) else jsonStyled
        }
      }
    }
    // Expanded: wrap in SelectionContainer so text is selectable
    SelectionContainer {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerLowest),
      ) {
        Box(
          modifier = Modifier
            .padding(12.dp)
            .horizontalScroll(rememberScrollState()),
        ) {
          val highlighted = fullHighlighted
          if (highlighted != null) {
            Text(
              text = highlighted,
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
              ),
            )
          } else {
            // Plain text fallback while async highlighting is in progress
            Text(
              text = body,
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
              ),
              color = Color(0xFFBDBDBD),
            )
          }
        }
        if (showToggle) {
          Icon(
            imageVector = Icons.Outlined.ExpandLess,
            contentDescription = stringResource(R.string.logs_body_collapse_cd),
            tint = labelColor.copy(alpha = 0.7f),
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp)
              .size(22.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f))
              .clickable(onClick = onToggle),
          )
        }
      }
    }
  } else {
    // Collapsed: tapping anywhere expands, no text selection needed
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .then(if (showToggle) Modifier.clickable(onClick = onToggle) else Modifier),
    ) {
      Box(
        modifier = Modifier
          .padding(12.dp)
          .horizontalScroll(rememberScrollState()),
      ) {
        Text(
          text = collapsedHighlighted,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
          ),
          maxLines = COLLAPSED_MAX_LINES,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (showToggle) {
        Icon(
          imageVector = Icons.Outlined.ExpandMore,
          contentDescription = stringResource(R.string.logs_body_expand_cd),
          tint = labelColor.copy(alpha = 0.7f),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)),
        )
      }
    }
  }
}

/** The client IP address pill shown in the log entry card header. */
@Composable
private fun EntryIpPill(entry: RequestLogEntry, searchQuery: String) {
  if (entry.clientIp == null) return
  if (searchQuery.isNotEmpty()) {
    val highlighted = remember(entry.clientIp, searchQuery) {
      buildHighlightedString(entry.clientIp, searchQuery, baseColor = Color(0xFFC2C6D8))
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
private fun EntryActionButtons(
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
 * Extracted so the same content can be rendered in both the non-overflow (inside weight(1f) row)
 * and overflow (single scrollable row with model·time appended) layout branches.
 */
@Composable
private fun FooterBadges(entry: RequestLogEntry, contextOverflow: Boolean) {
  StatusBadge(statusCode = entry.statusCode, contextOverflow = contextOverflow)
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
  // Per-request context utilization (e.g. "~258 / 1024 ctx")
  // Color-coded: white ≤50%, yellow 50–80%, red >80%
  if (entry.inputTokenEstimate > 0 && entry.maxContextTokens > 0) {
    val utilPct = entry.inputTokenEstimate.toDouble() / entry.maxContextTokens.toDouble()
    val ctxColor = when {
      utilPct > 0.8 -> MaterialTheme.colorScheme.error
      utilPct > 0.5 -> WarningColor
      else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    FooterDot()
    Text(
      text = stringResource(R.string.logs_badge_ctx_format, if (entry.isExactTokenCount) "" else "~", entry.inputTokenEstimate, entry.maxContextTokens),
      style = MaterialTheme.typography.labelSmall,
      color = ctxColor,
    )
  }
}
