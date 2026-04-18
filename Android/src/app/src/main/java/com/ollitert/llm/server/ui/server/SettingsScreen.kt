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
import androidx.compose.material.icons.outlined.SystemUpdate
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
        if (vm.keepScreenOnEntry.current) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        if (vm.keepScreenOnEntry.current) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    if (vm.searchQuery.isNotBlank() && listOf("general","hf_token","server_config","auto_launch","metrics","log_persistence","home_assistant","updates","advanced","developer","reset").none { vm.cardVisible(it) }) {
      Text(
        text = stringResource(R.string.settings_no_results, vm.searchQuery),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally),
      )
    }

    // General card — uniform toggles, rendered via data-driven loop
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
      ToggleCardContent(
        keys = listOf("keep_screen_awake", "auto_expand_logs", "stream_response_preview", "compact_image_data", "hide_health_logs", "clear_logs_on_stop", "confirm_clear_logs", "keep_partial_response"),
        vm = vm,
      )
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
        value = vm.hfTokenEntry.current,
        onValueChange = { vm.hfTokenEntry.update(it.trim()) },
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
            if (vm.hfTokenEntry.current.isNotBlank()) {
              IconButton(onClick = {
                vm.hfTokenEntry.update("")
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
          vm.clearError("host_port")
        },
        singleLine = true,
        isError = vm.hasError("host_port"),
        placeholder = {
          Text(
            stringResource(R.string.settings_host_port_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (vm.hasError("host_port")) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (vm.hasError("host_port")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
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
        checked = vm.bearerEnabledEntry.current,
        onCheckedChange = { enabled ->
          vm.bearerEnabledEntry.update(enabled)
          if (enabled && vm.bearerTokenEntry.current.isBlank()) {
            vm.bearerTokenEntry.update(java.util.UUID.randomUUID().toString().replace("-", ""))
          }
        },
        searchQuery = vm.searchQuery,
      )
      // Token display + actions (only when bearer is enabled and setting is visible)
      if (vm.bearerEnabledEntry.current && vm.settingVisible("bearer_token")) {
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
            text = vm.bearerTokenEntry.current,
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
              copyToClipboard(context, "OlliteRT Bearer Token", vm.bearerTokenEntry.current)
            },
          )

          Spacer(modifier = Modifier.width(4.dp))

          // Regenerate button
          TooltipIconButton(
            icon = Icons.Outlined.Refresh,
            tooltip = stringResource(R.string.settings_bearer_regenerate_tooltip),
            onClick = {
              vm.bearerTokenEntry.update(java.util.UUID.randomUUID().toString().replace("-", ""))
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
        value = vm.corsAllowedOriginsEntry.current,
        onValueChange = {
          vm.corsAllowedOriginsEntry.update(it)
          if (vm.hasError("cors_origins")) vm.clearError("cors_origins")
        },
        singleLine = true,
        isError = vm.hasError("cors_origins"),
        placeholder = {
          Text(
            stringResource(R.string.settings_cors_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        trailingIcon = {
          if (vm.corsAllowedOriginsEntry.current.isNotBlank()) {
            IconButton(onClick = {
              vm.corsAllowedOriginsEntry.update("")
              if (vm.hasError("cors_origins")) vm.clearError("cors_origins")
            }) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.settings_cors_clear),
                tint = if (vm.hasError("cors_origins")) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (vm.hasError("cors_origins")) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (vm.hasError("cors_origins")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
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
            value = vm.defaultModelEntry.current ?: stringResource(R.string.settings_none_manual_start),
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
                  color = if (vm.defaultModelEntry.current == null) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
                )
              },
              onClick = {
                vm.defaultModelEntry.update(null)
                vm.autoStartOnBootEntry.update(false)
                vm.showModelDropdown = false
              },
            )
            HorizontalDivider()
            downloadedModelNames.forEach { modelName ->
              DropdownMenuItem(
                text = {
                  Text(
                    modelName,
                    color = if (modelName == vm.defaultModelEntry.current) OlliteRTPrimary else MaterialTheme.colorScheme.onSurface,
                  )
                },
                onClick = {
                  vm.defaultModelEntry.update(modelName)
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
          if (vm.defaultModelEntry.current == null) R.string.settings_start_on_boot_desc_no_model
          else R.string.settings_start_on_boot_desc,
        ),
        checked = vm.autoStartOnBootEntry.current,
        onCheckedChange = { vm.autoStartOnBootEntry.update(it) },
        searchQuery = vm.searchQuery,
        enabled = vm.isSettingEnabled("start_on_boot"),
        alphaOverride = vm.settingAlpha("start_on_boot"),
      )
      }

      if (vm.settingVisible("start_on_boot") && vm.settingVisible("keep_alive")) {
        SettingDivider()
      }

      if (vm.settingVisible("keep_alive")) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_keep_alive),
        description = stringResource(R.string.settings_keep_alive_desc),
        checked = vm.keepAliveEnabledEntry.current,
        onCheckedChange = { vm.keepAliveEnabledEntry.update(it) },
        searchQuery = vm.searchQuery,
      )

      SettingDivider(verticalPadding = 8)

      NumericWithUnitRow(
        def = KEEP_ALIVE_TIMEOUT,
        baseValue = vm.keepAliveMinutesEntry.current.toLong(),
        savedBaseValue = vm.keepAliveMinutesEntry.saved.toLong(),
        onBaseValueChange = { vm.keepAliveMinutesEntry.update(it.toInt()) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError("keep_alive_timeout"),
        enabled = vm.keepAliveEnabledEntry.current,
        onErrorClear = { vm.clearError("keep_alive_timeout") },
        modifier = Modifier.alpha(vm.settingAlpha("keep_alive_timeout")),
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

    }
    } // AnimatedVisibility: Auto-Launch

    // Metrics card — uniform toggles, rendered via data-driven loop
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
      ToggleCardContent(
        keys = listOf("show_request_types", "show_advanced_metrics"),
        vm = vm,
        dividerPadding = 8,
      )
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
        checked = vm.logPersistenceEnabledEntry.current,
        onCheckedChange = { vm.logPersistenceEnabledEntry.update(it) },
        searchQuery = vm.searchQuery,
      )

      val childAlpha = vm.settingAlpha("log_max_entries")

      SettingDivider(verticalPadding = 8)

      var maxEntriesText by remember { mutableStateOf(vm.logMaxEntriesEntry.current.toString()) }
      NumericInputRow(
        label = stringResource(R.string.settings_max_log_entries_label),
        description = stringResource(R.string.settings_max_log_entries_desc),
        value = maxEntriesText,
        onValueChange = { text ->
          maxEntriesText = text
          text.toIntOrNull()?.let { vm.logMaxEntriesEntry.update(it) }
        },
        searchQuery = vm.searchQuery,
        enabled = vm.isSettingEnabled("log_max_entries"),
        modifier = Modifier.alpha(childAlpha),
      )

      SettingDivider(verticalPadding = 8)

      NumericWithUnitRow(
        def = LOG_AUTO_DELETE,
        baseValue = vm.logAutoDeleteMinutesEntry.current,
        savedBaseValue = vm.logAutoDeleteMinutesEntry.saved,
        onBaseValueChange = { vm.logAutoDeleteMinutesEntry.update(it) },
        searchQuery = vm.searchQuery,
        enabled = vm.isSettingEnabled("log_auto_delete"),
        modifier = Modifier.alpha(childAlpha),
      )

      SettingDivider(verticalPadding = 8)

      Column(modifier = Modifier.alpha(childAlpha)) {
        Button(
          onClick = { vm.showClearPersistedDialog = true },
          enabled = vm.isSettingEnabled("clear_all_logs"),
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
      val toRemove = currentCount - vm.logMaxEntriesEntry.current
      AlertDialog(
        onDismissRequest = { vm.showTrimLogsDialog = false },
        title = { Text(stringResource(R.string.dialog_reduce_log_limit_title)) },
        text = {
          Text(stringResource(R.string.dialog_reduce_log_limit_body, currentCount, vm.logMaxEntriesEntry.current, toRemove))
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
        val currentToken = if (vm.bearerEnabledEntry.current) vm.bearerTokenEntry.current else ""
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

    // Advanced Settings card — uniform toggles, rendered via data-driven loop
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
      ToggleCardContent(
        keys = listOf("warmup_message", "pre_init_vision", "custom_prompts", "truncate_history", "compact_tool_schemas", "trim_prompt", "ignore_client_params"),
        vm = vm,
      )
    }
    } // AnimatedVisibility: Advanced

    // Updates card
    AnimatedVisibility(
      visible = vm.cardVisible("updates"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.SystemUpdate,
      title = stringResource(R.string.settings_card_updates),
      searchQuery = vm.searchQuery,
    ) {
      // Check for Updates — manual check always available, automatic scheduling is separate
      if (vm.settingVisible("check_for_updates")) {

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
      } // if: check_for_updates

      if (vm.settingVisible("check_for_updates") && vm.settingVisible("auto_update_check")) {
        SettingDivider()
      }

      if (vm.settingVisible("auto_update_check")) {
      // Automatic update check — gated behind notification permissions
      val notifPermissionGranted = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
      val updateChannelMuted = UpdateCheckWorker.isUpdateChannelMuted(context)
      val updateControlsEnabled = notifPermissionGranted && !updateChannelMuted

      ToggleSettingRow(
        label = stringResource(R.string.settings_auto_update_check),
        description = stringResource(R.string.settings_auto_update_check_desc),
        checked = vm.updateCheckEnabledEntry.current,
        onCheckedChange = { vm.updateCheckEnabledEntry.update(it) },
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
        baseValue = vm.updateCheckIntervalHoursEntry.current.toLong(),
        savedBaseValue = vm.updateCheckIntervalHoursEntry.saved.toLong(),
        onBaseValueChange = { vm.updateCheckIntervalHoursEntry.update(it.toInt()) },
        searchQuery = vm.searchQuery,
        isError = vm.hasError("check_frequency"),
        enabled = vm.updateCheckEnabledEntry.current && updateControlsEnabled,
        modifier = Modifier.alpha(if (vm.updateCheckEnabledEntry.current && updateControlsEnabled) 1f else 0.4f),
        onErrorClear = { vm.clearError("check_frequency") },
      )
      } // if: auto_update_check

    }
    } // AnimatedVisibility: Updates

    // Developer card
    AnimatedVisibility(
      visible = vm.cardVisible("developer"),
      enter = expandVertically(),
      exit = shrinkVertically(),
    ) {
    SettingsCard(
      icon = Icons.Outlined.BugReport,
      title = stringResource(R.string.settings_card_developer),
      searchQuery = vm.searchQuery,
    ) {
      ToggleSettingRow(
        label = stringResource(R.string.settings_verbose_debug),
        description = stringResource(R.string.settings_verbose_debug_desc),
        checked = vm.verboseDebugEnabledEntry.current,
        onCheckedChange = { vm.verboseDebugEnabledEntry.update(it) },
        searchQuery = vm.searchQuery,
      )

      // Export Debug Logs button — visible only when verbose debug is enabled
      AnimatedVisibility(
        visible = vm.verboseDebugEnabledEntry.current,
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

/**
 * Renders a list of toggle settings with automatic dividers between visible items.
 * Used by cards that contain only uniform toggle rows (General, Advanced, Metrics).
 */
@Composable
private fun ToggleCardContent(
  keys: List<String>,
  vm: SettingsViewModel,
  dividerPadding: Int = 16,
) {
  val visible = keys.map { vm.settingVisible(it) }
  var visibleCount = 0
  keys.forEachIndexed { index, key ->
    if (!visible[index]) return@forEachIndexed
    if (visibleCount > 0) SettingDivider(verticalPadding = dividerPadding)
    visibleCount++
    val entry = vm.getToggleEntry(key) ?: return@forEachIndexed
    val def = com.ollitert.llm.server.ui.server.settings.settingDefsByKey[key] as? com.ollitert.llm.server.ui.server.settings.SettingDef.Toggle ?: return@forEachIndexed
    ToggleSettingRow(
      label = stringResource(def.labelRes),
      description = stringResource(def.descriptionRes),
      checked = entry.current,
      onCheckedChange = { entry.update(it) },
      searchQuery = vm.searchQuery,
      enabled = vm.isSettingEnabled(key),
      alphaOverride = vm.settingAlpha(key),
    )
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

/** Overload that accepts a [CardIcon] sealed class for data-driven card rendering. */
@Composable
private fun SettingsCard(
  cardIcon: com.ollitert.llm.server.ui.server.settings.CardIcon,
  title: String,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
  content: @Composable () -> Unit,
) {
  when (cardIcon) {
    is com.ollitert.llm.server.ui.server.settings.CardIcon.Vector ->
      SettingsCard(icon = cardIcon.icon, title = title, modifier = modifier, searchQuery = searchQuery, content = content)
    is com.ollitert.llm.server.ui.server.settings.CardIcon.Resource ->
      SettingsCard(iconRes = cardIcon.resId, title = title, modifier = modifier, searchQuery = searchQuery, content = content)
  }
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

