package com.ollitert.llm.server.ui.server

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.ui.common.matchesSearchQuery
import com.ollitert.llm.server.data.db.RequestLogPersistence
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.LlmHttpService
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.ui.server.settings.CardId
import com.ollitert.llm.server.ui.server.settings.SettingDef
import com.ollitert.llm.server.ui.server.settings.SettingEntry
import com.ollitert.llm.server.ui.server.settings.allCardDefs
import com.ollitert.llm.server.ui.server.settings.allSettingDefs
import com.ollitert.llm.server.ui.server.settings.isValidCorsOrigins
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

  /** Maps each SettingDef key to its corresponding SettingEntry for iteration. */
  private val entryByKey: Map<String, SettingEntry<*>> = buildMap {
    put("host_port", portEntry)
    put("bearer_token", bearerTokenEntry)
    put("hf_token", hfTokenEntry)
    put("default_model", defaultModelEntry)
    put("cors_origins", corsAllowedOriginsEntry)
    put("log_max_entries", logMaxEntriesEntry)
    put("log_auto_delete", logAutoDeleteMinutesEntry)
    put("keep_alive_timeout", keepAliveMinutesEntry)
    put("check_frequency", updateCheckIntervalHoursEntry)
    // All toggle entries keyed by their SettingDef key
    for (def in allSettingDefs) {
      if (def is SettingDef.Toggle) {
        getToggleEntry(def.key)?.let { put(def.key, it) }
      }
    }
  }

  // ─── UI State (non-persisted) ────────────────────────────────────────────

  var portText by mutableStateOf(portEntry.saved.toString())
  var hfTokenVisible by mutableStateOf(false)
  var showModelDropdown by mutableStateOf(false)

  /** Validation errors keyed by setting key. Compose-observable — reads trigger recomposition. */
  val validationErrors = mutableStateMapOf<String, String>()
  fun hasError(key: String): Boolean = key in validationErrors
  fun clearError(key: String) { validationErrors.remove(key) }

  // ─── Dialog State ────────────────────────────────────────────────────────
  var showRestartDialog by mutableStateOf(false)
  var showClearPersistedDialog by mutableStateOf(false)
  var showTrimLogsDialog by mutableStateOf(false)
  var showResetDialog by mutableStateOf(false)
  var showDiscardDialog by mutableStateOf(false)
  var showDonateDialog by mutableStateOf(false)

  // ─── Search ──────────────────────────────────────────────────────────────
  var searchQuery by mutableStateOf("")

  /** Card title text cached per setting key, so search can match card names (e.g. "General"). */
  private val cardTitleBySettingKey: Map<String, String> = buildMap {
    for (card in allCardDefs) {
      val title = context.getString(card.titleRes)
      for (setting in card.settings) put(setting.key, title)
    }
  }

  /** Returns true if an individual setting matches the current search query. */
  fun settingVisible(settingKey: String): Boolean {
    if (searchQuery.isBlank()) return true
    val def = settingDefsByKey[settingKey] ?: return true
    val searchable = def.searchKeywords + " " + (cardTitleBySettingKey[settingKey] ?: "")
    return matchesSearchQuery(searchable, searchQuery)
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
    "check_frequency" -> updateCheckEnabledEntry.current
    "auto_update_check" -> true
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
    validationErrors.clear()
    for (def in allSettingDefs) {
      val entry = entryByKey[def.key] ?: continue
      validateSetting(def, entry)?.let { validationErrors[def.key] = it }
    }
    if (validationErrors.isNotEmpty()) {
      return SaveResult.ValidationError(validationErrors.values.first())
    }

    val port = portText.toIntOrNull() ?: return SaveResult.ValidationError("Invalid port number")
    val isPortChanged = port != portEntry.saved
    val isEagerVisionChanged = eagerVisionInitEntry.isChanged
    val needsRestart = isPortChanged || isEagerVisionChanged
    val isServerActive = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING

    // ── Persist to SharedPreferences ──
    // Port uses a combined setter with the enabled flag
    LlmHttpPrefs.save(context, LlmHttpPrefs.isEnabled(context), port)
    // Bearer token uses effective value (blank when toggle is off)
    LlmHttpPrefs.setBearerToken(context, effectiveBearerToken)
    // All other settings: iterate definitions and persist changed entries
    for (def in allSettingDefs) {
      if (def.key == "bearer_token") continue // handled above
      val entry = entryByKey[def.key] ?: continue // Custom defs have no entry
      if (def is SettingDef.Custom) continue
      persistSetting(def, entry)
    }

    // ── Side effects ──
    if ((keepAliveEnabledEntry.isChanged || keepAliveMinutesEntry.isChanged) && isServerActive) {
      LlmHttpService.resetKeepAliveTimer(context)
    }
    if (updateCheckEnabledEntry.isChanged || updateCheckIntervalHoursEntry.isChanged) {
      if (updateCheckEnabledEntry.current) UpdateCheckWorker.scheduleUpdateCheck(context)
      else UpdateCheckWorker.cancelUpdateCheck(context)
    }

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

  /** Persists a single setting to SharedPreferences via the appropriate typed setter. */
  @Suppress("UNCHECKED_CAST")
  private fun persistSetting(def: SettingDef, entry: SettingEntry<*>) {
    when (def) {
      is SettingDef.Toggle -> {
        val value = (entry as SettingEntry<Boolean>).current
        when (def.key) {
          "keep_screen_awake" -> LlmHttpPrefs.setKeepScreenOn(context, value)
          "auto_expand_logs" -> LlmHttpPrefs.setAutoExpandLogs(context, value)
          "stream_response_preview" -> LlmHttpPrefs.setStreamLogsPreview(context, value)
          "compact_image_data" -> LlmHttpPrefs.setCompactImageData(context, value)
          "hide_health_logs" -> LlmHttpPrefs.setHideHealthLogs(context, value)
          "clear_logs_on_stop" -> LlmHttpPrefs.setClearLogsOnStop(context, value)
          "confirm_clear_logs" -> LlmHttpPrefs.setConfirmClearLogs(context, value)
          "keep_partial_response" -> LlmHttpPrefs.setKeepPartialResponse(context, value)
          "start_on_boot" -> LlmHttpPrefs.setAutoStartOnBoot(context, value)
          "keep_alive" -> LlmHttpPrefs.setKeepAliveEnabled(context, value)
          "auto_update_check" -> LlmHttpPrefs.setUpdateCheckEnabled(context, value)
          "show_request_types" -> LlmHttpPrefs.setShowRequestTypes(context, value)
          "show_advanced_metrics" -> LlmHttpPrefs.setShowAdvancedMetrics(context, value)
          "log_persistence_enabled" -> LlmHttpPrefs.setLogPersistenceEnabled(context, value)
          "warmup_message" -> LlmHttpPrefs.setWarmupEnabled(context, value)
          "pre_init_vision" -> LlmHttpPrefs.setEagerVisionInit(context, value)
          "custom_prompts" -> LlmHttpPrefs.setCustomPromptsEnabled(context, value)
          "truncate_history" -> LlmHttpPrefs.setAutoTruncateHistory(context, value)
          "compact_tool_schemas" -> LlmHttpPrefs.setCompactToolSchemas(context, value)
          "trim_prompt" -> LlmHttpPrefs.setAutoTrimPrompts(context, value)
          "ignore_client_params" -> LlmHttpPrefs.setIgnoreClientSamplerParams(context, value)
          "verbose_debug" -> LlmHttpPrefs.setVerboseDebugEnabled(context, value)
        }
      }
      is SettingDef.TextInput -> {
        val value = (entry as SettingEntry<String>).current
        when (def.key) {
          "hf_token" -> LlmHttpPrefs.setHfToken(context, value)
          "cors_origins" -> LlmHttpPrefs.setCorsAllowedOrigins(context, value)
        }
      }
      is SettingDef.NumericInput -> {} // host_port handled separately
      is SettingDef.NumericWithUnit -> when (def.key) {
        "keep_alive_timeout" -> LlmHttpPrefs.setKeepAliveMinutes(context, (entry as SettingEntry<Int>).current)
        "check_frequency" -> LlmHttpPrefs.setUpdateCheckIntervalHours(context, (entry as SettingEntry<Int>).current)
        "log_auto_delete" -> LlmHttpPrefs.setLogAutoDeleteMinutes(context, (entry as SettingEntry<Long>).current)
      }
      is SettingDef.NumericPlain -> when (def.key) {
        "log_max_entries" -> LlmHttpPrefs.setLogMaxEntries(context, (entry as SettingEntry<Int>).current)
      }
      is SettingDef.Dropdown -> when (def.key) {
        "default_model" -> LlmHttpPrefs.setDefaultModelName(context, (entry as SettingEntry<String?>).current)
      }
      is SettingDef.Custom -> {} // no persistence
    }
  }

  /** Collects all settings changes into one grouped log entry. */
  private fun logSettingsChanges(newPort: Int) {
    val changes = mutableListOf<String>()

    // Port: compared via parsed int (portText → int)
    if (newPort != portEntry.saved) changes.add("Port: ${portEntry.saved} → $newPort")

    // Bearer token: derived state (enabled = token non-blank)
    val bearerWasEnabled = bearerTokenEntry.saved.isNotBlank()
    val bearerIsEnabled = effectiveBearerToken.isNotBlank()
    if (bearerWasEnabled != bearerIsEnabled)
      changes.add("Bearer Auth: ${fmtToggle(bearerWasEnabled)} → ${fmtToggle(bearerIsEnabled)}")

    // All other settings: iterate definitions and format changed entries
    for (def in allSettingDefs) {
      if (def.key == "host_port" || def.key == "bearer_token") continue // handled above
      val entry = entryByKey[def.key] ?: continue
      if (!entry.isChanged) continue
      formatChange(def, entry)?.let { changes.add(it) }
    }

    if (changes.isNotEmpty()) {
      RequestLogStore.addEvent(
        "Settings updated (${changes.size} ${if (changes.size == 1) "change" else "changes"})",
        category = EventCategory.SETTINGS,
        body = changes.joinToString("\n"),
      )
    }
  }

  /** Formats a single setting's old→new change for the log entry. */
  private fun formatChange(def: SettingDef, entry: SettingEntry<*>): String? {
    val label = context.getString(def.labelRes)
    return when (def) {
      is SettingDef.Toggle -> {
        @Suppress("UNCHECKED_CAST")
        val e = entry as SettingEntry<Boolean>
        "$label: ${fmtToggle(e.saved)} → ${fmtToggle(e.current)}"
      }
      is SettingDef.TextInput -> {
        @Suppress("UNCHECKED_CAST")
        val e = entry as SettingEntry<String>
        if (def.isPassword) "$label: changed" // sensitive — don't log value
        else "$label: ${e.saved.ifBlank { "disabled" }} → ${e.current.ifBlank { "disabled" }}"
      }
      is SettingDef.NumericInput -> {
        @Suppress("UNCHECKED_CAST")
        val e = entry as SettingEntry<Int>
        "$label: ${e.saved} → ${e.current}"
      }
      is SettingDef.NumericWithUnit -> formatNumericWithUnitChange(def, entry, label)
      is SettingDef.NumericPlain -> {
        @Suppress("UNCHECKED_CAST")
        val e = entry as SettingEntry<Int>
        "$label: ${e.saved} → ${e.current}"
      }
      is SettingDef.Dropdown -> {
        @Suppress("UNCHECKED_CAST")
        val e = entry as SettingEntry<String?>
        "$label: ${e.saved ?: "none"} → ${e.current ?: "none"}"
      }
      is SettingDef.Custom -> null
    }
  }

  /** Formats NumericWithUnit changes using the definition's unit conversion. */
  @Suppress("UNCHECKED_CAST")
  private fun formatNumericWithUnitChange(
    def: SettingDef.NumericWithUnit,
    entry: SettingEntry<*>,
    label: String,
  ): String {
    fun fmt(base: Long): String {
      if (base == 0L) return "disabled"
      val (value, unit) = def.fromBaseUnit(base)
      val singular = unit.removeSuffix("s")
      val display = if (value == 1L) singular else unit
      return "$value $display"
    }
    // Entry may be Int (keepAliveMinutes, updateCheckIntervalHours) or Long (logAutoDeleteMinutes)
    return when (entry.saved) {
      is Int -> {
        val e = entry as SettingEntry<Int>
        "$label: ${fmt(e.saved.toLong())} → ${fmt(e.current.toLong())}"
      }
      is Long -> {
        val e = entry as SettingEntry<Long>
        "$label: ${fmt(e.saved)} → ${fmt(e.current)}"
      }
      else -> "$label: ${entry.saved} → ${entry.current}"
    }
  }

  // ─── Reset ───────────────────────────────────────────────────────────────

  /** Clear all persisted logs (in-memory + database). */
  fun clearPersistedLogs() {
    RequestLogStore.clear()
    persistence.clearPersistedLogs()
  }

  /** Reset all settings to factory defaults using SettingDef metadata. */
  @Suppress("UNCHECKED_CAST")
  fun resetToDefaults() {
    LlmHttpPrefs.resetToDefaults(context)

    // Reset each entry to its definition's resetDefault (not fresh-install default)
    for (def in allSettingDefs) {
      val entry = entryByKey[def.key] ?: continue
      when (def) {
        is SettingDef.Toggle -> (entry as SettingEntry<Boolean>).reset(def.resetDefault)
        is SettingDef.TextInput -> (entry as SettingEntry<String>).reset(def.resetDefault)
        is SettingDef.NumericInput -> (entry as SettingEntry<Int>).reset(def.default)
        is SettingDef.NumericWithUnit -> {
          // keepAliveMinutes and updateCheckIntervalHours are stored as Int entries
          // but the def's defaultValue is Long — convert to match entry type
          if (entry.saved is Int) (entry as SettingEntry<Int>).reset(def.defaultValue.toInt())
          else (entry as SettingEntry<Long>).reset(def.defaultValue)
        }
        is SettingDef.NumericPlain -> (entry as SettingEntry<Int>).reset(def.default)
        is SettingDef.Dropdown -> (entry as SettingEntry<String?>).reset(def.resetDefault)
        is SettingDef.Custom -> {} // no entry to reset
      }
    }

    // Reset bearer toggle (derived state, not a SettingDef)
    bearerEnabledEntry.reset(false)

    // Reset UI state
    portText = portEntry.saved.toString()
    validationErrors.clear()

    // Side effects
    persistence.updateMaxEntries()
    persistence.clearPersistedLogs()
    UpdateCheckWorker.scheduleUpdateCheck(context)
  }

  // ─── Validation ──────────────────────────────────────────────────────────

  /** Validates a single setting against its definition's constraints. Returns an error message or null. */
  @Suppress("UNCHECKED_CAST")
  private fun validateSetting(def: SettingDef, entry: SettingEntry<*>): String? {
    if (!isSettingEnabled(def.key)) return null

    return when (def) {
      is SettingDef.NumericInput -> {
        // Port is edited as String (portText), not directly from entry
        if (def.key == "host_port") {
          if (portText.isBlank()) return "A port number is required"
          val port = portText.toIntOrNull()
          if (port == null || port !in def.min..def.max)
            return "Port must be between ${def.min} and ${def.max}"
          null
        } else {
          val value = (entry as SettingEntry<Int>).current
          if (value !in def.min..def.max) {
            val label = context.getString(def.labelRes)
            "$label must be between ${def.min} and ${def.max}"
          } else null
        }
      }
      is SettingDef.NumericWithUnit -> {
        val value = when (val v = entry.current) {
          is Int -> v.toLong()
          is Long -> v
          else -> return null
        }
        if (value !in def.min..def.max) {
          val label = context.getString(def.labelRes)
          "$label must be between ${def.min} and ${def.max} ${def.baseUnitLabel}"
        } else null
      }
      is SettingDef.NumericPlain -> {
        val value = (entry as SettingEntry<Int>).current
        if (value !in def.min..def.max) {
          val label = context.getString(def.labelRes)
          "$label must be between ${def.min} and ${def.max}"
        } else null
      }
      is SettingDef.TextInput -> {
        def.validate?.invoke((entry as SettingEntry<String>).current)
      }
      else -> null
    }
  }

  // ─── Utility ─────────────────────────────────────────────────────────────

  companion object {
    private fun fmtToggle(enabled: Boolean) = if (enabled) "enabled" else "disabled"
  }
}
