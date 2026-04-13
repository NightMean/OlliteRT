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
import androidx.compose.ui.text.style.TextAlign
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
import com.ollitert.llm.server.ui.server.WarningColor
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LlmHttpErrorSuggestions
import com.ollitert.llm.server.ui.server.EventColor
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.TooltipAnchorPosition
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

  val categoryLabel = when (entry.eventCategory) {
    EventCategory.MODEL -> "MODEL"
    EventCategory.SETTINGS -> "SETTINGS"
    EventCategory.SERVER -> "SERVER"
    EventCategory.PROMPT -> "PROMPT"
    EventCategory.UPDATE -> "UPDATE"
    EventCategory.GENERAL -> "EVENT"
  }
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
  val headline = when (parsedEvent) {
    is ParsedEventType.Loading -> "Model Loading"
    is ParsedEventType.Ready -> "Model Loaded"
    is ParsedEventType.Warmup -> "Internal Warmup Message"
    is ParsedEventType.InferenceSettings -> "Settings changed"
    is ParsedEventType.SettingsToggle -> "Settings changed"
    is ParsedEventType.PromptActive -> "${parsedEvent.promptType} active"
    is ParsedEventType.ServerStopped -> "Server Stopped"
    is ParsedEventType.WarmupSkipped -> "Warmup Skipped"
    is ParsedEventType.ModelLoadFailed -> "Model Load Failed"
    is ParsedEventType.ServerFailed -> "Server Failed"
    is ParsedEventType.ModelNotFound -> "Model Not Found"
    is ParsedEventType.ImageDecodeFailed -> "Image Decode Failed"
    is ParsedEventType.QueuedReload -> "Queued Reload"
    is ParsedEventType.CorsChanged -> "Settings changed"
    is ParsedEventType.ConversationResetFailed -> "Conversation Reset Failed"
    is ParsedEventType.SettingsBatch -> "Settings updated"
    is ParsedEventType.ApiConfigChange -> "Config via REST API"
    is ParsedEventType.RestartRequested -> "Model Restart"
    is ParsedEventType.Unloading -> "Model Unloading"
    is ParsedEventType.KeepAliveUnloaded -> "Model Idle Unloaded"
    is ParsedEventType.KeepAliveReloading -> "Model Reloading"
    is ParsedEventType.KeepAliveReloaded -> "Model Reloaded"
    is ParsedEventType.UpdateAvailable -> "Update Available"
    is ParsedEventType.UpdateCurrent -> "Up to Date"
    is ParsedEventType.UpdateAutoDisabled -> "Update Check Disabled"
    null -> if (isDebug) "Debug" else null
  }

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
          text = headline,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 12.sp,
          ),
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Spacer(modifier = Modifier.weight(1f))
      // Copy button
      TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text("Copy event") } },
        state = rememberTooltipState(),
      ) {
        IconButton(
          onClick = { copyEventToClipboard(context, entry) },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy event",
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
          text = buildAnnotatedString {
            append("Loading ")
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" into memory")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.Ready -> {
        Text(
          text = buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" loaded into memory")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.Warmup -> {
        // Request/response style — mirrors LogEntryCard sections
        Text(
          text = "Request",
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
            text = parsedEvent.input,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Response",
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
            text = parsedEvent.output,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 11.sp),
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
            fontSize = 11.sp,
            lineHeight = 16.sp,
          ),
          textColor = MaterialTheme.colorScheme.onSurface,
        )
      }

      is ParsedEventType.ServerStopped -> {
        // Show model name that was unloaded — sourced from the entry's modelName field
        if (entry.modelName != null) {
          Text(
            text = buildAnnotatedString {
              append("Model ")
              withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
                append(entry.modelName)
              }
              append(" unloaded from memory")
            },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      is ParsedEventType.WarmupSkipped -> {
        Text(
          text = parsedEvent.reason,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.ModelLoadFailed -> {
        Text(
          text = parsedEvent.errorMessage,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.ServerFailed -> {
        Text(
          text = parsedEvent.errorMessage,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.ModelNotFound -> {
        // Show model name in primary color if extracted, otherwise the raw detail as error
        Text(
          text = parsedEvent.detail,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = OlliteRTPrimary,
          fontWeight = FontWeight.SemiBold,
        )
      }

      is ParsedEventType.ImageDecodeFailed -> {
        Text(
          text = parsedEvent.errorMessage,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.QueuedReload -> {
        val modelText = if (entry.modelName != null) "Reloading ${entry.modelName} with updated settings"
                        else "Reloading model with updated settings"
        Text(
          text = modelText,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = OlliteRTPrimary,
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.CorsChanged -> {
        // Reuse the settings change row style for CORS before → after
        SettingsChangeRows(
          parsed = ParsedInferenceEvent(
            changes = listOf(InferenceSettingsChange("CORS Allowed Origins", parsedEvent.oldValue, parsedEvent.newValue)),
            statusSuffix = null,
          ),
          accentColor = accentColor,
        )
      }

      is ParsedEventType.ConversationResetFailed -> {
        Text(
          text = parsedEvent.errorMessage,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
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
            text = buildAnnotatedString {
              append("Restarting ")
              withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
                append(entry.modelName)
              }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      is ParsedEventType.Unloading -> {
        Text(
          text = buildAnnotatedString {
            append("Unloading ")
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" from memory")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.KeepAliveUnloaded -> {
        Text(
          text = buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" unloaded after ${parsedEvent.idleMinutes}m idle to free RAM")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.KeepAliveReloading -> {
        Text(
          text = buildAnnotatedString {
            append("Reloading ")
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" (request received while idle)")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.KeepAliveReloaded -> {
        Text(
          text = buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" reloaded in ${parsedEvent.timeMs}ms")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.UpdateAvailable -> {
        Text(
          text = buildAnnotatedString {
            append("Version ")
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.version)
            }
            append(" is available")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Show body details (current version, release URL) if present
        if (parsedEvent.body != null) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = parsedEvent.body,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          )
        }
      }

      is ParsedEventType.UpdateCurrent -> {
        Text(
          text = parsedEvent.body ?: "No newer version found",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.UpdateAutoDisabled -> {
        Text(
          text = parsedEvent.body ?: "Too many consecutive failures",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
          fontWeight = FontWeight.Medium,
        )
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
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp, lineHeight = 17.sp),
            )
          }
        } else {
          Text(
            text = styledMessage,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp, lineHeight = 17.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (isLong) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (expanded) "Show less" else "Show more",
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .clickable { expanded = !expanded }
              .padding(vertical = 2.dp),
          )
        }
      }
    }

    // Recovery suggestion for error-level events — shown below the error body
    if (isError) {
      val suggestion = remember(message) {
        val kind = LlmHttpErrorSuggestions.classifyFromString(message)
        LlmHttpErrorSuggestions.suggest(kind)
      }
      if (suggestion != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = suggestion,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // ── Footer — horizontally scrollable so all badges remain visible at large font ──
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      // Timing in footer for model ready and warmup (mirrors request card latency)
      when (parsedEvent) {
        is ParsedEventType.Ready -> {
          Text(
            text = "Ready",
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
        else -> {}
      }
      // Separator dot — only shown when there's timing content before the timestamp
      if (parsedEvent is ParsedEventType.Ready || parsedEvent is ParsedEventType.Warmup) {
        FooterDot()
      }
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
            // No old value — just show param: new (e.g. initial set)
            Text(
              text = "${change.paramName}: ${change.newValue}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
              color = rowColor ?: OlliteRTPrimary,
              fontWeight = FontWeight.SemiBold,
            )
          } else {
            // Left column: "Param: oldValue" — muted
            Text(
              text = "${change.paramName}: ${change.oldValue}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.weight(1f),
            )
            // Center: arrow
            Text(
              text = "→",
              style = MaterialTheme.typography.labelSmall,
              color = ValueArrowColor,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              modifier = Modifier.width(28.dp),
            )
            // Right column: "Param: newValue" — bold/bright
            Text(
              text = "${change.paramName}: ${change.newValue}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
              color = rowColor ?: MaterialTheme.colorScheme.onSurface,
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
      text = parsed.statusSuffix,
      style = MaterialTheme.typography.labelSmall,
      color = accentColor,
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
    fontSize = 11.sp,
    lineHeight = 16.sp,
  )

  // Before
  if (diff.oldText.isNotBlank()) {
    Text(
      text = "Before",
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
    text = if (diff.oldText.isNotBlank()) "After" else "Set to",
    style = MaterialTheme.typography.labelSmall,
    color = OlliteRTPrimary,
  )
  Spacer(modifier = Modifier.height(2.dp))
  if (diff.newText.isBlank()) {
    Text(
      text = "(empty)",
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

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLowest)
      .then(if (isLong) Modifier.clickable { expanded = !expanded } else Modifier)
      .padding(10.dp),
  ) {
    if (expanded) {
      SelectionContainer {
        Text(text = text, style = textStyle, color = textColor)
      }
    } else {
      Text(
        text = text,
        style = textStyle,
        color = textColor,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (isLong) {
      Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(18.dp),
      )
    }
  }
}

internal fun copyEventToClipboard(context: Context, entry: RequestLogEntry) {
  val json = entryToJson(entry).toString(2)
  copyToClipboard(context, "OlliteRT Event", json, formatSuffix = "JSON")
}

