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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.LogLevel
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.common.OlliteSearchBar
import com.ollitert.llm.server.ui.common.SCREEN_CONTENT_MAX_WIDTH
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.server.logs.InternalEventCard
import com.ollitert.llm.server.ui.server.logs.LogEntryCard
import com.ollitert.llm.server.ui.server.logs.SegmentItem
import com.ollitert.llm.server.ui.server.logs.SegmentedToggleGroup
import com.ollitert.llm.server.ui.server.logs.StatusRange
import com.ollitert.llm.server.ui.server.logs.copyAllLogsToClipboard
import com.ollitert.llm.server.ui.server.logs.exportLogsAsJson
import com.ollitert.llm.server.ui.theme.OlliteRTCancelledAmber
import com.ollitert.llm.server.ui.theme.OlliteRTContextOverflowRed
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTThinkingGrey
import com.ollitert.llm.server.ui.theme.OlliteRTWarningYellow
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.launch

internal val EventColor = OlliteRTPrimary
internal val ThinkingColor = OlliteRTThinkingGrey
internal val CancelledColor = OlliteRTCancelledAmber
internal val WarningColor = OlliteRTWarningYellow
internal val TruncatedColor = OlliteRTCancelledAmber
internal val ContextOverflowColor = OlliteRTContextOverflowRed

@Composable
fun LogsScreen(
  modifier: Modifier = Modifier,
  viewModel: LogsViewModel = hiltViewModel(),
) {
  val entries by viewModel.entries.collectAsStateWithLifecycle()
  val displayedEntries by viewModel.displayedEntries.collectAsStateWithLifecycle()
  val filter by viewModel.filter.collectAsStateWithLifecycle()
  val searchDraft by viewModel.searchDraft.collectAsStateWithLifecycle()
  val searchBarVisible by viewModel.searchBarVisible.collectAsStateWithLifecycle()
  val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
  val showClearConfirmDialog by viewModel.showClearConfirmDialog.collectAsStateWithLifecycle()
  val showClearActiveDialog by viewModel.showClearActiveDialog.collectAsStateWithLifecycle()

  val context = LocalContext.current
  val autoExpand = remember { viewModel.autoExpand }
  val wrapLogText = remember { viewModel.wrapLogText }
  val scope = rememberCoroutineScope()

  // Clear logs confirmation dialog
  if (showClearConfirmDialog) {
    val totalCount = entries.size
    val filteredCount = displayedEntries.size
    val isFiltered = filter.isActive && filteredCount != totalCount
    AlertDialog(
      onDismissRequest = { viewModel.setShowClearConfirmDialog(false) },
      title = {
        Text(
          text = stringResource(R.string.logs_dialog_clear_title),
          style = MaterialTheme.typography.titleMedium,
        )
      },
      text = {
        Text(
          text = if (isFiltered) {
            stringResource(R.string.logs_dialog_clear_body_filtered, totalCount, filteredCount)
          } else {
            stringResource(R.string.logs_dialog_clear_body, totalCount)
          },
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.clearLogs()
            viewModel.clearAllFilters()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
          ),
        ) {
          Text(stringResource(R.string.logs_dialog_clear_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.setShowClearConfirmDialog(false) }) {
          Text(stringResource(R.string.logs_dialog_clear_cancel))
        }
      },
    )
  }

  // Clear logs with active generation dialog (Cancel | Yes | Stop)
  if (showClearActiveDialog) {
    val pendingCount = entries.count { it.isPending }
    AlertDialog(
      onDismissRequest = { viewModel.setShowClearActiveDialog(false) },
      title = {
        Text(
          text = stringResource(R.string.logs_dialog_clear_active_title),
          style = MaterialTheme.typography.titleMedium,
        )
      },
      text = {
        Text(
          text = pluralStringResource(R.plurals.logs_dialog_clear_active_body, pendingCount, pendingCount),
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
            onClick = {
              viewModel.clearLogs()
              viewModel.clearAllFilters()
              viewModel.setShowClearActiveDialog(false)
            },
          ) {
            Text(stringResource(R.string.logs_dialog_clear_active_clear))
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                RequestLogStore.cancelAllPending()
                viewModel.clearLogs()
                viewModel.clearAllFilters()
                viewModel.setShowClearActiveDialog(false)
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
              ),
            ) {
              Text(stringResource(R.string.logs_dialog_clear_active_stop))
            }
            TextButton(onClick = { viewModel.setShowClearActiveDialog(false) }) {
              Text(stringResource(R.string.logs_dialog_clear_cancel))
            }
          }
        }
      },
    )
  }

  // Centered container with max width for tablets
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(modifier = Modifier.widthIn(max = SCREEN_CONTENT_MAX_WIDTH).fillMaxWidth()) {
      // ── Header row ────────────────────────────────────────────────────────
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = stringResource(R.string.logs_header_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          modifier = Modifier.weight(1f),
        )
        if (entries.isNotEmpty()) {
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Clear all logs (with optional confirmation)
            TooltipIconButton(
              icon = Icons.Outlined.DeleteSweep,
              tooltip = stringResource(R.string.logs_tooltip_clear_all),
              onClick = {
                if (entries.any { it.isPending }) {
                  viewModel.setShowClearActiveDialog(true)
                } else if (ServerPrefs.isConfirmClearLogs(context)) {
                  viewModel.setShowClearConfirmDialog(true)
                } else {
                  viewModel.clearLogs()
                  viewModel.clearAllFilters()
                }
              },
              tint = OlliteRTDeleteRed,
              backgroundColor = OlliteRTDeleteRed.copy(alpha = 0.12f),
            )
            // Copy visible logs (filtered if active) as JSON
            TooltipIconButton(
              icon = Icons.Outlined.ContentCopy,
              tooltip = if (filter.isActive) stringResource(R.string.logs_tooltip_copy_filtered_json) else stringResource(R.string.logs_tooltip_copy_all_json),
              onClick = { scope.launch { copyAllLogsToClipboard(context, displayedEntries) } },
              tint = OlliteRTPrimary,
            )
            // Export visible logs as JSON file
            TooltipIconButton(
              icon = Icons.Outlined.Share,
              tooltip = if (filter.isActive) stringResource(R.string.logs_tooltip_export_filtered_json) else stringResource(R.string.logs_tooltip_export_all_json),
              onClick = { scope.launch { exportLogsAsJson(context, displayedEntries) } },
              tint = OlliteRTPrimary,
            )
            // Search toggle
            TooltipIconButton(
              icon = if (searchBarVisible) Icons.Outlined.Close else Icons.Outlined.Search,
              tooltip = if (searchBarVisible) stringResource(R.string.logs_tooltip_close_search) else stringResource(R.string.logs_tooltip_search),
              onClick = {
                viewModel.toggleSearchBar()
                if (searchBarVisible) viewModel.clearSearch()
              },
              tint = if (filter.query.isNotEmpty()) OlliteRTPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
              backgroundColor = if (filter.query.isNotEmpty()) OlliteRTPrimary.copy(alpha = 0.15f)
              else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
          }
        }
      }

      // ── Search bar (animated) ─────────────────────────────────────────────
      AnimatedVisibility(
        visible = searchBarVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
      ) {
        val focusRequester = remember { FocusRequester() }

        OlliteSearchBar(
          query = searchDraft,
          onQueryChange = { viewModel.setSearchDraft(it) },
          placeholderRes = R.string.logs_search_placeholder,
          clearContentDescriptionRes = R.string.logs_search_clear_cd,
          modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 4.dp)
            .focusRequester(focusRequester),
          onClear = { viewModel.clearSearch() },
          onSearchAction = { viewModel.commitSearch() },
        )

        LaunchedEffect(Unit) {
          focusRequester.requestFocus()
        }
      }

      // ── Filter segmented groups ─────────────────────────────────────────
      if (entries.isNotEmpty()) {
        BoxWithConstraints(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
          val textMeasurer = rememberTextMeasurer()
          val labelStyle = MaterialTheme.typography.labelSmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontWeight = FontWeight.Medium,
          )
          val allLabels = listOf("POST", "GET", "EVENT", "2xx", "4xx", "5xx", "ERROR", "WARN")
          val density = LocalDensity.current
          val itemPaddingPx = with(density) { 16.dp.toPx() }
          val gapsPx = with(density) { 12.dp.toPx() }
          val availableWidthPx = with(density) { maxWidth.toPx() }
          val needsScroll = remember(availableWidthPx, labelStyle) {
            val labelWidths = allLabels.map { label ->
              textMeasurer.measure(label, labelStyle).size.width + itemPaddingPx
            }
            val group1 = labelWidths.subList(0, 3).sum()
            val group2 = labelWidths.subList(3, 6).sum()
            val group3 = labelWidths.subList(6, 8).sum()
            (group1 + group2 + group3 + gapsPx) > availableWidthPx
          }

          if (needsScroll) {
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              SegmentedToggleGroup(segmentCount = 3) { segmentShape ->
                SegmentItem("POST", "POST" in filter.methods, onClick = { viewModel.toggleMethod("POST") }, shape = segmentShape(0))
                SegmentItem("GET", "GET" in filter.methods, onClick = { viewModel.toggleMethod("GET") }, shape = segmentShape(1))
                SegmentItem("EVENT", "EVENT" in filter.methods, onClick = { viewModel.toggleMethod("EVENT") }, shape = segmentShape(2))
              }
              SegmentedToggleGroup(segmentCount = StatusRange.entries.size) { segmentShape ->
                StatusRange.entries.forEachIndexed { index, range ->
                  SegmentItem(range.label, range in filter.statusRanges, onClick = { viewModel.toggleStatusRange(range) }, shape = segmentShape(index))
                }
              }
              SegmentedToggleGroup(segmentCount = 2) { segmentShape ->
                SegmentItem("ERROR", LogLevel.ERROR in filter.levels, onClick = { viewModel.toggleLevel(LogLevel.ERROR) }, shape = segmentShape(0), accentColor = OlliteRTDeleteRed)
                SegmentItem("WARN", LogLevel.WARNING in filter.levels, onClick = { viewModel.toggleLevel(LogLevel.WARNING) }, shape = segmentShape(1), accentColor = WarningColor)
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              SegmentedToggleGroup(segmentCount = 3, modifier = Modifier.weight(3f)) { segmentShape ->
                SegmentItem("POST", "POST" in filter.methods, onClick = { viewModel.toggleMethod("POST") }, modifier = Modifier.weight(1f), shape = segmentShape(0))
                SegmentItem("GET", "GET" in filter.methods, onClick = { viewModel.toggleMethod("GET") }, modifier = Modifier.weight(1f), shape = segmentShape(1))
                SegmentItem("EVENT", "EVENT" in filter.methods, onClick = { viewModel.toggleMethod("EVENT") }, modifier = Modifier.weight(1f), shape = segmentShape(2))
              }
              SegmentedToggleGroup(segmentCount = StatusRange.entries.size, modifier = Modifier.weight(3f)) { segmentShape ->
                StatusRange.entries.forEachIndexed { index, range ->
                  SegmentItem(range.label, range in filter.statusRanges, onClick = { viewModel.toggleStatusRange(range) }, modifier = Modifier.weight(1f), shape = segmentShape(index))
                }
              }
              SegmentedToggleGroup(segmentCount = 2, modifier = Modifier.weight(2f)) { segmentShape ->
                SegmentItem("ERROR", LogLevel.ERROR in filter.levels, onClick = { viewModel.toggleLevel(LogLevel.ERROR) }, modifier = Modifier.weight(1f), shape = segmentShape(0), accentColor = OlliteRTDeleteRed)
                SegmentItem("WARN", LogLevel.WARNING in filter.levels, onClick = { viewModel.toggleLevel(LogLevel.WARNING) }, modifier = Modifier.weight(1f), shape = segmentShape(1), accentColor = WarningColor)
              }
            }
          }
        }
      }

      // ── Result count banner ───────────────────────────────────────────────
      if (filter.isActive && entries.isNotEmpty()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (isSearching) {
            CircularProgressIndicator(
              modifier = Modifier.size(14.dp),
              strokeWidth = 2.dp,
              color = OlliteRTPrimary,
            )
            Text(
              text = stringResource(R.string.logs_searching),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Text(
              text = stringResource(R.string.logs_showing_count, displayedEntries.size, entries.size),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.weight(1f))
          TextButton(
            onClick = { viewModel.clearAllFilters() },
            modifier = Modifier.height(28.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
          ) {
            Text(
              text = stringResource(R.string.logs_clear_filters),
              style = MaterialTheme.typography.labelSmall,
              color = OlliteRTPrimary,
            )
          }
        }
      }

      // ── Log list / empty states ───────────────────────────────────────────
      if (entries.isEmpty()) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = stringResource(R.string.logs_empty_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = stringResource(R.string.logs_empty_body),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else if (displayedEntries.isEmpty() && filter.isActive) {
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = stringResource(R.string.logs_no_match_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = stringResource(R.string.logs_no_match_body),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = { viewModel.clearAllFilters() }) {
              Text(stringResource(R.string.logs_clear_filters), color = OlliteRTPrimary)
            }
          }
        }
      } else if (displayedEntries.isNotEmpty()) {
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        var unseenCount by remember { mutableIntStateOf(0) }
        val newestRawEntryId = entries.firstOrNull()?.id
        var isProgrammaticScroll by remember { mutableStateOf(false) }
        var autoScrollEnabled by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
          snapshotFlow {
            listState.isScrollInProgress to listState.firstVisibleItemIndex
          }.collect { (scrolling, firstIndex) ->
            if (scrolling && firstIndex > 0 && !isProgrammaticScroll) {
              autoScrollEnabled = false
            }
            if (firstIndex == 0 && !scrolling) {
              unseenCount = 0
              autoScrollEnabled = true
            }
          }
        }

        LaunchedEffect(newestRawEntryId) {
          if (newestRawEntryId != null && !filter.isActive) {
            if (autoScrollEnabled) {
              isProgrammaticScroll = true
              try {
                listState.animateScrollToItem(0)
              } finally {
                isProgrammaticScroll = false
              }
            } else {
              unseenCount++
            }
          }
        }

        LaunchedEffect(filter) {
          isProgrammaticScroll = true
          try {
            listState.scrollToItem(0)
          } finally {
            isProgrammaticScroll = false
          }
          unseenCount = 0
          autoScrollEnabled = true
        }

        Box(modifier = Modifier.fillMaxSize()) {
          LazyColumn(
            state = listState,
            modifier = Modifier
              .fillMaxSize()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(
              displayedEntries,
              key = { it.id },
              contentType = { if (it.method == "EVENT") "event" else "request" },
            ) { entry ->
              if (entry.method == "EVENT") {
                InternalEventCard(entry, searchQuery = filter.query)
              } else {
                LogEntryCard(entry, autoExpand = autoExpand, searchQuery = filter.query, wrapText = wrapLogText)
              }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
          }

          androidx.compose.animation.AnimatedVisibility(
            visible = unseenCount > 0 && !filter.isActive,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 8.dp)
              .zIndex(1f),
          ) {
            Row(
              modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(OlliteRTPrimary)
                .clickable {
                  unseenCount = 0
                  autoScrollEnabled = true
                  coroutineScope.launch {
                    isProgrammaticScroll = true
                    try {
                      listState.animateScrollToItem(0)
                    } finally {
                      isProgrammaticScroll = false
                    }
                  }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = pluralStringResource(R.plurals.logs_new_activity, unseenCount, unseenCount),
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
      }
    }
  }
}
