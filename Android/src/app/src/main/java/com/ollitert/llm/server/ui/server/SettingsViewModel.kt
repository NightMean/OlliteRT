package com.ollitert.llm.server.ui.server

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.data.db.RequestLogPersistence
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LlmHttpService
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.ui.server.settings.CardId
import com.ollitert.llm.server.ui.server.settings.SettingDef
import com.ollitert.llm.server.ui.server.settings.SettingEntry
import com.ollitert.llm.server.ui.server.settings.allCardDefs
import com.ollitert.llm.server.ui.server.settings.settingDefsByKey
import com.ollitert.llm.server.worker.UpdateCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * ViewModel for the Settings screen. Owns all settings state, validation,
 * change detection, search filtering, and save/reset logic.
 *
 * Uses [SettingEntry] instances for each persisted setting, enabling automatic
 * change detection, save, revert, and reset via iteration over [allSettings].
 * The UI reads/writes entries directly (e.g. `entry.current`, `entry.update()`).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val persistence: RequestLogPersistence,
) : ViewModel() {

  // ─── Setting Entries (data-driven state) ────────────────────────────────
  // Each SettingEntry tracks saved + current value for one persisted setting.

  val portEntry = SettingEntry(LlmHttpPrefs.getPort(context))
  val bearerEnabledEntry = SettingEntry(LlmHttpPrefs.getBearerToken(context).isNotBlank())
  val bearerTokenEntry = SettingEntry(LlmHttpPrefs.getBearerToken(context))
  val hfTokenEntry = SettingEntry(LlmHttpPrefs.getHfToken(context))
  val defaultModelEntry = SettingEntry(LlmHttpPrefs.getDefaultModelName(context))
  val autoStartOnBootEntry = SettingEntry(LlmHttpPrefs.isAutoStartOnBoot(context))
  val keepScreenOnEntry = SettingEntry(LlmHttpPrefs.isKeepScreenOn(context))
  val autoExpandLogsEntry = SettingEntry(LlmHttpPrefs.isAutoExpandLogs(context))
  val warmupEnabledEntry = SettingEntry(LlmHttpPrefs.isWarmupEnabled(context))
  val streamLogsPreviewEntry = SettingEntry(LlmHttpPrefs.isStreamLogsPreview(context))
  val keepPartialResponseEntry = SettingEntry(LlmHttpPrefs.isKeepPartialResponse(context))
  val eagerVisionInitEntry = SettingEntry(LlmHttpPrefs.isEagerVisionInit(context))
  val customPromptsEnabledEntry = SettingEntry(LlmHttpPrefs.isCustomPromptsEnabled(context))
  val autoTruncateHistoryEntry = SettingEntry(LlmHttpPrefs.isAutoTruncateHistory(context))
  val autoTrimPromptsEntry = SettingEntry(LlmHttpPrefs.isAutoTrimPrompts(context))
  val compactToolSchemasEntry = SettingEntry(LlmHttpPrefs.isCompactToolSchemas(context))
  val compactImageDataEntry = SettingEntry(LlmHttpPrefs.isCompactImageData(context))
  val hideHealthLogsEntry = SettingEntry(LlmHttpPrefs.isHideHealthLogs(context))
  val clearLogsOnStopEntry = SettingEntry(LlmHttpPrefs.isClearLogsOnStop(context))
  val confirmClearLogsEntry = SettingEntry(LlmHttpPrefs.isConfirmClearLogs(context))
  val showRequestTypesEntry = SettingEntry(LlmHttpPrefs.isShowRequestTypes(context))
  val showAdvancedMetricsEntry = SettingEntry(LlmHttpPrefs.isShowAdvancedMetrics(context))
  val corsAllowedOriginsEntry = SettingEntry(LlmHttpPrefs.getCorsAllowedOrigins(context))
  val logPersistenceEnabledEntry = SettingEntry(LlmHttpPrefs.isLogPersistenceEnabled(context))
  val logMaxEntriesEntry = SettingEntry(LlmHttpPrefs.getLogMaxEntries(context))
  val logAutoDeleteMinutesEntry = SettingEntry(LlmHttpPrefs.getLogAutoDeleteMinutes(context))
  val ignoreClientSamplerParamsEntry = SettingEntry(LlmHttpPrefs.isIgnoreClientSamplerParams(context))
  val keepAliveEnabledEntry = SettingEntry(LlmHttpPrefs.isKeepAliveEnabled(context))
  val keepAliveMinutesEntry = SettingEntry(LlmHttpPrefs.getKeepAliveMinutes(context))
  val updateCheckEnabledEntry = SettingEntry(LlmHttpPrefs.isUpdateCheckEnabled(context))
  val updateCheckIntervalHoursEntry = SettingEntry(LlmHttpPrefs.getUpdateCheckIntervalHours(context))
  val verboseDebugEnabledEntry = SettingEntry(LlmHttpPrefs.isVerboseDebugEnabled(context))

  /** All setting entries for bulk operations (change detection, discard, apply). */
  private val allSettings: List<SettingEntry<*>> = listOf(
    portEntry, bearerEnabledEntry, bearerTokenEntry, hfTokenEntry,
    defaultModelEntry, autoStartOnBootEntry, keepScreenOnEntry,
    autoExpandLogsEntry, warmupEnabledEntry, streamLogsPreviewEntry,
    keepPartialResponseEntry, eagerVisionInitEntry, customPromptsEnabledEntry,
    autoTruncateHistoryEntry, autoTrimPromptsEntry, compactToolSchemasEntry,
    compactImageDataEntry, hideHealthLogsEntry, clearLogsOnStopEntry,
    confirmClearLogsEntry, showRequestTypesEntry, showAdvancedMetricsEntry,
    corsAllowedOriginsEntry, logPersistenceEnabledEntry, logMaxEntriesEntry,
    logAutoDeleteMinutesEntry, ignoreClientSamplerParamsEntry,
    keepAliveEnabledEntry, keepAliveMinutesEntry,
    updateCheckEnabledEntry, updateCheckIntervalHoursEntry,
    verboseDebugEnabledEntry,
  )

  // ─── UI State (non-persisted) ────────────────────────────────────────────

  var portText by mutableStateOf(portEntry.saved.toString())
  var portError by mutableStateOf(false)
  var hfTokenVisible by mutableStateOf(false)
  var showModelDropdown by mutableStateOf(false)
  var corsError by mutableStateOf(false)
  var keepAliveError by mutableStateOf(false)
  var updateCheckError by mutableStateOf(false)

  // ─── Dialog State ────────────────────────────────────────────────────────
  var showRestartDialog by mutableStateOf(false)
  var showClearPersistedDialog by mutableStateOf(false)
  var showTrimLogsDialog by mutableStateOf(false)
  var showResetDialog by mutableStateOf(false)
  var showDiscardDialog by mutableStateOf(false)
  var showDonateDialog by mutableStateOf(false)

  // ─── Search ──────────────────────────────────────────────────────────────
  var searchQuery by mutableStateOf("")

  /** Returns true if an individual setting matches the current search query. */
  fun settingVisible(settingKey: String): Boolean {
    if (searchQuery.isBlank()) return true
    val def = settingDefsByKey[settingKey] ?: return true
    val searchable = def.searchKeywords
    val query = searchQuery.trim().lowercase()
    return query.split("\\s+".toRegex()).all { word ->
      searchable.lowercase().contains(word)
    }
  }

  /** Returns true if the card should be visible (any of its settings match). */
  fun cardVisible(cardKey: String): Boolean {
    if (searchQuery.isBlank()) return true
    val cardId = try { CardId.valueOf(cardKey.uppercase()) } catch (_: Exception) { return true }
    return cardVisible(cardId)
  }

  /** Returns true if the card should be visible (any of its settings match). */
  fun cardVisible(cardId: CardId): Boolean {
    if (searchQuery.isBlank()) return true
    val cardDef = allCardDefs.firstOrNull { it.id == cardId } ?: return true
    return cardDef.settings.any { settingVisible(it.key) }
  }

  /** Returns the SettingEntry for a toggle setting by key. */
  fun getToggleEntry(key: String): SettingEntry<Boolean>? = when (key) {
    "keep_screen_awake" -> keepScreenOnEntry
    "auto_expand_logs" -> autoExpandLogsEntry
    "stream_response_preview" -> streamLogsPreviewEntry
    "compact_image_data" -> compactImageDataEntry
    "hide_health_logs" -> hideHealthLogsEntry
    "clear_logs_on_stop" -> clearLogsOnStopEntry
    "confirm_clear_logs" -> confirmClearLogsEntry
    "keep_partial_response" -> keepPartialResponseEntry
    "start_on_boot" -> autoStartOnBootEntry
    "keep_alive" -> keepAliveEnabledEntry
    "auto_update_check" -> updateCheckEnabledEntry
    "show_request_types" -> showRequestTypesEntry
    "show_advanced_metrics" -> showAdvancedMetricsEntry
    "log_persistence_enabled" -> logPersistenceEnabledEntry
    "warmup_message" -> warmupEnabledEntry
    "pre_init_vision" -> eagerVisionInitEntry
    "custom_prompts" -> customPromptsEnabledEntry
    "truncate_history" -> autoTruncateHistoryEntry
    "compact_tool_schemas" -> compactToolSchemasEntry
    "trim_prompt" -> autoTrimPromptsEntry
    "ignore_client_params" -> ignoreClientSamplerParamsEntry
    "verbose_debug" -> verboseDebugEnabledEntry
    else -> null
  }

  /** Whether a setting is interactive (not disabled by a parent dependency). */
  fun isSettingEnabled(key: String): Boolean = when (key) {
    "start_on_boot" -> defaultModelEntry.current != null
    "keep_alive_timeout" -> keepAliveEnabledEntry.current
    "check_frequency", "auto_update_check" -> true
    "log_max_entries", "log_auto_delete", "clear_all_logs" -> logPersistenceEnabledEntry.current
    else -> true
  }

  /** Alpha for settings that dim when their parent is disabled. */
  fun settingAlpha(key: String): Float = when (key) {
    "start_on_boot" -> if (defaultModelEntry.current != null) 1f else 0.4f
    "keep_alive_timeout" -> if (keepAliveEnabledEntry.current) 1f else 0.4f
    "log_max_entries", "log_auto_delete", "clear_all_logs" -> if (logPersistenceEnabledEntry.current) 1f else 0.4f
    else -> 1f
  }

  // ─── Change Detection ────────────────────────────────────────────────────

  private val effectiveBearerToken: String
    get() = if (bearerEnabledEntry.current) bearerTokenEntry.current else ""

  val hasUnsavedChanges: Boolean get() {
    // Port is stored as Int but edited as String — compare via parsed int
    val portChanged = portText != portEntry.saved.toString()
    // Bearer token uses effective value (blank when disabled)
    val bearerChanged = effectiveBearerToken != bearerTokenEntry.saved
    // All other entries use SettingEntry change detection
    val entryChanged = allSettings.any { entry ->
      // Skip port, bearerEnabled, bearerToken — handled via special logic above
      entry !== portEntry && entry !== bearerEnabledEntry && entry !== bearerTokenEntry && entry.isChanged
    }
    return portChanged || bearerChanged || entryChanged
  }

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
    if (logMaxEntriesEntry.current < currentCount && logMaxEntriesEntry.isChanged) {
      return SaveResult.NeedsTrimConfirmation(currentCount, logMaxEntriesEntry.current)
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
    if (!isValidCorsOrigins(corsAllowedOriginsEntry.current)) {
      corsError = true
      return SaveResult.ValidationError("Invalid CORS origins — use *, blank, or comma-separated URLs with http(s)://")
    }
    if (keepAliveEnabledEntry.current && keepAliveMinutesEntry.current !in 1..7200) {
      keepAliveError = true
      return SaveResult.ValidationError("Keep-alive timeout must be between 1 and 7200 minutes")
    }
    if (updateCheckEnabledEntry.current && updateCheckIntervalHoursEntry.current !in 1..720) {
      updateCheckError = true
      return SaveResult.ValidationError("Update check interval must be between 1 and 720 hours")
    }

    // ── Clear validation errors ──
    corsError = false
    keepAliveError = false
    updateCheckError = false

    val port = portText.toInt()
    val isPortChanged = port != portEntry.saved
    val isEagerVisionChanged = eagerVisionInitEntry.isChanged
    val needsRestart = isPortChanged || isEagerVisionChanged
    val isServerActive = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING

    // ── Persist to SharedPreferences ──
    LlmHttpPrefs.save(context, LlmHttpPrefs.isEnabled(context), port)
    LlmHttpPrefs.setBearerToken(context, effectiveBearerToken)
    LlmHttpPrefs.setHfToken(context, hfTokenEntry.current)
    LlmHttpPrefs.setDefaultModelName(context, defaultModelEntry.current)
    LlmHttpPrefs.setAutoStartOnBoot(context, autoStartOnBootEntry.current)
    LlmHttpPrefs.setKeepScreenOn(context, keepScreenOnEntry.current)
    LlmHttpPrefs.setAutoExpandLogs(context, autoExpandLogsEntry.current)
    LlmHttpPrefs.setWarmupEnabled(context, warmupEnabledEntry.current)
    LlmHttpPrefs.setStreamLogsPreview(context, streamLogsPreviewEntry.current)
    LlmHttpPrefs.setKeepPartialResponse(context, keepPartialResponseEntry.current)
    LlmHttpPrefs.setEagerVisionInit(context, eagerVisionInitEntry.current)
    LlmHttpPrefs.setCustomPromptsEnabled(context, customPromptsEnabledEntry.current)
    LlmHttpPrefs.setAutoTruncateHistory(context, autoTruncateHistoryEntry.current)
    LlmHttpPrefs.setAutoTrimPrompts(context, autoTrimPromptsEntry.current)
    LlmHttpPrefs.setCompactToolSchemas(context, compactToolSchemasEntry.current)
    LlmHttpPrefs.setIgnoreClientSamplerParams(context, ignoreClientSamplerParamsEntry.current)
    LlmHttpPrefs.setKeepAliveEnabled(context, keepAliveEnabledEntry.current)
    LlmHttpPrefs.setKeepAliveMinutes(context, keepAliveMinutesEntry.current)
    if ((keepAliveEnabledEntry.isChanged || keepAliveMinutesEntry.isChanged) && isServerActive) {
      LlmHttpService.resetKeepAliveTimer(context)
    }
    LlmHttpPrefs.setUpdateCheckEnabled(context, updateCheckEnabledEntry.current)
    LlmHttpPrefs.setUpdateCheckIntervalHours(context, updateCheckIntervalHoursEntry.current)
    if (updateCheckEnabledEntry.isChanged || updateCheckIntervalHoursEntry.isChanged) {
      if (updateCheckEnabledEntry.current) UpdateCheckWorker.scheduleUpdateCheck(context)
      else UpdateCheckWorker.cancelUpdateCheck(context)
    }
    LlmHttpPrefs.setCompactImageData(context, compactImageDataEntry.current)
    LlmHttpPrefs.setHideHealthLogs(context, hideHealthLogsEntry.current)
    LlmHttpPrefs.setClearLogsOnStop(context, clearLogsOnStopEntry.current)
    LlmHttpPrefs.setConfirmClearLogs(context, confirmClearLogsEntry.current)
    LlmHttpPrefs.setShowRequestTypes(context, showRequestTypesEntry.current)
    LlmHttpPrefs.setShowAdvancedMetrics(context, showAdvancedMetricsEntry.current)
    LlmHttpPrefs.setCorsAllowedOrigins(context, corsAllowedOriginsEntry.current)
    LlmHttpPrefs.setLogPersistenceEnabled(context, logPersistenceEnabledEntry.current)
    LlmHttpPrefs.setLogMaxEntries(context, logMaxEntriesEntry.current)
    LlmHttpPrefs.setLogAutoDeleteMinutes(context, logAutoDeleteMinutesEntry.current)
    LlmHttpPrefs.setVerboseDebugEnabled(context, verboseDebugEnabledEntry.current)

    // ── Log changes ──
    logSettingsChanges(port)

    // Write a full settings snapshot to logcat when verbose debug is turned on,
    // so exported debug logs contain the active configuration for diagnosis.
    if (verboseDebugEnabledEntry.current && !verboseDebugEnabledEntry.saved) {
      LlmHttpPrefs.dumpToLogcat(context)
    }

    // ── Sync persistence layer ──
    if (logPersistenceEnabledEntry.current && !logPersistenceEnabledEntry.saved) {
      persistence.persistCurrentEntries()
    }
    persistence.updateMaxEntries()

    // ── Advance saved baselines ──
    portEntry.update(port)
    portEntry.apply()
    portText = port.toString()
    bearerEnabledEntry.apply()
    bearerTokenEntry.update(if (bearerEnabledEntry.current) bearerTokenEntry.current else "")
    bearerTokenEntry.apply()
    // Apply all other entries
    for (entry in allSettings) {
      if (entry !== portEntry && entry !== bearerEnabledEntry && entry !== bearerTokenEntry) {
        entry.apply()
      }
    }

    // Re-check live server status before triggering restart — the server may have crashed
    // or stopped between when the user opened Settings and when they pressed Save.
    val liveStatus = com.ollitert.llm.server.service.ServerMetrics.status.value
    val isStillActive = liveStatus == ServerStatus.RUNNING || liveStatus == ServerStatus.LOADING
    return if (needsRestart && isServerActive && isStillActive) {
      SaveResult.NeedsRestart(keepScreenOn = keepScreenOnEntry.current)
    } else {
      SaveResult.Success
    }
  }

  /** Collects all behavioral settings changes into one grouped log entry. */
  private fun logSettingsChanges(newPort: Int) {
    val changes = mutableListOf<String>()
    if (newPort != portEntry.saved) changes.add("Port: ${portEntry.saved} → $newPort")
    val bearerWasEnabled = bearerTokenEntry.saved.isNotBlank()
    val bearerIsEnabled = effectiveBearerToken.isNotBlank()
    if (bearerWasEnabled != bearerIsEnabled)
      changes.add("Bearer Auth: ${if (bearerWasEnabled) "enabled" else "disabled"} → ${if (bearerIsEnabled) "enabled" else "disabled"}")
    if (autoStartOnBootEntry.isChanged)
      changes.add("Auto-Start on Boot: ${fmtToggle(autoStartOnBootEntry.saved)} → ${fmtToggle(autoStartOnBootEntry.current)}")
    if (warmupEnabledEntry.isChanged)
      changes.add("Warmup Message: ${fmtToggle(warmupEnabledEntry.saved)} → ${fmtToggle(warmupEnabledEntry.current)}")
    if (eagerVisionInitEntry.isChanged)
      changes.add("Pre-initialize Vision: ${fmtToggle(eagerVisionInitEntry.saved)} → ${fmtToggle(eagerVisionInitEntry.current)}")
    if (customPromptsEnabledEntry.isChanged)
      changes.add("Custom System Prompt & Chat Template: ${fmtToggle(customPromptsEnabledEntry.saved)} → ${fmtToggle(customPromptsEnabledEntry.current)}")
    if (ignoreClientSamplerParamsEntry.isChanged)
      changes.add("Ignore Client Sampler Parameters: ${fmtToggle(ignoreClientSamplerParamsEntry.saved)} → ${fmtToggle(ignoreClientSamplerParamsEntry.current)}")
    if (autoTruncateHistoryEntry.isChanged)
      changes.add("Truncate Conversation History: ${fmtToggle(autoTruncateHistoryEntry.saved)} → ${fmtToggle(autoTruncateHistoryEntry.current)}")
    if (compactToolSchemasEntry.isChanged)
      changes.add("Compact Tool Schemas: ${fmtToggle(compactToolSchemasEntry.saved)} → ${fmtToggle(compactToolSchemasEntry.current)}")
    if (autoTrimPromptsEntry.isChanged)
      changes.add("Trim Prompt: ${fmtToggle(autoTrimPromptsEntry.saved)} → ${fmtToggle(autoTrimPromptsEntry.current)}")
    if (corsAllowedOriginsEntry.isChanged)
      changes.add("CORS Allowed Origins: ${corsAllowedOriginsEntry.saved.ifBlank { "disabled" }} → ${corsAllowedOriginsEntry.current.ifBlank { "disabled" }}")
    if (compactImageDataEntry.isChanged)
      changes.add("Compact Image Data: ${fmtToggle(compactImageDataEntry.saved)} → ${fmtToggle(compactImageDataEntry.current)}")
    if (hideHealthLogsEntry.isChanged)
      changes.add("Hide Health Logs: ${fmtToggle(hideHealthLogsEntry.saved)} → ${fmtToggle(hideHealthLogsEntry.current)}")
    if (logPersistenceEnabledEntry.isChanged)
      changes.add("Log Persistence: ${fmtToggle(logPersistenceEnabledEntry.saved)} → ${fmtToggle(logPersistenceEnabledEntry.current)}")
    if (logMaxEntriesEntry.isChanged)
      changes.add("Log Max Entries: ${logMaxEntriesEntry.saved} → ${logMaxEntriesEntry.current}")
    if (logAutoDeleteMinutesEntry.isChanged)
      changes.add("Log Auto-Delete: ${formatMinutesHumanReadable(logAutoDeleteMinutesEntry.saved)} → ${formatMinutesHumanReadable(logAutoDeleteMinutesEntry.current)}")
    if (keepAliveEnabledEntry.isChanged)
      changes.add("Keep Alive: ${fmtToggle(keepAliveEnabledEntry.saved)} → ${fmtToggle(keepAliveEnabledEntry.current)}")
    if (keepAliveMinutesEntry.isChanged)
      changes.add("Keep Alive Timeout: ${keepAliveMinutesEntry.saved}m → ${keepAliveMinutesEntry.current}m")
    if (updateCheckEnabledEntry.isChanged)
      changes.add("Check for Updates: ${fmtToggle(updateCheckEnabledEntry.saved)} → ${fmtToggle(updateCheckEnabledEntry.current)}")
    if (updateCheckIntervalHoursEntry.isChanged) {
      fun fmtInterval(hours: Int): String = when {
        hours % 24 == 0 -> "${hours / 24} ${if (hours / 24 == 1) "day" else "days"}"
        else -> "$hours ${if (hours == 1) "hour" else "hours"}"
      }
      changes.add("Update Check Frequency: ${fmtInterval(updateCheckIntervalHoursEntry.saved)} → ${fmtInterval(updateCheckIntervalHoursEntry.current)}")
    }
    if (verboseDebugEnabledEntry.isChanged)
      changes.add("Verbose Debug Mode: ${fmtToggle(verboseDebugEnabledEntry.saved)} → ${fmtToggle(verboseDebugEnabledEntry.current)}")
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

    // Reset all entries to their factory-reset defaults
    portEntry.reset(LlmHttpPrefs.getPort(context))
    portText = portEntry.saved.toString()
    portError = false
    bearerEnabledEntry.reset(false)
    bearerTokenEntry.reset("")
    hfTokenEntry.reset(LlmHttpPrefs.getHfToken(context))
    defaultModelEntry.reset("")
    autoStartOnBootEntry.reset(false)
    keepScreenOnEntry.reset(false)
    autoExpandLogsEntry.reset(false)
    warmupEnabledEntry.reset(true)
    streamLogsPreviewEntry.reset(true)
    keepPartialResponseEntry.reset(false)
    eagerVisionInitEntry.reset(false)
    customPromptsEnabledEntry.reset(false)
    autoTruncateHistoryEntry.reset(true)
    autoTrimPromptsEntry.reset(false)
    compactToolSchemasEntry.reset(true)
    compactImageDataEntry.reset(true)
    hideHealthLogsEntry.reset(false)
    clearLogsOnStopEntry.reset(false)
    confirmClearLogsEntry.reset(true)
    showRequestTypesEntry.reset(false)
    showAdvancedMetricsEntry.reset(false)
    corsAllowedOriginsEntry.reset("")
    corsError = false
    logPersistenceEnabledEntry.reset(LlmHttpPrefs.isLogPersistenceEnabled(context))
    logMaxEntriesEntry.reset(LlmHttpPrefs.getLogMaxEntries(context))
    logAutoDeleteMinutesEntry.reset(LlmHttpPrefs.getLogAutoDeleteMinutes(context))
    ignoreClientSamplerParamsEntry.reset(false)
    keepAliveEnabledEntry.reset(false)
    keepAliveMinutesEntry.reset(5)
    keepAliveError = false
    updateCheckEnabledEntry.reset(true)
    updateCheckIntervalHoursEntry.reset(24)
    updateCheckError = false
    verboseDebugEnabledEntry.reset(false)

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

  }
}
