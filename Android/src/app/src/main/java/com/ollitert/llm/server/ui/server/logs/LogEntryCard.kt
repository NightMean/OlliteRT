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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.humanReadableSize
import com.ollitert.llm.server.data.model.ErrorKind
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.ui.server.CancelledColor
import com.ollitert.llm.server.ui.server.ThinkingColor
import com.ollitert.llm.server.ui.server.WarningColor
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTOnBackground
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

@Composable
internal fun LogEntryCard(
  entry: RequestLogEntry,
  autoExpand: Boolean = false,
  searchQuery: String = "",
  wrapText: Boolean = true,
) {
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

  var pathIsMultiLine by remember { mutableStateOf(false) }
  val hasInfoButton = entry.ttfbMs > 0 || entry.decodeSpeed > 0 || entry.latencyMs > 0
  val hasCopyButton = !entry.isPending
  val responseContent = resolveLogResponseContent(entry)

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(cardBg)
      .padding(16.dp),
  ) {
    // Row 1: [METHOD] [path] [IP — inline only when path fits] [ⓘ] [copy]
    Row(
      verticalAlignment = if (pathIsMultiLine) Alignment.Top else Alignment.CenterVertically,
    ) {
      MethodBadge(method = entry.method)
      Spacer(modifier = Modifier.width(8.dp))
      if (searchQuery.isNotEmpty()) {
        val highlighted = remember(entry.path, searchQuery) {
          buildHighlightedString(entry.path, searchQuery, baseColor = OlliteRTOnBackground)
        }
        Text(
          text = highlighted,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
          onTextLayout = { result ->
            if (result.lineCount > 1 && !pathIsMultiLine) pathIsMultiLine = true
          },
        )
      } else {
        Text(
          text = entry.path,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.weight(1f),
          onTextLayout = { result ->
            if (result.lineCount > 1 && !pathIsMultiLine) pathIsMultiLine = true
          },
        )
      }

      if (!pathIsMultiLine && entry.clientIp != null) {
        Spacer(modifier = Modifier.width(4.dp))
        EntryIpPill(entry = entry, searchQuery = searchQuery)
      }

      EntryActionButtons(
        entry = entry,
        hasInfoButton = hasInfoButton,
        hasCopyButton = hasCopyButton,
        onInfoClick = { showMetricsDialog = true },
      )
    }

    if (pathIsMultiLine && entry.clientIp != null) {
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        EntryIpPill(entry = entry, searchQuery = searchQuery)
      }
    }

    // Body content: Request, Compacted Prompt, Response
    if (!entry.requestBody.isNullOrBlank()) {
      val formatted = remember(entry.requestBody) { prettyPrintJson(entry.requestBody) }
      val isLong = remember(formatted) { formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES }
      val requestSize = remember(entry.requestBody, entry.originalRequestBodySize) {
        requestBodySizeChars(entry).humanReadableSize()
      }
      Spacer(modifier = Modifier.height(10.dp))
      val requestLabel = stringResource(R.string.logs_entry_request_label, requestSize)
      val annotatedRequestLabel = if (entry.hasToolCalls) {
        val toolCallText = stringResource(R.string.logs_badge_tool_call)
        val baseColor = MaterialTheme.colorScheme.onSurfaceVariant
        val toolCallColor = OlliteRTPrimary
        remember(requestLabel, toolCallText, baseColor, toolCallColor) {
          buildAnnotatedRequestLabel(requestLabel, toolCallText, baseColor, toolCallColor)
        }
      } else null

      ExpandableBodySection(
        label = requestLabel,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        annotatedLabel = annotatedRequestLabel,
        body = formatted,
        expanded = requestExpanded,
        showToggle = isLong,
        onToggle = { requestExpanded = !requestExpanded },
        searchQuery = searchQuery,
        wrapText = wrapText,
      )
    }

    if (!entry.compactedPrompt.isNullOrBlank()) {
      val formatted = remember(entry.compactedPrompt) { prettyPrintJson(entry.compactedPrompt) }
      val isLong = remember(formatted) { formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES }
      val compactedSize = remember(entry.compactedPrompt) { entry.compactedPrompt.length.humanReadableSize() }
      val compactionBadges = remember(entry.compactionDetails) {
        parseCompactionBadges(entry.compactionDetails)
      }
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = stringResource(R.string.logs_entry_compacted_prompt_label, compactedSize),
        labelColor = ThinkingColor,
        body = formatted,
        expanded = compactedExpanded,
        showToggle = isLong,
        onToggle = { compactedExpanded = !compactedExpanded },
        searchQuery = searchQuery,
        wrapText = wrapText,
      )
      if (compactionBadges.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          compactionBadges.forEachIndexed { index, (badgeLabel, badgeColor) ->
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

    when (responseContent) {
      is LogResponseContent.Pending -> {
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
          partialText = responseContent.partialText,
          isGenerating = responseContent.isGenerating,
        )
      }

      is LogResponseContent.Cancelled -> {
        val cancelledDisplay = remember(responseContent.partialText) {
          responseContent.partialText?.replace("<think>", "")?.replace("</think>", "")?.trimStart()
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CancelledColor.copy(alpha = 0.08f))
            .padding(12.dp),
        ) {
          if (!cancelledDisplay.isNullOrEmpty()) {
            Text(
              text = cancelledDisplay,
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = LOG_DETAIL_FONT_SIZE,
              ),
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
          }
          Text(
            text = if (responseContent.cancelledByUser) stringResource(R.string.logs_entry_stopped_by_user)
                   else stringResource(R.string.logs_entry_client_disconnected),
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = LOG_DETAIL_FONT_SIZE,
            ),
            color = CancelledColor,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      is LogResponseContent.Completed -> {
        val formatted = remember(responseContent.body) { prettyPrintJson(responseContent.body) }
        val isLong = remember(formatted) { formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES }
        val responseSize = remember(responseContent.body) { responseContent.body.length.humanReadableSize() }
        Spacer(modifier = Modifier.height(10.dp))
        ExpandableBodySection(
          label = stringResource(R.string.logs_entry_response_label, responseSize),
          labelColor = OlliteRTPrimary,
          body = formatted,
          expanded = responseExpanded,
          showToggle = isLong,
          onToggle = { responseExpanded = !responseExpanded },
          searchQuery = searchQuery,
          wrapText = wrapText,
        )
      }

      LogResponseContent.None -> Unit
    }

    if (entry.ignoredClientParams != null) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = stringResource(R.string.logs_entry_ignored_params, entry.ignoredClientParams),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = LOG_DETAIL_FONT_SIZE),
        color = WarningColor,
      )
    }

    // Footer
    if (entry.isPending) {
      Spacer(modifier = Modifier.height(10.dp))
      val modelTimeText = listOfNotNull(entry.modelName, formatTimestamp(entry.timestamp)).joinToString(" · ")
      if (entry.isGenerating) {
        PendingLogFooter(startTimestampMs = entry.timestamp, modelTimeText = modelTimeText)
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.End,
        ) {
          Text(
            text = modelTimeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
    } else {
      val contextOverflow = entry.errorKind == ErrorKind.CONTEXT_OVERFLOW
      Spacer(modifier = Modifier.height(10.dp))
      val modelTimeText = listOfNotNull(entry.modelName, formatTimestamp(entry.timestamp)).joinToString(" · ")

      ResponsiveLogFooter(modelTimeText = modelTimeText) {
        FooterBadges(entry = entry, contextOverflow = contextOverflow)
      }
    }
  }

  if (showMetricsDialog) {
    RequestMetricsDialog(entry = entry, onDismiss = { showMetricsDialog = false })
  }
}
