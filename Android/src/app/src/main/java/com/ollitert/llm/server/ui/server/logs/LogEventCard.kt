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

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ErrorSuggestions
import com.ollitert.llm.server.common.copyToClipboard
import com.ollitert.llm.server.data.model.EventCategory
import com.ollitert.llm.server.data.model.LogLevel
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.ui.server.EventColor
import com.ollitert.llm.server.ui.server.WarningColor
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTForcedPurple
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// ── Internal event card ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InternalEventCard(entry: RequestLogEntry, searchQuery: String = "") {
  val context = LocalContext.current
  val isError = entry.level == LogLevel.ERROR
  val isWarning = entry.level == LogLevel.WARNING
  val isDebug = entry.level == LogLevel.DEBUG
  val accentColor = when {
    isError -> OlliteRTDeleteRed
    isWarning -> WarningColor
    isDebug -> MaterialTheme.colorScheme.outline
    else -> EventColor
  }
  val message = entry.path

  val categoryLabel = resolveCategoryLabel(context, entry.eventCategory)
  val categoryIcon = when (entry.eventCategory) {
    EventCategory.MODEL -> Icons.Outlined.Memory
    EventCategory.SETTINGS -> Icons.Outlined.Settings
    EventCategory.SERVER -> Icons.Outlined.Dns
    EventCategory.PROMPT -> Icons.AutoMirrored.Outlined.Notes
    EventCategory.UPDATE -> Icons.Outlined.NewReleases
    EventCategory.GENERAL -> Icons.Outlined.Info
  }

  val cardBg = when {
    isError -> OlliteRTDeleteRed.copy(alpha = 0.06f)
    isWarning -> WarningColor.copy(alpha = 0.06f)
    isDebug -> MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
    else -> MaterialTheme.colorScheme.surfaceContainerLow
  }

  val parsedEvent = remember(message, entry.requestBody) { parseEventType(message, entry.requestBody) }

  // Headline text shown next to the category badge
  val headline = if (parsedEvent != null) resolveEventHeadline(context, parsedEvent)
    else if (isDebug) stringResource(R.string.logs_headline_debug) else null

  CompositionLocalProvider(LocalSearchQuery provides searchQuery) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(cardBg)
        .padding(16.dp),
    ) {
      // ── Header: [BADGE] [headline] ... [copy] ──
      Row(verticalAlignment = Alignment.CenterVertically) {
        // Category pill badge
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = categoryIcon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(12.dp),
          )
          Text(
            text = categoryLabel,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            fontFamily = SpaceGroteskFontFamily,
          )
        }

        // Headline next to badge
        if (headline != null) {
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = highlightPlainIfSearching(headline),
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = LOG_BODY_FONT_SIZE,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
          )
        }

        Spacer(modifier = Modifier.weight(1f))
        // Copy button
        TooltipBox(
          positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
          tooltip = { PlainTooltip { Text(stringResource(R.string.logs_tooltip_copy_event)) } },
          state = rememberTooltipState(),
        ) {
          IconButton(
            onClick = { copyEventToClipboard(context, entry) },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.ContentCopy,
              contentDescription = stringResource(R.string.logs_tooltip_copy_event),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // ── Body — specialised per event type ──
      LogEventBodyRenderer(
        parsedEvent = parsedEvent,
        entry = entry,
        accentColor = accentColor,
        isError = isError,
        searchQuery = searchQuery,
      )

      // Recovery suggestion for error-level events — shown below the error body
      if (isError) {
        val suggestion = remember(message) {
          val kind = ErrorSuggestions.classifyFromString(message)
          ErrorSuggestions.suggest(kind, context)
        }
        if (suggestion != null) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = highlightPlainIfSearching(suggestion),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = LOG_DETAIL_FONT_SIZE),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      // ── Footer — scrollable badges on the left, model · time pinned to the right ──
      Spacer(modifier = Modifier.height(8.dp))
      val modelTimeText = listOfNotNull(entry.modelName, formatTimestamp(entry.timestamp)).joinToString(" · ")

      ResponsiveLogFooter(modelTimeText = modelTimeText) {
        EventFooterBadges(parsedEvent = parsedEvent)
      }
    }
  }
}

private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

internal fun copyEventToClipboard(context: Context, entry: RequestLogEntry) {
  val json = prettyJson.encodeToString(JsonElement.serializer(), entryToJson(entry))
  copyToClipboard(context, "OlliteRT Event", json, formatSuffix = "JSON")
}

@Composable
private fun EventFooterBadges(parsedEvent: ParsedEventType?) {
  when (parsedEvent) {
    is ParsedEventType.Ready -> {
      Text(
        text = stringResource(R.string.logs_event_ready),
        style = MaterialTheme.typography.labelSmall,
        color = OlliteRTGreen400,
        fontWeight = FontWeight.SemiBold,
      )
      FooterDot()
      Text(
        text = "${parsedEvent.timeMs}ms",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    is ParsedEventType.Warmup -> {
      Text(
        text = "${parsedEvent.timeMs}ms",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    is ParsedEventType.AudioTranscription -> {
      if (parsedEvent.forced) {
        Text(
          text = stringResource(R.string.logs_event_forced_transcription),
          style = MaterialTheme.typography.labelSmall,
          color = OlliteRTForcedPurple,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
    else -> {}
  }
}
