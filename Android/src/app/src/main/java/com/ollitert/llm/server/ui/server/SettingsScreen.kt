package com.ollitert.llm.server.ui.server

import com.ollitert.llm.server.BuildConfig
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import android.view.WindowManager
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

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
  var savedCorsAllowedOrigins by remember { mutableStateOf(LlmHttpPrefs.getCorsAllowedOrigins(context)) }
  var savedLogPersistenceEnabled by remember { mutableStateOf(LlmHttpPrefs.isLogPersistenceEnabled(context)) }
  var savedLogMaxEntries by remember { mutableStateOf(LlmHttpPrefs.getLogMaxEntries(context)) }
  var savedLogAutoDeleteMinutes by remember { mutableStateOf(LlmHttpPrefs.getLogAutoDeleteMinutes(context)) }
  var savedIgnoreClientSamplerParams by remember { mutableStateOf(LlmHttpPrefs.isIgnoreClientSamplerParams(context)) }

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
  var corsAllowedOrigins by remember { mutableStateOf(savedCorsAllowedOrigins) }
  var corsError by remember { mutableStateOf(false) }
  var logPersistenceEnabled by remember { mutableStateOf(savedLogPersistenceEnabled) }
  var logMaxEntries by remember { mutableStateOf(savedLogMaxEntries) }
  var logAutoDeleteMinutes by remember { mutableStateOf(savedLogAutoDeleteMinutes) }
  var ignoreClientSamplerParams by remember { mutableStateOf(savedIgnoreClientSamplerParams) }
  var showClearPersistedDialog by remember { mutableStateOf(false) }
  var showTrimLogsDialog by remember { mutableStateOf(false) }
  var showResetDialog by remember { mutableStateOf(false) }
  var verboseDebugEnabled by remember { mutableStateOf(LlmHttpPrefs.isVerboseDebugEnabled(context)) }

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
    corsAllowedOrigins != savedCorsAllowedOrigins ||
    logPersistenceEnabled != savedLogPersistenceEnabled ||
    logMaxEntries != savedLogMaxEntries ||
    logAutoDeleteMinutes != savedLogAutoDeleteMinutes ||
    ignoreClientSamplerParams != savedIgnoreClientSamplerParams

  // Discard confirmation dialog
  var showDiscardDialog by remember { mutableStateOf(false) }

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
    } else {
      corsError = false
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

        LlmHttpPrefs.setClearLogsOnStop(context, clearLogsOnStop)
        LlmHttpPrefs.setConfirmClearLogs(context, confirmClearLogs)
        LlmHttpPrefs.setShowRequestTypes(context, showRequestTypes)
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
        savedCorsAllowedOrigins = corsAllowedOrigins
        savedLogPersistenceEnabled = logPersistenceEnabled
        savedLogMaxEntries = logMaxEntries
        savedLogAutoDeleteMinutes = logAutoDeleteMinutes
        savedIgnoreClientSamplerParams = ignoreClientSamplerParams

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
  ) {
  Column(
    modifier = Modifier
      .fillMaxSize()
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

    // General card
    SettingsCard(
      icon = Icons.Outlined.PhoneAndroid,
      title = "General",
    ) {
      // Keep screen awake toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Keep Screen Awake",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      // Auto-expand logs toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Auto-Expand Logs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      // Stream response preview toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Stream Response Preview",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      // Clear logs on stop toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Clear Logs on Stop",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      // Confirm before clearing logs
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Confirm Before Clearing Logs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      // Keep partial response toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Keep Partial Response",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

    }

    // Hugging Face Token card
    SettingsCard(
      icon = Icons.Outlined.Key,
      title = "Hugging Face Token",
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

    // Server Config card
    SettingsCard(
      icon = Icons.Outlined.Tune,
      title = "Server Configuration",
    ) {
      Text(
        text = "Host Port (1024–65535)",
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // Bearer token toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Require Bearer Token",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // CORS allowed origins
      Text(
        text = "CORS Allowed Origins",
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

      // Token display + actions (only when bearer is enabled)
      if (bearerEnabled) {
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
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Bearer Token", bearerToken))
              Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
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

    // Auto-Launch & Behavior card
    SettingsCard(
      icon = Icons.Outlined.PlayArrow,
      title = "Auto-Launch & Behavior",
    ) {
      // Default model picker
      Text(
        text = "Default Model",
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // Auto-start on boot toggle — entire row dims when no default model is selected
      val autoStartAlpha = if (defaultModelName != null) 1f else 0.4f
      Row(
        modifier = Modifier.fillMaxWidth().alpha(autoStartAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Start on Boot",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

    }

    // Metrics card
    SettingsCard(
      icon = Icons.Outlined.BarChart,
      title = "Metrics",
    ) {
      // Show Request Types on Status screen
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Show Request Types",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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
    }

    // Log Persistence card
    SettingsCard(
      icon = Icons.Outlined.Storage,
      title = "Log Persistence",
    ) {
      // Master toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Persist Logs to Database",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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
          text = "Maximum Log Entries",
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
          text = "Auto-Delete After",
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
                .width(120.dp)
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

    // Advanced Settings card
    SettingsCard(
      icon = Icons.Outlined.Science,
      title = "Advanced",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Warmup Message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // Eager vision initialization toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Pre-initialize Vision",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // Custom system prompt & chat template toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Custom System Prompt & Chat Template",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // Truncate conversation history toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Truncate Conversation History",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // Compact tool schemas toggle (especially useful for Home Assistant)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Compact Tool Schemas",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // Trim prompt toggle (last resort)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Trim Prompt",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

      // Ignore client sampler parameters toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Ignore Client Sampler Parameters",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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
    }

    // Developer card — verbose debug toggle (immediate-apply, no save/cancel)
    SettingsCard(
      icon = Icons.Outlined.Code,
      title = "Developer",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Verbose Debug Mode",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
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
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
          },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
    }

    // Reset to Defaults
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

    // Footer
    Spacer(modifier = Modifier.height(8.dp))
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
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = OlliteRTPrimary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Spacer(modifier = Modifier.height(12.dp))
    content()
  }
}
