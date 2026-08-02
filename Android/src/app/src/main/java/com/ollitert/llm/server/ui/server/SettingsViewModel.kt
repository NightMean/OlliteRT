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

package com.ollitert.llm.server.ui.server

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.data.ClientIpAccessPolicy
import com.ollitert.llm.server.data.ClientIpPolicyCompileResult
import com.ollitert.llm.server.data.ClientIpPolicyConfig
import com.ollitert.llm.server.data.ClientIpPolicyMode
import com.ollitert.llm.server.data.DataStoreRepository
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_CACHE_PREFIX
import com.ollitert.llm.server.data.MODEL_ALLOWLIST_OFFICIAL_FILENAME
import com.ollitert.llm.server.data.ServerBindConfig
import com.ollitert.llm.server.data.ServerBindMode
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.data.db.RequestLogPersistence
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.ServerService
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.service.ServerMetrics
import com.ollitert.llm.server.ui.common.matchesSearchQuery
import com.ollitert.llm.server.ui.server.settings.CardId
import com.ollitert.llm.server.ui.server.settings.SettingDef
import com.ollitert.llm.server.ui.server.settings.SettingEntry
import com.ollitert.llm.server.ui.server.settings.allCardDefs
import com.ollitert.llm.server.ui.server.settings.allSettingDefs
import com.ollitert.llm.server.ui.server.settings.settingDefsByKey
import com.ollitert.llm.server.worker.UpdateCheckWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen. Owns all settings state, validation,
 * change detection, search filtering, and save/reset logic.
 *
 * Uses [SettingEntry] instances for each persisted setting, enabling automatic
 * change detection, save, revert, and reset via iteration over [entryByKey].
 * The UI reads/writes entries directly (e.g. `entry.current`, `entry.update()`).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val persistence: RequestLogPersistence,
  private val dataStoreRepository: DataStoreRepository,
) : ViewModel() {

  var repoCount: Int by mutableIntStateOf(0)
    private set
  var enabledRepoCount: Int by mutableIntStateOf(0)
    private set

  init {
    refreshRepositoryCounts()
  }

  fun refreshRepositoryCounts() {
    viewModelScope.launch(Dispatchers.IO) {
      val repos = dataStoreRepository.readRepositories()
      repoCount = repos.size
      enabledRepoCount = repos.count { it.enabled }
    }
  }

  // ─── Setting Entries (auto-generated from SettingDef metadata) ────────────
  // Each SettingEntry tracks saved + current value for one persisted setting.
  // Entries are created from the read lambda on each non-Custom SettingDef.

  // Bearer has derived state (enabled = token non-blank) — not a SettingDef
  val bearerEnabledEntry = SettingEntry(ServerPrefs.getBearerToken(context).isNotBlank())

  private val entryByKey: Map<String, SettingEntry<*>> = buildMap {
    for (def in allSettingDefs) {
      when (def) {
        is SettingDef.Toggle -> put(def.key, SettingEntry(def.read(context)))
        is SettingDef.TextInput -> put(def.key, SettingEntry(def.read(context)))
        is SettingDef.NumericInput -> put(def.key, SettingEntry(def.read(context)))
        is SettingDef.NumericWithUnit -> put(def.key, SettingEntry(def.read(context)))
        is SettingDef.NumericPlain -> put(def.key, SettingEntry(def.read(context)))
        is SettingDef.Dropdown -> put(def.key, SettingEntry(def.read(context)))
        is SettingDef.Custom -> {} // no persistence
      }
    }
    // Bearer token is a Custom def but has a manually-managed entry for UI state
    put("bearer_token", SettingEntry(ServerPrefs.getBearerToken(context)))
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> entry(key: String): SettingEntry<T> = entryByKey.getValue(key) as SettingEntry<T>

  // ─── Typed Accessors (preserve call-site readability) ──────────────────
  // Keys must match SettingDef.key values in SettingsDefinitions.kt.
  val serverBindModeEntry get() = entry<String?>("server_bind_mode")
  val customBindAddressEntry get() = entry<String>("custom_bind_address")
  val portEntry get() = entry<Int>("host_port")
  val clientIpPolicyModeEntry get() = entry<String?>("client_ip_policy_mode")
  val clientIpRulesEntry get() = entry<String>("client_ip_rules")
  val bearerTokenEntry get() = entry<String>("bearer_token")
  val hfTokenEntry get() = entry<String>("hf_token")
  val defaultModelEntry get() = entry<String?>("default_model")
  val corsAllowedOriginsEntry get() = entry<String>("cors_origins")
  val keepScreenOnEntry get() = entry<Boolean>("keep_screen_awake")
  val autoExpandLogsEntry get() = entry<Boolean>("auto_expand_logs")
  val streamLogsPreviewEntry get() = entry<Boolean>("stream_response_preview")
  val compactImageDataEntry get() = entry<Boolean>("compact_image_data")
  val hideHealthLogsEntry get() = entry<Boolean>("hide_health_logs")
  val clearLogsOnStopEntry get() = entry<Boolean>("clear_logs_on_stop")
  val confirmClearLogsEntry get() = entry<Boolean>("confirm_clear_logs")
  val keepPartialResponseEntry get() = entry<Boolean>("keep_partial_response")
  val autoStartOnBootEntry get() = entry<Boolean>("start_on_boot")
  val keepAliveEnabledEntry get() = entry<Boolean>("keep_alive")
  val keepAliveMinutesEntry get() = entry<Long>("keep_alive_timeout")
  val updateCheckEnabledEntry get() = entry<Boolean>("auto_update_check")
  val updateCheckIntervalHoursEntry get() = entry<Long>("check_frequency")
  val crossChannelNotifyEntry get() = entry<Boolean>("cross_channel_notify")
  val warmupEnabledEntry get() = entry<Boolean>("warmup_message")
  val eagerVisionInitEntry get() = entry<Boolean>("pre_init_vision")
  val customPromptsEnabledEntry get() = entry<Boolean>("custom_prompts")
  val autoTruncateHistoryEntry get() = entry<Boolean>("truncate_history")
  val autoTrimPromptsEntry get() = entry<Boolean>("trim_prompt")
  val ignoreClientSamplerParamsEntry get() = entry<Boolean>("ignore_client_params")
  val verboseDebugEnabledEntry get() = entry<Boolean>("verbose_debug")
  val notifShowRequestCountEntry get() = entry<Boolean>("notif_request_count")
  val sttTranscriptionPromptEntry get() = entry<Boolean>("stt_transcription_prompt")
  val sttTranscriptionPromptTextEntry get() = entry<String>("stt_transcription_prompt_text")
  val schemaInjectionEntry get() = entry<Boolean>("schema_injection_tool_calling")
  val logPersistenceEnabledEntry get() = entry<Boolean>("log_persistence_enabled")
  val logMaxEntriesEntry get() = entry<Int>("log_max_entries")
  val logAutoDeleteMinutesEntry get() = entry<Long>("log_auto_delete")
  val showRequestTypesEntry get() = entry<Boolean>("show_request_types")
  val showAdvancedMetricsEntry get() = entry<Boolean>("show_advanced_metrics")

  // Advanced Timeouts
  val timeoutChatCompletionsEntry get() = entry<Long>("timeout_chat_completions")
  val timeoutResponsesEntry get() = entry<Long>("timeout_responses")
  val timeoutStreamingEntry get() = entry<Long>("timeout_streaming")
  val timeoutBlockingEntry get() = entry<Long>("timeout_blocking")
  val timeoutWarmupEntry get() = entry<Long>("timeout_warmup")
  val timeoutKeepAliveRecheckEntry get() = entry<Long>("timeout_keep_alive_recheck")
  val timeoutCleanupAwaitEntry get() = entry<Long>("timeout_cleanup_await")

  // ─── UI State (non-persisted) ────────────────────────────────────────────

  var portText by mutableStateOf(portEntry.saved.toString())
  var hfTokenVisible by mutableStateOf(false)

  /** Validation errors keyed by setting key. Compose-observable — reads trigger recomposition. */
  val validationErrors = mutableStateMapOf<String, String>()
  fun hasError(key: String): Boolean = key in validationErrors
  fun clearError(key: String) { validationErrors.remove(key) }

  // ─── Advanced Settings Collapse ──────────────────────────────────────────
  var advancedSettingsExpanded by mutableStateOf(false)
  val shouldAutoExpandAdvanced: Boolean get() =
    searchQuery.isNotBlank() && cardVisible(CardId.ADVANCED_SETTINGS)

  // ─── Dialog State ────────────────────────────────────────────────────────
  var showRestartDialog by mutableStateOf(false)
  var showClearPersistedDialog by mutableStateOf(false)
  var showTrimLogsDialog by mutableStateOf(false)
  var showResetDialog by mutableStateOf(false)
  var showDiscardDialog by mutableStateOf(false)
  var showDonateDialog by mutableStateOf(false)
  var showTrimPromptWarning by mutableStateOf(false)

  // ─── Search ──────────────────────────────────────────────────────────────
  var searchQuery by mutableStateOf("")

  /** Cached searchable text per setting key: label + description + card title (from strings.xml). */
  private val searchableTextByKey: Map<String, String> = buildMap {
    for (card in allCardDefs) {
      val cardTitle = context.getString(card.titleRes)
      for (def in card.settings) {
        val label = context.getString(def.labelRes)
        val desc = context.getString(def.descriptionRes)
        put(def.key, "$label $desc $cardTitle")
      }
    }
  }

  /** Returns true if an individual setting matches the current search query. */
  fun settingVisible(settingKey: String): Boolean {
    if (searchQuery.isBlank()) return true
    val searchable = searchableTextByKey[settingKey] ?: return true
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
  @Suppress("UNCHECKED_CAST")
  fun getToggleEntry(key: String): SettingEntry<Boolean>? {
    val def = settingDefsByKey[key]
    if (def !is SettingDef.Toggle) return null
    return entryByKey[key] as? SettingEntry<Boolean>
  }

  /** Whether a setting is interactive (not disabled by a parent dependency).
   *  Keys must match SettingDef.key values. Update settingAlpha() in tandem. */
  fun isSettingEnabled(key: String): Boolean = when (key) {
    "custom_bind_address" -> ServerBindMode.fromPreference(serverBindModeEntry.current) == ServerBindMode.CUSTOM
    "client_ip_rules" -> ClientIpPolicyMode.fromPreference(clientIpPolicyModeEntry.current) != ClientIpPolicyMode.ALLOW_ALL
    "start_on_boot" -> defaultModelEntry.current != null
    "keep_alive_timeout" -> keepAliveEnabledEntry.current
    "check_frequency" -> updateCheckEnabledEntry.current
    "auto_update_check" -> true
    "log_max_entries", "log_auto_delete", "clear_all_logs" -> logPersistenceEnabledEntry.current
    else -> true
  }

  /** Alpha for settings that dim when their parent is disabled.
   *  Must stay in sync with isSettingEnabled() above. */
  fun settingAlpha(key: String): Float = when (key) {
    "custom_bind_address" -> if (isSettingEnabled(key)) 1f else 0.4f
    "client_ip_rules" -> if (isSettingEnabled(key)) 1f else 0.4f
    "start_on_boot" -> if (defaultModelEntry.current != null) 1f else 0.4f
    "keep_alive_timeout" -> if (keepAliveEnabledEntry.current) 1f else 0.4f
    "log_max_entries", "log_auto_delete", "clear_all_logs" -> if (logPersistenceEnabledEntry.current) 1f else 0.4f
    else -> 1f
  }

  fun settingDescriptionOverride(key: String): Int? = null

  // ─── Change Detection ────────────────────────────────────────────────────

  private val effectiveBearerToken: String
    get() = if (bearerEnabledEntry.current) bearerTokenEntry.current else ""

  val hasUnsavedChanges: Boolean get() {
    // Port is stored as Int but edited as String — compare via parsed int
    val portChanged = portText != portEntry.saved.toString()
    // Bearer token uses effective value (blank when disabled)
    val bearerChanged = effectiveBearerToken != bearerTokenEntry.saved
    // All other entries use SettingEntry change detection (skip port & bearer token)
    val entryChanged = entryByKey.entries.any { (key, entry) ->
      key != "host_port" && key != "bearer_token" && entry.isChanged
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

    val port = portText.toIntOrNull() ?: return SaveResult.ValidationError(context.getString(R.string.validation_invalid_port))
    val bindMode = ServerBindMode.fromPreference(serverBindModeEntry.current)
    val bindConfig = ServerBindConfig(bindMode, customBindAddressEntry.current.trim())
    val policyMode = ClientIpPolicyMode.fromPreference(clientIpPolicyModeEntry.current)
    val policyConfig = ClientIpPolicyConfig(policyMode, clientIpRulesEntry.current)
    val compiledPolicy = when (val result = ClientIpAccessPolicy.compile(policyConfig)) {
      is ClientIpPolicyCompileResult.Success -> result
      else -> return SaveResult.ValidationError(clientIpPolicyValidationError(result))
    }
    customBindAddressEntry.update(bindConfig.customAddress)
    clientIpRulesEntry.update(compiledPolicy.normalizedRulesText)

    val isPortChanged = port != portEntry.saved
    val savedBindMode = ServerBindMode.fromPreference(serverBindModeEntry.saved)
    val isBindChanged = serverBindModeEntry.isChanged ||
      (customBindAddressEntry.isChanged &&
        (bindMode == ServerBindMode.CUSTOM || savedBindMode == ServerBindMode.CUSTOM))
    val isEagerVisionChanged = eagerVisionInitEntry.isChanged
    val isTimeoutChanged = timeoutChatCompletionsEntry.isChanged ||
      timeoutResponsesEntry.isChanged || timeoutStreamingEntry.isChanged ||
      timeoutBlockingEntry.isChanged || timeoutWarmupEntry.isChanged ||
      timeoutKeepAliveRecheckEntry.isChanged || timeoutCleanupAwaitEntry.isChanged
    val needsRestart = isPortChanged || isBindChanged || isEagerVisionChanged || isTimeoutChanged
    val isServerActive = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING

    // Sync portEntry.current from portText before persisting (port is edited as String)
    portEntry.update(port)

    // ── Persist to SharedPreferences ──
    val wasLogPersistenceEnabled = ServerPrefs.isLogPersistenceEnabled(context)
    val oldAutoDeleteMinutes = ServerPrefs.getLogAutoDeleteMinutes(context)
    // Bearer token uses effective value (blank when toggle is off)
    ServerPrefs.setBearerToken(context, effectiveBearerToken)
    // Persist related network fields atomically so readers never observe a mixed configuration.
    ServerPrefs.setServerBindConfig(context, bindConfig)
    ServerPrefs.setClientIpPolicyConfig(
      context,
      policyConfig.copy(rulesText = compiledPolicy.normalizedRulesText),
    )
    // All non-Custom settings: persist via the definition's write lambda
    for (def in allSettingDefs) {
      if (def.key in NETWORK_CONFIG_SETTING_KEYS || def.key == "bearer_token") continue // handled above
      val entry = entryByKey[def.key] ?: continue
      persistViaDefinition(def, entry)
    }

    // ── Side effects ──
    ServerService.updateClientIpAccessPolicy(compiledPolicy.policy)
    if ((keepAliveEnabledEntry.isChanged || keepAliveMinutesEntry.isChanged) && isServerActive) {
      ServerService.resetKeepAliveTimer(context)
    }
    if (updateCheckEnabledEntry.isChanged || updateCheckIntervalHoursEntry.isChanged) {
      if (updateCheckEnabledEntry.current) UpdateCheckWorker.scheduleUpdateCheck(context)
      else UpdateCheckWorker.cancelUpdateCheck(context)
    }
    if (!crossChannelNotifyEntry.current) {
      val cached = ServerMetrics.availableUpdateVersion.value
      if (cached != null && !UpdateCheckWorker.isOwnChannelTag(cached)) {
        ServerMetrics.setAvailableUpdate(null, null)
        ServerPrefs.setCachedUpdateInfo(context, null, null, null)
      }
    }

    // ── Log changes ──
    logSettingsChanges(port)

    // Write a full settings snapshot to logcat when verbose debug is turned on,
    // so exported debug logs contain the active configuration for diagnosis.
    if (verboseDebugEnabledEntry.current && !verboseDebugEnabledEntry.saved) {
      ServerPrefs.dumpToLogcat(context)
    }

    // ── Sync persistence layer ──
    if (logPersistenceEnabledEntry.current && !logPersistenceEnabledEntry.saved) {
      persistence.persistCurrentEntries()
    }
    persistence.updateMaxEntries()
    val pruningConfigChanged = ServerPrefs.isLogPersistenceEnabled(context) != wasLogPersistenceEnabled
        || (ServerPrefs.isLogPersistenceEnabled(context)
            && ServerPrefs.getLogAutoDeleteMinutes(context) != oldAutoDeleteMinutes)
    if (pruningConfigChanged) {
      persistence.schedulePruning()
    }

    // ── Advance saved baselines ──
    portEntry.apply()
    portText = port.toString()
    bearerEnabledEntry.apply()
    bearerTokenEntry.update(if (bearerEnabledEntry.current) bearerTokenEntry.current else "")
    bearerTokenEntry.apply()
    for ((key, entry) in entryByKey) {
      if (key != "host_port" && key != "bearer_token") entry.apply()
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

  /** Persists a single setting via the definition's write lambda. */
  @Suppress("UNCHECKED_CAST")
  private fun persistViaDefinition(def: SettingDef, entry: SettingEntry<*>) {
    when (def) {
      is SettingDef.Toggle -> def.write(context, (entry as SettingEntry<Boolean>).current)
      is SettingDef.TextInput -> def.write(context, (entry as SettingEntry<String>).current)
      is SettingDef.NumericInput -> def.write(context, (entry as SettingEntry<Int>).current)
      is SettingDef.NumericWithUnit -> def.write(context, (entry as SettingEntry<Long>).current)
      is SettingDef.NumericPlain -> def.write(context, (entry as SettingEntry<Int>).current)
      is SettingDef.Dropdown -> def.write(context, (entry as SettingEntry<String?>).current)
      is SettingDef.Custom -> {}
    }
  }

  /**
   * Collects all settings changes into one grouped log entry.
   *
   * Log event text is intentionally English-only — these are diagnostic messages for the Logs tab,
   * not user-facing UI strings. They must be stable and grep-able across locales.
   */
  private fun logSettingsChanges(newPort: Int) {
    val changes = mutableListOf<String>()

    if (serverBindModeEntry.isChanged) {
      changes.add("Listen Mode: ${serverBindModeEntry.saved} -> ${serverBindModeEntry.current}")
    }
    if (customBindAddressEntry.isChanged) changes.add("Custom Bind Address: changed")

    // Port: compared via parsed int (portText → int)
    if (newPort != portEntry.saved) changes.add("Port: ${portEntry.saved} → $newPort")

    if (clientIpPolicyModeEntry.isChanged) {
      changes.add("Client IP Policy: ${clientIpPolicyModeEntry.saved} -> ${clientIpPolicyModeEntry.current}")
    }
    if (clientIpRulesEntry.isChanged) {
      changes.add(
        "Client IP Rules: ${countIpRules(clientIpRulesEntry.saved)} -> " +
          "${countIpRules(clientIpRulesEntry.current)} rules",
      )
    }

    // Bearer token: derived state (enabled = token non-blank)
    val bearerWasEnabled = bearerTokenEntry.saved.isNotBlank()
    val bearerIsEnabled = effectiveBearerToken.isNotBlank()
    if (bearerWasEnabled != bearerIsEnabled)
      changes.add("Bearer Auth: ${fmtToggle(bearerWasEnabled)} → ${fmtToggle(bearerIsEnabled)}")

    // All other settings: iterate definitions and format changed entries
    for (def in allSettingDefs) {
      if (def.key in NETWORK_CONFIG_SETTING_KEYS || def.key == "host_port" || def.key == "bearer_token") continue
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
    val e = entry as SettingEntry<Long>
    return "$label: ${fmt(e.saved)} → ${fmt(e.current)}"
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
    ServerPrefs.resetToDefaults(context)

    // Reset each entry to its definition's resetDefault (not fresh-install default)
    for (def in allSettingDefs) {
      val entry = entryByKey[def.key] ?: continue
      when (def) {
        is SettingDef.Toggle -> (entry as SettingEntry<Boolean>).reset(def.resetDefault)
        is SettingDef.TextInput -> (entry as SettingEntry<String>).reset(def.resetDefault)
        is SettingDef.NumericInput -> (entry as SettingEntry<Int>).reset(def.default)
        is SettingDef.NumericWithUnit -> (entry as SettingEntry<Long>).reset(def.defaultValue)
        is SettingDef.NumericPlain -> (entry as SettingEntry<Int>).reset(def.default)
        is SettingDef.Dropdown -> (entry as SettingEntry<String?>).reset(def.resetDefault)
        is SettingDef.Custom -> {}
      }
    }

    // Reset bearer state (derived, not a SettingDef)
    bearerEnabledEntry.reset(false)
    bearerTokenEntry.reset("")

    // Reset UI state
    portText = portEntry.saved.toString()
    validationErrors.clear()

    // Side effects
    persistence.updateMaxEntries()
    persistence.schedulePruning()
    persistence.clearPersistedLogs()
    UpdateCheckWorker.scheduleUpdateCheck(context)
    ServerService.updateClientIpAccessPolicy(ClientIpAccessPolicy.ALLOW_ALL)

    viewModelScope.launch(Dispatchers.IO) {
      dataStoreRepository.resetRepositories()
      val dir = context.getExternalFilesDir(null)
      if (dir != null) {
        // Delete only custom repo caches; keep the built-in official allowlist which ships with the APK.
        dir.listFiles { _, name ->
          name.startsWith(MODEL_ALLOWLIST_CACHE_PREFIX) && name.endsWith(".json")
            && name != MODEL_ALLOWLIST_OFFICIAL_FILENAME
        }?.forEach { it.delete() }
      }
      refreshRepositoryCounts()
    }
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
          if (portText.isBlank()) return context.getString(R.string.validation_port_required)
          val port = portText.toIntOrNull()
          if (port == null || port !in def.min..def.max)
            return context.getString(R.string.validation_port_range, def.min, def.max)
          null
        } else {
          val value = (entry as SettingEntry<Int>).current
          if (value !in def.min..def.max) {
            val label = context.getString(def.labelRes)
            context.getString(R.string.validation_numeric_range, label, def.min, def.max)
          } else null
        }
      }
      is SettingDef.NumericWithUnit -> {
        @Suppress("UNCHECKED_CAST")
        val value = (entry as SettingEntry<Long>).current
        if (value !in def.min..def.max) {
          formatNumericWithUnitRangeError(def, value, context)
        } else null
      }
      is SettingDef.NumericPlain -> {
        val value = (entry as SettingEntry<Int>).current
        if (value !in def.min..def.max) {
          val label = context.getString(def.labelRes)
          context.getString(R.string.validation_numeric_range, label, def.min, def.max)
        } else null
      }
      is SettingDef.TextInput -> {
        if (def.key == "client_ip_rules") {
          val config = ClientIpPolicyConfig(
            mode = ClientIpPolicyMode.fromPreference(clientIpPolicyModeEntry.current),
            rulesText = (entry as SettingEntry<String>).current,
          )
          val result = ClientIpAccessPolicy.compile(config)
          if (result is ClientIpPolicyCompileResult.Success) null
          else clientIpPolicyValidationError(result)
        } else {
          def.validate?.invoke((entry as SettingEntry<String>).current, context)
        }
      }
      else -> null
    }
  }

  // ─── Utility ─────────────────────────────────────────────────────────────

  companion object {
    private val NETWORK_CONFIG_SETTING_KEYS = setOf(
      "server_bind_mode",
      "custom_bind_address",
      "client_ip_policy_mode",
      "client_ip_rules",
    )

    private fun fmtToggle(enabled: Boolean) = if (enabled) "enabled" else "disabled"

    private fun countIpRules(rulesText: String): Int = rulesText
      .split(Regex("[,\\r\\n]+"))
      .count { it.isNotBlank() }

    internal fun formatNumericWithUnitRangeError(
      def: SettingDef.NumericWithUnit,
      baseValue: Long,
      context: Context,
    ): String {
      val label = context.getString(def.labelRes)
      val (_, displayUnit) = def.fromBaseUnit(baseValue)
      val multiplier = def.toBaseUnit(1L, displayUnit)
      val displayMin = (def.min + multiplier - 1) / multiplier
      val displayMax = def.max / multiplier
      return context.getString(R.string.validation_numeric_range_with_unit, label, displayMin, displayMax, displayUnit)
    }
  }

  private fun clientIpPolicyValidationError(result: ClientIpPolicyCompileResult): String = when (result) {
    ClientIpPolicyCompileResult.EmptyRules -> context.getString(R.string.validation_client_ip_rules_required)
    is ClientIpPolicyCompileResult.InvalidRule -> context.getString(
      R.string.validation_client_ip_rule_invalid,
      result.position,
      result.input,
    )
    is ClientIpPolicyCompileResult.Success -> error("A valid client IP policy has no validation error")
  }
}
