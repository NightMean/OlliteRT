package com.ollitert.llm.server.ui.server

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.db.RequestLogPersistence
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LlmHttpService
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.worker.UpdateCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * ViewModel for the Settings screen. Owns all settings state, validation,
 * change detection, search filtering, and save/reset logic.
 *
 * Separated from SettingsScreen.kt to:
 * - Survive configuration changes (rotation, theme switch)
 * - Make validation and save logic unit-testable
 * - Centralize the 28-field saved/editable state pairs
 *
 * State is exposed as individual [mutableStateOf] properties (not a single StateFlow<UiState>)
 * because each setting is independently editable and the UI observes them individually.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val persistence: RequestLogPersistence,
) : ViewModel() {

  // ─── Saved State ─────────────────────────────────────────────────────────
  // Persisted values — used for change detection; updated after successful save.
  private var savedPort by mutableStateOf(LlmHttpPrefs.getPort(context))
  private var savedBearerToken by mutableStateOf(LlmHttpPrefs.getBearerToken(context))
  private var savedHfToken by mutableStateOf(LlmHttpPrefs.getHfToken(context))
  private var savedDefaultModelName by mutableStateOf(LlmHttpPrefs.getDefaultModelName(context))
  private var savedAutoStartOnBoot by mutableStateOf(LlmHttpPrefs.isAutoStartOnBoot(context))
  private var savedKeepScreenOn by mutableStateOf(LlmHttpPrefs.isKeepScreenOn(context))
  private var savedAutoExpandLogs by mutableStateOf(LlmHttpPrefs.isAutoExpandLogs(context))
  private var savedWarmupEnabled by mutableStateOf(LlmHttpPrefs.isWarmupEnabled(context))
  private var savedStreamLogsPreview by mutableStateOf(LlmHttpPrefs.isStreamLogsPreview(context))
  private var savedKeepPartialResponse by mutableStateOf(LlmHttpPrefs.isKeepPartialResponse(context))
  private var savedEagerVisionInit by mutableStateOf(LlmHttpPrefs.isEagerVisionInit(context))
  private var savedCustomPromptsEnabled by mutableStateOf(LlmHttpPrefs.isCustomPromptsEnabled(context))
  private var savedAutoTruncateHistory by mutableStateOf(LlmHttpPrefs.isAutoTruncateHistory(context))
  private var savedAutoTrimPrompts by mutableStateOf(LlmHttpPrefs.isAutoTrimPrompts(context))
  private var savedCompactToolSchemas by mutableStateOf(LlmHttpPrefs.isCompactToolSchemas(context))
  private var savedCompactImageData by mutableStateOf(LlmHttpPrefs.isCompactImageData(context))
  private var savedHideHealthLogs by mutableStateOf(LlmHttpPrefs.isHideHealthLogs(context))
  private var savedClearLogsOnStop by mutableStateOf(LlmHttpPrefs.isClearLogsOnStop(context))
  private var savedConfirmClearLogs by mutableStateOf(LlmHttpPrefs.isConfirmClearLogs(context))
  private var savedShowRequestTypes by mutableStateOf(LlmHttpPrefs.isShowRequestTypes(context))
  private var savedShowAdvancedMetrics by mutableStateOf(LlmHttpPrefs.isShowAdvancedMetrics(context))
  private var savedCorsAllowedOrigins by mutableStateOf(LlmHttpPrefs.getCorsAllowedOrigins(context))
  private var savedLogPersistenceEnabled by mutableStateOf(LlmHttpPrefs.isLogPersistenceEnabled(context))
  private var savedLogMaxEntries by mutableStateOf(LlmHttpPrefs.getLogMaxEntries(context))
  internal var savedLogAutoDeleteMinutes by mutableStateOf(LlmHttpPrefs.getLogAutoDeleteMinutes(context))
  private var savedIgnoreClientSamplerParams by mutableStateOf(LlmHttpPrefs.isIgnoreClientSamplerParams(context))
  private var savedKeepAliveEnabled by mutableStateOf(LlmHttpPrefs.isKeepAliveEnabled(context))
  internal var savedKeepAliveMinutes by mutableStateOf(LlmHttpPrefs.getKeepAliveMinutes(context))
  private var savedUpdateCheckEnabled by mutableStateOf(LlmHttpPrefs.isUpdateCheckEnabled(context))
  internal var savedUpdateCheckIntervalHours by mutableStateOf(LlmHttpPrefs.getUpdateCheckIntervalHours(context))

  // ─── Editable State ──────────────────────────────────────────────────────
  // Current (editable) state — observed by the UI, compared against saved for change detection.
  var portText by mutableStateOf(savedPort.toString())
  var portError by mutableStateOf(false)
  var bearerEnabled by mutableStateOf(savedBearerToken.isNotBlank())
  var bearerToken by mutableStateOf(savedBearerToken)
  var hfToken by mutableStateOf(savedHfToken)
  var hfTokenVisible by mutableStateOf(false)
  var defaultModelName by mutableStateOf(savedDefaultModelName)
  var showModelDropdown by mutableStateOf(false)
  var autoStartOnBoot by mutableStateOf(savedAutoStartOnBoot)
  var keepScreenOn by mutableStateOf(savedKeepScreenOn)
  var autoExpandLogs by mutableStateOf(savedAutoExpandLogs)
  var warmupEnabled by mutableStateOf(savedWarmupEnabled)
  var streamLogsPreview by mutableStateOf(savedStreamLogsPreview)
  var keepPartialResponse by mutableStateOf(savedKeepPartialResponse)
  var eagerVisionInit by mutableStateOf(savedEagerVisionInit)
  var customPromptsEnabled by mutableStateOf(savedCustomPromptsEnabled)
  var autoTruncateHistory by mutableStateOf(savedAutoTruncateHistory)
  var autoTrimPrompts by mutableStateOf(savedAutoTrimPrompts)
  var compactToolSchemas by mutableStateOf(savedCompactToolSchemas)
  var compactImageData by mutableStateOf(savedCompactImageData)
  var hideHealthLogs by mutableStateOf(savedHideHealthLogs)
  var clearLogsOnStop by mutableStateOf(savedClearLogsOnStop)
  var confirmClearLogs by mutableStateOf(savedConfirmClearLogs)
  var showRequestTypes by mutableStateOf(savedShowRequestTypes)
  var showAdvancedMetrics by mutableStateOf(savedShowAdvancedMetrics)
  var corsAllowedOrigins by mutableStateOf(savedCorsAllowedOrigins)
  var corsError by mutableStateOf(false)
  var logPersistenceEnabled by mutableStateOf(savedLogPersistenceEnabled)
  var logMaxEntries by mutableStateOf(savedLogMaxEntries)
  var logAutoDeleteMinutes by mutableStateOf(savedLogAutoDeleteMinutes)
  var ignoreClientSamplerParams by mutableStateOf(savedIgnoreClientSamplerParams)
  var keepAliveEnabled by mutableStateOf(savedKeepAliveEnabled)
  var keepAliveMinutes by mutableStateOf(savedKeepAliveMinutes)
  var keepAliveError by mutableStateOf(false)
  var updateCheckError by mutableStateOf(false)
  var keepAliveUnit by mutableStateOf(
    if (savedKeepAliveMinutes > 0 && savedKeepAliveMinutes % 60 == 0) "hours" else "minutes"
  )
  var updateCheckEnabled by mutableStateOf(savedUpdateCheckEnabled)
  var updateCheckIntervalHours by mutableStateOf(savedUpdateCheckIntervalHours)
  var updateCheckUnit by mutableStateOf(
    if (savedUpdateCheckIntervalHours > 0 && savedUpdateCheckIntervalHours % 24 == 0) "days" else "hours"
  )
  var verboseDebugEnabled by mutableStateOf(LlmHttpPrefs.isVerboseDebugEnabled(context))

  // ─── Dialog State ────────────────────────────────────────────────────────
  var showRestartDialog by mutableStateOf(false)
  var showClearPersistedDialog by mutableStateOf(false)
  var showTrimLogsDialog by mutableStateOf(false)
  var showResetDialog by mutableStateOf(false)
  var showDiscardDialog by mutableStateOf(false)
  var showDonateDialog by mutableStateOf(false)

  // ─── Search ──────────────────────────────────────────────────────────────
  var searchQuery by mutableStateOf("")

  /** Per-setting keyword index — setting key → searchable text (name + description). */
  private val settingSearchIndex = mapOf(
    "keep_screen_awake" to "Keep Screen Awake Prevent screen from turning off while app is open",
    "auto_expand_logs" to "Auto-Expand Logs Show full request and response bodies in the Logs tab",
    "stream_response_preview" to "Stream Response Preview Show model output as it generates in the Logs tab for streaming requests",
    "compact_image_data" to "Compact Image Data Replace base64 image data with size placeholder logs multimodal vision performance lag",
    "hide_health_logs" to "Hide Health Logs Suppress health check endpoint entries from Logs tab noise monitoring polling",
    "clear_logs_on_stop" to "Clear Logs on Stop Automatically clear in-memory logs when the server stops",
    "confirm_clear_logs" to "Confirm Before Clearing Logs Show a confirmation dialog before clearing logs",
    "keep_partial_response" to "Keep Partial Response Preserve incomplete response text in logs when a streaming request is cancelled by the client",
    "hf_token" to "Hugging Face Token HuggingFace hf download models authentication required",
    "host_port" to "Host Port 1024 65535 server configuration default 8000 restart",
    "bearer_token" to "Require Bearer Token Protect API authentication Authorization header security",
    "cors_origins" to "CORS Allowed Origins cross-origin requests localhost",
    "default_model" to "Default Model Automatically load model when app launches",
    "start_on_boot" to "Start on Boot Launch server automatically when device starts",
    "keep_alive" to "Keep Alive Unload model after idle timeout free RAM cold start Idle Timeout",
    "dontkillmyapp" to "Device background settings manufacturers kill background apps dontkillmyapp",
    "update_check" to "Check for Updates version new notification background frequency interval update available",
    "show_request_types" to "Show Request Types text vision audio request counts Status screen",
    "show_advanced_metrics" to "Show Advanced Metrics prefill speed inter-token latency latency stats context utilization Status screen",
    "log_persistence" to "Log Persistence Persist Logs Database Maximum Log Entries Auto-Delete Clear All Logs storage oldest entries pruned survive app restarts",
    "ha_integration" to "Home Assistant REST API Integration configuration yaml sensors commands stop reload thinking config",
    "warmup_message" to "Warmup Message Send test message when model loads verify engine working startup",
    "pre_init_vision" to "Pre-initialize Vision Load vision backend multimodal model starts image request memory GPU",
    "custom_prompts" to "Custom System Prompt Chat Template per-model prompt formats Inference Settings",
    "truncate_history" to "Truncate Conversation History request exceeds context window drop older messages system prompts",
    "compact_tool_schemas" to "Compact Tool Schemas reduce tool schemas names descriptions context window Home Assistant tool definitions",
    "trim_prompt" to "Trim Prompt last resort hard-cuts prompt fit context window recent content discarding beginning",
    "ignore_client_params" to "Ignore Client Sampler Parameters Discard temperature top_p top_k max_tokens API clients server Inference Settings",
    "verbose_debug" to "Verbose Debug Mode Logs additional details stack traces memory snapshots model config per-request timing performance",
    "reset" to "Reset to Defaults reset all settings port token inference",
  )

  /** Which settings belong to which card — used to derive card visibility. */
  private val settingsByCard = mapOf(
    "general" to listOf("keep_screen_awake", "auto_expand_logs", "stream_response_preview", "compact_image_data", "hide_health_logs", "clear_logs_on_stop", "confirm_clear_logs", "keep_partial_response"),
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

  // ─── Change Detection ────────────────────────────────────────────────────

  private val effectiveBearerToken: String get() = if (bearerEnabled) bearerToken else ""

  val hasUnsavedChanges: Boolean get() =
    portText != savedPort.toString() ||
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
    compactImageData != savedCompactImageData ||
    hideHealthLogs != savedHideHealthLogs ||
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

  // ─── Save Logic ──────────────────────────────────────────────────────────

  sealed class SaveResult {
    data object Success : SaveResult()
    data class NeedsRestart(val keepScreenOn: Boolean) : SaveResult()
    data class ValidationError(val message: String) : SaveResult()
    data class NeedsTrimConfirmation(val currentCount: Int, val newMax: Int) : SaveResult()
  }

  /** Wrapper that warns if saving would trim existing logs. */
  fun trySave(serverStatus: ServerStatus): SaveResult {
    val currentCount = RequestLogStore.entries.value.size
    if (logMaxEntries < currentCount && logMaxEntries != savedLogMaxEntries) {
      return SaveResult.NeedsTrimConfirmation(currentCount, logMaxEntries)
    }
    return save(serverStatus)
  }

  /** Validates and persists all settings. Returns a result for the UI to act on. */
  fun save(serverStatus: ServerStatus): SaveResult {
    // ── Validation ──
    if (portText.isBlank()) {
      portError = true
      return SaveResult.ValidationError("A port number is required")
    }
    if (portText.toIntOrNull().let { it == null || it !in 1024..65535 }) {
      portError = true
      return SaveResult.ValidationError("Port must be between 1024 and 65535")
    }
    if (!isValidCorsOrigins(corsAllowedOrigins)) {
      corsError = true
      return SaveResult.ValidationError("Invalid CORS origins — use *, blank, or comma-separated URLs with http(s)://")
    }
    if (keepAliveEnabled && keepAliveMinutes !in 1..7200) {
      keepAliveError = true
      val rangeText = if (keepAliveUnit == "hours") "1 and 120 hours" else "1 and 7200 minutes"
      return SaveResult.ValidationError("Keep-alive timeout must be between $rangeText")
    }
    if (updateCheckEnabled && updateCheckIntervalHours !in 1..720) {
      updateCheckError = true
      val rangeText = if (updateCheckUnit == "days") "1 and 30 days" else "1 and 720 hours"
      return SaveResult.ValidationError("Update check interval must be between $rangeText")
    }

    // ── Clear validation errors ──
    corsError = false
    keepAliveError = false
    updateCheckError = false

    val port = portText.toInt()
    val isPortChanged = port != savedPort
    val isEagerVisionChanged = eagerVisionInit != savedEagerVisionInit
    val needsRestart = isPortChanged || isEagerVisionChanged
    val isServerActive = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING

    // ── Persist to SharedPreferences ──
    LlmHttpPrefs.save(context, LlmHttpPrefs.isEnabled(context), port)
    LlmHttpPrefs.setBearerToken(context, if (bearerEnabled) bearerToken else "")
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
    if ((keepAliveEnabled != savedKeepAliveEnabled || keepAliveMinutes != savedKeepAliveMinutes) && isServerActive) {
      LlmHttpService.resetKeepAliveTimer(context)
    }
    LlmHttpPrefs.setUpdateCheckEnabled(context, updateCheckEnabled)
    LlmHttpPrefs.setUpdateCheckIntervalHours(context, updateCheckIntervalHours)
    if (updateCheckEnabled != savedUpdateCheckEnabled || updateCheckIntervalHours != savedUpdateCheckIntervalHours) {
      if (updateCheckEnabled) UpdateCheckWorker.scheduleUpdateCheck(context)
      else UpdateCheckWorker.cancelUpdateCheck(context)
    }
    LlmHttpPrefs.setCompactImageData(context, compactImageData)
    LlmHttpPrefs.setHideHealthLogs(context, hideHealthLogs)
    LlmHttpPrefs.setClearLogsOnStop(context, clearLogsOnStop)
    LlmHttpPrefs.setConfirmClearLogs(context, confirmClearLogs)
    LlmHttpPrefs.setShowRequestTypes(context, showRequestTypes)
    LlmHttpPrefs.setShowAdvancedMetrics(context, showAdvancedMetrics)
    LlmHttpPrefs.setCorsAllowedOrigins(context, corsAllowedOrigins)
    LlmHttpPrefs.setLogPersistenceEnabled(context, logPersistenceEnabled)
    LlmHttpPrefs.setLogMaxEntries(context, logMaxEntries)
    LlmHttpPrefs.setLogAutoDeleteMinutes(context, logAutoDeleteMinutes)

    // ── Log changes ──
    logSettingsChanges(port)

    // ── Sync persistence layer ──
    if (logPersistenceEnabled && !savedLogPersistenceEnabled) {
      persistence.persistCurrentEntries()
    }
    persistence.updateMaxEntries()

    // ── Update saved state (reset change detection) ──
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
    savedCompactImageData = compactImageData
    savedHideHealthLogs = hideHealthLogs
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

    // Re-check live server status before triggering restart — the server may have crashed
    // or stopped between when the user opened Settings and when they pressed Save.
    val liveStatus = com.ollitert.llm.server.service.ServerMetrics.status.value
    val isStillActive = liveStatus == ServerStatus.RUNNING || liveStatus == ServerStatus.LOADING
    return if (needsRestart && isServerActive && isStillActive) {
      SaveResult.NeedsRestart(keepScreenOn = keepScreenOn)
    } else {
      SaveResult.Success
    }
  }

  /** Collects all behavioral settings changes into one grouped log entry. */
  private fun logSettingsChanges(newPort: Int) {
    val changes = mutableListOf<String>()
    if (newPort != savedPort) changes.add("Port: $savedPort → $newPort")
    val bearerWasEnabled = savedBearerToken.isNotBlank()
    val bearerIsEnabled = effectiveBearerToken.isNotBlank()
    if (bearerWasEnabled != bearerIsEnabled)
      changes.add("Bearer Auth: ${if (bearerWasEnabled) "enabled" else "disabled"} → ${if (bearerIsEnabled) "enabled" else "disabled"}")
    if (autoStartOnBoot != savedAutoStartOnBoot)
      changes.add("Auto-Start on Boot: ${fmtToggle(savedAutoStartOnBoot)} → ${fmtToggle(autoStartOnBoot)}")
    if (warmupEnabled != savedWarmupEnabled)
      changes.add("Warmup Message: ${fmtToggle(savedWarmupEnabled)} → ${fmtToggle(warmupEnabled)}")
    if (eagerVisionInit != savedEagerVisionInit)
      changes.add("Pre-initialize Vision: ${fmtToggle(savedEagerVisionInit)} → ${fmtToggle(eagerVisionInit)}")
    if (customPromptsEnabled != savedCustomPromptsEnabled)
      changes.add("Custom System Prompt & Chat Template: ${fmtToggle(savedCustomPromptsEnabled)} → ${fmtToggle(customPromptsEnabled)}")
    if (ignoreClientSamplerParams != savedIgnoreClientSamplerParams)
      changes.add("Ignore Client Sampler Parameters: ${fmtToggle(savedIgnoreClientSamplerParams)} → ${fmtToggle(ignoreClientSamplerParams)}")
    if (autoTruncateHistory != savedAutoTruncateHistory)
      changes.add("Truncate Conversation History: ${fmtToggle(savedAutoTruncateHistory)} → ${fmtToggle(autoTruncateHistory)}")
    if (compactToolSchemas != savedCompactToolSchemas)
      changes.add("Compact Tool Schemas: ${fmtToggle(savedCompactToolSchemas)} → ${fmtToggle(compactToolSchemas)}")
    if (autoTrimPrompts != savedAutoTrimPrompts)
      changes.add("Trim Prompt: ${fmtToggle(savedAutoTrimPrompts)} → ${fmtToggle(autoTrimPrompts)}")
    if (corsAllowedOrigins != savedCorsAllowedOrigins)
      changes.add("CORS Allowed Origins: ${savedCorsAllowedOrigins.ifBlank { "disabled" }} → ${corsAllowedOrigins.ifBlank { "disabled" }}")
    if (compactImageData != savedCompactImageData)
      changes.add("Compact Image Data: ${fmtToggle(savedCompactImageData)} → ${fmtToggle(compactImageData)}")
    if (hideHealthLogs != savedHideHealthLogs)
      changes.add("Hide Health Logs: ${fmtToggle(savedHideHealthLogs)} → ${fmtToggle(hideHealthLogs)}")
    if (logPersistenceEnabled != savedLogPersistenceEnabled)
      changes.add("Log Persistence: ${fmtToggle(savedLogPersistenceEnabled)} → ${fmtToggle(logPersistenceEnabled)}")
    if (logMaxEntries != savedLogMaxEntries)
      changes.add("Log Max Entries: $savedLogMaxEntries → $logMaxEntries")
    if (logAutoDeleteMinutes != savedLogAutoDeleteMinutes)
      changes.add("Log Auto-Delete: ${formatMinutesHumanReadable(savedLogAutoDeleteMinutes)} → ${formatMinutesHumanReadable(logAutoDeleteMinutes)}")
    if (keepAliveEnabled != savedKeepAliveEnabled)
      changes.add("Keep Alive: ${fmtToggle(savedKeepAliveEnabled)} → ${fmtToggle(keepAliveEnabled)}")
    if (keepAliveMinutes != savedKeepAliveMinutes)
      changes.add("Keep Alive Timeout: ${savedKeepAliveMinutes}m → ${keepAliveMinutes}m")
    if (updateCheckEnabled != savedUpdateCheckEnabled)
      changes.add("Check for Updates: ${fmtToggle(savedUpdateCheckEnabled)} → ${fmtToggle(updateCheckEnabled)}")
    if (updateCheckIntervalHours != savedUpdateCheckIntervalHours) {
      fun fmtInterval(hours: Int): String = when {
        hours % 24 == 0 -> "${hours / 24} ${if (hours / 24 == 1) "day" else "days"}"
        else -> "$hours ${if (hours == 1) "hour" else "hours"}"
      }
      changes.add("Update Check Frequency: ${fmtInterval(savedUpdateCheckIntervalHours)} → ${fmtInterval(updateCheckIntervalHours)}")
    }
    if (changes.isNotEmpty()) {
      RequestLogStore.addEvent(
        "Settings updated (${changes.size} ${if (changes.size == 1) "change" else "changes"})",
        category = EventCategory.SETTINGS,
        body = changes.joinToString("\n"),
      )
    }
  }

  // ─── Reset ───────────────────────────────────────────────────────────────

  /** Clear all persisted logs (in-memory + database). */
  fun clearPersistedLogs() {
    RequestLogStore.clear()
    persistence.clearPersistedLogs()
  }

  /** Reset all settings to factory defaults. */
  fun resetToDefaults() {
    LlmHttpPrefs.resetToDefaults(context)

    // Reload all state from freshly-reset prefs
    savedPort = LlmHttpPrefs.getPort(context)
    portText = savedPort.toString()
    portError = false
    savedBearerToken = ""
    bearerEnabled = false
    bearerToken = ""
    savedHfToken = LlmHttpPrefs.getHfToken(context)
    hfToken = savedHfToken
    savedDefaultModelName = ""
    defaultModelName = ""
    savedAutoStartOnBoot = false; autoStartOnBoot = false
    savedKeepScreenOn = false; keepScreenOn = false
    savedAutoExpandLogs = false; autoExpandLogs = false
    savedWarmupEnabled = true; warmupEnabled = true
    savedStreamLogsPreview = true; streamLogsPreview = true
    savedKeepPartialResponse = false; keepPartialResponse = false
    savedEagerVisionInit = false; eagerVisionInit = false
    savedCustomPromptsEnabled = false; customPromptsEnabled = false
    savedAutoTruncateHistory = true; autoTruncateHistory = true
    savedAutoTrimPrompts = false; autoTrimPrompts = false
    savedCompactToolSchemas = true; compactToolSchemas = true
    savedCompactImageData = true; compactImageData = true
    savedHideHealthLogs = false; hideHealthLogs = false
    savedClearLogsOnStop = false; clearLogsOnStop = false
    savedConfirmClearLogs = true; confirmClearLogs = true
    savedShowRequestTypes = false; showRequestTypes = false
    savedShowAdvancedMetrics = false; showAdvancedMetrics = false
    savedCorsAllowedOrigins = ""; corsAllowedOrigins = ""
    corsError = false
    savedLogPersistenceEnabled = LlmHttpPrefs.isLogPersistenceEnabled(context)
    logPersistenceEnabled = savedLogPersistenceEnabled
    savedLogMaxEntries = LlmHttpPrefs.getLogMaxEntries(context)
    logMaxEntries = savedLogMaxEntries
    savedLogAutoDeleteMinutes = LlmHttpPrefs.getLogAutoDeleteMinutes(context)
    logAutoDeleteMinutes = savedLogAutoDeleteMinutes
    savedIgnoreClientSamplerParams = false; ignoreClientSamplerParams = false
    savedKeepAliveEnabled = false; keepAliveEnabled = false
    savedKeepAliveMinutes = 5; keepAliveMinutes = 5
    keepAliveError = false; keepAliveUnit = "minutes"
    savedUpdateCheckEnabled = true; updateCheckEnabled = true
    savedUpdateCheckIntervalHours = 24; updateCheckIntervalHours = 24
    updateCheckError = false; updateCheckUnit = "hours"
    verboseDebugEnabled = false
    LlmHttpPrefs.setVerboseDebugEnabled(context, false)

    // Reset persistence and update check
    persistence.updateMaxEntries()
    persistence.clearPersistedLogs()
    UpdateCheckWorker.scheduleUpdateCheck(context)
  }

  // ─── Utility ─────────────────────────────────────────────────────────────

  companion object {
    private fun fmtToggle(enabled: Boolean) = if (enabled) "enabled" else "disabled"

    /** Formats a duration in minutes into human-readable text (e.g. 10080 → "7 days"). */
    fun formatMinutesHumanReadable(minutes: Long): String = when {
      minutes == 0L -> "disabled"
      minutes % (24 * 60) == 0L -> "${minutes / (24 * 60)} ${if (minutes / (24 * 60) == 1L) "day" else "days"}"
      minutes % 60 == 0L -> "${minutes / 60} ${if (minutes / 60 == 1L) "hour" else "hours"}"
      else -> "$minutes ${if (minutes == 1L) "minute" else "minutes"}"
    }

    /**
     * Validates CORS allowed origins input.
     * Valid formats: blank (disabled), "*" (allow all), or comma-separated origin URLs.
     */
    fun isValidCorsOrigins(input: String): Boolean {
      val trimmed = input.trim()
      if (trimmed.isEmpty() || trimmed == "*") return true
      return trimmed.split(",").all { entry ->
        val origin = entry.trim()
        origin.isNotEmpty() && (origin.startsWith("http://") || origin.startsWith("https://")) &&
          origin.substringAfter("://").let { host ->
            host.isNotEmpty() && !host.startsWith("/") && !host.contains(" ")
          }
      }
    }

    /** Highlights all occurrences of search query words in the given text with the specified color. */
    fun highlightSearchMatches(
      text: String,
      query: String,
      highlightColor: Color,
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
  }
}
