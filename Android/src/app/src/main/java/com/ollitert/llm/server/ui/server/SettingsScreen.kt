package com.ollitert.llm.server.ui.server

import android.content.Context
import android.os.Build
import android.widget.Toast
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.ui.common.SCREEN_CONTENT_MAX_WIDTH
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

  // All state, change detection, search, validation, and save logic in SettingsViewModel
  val vm: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()

  val performSave: () -> Unit = {
    val result = vm.trySave(serverStatus)
    when (result) {
      is SettingsViewModel.SaveResult.Success -> {
        val window = (context as? android.app.Activity)?.window
        if (vm.keepScreenOn) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
      }
      is SettingsViewModel.SaveResult.NeedsRestart -> {
        val window = (context as? android.app.Activity)?.window
        if (result.keepScreenOn) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vm.showRestartDialog = true
      }
      is SettingsViewModel.SaveResult.ValidationError -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
      is SettingsViewModel.SaveResult.NeedsTrimConfirmation -> vm.showTrimLogsDialog = true
    }
  }
  val forceSave: () -> Unit = {
    val result = vm.save(serverStatus)
    when (result) {
      is SettingsViewModel.SaveResult.Success -> {
        val window = (context as? android.app.Activity)?.window
        if (vm.keepScreenOn) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
      }
      is SettingsViewModel.SaveResult.NeedsRestart -> {
        val window = (context as? android.app.Activity)?.window
        if (result.keepScreenOn) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vm.showRestartDialog = true
      }
      is SettingsViewModel.SaveResult.ValidationError -> Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
      is SettingsViewModel.SaveResult.NeedsTrimConfirmation -> {}
    }
  }

  BackHandler(enabled = vm.hasUnsavedChanges) { vm.showDiscardDialog = true }
  val currentSave by rememberUpdatedState(performSave)
  DisposableEffect(Unit) {
    onSetTopBarTrailingContent {
      TooltipIconButton(
        icon = Icons.Outlined.Save,
        tooltip = "Save settings",
        onClick = { currentSave() },
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
      .widthIn(max = SCREEN_CONTENT_MAX_WIDTH)
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
      value = vm.searchQuery,
      onValueChange = { vm.searchQuery = it },
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
        if (vm.searchQuery.isNotEmpty()) {
          IconButton(onClick = { vm.searchQuery = "" }) {
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
    if (vm.searchQuery.isNotBlank() && listOf("general","hf_token","server_config","auto_launch","metrics","log_persistence","home_assistant","advanced","developer","reset").none { vm.cardVisible(it) }) {
      Text(
        text = "No settings match \"$vm.searchQuery\"",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally),
      )
    }

    // General card
    AnimatedVisibility(
      visible = vm.cardVisible("general"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.PhoneAndroid,
      title = "General",
      searchQuery = vm.searchQuery,
    ) {
      // Divider logic: only show between consecutive visible settings
      val generalKeys = listOf("keep_screen_awake", "auto_expand_logs", "stream_response_preview", "compact_image_data", "clear_logs_on_stop", "confirm_clear_logs", "keep_partial_response")
      val generalVisible = generalKeys.map { vm.settingVisible(it) }

      fun showGeneralDivider(index: Int): Boolean {
        if (!generalVisible[index]) return false
        return (0 until index).any { generalVisible[it] }
      }

      // Keep screen awake toggle
      if (vm.settingVisible("keep_screen_awake")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Keep Screen Awake", searchQuery = vm.searchQuery)
          Text(
            text = "Prevent screen from turning off while app is open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.keepScreenOn,
          onCheckedChange = { enabled ->
            vm.keepScreenOn = enabled
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
      if (vm.settingVisible("auto_expand_logs")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Auto-Expand Logs", searchQuery = vm.searchQuery)
          Text(
            text = "Show full request and response bodies in the Logs tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.autoExpandLogs,
          onCheckedChange = { vm.autoExpandLogs = it },
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
      if (vm.settingVisible("stream_response_preview")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Stream Response Preview", searchQuery = vm.searchQuery)
          Text(
            text = "Show model output as it generates in the Logs tab for streaming requests.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.streamLogsPreview,
          onCheckedChange = { vm.streamLogsPreview = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: stream_response_preview

      if (showGeneralDivider(3)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Compact image data toggle — replaces base64 payloads with size placeholders at capture time
      if (vm.settingVisible("compact_image_data")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Compact Image Data in Logs", searchQuery = vm.searchQuery)
          Text(
            text = "Replace inline base64 image data with a size placeholder when storing log entries. " +
              "Prevents UI lag from rendering multi-MB image payloads. " +
              "Disable for full raw data in logs (may cause lag with large images).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.compactImageData,
          onCheckedChange = { vm.compactImageData = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: compact_image_data

      if (showGeneralDivider(4)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Clear logs on stop toggle
      if (vm.settingVisible("clear_logs_on_stop")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Clear Logs on Stop", searchQuery = vm.searchQuery)
          Text(
            text = "Automatically clear in-memory logs when the server stops.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.clearLogsOnStop,
          onCheckedChange = { vm.clearLogsOnStop = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: clear_logs_on_stop

      if (showGeneralDivider(5)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Confirm before clearing logs
      if (vm.settingVisible("confirm_clear_logs")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Confirm Before Clearing Logs", searchQuery = vm.searchQuery)
          Text(
            text = "Show a confirmation dialog before clearing logs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.confirmClearLogs,
          onCheckedChange = { vm.confirmClearLogs = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: confirm_clear_logs

      if (showGeneralDivider(6)) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Keep partial response toggle
      if (vm.settingVisible("keep_partial_response")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Keep Partial Response", searchQuery = vm.searchQuery)
          Text(
            text = "Preserve incomplete response text in logs when a streaming request is cancelled by the client.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.keepPartialResponse,
          onCheckedChange = { vm.keepPartialResponse = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: keep_partial_response

    }
    } // AnimatedVisibility: General

    // Hugging Face Token card
    AnimatedVisibility(
      visible = vm.cardVisible("hf_token"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Key,
      title = "Hugging Face Token",
      searchQuery = vm.searchQuery,
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
        value = vm.hfToken,
        onValueChange = { vm.hfToken = it.trim() },
        singleLine = true,
        visualTransformation = if (vm.hfTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
        placeholder = {
          Text(
            "hf_...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        trailingIcon = {
          Row {
            IconButton(onClick = { vm.hfTokenVisible = !vm.hfTokenVisible }) {
              Icon(
                imageVector = if (vm.hfTokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (vm.hfTokenVisible) "Hide token" else "Show token",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (vm.hfToken.isNotBlank()) {
              IconButton(onClick = {
                vm.hfToken = ""
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
      visible = vm.cardVisible("server_config"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Tune,
      title = "Server Configuration",
      searchQuery = vm.searchQuery,
    ) {
      if (vm.settingVisible("host_port")) {
      Text(
        text = highlightSearchMatches("Host Port (1024–65535)", vm.searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = vm.portText,
        onValueChange = { input ->
          // Allow only digits, let user freely type/delete
          vm.portText = input.filter { it.isDigit() }.take(5)
          vm.portError = false
        },
        singleLine = true,
        isError = vm.portError,
        placeholder = {
          Text(
            "8000",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (vm.portError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (vm.portError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
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

      if (vm.settingVisible("host_port") && vm.settingVisible("bearer_token")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Bearer token toggle
      if (vm.settingVisible("bearer_token")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Require Bearer Token", searchQuery = vm.searchQuery)
          Text(
            text = "Protect the API with a bearer token. Clients must include it in the Authorization header.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.bearerEnabled,
          onCheckedChange = { enabled ->
            vm.bearerEnabled = enabled
            if (enabled && vm.bearerToken.isBlank()) {
              vm.bearerToken = java.util.UUID.randomUUID().toString().replace("-", "")
            }
          },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: bearer_token

      if (vm.settingVisible("bearer_token") && vm.settingVisible("cors_origins")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // CORS allowed origins
      if (vm.settingVisible("cors_origins")) {
      Text(
        text = highlightSearchMatches("CORS Allowed Origins", vm.searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = vm.corsAllowedOrigins,
        onValueChange = {
          vm.corsAllowedOrigins = it
          // Clear error as soon as the user edits the field
          if (vm.corsError) vm.corsError = false
        },
        singleLine = true,
        isError = vm.corsError,
        placeholder = {
          Text(
            "*",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        trailingIcon = {
          if (vm.corsAllowedOrigins.isNotBlank()) {
            IconButton(onClick = {
              vm.corsAllowedOrigins = ""
              if (vm.corsError) vm.corsError = false
            }) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Clear origins",
                tint = if (vm.corsError) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (vm.corsError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (vm.corsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
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
      if (vm.bearerEnabled && vm.settingVisible("bearer_token")) {
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
            text = vm.bearerToken,
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
              copyToClipboard(context, "OlliteRT Bearer Token", vm.bearerToken)
            },
          )

          Spacer(modifier = Modifier.width(4.dp))

          // Regenerate button
          TooltipIconButton(
            icon = Icons.Outlined.Refresh,
            tooltip = "Regenerate token",
            onClick = {
              vm.bearerToken = java.util.UUID.randomUUID().toString().replace("-", "")
              Toast.makeText(context, "Token regenerated — save to apply", Toast.LENGTH_SHORT).show()
            },
          )
        }
      }
    }
    } // AnimatedVisibility: Server Config

    // Auto-Launch & Behavior card
    AnimatedVisibility(
      visible = vm.cardVisible("auto_launch"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.PlayArrow,
      title = "Auto-Launch & Behavior",
      searchQuery = vm.searchQuery,
    ) {
      // Default model picker
      if (vm.settingVisible("default_model")) {
      Text(
        text = highlightSearchMatches("Default Model", vm.searchQuery, OlliteRTPrimary),
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
            value = vm.defaultModelName ?: "None (manual start)",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { vm.showModelDropdown = true },
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
              disabledTextColor = MaterialTheme.colorScheme.onSurface,
              disabledBorderColor = MaterialTheme.colorScheme.outline,
              disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
          )
          DropdownMenu(
            expanded = vm.showModelDropdown,
            onDismissRequest = { vm.showModelDropdown = false },
          ) {
            DropdownMenuItem(
              text = {
                Text(
                  "None (manual start)",
                  color = if (vm.defaultModelName == null) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
                )
              },
              onClick = {
                vm.defaultModelName = null
                vm.autoStartOnBoot = false  // Can't auto-start without a default model
                vm.showModelDropdown = false
              },
            )
            HorizontalDivider()
            downloadedModelNames.forEach { modelName ->
              DropdownMenuItem(
                text = {
                  Text(
                    modelName,
                    color = if (modelName == vm.defaultModelName) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
                  )
                },
                onClick = {
                  vm.defaultModelName = modelName
                  vm.showModelDropdown = false
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

      if (vm.settingVisible("default_model") && vm.settingVisible("start_on_boot")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Auto-start on boot toggle — entire row dims when no default model is selected
      if (vm.settingVisible("start_on_boot")) {
      val autoStartAlpha = if (vm.defaultModelName != null) 1f else 0.4f
      Row(
        modifier = Modifier.fillMaxWidth().alpha(autoStartAlpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Start on Boot", searchQuery = vm.searchQuery)
          Text(
            text = if (vm.defaultModelName == null) "Select a default model above to enable."
                   else "Launch server automatically when device starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.autoStartOnBoot,
          onCheckedChange = { enabled ->
            vm.autoStartOnBoot = enabled
          },
          enabled = vm.defaultModelName != null,
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: start_on_boot

      if (vm.settingVisible("start_on_boot") && vm.settingVisible("keep_alive")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Keep Alive — auto-unload model after idle timeout to free RAM
      if (vm.settingVisible("keep_alive")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Keep Alive", searchQuery = vm.searchQuery)
          Text(
            text = "Unload model after idle timeout to free RAM. Next request auto-reloads (cold start).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.keepAliveEnabled,
          onCheckedChange = { vm.keepAliveEnabled = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }

      // Idle timeout child control — dimmed when keep alive is disabled
      val keepAliveChildAlpha = if (vm.keepAliveEnabled) 1f else 0.4f

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(8.dp))

      Column(modifier = Modifier.alpha(keepAliveChildAlpha)) {
        Text(
          text = highlightSearchMatches("Idle Timeout", vm.searchQuery, OlliteRTPrimary),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        val keepAliveTimeoutUnits = listOf("minutes", "hours")
        val initialKeepAliveValue = remember(vm.savedKeepAliveMinutes) {
          val mins = vm.savedKeepAliveMinutes
          if (mins > 0 && mins % 60 == 0) (mins / 60).toString() else mins.toString()
        }
        var keepAliveValueText by remember { mutableStateOf(initialKeepAliveValue) }
        var showKeepAliveUnitDropdown by remember { mutableStateOf(false) }

        fun recomputeKeepAliveMinutes() {
          val num = keepAliveValueText.toIntOrNull() ?: 0
          val totalMinutes = when (vm.keepAliveUnit) {
            "hours" -> num * 60
            else -> num
          }
          vm.keepAliveMinutes = totalMinutes
          if (vm.keepAliveError) vm.keepAliveError = false
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
            isError = vm.keepAliveError,
            enabled = vm.keepAliveEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = if (vm.keepAliveError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
              unfocusedBorderColor = if (vm.keepAliveError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
              cursorColor = OlliteRTPrimary,
            ),
          )
          // Unit selector dropdown
          Column {
            OutlinedTextField(
              value = vm.keepAliveUnit,
              onValueChange = {},
              readOnly = true,
              singleLine = true,
              modifier = Modifier
                .widthIn(min = 90.dp, max = 120.dp)
                .clickable(enabled = vm.keepAliveEnabled) {
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
                      color = if (unit == vm.keepAliveUnit) OlliteRTPrimary
                              else MaterialTheme.colorScheme.onSurface,
                    )
                  },
                  onClick = {
                    vm.keepAliveUnit = unit
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

      if (vm.settingVisible("keep_alive") && vm.settingVisible("dontkillmyapp")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Link to dontkillmyapp.com — OEM-specific battery/background kill settings
      if (vm.settingVisible("dontkillmyapp")) {
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
            text = highlightSearchMatches("Device background settings", vm.searchQuery, OlliteRTPrimary),
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

      if ((vm.settingVisible("dontkillmyapp") || vm.settingVisible("keep_alive")) && vm.settingVisible("update_check")) {
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))
      }

      // Check for Updates — manual check always available, automatic scheduling is separate
      if (vm.settingVisible("update_check")) {

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
          SettingLabel(text = "Check for Updates", searchQuery = vm.searchQuery)
          Text(
            text = "Check for a newer version of OlliteRT.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (hasUpdate && !availableUrl.isNullOrBlank()) {
          // Update available — show download button that opens Play Store or GitHub
          val uriHandler = LocalUriHandler.current
          val url = availableUrl ?: ""
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
          SettingLabel(text = "Automatic Update Check", searchQuery = vm.searchQuery)
          Text(
            text = "Periodically check in the background and notify when an update is available.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.updateCheckEnabled,
          onCheckedChange = { vm.updateCheckEnabled = it },
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
      val updateChildAlpha = if (vm.updateCheckEnabled && updateControlsEnabled) 1f else 0.4f

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(8.dp))

      Column(modifier = Modifier.alpha(updateChildAlpha)) {
        Text(
          text = highlightSearchMatches("Check Frequency", vm.searchQuery, OlliteRTPrimary),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        val updateCheckUnits = listOf("hours", "days")
        val initialUpdateValue = remember(vm.savedUpdateCheckIntervalHours) {
          val h = vm.savedUpdateCheckIntervalHours
          if (h > 0 && h % 24 == 0) (h / 24).toString() else h.toString()
        }
        var updateValueText by remember { mutableStateOf(initialUpdateValue) }
        var showUpdateUnitDropdown by remember { mutableStateOf(false) }

        fun recomputeUpdateHours() {
          val num = updateValueText.toIntOrNull() ?: 0
          val totalHours = when (vm.updateCheckUnit) {
            "days" -> num * 24
            else -> num
          }
          vm.updateCheckIntervalHours = totalHours
          if (vm.updateCheckError) vm.updateCheckError = false
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
            isError = vm.updateCheckError,
            enabled = vm.updateCheckEnabled && updateControlsEnabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = if (vm.updateCheckError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
              unfocusedBorderColor = if (vm.updateCheckError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
              cursorColor = OlliteRTPrimary,
            ),
          )
          // Unit selector dropdown
          Column {
            OutlinedTextField(
              value = vm.updateCheckUnit,
              onValueChange = {},
              readOnly = true,
              singleLine = true,
              modifier = Modifier
                .widthIn(min = 90.dp, max = 120.dp)
                .clickable(enabled = vm.updateCheckEnabled && updateControlsEnabled) {
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
                      color = if (unit == vm.updateCheckUnit) OlliteRTPrimary
                              else MaterialTheme.colorScheme.onSurface,
                    )
                  },
                  onClick = {
                    vm.updateCheckUnit = unit
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
      visible = vm.cardVisible("metrics"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.BarChart,
      title = "Metrics",
      searchQuery = vm.searchQuery,
    ) {
      // Show Request Types on Status screen
      if (vm.settingVisible("show_request_types")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Show Request Types", searchQuery = vm.searchQuery)
          Text(
            text = "Show text, vision, and audio request counts on the Status screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.showRequestTypes,
          onCheckedChange = { vm.showRequestTypes = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: show_request_types

      if (vm.settingVisible("show_request_types") && vm.settingVisible("show_advanced_metrics")) {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))
      }

      // Show Advanced Metrics on Status screen
      if (vm.settingVisible("show_advanced_metrics")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Show Advanced Metrics", searchQuery = vm.searchQuery)
          Text(
            text = "Display prefill speed, inter-token latency, latency stats, and context utilization on the Status screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.showAdvancedMetrics,
          onCheckedChange = { vm.showAdvancedMetrics = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: show_advanced_metrics
    }
    } // AnimatedVisibility: Metrics

    // Log Persistence card
    AnimatedVisibility(
      visible = vm.cardVisible("log_persistence"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Storage,
      title = "Log Persistence",
      searchQuery = vm.searchQuery,
    ) {
      // Master toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Persist Logs to Database", searchQuery = vm.searchQuery)
          Text(
            text = "Save activity logs to a local database so they survive app restarts. Disabled by default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.logPersistenceEnabled,
          onCheckedChange = { vm.logPersistenceEnabled = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }

      // Child controls — disabled (greyed out) when master toggle is OFF
      val childAlpha = if (vm.logPersistenceEnabled) 1f else 0.4f

      Spacer(modifier = Modifier.height(8.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(8.dp))

      // Max Entries — simple number input, value updates live into unsaved state
      Column(modifier = Modifier.alpha(childAlpha)) {
        Text(
          text = highlightSearchMatches("Maximum Log Entries", vm.searchQuery, OlliteRTPrimary),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        // Track as text so the user can freely edit (e.g. delete "500" and type "200")
        var maxEntriesText by remember { mutableStateOf(vm.logMaxEntries.toString()) }
        OutlinedTextField(
          value = maxEntriesText,
          onValueChange = { text ->
            val filtered = text.filter { it.isDigit() }.take(5) // max 5 digits (99999)
            maxEntriesText = filtered
            filtered.toIntOrNull()?.let { vm.logMaxEntries = it }
          },
          singleLine = true,
          enabled = vm.logPersistenceEnabled,
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
          text = highlightSearchMatches("Auto-Delete After", vm.searchQuery, OlliteRTPrimary),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Decompose total minutes into a display value + unit for the UI.
        // Pick the largest unit that divides evenly, defaulting to minutes.
        val autoDeleteUnits = listOf("minutes", "hours", "days")
        val (initialValue, initialUnit) = remember(vm.savedLogAutoDeleteMinutes) {
          val mins = vm.savedLogAutoDeleteMinutes
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
          vm.logAutoDeleteMinutes = when (autoDeleteUnit) {
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
            enabled = vm.logPersistenceEnabled,
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
                .clickable(enabled = vm.logPersistenceEnabled) {
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
          onClick = { vm.showClearPersistedDialog = true },
          enabled = vm.logPersistenceEnabled,
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
    if (vm.showClearPersistedDialog) {
      AlertDialog(
        onDismissRequest = { vm.showClearPersistedDialog = false },
        title = { Text("Clear All Logs") },
        text = { Text("This will permanently delete all logs — both the current session and the database. This cannot be undone.") },
        confirmButton = {
          Button(
            onClick = {
              vm.showClearPersistedDialog = false
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
            onClick = { vm.showClearPersistedDialog = false },
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
    if (vm.showTrimLogsDialog) {
      val currentCount = RequestLogStore.entries.value.size
      val toRemove = currentCount - vm.logMaxEntries
      AlertDialog(
        onDismissRequest = { vm.showTrimLogsDialog = false },
        title = { Text("Reduce Log Limit") },
        text = {
          Text("You currently have $currentCount logs. Reducing the limit to $vm.logMaxEntries will remove the $toRemove oldest entries after saving.")
        },
        confirmButton = {
          Button(
            onClick = {
              vm.showTrimLogsDialog = false
              forceSave()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          ) {
            Text("Continue")
          }
        },
        dismissButton = {
          Button(
            onClick = { vm.showTrimLogsDialog = false },
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
    if (vm.showDiscardDialog) {
      AlertDialog(
        onDismissRequest = { vm.showDiscardDialog = false },
        title = { Text("Unsaved Changes") },
        text = { Text("You have unsaved changes. Would you like to save or discard them?") },
        confirmButton = {
          Button(onClick = {
            vm.showDiscardDialog = false
            performSave()
            onBackClick()
          }) {
            Text("Save")
          }
        },
        dismissButton = {
          Button(onClick = {
            vm.showDiscardDialog = false
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
    if (vm.showRestartDialog) {
      AlertDialog(
        onDismissRequest = {
          vm.showRestartDialog = false
          Toast.makeText(context, "Settings saved. Restart the server manually for changes to take effect.", Toast.LENGTH_LONG).show()
        },
        title = { Text("Restart server?") },
        text = {
          Text("Some of the changed settings require a server restart to take effect.")
        },
        confirmButton = {
          Button(onClick = {
            vm.showRestartDialog = false
            onRestartServer()
            Toast.makeText(context, "Server restarting with updated settings", Toast.LENGTH_SHORT).show()
          }) {
            Text("Restart")
          }
        },
        dismissButton = {
          Button(
            onClick = {
              vm.showRestartDialog = false
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
    if (vm.showResetDialog) {
      val isServerActive = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING
      AlertDialog(
        onDismissRequest = { vm.showResetDialog = false },
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
              vm.showResetDialog = false

              // Stop the server if it's running
              if (isServerActive) {
                onStopServer()
              }

              // Reset all settings and clear persisted logs via ViewModel
              vm.resetToDefaults()

              // Apply keep-screen-on default (off after reset)
              val window = (context as? android.app.Activity)?.window
              window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
            onClick = { vm.showResetDialog = false },
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
    if (vm.showDonateDialog) {
      com.ollitert.llm.server.ui.common.DonateDialog(
        onDismiss = { vm.showDonateDialog = false },
      )
    }

    // Home Assistant Integration card — immediate-apply (not part of save/cancel flow)
    var haIntegrationEnabled by remember { mutableStateOf(LlmHttpPrefs.isHaIntegrationEnabled(context)) }


    AnimatedVisibility(
      visible = vm.cardVisible("home_assistant"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      iconRes = com.ollitert.llm.server.R.drawable.ic_home_assistant,
      title = "Home Assistant",
      searchQuery = vm.searchQuery,
    ) {
      // Toggle for HA integration
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "REST API Integration", searchQuery = vm.searchQuery)
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
        val currentPort = vm.portText.toIntOrNull() ?: LlmHttpPrefs.getPort(context)
        val currentIp = remember { getWifiIpAddress(context) ?: "<YOUR_DEVICE_IP>" }
        val currentToken = if (vm.bearerEnabled) vm.bearerToken else ""
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
      visible = vm.cardVisible("advanced"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Science,
      title = "Advanced",
      searchQuery = vm.searchQuery,
    ) {
      // Track which advanced settings are visible to control dividers between them.
      // Dividers only show between two consecutively visible settings.
      val advancedKeys = listOf("warmup_message", "pre_init_vision", "custom_prompts", "truncate_history", "compact_tool_schemas", "trim_prompt", "ignore_client_params")
      val advancedVisible = advancedKeys.map { vm.settingVisible(it) }

      /** Show a divider before [index] only if a preceding setting is also visible. */
      fun showDividerBefore(index: Int): Boolean {
        if (!advancedVisible[index]) return false
        return (0 until index).any { advancedVisible[it] }
      }

      if (vm.settingVisible("warmup_message")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Warmup Message", searchQuery = vm.searchQuery)
          Text(
            text = "Send a test message when the model loads to verify the engine is working. Disabling this speeds up model startup.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.warmupEnabled,
          onCheckedChange = { vm.warmupEnabled = it },
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
      if (vm.settingVisible("pre_init_vision")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Pre-initialize Vision", searchQuery = vm.searchQuery)
          Text(
            text = "Load the vision backend when a multimodal model starts, even before any image request arrives. Eliminates delay on the first image request but increases memory and GPU usage from the start.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.eagerVisionInit,
          onCheckedChange = { vm.eagerVisionInit = it },
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
      if (vm.settingVisible("custom_prompts")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Custom System Prompt & Chat Template", searchQuery = vm.searchQuery)
          Text(
            text = "Enable per-model system prompt and chat template fields in Inference Settings. Useful for models with non-standard prompt formats.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.customPromptsEnabled,
          onCheckedChange = { vm.customPromptsEnabled = it },
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
      if (vm.settingVisible("truncate_history")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Truncate Conversation History", searchQuery = vm.searchQuery)
          Text(
            text = "When a request exceeds the model's context window, drop older messages from the conversation while keeping system prompts and the most recent messages.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.autoTruncateHistory,
          onCheckedChange = { vm.autoTruncateHistory = it },
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
      if (vm.settingVisible("compact_tool_schemas")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Compact Tool Schemas", searchQuery = vm.searchQuery)
          Text(
            text = "When a request with tools exceeds the model's context window, automatically reduce tool schemas to names and descriptions only (omitting parameter details). Especially useful for Home Assistant integration which sends many tool definitions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.compactToolSchemas,
          onCheckedChange = { vm.compactToolSchemas = it },
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
      if (vm.settingVisible("trim_prompt")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Trim Prompt", searchQuery = vm.searchQuery)
          Text(
            text = "Last resort when other strategies aren't enough. Hard-cuts the prompt to fit the context window, keeping the most recent content and discarding the beginning.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.autoTrimPrompts,
          onCheckedChange = { vm.autoTrimPrompts = it },
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
      if (vm.settingVisible("ignore_client_params")) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Ignore Client Sampler Parameters", searchQuery = vm.searchQuery)
          Text(
            text = "Discard temperature, top_p, top_k, and max_tokens values sent by API clients. The server's own Inference Settings will always be used instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.ignoreClientSamplerParams,
          onCheckedChange = { vm.ignoreClientSamplerParams = it },
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
        )
      }
      } // if: ignore_client_params
    }
    } // AnimatedVisibility: Advanced

    // Developer card — verbose debug toggle (immediate-apply, no save/cancel)
    AnimatedVisibility(
      visible = vm.cardVisible("developer"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.Code,
      title = "Developer",
      searchQuery = vm.searchQuery,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SettingLabel(text = "Verbose Debug Mode", searchQuery = vm.searchQuery)
          Text(
            text = "Logs additional details: full stack traces, memory snapshots, model config, per-request timing. May impact performance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = vm.verboseDebugEnabled,
          onCheckedChange = {
            vm.verboseDebugEnabled = it
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
      visible = vm.cardVisible("reset"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    Column {
    Spacer(modifier = Modifier.height(16.dp))
    Button(
      onClick = { vm.showResetDialog = true },
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
          .clickable { vm.showDonateDialog = true }
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
      visible = vm.hasUnsavedChanges,
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

