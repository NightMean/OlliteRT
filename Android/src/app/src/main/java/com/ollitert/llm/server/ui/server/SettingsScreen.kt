package com.ollitert.llm.server.ui.server

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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Science
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
fun SettingsScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  serverStatus: ServerStatus = ServerStatus.STOPPED,
  onRestartServer: () -> Unit = {},
  downloadedModelNames: List<String> = emptyList(),
  onSetTopBarTrailingContent: ((@Composable () -> Unit)?) -> Unit = {},
) {
  val context = LocalContext.current

  // Saved (persisted) values — used for change detection; updated after successful save
  var savedPort by remember { mutableStateOf(LlmHttpPrefs.getPort(context)) }
  var savedBearerToken by remember { mutableStateOf(LlmHttpPrefs.getBearerToken(context)) }
  var savedHfToken by remember { mutableStateOf(LlmHttpPrefs.getHfToken(context)) }
  var savedDefaultModelName by remember { mutableStateOf(LlmHttpPrefs.getDefaultModelName(context)) }
  var savedAutoStartOnBoot by remember { mutableStateOf(LlmHttpPrefs.isAutoStartOnBoot(context)) }
  var savedKeepScreenOn by remember { mutableStateOf(LlmHttpPrefs.isKeepScreenOn(context)) }
  var savedAutoExpandLogs by remember { mutableStateOf(LlmHttpPrefs.isAutoExpandLogs(context)) }
  var savedNotifShowRequestCount by remember { mutableStateOf(LlmHttpPrefs.isNotifShowRequestCount(context)) }
  var savedWarmupEnabled by remember { mutableStateOf(LlmHttpPrefs.isWarmupEnabled(context)) }
  var savedStreamLogsPreview by remember { mutableStateOf(LlmHttpPrefs.isStreamLogsPreview(context)) }
  var savedKeepPartialResponse by remember { mutableStateOf(LlmHttpPrefs.isKeepPartialResponse(context)) }
  var savedEagerVisionInit by remember { mutableStateOf(LlmHttpPrefs.isEagerVisionInit(context)) }
  var savedCustomPromptsEnabled by remember { mutableStateOf(LlmHttpPrefs.isCustomPromptsEnabled(context)) }

  // Current (editable) state
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
  var notifShowRequestCount by remember { mutableStateOf(savedNotifShowRequestCount) }
  var warmupEnabled by remember { mutableStateOf(savedWarmupEnabled) }
  var streamLogsPreview by remember { mutableStateOf(savedStreamLogsPreview) }
  var keepPartialResponse by remember { mutableStateOf(savedKeepPartialResponse) }
  var eagerVisionInit by remember { mutableStateOf(savedEagerVisionInit) }
  var customPromptsEnabled by remember { mutableStateOf(savedCustomPromptsEnabled) }

  // Unsaved changes detection — compare current vs persisted
  val effectiveBearerToken = if (bearerEnabled) bearerToken else ""
  val hasUnsavedChanges = portText != savedPort.toString() ||
    effectiveBearerToken != savedBearerToken ||
    hfToken != savedHfToken ||
    defaultModelName != savedDefaultModelName ||
    autoStartOnBoot != savedAutoStartOnBoot ||
    keepScreenOn != savedKeepScreenOn ||
    autoExpandLogs != savedAutoExpandLogs ||
    notifShowRequestCount != savedNotifShowRequestCount ||
    warmupEnabled != savedWarmupEnabled ||
    streamLogsPreview != savedStreamLogsPreview ||
    keepPartialResponse != savedKeepPartialResponse ||
    eagerVisionInit != savedEagerVisionInit ||
    customPromptsEnabled != savedCustomPromptsEnabled

  // Discard confirmation dialog
  var showDiscardDialog by remember { mutableStateOf(false) }

  // Intercept back navigation when there are unsaved changes
  BackHandler(enabled = hasUnsavedChanges) {
    showDiscardDialog = true
  }

  // Save action — shared between top bar button and internal logic
  val saveSettings: () -> Unit = {
    if (portText.isBlank()) {
      portError = true
      Toast.makeText(context, "A port number is required", Toast.LENGTH_SHORT).show()
    } else {
      val port = portText.toIntOrNull()
      if (port == null || port !in 1024..65535) {
        portError = true
        Toast.makeText(context, "Port must be between 1024 and 65535", Toast.LENGTH_SHORT).show()
      } else {
        val isPortChanged = port != savedPort
        val isEagerVisionChanged = eagerVisionInit != savedEagerVisionInit
        val needsRestart = isPortChanged || isEagerVisionChanged
        val isServerRunning = serverStatus == ServerStatus.RUNNING
        val isServerLoading = serverStatus == ServerStatus.LOADING

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
        LlmHttpPrefs.setNotifShowRequestCount(context, notifShowRequestCount)
        LlmHttpPrefs.setWarmupEnabled(context, warmupEnabled)
        LlmHttpPrefs.setStreamLogsPreview(context, streamLogsPreview)
        LlmHttpPrefs.setKeepPartialResponse(context, keepPartialResponse)
        LlmHttpPrefs.setEagerVisionInit(context, eagerVisionInit)
        LlmHttpPrefs.setCustomPromptsEnabled(context, customPromptsEnabled)

        // Apply keep-screen-on immediately
        val window = (context as? android.app.Activity)?.window
        if (keepScreenOn) {
          window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
          window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Update saved state so change detection resets
        savedPort = port
        savedBearerToken = if (bearerEnabled) bearerToken else ""
        savedHfToken = hfToken
        savedDefaultModelName = defaultModelName
        savedAutoStartOnBoot = autoStartOnBoot
        savedKeepScreenOn = keepScreenOn
        savedAutoExpandLogs = autoExpandLogs
        savedNotifShowRequestCount = notifShowRequestCount
        savedWarmupEnabled = warmupEnabled
        savedStreamLogsPreview = streamLogsPreview
        savedKeepPartialResponse = keepPartialResponse
        savedEagerVisionInit = eagerVisionInit
        savedCustomPromptsEnabled = customPromptsEnabled

        if (needsRestart && isServerRunning) {
          showRestartDialog = true
        } else if (needsRestart && isServerLoading) {
          Toast.makeText(context, "Settings saved. The server is still starting — restart it manually for changes to take effect.", Toast.LENGTH_LONG).show()
        } else {
          Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  // Inject save button into the top bar
  val currentSaveSettings by rememberUpdatedState(saveSettings)
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

    }

    // General card
    SettingsCard(
      icon = Icons.Outlined.PhoneAndroid,
      title = "General",
    ) {
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
    }

    // Notification card
    SettingsCard(
      icon = Icons.Outlined.Notifications,
      title = "Notification",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Show Request Count",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "Display live request count in the notification. Updates on every request.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = notifShowRequestCount,
          onCheckedChange = { notifShowRequestCount = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
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

      // Auto-start on boot toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
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
            text = "Launch server automatically when device starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = autoStartOnBoot,
          onCheckedChange = { enabled ->
            if (enabled && defaultModelName == null) {
              Toast.makeText(context, "Select a default model first", Toast.LENGTH_SHORT).show()
            } else {
              autoStartOnBoot = enabled
            }
          },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(16.dp))

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
    }

    // API Authentication card
    SettingsCard(
      icon = Icons.Outlined.Shield,
      title = "API Authentication",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Require Bearer Token",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
          checked = bearerEnabled,
          onCheckedChange = { enabled ->
            bearerEnabled = enabled
            if (enabled && bearerToken.isBlank()) {
              bearerToken = LlmHttpPrefs.ensureBearerToken(context)
            } else if (!enabled) {
              bearerToken = ""
            }
          },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      if (bearerEnabled && bearerToken.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = bearerToken,
          onValueChange = {},
          readOnly = true,
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OlliteRTPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          ),
          trailingIcon = {
            Row {
              @OptIn(ExperimentalMaterial3Api::class)
              TooltipBox(
                positionProvider = @Suppress("DEPRECATION") TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Copy token") } },
                state = rememberTooltipState(),
              ) {
                IconButton(onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  clipboard.setPrimaryClip(ClipData.newPlainText("Bearer Token", bearerToken))
                  Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
                }) {
                  Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy token",
                    tint = OlliteRTPrimary,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
              @OptIn(ExperimentalMaterial3Api::class)
              TooltipBox(
                positionProvider = @Suppress("DEPRECATION") TooltipDefaults.rememberTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text("Regenerate token") } },
                state = rememberTooltipState(),
              ) {
                IconButton(onClick = {
                  bearerToken = LlmHttpPrefs.ensureBearerToken(context).let {
                    // Force regenerate by clearing first
                    LlmHttpPrefs.setBearerToken(context, "")
                    LlmHttpPrefs.ensureBearerToken(context)
                  }
                }) {
                  Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "Regenerate token",
                    tint = OlliteRTPrimary,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Clients must send Authorization: Bearer <token> header.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Hugging Face Token card
    SettingsCard(
      icon = Icons.Outlined.Key,
      title = "Hugging Face Token",
    ) {
      Text(
        text = "Required for downloading gated models and private repositories.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = buildAnnotatedString {
          append("Generate a token with ")
          withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Read") }
          append(" permissions from ")
          withLink(LinkAnnotation.Url(
            url = "https://huggingface.co/settings/tokens",
            styles = TextLinkStyles(style = SpanStyle(color = OlliteRTPrimary, textDecoration = TextDecoration.Underline)),
          )) {
            append("HuggingFace token settings")
          }
          append(".")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = hfToken,
        onValueChange = { hfToken = it },
        singleLine = true,
        placeholder = {
          Text(
            "hf_...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        visualTransformation = if (hfTokenVisible) {
          VisualTransformation.None
        } else {
          PasswordVisualTransformation()
        },
        trailingIcon = {
          Row {
            IconButton(onClick = { hfTokenVisible = !hfTokenVisible }) {
              Icon(
                imageVector = if (hfTokenVisible) Icons.Outlined.VisibilityOff
                else Icons.Outlined.Visibility,
                contentDescription = if (hfTokenVisible) "Hide token" else "Show token",
                tint = OlliteRTPrimary,
                modifier = Modifier.size(20.dp),
              )
            }
            if (hfToken.isNotEmpty()) {
              IconButton(onClick = { hfToken = "" }) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = "Clear token",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
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

    // Discard unsaved changes dialog
    if (showDiscardDialog) {
      AlertDialog(
        onDismissRequest = { showDiscardDialog = false },
        title = { Text("Unsaved Changes") },
        text = { Text("You have unsaved changes. Would you like to save or discard them?") },
        confirmButton = {
          Button(onClick = {
            showDiscardDialog = false
            saveSettings()
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
    }

    // Footer
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "OlliteRT v1.0.11",
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
