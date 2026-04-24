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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.service.ErrorKind
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
internal fun StatusBadge(statusCode: Int, contextOverflow: Boolean = false, errorKind: ErrorKind? = null) {
  val isSuccess = statusCode in 200..299
  val color = when {
    contextOverflow -> ContextOverflowColor
    errorKind != null -> MaterialTheme.colorScheme.error
    isSuccess -> OlliteRTGreen400
    else -> MaterialTheme.colorScheme.error
  }
  val label = when {
    contextOverflow -> stringResource(R.string.logs_status_context_exceeded)
    errorKind != null -> errorKindLabel(errorKind, statusCode)
    else -> {
      val reasonPhrase = when (statusCode) {
        400 -> stringResource(R.string.logs_status_bad_request)
        401 -> stringResource(R.string.logs_status_unauthorized)
        403 -> stringResource(R.string.logs_status_forbidden)
        404 -> stringResource(R.string.logs_status_not_found)
        405 -> stringResource(R.string.logs_status_method_not_allowed)
        408 -> stringResource(R.string.logs_status_request_timeout)
        413 -> stringResource(R.string.logs_status_payload_too_large)
        429 -> stringResource(R.string.logs_status_too_many_requests)
        500 -> stringResource(R.string.logs_status_internal_server_error)
        502 -> stringResource(R.string.logs_status_bad_gateway)
        503 -> stringResource(R.string.logs_status_service_unavailable)
        504 -> stringResource(R.string.logs_status_gateway_timeout)
        else -> if (!isSuccess) stringResource(R.string.logs_status_error) else stringResource(R.string.logs_status_ok)
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
private fun errorKindLabel(errorKind: ErrorKind, statusCode: Int): String = when (errorKind) {
  ErrorKind.CONTEXT_OVERFLOW -> stringResource(R.string.logs_status_context_exceeded)
  ErrorKind.TIMEOUT -> stringResource(R.string.logs_error_kind_timeout)
  ErrorKind.OOM -> stringResource(R.string.logs_error_kind_oom)
  ErrorKind.MODEL_NOT_FOUND -> stringResource(R.string.logs_error_kind_model_not_found)
  ErrorKind.MODEL_FILES_MISSING -> stringResource(R.string.logs_error_kind_model_files_missing)
  ErrorKind.MODEL_INSTANCE_NULL -> stringResource(R.string.logs_error_kind_model_not_ready)
  ErrorKind.IMAGE_DECODE_FAILED -> stringResource(R.string.logs_error_kind_image_decode_failed)
  ErrorKind.UNKNOWN_LITERT -> stringResource(R.string.logs_error_kind_inference_error)
  ErrorKind.UNKNOWN -> "$statusCode ${stringResource(R.string.logs_status_error)}"
  ErrorKind.PORT_BIND_FAILURE -> "$statusCode ${stringResource(R.string.logs_status_error)}"
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
