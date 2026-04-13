package com.ollitert.llm.server.ui.server

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.ui.common.copyToClipboard
import java.net.URLEncoder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.service.LlmHttpService
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.common.getWifiIpAddress
import com.ollitert.llm.server.service.ServerMetrics
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.worker.UpdateCheckWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.outlined.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction

/** Formats a duration in minutes into human-readable text (e.g. 10080 → "7 days", 120 → "2 hours", 45 → "45 minutes"). */
private fun formatMinutesHumanReadable(minutes: Long): String = when {
  minutes == 0L -> "disabled"
  minutes % (24 * 60) == 0L -> "${minutes / (24 * 60)} ${if (minutes / (24 * 60) == 1L) "day" else "days"}"
  minutes % 60 == 0L -> "${minutes / 60} ${if (minutes / 60 == 1L) "hour" else "hours"}"
  else -> "$minutes ${if (minutes == 1L) "minute" else "minutes"}"
}

/**
 * Validates CORS allowed origins input.
 * Valid formats: blank (disabled), "*" (allow all), or comma-separated origin URLs.
 * Each origin must have a scheme (http:// or https://) and a host.
 */
private fun isValidCorsOrigins(input: String): Boolean {
  val trimmed = input.trim()
  if (trimmed.isEmpty() || trimmed == "*") return true
  return trimmed.split(",").all { entry ->
    val origin = entry.trim()
    origin.isNotEmpty() && (origin.startsWith("http://") || origin.startsWith("https://")) &&
      origin.substringAfter("://").let { host ->
        // Must have at least a host portion (e.g. "localhost", "example.com", "192.168.1.1:3000")
        host.isNotEmpty() && !host.startsWith("/") && !host.contains(" ")
      }
  }
}

/** Highlights all occurrences of search query words in the given text with the specified color. */
private fun highlightSearchMatches(
  text: String,
  query: String,
  highlightColor: androidx.compose.ui.graphics.Color,
): AnnotatedString {
  if (query.isBlank()) return AnnotatedString(text)
  val words = query.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
  if (words.isEmpty()) return AnnotatedString(text)
  val textLower = text.lowercase()
  val ranges = mutableListOf<IntRange>()
  for (word in words) {
    var start = 0
    while (true) {
      val idx = textLower.indexOf(word, start)
      if (idx < 0) break
      ranges.add(idx until idx + word.length)
      start = idx + 1
    }
  }
  if (ranges.isEmpty()) return AnnotatedString(text)
  val merged = ranges.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
    if (acc.isEmpty() || acc.last().last < r.first - 1) acc.add(r)
    else acc[acc.lastIndex] = acc.last().first..maxOf(acc.last().last, r.last)
    acc
  }
  return buildAnnotatedString {
    append(text)
    for (range in merged) {
      addStyle(
        SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold),
        start = range.first,
        end = range.last + 1,
      )
    }
  }
}

/** Setting name text with search term highlighting. Replaces plain Text() for setting labels. */
@Composable
private fun SettingLabel(text: String, searchQuery: String) {
  Text(
    text = highlightSearchMatches(text, searchQuery, OlliteRTPrimary),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurface,
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  serverStatus: ServerStatus = ServerStatus.STOPPED,
  onRestartServer: () -> Unit = {},
  onStopServer: () -> Unit = {},
  onNavigateToModels: () -> Unit = {},
  downloadedModelNames: List<String> = emptyList(),
  onSetTopBarTrailingContent: ((@Composable () -> Unit)?) -> Unit = {},
) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val uriHandler = LocalUriHandler.current

  // ─── Unsaved Changes Guard ─────────────────────────────────────────────────
  // When adding a NEW setting, update ALL FOUR sections below:
  //   1. [SAVED STATE]     Add: var savedXxx by remember { mutableStateOf(LlmHttpPrefs.getXxx(context)) }
  //   2. [EDITABLE STATE]  Add: var xxx by remember { mutableStateOf(savedXxx) }
  //   3. [CHANGE DETECT]   Add: xxx != savedXxx  to the hasUnsavedChanges expression
  //   4. [SAVE HANDLER]    Add: LlmHttpPrefs.setXxx(context, xxx) + savedXxx = xxx
  // The floating banner, BackHandler, and discard dialog are driven by hasUnsavedChanges —
  // no other wiring is needed. Missing any of the 4 steps causes silent bugs:
  //   - Missing #1/#3: changes aren't detected → no save prompt, user loses edits on back-press
  //   - Missing #4 persist: save button does nothing for that field
  //   - Missing #4 update savedXxx: banner stays visible after saving ("phantom unsaved changes")
  // ──────────────────────────────────────────────────────────────────────────────

  // [SAVED STATE] Persisted values — used for change detection; updated after successful save
  var savedPort by remember { mutableStateOf(LlmHttpPrefs.getPort(context)) }
  var savedBearerToken by remember { mutableStateOf(LlmHttpPrefs.getBearerToken(context)) }
  var savedHfToken by remember { mutableStateOf(LlmHttpPrefs.getHfToken(context)) }
  var savedDefaultModelName by remember { mutableStateOf(LlmHttpPrefs.getDefaultModelName(context)) }
  var savedAutoStartOnBoot by remember { mutableStateOf(LlmHttpPrefs.isAutoStartOnBoot(context)) }
  var savedKeepScreenOn by remember { mutableStateOf(LlmHttpPrefs.isKeepScreenOn(context)) }
  var savedAutoExpandLogs by remember { mutableStateOf(LlmHttpPrefs.isAutoExpandLogs(context)) }
  var savedWarmupEnabled by remember { mutableStateOf(LlmHttpPrefs.isWarmupEnabled(context)) }
  var savedStreamLogsPreview by remember { mutableStateOf(LlmHttpPrefs.isStreamLogsPreview(context)) }
  var savedKeepPartialResponse by remember { mutableStateOf(LlmHttpPrefs.isKeepPartialResponse(context)) }
  var savedEagerVisionInit by remember { mutableStateOf(LlmHttpPrefs.isEagerVisionInit(context)) }
  var savedCustomPromptsEnabled by remember { mutableStateOf(LlmHttpPrefs.isCustomPromptsEnabled(context)) }
  var savedAutoTruncateHistory by remember { mutableStateOf(LlmHttpPrefs.isAutoTruncateHistory(context)) }
  var savedAutoTrimPrompts by remember { mutableStateOf(LlmHttpPrefs.isAutoTrimPrompts(context)) }
  var savedCompactToolSchemas by remember { mutableStateOf(LlmHttpPrefs.isCompactToolSchemas(context)) }
  var savedClearLogsOnStop by remember { mutableStateOf(LlmHttpPrefs.isClearLogsOnStop(context)) }
  var savedConfirmClearLogs by remember { mutableStateOf(LlmHttpPrefs.isConfirmClearLogs(context)) }
  var savedShowRequestTypes by remember { mutableStateOf(LlmHttpPrefs.isShowRequestTypes(context)) }
  var savedShowAdvancedMetrics by remember { mutableStateOf(LlmHttpPrefs.isShowAdvancedMetrics(context)) }
  var savedCorsAllowedOrigins by remember { mutableStateOf(LlmHttpPrefs.getCorsAllowedOrigins(context)) }
  var savedLogPersistenceEnabled by remember { mutableStateOf(LlmHttpPrefs.isLogPersistenceEnabled(context)) }
  var savedLogMaxEntries by remember { mutableStateOf(LlmHttpPrefs.getLogMaxEntries(context)) }
  var savedLogAutoDeleteMinutes by remember { mutableStateOf(LlmHttpPrefs.getLogAutoDeleteMinutes(context)) }
  var savedIgnoreClientSamplerParams by remember { mutableStateOf(LlmHttpPrefs.isIgnoreClientSamplerParams(context)) }
  var savedKeepAliveEnabled by remember { mutableStateOf(LlmHttpPrefs.isKeepAliveEnabled(context)) }
  var savedKeepAliveMinutes by remember { mutableStateOf(LlmHttpPrefs.getKeepAliveMinutes(context)) }
  var savedUpdateCheckEnabled by remember { mutableStateOf(LlmHttpPrefs.isUpdateCheckEnabled(context)) }
  var savedUpdateCheckIntervalHours by remember { mutableStateOf(LlmHttpPrefs.getUpdateCheckIntervalHours(context)) }

  // [EDITABLE STATE] Current (editable) state — see Unsaved Changes Guard comment above
  var portText by remember { mutableStateOf(savedPort.toString()) }
  var portError by remember { mutableStateOf(false) }
  var showRestartDialog by remember { mutableStateOf(false) }
  var bearerEnabled by remember { mutableStateOf(savedBearerToken.isNotBlank()) }
  var bearerToken by remember { mutableStateOf(savedBearerToken) }
  var hfToken by remember { mutableStateOf(savedHfToken) }
  var hfTokenVisible by remember { mutableStateOf(false) }
  var defaultModelName by remember { mutableStateOf(savedDefaultModelName) }
  var showModelDropdown by remember { mutableStateOf(false) }
  var autoStartOnBoot by remember { mutableStateOf(savedAutoStartOnBoot) }
  var keepScreenOn by remember { mutableStateOf(savedKeepScreenOn) }
  var autoExpandLogs by remember { mutableStateOf(savedAutoExpandLogs) }
  var warmupEnabled by remember { mutableStateOf(savedWarmupEnabled) }
  var streamLogsPreview by remember { mutableStateOf(savedStreamLogsPreview) }
  var keepPartialResponse by remember { mutableStateOf(savedKeepPartialResponse) }
  var eagerVisionInit by remember { mutableStateOf(savedEagerVisionInit) }
  var customPromptsEnabled by remember { mutableStateOf(savedCustomPromptsEnabled) }
  var autoTruncateHistory by remember { mutableStateOf(savedAutoTruncateHistory) }
  var autoTrimPrompts by remember { mutableStateOf(savedAutoTrimPrompts) }
  var compactToolSchemas by remember { mutableStateOf(savedCompactToolSchemas) }
  var clearLogsOnStop by remember { mutableStateOf(savedClearLogsOnStop) }
  var confirmClearLogs by remember { mutableStateOf(savedConfirmClearLogs) }
  var showRequestTypes by remember { mutableStateOf(savedShowRequestTypes) }
  var showAdvancedMetrics by remember { mutableStateOf(savedShowAdvancedMetrics) }
  var corsAllowedOrigins by remember { mutableStateOf(savedCorsAllowedOrigins) }
  var corsError by remember { mutableStateOf(false) }
  var logPersistenceEnabled by remember { mutableStateOf(savedLogPersistenceEnabled) }
  var logMaxEntries by remember { mutableStateOf(savedLogMaxEntries) }
  var logAutoDeleteMinutes by remember { mutableStateOf(savedLogAutoDeleteMinutes) }
  var ignoreClientSamplerParams by remember { mutableStateOf(savedIgnoreClientSamplerParams) }
  var keepAliveEnabled by remember { mutableStateOf(savedKeepAliveEnabled) }
  var keepAliveMinutes by remember { mutableStateOf(savedKeepAliveMinutes) }
  var keepAliveError by remember { mutableStateOf(false) }
  var updateCheckError by remember { mutableStateOf(false) }
  // Hoisted so the save handler can show unit-aware validation messages
  var keepAliveUnit by remember {
    mutableStateOf(if (savedKeepAliveMinutes > 0 && savedKeepAliveMinutes % 60 == 0) "hours" else "minutes")
  }
  var updateCheckEnabled by remember { mutableStateOf(savedUpdateCheckEnabled) }
  var updateCheckIntervalHours by remember { mutableStateOf(savedUpdateCheckIntervalHours) }
  var updateCheckUnit by remember {
    mutableStateOf(if (savedUpdateCheckIntervalHours > 0 && savedUpdateCheckIntervalHours % 24 == 0) "days" else "hours")
  }
  var showClearPersistedDialog by remember { mutableStateOf(false) }
  var showTrimLogsDialog by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }
  var verboseDebugEnabled by remember { mutableStateOf(LlmHttpPrefs.isVerboseDebugEnabled(context)) }

  // ─── Search / Filter ────────────────────────────────────────────────────────
  // Per-setting search index: each setting has searchable keywords (name + description).
  // Cards are visible when at least one of their settings matches the query.
  // Individual settings within a visible card are shown/hidden independently.
  var searchQuery by remember { mutableStateOf("") }

  // Per-setting keyword index — setting key → searchable text (name + description).
  // All text is lowercased at match time; stored as-is for readability.
  val settingSearchIndex = remember {
    mapOf(
      // General
      "keep_screen_awake" to "Keep Screen Awake Prevent screen from turning off while app is open",
      "auto_expand_logs" to "Auto-Expand Logs Show full request and response bodies in the Logs tab",
      "stream_response_preview" to "Stream Response Preview Show model output as it generates in the Logs tab for streaming requests",
      "clear_logs_on_stop" to "Clear Logs on Stop Automatically clear in-memory logs when the server stops",
      "confirm_clear_logs" to "Confirm Before Clearing Logs Show a confirmation dialog before clearing logs",
      "keep_partial_response" to "Keep Partial Response Preserve incomplete response text in logs when a streaming request is cancelled by the client",
      // HF Token
      "hf_token" to "Hugging Face Token HuggingFace hf download models authentication required",
      // Server Config
      "host_port" to "Host Port 1024 65535 server configuration default 8000 restart",
      "bearer_token" to "Require Bearer Token Protect API authentication Authorization header security",
      "cors_origins" to "CORS Allowed Origins cross-origin requests localhost",
      // Auto-Launch & Behavior
      "default_model" to "Default Model Automatically load model when app launches",
      "start_on_boot" to "Start on Boot Launch server automatically when device starts",
      "keep_alive" to "Keep Alive Unload model after idle timeout free RAM cold start Idle Timeout",
      "dontkillmyapp" to "Device background settings manufacturers kill background apps dontkillmyapp",
      "update_check" to "Check for Updates version new notification background frequency interval update available",
      // Metrics
      "show_request_types" to "Show Request Types text vision audio request counts Status screen",
      "show_advanced_metrics" to "Show Advanced Metrics prefill speed inter-token latency latency stats context utilization Status screen",
      // Log Persistence (master toggle + children treated as one group)
      "log_persistence" to "Log Persistence Persist Logs Database Maximum Log Entries Auto-Delete Clear All Logs storage oldest entries pruned survive app restarts",
      // Home Assistant
      "ha_integration" to "Home Assistant REST API Integration configuration yaml sensors commands stop reload thinking config",
      // Advanced
      "warmup_message" to "Warmup Message Send test message when model loads verify engine working startup",
      "pre_init_vision" to "Pre-initialize Vision Load vision backend multimodal model starts image request memory GPU",
      "custom_prompts" to "Custom System Prompt Chat Template per-model prompt formats Inference Settings",
      "truncate_history" to "Truncate Conversation History request exceeds context window drop older messages system prompts",
      "compact_tool_schemas" to "Compact Tool Schemas reduce tool schemas names descriptions context window Home Assistant tool definitions",
      "trim_prompt" to "Trim Prompt last resort hard-cuts prompt fit context window recent content discarding beginning",
      "ignore_client_params" to "Ignore Client Sampler Parameters Discard temperature top_p top_k max_tokens API clients server Inference Settings",
      // Developer
      "verbose_debug" to "Verbose Debug Mode Logs additional details stack traces memory snapshots model config per-request timing performance",
      // Reset
      "reset" to "Reset to Defaults reset all settings port token inference",
    )
  }

  // Which settings belong to which card — used to derive card visibility
  val settingsByCard = remember {
    mapOf(
      "general" to listOf("keep_screen_awake", "auto_expand_logs", "stream_response_preview", "clear_logs_on_stop", "confirm_clear_logs", "keep_partial_response"),
      "hf_token" to listOf("hf_token"),
      "server_config" to listOf("host_port", "bearer_token", "cors_origins"),
      "auto_launch" to listOf("default_model", "start_on_boot", "keep_alive", "dontkillmyapp", "update_check"),
      "metrics" to listOf("show_request_types", "show_advanced_metrics"),
      "log_persistence" to listOf("log_persistence"),
      "home_assistant" to listOf("ha_integration"),
      "advanced" to listOf("warmup_message", "pre_init_vision", "custom_prompts", "truncate_history", "compact_tool_schemas", "trim_prompt", "ignore_client_params"),
      "developer" to listOf("verbose_debug"),
      "reset" to listOf("reset"),
    )
  }

  /** Returns true if an individual setting matches the current search query. */
  fun settingVisible(settingKey: String): Boolean {
    if (searchQuery.isBlank()) return true
    val keywords = settingSearchIndex[settingKey] ?: return true
    val query = searchQuery.trim().lowercase()
    return query.split("\\s+".toRegex()).all { word ->
      keywords.lowercase().contains(word)
    }
  }

  /** Returns true if the card should be visible (any of its settings match). */
  fun cardVisible(cardKey: String): Boolean {
    if (searchQuery.isBlank()) return true
    return settingsByCard[cardKey]?.any { settingVisible(it) } ?: true
  }

  // [CHANGE DETECT] Unsaved changes detection — compare current vs persisted (see Unsaved Changes Guard)
  val effectiveBearerToken = if (bearerEnabled) bearerToken else ""
  val hasUnsavedChanges = portText != savedPort.toString() ||
    effectiveBearerToken != savedBearerToken ||
    hfToken != savedHfToken ||
    defaultModelName != savedDefaultModelName ||
    autoStartOnBoot != savedAutoStartOnBoot ||
    keepScreenOn != savedKeepScreenOn ||
    autoExpandLogs != savedAutoExpandLogs ||
    warmupEnabled != savedWarmupEnabled ||
    streamLogsPreview != savedStreamLogsPreview ||
    keepPartialResponse != savedKeepPartialResponse ||
    eagerVisionInit != savedEagerVisionInit ||
    customPromptsEnabled != savedCustomPromptsEnabled ||
    autoTruncateHistory != savedAutoTruncateHistory ||
    autoTrimPrompts != savedAutoTrimPrompts ||
    compactToolSchemas != savedCompactToolSchemas ||
    clearLogsOnStop != savedClearLogsOnStop ||
    confirmClearLogs != savedConfirmClearLogs ||
    showRequestTypes != savedShowRequestTypes ||
    showAdvancedMetrics != savedShowAdvancedMetrics ||
    corsAllowedOrigins != savedCorsAllowedOrigins ||
    logPersistenceEnabled != savedLogPersistenceEnabled ||
    logMaxEntries != savedLogMaxEntries ||
    logAutoDeleteMinutes != savedLogAutoDeleteMinutes ||
    ignoreClientSamplerParams != savedIgnoreClientSamplerParams ||
    keepAliveEnabled != savedKeepAliveEnabled ||
    keepAliveMinutes != savedKeepAliveMinutes ||
    updateCheckEnabled != savedUpdateCheckEnabled ||
    updateCheckIntervalHours != savedUpdateCheckIntervalHours

  // Discard confirmation dialog
  var showDiscardDialog by remember { mutableStateOf(false) }
  var showDonateDialog by remember { mutableStateOf(false) }

  // Intercept back navigation when there are unsaved changes
  BackHandler(enabled = hasUnsavedChanges) {
    showDiscardDialog = true
  }

  // [SAVE HANDLER] Save action — shared between top bar button and internal logic (see Unsaved Changes Guard)
  val saveSettings: () -> Unit = {
    if (portText.isBlank()) {
      portError = true
      Toast.makeText(context, "A port number is required", Toast.LENGTH_SHORT).show()
    } else if (portText.toIntOrNull().let { it == null || it !in 1024..65535 }) {
      portError = true
      Toast.makeText(context, "Port must be between 1024 and 65535", Toast.LENGTH_SHORT).show()
    } else if (!isValidCorsOrigins(corsAllowedOrigins)) {
      corsError = true
      Toast.makeText(context, "Invalid CORS origins — use *, blank, or comma-separated URLs with http(s)://", Toast.LENGTH_LONG).show()
    } else if (keepAliveEnabled && keepAliveMinutes !in 1..7200) {
      keepAliveError = true
      val rangeText = if (keepAliveUnit == "hours") "1 and 120 hours" else "1 and 7200 minutes"
      Toast.makeText(context, "Keep-alive timeout must be between $rangeText", Toast.LENGTH_SHORT).show()
    } else if (updateCheckEnabled && updateCheckIntervalHours !in 1..720) {
      updateCheckError = true
      val rangeText = if (updateCheckUnit == "days") "1 and 30 days" else "1 and 720 hours"
      Toast.makeText(context, "Update check interval must be between $rangeText", Toast.LENGTH_SHORT).show()
    } else {
      corsError = false
      keepAliveError = false
      updateCheckError = false
      val port = portText.toInt()
        val isPortChanged = port != savedPort
        val isEagerVisionChanged = eagerVisionInit != savedEagerVisionInit
        val needsRestart = isPortChanged || isEagerVisionChanged
        val isServerRunning = serverStatus == ServerStatus.RUNNING
        val isServerLoading = serverStatus == ServerStatus.LOADING
        val isServerActive = isServerRunning || isServerLoading

        LlmHttpPrefs.save(context, LlmHttpPrefs.isEnabled(context), port)
        if (bearerEnabled) {
          LlmHttpPrefs.setBearerToken(context, bearerToken)
        } else {
          LlmHttpPrefs.setBearerToken(context, "")
        }
        LlmHttpPrefs.setHfToken(context, hfToken)
        LlmHttpPrefs.setDefaultModelName(context, defaultModelName)
        LlmHttpPrefs.setAutoStartOnBoot(context, autoStartOnBoot)
        LlmHttpPrefs.setKeepScreenOn(context, keepScreenOn)
        LlmHttpPrefs.setAutoExpandLogs(context, autoExpandLogs)
        LlmHttpPrefs.setWarmupEnabled(context, warmupEnabled)
        LlmHttpPrefs.setStreamLogsPreview(context, streamLogsPreview)
        LlmHttpPrefs.setKeepPartialResponse(context, keepPartialResponse)
        LlmHttpPrefs.setEagerVisionInit(context, eagerVisionInit)
        LlmHttpPrefs.setCustomPromptsEnabled(context, customPromptsEnabled)
        LlmHttpPrefs.setAutoTruncateHistory(context, autoTruncateHistory)
        LlmHttpPrefs.setAutoTrimPrompts(context, autoTrimPrompts)
        LlmHttpPrefs.setCompactToolSchemas(context, compactToolSchemas)
        LlmHttpPrefs.setIgnoreClientSamplerParams(context, ignoreClientSamplerParams)
        LlmHttpPrefs.setKeepAliveEnabled(context, keepAliveEnabled)
        LlmHttpPrefs.setKeepAliveMinutes(context, keepAliveMinutes)
        // Poke the running service to reschedule (or cancel) the keep-alive idle timer
        // so changes take effect immediately without waiting for the next inference request.
        if ((keepAliveEnabled != savedKeepAliveEnabled || keepAliveMinutes != savedKeepAliveMinutes) && isServerActive) {
          LlmHttpService.resetKeepAliveTimer(context)
        }
        LlmHttpPrefs.setUpdateCheckEnabled(context, updateCheckEnabled)
        LlmHttpPrefs.setUpdateCheckIntervalHours(context, updateCheckIntervalHours)
        // Schedule or cancel the periodic update check worker based on the toggle state
        if (updateCheckEnabled != savedUpdateCheckEnabled || updateCheckIntervalHours != savedUpdateCheckIntervalHours) {
          if (updateCheckEnabled) {
            com.ollitert.llm.server.worker.UpdateCheckWorker.scheduleUpdateCheck(context)
          } else {
            com.ollitert.llm.server.worker.UpdateCheckWorker.cancelUpdateCheck(context)
          }
        }

        LlmHttpPrefs.setClearLogsOnStop(context, clearLogsOnStop)
        LlmHttpPrefs.setConfirmClearLogs(context, confirmClearLogs)
        LlmHttpPrefs.setShowRequestTypes(context, showRequestTypes)
        LlmHttpPrefs.setShowAdvancedMetrics(context, showAdvancedMetrics)
        LlmHttpPrefs.setCorsAllowedOrigins(context, corsAllowedOrigins)
        LlmHttpPrefs.setLogPersistenceEnabled(context, logPersistenceEnabled)
        LlmHttpPrefs.setLogMaxEntries(context, logMaxEntries)
        LlmHttpPrefs.setLogAutoDeleteMinutes(context, logAutoDeleteMinutes)

        // Collect all behavioral settings changes into one grouped log entry
        val changes = mutableListOf<String>()
        if (port != savedPort) changes.add("Port: $savedPort → $port")
        val bearerWasEnabled = savedBearerToken.isNotBlank()
        val bearerIsEnabled = effectiveBearerToken.isNotBlank()
        if (bearerWasEnabled != bearerIsEnabled)
          changes.add("Bearer Auth: ${if (bearerWasEnabled) "enabled" else "disabled"} → ${if (bearerIsEnabled) "enabled" else "disabled"}")
        if (autoStartOnBoot != savedAutoStartOnBoot)
          changes.add("Auto-Start on Boot: ${if (savedAutoStartOnBoot) "enabled" else "disabled"} → ${if (autoStartOnBoot) "enabled" else "disabled"}")
        if (warmupEnabled != savedWarmupEnabled)
          changes.add("Warmup Message: ${if (savedWarmupEnabled) "enabled" else "disabled"} → ${if (warmupEnabled) "enabled" else "disabled"}")
        if (eagerVisionInit != savedEagerVisionInit)
          changes.add("Pre-initialize Vision: ${if (savedEagerVisionInit) "enabled" else "disabled"} → ${if (eagerVisionInit) "enabled" else "disabled"}")
        if (customPromptsEnabled != savedCustomPromptsEnabled)
          changes.add("Custom System Prompt & Chat Template: ${if (savedCustomPromptsEnabled) "enabled" else "disabled"} → ${if (customPromptsEnabled) "enabled" else "disabled"}")
        if (ignoreClientSamplerParams != savedIgnoreClientSamplerParams)
          changes.add("Ignore Client Sampler Parameters: ${if (savedIgnoreClientSamplerParams) "enabled" else "disabled"} → ${if (ignoreClientSamplerParams) "enabled" else "disabled"}")
        if (autoTruncateHistory != savedAutoTruncateHistory)
          changes.add("Truncate Conversation History: ${if (savedAutoTruncateHistory) "enabled" else "disabled"} → ${if (autoTruncateHistory) "enabled" else "disabled"}")
        if (compactToolSchemas != savedCompactToolSchemas)
          changes.add("Compact Tool Schemas: ${if (savedCompactToolSchemas) "enabled" else "disabled"} → ${if (compactToolSchemas) "enabled" else "disabled"}")
        if (autoTrimPrompts != savedAutoTrimPrompts)
          changes.add("Trim Prompt: ${if (savedAutoTrimPrompts) "enabled" else "disabled"} → ${if (autoTrimPrompts) "enabled" else "disabled"}")
        if (corsAllowedOrigins != savedCorsAllowedOrigins) {
          val oldDisplay = savedCorsAllowedOrigins.ifBlank { "disabled" }
          val newDisplay = corsAllowedOrigins.ifBlank { "disabled" }
          changes.add("CORS Allowed Origins: $oldDisplay → $newDisplay")
        }
        if (logPersistenceEnabled != savedLogPersistenceEnabled)
          changes.add("Log Persistence: ${if (savedLogPersistenceEnabled) "enabled" else "disabled"} → ${if (logPersistenceEnabled) "enabled" else "disabled"}")
        if (logMaxEntries != savedLogMaxEntries)
          changes.add("Log Max Entries: $savedLogMaxEntries → $logMaxEntries")
        if (logAutoDeleteMinutes != savedLogAutoDeleteMinutes)
          changes.add("Log Auto-Delete: ${formatMinutesHumanReadable(savedLogAutoDeleteMinutes)} → ${formatMinutesHumanReadable(logAutoDeleteMinutes)}")
        if (keepAliveEnabled != savedKeepAliveEnabled)
          changes.add("Keep Alive: ${if (savedKeepAliveEnabled) "enabled" else "disabled"} → ${if (keepAliveEnabled) "enabled" else "disabled"}")
        if (keepAliveMinutes != savedKeepAliveMinutes)
          changes.add("Keep Alive Timeout: ${savedKeepAliveMinutes}m → ${keepAliveMinutes}m")
        if (updateCheckEnabled != savedUpdateCheckEnabled)
          changes.add("Check for Updates: ${if (savedUpdateCheckEnabled) "enabled" else "disabled"} → ${if (updateCheckEnabled) "enabled" else "disabled"}")
        if (updateCheckIntervalHours != savedUpdateCheckIntervalHours) {
          fun formatIntervalHuman(hours: Int): String = when {
            hours % 24 == 0 -> "${hours / 24} ${if (hours / 24 == 1) "day" else "days"}"
            else -> "$hours ${if (hours == 1) "hour" else "hours"}"
          }
          changes.add("Update Check Frequency: ${formatIntervalHuman(savedUpdateCheckIntervalHours)} → ${formatIntervalHuman(updateCheckIntervalHours)}")
        }
        if (changes.isNotEmpty()) {
          RequestLogStore.addEvent(
            "Settings updated (${changes.size} ${if (changes.size == 1) "change" else "changes"})",
            category = EventCategory.SETTINGS,
            body = changes.joinToString("\n"),
          )
        }

        // Access the persistence layer to sync settings
        val persistenceEntryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
          context.applicationContext,
          com.ollitert.llm.server.OlliteRTApplication.PersistenceEntryPoint::class.java,
        )
        val persistence = persistenceEntryPoint.requestLogPersistence()

        // If persistence was just enabled, persist current in-memory entries to DB
        if (logPersistenceEnabled && !savedLogPersistenceEnabled) {
          persistence.persistCurrentEntries()
        }
        // Always sync the in-memory cap with the persistence setting
        persistence.updateMaxEntries()

        // Apply keep-screen-on immediately
        val window = (context as? android.app.Activity)?.window
        if (keepScreenOn) {
          window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
          window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // [SAVE HANDLER — update savedXxx] Reset change detection (see Unsaved Changes Guard)
        savedPort = port
        savedBearerToken = if (bearerEnabled) bearerToken else ""
        savedHfToken = hfToken
        savedDefaultModelName = defaultModelName
        savedAutoStartOnBoot = autoStartOnBoot
        savedKeepScreenOn = keepScreenOn
        savedAutoExpandLogs = autoExpandLogs
        savedWarmupEnabled = warmupEnabled
        savedStreamLogsPreview = streamLogsPreview
        savedKeepPartialResponse = keepPartialResponse
        savedEagerVisionInit = eagerVisionInit
        savedCustomPromptsEnabled = customPromptsEnabled
        savedAutoTruncateHistory = autoTruncateHistory
        savedAutoTrimPrompts = autoTrimPrompts
        savedCompactToolSchemas = compactToolSchemas
        savedClearLogsOnStop = clearLogsOnStop
        savedConfirmClearLogs = confirmClearLogs
        savedShowRequestTypes = showRequestTypes
        savedShowAdvancedMetrics = showAdvancedMetrics
        savedCorsAllowedOrigins = corsAllowedOrigins
        savedLogPersistenceEnabled = logPersistenceEnabled
        savedLogMaxEntries = logMaxEntries
        savedLogAutoDeleteMinutes = logAutoDeleteMinutes
        savedIgnoreClientSamplerParams = ignoreClientSamplerParams
        savedKeepAliveEnabled = keepAliveEnabled
        savedKeepAliveMinutes = keepAliveMinutes
        savedUpdateCheckEnabled = updateCheckEnabled
        savedUpdateCheckIntervalHours = updateCheckIntervalHours

        if (needsRestart && isServerActive) {
          showRestartDialog = true
        } else {
          Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
        }
      }
  }

  // Wrapper that warns if saving would trim existing logs
  val trySave: () -> Unit = {
    val currentCount = RequestLogStore.entries.value.size
    if (logMaxEntries < currentCount && logMaxEntries != savedLogMaxEntries) {
      showTrimLogsDialog = true
    } else {
      saveSettings()
    }
  }

  // Inject save button into the top bar
  val currentSaveSettings by rememberUpdatedState(trySave)
  DisposableEffect(Unit) {
    onSetTopBarTrailingContent {
      TooltipIconButton(
        icon = Icons.Outlined.Save,
        tooltip = "Save settings",
        onClick = { currentSaveSettings() },
        tint = OlliteRTPrimary,
      )
    }
    onDispose { onSetTopBarTrailingContent(null) }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .imePadding(),
    contentAlignment = Alignment.TopCenter,
  ) {
  Column(
    modifier = Modifier
      .widthIn(max = 840.dp)
      .fillMaxWidth()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 16.dp)
      .padding(bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Heading
    Text(
      text = "Global Settings",
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = "Configure server behavior, authentication, and preferences.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Search bar — always visible, filters settings cards and individual settings within cards
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
      placeholder = { Text("Search settings...", style = MaterialTheme.typography.bodyLarge) },
      leadingIcon = {
        Icon(
          Icons.Outlined.Search,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(
              Icons.Outlined.Close,
              contentDescription = "Clear search",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(16.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedBorderColor = OlliteRTPrimary,
        unfocusedBorderColor = Color.Transparent,
        cursorColor = OlliteRTPrimary,
      ),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
    )

    // "No results" message when search has no matches
    if (searchQuery.isNotBlank() && settingsByCard.keys.none { cardVisible(it) }) {
      Text(
        text = "No settings match \"$searchQuery\"",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally),
      )
    }

    // General card
    AnimatedVisibility(
      visible = cardVisible("general"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.PhoneAndroid,
      title = "General",
      searchQuery = searchQuery,
    ) {
      // Divider logic: only show between consecutive visible settings
      val generalKeys = listOf("keep_screen_awake", "auto_expand_logs", "stream_response_preview", "clear_logs_on_stop", "confirm_clear_logs", "keep_partial_response")
      val generalVisible = generalKeys.map { settingVisible(it) }

      fun showGeneralDivider(index: Int): Boolean {
        if (!generalVisible[index]) return false
        return (0 until index).any { generalVisible[it] }
      }

      // Keep screen awake toggle
      if (settingVisible("keep_screen_awake")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Keep Screen Awake", searchQuery = searchQuery)
          Text(
            text = "Prevent screen from turning off while app is open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = keepScreenOn,
          onCheckedChange = { enabled ->
            keepScreenOn = enabled
          },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: keep_screen_awake

      if (showGeneralDivider(1)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Auto-expand logs toggle
      if (settingVisible("auto_expand_logs")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Auto-Expand Logs", searchQuery = searchQuery)
          Text(
            text = "Show full request and response bodies in the Logs tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = autoExpandLogs,
          onCheckedChange = { autoExpandLogs = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: auto_expand_logs

      if (showGeneralDivider(2)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Stream response preview toggle
      if (settingVisible("stream_response_preview")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Stream Response Preview", searchQuery = searchQuery)
          Text(
            text = "Show model output as it generates in the Logs tab for streaming requests.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = streamLogsPreview,
          onCheckedChange = { streamLogsPreview = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: stream_response_preview

      if (showGeneralDivider(3)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Clear logs on stop toggle
      if (settingVisible("clear_logs_on_stop")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Clear Logs on Stop", searchQuery = searchQuery)
          Text(
            text = "Automatically clear in-memory logs when the server stops.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = clearLogsOnStop,
          onCheckedChange = { clearLogsOnStop = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: clear_logs_on_stop

      if (showGeneralDivider(4)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Confirm before clearing logs
      if (settingVisible("confirm_clear_logs")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Confirm Before Clearing Logs", searchQuery = searchQuery)
          Text(
            text = "Show a confirmation dialog before clearing logs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = confirmClearLogs,
          onCheckedChange = { confirmClearLogs = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: confirm_clear_logs

      if (showGeneralDivider(5)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Keep partial response toggle
      if (settingVisible("keep_partial_response")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Keep Partial Response", searchQuery = searchQuery)
          Text(
            text = "Preserve incomplete response text in logs when a streaming request is cancelled by the client.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = keepPartialResponse,
          onCheckedChange = { keepPartialResponse = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: keep_partial_response

    }
    } // AnimatedVisibility: General

    // Hugging Face Token card
    AnimatedVisibility(
      visible = cardVisible("hf_token"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Key,
      title = "Hugging Face Token",
      searchQuery = searchQuery,
    ) {
      Text(
        text = "Required for downloading models from Hugging Face. Get your token from",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = buildAnnotatedString {
          withLink(
            LinkAnnotation.Url(
              url = "https://huggingface.co/settings/tokens",
              styles = TextLinkStyles(
                style = SpanStyle(
                  color = OlliteRTPrimary,
                  textDecoration = TextDecoration.Underline,
                ),
              ),
            ),
          ) {
            append("huggingface.co/settings/tokens")
          }
        },
        style = MaterialTheme.typography.bodySmall,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = hfToken,
        onValueChange = { hfToken = it.trim() },
        singleLine = true,
        visualTransformation = if (hfTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
        placeholder = {
          Text(
            "hf_...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        trailingIcon = {
          Row {
            IconButton(onClick = { hfTokenVisible = !hfTokenVisible }) {
              Icon(
                imageVector = if (hfTokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (hfTokenVisible) "Hide token" else "Show token",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (hfToken.isNotBlank()) {
              IconButton(onClick = {
                hfToken = ""
                Toast.makeText(context, "Token cleared", Toast.LENGTH_SHORT).show()
              }) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = "Clear token",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        },
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = OlliteRTPrimary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    } // AnimatedVisibility: HF Token

    // Server Config card
    AnimatedVisibility(
      visible = cardVisible("server_config"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Tune,
      title = "Server Configuration",
      searchQuery = searchQuery,
    ) {
      if (settingVisible("host_port")) {
      Text(
        text = highlightSearchMatches("Host Port (1024–65535)", searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = portText,
        onValueChange = { input ->
          // Allow only digits, let user freely type/delete
          portText = input.filter { it.isDigit() }.take(5)
          portError = false
        },
        singleLine = true,
        isError = portError,
        placeholder = {
          Text(
            "8000",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (portError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (portError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Default: 8000. Requires server restart to take effect.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      } // if: host_port

      if (settingVisible("host_port") && settingVisible("bearer_token")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Bearer token toggle
      if (settingVisible("bearer_token")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Require Bearer Token", searchQuery = searchQuery)
          Text(
            text = "Protect the API with a bearer token. Clients must include it in the Authorization header.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = bearerEnabled,
          onCheckedChange = { enabled ->
            bearerEnabled = enabled
            if (enabled && bearerToken.isBlank()) {
              bearerToken = java.util.UUID.randomUUID().toString().replace("-", "")
            }
          },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: bearer_token

      if (settingVisible("bearer_token") && settingVisible("cors_origins")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // CORS allowed origins
      if (settingVisible("cors_origins")) {
      Text(
        text = highlightSearchMatches("CORS Allowed Origins", searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = corsAllowedOrigins,
        onValueChange = {
          corsAllowedOrigins = it
          // Clear error as soon as the user edits the field
          if (corsError) corsError = false
        },
        singleLine = true,
        isError = corsError,
        placeholder = {
          Text(
            "*",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        trailingIcon = {
          if (corsAllowedOrigins.isNotBlank()) {
            IconButton(onClick = {
              corsAllowedOrigins = ""
              if (corsError) corsError = false
            }) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Clear origins",
                tint = if (corsError) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (corsError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (corsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Origins allowed to make cross-origin requests. Use * to allow all, comma-separated URLs for specific origins (e.g. http://localhost:3000, https://my-app.com), or leave blank to disable CORS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      } // if: cors_origins

      // Token display + actions (only when bearer is enabled and setting is visible)
      if (bearerEnabled && settingVisible("bearer_token")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))

        // Token value in a copyable box
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = bearerToken,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.width(8.dp))

          // Copy button
          TooltipIconButton(
            icon = Icons.Outlined.ContentCopy,
            tooltip = "Copy token",
            onClick = {
              copyToClipboard(context, "OlliteRT Bearer Token", bearerToken)
            },
          )

          Spacer(modifier = Modifier.width(4.dp))

          // Regenerate button
          TooltipIconButton(
            icon = Icons.Outlined.Refresh,
            tooltip = "Regenerate token",
            onClick = {
              bearerToken = java.util.UUID.randomUUID().toString().replace("-", "")
              Toast.makeText(context, "Token regenerated — save to apply", Toast.LENGTH_SHORT).show()
            },
          )
        }
      }
    }
    } // AnimatedVisibility: Server Config

    // Auto-Launch & Behavior card
    AnimatedVisibility(
      visible = cardVisible("auto_launch"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.PlayArrow,
      title = "Auto-Launch & Behavior",
      searchQuery = searchQuery,
    ) {
      // Default model picker
      if (settingVisible("default_model")) {
      Text(
        text = highlightSearchMatches("Default Model", searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      if (downloadedModelNames.isEmpty()) {
        Text(
          text = "No downloaded models. Download a model first from the Models tab.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      } else {
        // Dropdown trigger
        Column {
          OutlinedTextField(
            value = defaultModelName ?: "None (manual start)",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { showModelDropdown = true },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
              disabledTextColor = MaterialTheme.colorScheme.onSurface,
              disabledBorderColor = MaterialTheme.colorScheme.outline,
              disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
          )
          DropdownMenu(
            expanded = showModelDropdown,
            onDismissRequest = { showModelDropdown = false },
          ) {
            DropdownMenuItem(
              text = {
                Text(
                  "None (manual start)",
                  color = if (defaultModelName == null) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
                )
              },
              onClick = {
                defaultModelName = null
                autoStartOnBoot = false  // Can't auto-start without a default model
                showModelDropdown = false
              },
            )
            HorizontalDivider()
            downloadedModelNames.forEach { modelName ->
              DropdownMenuItem(
                text = {
                  Text(
                    modelName,
                    color = if (modelName == defaultModelName) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
                  )
                },
                onClick = {
                  defaultModelName = modelName
                  showModelDropdown = false
                },
              )
            }
          }
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Automatically load this model when the app is launched.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      } // if: default_model

      if (settingVisible("default_model") && settingVisible("start_on_boot")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Auto-start on boot toggle — entire row dims when no default model is selected
      if (settingVisible("start_on_boot")) {
      val autoStartAlpha = if (defaultModelName != null) 1f else 0.4f
      Row(
        modifier = Modifier.fillMaxWidth().alpha(autoStartAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Start on Boot", searchQuery = searchQuery)
          Text(
            text = if (defaultModelName == null) "Select a default model above to enable."
                   else "Launch server automatically when device starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = autoStartOnBoot,
          onCheckedChange = { enabled ->
            autoStartOnBoot = enabled
          },
          enabled = defaultModelName != null,
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: start_on_boot

      if (settingVisible("start_on_boot") && settingVisible("keep_alive")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Keep Alive — auto-unload model after idle timeout to free RAM
      if (settingVisible("keep_alive")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Keep Alive", searchQuery = searchQuery)
          Text(
            text = "Unload model after idle timeout to free RAM. Next request auto-reloads (cold start).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = keepAliveEnabled,
          onCheckedChange = { keepAliveEnabled = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }

      // Idle timeout child control — dimmed when keep alive is disabled
      val keepAliveChildAlpha = if (keepAliveEnabled) 1f else 0.4f

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(8.dp))

      Column(modifier = Modifier.alpha(keepAliveChildAlpha)) {
        Text(
          text = highlightSearchMatches("Idle Timeout", searchQuery, OlliteRTPrimary),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        val keepAliveTimeoutUnits = listOf("minutes", "hours")
        val initialKeepAliveValue = remember(savedKeepAliveMinutes) {
          val mins = savedKeepAliveMinutes
          if (mins > 0 && mins % 60 == 0) (mins / 60).toString() else mins.toString()
        }
        var keepAliveValueText by remember { mutableStateOf(initialKeepAliveValue) }
        var showKeepAliveUnitDropdown by remember { mutableStateOf(false) }

        fun recomputeKeepAliveMinutes() {
          val num = keepAliveValueText.toIntOrNull() ?: 0
          val totalMinutes = when (keepAliveUnit) {
            "hours" -> num * 60
            else -> num
          }
          keepAliveMinutes = totalMinutes
          if (keepAliveError) keepAliveError = false
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = keepAliveValueText,
            onValueChange = { text ->
              val filtered = text.filter { it.isDigit() }.take(4)
              keepAliveValueText = filtered
              recomputeKeepAliveMinutes()
            },
            singleLine = true,
            isError = keepAliveError,
            enabled = keepAliveEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = if (keepAliveError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
              unfocusedBorderColor = if (keepAliveError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
              cursorColor = OlliteRTPrimary,
            ),
          )
          // Unit selector dropdown
          Column {
            OutlinedTextField(
              value = keepAliveUnit,
              onValueChange = {},
              readOnly = true,
              singleLine = true,
              modifier = Modifier
                .widthIn(min = 90.dp, max = 120.dp)
                .clickable(enabled = keepAliveEnabled) {
                  focusManager.clearFocus()
                  showKeepAliveUnitDropdown = true
                },
              enabled = false,
              colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              ),
            )
            DropdownMenu(
              expanded = showKeepAliveUnitDropdown,
              onDismissRequest = { showKeepAliveUnitDropdown = false },
            ) {
              keepAliveTimeoutUnits.forEach { unit ->
                DropdownMenuItem(
                  text = {
                    Text(
                      unit,
                      color = if (unit == keepAliveUnit) OlliteRTPrimary
                              else MaterialTheme.colorScheme.onSurface,
                    )
                  },
                  onClick = {
                    keepAliveUnit = unit
                    showKeepAliveUnitDropdown = false
                    recomputeKeepAliveMinutes()
                  },
                )
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "How long the model stays loaded after the last request. When the timeout expires, native memory is freed. The next request triggers an automatic reload (5–30s cold start depending on model size).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      } // if: keep_alive

      if (settingVisible("keep_alive") && settingVisible("dontkillmyapp")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Link to dontkillmyapp.com — OEM-specific battery/background kill settings
      if (settingVisible("dontkillmyapp")) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable { uriHandler.openUri("https://dontkillmyapp.com") }
          .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = highlightSearchMatches("Device background settings", searchQuery, OlliteRTPrimary),
            style = MaterialTheme.typography.bodyMedium,
            color = OlliteRTPrimary,
          )
          Text(
            text = "Some manufacturers kill background apps aggressively. Check dontkillmyapp.com for device-specific fixes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = "Open dontkillmyapp.com",
          tint = OlliteRTPrimary,
          modifier = Modifier.size(18.dp),
        )
      }
      } // if: dontkillmyapp

      if ((settingVisible("dontkillmyapp") || settingVisible("keep_alive")) && settingVisible("update_check")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Check for Updates — manual check always available, automatic scheduling is separate
      if (settingVisible("update_check")) {

      // Observe update availability from ServerMetrics to swap refresh → download icon
      val availableVersion by ServerMetrics.availableUpdateVersion.collectAsState()
      val availableUrl by ServerMetrics.availableUpdateUrl.collectAsState()
      val hasUpdate = availableVersion != null

      // Track the work request ID to observe results and show toast feedback
      var checkWorkId by remember { mutableStateOf<java.util.UUID?>(null) }
      val workManager = remember { WorkManager.getInstance(context) }

      // Observe work completion and show result toast.
      // Includes a 15-second timeout for cases where WorkManager can't run
      // the work (e.g. no network — work stays ENQUEUED indefinitely).
      checkWorkId?.let { id ->
        val workInfo by workManager.getWorkInfoByIdFlow(id).collectAsState(initial = null)
        LaunchedEffect(workInfo?.state) {
          val info = workInfo ?: return@LaunchedEffect
          if (info.state.isFinished) {
            val message = info.outputData.getString(UpdateCheckWorker.KEY_MESSAGE)
            if (message != null) {
              Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            checkWorkId = null
          }
        }
        // Timeout: if work hasn't finished after 15s, show error and stop observing
        LaunchedEffect(id) {
          kotlinx.coroutines.delay(15_000)
          if (checkWorkId == id) {
            Toast.makeText(context, "Update check timed out — check your network connection", Toast.LENGTH_SHORT).show()
            workManager.cancelWorkById(id)
            checkWorkId = null
          }
        }
      }

      // Manual check — always available regardless of automatic toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Check for Updates", searchQuery = searchQuery)
          Text(
            text = "Check for a newer version of OlliteRT.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (hasUpdate && !availableUrl.isNullOrBlank()) {
          // Update available — show download button that opens Play Store or GitHub
          val uriHandler = LocalUriHandler.current
          val url = availableUrl!! // Safe — guarded by isNullOrBlank check above
          TooltipIconButton(
            icon = Icons.Outlined.FileDownload,
            tooltip = "Download $availableVersion",
            onClick = {
              val intent = UpdateCheckWorker.buildUpdateIntent(context, url)
              intent.data?.let { uri -> uriHandler.openUri(uri.toString()) }
            },
          )
        } else {
          // No update pending — show check now button
          TooltipIconButton(
            icon = Icons.Outlined.Refresh,
            tooltip = "Check now",
            onClick = {
              Toast.makeText(context, "Checking for updates…", Toast.LENGTH_SHORT).show()
              checkWorkId = UpdateCheckWorker.checkNow(context)
            },
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // Automatic update check — gated behind notification permissions
      val notifPermissionGranted = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
      val updateChannelMuted = UpdateCheckWorker.isUpdateChannelMuted(context)
      val updateControlsEnabled = notifPermissionGranted && !updateChannelMuted

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Automatic Update Check", searchQuery = searchQuery)
          Text(
            text = "Periodically check in the background and notify when an update is available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = updateCheckEnabled,
          onCheckedChange = { updateCheckEnabled = it },
          enabled = updateControlsEnabled,
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }

      // Notification permission/channel warning
      if (!notifPermissionGranted) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Notification access not enabled. Grant notification permission in system settings to receive update alerts.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      } else if (updateChannelMuted) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Update notification channel is muted in system settings.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.clickable {
            val intent = android.content.Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
              putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
              putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, UpdateCheckWorker.UPDATE_CHANNEL_ID)
            }
            context.startActivity(intent)
          },
        )
      }

      // Frequency input — dimmed when toggle is off or permission missing
      val updateChildAlpha = if (updateCheckEnabled && updateControlsEnabled) 1f else 0.4f

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(8.dp))

      Column(modifier = Modifier.alpha(updateChildAlpha)) {
        Text(
          text = highlightSearchMatches("Check Frequency", searchQuery, OlliteRTPrimary),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        val updateCheckUnits = listOf("hours", "days")
        val initialUpdateValue = remember(savedUpdateCheckIntervalHours) {
          val h = savedUpdateCheckIntervalHours
          if (h > 0 && h % 24 == 0) (h / 24).toString() else h.toString()
        }
        var updateValueText by remember { mutableStateOf(initialUpdateValue) }
        var showUpdateUnitDropdown by remember { mutableStateOf(false) }

        fun recomputeUpdateHours() {
          val num = updateValueText.toIntOrNull() ?: 0
          val totalHours = when (updateCheckUnit) {
            "days" -> num * 24
            else -> num
          }
          updateCheckIntervalHours = totalHours
          if (updateCheckError) updateCheckError = false
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedTextField(
            value = updateValueText,
            onValueChange = { text ->
              val filtered = text.filter { it.isDigit() }.take(4)
              updateValueText = filtered
              recomputeUpdateHours()
            },
            singleLine = true,
            isError = updateCheckError,
            enabled = updateCheckEnabled && updateControlsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = if (updateCheckError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
              unfocusedBorderColor = if (updateCheckError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
              cursorColor = OlliteRTPrimary,
            ),
          )
          // Unit selector dropdown
          Column {
            OutlinedTextField(
              value = updateCheckUnit,
              onValueChange = {},
              readOnly = true,
              singleLine = true,
              modifier = Modifier
                .widthIn(min = 90.dp, max = 120.dp)
                .clickable(enabled = updateCheckEnabled && updateControlsEnabled) {
                  focusManager.clearFocus()
                  showUpdateUnitDropdown = true
                },
              enabled = false,
              colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              ),
            )
            DropdownMenu(
              expanded = showUpdateUnitDropdown,
              onDismissRequest = { showUpdateUnitDropdown = false },
            ) {
              updateCheckUnits.forEach { unit ->
                DropdownMenuItem(
                  text = {
                    Text(
                      unit,
                      color = if (unit == updateCheckUnit) OlliteRTPrimary
                              else MaterialTheme.colorScheme.onSurface,
                    )
                  },
                  onClick = {
                    updateCheckUnit = unit
                    showUpdateUnitDropdown = false
                    recomputeUpdateHours()
                  },
                )
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "How often to check for new releases. Default: 24 hours. Min: 1 hour, Max: 30 days.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      } // if: update_check

    }
    } // AnimatedVisibility: Auto-Launch

    // Metrics card
    AnimatedVisibility(
      visible = cardVisible("metrics"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.BarChart,
      title = "Metrics",
      searchQuery = searchQuery,
    ) {
      // Show Request Types on Status screen
      if (settingVisible("show_request_types")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Show Request Types", searchQuery = searchQuery)
          Text(
            text = "Show text, vision, and audio request counts on the Status screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = showRequestTypes,
          onCheckedChange = { showRequestTypes = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: show_request_types

      if (settingVisible("show_request_types") && settingVisible("show_advanced_metrics")) {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))
      }

      // Show Advanced Metrics on Status screen
      if (settingVisible("show_advanced_metrics")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Show Advanced Metrics", searchQuery = searchQuery)
          Text(
            text = "Display prefill speed, inter-token latency, latency stats, and context utilization on the Status screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = showAdvancedMetrics,
          onCheckedChange = { showAdvancedMetrics = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: show_advanced_metrics
    }
    } // AnimatedVisibility: Metrics

    // Log Persistence card
    AnimatedVisibility(
      visible = cardVisible("log_persistence"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Storage,
      title = "Log Persistence",
      searchQuery = searchQuery,
    ) {
      // Master toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Persist Logs to Database", searchQuery = searchQuery)
          Text(
            text = "Save activity logs to a local database so they survive app restarts. Disabled by default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = logPersistenceEnabled,
          onCheckedChange = { logPersistenceEnabled = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }

      // Child controls — disabled (greyed out) when master toggle is OFF
      val childAlpha = if (logPersistenceEnabled) 1f else 0.4f

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(8.dp))

      // Max Entries — simple number input, value updates live into unsaved state
      Column(modifier = Modifier.alpha(childAlpha)) {
        Text(
          text = highlightSearchMatches("Maximum Log Entries", searchQuery, OlliteRTPrimary),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Track as text so the user can freely edit (e.g. delete "500" and type "200")
        var maxEntriesText by remember { mutableStateOf(logMaxEntries.toString()) }
        OutlinedTextField(
          value = maxEntriesText,
          onValueChange = { text ->
            val filtered = text.filter { it.isDigit() }.take(5) // max 5 digits (99999)
            maxEntriesText = filtered
            filtered.toIntOrNull()?.let { logMaxEntries = it }
          },
          singleLine = true,
          enabled = logPersistenceEnabled,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OlliteRTPrimary,
            cursorColor = OlliteRTPrimary,
          ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Maximum entries kept in memory and in the database. Oldest entries are pruned when the limit is reached.\nSet to 0 for no limit.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(8.dp))

      // Auto-Delete — number input + unit dropdown (minutes/hours/days)
      Column(modifier = Modifier.alpha(childAlpha)) {
        Text(
          text = highlightSearchMatches("Auto-Delete After", searchQuery, OlliteRTPrimary),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Decompose total minutes into a display value + unit for the UI.
        // Pick the largest unit that divides evenly, defaulting to minutes.
        val autoDeleteUnits = listOf("minutes", "hours", "days")
        val (initialValue, initialUnit) = remember(savedLogAutoDeleteMinutes) {
          val mins = savedLogAutoDeleteMinutes
          when {
            mins > 0 && mins % (24 * 60) == 0L -> (mins / (24 * 60)).toString() to "days"
            mins > 0 && mins % 60 == 0L -> (mins / 60).toString() to "hours"
            else -> mins.toString() to "minutes"
          }
        }
        var autoDeleteValueText by remember { mutableStateOf(initialValue) }
        var autoDeleteUnit by remember { mutableStateOf(initialUnit) }
        var showUnitDropdown by remember { mutableStateOf(false) }

        // Recompute total minutes whenever the value or unit changes.
        // A value of 0 means auto-delete is disabled (logs kept indefinitely).
        fun recomputeMinutes() {
          val num = autoDeleteValueText.toLongOrNull() ?: return
          logAutoDeleteMinutes = when (autoDeleteUnit) {
            "hours" -> num * 60
            "days" -> num * 24 * 60
            else -> num
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Number input
          OutlinedTextField(
            value = autoDeleteValueText,
            onValueChange = { text ->
              val filtered = text.filter { it.isDigit() }.take(5)
              autoDeleteValueText = filtered
              recomputeMinutes()
            },
            singleLine = true,
            enabled = logPersistenceEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = OlliteRTPrimary,
              cursorColor = OlliteRTPrimary,
            ),
          )
          // Unit selector dropdown
          Column {
            OutlinedTextField(
              value = autoDeleteUnit,
              onValueChange = {},
              readOnly = true,
              singleLine = true,
              modifier = Modifier
                .widthIn(min = 90.dp, max = 120.dp)
                .clickable(enabled = logPersistenceEnabled) {
                  focusManager.clearFocus() // dismiss keyboard so dropdown anchors correctly
                  showUnitDropdown = true
                },
              enabled = false,
              colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              ),
            )
            DropdownMenu(
              expanded = showUnitDropdown,
              onDismissRequest = { showUnitDropdown = false },
            ) {
              autoDeleteUnits.forEach { unit ->
                DropdownMenuItem(
                  text = {
                    Text(
                      unit,
                      color = if (unit == autoDeleteUnit) OlliteRTPrimary
                              else MaterialTheme.colorScheme.onSurface,
                    )
                  },
                  onClick = {
                    autoDeleteUnit = unit
                    showUnitDropdown = false
                    recomputeMinutes()
                  },
                )
              }
            }
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Persisted logs older than this are automatically removed from the database. Does not affect the current session's in-memory logs.\nSet to 0 to disable.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(8.dp))

      // Clear All Logs button — wipes both in-memory and persisted logs.
      // The user has no visibility into what's only in the DB vs in memory,
      // so clearing should remove everything to avoid confusion.
      Column(modifier = Modifier.alpha(childAlpha)) {
        Button(
          onClick = { showClearPersistedDialog = true },
          enabled = logPersistenceEnabled,
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
          ),
          shape = RoundedCornerShape(50),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Clear All Logs", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Remove all logs from the database and current session.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    } // AnimatedVisibility: Log Persistence

    // Clear persisted logs confirmation dialog
    if (showClearPersistedDialog) {
      AlertDialog(
        onDismissRequest = { showClearPersistedDialog = false },
        title = { Text("Clear All Logs") },
        text = { Text("This will permanently delete all logs — both the current session and the database. This cannot be undone.") },
        confirmButton = {
          Button(
            onClick = {
              showClearPersistedDialog = false
              // Clear in-memory logs (also triggers onEntriesCleared → wipes DB via callback)
              RequestLogStore.clear()
              // Explicit DB wipe as well, in case callback didn't fire (e.g. persistence was just enabled)
              val persistenceEntryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.ollitert.llm.server.OlliteRTApplication.PersistenceEntryPoint::class.java,
              )
              persistenceEntryPoint.requestLogPersistence().clearPersistedLogs()
              Toast.makeText(context, "All logs cleared", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          ) {
            Text("Clear")
          }
        },
        dismissButton = {
          Button(
            onClick = { showClearPersistedDialog = false },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
          ) {
            Text("Cancel")
          }
        },
      )
    }

    // Trim logs confirmation — shown when max entries is reduced below current count
    if (showTrimLogsDialog) {
      val currentCount = RequestLogStore.entries.value.size
      val toRemove = currentCount - logMaxEntries
      AlertDialog(
        onDismissRequest = { showTrimLogsDialog = false },
        title = { Text("Reduce Log Limit") },
        text = {
          Text("You currently have $currentCount logs. Reducing the limit to $logMaxEntries will remove the $toRemove oldest entries after saving.")
        },
        confirmButton = {
          Button(
            onClick = {
              showTrimLogsDialog = false
              saveSettings()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          ) {
            Text("Continue")
          }
        },
        dismissButton = {
          Button(
            onClick = { showTrimLogsDialog = false },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
          ) {
            Text("Cancel")
          }
        },
      )
    }

    // Discard unsaved changes dialog
    if (showDiscardDialog) {
      AlertDialog(
        onDismissRequest = { showDiscardDialog = false },
        title = { Text("Unsaved Changes") },
        text = { Text("You have unsaved changes. Would you like to save or discard them?") },
        confirmButton = {
          Button(onClick = {
            showDiscardDialog = false
            trySave()
            onBackClick()
          }) {
            Text("Save")
          }
        },
        dismissButton = {
          Button(onClick = {
            showDiscardDialog = false
            onBackClick()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
          )) {
            Text("Discard")
          }
        },
      )
    }

    // Restart server dialog when settings that require a restart are changed
    if (showRestartDialog) {
      AlertDialog(
        onDismissRequest = {
          showRestartDialog = false
          Toast.makeText(context, "Settings saved. Restart the server manually for changes to take effect.", Toast.LENGTH_LONG).show()
        },
        title = { Text("Restart server?") },
        text = {
          Text("Some of the changed settings require a server restart to take effect.")
        },
        confirmButton = {
          Button(onClick = {
            showRestartDialog = false
            onRestartServer()
            Toast.makeText(context, "Server restarting with updated settings", Toast.LENGTH_SHORT).show()
          }) {
            Text("Restart")
          }
        },
        dismissButton = {
          Button(
            onClick = {
              showRestartDialog = false
              Toast.makeText(context, "Settings saved. Restart the server manually for changes to take effect.", Toast.LENGTH_LONG).show()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
          ) {
            Text("Later")
          }
        },
      )
    }

    // Reset to Defaults confirmation dialog
    if (showResetDialog) {
      val isServerActive = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING
      AlertDialog(
        onDismissRequest = { showResetDialog = false },
        title = { Text("Reset to Defaults") },
        text = {
          Text(
            "This will reset all settings to their default values, including port, " +
              "bearer token, HuggingFace token, inference configs, system prompts, " +
              "and all toggles." +
              if (isServerActive) "\n\nThe server will be stopped." else "",
          )
        },
        confirmButton = {
          Button(
            onClick = {
              showResetDialog = false

              // Stop the server if it's running
              if (isServerActive) {
                onStopServer()
              }

              // Clear all SharedPreferences
              LlmHttpPrefs.resetToDefaults(context)

              // Clear persisted logs and in-memory log store
              RequestLogStore.clear()
              val persistenceEntryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.ollitert.llm.server.OlliteRTApplication.PersistenceEntryPoint::class.java,
              )
              persistenceEntryPoint.requestLogPersistence().clearPersistedLogs()

              // Restore keep-screen-on to default (true) immediately
              val window = (context as? android.app.Activity)?.window
              window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

              Toast.makeText(context, "All settings reset to defaults", Toast.LENGTH_SHORT).show()

              // Navigate to the Models screen
              onNavigateToModels()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
            ),
          ) {
            Text("Reset")
          }
        },
        dismissButton = {
          Button(
            onClick = { showResetDialog = false },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
          ) {
            Text("Cancel")
          }
        },
      )
    }

    // Donate dialog — shows donation platform options (shared composable)
    if (showDonateDialog) {
      com.ollitert.llm.server.ui.common.DonateDialog(
        onDismiss = { showDonateDialog = false },
      )
    }

    // Home Assistant Integration card — immediate-apply (not part of save/cancel flow)
    var haIntegrationEnabled by remember { mutableStateOf(LlmHttpPrefs.isHaIntegrationEnabled(context)) }


    AnimatedVisibility(
      visible = cardVisible("home_assistant"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      iconRes = com.ollitert.llm.server.R.drawable.ic_home_assistant,
      title = "Home Assistant",
      searchQuery = searchQuery,
    ) {
      // Toggle for HA integration
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "REST API Integration", searchQuery = searchQuery)
          Text(
            text = "Generate a ready-made configuration.yaml snippet for Home Assistant. Creates REST sensors for server status, model info, and performance metrics, plus a command to stop the server remotely.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = haIntegrationEnabled,
          onCheckedChange = {
            haIntegrationEnabled = it
            LlmHttpPrefs.setHaIntegrationEnabled(context, it)
          },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }

      // "Copy Configuration" button — only visible when the toggle is on
      if (haIntegrationEnabled) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))

        // Build the HA YAML config dynamically using current IP, port, and bearer token
        val currentPort = portText.toIntOrNull() ?: LlmHttpPrefs.getPort(context)
        val currentIp = remember { getWifiIpAddress(context) ?: "<YOUR_DEVICE_IP>" }
        val currentToken = if (bearerEnabled) bearerToken else ""
        val baseUrl = "http://$currentIp:$currentPort"

        // Auth header block reused across REST sensor and commands
        val authYaml = if (currentToken.isNotBlank()) "    headers:\n      Authorization: \"Bearer $currentToken\"\n" else ""

        // IMPORTANT: Keep this YAML template in sync with healthPayload() in LlmHttpService.kt.
        // When new fields are added to /health?metrics=true or new /v1/server/* endpoints
        // are created, update the sensors and rest_commands here to match.
        val haConfig = buildString {
          appendLine("# OlliteRT — Home Assistant REST Integration")
          appendLine("# Add this to your configuration.yaml")
          appendLine("# Docs: GET /health?metrics=true for sensors, POST /v1/server/* for commands")
          appendLine()

          // REST sensors — single poll for status + metrics
          appendLine("rest:")
          appendLine("  - resource: \"$baseUrl/health?metrics=true\"")
          appendLine("    scan_interval: 30")
          if (currentToken.isNotBlank()) {
            append(authYaml)
          }
          appendLine("    sensor:")
          appendLine("      - name: \"OlliteRT Status\"")
          appendLine("        value_template: \"{{ value_json.status }}\"")
          appendLine("      - name: \"OlliteRT Model\"")
          appendLine("        value_template: \"{{ value_json.model | default('none') }}\"")
          appendLine("      - name: \"OlliteRT Uptime\"")
          appendLine("        value_template: \"{{ value_json.uptime_seconds | default(0) }}\"")
          appendLine("        unit_of_measurement: \"s\"")
          appendLine("      - name: \"OlliteRT Thinking\"")
          appendLine("        value_template: \"{{ value_json.thinking_enabled | default(false) }}\"")
          appendLine("      - name: \"OlliteRT Accelerator\"")
          appendLine("        value_template: \"{{ value_json.accelerator | default('unknown') }}\"")
          appendLine("      - name: \"OlliteRT Idle\"")
          appendLine("        value_template: \"{{ value_json.is_idle_unloaded | default(false) }}\"")
          appendLine("      - name: \"OlliteRT Requests\"")
          appendLine("        value_template: \"{{ value_json.metrics.requests_total | default(0) }}\"")
          appendLine("      - name: \"OlliteRT Errors\"")
          appendLine("        value_template: \"{{ value_json.metrics.errors_total | default(0) }}\"")
          appendLine("      - name: \"OlliteRT TTFB\"")
          appendLine("        value_template: \"{{ value_json.metrics.ttfb_avg_ms | default(0) }}\"")
          appendLine("        unit_of_measurement: \"ms\"")
          appendLine("      - name: \"OlliteRT Decode Speed\"")
          appendLine("        value_template: \"{{ value_json.metrics.decode_tokens_per_second | default(0) | round(1) }}\"")
          appendLine("        unit_of_measurement: \"t/s\"")
          appendLine("      - name: \"OlliteRT Context Usage\"")
          appendLine("        value_template: \"{{ value_json.metrics.context_utilization_percent | default(0) | round(1) }}\"")
          appendLine("        unit_of_measurement: \"%\"")
          appendLine()

          // REST commands for server control
          appendLine("rest_command:")

          // Stop server
          appendLine("  ollitert_stop:")
          appendLine("    url: \"$baseUrl/v1/server/stop\"")
          appendLine("    method: POST")
          if (currentToken.isNotBlank()) append(authYaml)
          appendLine("    content_type: \"application/json\"")

          // Reload model
          appendLine("  ollitert_reload:")
          appendLine("    url: \"$baseUrl/v1/server/reload\"")
          appendLine("    method: POST")
          if (currentToken.isNotBlank()) append(authYaml)
          appendLine("    content_type: \"application/json\"")

          // Toggle thinking (sends { "enabled": true/false } via HA payload template)
          appendLine("  ollitert_thinking:")
          appendLine("    url: \"$baseUrl/v1/server/thinking\"")
          appendLine("    method: POST")
          if (currentToken.isNotBlank()) append(authYaml)
          appendLine("    content_type: \"application/json\"")
          appendLine("    payload: '{\"enabled\": {{ enabled }}}'")

          // Update inference config (send any subset of: temperature, max_tokens, top_k, top_p)
          appendLine("  ollitert_config:")
          appendLine("    url: \"$baseUrl/v1/server/config\"")
          appendLine("    method: POST")
          if (currentToken.isNotBlank()) append(authYaml)
          appendLine("    content_type: \"application/json\"")
          appendLine("    payload: '{{ payload }}'")

          // TODO: Add ollitert_switch_model command when multi-model support is implemented.
          // Would call POST /v1/server/model with { "model": "model-name" } to switch the
          // active model via HA automation. Blocked until the server exposes all downloaded
          // models and supports on-demand model switching.
        }

        // Preview of what will be copied (truncated)
        Text(
          text = "Generates a configuration.yaml snippet with your current IP ($currentIp), port ($currentPort)${if (currentToken.isNotBlank()) ", and bearer token" else ""}.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Full-width copy button
        Button(
          onClick = {
            copyToClipboard(context, "OlliteRT HA Config", haConfig)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(50),
          colors = ButtonDefaults.buttonColors(containerColor = OlliteRTPrimary),
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Copy Configuration",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
        }
      }

    }
    } // AnimatedVisibility: Home Assistant

    // Advanced Settings card
    AnimatedVisibility(
      visible = cardVisible("advanced"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Science,
      title = "Advanced",
      searchQuery = searchQuery,
    ) {
      // Track which advanced settings are visible to control dividers between them.
      // Dividers only show between two consecutively visible settings.
      val advancedKeys = listOf("warmup_message", "pre_init_vision", "custom_prompts", "truncate_history", "compact_tool_schemas", "trim_prompt", "ignore_client_params")
      val advancedVisible = advancedKeys.map { settingVisible(it) }

      /** Show a divider before [index] only if a preceding setting is also visible. */
      fun showDividerBefore(index: Int): Boolean {
        if (!advancedVisible[index]) return false
        return (0 until index).any { advancedVisible[it] }
      }

      if (settingVisible("warmup_message")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Warmup Message", searchQuery = searchQuery)
          Text(
            text = "Send a test message when the model loads to verify the engine is working. Disabling this speeds up model startup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = warmupEnabled,
          onCheckedChange = { warmupEnabled = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: warmup_message

      if (showDividerBefore(1)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Eager vision initialization toggle
      if (settingVisible("pre_init_vision")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Pre-initialize Vision", searchQuery = searchQuery)
          Text(
            text = "Load the vision backend when a multimodal model starts, even before any image request arrives. Eliminates delay on the first image request but increases memory and GPU usage from the start.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = eagerVisionInit,
          onCheckedChange = { eagerVisionInit = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: pre_init_vision

      if (showDividerBefore(2)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Custom system prompt & chat template toggle
      if (settingVisible("custom_prompts")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Custom System Prompt & Chat Template", searchQuery = searchQuery)
          Text(
            text = "Enable per-model system prompt and chat template fields in Inference Settings. Useful for models with non-standard prompt formats.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = customPromptsEnabled,
          onCheckedChange = { customPromptsEnabled = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: custom_prompts

      if (showDividerBefore(3)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Truncate conversation history toggle
      if (settingVisible("truncate_history")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Truncate Conversation History", searchQuery = searchQuery)
          Text(
            text = "When a request exceeds the model's context window, drop older messages from the conversation while keeping system prompts and the most recent messages.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = autoTruncateHistory,
          onCheckedChange = { autoTruncateHistory = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: truncate_history

      if (showDividerBefore(4)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Compact tool schemas toggle (especially useful for Home Assistant)
      if (settingVisible("compact_tool_schemas")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Compact Tool Schemas", searchQuery = searchQuery)
          Text(
            text = "When a request with tools exceeds the model's context window, automatically reduce tool schemas to names and descriptions only (omitting parameter details). Especially useful for Home Assistant integration which sends many tool definitions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = compactToolSchemas,
          onCheckedChange = { compactToolSchemas = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: compact_tool_schemas

      if (showDividerBefore(5)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Trim prompt toggle (last resort)
      if (settingVisible("trim_prompt")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Trim Prompt", searchQuery = searchQuery)
          Text(
            text = "Last resort when other strategies aren't enough. Hard-cuts the prompt to fit the context window, keeping the most recent content and discarding the beginning.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = autoTrimPrompts,
          onCheckedChange = { autoTrimPrompts = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: trim_prompt

      if (showDividerBefore(6)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Ignore client sampler parameters toggle
      if (settingVisible("ignore_client_params")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Ignore Client Sampler Parameters", searchQuery = searchQuery)
          Text(
            text = "Discard temperature, top_p, top_k, and max_tokens values sent by API clients. The server's own Inference Settings will always be used instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = ignoreClientSamplerParams,
          onCheckedChange = { ignoreClientSamplerParams = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: ignore_client_params
    }
    } // AnimatedVisibility: Advanced

    // Developer card — verbose debug toggle (immediate-apply, no save/cancel)
    AnimatedVisibility(
      visible = cardVisible("developer"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Code,
      title = "Developer",
      searchQuery = searchQuery,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Verbose Debug Mode", searchQuery = searchQuery)
          Text(
            text = "Logs additional details: full stack traces, memory snapshots, model config, per-request timing. May impact performance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = verboseDebugEnabled,
          onCheckedChange = {
            verboseDebugEnabled = it
            LlmHttpPrefs.setVerboseDebugEnabled(context, it)
            RequestLogStore.addEvent(
              "Settings updated (1 change)",
              category = EventCategory.SETTINGS,
              body = "Verbose Debug Mode: ${if (!it) "enabled" else "disabled"} → ${if (it) "enabled" else "disabled"}",
            )
            val msg = if (it) "Debug mode enabled — additional details will appear in Logs"
              else "Debug mode disabled"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
          },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
    }
    } // AnimatedVisibility: Developer

    // Reset to Defaults
    AnimatedVisibility(
      visible = cardVisible("reset"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    Column {
    Spacer(modifier = Modifier.height(16.dp))
    Button(
      onClick = { showResetDialog = true },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(50),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
      ),
    ) {
      Icon(
        imageVector = Icons.Outlined.RestartAlt,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "Reset to Defaults",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
    }

    } // Column
    } // AnimatedVisibility: Reset

    // Footer links — What's New, Report Issue, and Donate; wraps on narrow/large-font screens
    Spacer(modifier = Modifier.height(12.dp))
    FlowRow(
      modifier = Modifier.align(Alignment.CenterHorizontally),
      horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      // What's New — opens changelog
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable {
            val intent = UpdateCheckWorker.buildUpdateIntent(context, UpdateCheckWorker.GITHUB_RELEASES_URL)
            intent.data?.let { uri -> uriHandler.openUri(uri.toString()) }
          }
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.NewReleases,
          contentDescription = null,
          tint = OlliteRTPrimary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = "What's New",
          style = MaterialTheme.typography.bodyMedium,
          color = OlliteRTPrimary,
        )
      }

      // Report Issue — opens GitHub bug report template with prefilled device info
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable {
            val activeModel = ServerMetrics.activeModelName.value ?: "None"
            val deviceInfo = listOf(
              "- App version: OlliteRT v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_HASH}) [${BuildConfig.CHANNEL}]",
              "- Device: ${Build.MANUFACTURER} ${Build.MODEL}",
              "- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
              "- LLM Model: $activeModel",
            ).joinToString("\n")
            val encoded = URLEncoder.encode(deviceInfo, "UTF-8")
            val url = "${GitHubConfig.NEW_BUG_REPORT_URL}&device-info=$encoded"
            uriHandler.openUri(url)
          }
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.BugReport,
          contentDescription = null,
          tint = OlliteRTPrimary,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = "Report Issue",
          style = MaterialTheme.typography.bodyMedium,
          color = OlliteRTPrimary,
        )
      }

      // Donate — opens dialog with donation platform options
      val heartPulse = rememberInfiniteTransition(label = "heartPulse")
      val heartScale by heartPulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
          animation = tween(durationMillis = 600),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "heartScale",
      )
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable { showDonateDialog = true }
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Favorite,
          contentDescription = null,
          tint = OlliteRTPrimary,
          modifier = Modifier
            .size(18.dp)
            .graphicsLayer {
              scaleX = heartScale
              scaleY = heartScale
            },
        )
        Text(
          text = "Donate",
          style = MaterialTheme.typography.bodyMedium,
          color = OlliteRTPrimary,
        )
      }
    }

    // Footer
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = "OlliteRT v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_HASH})",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
  }

    // Unsaved changes banner
    androidx.compose.animation.AnimatedVisibility(
      visible = hasUnsavedChanges,
      enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) +
        androidx.compose.animation.fadeIn(),
      exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) +
        androidx.compose.animation.fadeOut(),
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 16.dp),
    ) {
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
          .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(12.dp),
          )
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "You have unsaved changes",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

@Composable
private fun SettingsCard(
  icon: ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
  content: @Composable () -> Unit,
) {
  SettingsCardLayout(
    iconContent = {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = OlliteRTPrimary,
        modifier = Modifier.size(20.dp),
      )
    },
    title = title,
    modifier = modifier,
    searchQuery = searchQuery,
    content = content,
  )
}

/** Overload for cards that use a drawable resource icon (e.g. brand icons like Home Assistant). */
@Composable
private fun SettingsCard(
  iconRes: Int,
  title: String,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
  content: @Composable () -> Unit,
) {
  SettingsCardLayout(
    iconContent = {
      Icon(
        painter = androidx.compose.ui.res.painterResource(id = iconRes),
        contentDescription = null,
        tint = OlliteRTPrimary,
        modifier = Modifier.size(20.dp),
      )
    },
    title = title,
    modifier = modifier,
    searchQuery = searchQuery,
    content = content,
  )
}

@Composable
private fun SettingsCardLayout(
  iconContent: @Composable () -> Unit,
  title: String,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(20.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      iconContent()
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = highlightSearchMatches(title, searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Spacer(modifier = Modifier.height(12.dp))
    content()
  }
}

