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
 *
 * State is exposed as individual properties (via bridge getters/setters that delegate
 * to SettingEntry.current) for backward compatibility with SettingsScreen.kt.
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

  // ─── Bridge Properties (temporary) ─────────────────
  // These maintain the same public API that SettingsScreen.kt currently uses.

  var portText by mutableStateOf(portEntry.saved.toString())
  var portError by mutableStateOf(false)
  var bearerEnabled: Boolean
    get() = bearerEnabledEntry.current
    set(value) { bearerEnabledEntry.update(value) }
  var bearerToken: String
    get() = bearerTokenEntry.current
    set(value) { bearerTokenEntry.update(value) }
  var hfToken: String
    get() = hfTokenEntry.current
    set(value) { hfTokenEntry.update(value) }
  var hfTokenVisible by mutableStateOf(false)
  var defaultModelName: String?
    get() = defaultModelEntry.current
    set(value) { defaultModelEntry.update(value) }
  var showModelDropdown by mutableStateOf(false)
  var autoStartOnBoot: Boolean
    get() = autoStartOnBootEntry.current
    set(value) { autoStartOnBootEntry.update(value) }
  var keepScreenOn: Boolean
    get() = keepScreenOnEntry.current
    set(value) { keepScreenOnEntry.update(value) }
  var autoExpandLogs: Boolean
    get() = autoExpandLogsEntry.current
    set(value) { autoExpandLogsEntry.update(value) }
  var warmupEnabled: Boolean
    get() = warmupEnabledEntry.current
    set(value) { warmupEnabledEntry.update(value) }
  var streamLogsPreview: Boolean
    get() = streamLogsPreviewEntry.current
    set(value) { streamLogsPreviewEntry.update(value) }
  var keepPartialResponse: Boolean
    get() = keepPartialResponseEntry.current
    set(value) { keepPartialResponseEntry.update(value) }
  var eagerVisionInit: Boolean
    get() = eagerVisionInitEntry.current
    set(value) { eagerVisionInitEntry.update(value) }
  var customPromptsEnabled: Boolean
    get() = customPromptsEnabledEntry.current
    set(value) { customPromptsEnabledEntry.update(value) }
  var autoTruncateHistory: Boolean
    get() = autoTruncateHistoryEntry.current
    set(value) { autoTruncateHistoryEntry.update(value) }
  var autoTrimPrompts: Boolean
    get() = autoTrimPromptsEntry.current
    set(value) { autoTrimPromptsEntry.update(value) }
  var compactToolSchemas: Boolean
    get() = compactToolSchemasEntry.current
    set(value) { compactToolSchemasEntry.update(value) }
  var compactImageData: Boolean
    get() = compactImageDataEntry.current
    set(value) { compactImageDataEntry.update(value) }
  var hideHealthLogs: Boolean
    get() = hideHealthLogsEntry.current
    set(value) { hideHealthLogsEntry.update(value) }
  var clearLogsOnStop: Boolean
    get() = clearLogsOnStopEntry.current
    set(value) { clearLogsOnStopEntry.update(value) }
  var confirmClearLogs: Boolean
    get() = confirmClearLogsEntry.current
    set(value) { confirmClearLogsEntry.update(value) }
  var showRequestTypes: Boolean
    get() = showRequestTypesEntry.current
    set(value) { showRequestTypesEntry.update(value) }
  var showAdvancedMetrics: Boolean
    get() = showAdvancedMetricsEntry.current
    set(value) { showAdvancedMetricsEntry.update(value) }
  var corsAllowedOrigins: String
    get() = corsAllowedOriginsEntry.current
    set(value) { corsAllowedOriginsEntry.update(value) }
  var corsError by mutableStateOf(false)
  var logPersistenceEnabled: Boolean
    get() = logPersistenceEnabledEntry.current
    set(value) { logPersistenceEnabledEntry.update(value) }
  var logMaxEntries: Int
    get() = logMaxEntriesEntry.current
    set(value) { logMaxEntriesEntry.update(value) }
  var logAutoDeleteMinutes: Long
    get() = logAutoDeleteMinutesEntry.current
    set(value) { logAutoDeleteMinutesEntry.update(value) }
  var ignoreClientSamplerParams: Boolean
    get() = ignoreClientSamplerParamsEntry.current
    set(value) { ignoreClientSamplerParamsEntry.update(value) }
  var keepAliveEnabled: Boolean
    get() = keepAliveEnabledEntry.current
    set(value) { keepAliveEnabledEntry.update(value) }
  var keepAliveMinutes: Int
    get() = keepAliveMinutesEntry.current
    set(value) { keepAliveMinutesEntry.update(value) }
  var keepAliveError by mutableStateOf(false)
  var updateCheckError by mutableStateOf(false)
  var keepAliveUnit by mutableStateOf(
    if (keepAliveMinutesEntry.saved > 0 && keepAliveMinutesEntry.saved % 60 == 0) "hours" else "minutes"
  )
  var updateCheckEnabled: Boolean
    get() = updateCheckEnabledEntry.current
    set(value) { updateCheckEnabledEntry.update(value) }
  var updateCheckIntervalHours: Int
    get() = updateCheckIntervalHoursEntry.current
    set(value) { updateCheckIntervalHoursEntry.update(value) }
  var updateCheckUnit by mutableStateOf(
    if (updateCheckIntervalHoursEntry.saved > 0 && updateCheckIntervalHoursEntry.saved % 24 == 0) "days" else "hours"
  )
  var verboseDebugEnabled: Boolean
    get() = verboseDebugEnabledEntry.current
    set(value) { verboseDebugEnabledEntry.update(value) }

  // Expose saved values for SettingsScreen references that read savedKeepAliveMinutes etc.
  val savedKeepAliveMinutes: Int get() = keepAliveMinutesEntry.saved
  val savedUpdateCheckIntervalHours: Int get() = updateCheckIntervalHoursEntry.saved
  val savedLogAutoDeleteMinutes: Long get() = logAutoDeleteMinutesEntry.saved

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
    val cardDef = allCardDefs.firstOrNull { it.id == cardId } ?: return true
    return cardDef.settings.any { settingVisible(it.key) }
  }

  // ─── Change Detection ────────────────────────────────────────────────────

  private val effectiveBearerToken: String get() = if (bearerEnabled) bearerToken else ""

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
    if (logMaxEntries < currentCount && logMaxEntriesEntry.isChanged) {
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
    val isPortChanged = port != portEntry.saved
    val isEagerVisionChanged = eagerVisionInitEntry.isChanged
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
    if ((keepAliveEnabledEntry.isChanged || keepAliveMinutesEntry.isChanged) && isServerActive) {
      LlmHttpService.resetKeepAliveTimer(context)
    }
    LlmHttpPrefs.setUpdateCheckEnabled(context, updateCheckEnabled)
    LlmHttpPrefs.setUpdateCheckIntervalHours(context, updateCheckIntervalHours)
    if (updateCheckEnabledEntry.isChanged || updateCheckIntervalHoursEntry.isChanged) {
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
    LlmHttpPrefs.setVerboseDebugEnabled(context, verboseDebugEnabled)

    // ── Log changes ──
    logSettingsChanges(port)

    // Write a full settings snapshot to logcat when verbose debug is turned on,
    // so exported debug logs contain the active configuration for diagnosis.
    if (verboseDebugEnabled && !verboseDebugEnabledEntry.saved) {
      LlmHttpPrefs.dumpToLogcat(context)
    }

    // ── Sync persistence layer ──
    if (logPersistenceEnabled && !logPersistenceEnabledEntry.saved) {
      persistence.persistCurrentEntries()
    }
    persistence.updateMaxEntries()

    // ── Advance saved baselines ──
    portEntry.update(port)
    portEntry.apply()
    portText = port.toString()
    bearerEnabledEntry.apply()
    bearerTokenEntry.update(if (bearerEnabled) bearerToken else "")
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
      SaveResult.NeedsRestart(keepScreenOn = keepScreenOn)
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
      changes.add("Auto-Start on Boot: ${fmtToggle(autoStartOnBootEntry.saved)} → ${fmtToggle(autoStartOnBoot)}")
    if (warmupEnabledEntry.isChanged)
      changes.add("Warmup Message: ${fmtToggle(warmupEnabledEntry.saved)} → ${fmtToggle(warmupEnabled)}")
    if (eagerVisionInitEntry.isChanged)
      changes.add("Pre-initialize Vision: ${fmtToggle(eagerVisionInitEntry.saved)} → ${fmtToggle(eagerVisionInit)}")
    if (customPromptsEnabledEntry.isChanged)
      changes.add("Custom System Prompt & Chat Template: ${fmtToggle(customPromptsEnabledEntry.saved)} → ${fmtToggle(customPromptsEnabled)}")
    if (ignoreClientSamplerParamsEntry.isChanged)
      changes.add("Ignore Client Sampler Parameters: ${fmtToggle(ignoreClientSamplerParamsEntry.saved)} → ${fmtToggle(ignoreClientSamplerParams)}")
    if (autoTruncateHistoryEntry.isChanged)
      changes.add("Truncate Conversation History: ${fmtToggle(autoTruncateHistoryEntry.saved)} → ${fmtToggle(autoTruncateHistory)}")
    if (compactToolSchemasEntry.isChanged)
      changes.add("Compact Tool Schemas: ${fmtToggle(compactToolSchemasEntry.saved)} → ${fmtToggle(compactToolSchemas)}")
    if (autoTrimPromptsEntry.isChanged)
      changes.add("Trim Prompt: ${fmtToggle(autoTrimPromptsEntry.saved)} → ${fmtToggle(autoTrimPrompts)}")
    if (corsAllowedOriginsEntry.isChanged)
      changes.add("CORS Allowed Origins: ${corsAllowedOriginsEntry.saved.ifBlank { "disabled" }} → ${corsAllowedOrigins.ifBlank { "disabled" }}")
    if (compactImageDataEntry.isChanged)
      changes.add("Compact Image Data: ${fmtToggle(compactImageDataEntry.saved)} → ${fmtToggle(compactImageData)}")
    if (hideHealthLogsEntry.isChanged)
      changes.add("Hide Health Logs: ${fmtToggle(hideHealthLogsEntry.saved)} → ${fmtToggle(hideHealthLogs)}")
    if (logPersistenceEnabledEntry.isChanged)
      changes.add("Log Persistence: ${fmtToggle(logPersistenceEnabledEntry.saved)} → ${fmtToggle(logPersistenceEnabled)}")
    if (logMaxEntriesEntry.isChanged)
      changes.add("Log Max Entries: ${logMaxEntriesEntry.saved} → $logMaxEntries")
    if (logAutoDeleteMinutesEntry.isChanged)
      changes.add("Log Auto-Delete: ${formatMinutesHumanReadable(logAutoDeleteMinutesEntry.saved)} → ${formatMinutesHumanReadable(logAutoDeleteMinutes)}")
    if (keepAliveEnabledEntry.isChanged)
      changes.add("Keep Alive: ${fmtToggle(keepAliveEnabledEntry.saved)} → ${fmtToggle(keepAliveEnabled)}")
    if (keepAliveMinutesEntry.isChanged)
      changes.add("Keep Alive Timeout: ${keepAliveMinutesEntry.saved}m → ${keepAliveMinutes}m")
    if (updateCheckEnabledEntry.isChanged)
      changes.add("Check for Updates: ${fmtToggle(updateCheckEnabledEntry.saved)} → ${fmtToggle(updateCheckEnabled)}")
    if (updateCheckIntervalHoursEntry.isChanged) {
      fun fmtInterval(hours: Int): String = when {
        hours % 24 == 0 -> "${hours / 24} ${if (hours / 24 == 1) "day" else "days"}"
        else -> "$hours ${if (hours == 1) "hour" else "hours"}"
      }
      changes.add("Update Check Frequency: ${fmtInterval(updateCheckIntervalHoursEntry.saved)} → ${fmtInterval(updateCheckIntervalHours)}")
    }
    if (verboseDebugEnabledEntry.isChanged)
      changes.add("Verbose Debug Mode: ${fmtToggle(verboseDebugEnabledEntry.saved)} → ${fmtToggle(verboseDebugEnabled)}")
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
    keepAliveError = false; keepAliveUnit = "minutes"
    updateCheckEnabledEntry.reset(true)
    updateCheckIntervalHoursEntry.reset(24)
    updateCheckError = false; updateCheckUnit = "hours"
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
