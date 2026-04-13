package com.ollitert.llm.server.ui.server

import com.ollitert.llm.server.ui.server.logs.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import com.ollitert.llm.server.ui.common.SCREEN_CONTENT_MAX_WIDTH
import com.ollitert.llm.server.ui.common.TooltipIconButton
import com.ollitert.llm.server.ui.common.copyToClipboard
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
// TODO: Uncomment these imports when model-based compaction is implemented (CompactingIcon)
// import androidx.compose.foundation.Canvas
// import androidx.compose.ui.graphics.StrokeCap
// import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.service.LlmHttpErrorSuggestions
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.theme.OlliteRTDeleteRed
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val EventColor = OlliteRTPrimary // matches the app's blue accent
private val ThinkingColor = Color(0xFFB0B3BE) // muted grey — subtle indicator, not eye-catching
private val CancelledColor = Color(0xFFFFB74D) // amber for cancelled/stopped requests
internal val WarningColor = Color(0xFFFFF176) // yellow for warnings (e.g. compacted tool schemas)
internal val TruncatedColor = Color(0xFFFFB74D) // amber for history truncation
internal val ContextOverflowColor = Color(0xFFEF5350) // red for context window exceeded errors

// Log filter model, search highlighting — moved to logs/LogFilters.kt

/** Patterns that indicate a context window overflow error in the response body. */
private val CONTEXT_OVERFLOW_PATTERNS = listOf(
  "context", "token", "exceed", "too long", "too many tokens", ">=",
)

/**
 * Checks if a response body contains a context window overflow error.
 * Detects patterns like "1031 >= 1024", "exceeds context", "token limit exceeded", etc.
 * Only matches actual error responses — not success responses that happen to contain
 * metric field names like "errors_total" or "context_utilization_percent".
 */
private fun isContextOverflowError(responseBody: String?, statusCode: Int = 200): Boolean {
  if (responseBody.isNullOrBlank()) return false
  // Only check error responses (4xx/5xx) — successful responses can contain
  // metric field names like "errors_total" and "context_utilization_percent"
  // that would false-positive (e.g. /health?metrics=true).
  if (statusCode in 200..299) return false
  val lower = responseBody.lowercase()
  val hasError = lower.contains("error") || lower.contains("fail")
  val hasOverflow = CONTEXT_OVERFLOW_PATTERNS.any { lower.contains(it) }
  return hasError && hasOverflow
}

/**
 * Easter-egg "Generating" messages with rarity tiers.
 * First [DEFAULT_COUNT] pending entries always show "Generating".
 * After that, ~65% chance of "Generating", ~35% chance of a random pick weighted by rarity.
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
  val entries by RequestLogStore.entries.collectAsState()
  val context = LocalContext.current
  val autoExpand = remember { LlmHttpPrefs.isAutoExpandLogs(context) }
  var showClearConfirmDialog by remember { mutableStateOf(false) }
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
  val displayedEntries by produceState(
    initialValue = sourceEntries,
    key1 = sourceEntries,
    key2 = filter,
  ) {
    if (!filter.isActive) {
      isSearching = false
      value = sourceEntries
    } else if (filter.query.isNotEmpty()) {
      // Text search always includes bodies — run off main thread
      isSearching = true
      value = withContext(Dispatchers.Default) {
        sourceEntries.filter { it.matchesFilter(filter) }
      }
      isSearching = false
    } else {
      // Chip-only filters — cheap, run inline
      isSearching = false
      value = sourceEntries.filter { it.matchesFilter(filter) }
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
          text = "Clear All Logs",
          style = MaterialTheme.typography.titleMedium,
        )
      },
      text = {
        Text(
          text = if (isFiltered) {
            "This will delete all $totalCount log entries (not just the $filteredCount shown). This action cannot be undone."
          } else {
            "This will delete all $totalCount log entries. This action cannot be undone."
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
          Text("Clear")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirmDialog = false }) {
          Text("Cancel")
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
        text = "Activity Log",
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
            tooltip = "Clear all logs",
            onClick = {
              if (LlmHttpPrefs.isConfirmClearLogs(context)) {
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
            tooltip = if (filter.isActive) "Copy filtered logs (JSON)" else "Copy all logs (JSON)",
            onClick = { scope.launch { copyAllLogsToClipboard(context, displayedEntries) } },
            tint = OlliteRTPrimary,
          )
          // Export visible logs as JSON file
          TooltipIconButton(
            icon = Icons.Outlined.Share,
            tooltip = if (filter.isActive) "Export filtered logs as JSON" else "Export logs as JSON",
            onClick = { scope.launch { exportLogsAsJson(context, displayedEntries) } },
            tint = OlliteRTPrimary,
          )
          // Search toggle
          TooltipIconButton(
            icon = if (searchBarVisible) Icons.Outlined.Close else Icons.Outlined.Search,
            tooltip = if (searchBarVisible) "Close search" else "Search logs",
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

      OutlinedTextField(
        value = searchDraft,
        onValueChange = { searchDraft = it },
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 8.dp)
          .focusRequester(focusRequester),
        placeholder = {
          Text("Search logs...", style = MaterialTheme.typography.bodyLarge)
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        trailingIcon = {
          if (searchDraft.isNotEmpty()) {
            IconButton(onClick = { clearSearch() }) {
              Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Clear search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { commitSearch() }),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          focusedBorderColor = OlliteRTPrimary,
          unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
          cursorColor = OlliteRTPrimary,
        ),
      )

      // Request focus when search bar opens
      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }
    }

    // ── Filter segmented groups ─────────────────────────────────────────
    // Horizontally scrollable so filters remain usable at large font scaling.
    if (entries.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Method group
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
        // Status group
        SegmentedToggleGroup(segmentCount = StatusRange.entries.size) { segmentShape ->
          StatusRange.entries.forEachIndexed { index, range ->
            SegmentItem(range.label, range in filter.statusRanges, shape = segmentShape(index)) {
              filter = filter.copy(statusRanges = filter.statusRanges.toggle(range))
            }
          }
        }
        // Level group
        SegmentedToggleGroup(segmentCount = 2) { segmentShape ->
          SegmentItem("ERROR", LogLevel.ERROR in filter.levels, shape = segmentShape(0), accentColor = OlliteRTDeleteRed) {
            filter = filter.copy(levels = filter.levels.toggle(LogLevel.ERROR))
          }
          SegmentItem("WARN", LogLevel.WARNING in filter.levels, shape = segmentShape(1), accentColor = WarningColor) {
            filter = filter.copy(levels = filter.levels.toggle(LogLevel.WARNING))
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
            text = "Searching...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Text(
            text = "Showing ${displayedEntries.size} of ${entries.size}",
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
            text = "Clear filters",
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
            text = "No activity yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Server activity will appear here.",
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
            text = "No matching entries",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "No logs match the current filters.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(12.dp))
          TextButton(onClick = { clearAllFilters() }) {
            Text("Clear filters", color = OlliteRTPrimary)
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
              LogEntryCard(entry, autoExpand = autoExpand, searchQuery = filter.query)
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
              text = if (unseenCount == 1) "New activity" else "$unseenCount new entries",
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
private fun <T> Set<T>.toggle(element: T): Set<T> =
  if (element in this) this - element else this + element

/**
 * A connected toggle bar — segments share a common rounded container with
 * [surfaceContainerHighest] background. Individual segments highlight with the
 * accent color when selected. Uses [segmentCount] to give each segment
 * position-aware corner rounding: only outer ends are rounded, inner
 * boundaries are flat so adjacent selected segments look seamless.
 */
@Composable
private fun SegmentedToggleGroup(
  segmentCount: Int,
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
    modifier = Modifier
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
 */
@Composable
private fun RowScope.SegmentItem(
  label: String,
  selected: Boolean,
  shape: Shape = RoundedCornerShape(0.dp),
  accentColor: Color? = null,
  onClick: () -> Unit,
) {
  val selectedColor = accentColor ?: OlliteRTPrimary
  val bgColor by animateColorAsState(
    targetValue = if (selected) selectedColor else Color.Transparent,
    animationSpec = tween(150),
    label = "seg_bg",
  )

  Box(
    modifier = Modifier
      .fillMaxHeight()
      .clip(shape)
      .background(bgColor)
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp),
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
private fun PendingResponseSection(entryId: String, partialText: String?) {
  // Collect the lightweight partial-text flow directly here so only this composable
  // recomposes on streaming updates (~300ms), not the entire LazyColumn.
  // Falls back to the entry's partialText from the list for the initial render.
  val pendingPartial by RequestLogStore.pendingPartialText.collectAsState()
  val liveText = if (pendingPartial.first == entryId) pendingPartial.second else partialText

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLowest)
      .padding(horizontal = 12.dp, vertical = 14.dp),
  ) {
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
  }
}

/**
 * "Generating..." text + bouncing dots + stop button.
 * Keyed only on [entryId] so it skips recomposition when [partialText] changes.
 */
@Composable
private fun GeneratingStatusRow(entryId: String) {
  val generatingText = remember(entryId) { GeneratingMessages.pick() }
  Row(
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
    Spacer(modifier = Modifier.weight(1f))
    // Stop button — cancels the in-flight inference from the Logs screen
    @OptIn(ExperimentalMaterial3Api::class)
    TooltipBox(
      positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
      tooltip = { PlainTooltip { Text("Stop generation") } },
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
          contentDescription = "Stop generation",
          tint = CancelledColor,
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}

@Composable
private fun BouncingDots() {
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
// Currently unused because compaction is instant (string manipulation, not model inference).
// See CLAUDE.md backlog for the full design concept.
//
// /**
//  * Animated compaction icon — two inward-pointing chevrons squeezing a box between them.
//  * The box shrinks horizontally as the chevrons compress, visually representing
//  * content being compacted to fit the context window.
//  */
// @Composable
// private fun CompactingIcon() {
//   val transition = rememberInfiniteTransition(label = "compact")
//   val spread by transition.animateFloat(
//     initialValue = 1f,
//     targetValue = 0.15f,
//     animationSpec = infiniteRepeatable(
//       animation = tween(durationMillis = 1000),
//       repeatMode = RepeatMode.Reverse,
//     ),
//     label = "squeeze",
//   )
//   Canvas(modifier = Modifier.size(width = 22.dp, height = 16.dp)) {
//     val midY = size.height / 2f
//     val midX = size.width / 2f
//     val chevronH = size.height * 0.32f
//     val strokeW = 1.8f.dp.toPx()
//     val color = WarningColor
//     val boxHalfW = spread * 3.5f.dp.toPx()
//     val boxHalfH = 4.dp.toPx()
//     drawRect(
//       color = color,
//       topLeft = Offset(midX - boxHalfW, midY - boxHalfH),
//       size = androidx.compose.ui.geometry.Size(boxHalfW * 2f, boxHalfH * 2f),
//     )
//     val gap = 2.dp.toPx()
//     val leftTip = midX - boxHalfW - gap
//     val leftBack = leftTip - 4.dp.toPx()
//     drawLine(color, Offset(leftBack, midY - chevronH), Offset(leftTip, midY), strokeW, StrokeCap.Round)
//     drawLine(color, Offset(leftBack, midY + chevronH), Offset(leftTip, midY), strokeW, StrokeCap.Round)
//     val rightTip = midX + boxHalfW + gap
//     val rightBack = rightTip + 4.dp.toPx()
//     drawLine(color, Offset(rightBack, midY - chevronH), Offset(rightTip, midY), strokeW, StrokeCap.Round)
//     drawLine(color, Offset(rightBack, midY + chevronH), Offset(rightTip, midY), strokeW, StrokeCap.Round)
//   }
// }

// Event message parsing, highlighting — moved to logs/LogEventParsers.kt

// ── Internal event card ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InternalEventCard(entry: RequestLogEntry, searchQuery: String = "") {
  val context = LocalContext.current
  val isError = entry.level == LogLevel.ERROR
  val isWarning = entry.level == LogLevel.WARNING
  val isDebug = entry.level == LogLevel.DEBUG
  val accentColor = when {
    isError -> OlliteRTDeleteRed
    isWarning -> WarningColor
    isDebug -> MaterialTheme.colorScheme.outline
    else -> EventColor
  }
  val message = entry.path

  val categoryLabel = when (entry.eventCategory) {
    EventCategory.MODEL -> "MODEL"
    EventCategory.SETTINGS -> "SETTINGS"
    EventCategory.SERVER -> "SERVER"
    EventCategory.PROMPT -> "PROMPT"
    EventCategory.UPDATE -> "UPDATE"
    EventCategory.GENERAL -> "EVENT"
  }
  val categoryIcon = when (entry.eventCategory) {
    EventCategory.MODEL -> Icons.Outlined.Memory
    EventCategory.SETTINGS -> Icons.Outlined.Settings
    EventCategory.SERVER -> Icons.Outlined.Dns
    EventCategory.PROMPT -> Icons.AutoMirrored.Outlined.Notes
    EventCategory.UPDATE -> Icons.Outlined.NewReleases
    EventCategory.GENERAL -> Icons.Outlined.Info
  }

  val cardBg = when {
    isError -> OlliteRTDeleteRed.copy(alpha = 0.06f)
    isWarning -> WarningColor.copy(alpha = 0.06f)
    isDebug -> MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
    else -> MaterialTheme.colorScheme.surfaceContainerLow
  }

  val parsedEvent = remember(message, entry.requestBody) { parseEventType(message, entry.requestBody) }

  // Headline text shown next to the category badge
  val headline = when (parsedEvent) {
    is ParsedEventType.Loading -> "Model Loading"
    is ParsedEventType.Ready -> "Model Loaded"
    is ParsedEventType.Warmup -> "Internal Warmup Message"
    is ParsedEventType.InferenceSettings -> "Settings changed"
    is ParsedEventType.SettingsToggle -> "Settings changed"
    is ParsedEventType.PromptActive -> "${parsedEvent.promptType} active"
    is ParsedEventType.ServerStopped -> "Server Stopped"
    is ParsedEventType.WarmupSkipped -> "Warmup Skipped"
    is ParsedEventType.ModelLoadFailed -> "Model Load Failed"
    is ParsedEventType.ServerFailed -> "Server Failed"
    is ParsedEventType.ModelNotFound -> "Model Not Found"
    is ParsedEventType.ImageDecodeFailed -> "Image Decode Failed"
    is ParsedEventType.QueuedReload -> "Queued Reload"
    is ParsedEventType.CorsChanged -> "Settings changed"
    is ParsedEventType.ConversationResetFailed -> "Conversation Reset Failed"
    is ParsedEventType.SettingsBatch -> "Settings updated"
    is ParsedEventType.ApiConfigChange -> "Config via REST API"
    is ParsedEventType.RestartRequested -> "Model Restart"
    is ParsedEventType.Unloading -> "Model Unloading"
    is ParsedEventType.KeepAliveUnloaded -> "Model Idle Unloaded"
    is ParsedEventType.KeepAliveReloading -> "Model Reloading"
    is ParsedEventType.KeepAliveReloaded -> "Model Reloaded"
    is ParsedEventType.UpdateAvailable -> "Update Available"
    is ParsedEventType.UpdateCurrent -> "Up to Date"
    is ParsedEventType.UpdateAutoDisabled -> "Update Check Disabled"
    null -> if (isDebug) "Debug" else null
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(cardBg)
      .padding(16.dp),
  ) {
    // ── Header: [BADGE] [headline] ... [copy] ──
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Category pill badge
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .background(accentColor.copy(alpha = 0.15f))
          .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = categoryIcon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(12.dp),
        )
        Text(
          text = categoryLabel,
          style = MaterialTheme.typography.labelSmall,
          color = accentColor,
          fontWeight = FontWeight.Bold,
          fontFamily = SpaceGroteskFontFamily,
        )
      }

      // Headline next to badge
      if (headline != null) {
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = headline,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 12.sp,
          ),
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.SemiBold,
        )
      }

      Spacer(modifier = Modifier.weight(1f))
      // Copy button
      TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text("Copy event") } },
        state = rememberTooltipState(),
      ) {
        IconButton(
          onClick = { copyEventToClipboard(context, entry) },
          modifier = Modifier.size(32.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.ContentCopy,
            contentDescription = "Copy event",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // ── Body — specialised per event type ──
    when (parsedEvent) {
      is ParsedEventType.Loading -> {
        Text(
          text = buildAnnotatedString {
            append("Loading ")
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" into memory")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.Ready -> {
        Text(
          text = buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" loaded into memory")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.Warmup -> {
        // Request/response style — mirrors LogEntryCard sections
        Text(
          text = "Request",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp),
        ) {
          Text(
            text = parsedEvent.input,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Response",
          style = MaterialTheme.typography.labelSmall,
          color = OlliteRTPrimary,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp),
        ) {
          Text(
            text = parsedEvent.output,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }

      is ParsedEventType.InferenceSettings -> {
        SettingsChangeRows(parsedEvent.parsed, accentColor)
      }

      is ParsedEventType.SettingsToggle -> {
        // Single row matching the inference settings row style — shows state transition
        val oldState = if (parsedEvent.enabled) "disabled" else "enabled"
        val newState = if (parsedEvent.enabled) "enabled" else "disabled"
        val newColor = if (parsedEvent.enabled) OlliteRTGreen400 else OlliteRTDeleteRed
        // Reuse the same SettingsChangeRows composable via a synthetic ParsedInferenceEvent
        SettingsChangeRows(
          parsed = ParsedInferenceEvent(
            changes = listOf(InferenceSettingsChange(parsedEvent.settingName, oldState, newState)),
            statusSuffix = null,
          ),
          accentColor = accentColor,
          newValueColorOverride = newColor,
        )
      }

      is ParsedEventType.PromptActive -> {
        // Show the prompt text in an expandable text box (same style as prompt diffs)
        ExpandablePromptBox(
          text = parsedEvent.promptText,
          textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
          ),
          textColor = MaterialTheme.colorScheme.onSurface,
        )
      }

      is ParsedEventType.ServerStopped -> {
        // Show model name that was unloaded — sourced from the entry's modelName field
        if (entry.modelName != null) {
          Text(
            text = buildAnnotatedString {
              append("Model ")
              withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
                append(entry.modelName)
              }
              append(" unloaded from memory")
            },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      is ParsedEventType.WarmupSkipped -> {
        Text(
          text = parsedEvent.reason,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.ModelLoadFailed -> {
        Text(
          text = parsedEvent.errorMessage,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.ServerFailed -> {
        Text(
          text = parsedEvent.errorMessage,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.ModelNotFound -> {
        // Show model name in primary color if extracted, otherwise the raw detail as error
        Text(
          text = parsedEvent.detail,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = OlliteRTPrimary,
          fontWeight = FontWeight.SemiBold,
        )
      }

      is ParsedEventType.ImageDecodeFailed -> {
        Text(
          text = parsedEvent.errorMessage,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.QueuedReload -> {
        val modelText = if (entry.modelName != null) "Reloading ${entry.modelName} with updated settings"
                        else "Reloading model with updated settings"
        Text(
          text = modelText,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = OlliteRTPrimary,
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.CorsChanged -> {
        // Reuse the settings change row style for CORS before → after
        SettingsChangeRows(
          parsed = ParsedInferenceEvent(
            changes = listOf(InferenceSettingsChange("CORS Allowed Origins", parsedEvent.oldValue, parsedEvent.newValue)),
            statusSuffix = null,
          ),
          accentColor = accentColor,
        )
      }

      is ParsedEventType.ConversationResetFailed -> {
        Text(
          text = parsedEvent.errorMessage,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
          fontWeight = FontWeight.Medium,
        )
      }

      is ParsedEventType.SettingsBatch, is ParsedEventType.ApiConfigChange -> {
        // Reuse SettingsChangeRows via a synthetic ParsedInferenceEvent.
        // For toggle-style changes (new value is "enabled"/"disabled"),
        // color the new value green/red for clarity.
        val batchChanges = when (parsedEvent) {
          is ParsedEventType.SettingsBatch -> parsedEvent.changes
          is ParsedEventType.ApiConfigChange -> parsedEvent.changes
        }
        val toggleValues = setOf("enabled", "disabled")
        SettingsChangeRows(
          parsed = ParsedInferenceEvent(
            changes = batchChanges,
            statusSuffix = null,
          ),
          accentColor = accentColor,
          newValueColorOverride = null,
          perRowNewColor = { change ->
            if (change.newValue in toggleValues) {
              if (change.newValue == "enabled") OlliteRTGreen400 else OlliteRTDeleteRed
            } else null
          },
        )
      }

      is ParsedEventType.RestartRequested -> {
        // Show the model name being restarted if available
        if (entry.modelName != null) {
          Text(
            text = buildAnnotatedString {
              append("Restarting ")
              withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
                append(entry.modelName)
              }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      is ParsedEventType.Unloading -> {
        Text(
          text = buildAnnotatedString {
            append("Unloading ")
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" from memory")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.KeepAliveUnloaded -> {
        Text(
          text = buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" unloaded after ${parsedEvent.idleMinutes}m idle to free RAM")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.KeepAliveReloading -> {
        Text(
          text = buildAnnotatedString {
            append("Reloading ")
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" (request received while idle)")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.KeepAliveReloaded -> {
        Text(
          text = buildAnnotatedString {
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.modelName)
            }
            append(" reloaded in ${parsedEvent.timeMs}ms")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.UpdateAvailable -> {
        Text(
          text = buildAnnotatedString {
            append("Version ")
            withStyle(SpanStyle(color = OlliteRTPrimary, fontWeight = FontWeight.SemiBold)) {
              append(parsedEvent.version)
            }
            append(" is available")
          },
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Show body details (current version, release URL) if present
        if (parsedEvent.body != null) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = parsedEvent.body,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          )
        }
      }

      is ParsedEventType.UpdateCurrent -> {
        Text(
          text = parsedEvent.body ?: "No newer version found",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      is ParsedEventType.UpdateAutoDisabled -> {
        Text(
          text = parsedEvent.body ?: "Too many consecutive failures",
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = accentColor,
          fontWeight = FontWeight.Medium,
        )
      }

      null -> {
        // Default: styled text with highlighted values
        val isLong = message.length > 120 || message.count { it == '\n' } > 2
        var expanded by remember { mutableStateOf(false) }
        val styledMessage = remember(message, searchQuery) {
          val base = highlightEventMessage(message, isError, accentColor)
          if (searchQuery.isNotEmpty()) overlaySearchHighlights(base, searchQuery) else base
        }

        if (expanded) {
          SelectionContainer {
            Text(
              text = styledMessage,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp, lineHeight = 17.sp),
            )
          }
        } else {
          Text(
            text = styledMessage,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp, lineHeight = 17.sp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (isLong) {
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = if (expanded) "Show less" else "Show more",
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .clickable { expanded = !expanded }
              .padding(vertical = 2.dp),
          )
        }
      }
    }

    // Recovery suggestion for error-level events — shown below the error body
    if (isError) {
      val suggestion = remember(message) {
        val kind = LlmHttpErrorSuggestions.classifyFromString(message)
        LlmHttpErrorSuggestions.suggest(kind)
      }
      if (suggestion != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = suggestion,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // ── Footer — horizontally scrollable so all badges remain visible at large font ──
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      // Timing in footer for model ready and warmup (mirrors request card latency)
      when (parsedEvent) {
        is ParsedEventType.Ready -> {
          Text(
            text = "Ready",
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTGreen400,
            fontWeight = FontWeight.SemiBold,
          )
          FooterDot()
          Text(
            text = "${parsedEvent.timeMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        is ParsedEventType.Warmup -> {
          Text(
            text = "${parsedEvent.timeMs}ms",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        else -> {}
      }
      // Separator dot — only shown when there's timing content before the timestamp
      if (parsedEvent is ParsedEventType.Ready || parsedEvent is ParsedEventType.Warmup) {
        FooterDot()
      }
      Text(
        text = listOfNotNull(
          entry.modelName,
          formatTimestamp(entry.timestamp),
        ).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }
}

// ── Settings change rows ─────────────────────────────────────────────────────

/**
 * Structured rows for settings changes (inference params or toggle states).
 * Each row: [param name]  [old → new], all full-width with consistent alignment.
 * @param newValueColorOverride optional color override for the new value text on ALL rows
 *   (e.g. green/red for enabled/disabled toggles)
 * @param perRowNewColor optional per-row color function — takes precedence over [newValueColorOverride]
 *   when non-null. Used by SettingsBatch to color "enabled" green and "disabled" red per row.
 */
@Composable
private fun SettingsChangeRows(
  parsed: ParsedInferenceEvent,
  accentColor: Color,
  newValueColorOverride: Color? = null,
  perRowNewColor: ((InferenceSettingsChange) -> Color?)? = null,
) {
  // Two-column diff layout: [Param: old]  →  [Param: new]
  // Both sides are equal weight(1f), arrow is fixed-width centered between them.
  // This guarantees vertical arrow alignment regardless of value text widths.
  if (parsed.changes.isNotEmpty()) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      parsed.changes.forEach { change ->
        val rowColor = perRowNewColor?.invoke(change) ?: newValueColorOverride
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (change.oldValue.isEmpty()) {
            // No old value — just show param: new (e.g. initial set)
            Text(
              text = "${change.paramName}: ${change.newValue}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
              color = rowColor ?: OlliteRTPrimary,
              fontWeight = FontWeight.SemiBold,
            )
          } else {
            // Left column: "Param: oldValue" — muted
            Text(
              text = "${change.paramName}: ${change.oldValue}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.weight(1f),
            )
            // Center: arrow
            Text(
              text = "→",
              style = MaterialTheme.typography.labelSmall,
              color = ValueArrowColor,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              modifier = Modifier.width(28.dp),
            )
            // Right column: "Param: newValue" — bold/bright
            Text(
              text = "${change.paramName}: ${change.newValue}",
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
              color = rowColor ?: MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.End,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }

  // Prompt before/after diffs — expandable text boxes for system_prompt / chat_template
  parsed.promptDiffs.forEach { diff ->
    if (parsed.changes.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
    else Spacer(modifier = Modifier.height(2.dp))
    // Prompt label (e.g. "system_prompt" or "chat_template")
    val displayName = diff.paramName.replace("_", " ")
      .replaceFirstChar { it.uppercaseChar() }
    Text(
      text = displayName,
      style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(4.dp))
    PromptBeforeAfterBoxes(diff)
  }

  // Status badge (reloading model, reload queued, etc.)
  if (!parsed.statusSuffix.isNullOrBlank()) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = parsed.statusSuffix,
      style = MaterialTheme.typography.labelSmall,
      color = accentColor,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(accentColor.copy(alpha = 0.10f))
        .padding(horizontal = 8.dp, vertical = 3.dp),
    )
  }
}

/**
 * Expandable before/after text boxes for a prompt change.
 * "Before" is shown in muted style, "After" in primary style.
 * Both are collapsible for long prompts.
 */
@Composable
private fun PromptBeforeAfterBoxes(diff: PromptDiff) {
  val textStyle = MaterialTheme.typography.bodySmall.copy(
    fontFamily = SpaceGroteskFontFamily,
    fontSize = 11.sp,
    lineHeight = 16.sp,
  )

  // Before
  if (diff.oldText.isNotBlank()) {
    Text(
      text = "Before",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(2.dp))
    ExpandablePromptBox(
      text = diff.oldText,
      textStyle = textStyle,
      textColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(4.dp))
  }

  // After
  Text(
    text = if (diff.oldText.isNotBlank()) "After" else "Set to",
    style = MaterialTheme.typography.labelSmall,
    color = OlliteRTPrimary,
  )
  Spacer(modifier = Modifier.height(2.dp))
  if (diff.newText.isBlank()) {
    Text(
      text = "(empty)",
      style = textStyle,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
      fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
    )
  } else {
    ExpandablePromptBox(
      text = diff.newText,
      textStyle = textStyle,
      textColor = MaterialTheme.colorScheme.onSurface,
    )
  }
}

/**
 * A text box with a dark background that collapses to 4 lines for long text.
 * Tap to expand/collapse.
 */
@Composable
private fun ExpandablePromptBox(
  text: String,
  textStyle: androidx.compose.ui.text.TextStyle,
  textColor: Color,
) {
  val isLong = text.length > 200 || text.count { it == '\n' } > 3
  var expanded by remember { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLowest)
      .then(if (isLong) Modifier.clickable { expanded = !expanded } else Modifier)
      .padding(10.dp),
  ) {
    if (expanded) {
      SelectionContainer {
        Text(text = text, style = textStyle, color = textColor)
      }
    } else {
      Text(
        text = text,
        style = textStyle,
        color = textColor,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (isLong) {
      Icon(
        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
        contentDescription = if (expanded) "Collapse" else "Expand",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(18.dp),
      )
    }
  }
}

private fun copyEventToClipboard(context: Context, entry: RequestLogEntry) {
  val json = entryToJson(entry).toString(2)
  copyToClipboard(context, "OlliteRT Event", json, formatSuffix = "JSON")
}

private const val COLLAPSED_MAX_LINES = 8
private const val COLLAPSED_MAX_CHARS = 600
/**
 * Bodies above this size get highlighted asynchronously (off main thread) to avoid jank.
 * At 1KB, regex-based JSON highlighting can take 10-30ms on mid-range devices — enough
 * to drop frames during scrolling. Larger bodies (2-4KB) can take 50-100ms.
 */
private const val ASYNC_HIGHLIGHT_THRESHOLD = 1_000

@Composable
private fun LogEntryCard(entry: RequestLogEntry, autoExpand: Boolean = false, searchQuery: String = "") {
  val context = LocalContext.current
  val isError = entry.level == LogLevel.ERROR
  val isWarning = entry.level == LogLevel.WARNING
  val cardBg = when {
    entry.isCancelled -> CancelledColor.copy(alpha = 0.06f)
    isError -> OlliteRTDeleteRed.copy(alpha = 0.06f)
    isWarning -> WarningColor.copy(alpha = 0.06f)
    else -> MaterialTheme.colorScheme.surfaceContainerLow
  }

  var requestExpanded by remember { mutableStateOf(autoExpand) }
  var compactedExpanded by remember { mutableStateOf(autoExpand) }
  var responseExpanded by remember { mutableStateOf(autoExpand) }
  var showMetricsDialog by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(cardBg)
      .padding(16.dp),
  ) {
    // Method badge + path + info/copy buttons (top row)
    // Client IP shown on a second row so it doesn't squeeze the path at large font
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MethodBadge(method = entry.method)
      Spacer(modifier = Modifier.width(8.dp))
      // Path text with optional search highlighting
      if (searchQuery.isNotEmpty()) {
        val highlighted = remember(entry.path, searchQuery) {
          buildHighlightedString(entry.path, searchQuery, baseColor = Color(0xFFE5E2E3))
        }
        Text(
          text = highlighted,
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = SpaceGroteskFontFamily,
          fontWeight = FontWeight.Medium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      } else {
        Text(
          text = entry.path,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          fontFamily = SpaceGroteskFontFamily,
          fontWeight = FontWeight.Medium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
      }
      // Per-request metrics info button — only shown when metrics are available
      if (entry.ttfbMs > 0 || entry.decodeSpeed > 0 || entry.latencyMs > 0) {
        Spacer(modifier = Modifier.width(2.dp))
        @OptIn(ExperimentalMaterial3Api::class)
        TooltipBox(
          positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
          tooltip = { PlainTooltip { Text("Request metrics") } },
          state = rememberTooltipState(),
        ) {
          IconButton(
            onClick = { showMetricsDialog = true },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.Info,
              contentDescription = "Request metrics",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
      // Hide copy while response is still generating — entry has no response body yet
      if (!entry.isPending) {
        Spacer(modifier = Modifier.width(2.dp))
        @OptIn(ExperimentalMaterial3Api::class)
        TooltipBox(
          positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
          tooltip = { PlainTooltip { Text("Copy log entry") } },
          state = rememberTooltipState(),
        ) {
          IconButton(
            onClick = { copyEntryToClipboard(context, entry) },
            modifier = Modifier.size(32.dp),
          ) {
            Icon(
              imageVector = Icons.Outlined.ContentCopy,
              contentDescription = "Copy log entry",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    }

    // Client IP on its own row, right-aligned, so it's visible at large font scaling
    if (entry.clientIp != null) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
      ) {
        if (searchQuery.isNotEmpty()) {
          val highlighted = remember(entry.clientIp, searchQuery) {
            buildHighlightedString(entry.clientIp, searchQuery, baseColor = Color(0xFFC2C6D8))
          }
          Text(
            text = highlighted,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = SpaceGroteskFontFamily,
            maxLines = 1,
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHighest)
              .padding(horizontal = 8.dp, vertical = 3.dp),
          )
        } else {
          Text(
            text = entry.clientIp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = SpaceGroteskFontFamily,
            maxLines = 1,
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHighest)
              .padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
      }
    }

    // Request body preview (if present)
    if (!entry.requestBody.isNullOrBlank()) {
      val formatted = remember(entry.requestBody) { prettyPrintJson(entry.requestBody) }
      val isLong = remember(formatted) { formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES }
      val requestSize = remember(entry.requestBody) { formatByteSize(entry.requestBody.length) }
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = "Request · $requestSize",
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        body = formatted,
        expanded = requestExpanded,
        showToggle = isLong,
        onToggle = { requestExpanded = !requestExpanded },
        searchQuery = searchQuery,
      )
    }

    // Compacted prompt preview — shown between Request and Response when compaction was applied.
    // This is the actual prompt that was sent to inference after compaction strategies were applied.
    if (!entry.compactedPrompt.isNullOrBlank()) {
      val compactedSize = remember(entry.compactedPrompt) { formatByteSize(entry.compactedPrompt.length) }
      val isLong = remember(entry.compactedPrompt) { entry.compactedPrompt.length > COLLAPSED_MAX_CHARS || entry.compactedPrompt.count { it == '\n' } > COLLAPSED_MAX_LINES }
      val badges = remember(entry.compactionDetails) { parseCompactionBadges(entry.compactionDetails) }
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = "Compacted Prompt · $compactedSize",
        labelColor = WarningColor,
        body = entry.compactedPrompt,
        expanded = compactedExpanded,
        showToggle = isLong,
        onToggle = { compactedExpanded = !compactedExpanded },
        searchQuery = searchQuery,
      )
      // Strategy badges below the text box, above the Response section
      if (badges.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          badges.forEachIndexed { index, (badgeLabel, badgeColor) ->
            if (index > 0) FooterDot()
            Text(
              text = badgeLabel,
              style = MaterialTheme.typography.labelSmall,
              color = badgeColor,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    // Response area: streaming partial text while pending, full JSON when done
    // NOTE: Compaction is instant (string manipulation before inference), so the pending state
    // always shows normal generating text. If model-based compaction is added, this should
    // show CompactingIcon + "Compacting prompt" text while the model summarizes.
    if (entry.isPending) {
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "Response",
        style = MaterialTheme.typography.labelSmall,
        color = OlliteRTPrimary,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      PendingResponseSection(
        entryId = entry.id,
        partialText = entry.partialText,
      )
    } else if (entry.isCancelled) {
      Spacer(modifier = Modifier.height(10.dp))
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(CancelledColor.copy(alpha = 0.08f))
          .padding(horizontal = 12.dp, vertical = 14.dp),
      ) {
        val cancelledDisplay = remember(entry.partialText) {
          entry.partialText?.replace("<think>", "")?.replace("</think>", "")?.trimStart()
        }
        if (!cancelledDisplay.isNullOrEmpty()) {
          Text(
            text = cancelledDisplay,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = 11.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
          text = if (entry.cancelledByUser) "Generation stopped by user"
                 else "Client disconnected — generation stopped",
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 11.sp,
          ),
          color = CancelledColor,
          fontWeight = FontWeight.SemiBold,
        )
      }
    } else if (!entry.responseBody.isNullOrBlank()) {
      val formatted = remember(entry.responseBody) { prettyPrintJson(entry.responseBody) }
      val isLong = remember(formatted) { formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES }
      val responseSize = remember(entry.responseBody) { formatByteSize(entry.responseBody.length) }
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = "Response · $responseSize",
        labelColor = OlliteRTPrimary,
        body = formatted,
        expanded = responseExpanded,
        showToggle = isLong,
        onToggle = { responseExpanded = !responseExpanded },
        searchQuery = searchQuery,
      )
    }

    // Show which client-supplied sampler params were ignored
    if (entry.ignoredClientParams != null) {
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = "Client params ignored: ${entry.ignoredClientParams}",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = WarningColor,
      )
    }

    // Footer area: badges (compaction/overflow) are placed inline when they fit,
    // or on a separate row above when they'd overflow. Measured dynamically via
    // SubcomposeLayout so the layout scales correctly across phones and tablets.
    if (!entry.isPending) {
      val contextOverflow = remember(entry.responseBody, entry.statusCode) { isContextOverflowError(entry.responseBody, entry.statusCode) }

      Spacer(modifier = Modifier.height(10.dp))

      // Footer: status · latency · SSE · Thinking · Cancelled  ···  model · time
      // Context overflow is shown in the StatusBadge as "Context Exceeded" instead of "400 Bad Request".
      // Compaction badges (Compacted, Truncated, Trimmed) are shown below the
      // Compacted Prompt text box instead of here.
      // Horizontally scrollable so all badges remain visible at large font scaling.
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        StatusBadge(statusCode = entry.statusCode, contextOverflow = contextOverflow)
        FooterDot()
        Text(
          text = "${entry.latencyMs}ms",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.isStreaming) {
          FooterDot()
          Text(
            text = "SSE",
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTPrimary,
            fontWeight = FontWeight.SemiBold,
          )
        }
        if (entry.isThinking) {
          FooterDot()
          Text(
            text = "Thinking",
            style = MaterialTheme.typography.labelSmall,
            color = ThinkingColor,
            fontWeight = FontWeight.SemiBold,
          )
        }
        if (entry.isCancelled) {
          FooterDot()
          Text(
            text = "Cancelled",
            style = MaterialTheme.typography.labelSmall,
            color = CancelledColor,
            fontWeight = FontWeight.SemiBold,
          )
        }
        // Per-request context utilization (e.g. "~258 / 1024 ctx")
        // Color-coded: white ≤50%, yellow 50–80%, red >80%
        if (entry.inputTokenEstimate > 0 && entry.maxContextTokens > 0) {
          val utilPct = entry.inputTokenEstimate.toDouble() / entry.maxContextTokens.toDouble()
          val ctxColor = when {
            utilPct > 0.8 -> MaterialTheme.colorScheme.error
            utilPct > 0.5 -> WarningColor
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          }
          FooterDot()
          Text(
            text = "${if (entry.isExactTokenCount) "" else "~"}${entry.inputTokenEstimate} / ${entry.maxContextTokens} ctx",
            style = MaterialTheme.typography.labelSmall,
            color = ctxColor,
          )
        }
        FooterDot()
        Text(
          text = listOfNotNull(
            entry.modelName,
            formatTimestamp(entry.timestamp),
          ).joinToString(" · "),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
    }
  }

  // Per-request performance metrics popup
  if (showMetricsDialog) {
    RequestMetricsDialog(entry = entry, onDismiss = { showMetricsDialog = false })
  }
}

/**
 * Dialog showing per-request performance metrics for a single log entry.
 * Displays TTFB, decode speed, prefill speed, inter-token latency, token counts,
 * context utilization, and total latency.
 */
@Composable
private fun RequestMetricsDialog(entry: RequestLogEntry, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = "Request Metrics",
        style = MaterialTheme.typography.titleMedium,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (entry.ttfbMs > 0) {
          MetricsRow("Time to First Token", "${entry.ttfbMs}ms")
        }
        if (entry.decodeSpeed > 0) {
          MetricsRow("Decode Speed", "%.1f t/s".format(entry.decodeSpeed))
        }
        if (entry.prefillSpeed > 0) {
          MetricsRow("Prefill Speed", "%.1f t/s".format(entry.prefillSpeed))
        }
        if (entry.itlMs > 0) {
          MetricsRow("Inter-Token Latency", "%.1fms".format(entry.itlMs))
        }
        if (entry.latencyMs > 0) {
          MetricsRow("Total Latency", "${entry.latencyMs}ms")
        }
        if (entry.inputTokenEstimate > 0) {
          val prefix = if (entry.isExactTokenCount) "" else "~"
          MetricsRow("Input Tokens", "$prefix${entry.inputTokenEstimate}")
        }
        if (entry.tokens > 0) {
          MetricsRow("Output Tokens", "~${entry.tokens}")
        }
        if (entry.inputTokenEstimate > 0 && entry.maxContextTokens > 0) {
          val utilPct = (entry.inputTokenEstimate.toDouble() / entry.maxContextTokens.toDouble()) * 100.0
          val utilColor = when {
            utilPct > 80 -> MaterialTheme.colorScheme.error
            utilPct > 50 -> WarningColor
            else -> MaterialTheme.colorScheme.onSurface
          }
          MetricsRow(
            label = "Context Utilization",
            value = "%.1f%%".format(utilPct),
            valueColor = utilColor,
            detail = "${entry.inputTokenEstimate} / ${entry.maxContextTokens}",
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close")
      }
    },
  )
}

@Composable
private fun MetricsRow(
  label: String,
  value: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
  detail: String? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(horizontalAlignment = Alignment.End) {
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = valueColor,
        fontWeight = FontWeight.SemiBold,
        fontFamily = SpaceGroteskFontFamily,
      )
      if (detail != null) {
        Text(
          text = detail,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ExpandableBodySection(
  label: String,
  labelColor: Color,
  body: String,
  expanded: Boolean,
  showToggle: Boolean,
  onToggle: () -> Unit,
  /** Optional annotated label — takes precedence over plain [label] when provided. */
  annotatedLabel: AnnotatedString? = null,
  /** Active search query — overlays yellow highlight on matches within JSON-highlighted text. */
  searchQuery: String = "",
) {
  if (annotatedLabel != null) {
    Text(
      text = annotatedLabel,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.SemiBold,
    )
  } else if (label.isNotBlank()) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = labelColor,
      fontWeight = FontWeight.SemiBold,
    )
  }
  Spacer(modifier = Modifier.height(4.dp))

  // Collapsed: highlight only the visible prefix (cheap — max ~600 chars).
  // Expanded: compute full highlight asynchronously to avoid main-thread jank on large bodies
  // (50KB+ JSON with regex highlighting can take 100-200ms). Show plain text instantly,
  // then swap in highlighted version when ready.
  val collapsedHighlighted = remember(body, searchQuery) {
    val preview = if (body.length > COLLAPSED_MAX_CHARS) body.substring(0, COLLAPSED_MAX_CHARS) else body
    val jsonStyled = highlightJson(preview)
    if (searchQuery.isNotEmpty()) overlaySearchHighlights(jsonStyled, searchQuery) else jsonStyled
  }

  if (expanded) {
    // For large bodies, compute JSON + search highlighting off the main thread.
    // Small bodies (<4KB) are fast enough to highlight synchronously.
    val fullHighlighted by produceState(
      initialValue = if (body.length <= ASYNC_HIGHLIGHT_THRESHOLD) {
        val jsonStyled = highlightJson(body)
        if (searchQuery.isNotEmpty()) overlaySearchHighlights(jsonStyled, searchQuery) else jsonStyled
      } else {
        null // show plain text while computing
      },
      key1 = body,
      key2 = searchQuery,
    ) {
      if (body.length > ASYNC_HIGHLIGHT_THRESHOLD) {
        value = withContext(Dispatchers.Default) {
          val jsonStyled = highlightJson(body)
          if (searchQuery.isNotEmpty()) overlaySearchHighlights(jsonStyled, searchQuery) else jsonStyled
        }
      }
    }
    // Expanded: wrap in SelectionContainer so text is selectable
    SelectionContainer {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerLowest),
      ) {
        Box(
          modifier = Modifier
            .padding(12.dp)
            .horizontalScroll(rememberScrollState()),
        ) {
          if (fullHighlighted != null) {
            Text(
              text = fullHighlighted!!,
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
              ),
            )
          } else {
            // Plain text fallback while async highlighting is in progress
            Text(
              text = body,
              style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = SpaceGroteskFontFamily,
                fontSize = 11.sp,
                lineHeight = 16.sp,
              ),
              color = Color(0xFFBDBDBD),
            )
          }
        }
        if (showToggle) {
          Icon(
            imageVector = Icons.Outlined.ExpandLess,
            contentDescription = "Collapse",
            tint = labelColor.copy(alpha = 0.7f),
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(6.dp)
              .size(22.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f))
              .clickable(onClick = onToggle),
          )
        }
      }
    }
  } else {
    // Collapsed: tapping anywhere expands, no text selection needed
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        .then(if (showToggle) Modifier.clickable(onClick = onToggle) else Modifier),
    ) {
      Box(
        modifier = Modifier
          .padding(12.dp)
          .horizontalScroll(rememberScrollState()),
      ) {
        Text(
          text = collapsedHighlighted,
          style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = SpaceGroteskFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
          ),
          maxLines = COLLAPSED_MAX_LINES,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (showToggle) {
        Icon(
          imageVector = Icons.Outlined.ExpandMore,
          contentDescription = "Expand",
          tint = labelColor.copy(alpha = 0.7f),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)),
        )
      }
    }
  }
}

// Clipboard/export functions — moved to logs/LogClipboardExport.kt
// Badge composables, formatters — moved to logs/LogBadges.kt

// JSON highlighting — moved to logs/LogJsonHighlighter.kt
