package com.ollitert.llm.server.ui.server.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.ui.server.ContextOverflowColor
import com.ollitert.llm.server.ui.server.TruncatedColor
import com.ollitert.llm.server.ui.server.WarningColor
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MethodBadge(method: String) {
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
internal fun StatusBadge(statusCode: Int, contextOverflow: Boolean = false) {
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
internal fun FooterDot() {
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
internal fun parseCompactionBadges(details: String?, short: Boolean = false): List<Pair<String, Color>> {
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

internal val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

internal fun formatTimestamp(millis: Long): String = timeFormat.format(Date(millis))

internal fun formatByteSize(bytes: Int): String = when {
  bytes < 1024 -> "$bytes B"
  bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
  else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

