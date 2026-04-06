package com.ollite.llm.server.ui.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollite.llm.server.service.RequestLogEntry
import com.ollite.llm.server.service.RequestLogStore
import com.ollite.llm.server.ui.theme.OlliteGreen400
import com.ollite.llm.server.ui.theme.OllitePrimary
import com.ollite.llm.server.ui.theme.SpaceGroteskFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogsScreen(
  modifier: Modifier = Modifier,
) {
  val entries by RequestLogStore.entries.collectAsState()

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
        IconButton(onClick = { RequestLogStore.clear() }) {
          Icon(
            imageVector = Icons.Outlined.DeleteSweep,
            contentDescription = "Clear logs",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

      // Auto-scroll to top when a new entry arrives
      LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
          listState.animateScrollToItem(0)
        }
      }

      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(entries, key = { it.id }) { entry ->
          LogEntryCard(entry)
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
      }
    }
  }
}

@Composable
private fun LogEntryCard(entry: RequestLogEntry) {
  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(16.dp),
  ) {
    // Top row: method badge + path + timestamp + copy button
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
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = formatTimestamp(entry.timestamp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "Request",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      SelectionContainer {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp)
            .horizontalScroll(rememberScrollState()),
        ) {
          Text(
            text = entry.requestBody.take(500),
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = 11.sp,
              lineHeight = 16.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    // Response body preview (if present)
    if (!entry.responseBody.isNullOrBlank()) {
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "Response",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      SelectionContainer {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp)
            .horizontalScroll(rememberScrollState()),
        ) {
          Text(
            text = entry.responseBody.take(500),
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = 11.sp,
              lineHeight = 16.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    // Footer: status + latency + streaming indicator
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
          color = OllitePrimary,
          fontWeight = FontWeight.SemiBold,
        )
      }
      if (entry.modelName != null) {
        Spacer(modifier = Modifier.weight(1f))
        Text(
          text = entry.modelName,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private fun copyEntryToClipboard(context: Context, entry: RequestLogEntry) {
  val text = buildString {
    appendLine("${entry.method} ${entry.path}")
    appendLine("Status: ${entry.statusCode} | Latency: ${entry.latencyMs}ms")
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
  clipboard.setPrimaryClip(ClipData.newPlainText("Ollite Log Entry", text))
  Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Composable
private fun MethodBadge(method: String) {
  val color = when (method) {
    "POST" -> OllitePrimary
    "GET" -> OlliteGreen400
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
  val color = if (isSuccess) OlliteGreen400 else MaterialTheme.colorScheme.error
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
