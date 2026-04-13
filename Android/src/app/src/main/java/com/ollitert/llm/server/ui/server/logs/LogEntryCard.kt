package com.ollitert.llm.server.ui.server.logs

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.copyToClipboard
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

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(cardBg)
      .padding(16.dp),
  ) {
    // Method badge + path + info/copy buttons (top row)
    // Client IP shown on a second row so it doesn't squeeze the path at large font
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MethodBadge(method = entry.method)
      Spacer(modifier = Modifier.width(8.dp))
      // Path text with optional search highlighting
      if (searchQuery.isNotEmpty()) {
        val highlighted = remember(entry.path, searchQuery) {
          buildHighlightedString(entry.path, searchQuery, baseColor = Color(0xFFE5E2E3))
        }
        Text(
          text = highlighted,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = SpaceGroteskFontFamily,
          fontWeight = FontWeight.Medium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      } else {
        Text(
          text = entry.path,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          fontFamily = SpaceGroteskFontFamily,
          fontWeight = FontWeight.Medium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }
      // Per-request metrics info button — only shown when metrics are available
      if (entry.ttfbMs > 0 || entry.decodeSpeed > 0 || entry.latencyMs > 0) {
        Spacer(modifier = Modifier.width(2.dp))
        @OptIn(ExperimentalMaterial3Api::class)
        TooltipBox(
          positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
          tooltip = { PlainTooltip { Text("Request metrics") } },
          state = rememberTooltipState(),
        ) {
          IconButton(
            onClick = { showMetricsDialog = true },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = "Request metrics",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
      // Hide copy while response is still generating — entry has no response body yet
      if (!entry.isPending) {
        Spacer(modifier = Modifier.width(2.dp))
        @OptIn(ExperimentalMaterial3Api::class)
        TooltipBox(
          positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
          tooltip = { PlainTooltip { Text("Copy log entry") } },
          state = rememberTooltipState(),
        ) {
          IconButton(
            onClick = { copyEntryToClipboard(context, entry) },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.ContentCopy,
              contentDescription = "Copy log entry",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }

    // Client IP on its own row, right-aligned, so it's visible at large font scaling
    if (entry.clientIp != null) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      ) {
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
    }

    // Request body preview (if present)
    if (!entry.requestBody.isNullOrBlank()) {
      val formatted = remember(entry.requestBody) { prettyPrintJson(entry.requestBody) }
      val isLong = remember(formatted) { formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES }
      val requestSize = remember(entry.requestBody) { formatByteSize(entry.requestBody.length) }
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = "Request · $requestSize",
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
        label = "Compacted Prompt · $compactedSize",
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
        text = "Response",
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
          text = if (entry.cancelledByUser) "Generation stopped by user"
                 else "Client disconnected — generation stopped",
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
        label = "Response · $responseSize",
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
        text = "Client params ignored: ${entry.ignoredClientParams}",
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

      // Footer: status · latency · SSE · Thinking · Cancelled  ···  model · time
      // Context overflow is shown in the StatusBadge as "Context Exceeded" instead of "400 Bad Request".
      // Compaction badges (Compacted, Truncated, Trimmed) are shown below the
      // Compacted Prompt text box instead of here.
      // Horizontally scrollable so all badges remain visible at large font scaling.
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
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
            text = "SSE",
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTPrimary,
            fontWeight = FontWeight.SemiBold,
          )
        }
        if (entry.isThinking) {
          FooterDot()
          Text(
            text = "Thinking",
            style = MaterialTheme.typography.labelSmall,
            color = ThinkingColor,
            fontWeight = FontWeight.SemiBold,
          )
        }
        if (entry.isCancelled) {
          FooterDot()
          Text(
            text = "Cancelled",
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
            text = "${if (entry.isExactTokenCount) "" else "~"}${entry.inputTokenEstimate} / ${entry.maxContextTokens} ctx",
            style = MaterialTheme.typography.labelSmall,
            color = ctxColor,
          )
        }
        FooterDot()
        Text(
          text = listOfNotNull(
            entry.modelName,
            formatTimestamp(entry.timestamp),
          ).joinToString(" · "),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
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
        text = "Request Metrics",
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (entry.ttfbMs > 0) {
          MetricsRow("Time to First Token", "${entry.ttfbMs}ms")
        }
        if (entry.decodeSpeed > 0) {
          MetricsRow("Decode Speed", "%.1f t/s".format(entry.decodeSpeed))
        }
        if (entry.prefillSpeed > 0) {
          MetricsRow("Prefill Speed", "%.1f t/s".format(entry.prefillSpeed))
        }
        if (entry.itlMs > 0) {
          MetricsRow("Inter-Token Latency", "%.1fms".format(entry.itlMs))
        }
        if (entry.latencyMs > 0) {
          MetricsRow("Total Latency", "${entry.latencyMs}ms")
        }
        if (entry.inputTokenEstimate > 0) {
          val prefix = if (entry.isExactTokenCount) "" else "~"
          MetricsRow("Input Tokens", "$prefix${entry.inputTokenEstimate}")
        }
        if (entry.tokens > 0) {
          MetricsRow("Output Tokens", "~${entry.tokens}")
        }
        if (entry.inputTokenEstimate > 0 && entry.maxContextTokens > 0) {
          val utilPct = (entry.inputTokenEstimate.toDouble() / entry.maxContextTokens.toDouble()) * 100.0
          val utilColor = when {
            utilPct > 80 -> MaterialTheme.colorScheme.error
            utilPct > 50 -> WarningColor
            else -> MaterialTheme.colorScheme.onSurface
          }
          MetricsRow(
            label = "Context Utilization",
            value = "%.1f%%".format(utilPct),
            valueColor = utilColor,
            detail = "${entry.inputTokenEstimate} / ${entry.maxContextTokens}",
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
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
          if (fullHighlighted != null) {
            Text(
              text = fullHighlighted!!,
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
            contentDescription = "Collapse",
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
          contentDescription = "Expand",
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

