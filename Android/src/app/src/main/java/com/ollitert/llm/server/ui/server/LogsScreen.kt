package com.ollitert.llm.server.ui.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ContentCopy
import com.ollitert.llm.server.ui.common.TooltipIconButton
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
// TODO: F72 — Uncomment these imports when model-based compaction is implemented (CompactingIcon)
// import androidx.compose.foundation.Canvas
// import androidx.compose.ui.graphics.StrokeCap
// import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.service.EventCategory
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DeleteRedTint = Color(0xFFE57373)
private val EventColor = OlliteRTPrimary // matches the app's blue accent
private val ThinkingColor = Color(0xFFCE93D8) // soft purple for thinking mode
private val CancelledColor = Color(0xFFFFB74D) // amber for cancelled/stopped requests
private val WarningColor = Color(0xFFFFF176) // yellow for warnings (e.g. compacted tool schemas)
private val TruncatedColor = Color(0xFFFFB74D) // amber for history truncation
private val ContextOverflowColor = Color(0xFFEF5350) // red for context window exceeded errors

/** Patterns that indicate a context window overflow error in the response body. */
private val CONTEXT_OVERFLOW_PATTERNS = listOf(
  "context", "token", "exceed", "too long", "too many tokens", ">=",
)

/**
 * Checks if a response body contains a context window overflow error.
 * Detects patterns like "1031 >= 1024", "exceeds context", "token limit exceeded", etc.
 */
private fun isContextOverflowError(responseBody: String?): Boolean {
  if (responseBody.isNullOrBlank()) return false
  val lower = responseBody.lowercase()
  // Must contain "error" (indicating it's an error response) plus a context-overflow keyword
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

  // Clear logs confirmation dialog
  if (showClearConfirmDialog) {
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
          text = "This will delete all ${entries.size} log entries. This action cannot be undone.",
          style = MaterialTheme.typography.bodyMedium,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            RequestLogStore.clear()
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

  Column(modifier = modifier.fillMaxSize()) {
    // Header with clear button
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
      )
      if (entries.isNotEmpty()) {
        val context = LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // Clear all logs (with optional confirmation)
          TooltipIconButton(
            icon = Icons.Outlined.DeleteSweep,
            tooltip = "Clear all logs",
            onClick = {
              if (LlmHttpPrefs.isConfirmClearLogs(context)) {
                showClearConfirmDialog = true
              } else {
                RequestLogStore.clear()
              }
            },
            tint = DeleteRedTint,
            backgroundColor = DeleteRedTint.copy(alpha = 0.12f),
          )
          // Copy all logs (JSON)
          TooltipIconButton(
            icon = Icons.Outlined.ContentCopy,
            tooltip = "Copy all logs (JSON)",
            onClick = { scope.launch { copyAllLogsToClipboard(context, entries) } },
            tint = OlliteRTPrimary,
          )
          // Export/share logs as JSON file
          TooltipIconButton(
            icon = Icons.Outlined.Share,
            tooltip = "Export logs as JSON",
            onClick = { scope.launch { exportLogsAsJson(context, entries) } },
            tint = OlliteRTPrimary,
          )
        }
      }
    }

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
    } else {
      val listState = rememberLazyListState()
      val coroutineScope = rememberCoroutineScope()

      // Count of unseen new entries (shown in the floating indicator)
      var unseenCount by remember { mutableIntStateOf(0) }

      // Track the newest entry's ID to detect actual new entries (not partialText updates).
      // Using entries.size as a key misses new entries when at max capacity (add + evict = same size).
      val newestEntryId = entries.firstOrNull()?.id

      // True when a programmatic scroll is in progress — prevents the user-scroll
      // detector from incorrectly disabling auto-scroll during animateScrollToItem.
      var isProgrammaticScroll by remember { mutableStateOf(false) }

      // Auto-scroll is ON by default — disabled when the user manually scrolls away from top.
      var autoScrollEnabled by remember { mutableStateOf(true) }

      // Detect user-initiated scrolling away from top.
      // Uses snapshotFlow to avoid race conditions with LaunchedEffect key-based triggers.
      LaunchedEffect(Unit) {
        snapshotFlow {
          listState.isScrollInProgress to listState.firstVisibleItemIndex
        }.collect { (scrolling, firstIndex) ->
          if (scrolling && firstIndex > 0 && !isProgrammaticScroll) {
            autoScrollEnabled = false
          }
          if (firstIndex == 0 && !scrolling) {
            // User reached the top — re-enable auto-scroll and clear unseen count
            unseenCount = 0
            autoScrollEnabled = true
          }
        }
      }

      // When the newest entry changes: auto-scroll if enabled, otherwise bump unseen count
      LaunchedEffect(newestEntryId) {
        if (newestEntryId != null) {
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

      Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
          state = listState,
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(entries, key = { it.id }) { entry ->
            if (entry.method == "EVENT") {
              InternalEventCard(entry)
            } else {
              LogEntryCard(entry, autoExpand = autoExpand)
            }
          }
          item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // Floating "new activity" indicator — shown when new entries arrive while scrolled down
        androidx.compose.animation.AnimatedVisibility(
          visible = unseenCount > 0,
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
    // Show partial text if tokens have started arriving
    if (!liveText.isNullOrEmpty()) {
      Text(
        text = liveText,
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

// TODO: F72 — Model-based prompt compaction. When implemented, this animation will be used
// in the pending state to indicate the model is actively compacting/summarizing the prompt.
// Currently unused because compaction is instant (string manipulation, not model inference).
// See CLAUDE.md F72 for the full design concept.
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

// ── Event message parsing ────────────────────────────────────────────────────

/** A single parameter change in an inference settings event. */
private data class InferenceSettingsChange(
  val paramName: String,
  val oldValue: String,
  val newValue: String,
)

/** A before/after diff for a prompt (system prompt or chat template). */
private data class PromptDiff(
  val paramName: String,
  val oldText: String,
  val newText: String,
)

/** Parsed inference settings change with optional reload status and prompt diffs. */
private data class ParsedInferenceEvent(
  val changes: List<InferenceSettingsChange>,
  val statusSuffix: String?,
  val promptDiffs: List<PromptDiff> = emptyList(),
)

/** Parsed event — enables specialised card rendering for known message patterns. */
private sealed class ParsedEventType {
  data class Loading(val modelName: String) : ParsedEventType()
  data class Ready(val modelName: String, val timeMs: String) : ParsedEventType()
  data class Warmup(val input: String, val output: String, val timeMs: String) : ParsedEventType()
  data class InferenceSettings(val parsed: ParsedInferenceEvent) : ParsedEventType()
  /** A settings toggle like "Compact Tool Schemas enabled/disabled". */
  data class SettingsToggle(val settingName: String, val enabled: Boolean) : ParsedEventType()
  /** System prompt or chat template active on server start. */
  data class PromptActive(val promptType: String, val promptText: String) : ParsedEventType()
}

private val INFERENCE_CHANGE_PREFIX = "Inference settings changed: "

/**
 * Parses an inference settings change event.
 *
 * Structured JSON body schema (preferred):
 * ```json
 * {
 *   "type": "inference_settings",
 *   "changes": [{"param": "TopK", "old": "14", "new": "15"}, ...],
 *   "prompt_diffs": {
 *     "system_prompt": {"old": "...", "new": "..."},
 *     "chat_template": {"old": "...", "new": "..."}
 *   },
 *   "status": "reloading model"
 * }
 * ```
 *
 */
private fun parseInferenceSettingsMessage(message: String, eventBody: String? = null): ParsedInferenceEvent? {
  if (!message.startsWith(INFERENCE_CHANGE_PREFIX)) return null
  if (eventBody.isNullOrBlank()) return null

  return try {
    val json = JSONObject(eventBody)
    val changes = mutableListOf<InferenceSettingsChange>()
    val promptDiffs = mutableListOf<PromptDiff>()

    // Parse changes array
    val changesArr = json.optJSONArray("changes")
    if (changesArr != null) {
      for (i in 0 until changesArr.length()) {
        val c = changesArr.getJSONObject(i)
        changes.add(InferenceSettingsChange(
          paramName = c.getString("param"),
          oldValue = c.optString("old", ""),
          newValue = c.optString("new", ""),
        ))
      }
    }
    // Parse prompt diffs
    val diffs = json.optJSONObject("prompt_diffs")
    if (diffs != null) {
      for (key in diffs.keys()) {
        val diff = diffs.getJSONObject(key)
        promptDiffs.add(PromptDiff(key, diff.optString("old", ""), diff.optString("new", "")))
      }
    }
    val statusSuffix = json.optString("status", "").ifBlank { null }
    if (changes.isNotEmpty() || promptDiffs.isNotEmpty()) {
      ParsedInferenceEvent(changes, statusSuffix, promptDiffs)
    } else null
  } catch (_: Exception) { null }
}

/**
 * Parses well-known event messages into structured types.
 * Returns null for messages that don't match any known pattern (rendered as styled text).
 */
private fun parseEventType(message: String, eventBody: String? = null): ParsedEventType? {
  // Inference settings: "Inference settings changed: TopK: 100 → 64, ..."
  parseInferenceSettingsMessage(message, eventBody)?.let { return ParsedEventType.InferenceSettings(it) }

  // Loading model: ModelName
  if (message.startsWith("Loading model: ")) {
    return ParsedEventType.Loading(message.removePrefix("Loading model: "))
  }

  // Model ready: ModelName (Xms)
  Regex("""^Model ready: (.+?) \((\d+)ms\)$""").find(message)?.let {
    return ParsedEventType.Ready(it.groupValues[1], it.groupValues[2])
  }

  // Sending a warmup message: "input" → "output" (Xms)
  // Greedy match on output to handle response text that might contain quotes
  Regex("""^Sending a warmup message: "(.+?)" → "(.*)" \((\d+)ms\)$""").find(message)?.let {
    return ParsedEventType.Warmup(it.groupValues[1], it.groupValues[2], it.groupValues[3])
  }

  // Prompt active on server start: "System prompt active: \"...\""
  // Body JSON schema: {"type":"prompt_active","prompt_type":"system_prompt|chat_template","text":"..."}
  if (message.startsWith("System prompt active: ") || message.startsWith("Chat template active: ")) {
    val isSystem = message.startsWith("System prompt")
    val text = if (!eventBody.isNullOrBlank()) {
      try { JSONObject(eventBody).optString("text", "") } catch (_: Exception) { "" }
    } else ""
    return ParsedEventType.PromptActive(
      promptType = if (isSystem) "System prompt" else "Chat template",
      promptText = text,
    )
  }

  // Settings toggle: "SettingName enabled" / "SettingName disabled"
  if (message.endsWith(" enabled") || message.endsWith(" disabled")) {
    val enabled = message.endsWith(" enabled")
    val name = message.removeSuffix(if (enabled) " enabled" else " disabled")
    // Sanity check: the name shouldn't be empty or overly long (avoid false matches)
    if (name.isNotBlank() && name.length < 80) {
      return ParsedEventType.SettingsToggle(name, enabled)
    }
  }

  return null
}

// ── Event text highlighting ──────────────────────────────────────────────────

/** Accent color for arrows in settings/event values. */
private val ValueArrowColor = Color(0xFF64B5F6) // light blue
/** Color for quoted text in event messages. */
private val QuotedTextColor = Color(0xFFCE93D8) // soft purple

/**
 * Highlights key values in event messages using AnnotatedString.
 * Styles: timing "(Xms)", quoted strings, arrows "→", and model names after known prefixes.
 */
private fun highlightEventMessage(
  message: String,
  isError: Boolean,
  errorColor: Color,
): AnnotatedString {
  if (isError) {
    return buildAnnotatedString {
      withStyle(SpanStyle(color = errorColor, fontWeight = FontWeight.SemiBold)) { append(message) }
    }
  }

  data class StyledSpan(val start: Int, val end: Int, val style: SpanStyle)
  val spans = mutableListOf<StyledSpan>()
  val primaryColor = Color(0xFFAFC6FF)
  val greenColor = Color(0xFF4ADE80)
  val defaultText = Color(0xFFE5E2E3)

  Regex("""\(\d+ms\)""").findAll(message).forEach {
    spans.add(StyledSpan(it.range.first, it.range.last + 1,
      SpanStyle(color = greenColor, fontWeight = FontWeight.SemiBold)))
  }
  Regex("""→""").findAll(message).forEach {
    spans.add(StyledSpan(it.range.first, it.range.last + 1,
      SpanStyle(color = ValueArrowColor, fontWeight = FontWeight.Bold)))
  }
  Regex(""""[^"]*"""").findAll(message).forEach {
    spans.add(StyledSpan(it.range.first, it.range.last + 1,
      SpanStyle(color = QuotedTextColor)))
  }
  val modelPrefixes = listOf("Loading model: ", "Model ready: ", "Model load failed: ")
  for (prefix in modelPrefixes) {
    val idx = message.indexOf(prefix)
    if (idx >= 0) {
      val nameStart = idx + prefix.length
      val nameEnd = message.indexOf(" (", nameStart).let { if (it < 0) message.length else it }
      if (nameEnd > nameStart) {
        spans.add(StyledSpan(nameStart, nameEnd,
          SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold)))
      }
    }
  }

  val nonOverlapping = mutableListOf<StyledSpan>()
  var lastEnd = 0
  for (span in spans.sortedBy { it.start }) {
    if (span.start >= lastEnd) { nonOverlapping.add(span); lastEnd = span.end }
  }
  return buildAnnotatedString {
    var pos = 0
    for (span in nonOverlapping) {
      if (span.start > pos) withStyle(SpanStyle(color = defaultText)) { append(message.substring(pos, span.start)) }
      withStyle(span.style) { append(message.substring(span.start, span.end)) }
      pos = span.end
    }
    if (pos < message.length) withStyle(SpanStyle(color = defaultText)) { append(message.substring(pos)) }
  }
}

// ── Internal event card ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InternalEventCard(entry: RequestLogEntry) {
  val context = LocalContext.current
  val isError = entry.level == LogLevel.ERROR
  val isWarning = entry.level == LogLevel.WARNING
  val accentColor = when {
    isError -> DeleteRedTint
    isWarning -> WarningColor
    else -> EventColor
  }
  val message = entry.path

  val categoryLabel = when (entry.eventCategory) {
    EventCategory.MODEL -> "MODEL"
    EventCategory.SETTINGS -> "SETTINGS"
    EventCategory.SERVER -> "SERVER"
    EventCategory.PROMPT -> "PROMPT"
    EventCategory.GENERAL -> "EVENT"
  }
  val categoryIcon = when (entry.eventCategory) {
    EventCategory.MODEL -> Icons.Outlined.Memory
    EventCategory.SETTINGS -> Icons.Outlined.Settings
    EventCategory.SERVER -> Icons.Outlined.Dns
    EventCategory.PROMPT -> Icons.AutoMirrored.Outlined.Notes
    EventCategory.GENERAL -> Icons.Outlined.Info
  }

  val cardBg = when {
    isError -> DeleteRedTint.copy(alpha = 0.06f)
    isWarning -> WarningColor.copy(alpha = 0.06f)
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
    null -> null
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
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
          text = parsedEvent.modelName,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = OlliteRTPrimary,
          fontWeight = FontWeight.SemiBold,
        )
      }

      is ParsedEventType.Ready -> {
        Text(
          text = parsedEvent.modelName,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily, fontSize = 12.sp),
          color = OlliteRTPrimary,
          fontWeight = FontWeight.SemiBold,
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
        val newColor = if (parsedEvent.enabled) OlliteRTGreen400 else DeleteRedTint
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

      null -> {
        // Default: styled text with highlighted values
        val isLong = message.length > 120 || message.count { it == '\n' } > 2
        var expanded by remember { mutableStateOf(false) }
        val styledMessage = remember(message) { highlightEventMessage(message, isError, accentColor) }

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

    // ── Footer ──
    Spacer(modifier = Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
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
      Spacer(modifier = Modifier.weight(1f))
      Text(
        text = listOfNotNull(
          entry.modelName,
          formatTimestamp(entry.timestamp),
        ).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

// ── Settings change rows ─────────────────────────────────────────────────────

/**
 * Structured rows for settings changes (inference params or toggle states).
 * Each row: [param name]  [old → new], all full-width with consistent alignment.
 * @param newValueColorOverride optional color override for the new value text
 *   (e.g. green/red for enabled/disabled toggles)
 */
@Composable
private fun SettingsChangeRows(
  parsed: ParsedInferenceEvent,
  accentColor: Color,
  newValueColorOverride: Color? = null,
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
              color = newValueColorOverride ?: OlliteRTPrimary,
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
              color = newValueColorOverride ?: MaterialTheme.colorScheme.onSurface,
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
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("OlliteRT Event", json))
  Toast.makeText(context, "Event copied (JSON)", Toast.LENGTH_SHORT).show()
}

private const val COLLAPSED_MAX_LINES = 8
private const val COLLAPSED_MAX_CHARS = 600
/** Bodies above this size get highlighted asynchronously to avoid main-thread jank. */
private const val ASYNC_HIGHLIGHT_THRESHOLD = 4_000

@Composable
private fun LogEntryCard(entry: RequestLogEntry, autoExpand: Boolean = false) {
  val context = LocalContext.current
  val isError = entry.level == LogLevel.ERROR
  val isWarning = entry.level == LogLevel.WARNING
  val cardBg = when {
    entry.isCancelled -> CancelledColor.copy(alpha = 0.06f)
    isError -> DeleteRedTint.copy(alpha = 0.06f)
    isWarning -> WarningColor.copy(alpha = 0.06f)
    else -> MaterialTheme.colorScheme.surfaceContainerLow
  }

  var requestExpanded by remember { mutableStateOf(autoExpand) }
  var compactedExpanded by remember { mutableStateOf(autoExpand) }
  var responseExpanded by remember { mutableStateOf(autoExpand) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(cardBg)
      .padding(16.dp),
  ) {
    // Method badge + path + client IP pill + copy button
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      MethodBadge(method = entry.method)
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = entry.path,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      if (entry.clientIp != null) {
        Spacer(modifier = Modifier.width(8.dp))
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
      Spacer(modifier = Modifier.width(4.dp))
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
    // always shows normal generating text. If model-based compaction is added (F72), this should
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
        if (!entry.partialText.isNullOrEmpty()) {
          Text(
            text = entry.partialText,
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
      )
    }

    // Footer area: badges (compaction/overflow) are placed inline when they fit,
    // or on a separate row above when they'd overflow. Measured dynamically via
    // SubcomposeLayout so the layout scales correctly across phones and tablets.
    if (!entry.isPending) {
      val contextOverflow = remember(entry.responseBody) { isContextOverflowError(entry.responseBody) }

      Spacer(modifier = Modifier.height(10.dp))

      // Footer: status · latency · SSE · Thinking · Cancelled  ···  model · time
      // Context overflow is shown in the StatusBadge as "Context Exceeded" instead of "400 Bad Request".
      // Compaction badges (Compacted, Truncated, Trimmed) are shown below the
      // Compacted Prompt text box instead of here.
      Row(
        modifier = Modifier.fillMaxWidth(),
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
        Spacer(modifier = Modifier.weight(1f))
        Text(
          text = listOfNotNull(
            entry.modelName,
            formatTimestamp(entry.timestamp),
          ).joinToString(" · "),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
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
  val collapsedHighlighted = remember(body) {
    val preview = if (body.length > COLLAPSED_MAX_CHARS) body.substring(0, COLLAPSED_MAX_CHARS) else body
    highlightJson(preview)
  }

  if (expanded) {
    // For large bodies, compute highlighting off the main thread.
    // Small bodies (<4KB) are fast enough to highlight synchronously.
    val fullHighlighted by produceState(
      initialValue = if (body.length <= ASYNC_HIGHLIGHT_THRESHOLD) {
        highlightJson(body)
      } else {
        null // show plain text while computing
      },
      key1 = body,
    ) {
      if (body.length > ASYNC_HIGHLIGHT_THRESHOLD) {
        value = withContext(Dispatchers.Default) { highlightJson(body) }
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

/** Convert a single log entry to a JSONObject for structured export. */
private fun entryToJson(entry: RequestLogEntry): JSONObject {
  val obj = JSONObject()
  obj.put("id", entry.id)
  obj.put("timestamp", formatTimestamp(entry.timestamp))
  obj.put("timestamp_ms", entry.timestamp)
  obj.put("type", if (entry.method == "EVENT") "event" else "request")

  if (entry.method == "EVENT") {
    obj.put("message", entry.path)
    obj.put("category", entry.eventCategory.name.lowercase())
    obj.put("level", entry.level.name.lowercase())
    // Include structured event body (inference settings, prompt active, etc.)
    if (!entry.requestBody.isNullOrBlank()) {
      obj.put("data", tryParseJson(entry.requestBody))
    }
  } else {
    obj.put("method", entry.method)
    obj.put("path", entry.path)
    obj.put("status_code", entry.statusCode)
    obj.put("latency_ms", entry.latencyMs)
    obj.put("tokens", entry.tokens)
    obj.put("streaming", entry.isStreaming)
    if (entry.isThinking) obj.put("thinking", true)
    if (entry.isCompacted) {
      obj.put("compacted", true)
      if (!entry.compactionDetails.isNullOrBlank()) obj.put("compaction_details", entry.compactionDetails)
    }
    if (entry.isCancelled) {
      obj.put("cancelled", true)
      if (entry.cancelledByUser) obj.put("cancelled_by_user", true)
    }
    if (entry.inputTokenEstimate > 0) {
      obj.put("input_token_estimate", entry.inputTokenEstimate)
      obj.put("is_exact_token_count", entry.isExactTokenCount)
    }
    if (entry.maxContextTokens > 0) obj.put("max_context_tokens", entry.maxContextTokens)
    if (entry.clientIp != null) obj.put("client_ip", entry.clientIp)

    // Parse request/response bodies as JSON if possible, otherwise keep as string
    if (!entry.requestBody.isNullOrBlank()) {
      obj.put("request_body", tryParseJson(entry.requestBody))
    }
    if (!entry.compactedPrompt.isNullOrBlank()) {
      obj.put("compacted_prompt", entry.compactedPrompt)
    }
    if (!entry.responseBody.isNullOrBlank()) {
      obj.put("response_body", tryParseJson(entry.responseBody))
    }
  }

  if (entry.modelName != null) obj.put("model", entry.modelName)
  return obj
}

/** Try to parse a string as JSON (object or array); return the string as-is on failure. */
private fun tryParseJson(text: String): Any {
  val trimmed = text.trim()
  return try {
    if (trimmed.startsWith("{")) JSONObject(trimmed)
    else if (trimmed.startsWith("[")) JSONArray(trimmed)
    else text
  } catch (_: Exception) {
    text
  }
}

/** Build the full JSON export as a formatted string. */
private fun buildLogsJson(entries: List<RequestLogEntry>): String {
  val root = JSONObject()
  root.put("exported_at", formatTimestamp(System.currentTimeMillis()))
  root.put("app", "OlliteRT")
  root.put("entry_count", entries.size)
  val array = JSONArray()
  for (entry in entries) {
    array.put(entryToJson(entry))
  }
  root.put("entries", array)
  return root.toString(2)
}

/** Build JSON on a background thread to avoid UI jank with large log sets (2500+ entries). */
private suspend fun copyAllLogsToClipboard(context: Context, entries: List<RequestLogEntry>) {
  try {
    val json = withContext(Dispatchers.Default) { buildLogsJson(entries) }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("OlliteRT Logs", json))
    Toast.makeText(context, "All logs copied as JSON (${entries.size} entries)", Toast.LENGTH_SHORT).show()
  } catch (_: Exception) {
    // TransactionTooLargeException (or similar) — clipboard has a ~1MB Binder limit.
    // With many entries and large request/response bodies, the JSON can exceed this.
    Toast.makeText(
      context,
      "Log data too large for clipboard (${entries.size} entries) — use Export instead.",
      Toast.LENGTH_LONG,
    ).show()
  }
}

/** Build JSON and write file on a background thread to avoid UI jank with large log sets. */
private suspend fun exportLogsAsJson(context: Context, entries: List<RequestLogEntry>) {
  try {
    val file = withContext(Dispatchers.IO) {
      val json = buildLogsJson(entries)
      val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val cacheDir = File(context.cacheDir, "log_exports")
      cacheDir.mkdirs()
      val f = File(cacheDir, "ollitert_logs_$timestamp.json")
      f.writeText(json, Charsets.UTF_8)
      f
    }

    val uri = FileProvider.getUriForFile(
      context,
      "${context.packageName}.provider",
      file,
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/json"
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export OlliteRT Logs"))
  } catch (e: Exception) {
    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
  }
}

private fun copyEntryToClipboard(context: Context, entry: RequestLogEntry) {
  val json = entryToJson(entry).toString(2)
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("OlliteRT Log Entry", json))
  Toast.makeText(context, "Copied to clipboard (JSON)", Toast.LENGTH_SHORT).show()
}

@Composable
private fun MethodBadge(method: String) {
  val color = when (method) {
    "POST" -> OlliteRTPrimary
    "GET" -> OlliteRTGreen400
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Text(
    text = method,
    style = MaterialTheme.typography.labelSmall,
    color = color,
    fontWeight = FontWeight.Bold,
    fontFamily = SpaceGroteskFontFamily,
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(color.copy(alpha = 0.12f))
      .padding(horizontal = 8.dp, vertical = 3.dp),
  )
}

@Composable
private fun StatusBadge(statusCode: Int, contextOverflow: Boolean = false) {
  val isSuccess = statusCode in 200..299
  val color = when {
    contextOverflow -> ContextOverflowColor
    isSuccess -> OlliteRTGreen400
    else -> MaterialTheme.colorScheme.error
  }
  val label = when {
    // Context overflow gets a specific label regardless of status code
    contextOverflow -> "Context Exceeded"
    else -> {
      val reasonPhrase = when (statusCode) {
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        408 -> "Request Timeout"
        413 -> "Payload Too Large"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        else -> if (!isSuccess) "Error" else "OK"
      }
      "$statusCode $reasonPhrase"
    }
  }
  Text(
    text = label,
    style = MaterialTheme.typography.labelSmall,
    color = color,
    fontWeight = FontWeight.SemiBold,
    fontFamily = SpaceGroteskFontFamily,
  )
}

@Composable
private fun FooterDot() {
  Text(
    text = "·",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * Parses compactionDetails string (comma-separated strategy tags from [LlmHttpPromptCompactor])
 * into short badge labels with colors. Strategy tag format:
 *   "tools:compacted" → "Compacted: Tools"
 *   "tools:removed"   → "Compacted: Tools removed"
 *   "truncated:-4 msgs" → "Truncated: -4 msgs"
 *   "trimmed"         → "Trimmed"
 */
/**
 * @param short When true, produces short labels for the footer (e.g. "Compacted" instead of
 *   "Compacted: Tools"). The detailed variant is used in the Compacted Prompt section header.
 */
private fun parseCompactionBadges(details: String?, short: Boolean = false): List<Pair<String, Color>> {
  if (details.isNullOrBlank()) return emptyList()
  return details.split(", ").mapNotNull { tag ->
    when {
      tag.startsWith("tools:") -> {
        if (short) {
          "Compacted" to WarningColor
        } else {
          val suffix = tag.removePrefix("tools:")
          val label = when (suffix) {
            "compacted" -> "Compacted: Tools"
            "removed" -> "Compacted: Tools removed"
            else -> "Compacted: Tools"
          }
          label to WarningColor
        }
      }
      tag.startsWith("truncated:") -> {
        if (short) {
          "Truncated" to TruncatedColor
        } else {
          val suffix = tag.removePrefix("truncated:")
          "Truncated: $suffix" to TruncatedColor
        }
      }
      tag == "trimmed" -> "Trimmed" to WarningColor
      // Fallback for any unrecognized tag — show as-is in yellow
      else -> tag to WarningColor
    }
  }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTimestamp(millis: Long): String = timeFormat.format(Date(millis))

private fun formatByteSize(bytes: Int): String = when {
  bytes < 1024 -> "$bytes B"
  bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
  else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

// JSON syntax highlighting colors
private val JsonKeyColor = Color(0xFF82AAFF)      // blue — object keys
private val JsonStringColor = Color(0xFFC3E88D)   // green — string values
private val JsonNumberColor = Color(0xFFF78C6C)   // orange — numbers
private val JsonBoolNullColor = Color(0xFFFF5370)  // red/pink — true, false, null
private val JsonBraceColor = Color(0xFF89DDFF)     // cyan — brackets, braces, colons, commas

private val jsonTokenRegex = Regex(
  """("(?:[^"\\]|\\.)*")\s*:|("(?:[^"\\]|\\.)*")|([-+]?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|(\btrue\b|\bfalse\b|\bnull\b)|([{}\[\]:,])""",
)

private fun highlightJson(text: String): AnnotatedString = buildAnnotatedString {
  val fallbackColor = Color(0xFFBDBDBD) // light grey for non-JSON / whitespace
  var lastIndex = 0
  for (match in jsonTokenRegex.findAll(text)) {
    // Append any text between tokens (whitespace, newlines) in fallback color
    if (match.range.first > lastIndex) {
      withStyle(SpanStyle(color = fallbackColor)) {
        append(text.substring(lastIndex, match.range.first))
      }
    }
    val (key, string, number, boolNull, brace) = match.destructured
    when {
      key.isNotEmpty() -> {
        withStyle(SpanStyle(color = JsonKeyColor)) { append(key) }
        // Append the colon (and any whitespace) that follows the key
        val keyEnd = match.groupValues[1].let { match.range.first + it.length }
        val trailing = text.substring(keyEnd, match.range.last + 1) // e.g. ": "
        withStyle(SpanStyle(color = JsonBraceColor)) { append(trailing) }
      }
      string.isNotEmpty() -> withStyle(SpanStyle(color = JsonStringColor)) { append(string) }
      number.isNotEmpty() -> withStyle(SpanStyle(color = JsonNumberColor)) { append(number) }
      boolNull.isNotEmpty() -> withStyle(SpanStyle(color = JsonBoolNullColor, fontWeight = FontWeight.SemiBold)) { append(boolNull) }
      brace.isNotEmpty() -> withStyle(SpanStyle(color = JsonBraceColor)) { append(brace) }
    }
    lastIndex = match.range.last + 1
  }
  // Append any trailing text
  if (lastIndex < text.length) {
    withStyle(SpanStyle(color = fallbackColor)) {
      append(text.substring(lastIndex))
    }
  }
}

/** Try to pretty-print JSON; return the original string if it's not valid JSON. */
private fun prettyPrintJson(raw: String): String = try {
  val trimmed = raw.trimStart()
  if (trimmed.startsWith("{")) {
    JSONObject(trimmed).toString(2)
  } else if (trimmed.startsWith("[")) {
    JSONArray(trimmed).toString(2)
  } else {
    raw
  }
} catch (_: Exception) {
  raw
}
