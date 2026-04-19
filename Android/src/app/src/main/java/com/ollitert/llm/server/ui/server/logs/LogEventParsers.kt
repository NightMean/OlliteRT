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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.json.JSONObject

// ── Event message parsing ────────────────────────────────────────────────────

/** A single parameter change in an inference settings event. */
internal data class InferenceSettingsChange(
  val paramName: String,
  val oldValue: String,
  val newValue: String,
)

/** A before/after diff for a prompt (system prompt or chat template). */
internal data class PromptDiff(
  val paramName: String,
  val oldText: String,
  val newText: String,
)

/** Parsed inference settings change with optional reload status and prompt diffs. */
internal data class ParsedInferenceEvent(
  val changes: List<InferenceSettingsChange>,
  val statusSuffix: String?,
  val promptDiffs: List<PromptDiff> = emptyList(),
)

/** Parsed event — enables specialised card rendering for known message patterns. */
internal sealed class ParsedEventType {
  data class Loading(val modelName: String) : ParsedEventType()
  data class Ready(val modelName: String, val timeMs: String) : ParsedEventType()
  data class Warmup(val input: String, val output: String, val timeMs: String) : ParsedEventType()
  data class InferenceSettings(val parsed: ParsedInferenceEvent) : ParsedEventType()
  /** A settings toggle like "Compact Tool Schemas enabled/disabled". */
  data class SettingsToggle(val settingName: String, val enabled: Boolean) : ParsedEventType()
  /** System prompt or chat template active on server start. */
  data class PromptActive(val promptType: String, val promptText: String) : ParsedEventType()
  /** Server stopped cleanly. */
  data object ServerStopped : ParsedEventType()
  /** Warmup skipped because disabled in settings. */
  data class WarmupSkipped(val reason: String) : ParsedEventType()
  /** Model load failed with an error message. */
  data class ModelLoadFailed(val errorMessage: String) : ParsedEventType()
  /** Server failed to start (e.g. port in use). */
  data class ServerFailed(val errorMessage: String) : ParsedEventType()
  /** Model not found or model files missing on disk. */
  data class ModelNotFound(val detail: String) : ParsedEventType()
  /** Failed to decode a base64 image in a multimodal request. */
  data class ImageDecodeFailed(val errorMessage: String) : ParsedEventType()
  /** Queued settings change being applied — model reloading. */
  data object QueuedReload : ParsedEventType()
  /** CORS allowed origins changed. */
  data class CorsChanged(val oldValue: String, val newValue: String) : ParsedEventType()
  /** Failed to reset conversation (e.g. during model reuse). */
  data class ConversationResetFailed(val errorMessage: String) : ParsedEventType()
  /** Grouped batch of settings changes from the Settings screen (body = newline-separated "Name: old → new"). */
  data class SettingsBatch(val changes: List<InferenceSettingsChange>) : ParsedEventType()
  /** Settings changed via REST API endpoint (/v1/server/config or /v1/server/thinking). */
  data class ApiConfigChange(val changes: List<InferenceSettingsChange>) : ParsedEventType()
  /** Model restart requested (user tapped Reload in Status screen). */
  data object RestartRequested : ParsedEventType()
  /** Model being unloaded during a restart (before the new load begins). */
  data class Unloading(val modelName: String) : ParsedEventType()
  /** Model unloaded due to keep_alive idle timeout. */
  data class KeepAliveUnloaded(val modelName: String, val idleMinutes: String) : ParsedEventType()
  /** Model auto-reloading after keep_alive idle unload (request arrived). */
  data class KeepAliveReloading(val modelName: String) : ParsedEventType()
  /** Model reloaded successfully after keep_alive idle unload. */
  data class KeepAliveReloaded(val modelName: String, val timeMs: String) : ParsedEventType()
  /** A newer version is available from GitHub. */
  data class UpdateAvailable(val version: String, val body: String?) : ParsedEventType()
  /** Update check ran and current version is latest. */
  data class UpdateCurrent(val body: String?) : ParsedEventType()
  /** Update check auto-disabled after consecutive failures. */
  data class UpdateAutoDisabled(val body: String?) : ParsedEventType()
  /** Android system memory pressure — fired by onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL). */
  data object MemoryPressure : ParsedEventType()
  /** Audio transcription completed via /v1/audio/transcriptions. */
  data class AudioTranscription(
    val modelName: String,
    val language: String?,
    val audioFormat: String,
    val fileSize: String,
    val durationSec: String,
    val forced: Boolean,
  ) : ParsedEventType()
}

internal val INFERENCE_CHANGE_PREFIX = "Inference settings changed: "

/**
 * Parses an inference settings change event.
 *
 * Structured JSON body schema (preferred):
 * ```json
 * {
 *   "type": "inference_settings",
 *   "changes": [{"param": "TopK", "old": "14", "new": "15"}, ...],
 *   "prompt_diffs": {
 *     "system_prompt": {"old": "...", "new": "..."},
 *     "chat_template": {"old": "...", "new": "..."}
 *   },
 *   "status": "reloading model"
 * }
 * ```
 *
 */
internal fun parseInferenceSettingsMessage(message: String, eventBody: String? = null): ParsedInferenceEvent? {
  if (!message.startsWith(INFERENCE_CHANGE_PREFIX)) return null
  if (eventBody.isNullOrBlank()) return null

  return try {
    val json = JSONObject(eventBody)
    val changes = mutableListOf<InferenceSettingsChange>()
    val promptDiffs = mutableListOf<PromptDiff>()

    // Parse changes array
    val changesArr = json.optJSONArray("changes")
    if (changesArr != null) {
      for (i in 0 until changesArr.length()) {
        val c = changesArr.getJSONObject(i)
        changes.add(InferenceSettingsChange(
          paramName = c.getString("param"),
          oldValue = c.optString("old", ""),
          newValue = c.optString("new", ""),
        ))
      }
    }
    // Parse prompt diffs
    val diffs = json.optJSONObject("prompt_diffs")
    if (diffs != null) {
      for (key in diffs.keys()) {
        val diff = diffs.getJSONObject(key)
        promptDiffs.add(PromptDiff(key, diff.optString("old", ""), diff.optString("new", "")))
      }
    }
    val statusSuffix = json.optString("status", "").ifBlank { null }
    if (changes.isNotEmpty() || promptDiffs.isNotEmpty()) {
      ParsedInferenceEvent(changes, statusSuffix, promptDiffs)
    } else null
  } catch (_: Exception) { null }
}

/**
 * Parses well-known event messages into structured types.
 * Returns null for messages that don't match any known pattern (rendered as styled text).
 */
internal fun parseEventType(message: String, eventBody: String? = null): ParsedEventType? {
  // Inference settings: "Inference settings changed: TopK: 100 → 64, ..."
  parseInferenceSettingsMessage(message, eventBody)?.let { return ParsedEventType.InferenceSettings(it) }

  // Loading model: ModelName
  if (message.startsWith("Loading model: ")) {
    return ParsedEventType.Loading(message.removePrefix("Loading model: "))
  }

  // Model ready: ModelName (Xms)
  PATTERN_MODEL_READY.find(message)?.let {
    return ParsedEventType.Ready(it.groupValues[1], it.groupValues[2])
  }

  // Sending a warmup message: "input" → "output" (Xms)
  // Greedy match on output to handle response text that might contain quotes
  PATTERN_WARMUP.find(message)?.let {
    return ParsedEventType.Warmup(it.groupValues[1], it.groupValues[2], it.groupValues[3])
  }

  // Prompt active on server start: "System prompt active: \"...\""
  // Body JSON schema: {"type":"prompt_active","prompt_type":"system_prompt|chat_template","text":"..."}
  if (message.startsWith("System prompt active: ") || message.startsWith("Chat template active: ")) {
    val isSystem = message.startsWith("System prompt")
    val text = if (!eventBody.isNullOrBlank()) {
      try { JSONObject(eventBody).optString("text", "") } catch (_: Exception) { "" }
    } else ""
    return ParsedEventType.PromptActive(
      promptType = if (isSystem) "System prompt" else "Chat template",
      promptText = text,
    )
  }

  // Model restart requested
  if (message == "Model restart requested") {
    return ParsedEventType.RestartRequested
  }

  // Unloading model during restart: "Unloading model: ModelName"
  if (message.startsWith("Unloading model: ")) {
    return ParsedEventType.Unloading(message.removePrefix("Unloading model: "))
  }

  // Keep-alive idle unload: "Model unloaded: ModelName (after Xm idle, keep_alive)"
  PATTERN_KEEP_ALIVE_UNLOADED.find(message)?.let {
    return ParsedEventType.KeepAliveUnloaded(it.groupValues[1], it.groupValues[2])
  }

  // Keep-alive auto-reload: "Auto-reloading model: ModelName (keep_alive wake-up)"
  if (message.startsWith("Auto-reloading model: ") && message.contains("keep_alive")) {
    val modelName = message.removePrefix("Auto-reloading model: ").substringBefore(" (")
    return ParsedEventType.KeepAliveReloading(modelName)
  }

  // Keep-alive reloaded: "Model reloaded: ModelName (Xms, keep_alive wake-up)"
  PATTERN_KEEP_ALIVE_RELOADED.find(message)?.let {
    return ParsedEventType.KeepAliveReloaded(it.groupValues[1], it.groupValues[2])
  }

  // Server stopped
  if (message == "Server stopped") {
    return ParsedEventType.ServerStopped
  }

  // Warmup skipped: "Warmup skipped — model loaded without test inference (disabled in Settings)"
  if (message.startsWith("Warmup skipped")) {
    // Extract everything after "Warmup skipped — " as the body text
    val body = message.removePrefix("Warmup skipped").trimStart(' ', '—', '-').trim()
    return ParsedEventType.WarmupSkipped(body)
  }

  // Model load failed: "Model load failed: <error>"
  if (message.startsWith("Model load failed: ")) {
    return ParsedEventType.ModelLoadFailed(message.removePrefix("Model load failed: "))
  }

  // Server failed to start: "Server failed to start on port <N>: <error>"
  if (message.startsWith("Server failed to start")) {
    return ParsedEventType.ServerFailed(message)
  }

  // Model not found: "Model '<name>' not found" or "Model files not found on disk"
  if (message.startsWith("Model") && message.contains("not found")) {
    // Extract quoted model name if present: "Model 'Foo' not found" → "Foo"
    val quoted = Regex("""'(.+?)'""").find(message)?.groupValues?.get(1)
    val detail = if (quoted != null) {
      "$quoted (not in downloaded or available models)"
    } else if (message.contains("files")) {
      "Model files missing from device storage"
    } else {
      message
    }
    return ParsedEventType.ModelNotFound(detail)
  }

  // Failed to decode image: "Failed to decode image: <error>"
  if (message.startsWith("Failed to decode image: ")) {
    return ParsedEventType.ImageDecodeFailed(message.removePrefix("Failed to decode image: "))
  }

  // Queued settings change: "Applying queued settings change — reloading model"
  if (message.startsWith("Applying queued settings change")) {
    return ParsedEventType.QueuedReload
  }

  // CORS changed: "CORS Allowed Origins changed: \"old\" → \"new\""
  if (message.startsWith("CORS Allowed Origins changed: ")) {
    val rest = message.removePrefix("CORS Allowed Origins changed: ")
    val parts = rest.split(" → ", limit = 2)
    val oldValue = parts.getOrElse(0) { "" }.trim('"')
    val newValue = parts.getOrElse(1) { "" }.trim('"')
    return ParsedEventType.CorsChanged(oldValue, newValue)
  }

  // Failed to reset conversation: "Failed to reset conversation: <error>"
  if (message.startsWith("Failed to reset conversation: ")) {
    return ParsedEventType.ConversationResetFailed(message.removePrefix("Failed to reset conversation: "))
  }

  // Settings changed via REST API: "Config via REST API (N changes)" or via Settings UI: "Settings updated (N changes)"
  // Both use the same body format: newline-separated "Name: old → new" lines.
  val isApiConfig = message.startsWith("Config via REST API (") && !eventBody.isNullOrBlank()
  val isUiSettings = message.startsWith("Settings updated (") && !eventBody.isNullOrBlank()
  if (isApiConfig || isUiSettings) {
    val changes = eventBody.lines().filter { it.isNotBlank() }.map { line ->
      // Parse "Name: old → new" or "Name: enabled/disabled" (no arrow)
      val arrowIdx = line.indexOf(" → ")
      if (arrowIdx >= 0) {
        // "Port: 8000 → 8001" → paramName="Port", old="8000", new="8001"
        val left = line.substring(0, arrowIdx)
        val newValue = line.substring(arrowIdx + 3)
        val colonIdx = left.indexOf(": ")
        if (colonIdx >= 0) {
          InferenceSettingsChange(left.substring(0, colonIdx), left.substring(colonIdx + 2), newValue)
        } else {
          InferenceSettingsChange(left, "", newValue)
        }
      } else {
        // "Auto-Start on Boot: enabled" → paramName="Auto-Start on Boot", old="", new="enabled"
        val colonIdx = line.indexOf(": ")
        if (colonIdx >= 0) {
          InferenceSettingsChange(line.substring(0, colonIdx), "", line.substring(colonIdx + 2))
        } else {
          InferenceSettingsChange(line, "", "")
        }
      }
    }
    if (changes.isNotEmpty()) {
      return if (isApiConfig) ParsedEventType.ApiConfigChange(changes)
      else ParsedEventType.SettingsBatch(changes)
    }
  }

  // Update available: "Update available: vX.Y.Z"
  if (message.startsWith("Update available: v")) {
    val version = message.removePrefix("Update available: ")
    return ParsedEventType.UpdateAvailable(version, eventBody)
  }

  // Already on latest version
  if (message == "Already on latest version") {
    return ParsedEventType.UpdateCurrent(eventBody)
  }

  // Update check auto-disabled
  if (message.startsWith("Update check auto-disabled")) {
    return ParsedEventType.UpdateAutoDisabled(eventBody)
  }

  // Audio transcription: "Audio transcription: ModelName (lang=en, wav, 245KB, 3.2s, forced)"
  PATTERN_AUDIO_TRANSCRIPTION.find(message)?.let {
    return ParsedEventType.AudioTranscription(
      modelName = it.groupValues[1],
      language = it.groupValues[2].ifEmpty { null },
      audioFormat = it.groupValues[3],
      fileSize = it.groupValues[4],
      durationSec = it.groupValues[5],
      forced = it.groupValues[6].isNotEmpty(),
    )
  }

  // Android memory pressure from onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL)
  if (message == "System memory pressure (critical)") {
    return ParsedEventType.MemoryPressure
  }

  // Settings toggle: "SettingName enabled" / "SettingName disabled"
  if (message.endsWith(" enabled") || message.endsWith(" disabled")) {
    val enabled = message.endsWith(" enabled")
    val name = message.removeSuffix(if (enabled) " enabled" else " disabled")
    // Sanity check: the name shouldn't be empty or overly long (avoid false matches)
    if (name.isNotBlank() && name.length < 80) {
      return ParsedEventType.SettingsToggle(name, enabled)
    }
  }

  return null
}

// ── Cached regex patterns for event parsing & highlighting ──────────────────
// Compiled once at class-load instead of per-render to avoid allocation in hot paths.
internal val PATTERN_MODEL_READY = Regex("""^Model ready: (.+?) \((\d+)ms\)$""")
internal val PATTERN_WARMUP = Regex("""^Sending a warmup message: "(.+?)" → "(.*)" \((\d+)ms\)$""")
internal val PATTERN_KEEP_ALIVE_UNLOADED = Regex("""^Model unloaded: (.+?) \(after (\d+)m idle, keep_alive\)$""")
internal val PATTERN_KEEP_ALIVE_RELOADED = Regex("""^Model reloaded: (.+?) \((\d+)ms, keep_alive wake-up\)$""")
internal val PATTERN_AUDIO_TRANSCRIPTION = Regex("""^Audio transcription: (.+?) \((?:lang=(\w+), )?(\w+), ([\d.]+[KM]B), ([\d.]+s)(, forced)?\)$""")
internal val PATTERN_TIME_MS = Regex("""\(\d+ms\)""")
internal val PATTERN_ARROW = Regex("""→""")
internal val PATTERN_QUOTED = Regex(""""[^"]*"""")

// ── Event text highlighting ──────────────────────────────────────────────────

/** Accent color for arrows in settings/event values. */
internal val ValueArrowColor = Color(0xFF64B5F6) // light blue
/** Color for quoted text in event messages. */
internal val QuotedTextColor = Color(0xFFCE93D8) // soft purple

/**
 * Highlights key values in event messages using AnnotatedString.
 * Styles: timing "(Xms)", quoted strings, arrows "→", and model names after known prefixes.
 */
internal fun highlightEventMessage(
  message: String,
  isError: Boolean,
  errorColor: Color,
): AnnotatedString {
  if (isError) {
    return buildAnnotatedString {
      withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.SemiBold)) { append(message) }
    }
  }

  data class StyledSpan(val start: Int, val end: Int, val style: SpanStyle)
  val spans = mutableListOf<StyledSpan>()
  val primaryColor = Color(0xFFAFC6FF)
  val greenColor = Color(0xFF4ADE80)
  val defaultText = Color(0xFFE5E2E3)

  PATTERN_TIME_MS.findAll(message).forEach {
    spans.add(StyledSpan(it.range.first, it.range.last + 1,
      SpanStyle(color = greenColor, fontWeight = FontWeight.SemiBold)))
  }
  PATTERN_ARROW.findAll(message).forEach {
    spans.add(StyledSpan(it.range.first, it.range.last + 1,
      SpanStyle(color = ValueArrowColor, fontWeight = FontWeight.Bold)))
  }
  PATTERN_QUOTED.findAll(message).forEach {
    spans.add(StyledSpan(it.range.first, it.range.last + 1,
      SpanStyle(color = QuotedTextColor)))
  }
  val modelPrefixes = listOf("Loading model: ", "Model ready: ", "Model load failed: ", "Audio transcription: ")
  for (prefix in modelPrefixes) {
    val idx = message.indexOf(prefix)
    if (idx >= 0) {
      val nameStart = idx + prefix.length
      val nameEnd = message.indexOf(" (", nameStart).let { if (it < 0) message.length else it }
      if (nameEnd > nameStart) {
        spans.add(StyledSpan(nameStart, nameEnd,
          SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)))
      }
    }
  }

  val nonOverlapping = mutableListOf<StyledSpan>()
  var lastEnd = 0
  for (span in spans.sortedBy { it.start }) {
    if (span.start >= lastEnd) { nonOverlapping.add(span); lastEnd = span.end }
  }
  return buildAnnotatedString {
    var pos = 0
    for (span in nonOverlapping) {
      if (span.start > pos) withStyle(SpanStyle(color = defaultText)) { append(message.substring(pos, span.start)) }
      withStyle(span.style) { append(message.substring(span.start, span.end)) }
      pos = span.end
    }
    if (pos < message.length) withStyle(SpanStyle(color = defaultText)) { append(message.substring(pos)) }
  }
}
