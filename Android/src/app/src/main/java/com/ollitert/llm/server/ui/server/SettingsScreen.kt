package com.ollitert.llm.server.ui.server

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
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
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.ollitert.llm.server.ui.server.logs.exportLogcat
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.ui.server.settings.SettingDivider
import com.ollitert.llm.server.ui.server.settings.SettingLabel
import com.ollitert.llm.server.ui.server.settings.ToggleSettingRow
import com.ollitert.llm.server.ui.server.settings.NumericWithUnitRow
import com.ollitert.llm.server.ui.server.settings.NumericInputRow
import com.ollitert.llm.server.ui.server.settings.highlightSearchMatches
import com.ollitert.llm.server.ui.server.settings.KEEP_ALIVE_TIMEOUT
import com.ollitert.llm.server.ui.server.settings.CHECK_FREQUENCY
import com.ollitert.llm.server.ui.server.settings.LOG_AUTO_DELETE
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
import kotlinx.coroutines.launch

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

  // Hoist string resources for use inside non-composable callbacks (onClick, onDismissRequest, etc.)
  val settingsSavedText = stringResource(R.string.toast_settings_saved)
  val hfTokenLinkText = stringResource(R.string.settings_hf_token_link_text)
  val tokenClearedText = stringResource(R.string.toast_token_cleared)
  val tokenRegeneratedText = stringResource(R.string.toast_token_regenerated)
  val updateCheckTimeoutText = stringResource(R.string.settings_update_check_timeout)
  val checkingForUpdatesText = stringResource(R.string.settings_checking_for_updates)
  val logsClearedText = stringResource(R.string.toast_logs_cleared)
  val settingsSavedRestartManualText = stringResource(R.string.toast_settings_saved_restart_manual)
  val serverRestartingText = stringResource(R.string.toast_server_restarting)
  val settingsResetText = stringResource(R.string.toast_settings_reset)

  val performSave: () -> Unit = {
    val result = vm.trySave(serverStatus)
    when (result) {
      is SettingsViewModel.SaveResult.Success -> {
        val window = (context as? android.app.Activity)?.window
        if (vm.keepScreenOn) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(context, settingsSavedText, Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, settingsSavedText, Toast.LENGTH_SHORT).show()
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
        tooltip = stringResource(R.string.settings_save_tooltip),
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
      text = stringResource(R.string.settings_heading),
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = stringResource(R.string.settings_subheading),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Search bar — always visible, filters settings cards and individual settings within cards
    OutlinedTextField(
      value = vm.searchQuery,
      onValueChange = { vm.searchQuery = it },
      modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
      placeholder = { Text(stringResource(R.string.settings_search_placeholder), style = MaterialTheme.typography.bodyLarge) },
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
              contentDescription = stringResource(R.string.settings_search_clear),
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
        text = stringResource(R.string.settings_no_results, vm.searchQuery),
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
      title = stringResource(R.string.settings_card_general),
      searchQuery = vm.searchQuery,
    ) {
      // Divider logic: only show between consecutive visible settings
      val generalKeys = listOf("keep_screen_awake", "auto_expand_logs", "stream_response_preview", "compact_image_data", "hide_health_logs", "clear_logs_on_stop", "confirm_clear_logs", "keep_partial_response")
      val generalVisible = generalKeys.map { vm.settingVisible(it) }

      fun showGeneralDivider(index: Int): Boolean {
        if (!generalVisible[index]) return false
        return (0 until index).any { generalVisible[it] }
      }

      if (vm.settingVisible("keep_screen_awake")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_keep_screen_awake),
        description = stringResource(R.string.settings_keep_screen_awake_desc),
        checked = vm.keepScreenOn,
        onCheckedChange = { vm.keepScreenOn = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showGeneralDivider(1)) SettingDivider()
      if (vm.settingVisible("auto_expand_logs")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_auto_expand_logs),
        description = stringResource(R.string.settings_auto_expand_logs_desc),
        checked = vm.autoExpandLogs,
        onCheckedChange = { vm.autoExpandLogs = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showGeneralDivider(2)) SettingDivider()
      if (vm.settingVisible("stream_response_preview")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_stream_response_preview),
        description = stringResource(R.string.settings_stream_response_preview_desc),
        checked = vm.streamLogsPreview,
        onCheckedChange = { vm.streamLogsPreview = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showGeneralDivider(3)) SettingDivider()
      if (vm.settingVisible("compact_image_data")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_compact_image_data),
        description = stringResource(R.string.settings_compact_image_data_desc),
        checked = vm.compactImageData,
        onCheckedChange = { vm.compactImageData = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showGeneralDivider(4)) SettingDivider()
      if (vm.settingVisible("hide_health_logs")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_hide_health_logs),
        description = stringResource(R.string.settings_hide_health_logs_desc),
        checked = vm.hideHealthLogs,
        onCheckedChange = { vm.hideHealthLogs = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showGeneralDivider(5)) SettingDivider()
      if (vm.settingVisible("clear_logs_on_stop")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_clear_logs_on_stop),
        description = stringResource(R.string.settings_clear_logs_on_stop_desc),
        checked = vm.clearLogsOnStop,
        onCheckedChange = { vm.clearLogsOnStop = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showGeneralDivider(6)) SettingDivider()
      if (vm.settingVisible("confirm_clear_logs")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_confirm_clear_logs),
        description = stringResource(R.string.settings_confirm_clear_logs_desc),
        checked = vm.confirmClearLogs,
        onCheckedChange = { vm.confirmClearLogs = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showGeneralDivider(7)) SettingDivider()
      if (vm.settingVisible("keep_partial_response")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_keep_partial_response),
        description = stringResource(R.string.settings_keep_partial_response_desc),
        checked = vm.keepPartialResponse,
        onCheckedChange = { vm.keepPartialResponse = it },
        searchQuery = vm.searchQuery,
      )
      }

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
      title = stringResource(R.string.settings_card_hf_token),
      searchQuery = vm.searchQuery,
    ) {
      Text(
        text = stringResource(R.string.settings_hf_token_desc),
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
            append(hfTokenLinkText)
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
            stringResource(R.string.settings_hf_token_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        trailingIcon = {
          Row {
            IconButton(onClick = { vm.hfTokenVisible = !vm.hfTokenVisible }) {
              Icon(
                imageVector = if (vm.hfTokenVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = stringResource(if (vm.hfTokenVisible) R.string.settings_hf_token_hide else R.string.settings_hf_token_show),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (vm.hfToken.isNotBlank()) {
              IconButton(onClick = {
                vm.hfToken = ""
                Toast.makeText(context, tokenClearedText, Toast.LENGTH_SHORT).show()
              }) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = stringResource(R.string.settings_hf_token_clear),
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
      title = stringResource(R.string.settings_card_server_config),
      searchQuery = vm.searchQuery,
    ) {
      if (vm.settingVisible("host_port")) {
      Text(
        text = highlightSearchMatches(stringResource(R.string.settings_host_port_label), vm.searchQuery, OlliteRTPrimary),
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
            stringResource(R.string.settings_host_port_placeholder),
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
        text = stringResource(R.string.settings_host_port_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      } // if: host_port

      if (vm.settingVisible("host_port") && vm.settingVisible("bearer_token")) {
        SettingDivider()
      }

      // Bearer token toggle
      if (vm.settingVisible("bearer_token")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_bearer_token),
        description = stringResource(R.string.settings_bearer_token_desc),
        checked = vm.bearerEnabled,
        onCheckedChange = { enabled ->
          vm.bearerEnabled = enabled
          if (enabled && vm.bearerToken.isBlank()) {
            vm.bearerToken = java.util.UUID.randomUUID().toString().replace("-", "")
          }
        },
        searchQuery = vm.searchQuery,
      )
      // Token display + actions (only when bearer is enabled and setting is visible)
      if (vm.bearerEnabled && vm.settingVisible("bearer_token")) {
        SettingDivider()

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
            tooltip = stringResource(R.string.settings_bearer_copy_tooltip),
            onClick = {
              copyToClipboard(context, "OlliteRT Bearer Token", vm.bearerToken)
            },
          )

          Spacer(modifier = Modifier.width(4.dp))

          // Regenerate button
          TooltipIconButton(
            icon = Icons.Outlined.Refresh,
            tooltip = stringResource(R.string.settings_bearer_regenerate_tooltip),
            onClick = {
              vm.bearerToken = java.util.UUID.randomUUID().toString().replace("-", "")
              Toast.makeText(context, tokenRegeneratedText, Toast.LENGTH_SHORT).show()
            },
          )
        }
      }
      } // if: bearer_token

      if (vm.settingVisible("bearer_token") && vm.settingVisible("cors_origins")) {
        SettingDivider()
      }

      // CORS allowed origins
      if (vm.settingVisible("cors_origins")) {
      Text(
        text = highlightSearchMatches(stringResource(R.string.settings_cors_label), vm.searchQuery, OlliteRTPrimary),
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
            stringResource(R.string.settings_cors_placeholder),
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
                contentDescription = stringResource(R.string.settings_cors_clear),
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
        text = stringResource(R.string.settings_cors_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      } // if: cors_origins
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
      title = stringResource(R.string.settings_card_auto_launch),
      searchQuery = vm.searchQuery,
    ) {
      // Default model picker
      if (vm.settingVisible("default_model")) {
      Text(
        text = highlightSearchMatches(stringResource(R.string.settings_default_model_label), vm.searchQuery, OlliteRTPrimary),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      if (downloadedModelNames.isEmpty()) {
        Text(
          text = stringResource(R.string.settings_no_downloaded_models),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      } else {
        // Dropdown trigger
        Column {
          OutlinedTextField(
            value = vm.defaultModelName ?: stringResource(R.string.settings_none_manual_start),
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
                  stringResource(R.string.settings_none_manual_start),
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
        text = stringResource(R.string.settings_default_model_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      } // if: default_model

      if (vm.settingVisible("default_model") && vm.settingVisible("start_on_boot")) {
        SettingDivider()
      }

      if (vm.settingVisible("start_on_boot")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_start_on_boot),
        description = stringResource(
          if (vm.defaultModelName == null) R.string.settings_start_on_boot_desc_no_model
          else R.string.settings_start_on_boot_desc,
        ),
        checked = vm.autoStartOnBoot,
        onCheckedChange = { vm.autoStartOnBoot = it },
        searchQuery = vm.searchQuery,
        enabled = vm.defaultModelName != null,
        alphaOverride = if (vm.defaultModelName != null) 1f else 0.4f,
      )
      }

      if (vm.settingVisible("start_on_boot") && vm.settingVisible("keep_alive")) {
        SettingDivider()
      }

      if (vm.settingVisible("keep_alive")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_keep_alive),
        description = stringResource(R.string.settings_keep_alive_desc),
        checked = vm.keepAliveEnabled,
        onCheckedChange = { vm.keepAliveEnabled = it },
        searchQuery = vm.searchQuery,
      )

      SettingDivider(verticalPadding = 8)

      NumericWithUnitRow(
        def = KEEP_ALIVE_TIMEOUT,
        baseValue = vm.keepAliveMinutes.toLong(),
        savedBaseValue = vm.savedKeepAliveMinutes.toLong(),
        onBaseValueChange = { vm.keepAliveMinutes = it.toInt() },
        searchQuery = vm.searchQuery,
        isError = vm.keepAliveError,
        enabled = vm.keepAliveEnabled,
        onErrorClear = { vm.keepAliveError = false },
        modifier = Modifier.alpha(if (vm.keepAliveEnabled) 1f else 0.4f),
      )
      }

      if (vm.settingVisible("keep_alive") && vm.settingVisible("dontkillmyapp")) {
        SettingDivider()
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
            text = highlightSearchMatches(stringResource(R.string.settings_dontkillmyapp_title), vm.searchQuery, OlliteRTPrimary),
            style = MaterialTheme.typography.bodyMedium,
            color = OlliteRTPrimary,
          )
          Text(
            text = stringResource(R.string.settings_dontkillmyapp_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = stringResource(R.string.settings_dontkillmyapp_cd),
          tint = OlliteRTPrimary,
          modifier = Modifier.size(18.dp),
        )
      }
      } // if: dontkillmyapp

      if ((vm.settingVisible("dontkillmyapp") || vm.settingVisible("keep_alive")) && vm.settingVisible("update_check")) {
        SettingDivider()
      }

      // Check for Updates — manual check always available, automatic scheduling is separate
      if (vm.settingVisible("update_check")) {

      // Observe update availability from ServerMetrics to swap refresh → download icon
      val availableVersion by ServerMetrics.availableUpdateVersion.collectAsStateWithLifecycle()
      val availableUrl by ServerMetrics.availableUpdateUrl.collectAsStateWithLifecycle()
      val hasUpdate = availableVersion != null

      // Track the work request ID to observe results and show toast feedback
      var checkWorkId by remember { mutableStateOf<java.util.UUID?>(null) }
      val workManager = remember { WorkManager.getInstance(context) }

      // Observe work completion and show result toast.
      // Includes a 15-second timeout for cases where WorkManager can't run
      // the work (e.g. no network — work stays ENQUEUED indefinitely).
      checkWorkId?.let { id ->
        val workInfo by workManager.getWorkInfoByIdFlow(id).collectAsStateWithLifecycle(initialValue = null)
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
            Toast.makeText(context, updateCheckTimeoutText, Toast.LENGTH_SHORT).show()
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
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
          SettingLabel(text = stringResource(R.string.settings_check_for_updates), searchQuery = vm.searchQuery)
          Text(
            text = stringResource(R.string.settings_check_for_updates_desc),
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
            tooltip = stringResource(R.string.settings_download_version, availableVersion ?: ""),
            onClick = {
              val intent = UpdateCheckWorker.buildUpdateIntent(context, url)
              intent.data?.let { uri -> uriHandler.openUri(uri.toString()) }
            },
          )
        } else {
          // No update pending — show check now button
          TooltipIconButton(
            icon = Icons.Outlined.Refresh,
            tooltip = stringResource(R.string.settings_check_now_tooltip),
            onClick = {
              Toast.makeText(context, checkingForUpdatesText, Toast.LENGTH_SHORT).show()
              checkWorkId = UpdateCheckWorker.checkNow(context)
            },
          )
        }
      }

      SettingDivider()

      // Automatic update check — gated behind notification permissions
      val notifPermissionGranted = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
      val updateChannelMuted = UpdateCheckWorker.isUpdateChannelMuted(context)
      val updateControlsEnabled = notifPermissionGranted && !updateChannelMuted

      ToggleSettingRow(
        label = stringResource(R.string.settings_auto_update_check),
        description = stringResource(R.string.settings_auto_update_check_desc),
        checked = vm.updateCheckEnabled,
        onCheckedChange = { vm.updateCheckEnabled = it },
        searchQuery = vm.searchQuery,
        enabled = updateControlsEnabled,
      )

      // Notification permission/channel warning
      if (!notifPermissionGranted) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.settings_notif_permission_warning),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
        )
      } else if (updateChannelMuted) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.settings_notif_channel_muted),
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

      SettingDivider(verticalPadding = 8)

      NumericWithUnitRow(
        def = CHECK_FREQUENCY,
        baseValue = vm.updateCheckIntervalHours.toLong(),
        savedBaseValue = vm.savedUpdateCheckIntervalHours.toLong(),
        onBaseValueChange = { vm.updateCheckIntervalHours = it.toInt() },
        searchQuery = vm.searchQuery,
        isError = vm.updateCheckError,
        enabled = vm.updateCheckEnabled && updateControlsEnabled,
        modifier = Modifier.alpha(if (vm.updateCheckEnabled && updateControlsEnabled) 1f else 0.4f),
        onErrorClear = { vm.updateCheckError = false },
      )
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
      title = stringResource(R.string.settings_card_metrics),
      searchQuery = vm.searchQuery,
    ) {
      if (vm.settingVisible("show_request_types")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_show_request_types),
        description = stringResource(R.string.settings_show_request_types_desc),
        checked = vm.showRequestTypes,
        onCheckedChange = { vm.showRequestTypes = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (vm.settingVisible("show_request_types") && vm.settingVisible("show_advanced_metrics")) {
        SettingDivider(verticalPadding = 8)
      }

      if (vm.settingVisible("show_advanced_metrics")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_show_advanced_metrics),
        description = stringResource(R.string.settings_show_advanced_metrics_desc),
        checked = vm.showAdvancedMetrics,
        onCheckedChange = { vm.showAdvancedMetrics = it },
        searchQuery = vm.searchQuery,
      )
      }
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
      title = stringResource(R.string.settings_card_log_persistence),
      searchQuery = vm.searchQuery,
    ) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_persist_logs),
        description = stringResource(R.string.settings_persist_logs_desc),
        checked = vm.logPersistenceEnabled,
        onCheckedChange = { vm.logPersistenceEnabled = it },
        searchQuery = vm.searchQuery,
      )

      val childAlpha = if (vm.logPersistenceEnabled) 1f else 0.4f

      SettingDivider(verticalPadding = 8)

      var maxEntriesText by remember { mutableStateOf(vm.logMaxEntries.toString()) }
      NumericInputRow(
        label = stringResource(R.string.settings_max_log_entries_label),
        description = stringResource(R.string.settings_max_log_entries_desc),
        value = maxEntriesText,
        onValueChange = { text ->
          maxEntriesText = text
          text.toIntOrNull()?.let { vm.logMaxEntries = it }
        },
        searchQuery = vm.searchQuery,
        enabled = vm.logPersistenceEnabled,
        modifier = Modifier.alpha(childAlpha),
      )

      SettingDivider(verticalPadding = 8)

      NumericWithUnitRow(
        def = LOG_AUTO_DELETE,
        baseValue = vm.logAutoDeleteMinutes,
        savedBaseValue = vm.savedLogAutoDeleteMinutes,
        onBaseValueChange = { vm.logAutoDeleteMinutes = it },
        searchQuery = vm.searchQuery,
        enabled = vm.logPersistenceEnabled,
        modifier = Modifier.alpha(childAlpha),
      )

      SettingDivider(verticalPadding = 8)

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
          Text(stringResource(R.string.settings_clear_all_logs_button), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.settings_clear_all_logs_desc),
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
        title = { Text(stringResource(R.string.dialog_clear_logs_title)) },
        text = { Text(stringResource(R.string.dialog_clear_logs_body)) },
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
              Toast.makeText(context, logsClearedText, Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          ) {
            Text(stringResource(R.string.button_clear))
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
            Text(stringResource(R.string.cancel))
          }
        },
      )
    }

    // Trim logs confirmation — shown when max entries is reduced below current count
    if (vm.showTrimLogsDialog) {
      val currentCount = RequestLogStore.entries.collectAsStateWithLifecycle().value.size
      val toRemove = currentCount - vm.logMaxEntries
      AlertDialog(
        onDismissRequest = { vm.showTrimLogsDialog = false },
        title = { Text(stringResource(R.string.dialog_reduce_log_limit_title)) },
        text = {
          Text(stringResource(R.string.dialog_reduce_log_limit_body, currentCount, vm.logMaxEntries, toRemove))
        },
        confirmButton = {
          Button(
            onClick = {
              vm.showTrimLogsDialog = false
              forceSave()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          ) {
            Text(stringResource(R.string.continue_button_label))
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
            Text(stringResource(R.string.cancel))
          }
        },
      )
    }

    // Discard unsaved changes dialog
    if (vm.showDiscardDialog) {
      AlertDialog(
        onDismissRequest = { vm.showDiscardDialog = false },
        title = { Text(stringResource(R.string.dialog_unsaved_changes_title)) },
        text = { Text(stringResource(R.string.dialog_unsaved_changes_body)) },
        confirmButton = {
          Button(onClick = {
            vm.showDiscardDialog = false
            performSave()
            onBackClick()
          }) {
            Text(stringResource(R.string.button_save))
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
            Text(stringResource(R.string.button_discard))
          }
        },
      )
    }

    // Restart server dialog when settings that require a restart are changed
    if (vm.showRestartDialog) {
      AlertDialog(
        onDismissRequest = {
          vm.showRestartDialog = false
          Toast.makeText(context, settingsSavedRestartManualText, Toast.LENGTH_LONG).show()
        },
        title = { Text(stringResource(R.string.dialog_restart_server_title)) },
        text = {
          Text(stringResource(R.string.dialog_restart_server_body))
        },
        confirmButton = {
          Button(onClick = {
            vm.showRestartDialog = false
            onRestartServer()
            Toast.makeText(context, serverRestartingText, Toast.LENGTH_SHORT).show()
          }) {
            Text(stringResource(R.string.button_restart))
          }
        },
        dismissButton = {
          Button(
            onClick = {
              vm.showRestartDialog = false
              Toast.makeText(context, settingsSavedRestartManualText, Toast.LENGTH_LONG).show()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
          ) {
            Text(stringResource(R.string.button_later))
          }
        },
      )
    }

    // Reset to Defaults confirmation dialog
    if (vm.showResetDialog) {
      val isServerActive = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING
      AlertDialog(
        onDismissRequest = { vm.showResetDialog = false },
        title = { Text(stringResource(R.string.dialog_reset_defaults_title)) },
        text = {
          Text(
            stringResource(
              if (isServerActive) R.string.dialog_reset_defaults_body_server_active
              else R.string.dialog_reset_defaults_body,
            ),
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

              Toast.makeText(context, settingsResetText, Toast.LENGTH_SHORT).show()

              // Navigate to the Models screen
              onNavigateToModels()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
            ),
          ) {
            Text(stringResource(R.string.button_reset))
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
            Text(stringResource(R.string.cancel))
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
      title = stringResource(R.string.settings_card_home_assistant),
      searchQuery = vm.searchQuery,
    ) {
      // Toggle for HA integration (immediate-apply, not part of save flow)
      ToggleSettingRow(
        label = stringResource(R.string.settings_ha_rest_api),
        description = stringResource(R.string.settings_ha_rest_api_desc),
        checked = haIntegrationEnabled,
        onCheckedChange = {
          haIntegrationEnabled = it
          LlmHttpPrefs.setHaIntegrationEnabled(context, it)
        },
        searchQuery = vm.searchQuery,
      )

      // "Copy Configuration" button — only visible when the toggle is on
      if (haIntegrationEnabled) {
        SettingDivider()

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
          text = stringResource(R.string.settings_ha_config_preview, currentIp, currentPort, if (currentToken.isNotBlank()) stringResource(R.string.settings_ha_config_preview_token_suffix) else ""),
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
            text = stringResource(R.string.settings_ha_copy_config),
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
      title = stringResource(R.string.settings_card_advanced),
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
      ToggleSettingRow(
        label = stringResource(R.string.settings_warmup_message),
        description = stringResource(R.string.settings_warmup_message_desc),
        checked = vm.warmupEnabled,
        onCheckedChange = { vm.warmupEnabled = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showDividerBefore(1)) SettingDivider()
      if (vm.settingVisible("pre_init_vision")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_pre_init_vision),
        description = stringResource(R.string.settings_pre_init_vision_desc),
        checked = vm.eagerVisionInit,
        onCheckedChange = { vm.eagerVisionInit = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showDividerBefore(2)) SettingDivider()
      if (vm.settingVisible("custom_prompts")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_custom_prompts),
        description = stringResource(R.string.settings_custom_prompts_desc),
        checked = vm.customPromptsEnabled,
        onCheckedChange = { vm.customPromptsEnabled = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showDividerBefore(3)) SettingDivider()
      if (vm.settingVisible("truncate_history")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_truncate_history),
        description = stringResource(R.string.settings_truncate_history_desc),
        checked = vm.autoTruncateHistory,
        onCheckedChange = { vm.autoTruncateHistory = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showDividerBefore(4)) SettingDivider()
      if (vm.settingVisible("compact_tool_schemas")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_compact_tool_schemas),
        description = stringResource(R.string.settings_compact_tool_schemas_desc),
        checked = vm.compactToolSchemas,
        onCheckedChange = { vm.compactToolSchemas = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showDividerBefore(5)) SettingDivider()
      if (vm.settingVisible("trim_prompt")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_trim_prompt),
        description = stringResource(R.string.settings_trim_prompt_desc),
        checked = vm.autoTrimPrompts,
        onCheckedChange = { vm.autoTrimPrompts = it },
        searchQuery = vm.searchQuery,
      )
      }

      if (showDividerBefore(6)) SettingDivider()
      if (vm.settingVisible("ignore_client_params")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_ignore_client_params),
        description = stringResource(R.string.settings_ignore_client_params_desc),
        checked = vm.ignoreClientSamplerParams,
        onCheckedChange = { vm.ignoreClientSamplerParams = it },
        searchQuery = vm.searchQuery,
      )
      }
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
      title = stringResource(R.string.settings_card_developer),
      searchQuery = vm.searchQuery,
    ) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_verbose_debug),
        description = stringResource(R.string.settings_verbose_debug_desc),
        checked = vm.verboseDebugEnabled,
        onCheckedChange = { vm.verboseDebugEnabled = it },
        searchQuery = vm.searchQuery,
      )

      // Export Debug Logs button — visible only when verbose debug is enabled
      AnimatedVisibility(
        visible = vm.verboseDebugEnabled,
        enter = expandVertically(),
        exit = shrinkVertically(),
      ) {
        val logcatScope = rememberCoroutineScope()
        Column {
          SettingDivider(verticalPadding = 12)
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              SettingLabel(text = stringResource(R.string.settings_export_logcat), searchQuery = vm.searchQuery)
              Text(
                text = stringResource(R.string.settings_export_logcat_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            TooltipIconButton(
              onClick = { logcatScope.launch { exportLogcat(context) } },
              icon = Icons.Outlined.Share,
              tooltip = stringResource(R.string.settings_export_logcat),
            )
          }
        }
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
        text = stringResource(R.string.settings_reset_to_defaults),
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
          text = stringResource(R.string.settings_whats_new),
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
            val flavorDropdown = when (BuildConfig.CHANNEL) {
              "stable" -> "stable (OlliteRT)"
              "beta" -> "beta (OlliteRT Beta)"
              "dev" -> "dev (OlliteRT Dev)"
              else -> ""
            }
            val flavorParam = if (flavorDropdown.isNotEmpty()) "&flavor=$flavorDropdown" else ""
            val url = "${GitHubConfig.NEW_BUG_REPORT_URL}&device-info=$encoded$flavorParam"
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
          text = stringResource(R.string.settings_report_issue),
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
          text = stringResource(R.string.settings_donate),
          style = MaterialTheme.typography.bodyMedium,
          color = OlliteRTPrimary,
        )
      }
    }

    // Footer
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = stringResource(R.string.settings_version_footer, BuildConfig.VERSION_NAME, BuildConfig.GIT_HASH),
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
          text = stringResource(R.string.settings_unsaved_changes_banner),
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

