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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_LONG_CHARS
import com.ollitert.llm.server.data.model.RequestLogEntry
import com.ollitert.llm.server.ui.common.buildTrackableUrlAnnotatedString
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

// Threshold for collapse: messages with more than this many newlines become expandable.
private const val MIN_LINES_FOR_COLLAPSE = 2

@Composable
internal fun LogEventBodyRenderer(
  parsedEvent: ParsedEventType?,
  entry: RequestLogEntry,
  accentColor: Color,
  isError: Boolean,
  searchQuery: String,
) {
  val message = entry.path

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
      val oldState = if (parsedEvent.enabled) "disabled" else "enabled"
      val newState = if (parsedEvent.enabled) "enabled" else "disabled"
      val newColor = if (parsedEvent.enabled) OlliteRTGreen400 else OlliteRTDeleteRed
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

    is ParsedEventType.CpuFallbackStarted -> {
      Text(
        text = highlightPlainIfSearching(
          stringResource(R.string.logs_event_cpu_fallback_started, parsedEvent.technicalReason),
        ),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    is ParsedEventType.CpuFallbackSucceeded -> {
      Text(
        text = highlightPlainIfSearching(stringResource(R.string.logs_event_cpu_fallback_succeeded)),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_BODY_FONT_SIZE),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
      if (parsedEvent.releaseUrl != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = buildTrackableUrlAnnotatedString(parsedEvent.releaseUrl, parsedEvent.releaseUrl),
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_DETAIL_FONT_SIZE),
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
      if (parsedEvent.serverPrompt != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.logs_audio_server_prompt),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        ExpandablePromptBox(
          text = parsedEvent.serverPrompt,
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = LOG_DETAIL_FONT_SIZE),
          textColor = MaterialTheme.colorScheme.onSurface,
        )
      }
      if (parsedEvent.clientLanguage != null || parsedEvent.clientPrompt != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = stringResource(R.string.logs_audio_client_params),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        ExpandablePromptBox(
          text = buildString {
            if (parsedEvent.clientLanguage != null) append("language: ${parsedEvent.clientLanguage}")
            if (parsedEvent.clientPrompt != null) {
              if (isNotEmpty()) append("\n")
              append("prompt: ${parsedEvent.clientPrompt}")
            }
          },
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
      val isLong = message.length > LOG_ERROR_PREVIEW_LONG_CHARS || message.count { it == '\n' } > MIN_LINES_FOR_COLLAPSE
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
}
