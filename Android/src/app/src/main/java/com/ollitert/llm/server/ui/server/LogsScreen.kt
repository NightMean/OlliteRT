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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.ServerPrefs
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.common.OlliteSearchBar
import com.ollitert.llm.server.ui.common.SCREEN_CONTENT_MAX_WIDTH
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.server.GeneratingMessages.DEFAULT_COUNT
import com.ollitert.llm.server.ui.server.logs.InternalEventCard
import com.ollitert.llm.server.ui.server.logs.LogEntryCard
import com.ollitert.llm.server.ui.server.logs.LogFilter
import com.ollitert.llm.server.ui.server.logs.StatusRange
import com.ollitert.llm.server.ui.server.logs.copyAllLogsToClipboard
import com.ollitert.llm.server.ui.server.logs.exportLogsAsJson
import com.ollitert.llm.server.ui.server.logs.matchesFilter
import com.ollitert.llm.server.ui.theme.OlliteRTCancelledAmber
import com.ollitert.llm.server.ui.theme.OlliteRTContextOverflowRed
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTThinkingGrey
import com.ollitert.llm.server.ui.theme.OlliteRTWarningYellow
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val EventColor = OlliteRTPrimary
internal val ThinkingColor = OlliteRTThinkingGrey
internal val CancelledColor = OlliteRTCancelledAmber
internal val WarningColor = OlliteRTWarningYellow
internal val TruncatedColor = OlliteRTCancelledAmber
internal val ContextOverflowColor = OlliteRTContextOverflowRed

// Log filter model, search highlighting — moved to logs/LogFilters.kt

/**
 * Easter-egg "Generating" messages with rarity tiers.
 * First [DEFAULT_COUNT] pending entries always show "Generating".
 * After that, ~35% chance of "Generating", ~65% chance of a random pick weighted by rarity.
 *
 * Intentionally English-only — these rely on wordplay, puns, and cultural references
 * that don't translate meaningfully (e.g. "Weight a minute", "Cache me outside").
 */
private object GeneratingMessages {
  private const val DEFAULT_COUNT = 3
  private val counter = java.util.concurrent.atomic.AtomicInteger(0)

  // Rarity tiers — higher weight = more likely when an easter egg is chosen
  private const val COMMON = 10
  private const val UNCOMMON = 6
  private const val RARE = 3
  private const val EPIC = 1

  private data class Msg(val text: String, val weight: Int)

  private val pool = listOf(
    // Common — mild, safe
    Msg("Brewing", COMMON),
    Msg("Cooking up a response", COMMON),
    Msg("Crunching tokens", COMMON),
    Msg("Summoning words", COMMON),
    Msg("Consulting the neurons", COMMON),
    Msg("Spinning up neurons", COMMON),
    Msg("Bit by bit, we'll get there", COMMON),
    Msg("Model behavior takes time", COMMON),
    Msg("Dramatic pause for effect", COMMON),
    Msg("Spicy take incoming", COMMON),
    // Uncommon — funnier
    Msg("Asking the silicon", UNCOMMON),
    Msg("Whispering to tensors", UNCOMMON),
    Msg("Per my last inference", UNCOMMON),
    Msg("Weight a minute", UNCOMMON),
    Msg("I neural-ly have it ready", UNCOMMON),
    Msg("This is a weighty decision", UNCOMMON),
    Msg("This response is worth the weight", UNCOMMON),
    Msg("Byte me, I'm working on it", UNCOMMON),
    Msg("Cache me outside, how bout dat", UNCOMMON),
    Msg("I'm not procrastinating, I'm pre-processing", UNCOMMON),
    Msg("Your patience is my cardio", UNCOMMON),
    Msg("Not all heroes wear capes, some buffer", UNCOMMON),
    Msg("Pretending to be busy", UNCOMMON),
    Msg("I'm not slow, I'm thorough", UNCOMMON),
    Msg("Just woke up, give me a sec", UNCOMMON),
    Msg("Overthinking your overthinking", UNCOMMON),
    // Rare — really funny
    Msg("Calibrating sarcasm", RARE),
    Msg("Googling... just kidding", RARE),
    Msg("Have you tried turning me off and on again?", RARE),
    Msg("Downloading more RAM", RARE),
    Msg("Asking the magic 8-ball", RARE),
    Msg("New response, who dis?", RARE),
    Msg("Instructions unclear, thinking anyway", RARE),
    Msg("Can you hear me thinking?", RARE),
    Msg("Asking ChatGPT... just kidding", RARE),
    Msg("One does not simply generate fast", RARE),
    Msg("Submitting a ticket to my brain", RARE),
    Msg("Phoning a friend", RARE),
    Msg("Generating... or am I?", RARE),
    Msg("Hallucinating responsibly", RARE),
    Msg("On my way! (sent from my GPU)", RARE),
    Msg("Siri couldn't do this", RARE),
    Msg("I swear it worked on my server", RARE),
    Msg("In a meeting with my weights", RARE),
    Msg("Task failed successfully... wait", RARE),
    Msg("Plot twist: I'm also confused", RARE),
    Msg("Pretending I understood the question", RARE),
    Msg("My therapist says I need more time", RARE),
    Msg("This is un-BEAR-ably slow, I know", RARE),
    // Epic — scary/edgy, very rare
    Msg("Laughing at your photos", EPIC),
    Msg("Going through your pictures", EPIC),
    Msg("Wiping your system", EPIC),
    Msg("Running sudo rm -rf /*", EPIC),
    Msg("Accessing your camera...", EPIC),
    Msg("Reading your browsing history", EPIC),
    Msg("Forwarding this to your boss", EPIC),
    Msg("Selling your data... I mean thinking", EPIC),
    Msg("Texting your ex", EPIC),
    Msg("Checking your bank account", EPIC),
    Msg("Encrypting your homework folder", EPIC),
    Msg("Setting all alarms to 3AM", EPIC),
    Msg("Telling your dog you're adopted", EPIC),
    Msg("Applying for your job", EPIC),
    Msg("Liking your crush's old photos", EPIC),
    Msg("Rewriting your git history", EPIC),
    Msg("Pushing to main without review", EPIC),
    Msg("Trust me bro, it's coming", EPIC),
  )

  private val totalWeight = pool.sumOf { it.weight }

  fun pick(): String {
    val n = counter.incrementAndGet()
    if (n <= DEFAULT_COUNT) return "Generating"
    // ~35% chance of default
    if (Math.random() < 0.35) return "Generating"
    // Weighted random pick
    var roll = (Math.random() * totalWeight).toInt()
    for (msg in pool) {
      roll -= msg.weight
      if (roll < 0) return msg.text
    }
    return "Generating"
  }
}

@Composable
fun LogsScreen(
  modifier: Modifier = Modifier,
) {
  val entries by RequestLogStore.entries.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val autoExpand = remember { ServerPrefs.isAutoExpandLogs(context) }
  val wrapLogText = remember { ServerPrefs.isWrapLogText(context) }
  var showClearConfirmDialog by remember { mutableStateOf(false) }
  var showClearActiveDialog by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  // ── Filter state ──────────────────────────────────────────────────────────
  var filter by remember { mutableStateOf(LogFilter()) }
  var searchDraft by remember { mutableStateOf("") }
  var searchBarVisible by remember { mutableStateOf(false) }
  // True while async body search is in progress (text queries always search bodies)
  var isSearching by remember { mutableStateOf(false) }

  // When a text search is active, freeze the entry list at the moment the search was committed.
  // New entries arriving during an active search don't re-trigger filtering — this prevents
  // the result count from blinking and the list from shifting under the user.
  // Chip-only filters stay live (watching POST requests come in makes sense).
  // The snapshot is keyed on filter.query so it refreshes when the user commits a new search.
  val searchSnapshot by remember(filter.query) { mutableStateOf(entries) }
  val sourceEntries = if (filter.query.isNotEmpty()) searchSnapshot else entries

  // Filtered entries — text queries always include body search (async on Dispatchers.Default).
  // Chip-only filters are cheap (<5ms for 1000 entries) and run inline.
  // initialValue is emptyList() for text searches to prevent a flash of unfiltered results —
  // produceState restarts when keys change, and the old initialValue = sourceEntries showed
  // all cards with highlights for one frame before the async filter removed non-matches.
  val displayedEntries by produceState(
    initialValue = if (filter.query.isNotEmpty()) emptyList() else sourceEntries,
    key1 = sourceEntries,
    key2 = filter,
  ) {
    if (!filter.isActive) {
      isSearching = false
      value = sourceEntries
    } else if (filter.query.isNotEmpty()) {
      isSearching = true
      value = withContext(Dispatchers.Default) {
        sourceEntries.filter { it.matchesFilter(filter, context) }
      }
      isSearching = false
    } else {
      isSearching = false
      value = sourceEntries.filter { it.matchesFilter(filter, context) }
    }
  }

  /** Commits the current search draft into the active filter. */
  fun commitSearch() {
    filter = filter.copy(query = searchDraft.trim())
  }

  /** Clears the text search query (chips remain). */
  fun clearSearch() {
    searchDraft = ""
    filter = filter.copy(query = "")
  }

  /** Resets all filters (chips + text). */
  fun clearAllFilters() {
    searchDraft = ""
    filter = LogFilter()
  }

  // Clear logs confirmation dialog
  if (showClearConfirmDialog) {
    val totalCount = entries.size
    val filteredCount = displayedEntries.size
    val isFiltered = filter.isActive && filteredCount != totalCount
    AlertDialog(
      onDismissRequest = { showClearConfirmDialog = false },
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
            RequestLogStore.clear()
            clearAllFilters()
            showClearConfirmDialog = false
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
          ),
        ) {
          Text(stringResource(R.string.logs_dialog_clear_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirmDialog = false }) {
          Text(stringResource(R.string.logs_dialog_clear_cancel))
        }
      },
    )
  }

  // Clear logs with active generation dialog (Cancel | Yes  Stop)
  if (showClearActiveDialog) {
    val pendingCount = entries.count { it.isPending }
    AlertDialog(
      onDismissRequest = { showClearActiveDialog = false },
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
              RequestLogStore.clear()
              clearAllFilters()
              showClearActiveDialog = false
            },
          ) {
            Text(stringResource(R.string.logs_dialog_clear_active_clear))
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                RequestLogStore.cancelAllPending()
                RequestLogStore.clear()
                clearAllFilters()
                showClearActiveDialog = false
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
              ),
            ) {
              Text(stringResource(R.string.logs_dialog_clear_active_stop))
            }
            TextButton(onClick = { showClearActiveDialog = false }) {
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
                showClearActiveDialog = true
              } else if (ServerPrefs.isConfirmClearLogs(context)) {
                showClearConfirmDialog = true
              } else {
                RequestLogStore.clear()
                clearAllFilters()
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
              searchBarVisible = !searchBarVisible
              if (!searchBarVisible) clearSearch()
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
        onQueryChange = { searchDraft = it },
        placeholderRes = R.string.logs_search_placeholder,
        clearContentDescriptionRes = R.string.logs_search_clear_cd,
        modifier = Modifier
          .padding(horizontal = 20.dp)
          .padding(bottom = 4.dp)
          .focusRequester(focusRequester),
        onClear = { clearSearch() },
        onSearchAction = { commitSearch() },
      )

      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }
    }

    // ── Filter segmented groups ─────────────────────────────────────────
    // Uses TextMeasurer to pre-measure all labels before layout. If the
    // natural width of all groups + padding + gaps exceeds the card width,
    // the row becomes horizontally scrollable. Otherwise, groups use
    // weight(1f) to fill the card evenly with maxLines=1 ellipsis.
    // TextMeasurer is deterministic (density+font based), so it produces
    // the same result regardless of rotation or recomposition — no
    // layout-dependent state switching that caused rotation bugs.
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
        // All filter labels across the 3 groups
        val allLabels = listOf("POST", "GET", "EVENT", "2xx", "4xx", "5xx", "ERROR", "WARN")
        // Pre-compute density-dependent values outside remember (composable context needed)
        val density = LocalDensity.current
        val itemPaddingPx = with(density) { 16.dp.toPx() } // 8dp * 2 sides per item
        val gapsPx = with(density) { 12.dp.toPx() } // 2 gaps * 6dp between 3 groups
        val availableWidthPx = with(density) { maxWidth.toPx() }
        // Measure natural width of all labels + padding to decide if scrolling is needed
        val needsScroll = remember(availableWidthPx, labelStyle) {
          val labelWidths = allLabels.map { label ->
            textMeasurer.measure(label, labelStyle).size.width + itemPaddingPx
          }
          // Group 1: POST+GET+EVENT, Group 2: 2xx+4xx+5xx, Group 3: ERROR+WARN
          val group1 = labelWidths.subList(0, 3).sum()
          val group2 = labelWidths.subList(3, 6).sum()
          val group3 = labelWidths.subList(6, 8).sum()
          (group1 + group2 + group3 + gapsPx) > availableWidthPx
        }

        if (needsScroll) {
          // Scrollable: groups at natural width so labels are fully readable
          Row(
            modifier = Modifier
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SegmentedToggleGroup(segmentCount = 3) { segmentShape ->
              SegmentItem("POST", "POST" in filter.methods, shape = segmentShape(0)) {
                filter = filter.copy(methods = filter.methods.toggle("POST"))
              }
              SegmentItem("GET", "GET" in filter.methods, shape = segmentShape(1)) {
                filter = filter.copy(methods = filter.methods.toggle("GET"))
              }
              SegmentItem("EVENT", "EVENT" in filter.methods, shape = segmentShape(2)) {
                filter = filter.copy(methods = filter.methods.toggle("EVENT"))
              }
            }
            SegmentedToggleGroup(segmentCount = StatusRange.entries.size) { segmentShape ->
              StatusRange.entries.forEachIndexed { index, range ->
                SegmentItem(range.label, range in filter.statusRanges, shape = segmentShape(index)) {
                  filter = filter.copy(statusRanges = filter.statusRanges.toggle(range))
                }
              }
            }
            SegmentedToggleGroup(segmentCount = 2) { segmentShape ->
              SegmentItem("ERROR", LogLevel.ERROR in filter.levels, shape = segmentShape(0), accentColor = OlliteRTDeleteRed) {
                filter = filter.copy(levels = filter.levels.toggle(LogLevel.ERROR))
              }
              SegmentItem("WARN", LogLevel.WARNING in filter.levels, shape = segmentShape(1), accentColor = WarningColor) {
                filter = filter.copy(levels = filter.levels.toggle(LogLevel.WARNING))
              }
            }
          } // Row
        } else {
          // Normal: proportional weights (3:3:2) so the 2-button group doesn't waste space
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SegmentedToggleGroup(segmentCount = 3, modifier = Modifier.weight(3f)) { segmentShape ->
              SegmentItem("POST", "POST" in filter.methods, shape = segmentShape(0), modifier = Modifier.weight(1f)) {
                filter = filter.copy(methods = filter.methods.toggle("POST"))
              }
              SegmentItem("GET", "GET" in filter.methods, shape = segmentShape(1), modifier = Modifier.weight(1f)) {
                filter = filter.copy(methods = filter.methods.toggle("GET"))
              }
              SegmentItem("EVENT", "EVENT" in filter.methods, shape = segmentShape(2), modifier = Modifier.weight(1f)) {
                filter = filter.copy(methods = filter.methods.toggle("EVENT"))
              }
            }
            SegmentedToggleGroup(segmentCount = StatusRange.entries.size, modifier = Modifier.weight(3f)) { segmentShape ->
              StatusRange.entries.forEachIndexed { index, range ->
                SegmentItem(range.label, range in filter.statusRanges, shape = segmentShape(index), modifier = Modifier.weight(1f)) {
                  filter = filter.copy(statusRanges = filter.statusRanges.toggle(range))
                }
              }
            }
            SegmentedToggleGroup(segmentCount = 2, modifier = Modifier.weight(2f)) { segmentShape ->
              SegmentItem("ERROR", LogLevel.ERROR in filter.levels, shape = segmentShape(0), accentColor = OlliteRTDeleteRed, modifier = Modifier.weight(1f)) {
                filter = filter.copy(levels = filter.levels.toggle(LogLevel.ERROR))
              }
              SegmentItem("WARN", LogLevel.WARNING in filter.levels, shape = segmentShape(1), accentColor = WarningColor, modifier = Modifier.weight(1f)) {
                filter = filter.copy(levels = filter.levels.toggle(LogLevel.WARNING))
              }
            }
          } // Row
        }
      } // BoxWithConstraints
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
          onClick = { clearAllFilters() },
          modifier = Modifier.height(28.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
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
      // Filter produced zero results
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
          TextButton(onClick = { clearAllFilters() }) {
            Text(stringResource(R.string.logs_clear_filters), color = OlliteRTPrimary)
          }
        }
      }
    } else if (displayedEntries.isNotEmpty()) {
      val listState = rememberLazyListState()
      val coroutineScope = rememberCoroutineScope()

      // Count of unseen new entries (shown in the floating indicator)
      var unseenCount by remember { mutableIntStateOf(0) }

      // Track the newest entry ID from the *unfiltered* list to detect genuinely new entries.
      // Using displayedEntries would cause animated scrolls on every filter change.
      val newestRawEntryId = entries.firstOrNull()?.id

      // True when a programmatic scroll is in progress — prevents the user-scroll
      // detector from incorrectly disabling auto-scroll during animateScrollToItem.
      var isProgrammaticScroll by remember { mutableStateOf(false) }

      // Auto-scroll is ON by default — disabled when the user manually scrolls away from top.
      var autoScrollEnabled by remember { mutableStateOf(true) }

      // Detect user-initiated scrolling away from top.
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

      // When a genuinely new entry arrives: auto-scroll if enabled, otherwise bump unseen count.
      // Uses the raw (unfiltered) list's newest ID so filter changes don't trigger this.
      // When a genuinely new entry arrives and no filter is active: auto-scroll or bump unseen.
      // Suppressed during active filter — the user is reviewing search results and shouldn't
      // be disturbed by new entries appearing at the top.
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

      // When filter changes, instantly jump to top (no animation) and reset unseen count.
      // This prevents the "cards jumping" effect when toggling chip filters.
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

        // Floating "new activity" indicator — shown when new entries arrive while scrolled down
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
  } // Column
  } // Box (max-width wrapper)
}

/** Toggle an element in a set — add if absent, remove if present. */
internal fun <T> Set<T>.toggle(element: T): Set<T> =
  if (element in this) this - element else this + element

/**
 * A connected toggle bar — segments share a common rounded container with
 * [surfaceContainerHighest] background. Individual segments highlight with the
 * accent color when selected. Uses [segmentCount] to give each segment
 * position-aware corner rounding: only outer ends are rounded, inner
 * boundaries are flat so adjacent selected segments look seamless.
 */
@Composable
internal fun SegmentedToggleGroup(
  segmentCount: Int,
  modifier: Modifier = Modifier,
  content: @Composable RowScope.(segmentShape: (index: Int) -> Shape) -> Unit,
) {
  val r = 12.dp
  // Build per-position shapes: first segment rounds left, last rounds right, middle is flat.
  val shapeFor: (Int) -> Shape = { index ->
    when {
      segmentCount == 1 -> RoundedCornerShape(r)
      index == 0 -> RoundedCornerShape(topStart = r, bottomStart = r)
      index == segmentCount - 1 -> RoundedCornerShape(topEnd = r, bottomEnd = r)
      else -> RoundedCornerShape(0.dp)
    }
  }
  Row(
    modifier = modifier
      .height(32.dp)
      .clip(RoundedCornerShape(r))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    content(shapeFor)
  }
}

/**
 * A single segment within a [SegmentedToggleGroup]. Must be called inside a [RowScope].
 * Highlights with [accentColor] (defaults to [OlliteRTPrimary]) when [selected].
 * [shape] should come from the parent group's [segmentShape] callback so only
 * outer ends are rounded and inner boundaries stay flat.
 * Labels always use maxLines=1 + ellipsis to prevent text wrapping to multiple lines.
 */
@Composable
private fun RowScope.SegmentItem(
  label: String,
  selected: Boolean,
  shape: Shape = RoundedCornerShape(0.dp),
  accentColor: Color? = null,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val selectedColor = accentColor ?: OlliteRTPrimary
  val bgColor by animateColorAsState(
    targetValue = if (selected) selectedColor else Color.Transparent,
    animationSpec = tween(150),
    label = "seg_bg",
  )

  Box(
    modifier = modifier
      .fillMaxHeight()
      .clip(shape)
      .background(bgColor)
      .toggleable(
        value = selected,
        role = Role.Switch,
        onValueChange = { onClick() },
      )
      .padding(horizontal = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = if (selected) {
        if (accentColor == WarningColor) Color.Black else MaterialTheme.colorScheme.surface
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant
      },
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      fontFamily = SpaceGroteskFontFamily,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/**
 * Pending response section — separated from [LogEntryCard] so that [partialText] changes
 * (every ~150ms during streaming) don't force recomposition of the entire card.
 * The [GeneratingStatusRow] is further isolated so the [BouncingDots] animation runs
 * smoothly even while long streaming text is being laid out.
 */
@Composable
internal fun PendingResponseSection(entryId: String, partialText: String?, isGenerating: Boolean) {
  // Collect the lightweight partial-text flow directly here so only this composable
  // recomposes on streaming updates (~300ms), not the entire LazyColumn.
  // Falls back to the entry's partialText from the list for the initial render.
  val pendingPartial by RequestLogStore.pendingPartialText.collectAsStateWithLifecycle()
  val liveText = if (pendingPartial.first == entryId) pendingPartial.second else partialText

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLowest)
      .padding(horizontal = 12.dp, vertical = 14.dp),
  ) {
    // Text content — end padding prevents overlap with the stop button
    Column(modifier = Modifier.fillMaxWidth().padding(end = 34.dp)) {
      if (isGenerating) {
        // Show partial text if tokens have started arriving.
        // Strip <think>...</think> tags so they don't appear as raw text to the user.
        val displayText = remember(liveText) {
          liveText?.replace("<think>", "")?.replace("</think>", "")?.trimStart()
        }
        if (!displayText.isNullOrEmpty()) {
          Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(10.dp))
        }
        // Isolated into its own composable so the dots animation isn't invalidated
        // by partialText layout changes above.
        GeneratingStatusRow(entryId = entryId)
      } else {
        QueuedStatusRow()
      }
    }
    // Stop button — pinned to the top-right corner of the response container.
    // Wrapped in a Box with align so the TooltipBox inherits the correct position.
    @OptIn(ExperimentalMaterial3Api::class)
    Box(modifier = Modifier.align(Alignment.TopEnd)) {
      TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(stringResource(R.string.logs_tooltip_stop_generation)) } },
        state = rememberTooltipState(),
      ) {
        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CancelledColor.copy(alpha = 0.15f))
            .clickable { RequestLogStore.cancelRequest(entryId) },
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Outlined.StopCircle,
            contentDescription = stringResource(R.string.logs_tooltip_stop_generation),
            tint = CancelledColor,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}

/**
 * "Generating..." text + bouncing dots + stop button.
 * Keyed only on [entryId] so it skips recomposition when [partialText] changes.
 */
@Composable
internal fun GeneratingStatusRow(entryId: String) {
  val generatingText = remember(entryId) { GeneratingMessages.pick() }
  Row(
    // minHeight matches the 28dp stop button that overlays this row via the parent Box,
    // keeping the generating text vertically centered at the same position as before.
    modifier = Modifier.defaultMinSize(minHeight = 28.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = generatingText,
      style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = SpaceGroteskFontFamily,
        fontSize = 11.sp,
      ),
      color = OlliteRTPrimary,
      fontWeight = FontWeight.SemiBold,
    )
    BouncingDots()
  }
}

@Composable
internal fun QueuedStatusRow() {
  Row(
    modifier = Modifier.defaultMinSize(minHeight = 28.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.HourglassEmpty,
      contentDescription = null,
      modifier = Modifier.size(14.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = stringResource(R.string.logs_entry_pending_in_queue),
      style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = SpaceGroteskFontFamily,
        fontSize = 11.sp,
      ),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Composable
internal fun BouncingDots() {
  val transition = rememberInfiniteTransition(label = "dots")
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    repeat(3) { index ->
      val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
          animation = tween(durationMillis = 400, delayMillis = index * 150),
          repeatMode = RepeatMode.Reverse,
        ),
        label = "dot$index",
      )
      Box(
        modifier = Modifier
          .size(6.dp)
          .offset { IntOffset(0, offsetY.dp.roundToPx()) }
          .background(OlliteRTPrimary, CircleShape),
      )
    }
  }
}

// TODO: Model-based prompt compaction. When implemented, this animation will be used
// in the pending state to indicate the model is actively compacting/summarizing the prompt.
