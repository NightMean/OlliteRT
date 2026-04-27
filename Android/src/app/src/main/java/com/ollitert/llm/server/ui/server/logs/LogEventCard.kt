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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.copyToClipboard
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.ErrorSuggestions
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.ui.server.EventColor
import com.ollitert.llm.server.ui.server.WarningColor
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTForcedPurple
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

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

  // Hoisted here so footerOverflowing can observe maxValue and trigger recomposition
  // when the timing badges overflow the weight(1f) area.
  val footerScrollState = rememberScrollState()

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
    when (parsedEvent) {
      is ParsedEventType.Loading -> {
        Text(
          text = highlightIfSearching(buildAnnotatedString {
            append(stringResource(R.string.logs_event_loading_prefix))
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(stringResource(R.string.logs_event_loading_suffix))
          }),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.Ready -> {
        Text(
          text = highlightIfSearching(buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(stringResource(R.string.logs_event_loaded_suffix))
          }),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.Warmup -> {
        // Request/response style — mirrors LogEntryCard sections
        Text(
          text = stringResource(R.string.logs_entry_request),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp),
        ) {
          Text(
            text = highlightPlainIfSearching(parsedEvent.input),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_DETAIL_FONT_SIZE),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.logs_entry_response),
          style = MaterialTheme.typography.labelSmall,
          color = OlliteRTPrimary,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp),
        ) {
          Text(
            text = highlightPlainIfSearching(parsedEvent.output),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_DETAIL_FONT_SIZE),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      is ParsedEventType.InferenceSettings -> {
        SettingsChangeRows(parsedEvent.parsed, accentColor)
      }

      is ParsedEventType.SettingsToggle -> {
        // Single row matching the inference settings row style — shows state transition
        val oldState = if (parsedEvent.enabled) "disabled" else "enabled"
        val newState = if (parsedEvent.enabled) "enabled" else "disabled"
        val newColor = if (parsedEvent.enabled) OlliteRTGreen400 else OlliteRTDeleteRed
        // Reuse the same SettingsChangeRows composable via a synthetic ParsedInferenceEvent
        SettingsChangeRows(
          parsed = ParsedInferenceEvent(
            changes = listOf(InferenceSettingsChange(parsedEvent.settingName, oldState, newState)),
            statusSuffix = null,
          ),
          accentColor = accentColor,
          newValueColorOverride = newColor,
        )
      }

      is ParsedEventType.PromptActive -> {
        // Show the prompt text in an expandable text box (same style as prompt diffs)
        ExpandablePromptBox(
          text = parsedEvent.promptText,
          textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontSize = LOG_DETAIL_FONT_SIZE,
            lineHeight = LOG_BODY_LINE_HEIGHT,
          ),
          textColor = MaterialTheme.colorScheme.onSurface,
        )
      }

      is ParsedEventType.ServerStopped -> {
        // Show model name that was unloaded — sourced from the entry's modelName field
        if (entry.modelName != null) {
          Text(
            text = highlightIfSearching(buildAnnotatedString {
              append(stringResource(R.string.logs_event_model_unloaded_prefix))
              withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
                append(entry.modelName)
              }
              append(stringResource(R.string.logs_event_model_unloaded_suffix))
            }),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      is ParsedEventType.WarmupSkipped -> {
        Text(
          text = highlightPlainIfSearching(parsedEvent.reason),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.ModelLoadFailed -> {
        Text(
          text = highlightPlainIfSearching(parsedEvent.errorMessage, accentColor),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.ServerFailed -> {
        Text(
          text = highlightPlainIfSearching(parsedEvent.errorMessage, accentColor),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.ModelNotFound -> {
        Text(
          text = highlightPlainIfSearching(parsedEvent.detail, OlliteRTPrimary),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          fontWeight = FontWeight.SemiBold,
        )
      }

      is ParsedEventType.ImageDecodeFailed -> {
        Text(
          text = highlightPlainIfSearching(parsedEvent.errorMessage, accentColor),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.QueuedReload -> {
        val name = entry.modelName
        val modelText = if (name != null) stringResource(R.string.logs_event_queued_reload_model, name)
                        else stringResource(R.string.logs_event_queued_reload_generic)
        Text(
          text = highlightPlainIfSearching(modelText, OlliteRTPrimary),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.ConversationResetFailed -> {
        Text(
          text = highlightPlainIfSearching(parsedEvent.errorMessage, accentColor),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.SettingsBatch, is ParsedEventType.ApiConfigChange -> {
        // Reuse SettingsChangeRows via a synthetic ParsedInferenceEvent.
        // For toggle-style changes (new value is "enabled"/"disabled"),
        // color the new value green/red for clarity.
        val batchChanges = when (parsedEvent) {
          is ParsedEventType.SettingsBatch -> parsedEvent.changes
          is ParsedEventType.ApiConfigChange -> parsedEvent.changes
        }
        val toggleValues = setOf("enabled", "disabled")
        SettingsChangeRows(
          parsed = ParsedInferenceEvent(
            changes = batchChanges,
            statusSuffix = null,
          ),
          accentColor = accentColor,
          newValueColorOverride = null,
          perRowNewColor = { change ->
            if (change.newValue in toggleValues) {
              if (change.newValue == "enabled") OlliteRTGreen400 else OlliteRTDeleteRed
            } else null
          },
        )
      }

      is ParsedEventType.RestartRequested -> {
        // Show the model name being restarted if available
        if (entry.modelName != null) {
          Text(
            text = highlightIfSearching(buildAnnotatedString {
              append(stringResource(R.string.logs_event_reloading_prefix))
              withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
                append(entry.modelName)
              }
            }),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      is ParsedEventType.Unloading -> {
        Text(
          text = highlightIfSearching(buildAnnotatedString {
            append(stringResource(R.string.logs_event_unloading_prefix))
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(stringResource(R.string.logs_event_unloading_suffix))
          }),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.KeepAliveUnloaded -> {
        Text(
          text = highlightIfSearching(buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(stringResource(R.string.logs_event_keepalive_unloaded_suffix, parsedEvent.idleMinutes))
          }),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.KeepAliveReloading -> {
        Text(
          text = highlightIfSearching(buildAnnotatedString {
            append(stringResource(R.string.logs_event_keepalive_reloading_prefix))
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(stringResource(R.string.logs_event_keepalive_reloading_suffix))
          }),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.KeepAliveReloaded -> {
        Text(
          text = highlightIfSearching(buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(stringResource(R.string.logs_event_keepalive_reloaded_suffix, parsedEvent.timeMs))
          }),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.UpdateAvailable -> {
        Text(
          text = highlightIfSearching(buildAnnotatedString {
            append(stringResource(R.string.logs_event_version_prefix))
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.version)
            }
            append(stringResource(R.string.logs_event_version_suffix))
          }),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (parsedEvent.body != null) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = highlightPlainIfSearching(parsedEvent.body),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_DETAIL_FONT_SIZE),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          )
        }
      }

      is ParsedEventType.UpdateCurrent -> {
        Text(
          text = highlightPlainIfSearching(parsedEvent.body ?: stringResource(R.string.logs_event_update_none)),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.UpdateAutoDisabled -> {
        Text(
          text = highlightPlainIfSearching(parsedEvent.body ?: stringResource(R.string.logs_event_update_auto_disabled), accentColor),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.MemoryPressure -> {
        Text(
          text = highlightPlainIfSearching(stringResource(R.string.logs_event_memory_pressure_body), accentColor),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.AudioTranscription -> {
        Text(
          text = highlightIfSearching(buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" · ${parsedEvent.audioFormat.uppercase()}")
            append(" · ${parsedEvent.fileSize}")
            if (parsedEvent.language != null) append(" · ${parsedEvent.language}")
            append(" · ${parsedEvent.durationSec}")
          }),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (parsedEvent.instruction != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = stringResource(R.string.logs_audio_instruction),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          ExpandablePromptBox(
            text = parsedEvent.instruction,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_DETAIL_FONT_SIZE),
            textColor = MaterialTheme.colorScheme.onSurface,
          )
        }
        if (parsedEvent.transcription != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = if (parsedEvent.forced) stringResource(R.string.logs_audio_transcription_output)
              else stringResource(R.string.logs_entry_response),
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTPrimary,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(modifier = Modifier.height(4.dp))
          ExpandablePromptBox(
            text = parsedEvent.transcription,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_DETAIL_FONT_SIZE),
            textColor = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      null -> {
        // Default: styled text with highlighted values
        val isLong = message.length > 120 || message.count { it == '\n' } > 2
        var expanded by remember { mutableStateOf(false) }
        val styledMessage = remember(message, searchQuery) {
          val base = highlightEventMessage(message, isError, accentColor)
          if (searchQuery.isNotEmpty()) overlaySearchHighlights(base, searchQuery) else base
        }

        if (expanded) {
          SelectionContainer {
            Text(
              text = styledMessage,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE, lineHeight = 17.sp),
            )
          }
        } else {
          Text(
            text = styledMessage,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE, lineHeight = 17.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (isLong) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (expanded) stringResource(R.string.logs_event_show_less) else stringResource(R.string.logs_event_show_more),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .clickable { expanded = !expanded }
              .padding(vertical = 2.dp),
          )
        }
        if (!entry.requestBody.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(6.dp))
          ExpandablePromptBox(
            text = entry.requestBody,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_DETAIL_FONT_SIZE),
            textColor = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }

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
    // Two-branch layout driven by overflow detection (footerScrollState.maxValue > 0):
    //   Non-overflow: badges scroll in weight(1f) area; model·time pinned to the right.
    //   Overflow: everything in one wide scrollable row — model·time visible by scrolling right.
    Spacer(modifier = Modifier.height(8.dp))
    val footerOverflowing = footerScrollState.maxValue > 0
    val modelTimeText = listOfNotNull(entry.modelName, formatTimestamp(entry.timestamp)).joinToString(" · ")

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!footerOverflowing) {
        // Normal: timing badges scroll within weight(1f), model·time pinned to the right
        Row(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(footerScrollState),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          EventFooterBadges(parsedEvent = parsedEvent)
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
          EventFooterBadges(parsedEvent = parsedEvent)
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
  } // CompositionLocalProvider
}

// ── Settings change rows ─────────────────────────────────────────────────────

/**
 * Structured rows for settings changes (inference params or toggle states).
 * Each row: [param name]  [old → new], all full-width with consistent alignment.
 * @param newValueColorOverride optional color override for the new value text on ALL rows
 *   (e.g. green/red for enabled/disabled toggles)
 * @param perRowNewColor optional per-row color function — takes precedence over [newValueColorOverride]
 *   when non-null. Used by SettingsBatch to color "enabled" green and "disabled" red per row.
 */
@Composable
internal fun SettingsChangeRows(
  parsed: ParsedInferenceEvent,
  accentColor: Color,
  newValueColorOverride: Color? = null,
  perRowNewColor: ((InferenceSettingsChange) -> Color?)? = null,
) {
  // Two-column diff layout: [Param: old]  →  [Param: new]
  // Both sides are equal weight(1f), arrow is fixed-width centered between them.
  // This guarantees vertical arrow alignment regardless of value text widths.
  if (parsed.changes.isNotEmpty()) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      parsed.changes.forEach { change ->
        val rowColor = perRowNewColor?.invoke(change) ?: newValueColorOverride
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (change.oldValue.isEmpty()) {
            Text(
              text = highlightPlainIfSearching("${change.paramName}: ${change.newValue}", rowColor ?: OlliteRTPrimary),
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
              fontWeight = FontWeight.SemiBold,
            )
          } else {
            Text(
              text = highlightPlainIfSearching("${change.paramName}: ${change.oldValue}",
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
              modifier = Modifier.weight(1f),
            )
            Text(
              text = "→",
              style = MaterialTheme.typography.labelSmall,
              color = ValueArrowColor,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              modifier = Modifier.width(28.dp),
            )
            Text(
              text = highlightPlainIfSearching("${change.paramName}: ${change.newValue}", rowColor ?: MaterialTheme.colorScheme.onSurface),
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.End,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }

  // Prompt before/after diffs — expandable text boxes for system_prompt / chat_template
  parsed.promptDiffs.forEach { diff ->
    if (parsed.changes.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
    else Spacer(modifier = Modifier.height(2.dp))
    // Prompt label (e.g. "system_prompt" or "chat_template")
    val displayName = diff.paramName.replace("_", " ")
      .replaceFirstChar { it.uppercaseChar() }
    Text(
      text = displayName,
      style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(4.dp))
    PromptBeforeAfterBoxes(diff)
  }

  // Status badge (reloading model, reload queued, etc.)
  if (!parsed.statusSuffix.isNullOrBlank()) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = highlightPlainIfSearching(parsed.statusSuffix, accentColor),
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(accentColor.copy(alpha = 0.10f))
        .padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

/**
 * Expandable before/after text boxes for a prompt change.
 * "Before" is shown in muted style, "After" in primary style.
 * Both are collapsible for long prompts.
 */
@Composable
internal fun PromptBeforeAfterBoxes(diff: PromptDiff) {
  val textStyle = MaterialTheme.typography.bodySmall.copy(
    fontFamily = SpaceGroteskFontFamily,
    fontSize = LOG_DETAIL_FONT_SIZE,
    lineHeight = LOG_BODY_LINE_HEIGHT,
  )

  // Before
  if (diff.oldText.isNotBlank()) {
    Text(
      text = stringResource(R.string.logs_prompt_before),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(2.dp))
    ExpandablePromptBox(
      text = diff.oldText,
      textStyle = textStyle,
      textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(4.dp))
  }

  // After
  Text(
    text = if (diff.oldText.isNotBlank()) stringResource(R.string.logs_prompt_after) else stringResource(R.string.logs_prompt_set_to),
    style = MaterialTheme.typography.labelSmall,
    color = OlliteRTPrimary,
  )
  Spacer(modifier = Modifier.height(2.dp))
  if (diff.newText.isBlank()) {
    Text(
      text = stringResource(R.string.logs_prompt_empty),
      style = textStyle,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
      fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
    )
  } else {
    ExpandablePromptBox(
      text = diff.newText,
      textStyle = textStyle,
      textColor = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/**
 * A text box with a dark background that collapses to 4 lines for long text.
 * Tap to expand/collapse.
 */
@Composable
internal fun ExpandablePromptBox(
  text: String,
  textStyle: androidx.compose.ui.text.TextStyle,
  textColor: Color,
) {
  val isLong = text.length > 200 || text.count { it == '\n' } > 3
  var expanded by remember { mutableStateOf(false) }
  val highlighted = highlightPlainIfSearching(text, textColor)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLowest)
      .clearAndSetSemantics { contentDescription = text }
      .then(if (isLong) Modifier.clickable { expanded = !expanded } else Modifier)
      .padding(10.dp),
  ) {
    if (expanded) {
      SelectionContainer {
        Text(text = highlighted, style = textStyle)
      }
    } else {
      Text(
        text = highlighted,
        style = textStyle,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (isLong) {
      Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = if (expanded) stringResource(R.string.logs_body_collapse_cd) else stringResource(R.string.logs_body_expand_cd),
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(18.dp),
      )
    }
  }
}

internal fun copyEventToClipboard(context: Context, entry: RequestLogEntry) {
  val json = entryToJson(entry).toString(2).replace("\\/", "/")
  copyToClipboard(context, "OlliteRT Event", json, formatSuffix = "JSON")
}

/**
 * The timing badge items that appear in the event card footer.
 * Extracted so the same content can be rendered in both the non-overflow (inside weight(1f) row)
 * and overflow (single scrollable row with model·time appended) layout branches.
 */
@Composable
private fun EventFooterBadges(parsedEvent: ParsedEventType?) {
  // Timing shown for model ready and warmup events (mirrors request card latency footer)
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
