package com.ollitert.llm.server.ui.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.service.LogLevel
import com.ollitert.llm.server.service.RequestLogEntry
import com.ollitert.llm.server.service.RequestLogStore
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DeleteRedTint = Color(0xFFE57373)
private val EventColor = Color(0xFF90A4AE) // blue-grey for internal events

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
        text = "Request Log",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      if (entries.isNotEmpty()) {
        val context = LocalContext.current
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // Clear all logs
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(DeleteRedTint.copy(alpha = 0.12f))
              .clickable { RequestLogStore.clear() },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteSweep,
              contentDescription = "Clear logs",
              tint = DeleteRedTint,
              modifier = Modifier.size(22.dp),
            )
          }
          // Copy all logs
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceContainerHighest)
              .clickable { copyAllLogsToClipboard(context, entries) },
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Outlined.ContentCopy,
              contentDescription = "Copy all logs",
              tint = OlliteRTPrimary,
              modifier = Modifier.size(22.dp),
            )
          }
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
            text = "No requests yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "API traffic will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      val listState = rememberLazyListState()
      val coroutineScope = rememberCoroutineScope()

      // Track whether the top of the list is visible
      val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
      }

      // Auto-scroll is ON by default — stays on until user manually scrolls away
      var autoScrollEnabled by remember { mutableStateOf(true) }

      // Count of unseen new entries (only tracked when auto-scroll is off)
      var unseenCount by remember { mutableIntStateOf(0) }

      // Detect user-initiated scrolling: if user scrolls away from top, disable auto-scroll
      val isScrollInProgress by remember {
        derivedStateOf { listState.isScrollInProgress }
      }
      LaunchedEffect(isScrollInProgress, isAtTop) {
        if (isScrollInProgress && !isAtTop) {
          autoScrollEnabled = false
        }
      }

      // When new entries arrive: auto-scroll if enabled, otherwise bump unseen count
      LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
          if (autoScrollEnabled) {
            listState.animateScrollToItem(0)
          } else {
            unseenCount++
          }
        }
      }

      // When user scrolls back to top manually, clear unseen and re-enable auto-scroll
      LaunchedEffect(isAtTop) {
        if (isAtTop) {
          unseenCount = 0
          autoScrollEnabled = true
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

        // Floating "new logs" indicator
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
                autoScrollEnabled = true
                unseenCount = 0
                coroutineScope.launch {
                  listState.animateScrollToItem(0)
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
              text = if (unseenCount == 1) "New log" else "$unseenCount new logs",
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

@Composable
private fun InternalEventCard(entry: RequestLogEntry) {
  val isError = entry.level == LogLevel.ERROR
  val accentColor = if (isError) DeleteRedTint else EventColor

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(accentColor.copy(alpha = 0.08f))
      .padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = Icons.Outlined.Info,
      contentDescription = null,
      tint = accentColor,
      modifier = Modifier.size(16.dp),
    )
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = entry.path, // message is stored in path field
        style = MaterialTheme.typography.bodySmall,
        color = accentColor,
        fontWeight = if (isError) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Text(
      text = formatTimestamp(entry.timestamp),
      style = MaterialTheme.typography.labelSmall,
      color = accentColor.copy(alpha = 0.7f),
    )
  }
}

private const val COLLAPSED_MAX_LINES = 8
private const val COLLAPSED_MAX_CHARS = 600

@Composable
private fun LogEntryCard(entry: RequestLogEntry, autoExpand: Boolean = false) {
  val context = LocalContext.current
  val isError = entry.level == LogLevel.ERROR
  val cardBg = if (isError) {
    DeleteRedTint.copy(alpha = 0.06f)
  } else {
    MaterialTheme.colorScheme.surfaceContainerLow
  }

  var requestExpanded by remember { mutableStateOf(autoExpand) }
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
      IconButton(
        onClick = { copyEntryToClipboard(context, entry) },
        modifier = Modifier.size(32.dp),
      ) {
        Icon(
          imageVector = Icons.Outlined.ContentCopy,
          contentDescription = "Copy request",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(16.dp),
        )
      }
    }

    // Request body preview (if present)
    if (!entry.requestBody.isNullOrBlank()) {
      val formatted = remember(entry.requestBody) { prettyPrintJson(entry.requestBody) }
      val isLong = formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = "Request",
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        body = formatted,
        expanded = requestExpanded,
        showToggle = isLong,
        onToggle = { requestExpanded = !requestExpanded },
      )
    }

    // Response area: typing indicator while pending, body when available
    if (entry.isPending) {
      val generatingText = remember(entry.id) { GeneratingMessages.pick() }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "Response",
        style = MaterialTheme.typography.labelSmall,
        color = OlliteRTPrimary,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerLowest)
          .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
    } else if (!entry.responseBody.isNullOrBlank()) {
      val formatted = remember(entry.responseBody) { prettyPrintJson(entry.responseBody) }
      val isLong = formatted.length > COLLAPSED_MAX_CHARS || formatted.count { it == '\n' } > COLLAPSED_MAX_LINES
      Spacer(modifier = Modifier.height(10.dp))
      ExpandableBodySection(
        label = "Response",
        labelColor = OlliteRTPrimary,
        body = formatted,
        expanded = responseExpanded,
        showToggle = isLong,
        onToggle = { responseExpanded = !responseExpanded },
      )
    }

    // Footer: status + latency + streaming indicator (hidden while pending)
    if (!entry.isPending) {
      Spacer(modifier = Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        StatusBadge(statusCode = entry.statusCode)
        Text(
          text = "${entry.latencyMs}ms",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (entry.isStreaming) {
          Text(
            text = "SSE",
            style = MaterialTheme.typography.labelSmall,
            color = OlliteRTPrimary,
            fontWeight = FontWeight.SemiBold,
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
) {
  Text(
    text = label,
    style = MaterialTheme.typography.labelSmall,
    color = labelColor,
    fontWeight = FontWeight.SemiBold,
  )
  Spacer(modifier = Modifier.height(4.dp))
  val highlighted = remember(body) { highlightJson(body) }
  if (expanded) {
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
          Text(
            text = highlighted,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = 11.sp,
              lineHeight = 16.sp,
            ),
          )
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
          text = remember(highlighted) {
            if (highlighted.length > COLLAPSED_MAX_CHARS) highlighted.subSequence(0, COLLAPSED_MAX_CHARS)
            else highlighted
          },
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

private fun copyAllLogsToClipboard(context: Context, entries: List<RequestLogEntry>) {
  val text = entries.joinToString("\n${"=".repeat(60)}\n") { entry ->
    buildString {
      if (entry.method == "EVENT") {
        appendLine("[${formatTimestamp(entry.timestamp)}] EVENT: ${entry.path}")
        if (entry.modelName != null) appendLine("Model: ${entry.modelName}")
      } else {
        appendLine("[${formatTimestamp(entry.timestamp)}] ${entry.method} ${entry.path}")
        appendLine("Status: ${entry.statusCode} | Latency: ${entry.latencyMs}ms")
        if (entry.modelName != null) appendLine("Model: ${entry.modelName}")
        if (entry.clientIp != null) appendLine("Client: ${entry.clientIp}")
        if (!entry.requestBody.isNullOrBlank()) {
          appendLine("--- Request ---")
          appendLine(entry.requestBody)
        }
        if (!entry.responseBody.isNullOrBlank()) {
          appendLine("--- Response ---")
          appendLine(entry.responseBody)
        }
      }
    }.trimEnd()
  }
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("OlliteRT Logs", text))
  Toast.makeText(context, "All logs copied (${entries.size} entries)", Toast.LENGTH_SHORT).show()
}

private fun copyEntryToClipboard(context: Context, entry: RequestLogEntry) {
  val text = buildString {
    appendLine("[${formatTimestamp(entry.timestamp)}] ${entry.method} ${entry.path}")
    appendLine("Status: ${entry.statusCode} | Latency: ${entry.latencyMs}ms")
    if (entry.modelName != null) appendLine("Model: ${entry.modelName}")
    if (entry.clientIp != null) appendLine("Client: ${entry.clientIp}")
    if (entry.isStreaming) appendLine("Streaming: SSE")
    if (!entry.requestBody.isNullOrBlank()) {
      appendLine("\n--- Request ---")
      appendLine(entry.requestBody)
    }
    if (!entry.responseBody.isNullOrBlank()) {
      appendLine("\n--- Response ---")
      appendLine(entry.responseBody)
    }
  }
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText("OlliteRT Log Entry", text))
  Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
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
private fun StatusBadge(statusCode: Int) {
  val isSuccess = statusCode in 200..299
  val color = if (isSuccess) OlliteRTGreen400 else MaterialTheme.colorScheme.error
  val label = if (isSuccess) "$statusCode OK" else "$statusCode"
  Text(
    text = label,
    style = MaterialTheme.typography.labelSmall,
    color = color,
    fontWeight = FontWeight.SemiBold,
    fontFamily = SpaceGroteskFontFamily,
  )
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private fun formatTimestamp(millis: Long): String = timeFormat.format(Date(millis))

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
