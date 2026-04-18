/*
 * Copyright 2025 Google LLC
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

package com.ollitert.llm.server.ui.common

import com.ollitert.llm.server.R
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush.Companion.linearGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.data.Task
import com.ollitert.llm.server.ui.modelmanager.ModelManagerViewModel
import java.io.File
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.delay

private const val TAG = "OlliteRTUiUtils"

// ── Shared UI dimension constants ──────────────────────────────────────────────
/** Max width for ModalBottomSheet content across the app. */
val SHEET_MAX_WIDTH = 640.dp
/** Max width for screen-level scrollable content (Models, Status, Logs, Settings). */
val SCREEN_CONTENT_MAX_WIDTH = 840.dp

/**
 * Copy text to the system clipboard with a standardized toast notification.
 *
 * @param label Clipboard metadata label (prefix with "OlliteRT", e.g. "OlliteRT Endpoint").
 *              Visible in clipboard manager apps, not shown to the user directly.
 * @param text The content to copy.
 * @param formatSuffix Optional format hint appended to the toast (e.g. "JSON", "CSV").
 *                     Omit for simple values like URLs or tokens.
 */
fun copyToClipboard(context: Context, label: String, text: String, formatSuffix: String? = null, toastOverride: String? = null) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
  if (clipboard == null) {
    Toast.makeText(context, context.getString(R.string.toast_clipboard_unavailable), Toast.LENGTH_SHORT).show()
    return
  }
  clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
  val toast = toastOverride
    ?: if (formatSuffix != null) context.getString(R.string.toast_copied_to_clipboard_format, formatSuffix)
    else context.getString(R.string.toast_copied_to_clipboard)
  Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}

/** Consistent error text for model loading failures across all screens. */
fun formatModelError(context: Context, error: String?): String = when {
  error.isNullOrBlank() -> context.getString(R.string.error_model_load_failed)
  error.length > 80 -> context.getString(R.string.error_model_load_failed_short)
  else -> context.getString(R.string.error_model_load_failed_detail, error)
}

/** Format a byte count as a human-readable string (B/KB/MB), using binary 1024 thresholds. */
internal fun formatByteSize(bytes: Long): String = when {
  bytes < 1024L -> "$bytes B"
  bytes < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
  else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Int overload for Compose contexts where sizes come as Int (e.g. String.length). */
internal fun formatByteSize(bytes: Int): String = formatByteSize(bytes.toLong())

val SMALL_BUTTON_CONTENT_PADDING =
  PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)

/** Format the bytes into a human-readable format. */
fun Long.humanReadableSize(si: Boolean = true, extraDecimalForGbAndAbove: Boolean = false): String {
  val bytes = this

  val unit = if (si) 1000 else 1024
  if (bytes < unit) return "$bytes B"
  val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
  val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
  var formatString = "%.1f %sB"
  if (extraDecimalForGbAndAbove && pre.lowercase() != "k" && pre != "M") {
    formatString = "%.2f %sB"
  }
  return formatString.format(bytes / unit.toDouble().pow(exp.toDouble()), pre)
}

fun Long.formatToHourMinSecond(): String {
  val ms = this
  if (ms < 0) {
    return "-"
  }

  val seconds = ms / 1000
  val hours = seconds / 3600
  val minutes = (seconds % 3600) / 60
  val remainingSeconds = seconds % 60

  val parts = mutableListOf<String>()

  if (hours > 0) {
    parts.add("$hours h")
  }
  if (minutes > 0) {
    parts.add("$minutes min")
  }
  if (remainingSeconds > 0 || (hours == 0L && minutes == 0L)) {
    parts.add("$remainingSeconds sec")
  }

  return parts.joinToString(" ")
}

fun Context.createTempPictureUri(
  fileName: String = "picture_${System.currentTimeMillis()}",
  fileExtension: String = ".png",
): Uri {
  val tempFile = File.createTempFile(fileName, fileExtension, cacheDir).apply { createNewFile() }

  return FileProvider.getUriForFile(
    applicationContext,
    "com.ollitert.llm.server.provider" /* {applicationId}.provider */,
    tempFile,
  )
}

fun checkNotificationPermissionAndStartDownload(
  context: Context,
  launcher: ManagedActivityResultLauncher<String, Boolean>,
  modelManagerViewModel: ModelManagerViewModel,
  task: Task?,
  model: Model,
) {
  // Check permission
  when (PackageManager.PERMISSION_GRANTED) {
    // Already got permission. Call the lambda.
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) -> {
      modelManagerViewModel.downloadModel(task = task, model = model)
    }

    // Otherwise, ask for permission
    else -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }
}

fun ensureValidFileName(fileName: String): String {
  return fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}

/**
 * A composable that animates the revelation of text using a linear gradient mask.
 *
 * The text appears to "wipe" into view from left to right, controlled by an animation progress.
 * This is achieved by drawing a gradient mask over the text that moves horizontally, revealing the
 * content as the animation progresses.
 *
 * The core of the revelation effect relies on `BlendMode.DstOut`. First, the text content
 * (`drawContent()`) is rendered as the "destination." Then, a rectangle filled with a `maskBrush`
 * (our linear gradient) is drawn as the "source." `DstOut` works by taking the destination (the
 * text) and making transparent any parts that overlap with the opaque (non-transparent) regions of
 * the source (the red part of our mask). As the `maskBrush` animates and slides across the text,
 * the transparent portion of the mask "reveals" the text, creating the wipe-in effect.
 */
@Composable
fun RevealingText(
  text: String,
  style: TextStyle,
  modifier: Modifier = Modifier,
  annotatedText: AnnotatedString? = null,
  animationDelay: Long = 0,
  animationDurationMs: Int = 300,
  edgeGradientRelativeSize: Float = 0.5f,
  extraTextPadding: Dp = 16.dp,
) {
  val progress =
    rememberDelayedAnimationProgress(
      initialDelay = animationDelay,
      animationDurationMs = animationDurationMs,
      animationLabel = "revealing text",
    )
  val maskBrush =
    linearGradient(
      colorStops =
        arrayOf(
          (1f + edgeGradientRelativeSize) * progress - edgeGradientRelativeSize to
            Color.Transparent,
          (1f + edgeGradientRelativeSize) * progress to Color.Red,
        )
    )
  Box(
    modifier =
      modifier
        .graphicsLayer(alpha = 0.99f, compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
          drawContent()
          drawRect(brush = maskBrush, blendMode = BlendMode.DstOut)
        },
    contentAlignment = Alignment.Center,
  ) {
    if (annotatedText != null) {
      Text(annotatedText, style = style, modifier = Modifier.padding(horizontal = extraTextPadding))
    } else {
      Text(text, style = style, modifier = Modifier.padding(horizontal = extraTextPadding))
    }
  }
}

/** Another version of RevealingText with animationProgress passed in. */
@Composable
fun RevealingText(
  text: String,
  style: TextStyle,
  animationProgress: Float,
  modifier: Modifier = Modifier,
  textAlign: TextAlign? = null,
  edgeGradientRelativeSize: Float = 0.5f,
) {
  val maskBrush =
    linearGradient(
      colorStops =
        arrayOf(
          (1f + edgeGradientRelativeSize) * animationProgress - edgeGradientRelativeSize to
            Color.Transparent,
          (1f + edgeGradientRelativeSize) * animationProgress to Color.Red,
        )
    )
  Box(
    modifier =
      modifier
        .graphicsLayer(alpha = 0.99f, compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
          drawContent()
          drawRect(brush = maskBrush, blendMode = BlendMode.DstOut)
        },
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text,
      style = style,
      modifier = modifier.padding(horizontal = 16.dp),
      textAlign = textAlign,
    )
  }
}

/**
 * A reusable Composable function that provides an animated float progress value after an initial
 * delay.
 *
 * This function is ideal for creating "enter" animations that start after a specified pause,
 * allowing for staggered or timed visual effects. It uses `animateFloatAsState` to smoothly
 * transition the progress from 0f to 1f.
 */
@Composable
fun rememberDelayedAnimationProgress(
  initialDelay: Long = 0,
  animationDurationMs: Int,
  animationLabel: String,
  easing: Easing = FastOutSlowInEasing,
): Float {
  var startAnimation by remember { mutableStateOf(false) }
  val progress: Float by
    animateFloatAsState(
      if (startAnimation) 1f else 0f,
      label = animationLabel,
      animationSpec = tween(durationMillis = animationDurationMs, easing = easing),
    )
  LaunchedEffect(Unit) {
    delay(initialDelay)
    startAnimation = true
  }
  return progress
}
