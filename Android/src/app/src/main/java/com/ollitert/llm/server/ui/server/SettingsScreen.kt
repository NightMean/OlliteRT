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

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.ServerStatus
import com.ollitert.llm.server.ui.common.OlliteSearchBar
import com.ollitert.llm.server.ui.common.SCREEN_CONTENT_MAX_WIDTH
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.server.settings.AdvancedCard
import com.ollitert.llm.server.ui.server.settings.AutoLaunchCard
import com.ollitert.llm.server.ui.server.settings.CardId
import com.ollitert.llm.server.ui.server.settings.ContextManagementCard
import com.ollitert.llm.server.ui.server.settings.DeveloperCard
import com.ollitert.llm.server.ui.server.settings.GeneralCard
import com.ollitert.llm.server.ui.server.settings.HfTokenCard
import com.ollitert.llm.server.ui.server.settings.HomeAssistantCard
import com.ollitert.llm.server.ui.server.settings.LogPersistenceCard
import com.ollitert.llm.server.ui.server.settings.MetricsCard
import com.ollitert.llm.server.ui.server.settings.ResetCard
import com.ollitert.llm.server.ui.server.settings.ServerConfigCard
import com.ollitert.llm.server.ui.server.settings.SettingsDialogs
import com.ollitert.llm.server.ui.server.settings.SettingsFooter
import com.ollitert.llm.server.ui.server.settings.RepositoriesCard
import com.ollitert.llm.server.ui.server.settings.UpdatesCard
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

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
  onSettingsSaved: () -> Unit = {},
  onNavigateToRepositories: () -> Unit = {},
) {
  val context = LocalContext.current

  val vm: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel()

  val settingsSavedText = stringResource(R.string.toast_settings_saved)

  val performSave: () -> Unit = {
    val result = vm.trySave(serverStatus)
    when (result) {
      is SettingsViewModel.SaveResult.Success -> {
        val window = (context as? android.app.Activity)?.window
        if (vm.keepScreenOnEntry.current) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(context, settingsSavedText, Toast.LENGTH_SHORT).show()
        onSettingsSaved()
      }
      is SettingsViewModel.SaveResult.NeedsRestart -> {
        val window = (context as? android.app.Activity)?.window
        if (result.keepScreenOn) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vm.showRestartDialog = true
        onSettingsSaved()
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
        onSettingsSaved()
      }
      is SettingsViewModel.SaveResult.NeedsRestart -> {
        val window = (context as? android.app.Activity)?.window
        if (result.keepScreenOn) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        vm.showRestartDialog = true
        onSettingsSaved()
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

    // Search bar
    OlliteSearchBar(
      query = vm.searchQuery,
      onQueryChange = { vm.searchQuery = it },
      placeholderRes = R.string.settings_search_placeholder,
      clearContentDescriptionRes = R.string.settings_search_clear,
    )

    // "No results" message
    if (vm.searchQuery.isNotBlank() && CardId.entries.none { vm.cardVisible(it) }) {
      Text(
        text = stringResource(R.string.settings_no_results, vm.searchQuery),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally),
      )
    }

    // Cards
    AnimatedVisibility(visible = vm.cardVisible(CardId.GENERAL), enter = expandVertically(), exit = shrinkVertically()) {
      GeneralCard(vm)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.REPOSITORIES), enter = expandVertically(), exit = shrinkVertically()) {
      RepositoriesCard(
        repoCount = vm.repoCount,
        enabledCount = vm.enabledRepoCount,
        onNavigateToRepositories = onNavigateToRepositories,
        searchQuery = vm.searchQuery,
      )
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.HF_TOKEN), enter = expandVertically(), exit = shrinkVertically()) {
      HfTokenCard(vm, context)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.SERVER_CONFIG), enter = expandVertically(), exit = shrinkVertically()) {
      ServerConfigCard(vm, context)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.AUTO_LAUNCH), enter = expandVertically(), exit = shrinkVertically()) {
      AutoLaunchCard(vm, downloadedModelNames)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.CONTEXT_MANAGEMENT), enter = expandVertically(), exit = shrinkVertically()) {
      ContextManagementCard(vm)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.METRICS), enter = expandVertically(), exit = shrinkVertically()) {
      MetricsCard(vm)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.LOG_PERSISTENCE), enter = expandVertically(), exit = shrinkVertically()) {
      LogPersistenceCard(vm)
    }

    // Dialogs (conditionally rendered based on ViewModel state)
    SettingsDialogs(
      vm = vm,
      context = context,
      serverStatus = serverStatus,
      onRestartServer = onRestartServer,
      onStopServer = onStopServer,
      onNavigateToModels = onNavigateToModels,
      onBackClick = onBackClick,
      performSave = performSave,
      forceSave = forceSave,
    )

    AnimatedVisibility(visible = vm.cardVisible(CardId.HOME_ASSISTANT), enter = expandVertically(), exit = shrinkVertically()) {
      HomeAssistantCard(vm, context)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.UPDATES), enter = expandVertically(), exit = shrinkVertically()) {
      UpdatesCard(vm, context)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.ADVANCED), enter = expandVertically(), exit = shrinkVertically()) {
      AdvancedCard(vm)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.DEVELOPER), enter = expandVertically(), exit = shrinkVertically()) {
      DeveloperCard(vm, context)
    }
    AnimatedVisibility(visible = vm.cardVisible(CardId.RESET), enter = expandVertically(), exit = shrinkVertically()) {
      ResetCard(vm)
    }

    // Footer
    SettingsFooter(vm, context)

    // Version footer
    Spacer(modifier = Modifier.height(4.dp))
  }

    // Unsaved changes banner
    AnimatedVisibility(
      visible = vm.hasUnsavedChanges,
      enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
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
