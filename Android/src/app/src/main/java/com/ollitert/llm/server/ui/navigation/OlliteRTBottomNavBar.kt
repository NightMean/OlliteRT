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

package com.ollitert.llm.server.ui.navigation

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.ollitert.llm.server.service.ServerMetrics
import com.ollitert.llm.server.ui.common.SYSTEM_RESERVED_MEMORY_IN_BYTES
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.common.humanReadableSize
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTSurfaceContainerLowest
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

enum class OlliteRTTab(val labelResId: Int, val icon: ImageVector, val route: String) {
  Models(R.string.nav_tab_models, Icons.Outlined.ViewInAr, OlliteRTRoutes.MODELS),
  Status(R.string.nav_tab_status, Icons.Outlined.Analytics, OlliteRTRoutes.STATUS),
  Logs(R.string.nav_tab_logs, Icons.Outlined.Terminal, OlliteRTRoutes.LOGS),
}

@Composable
fun OlliteRTBottomNavBar(
  currentRoute: String?,
  onTabSelected: (OlliteRTTab) -> Unit,
  modifier: Modifier = Modifier,
  storageUpdateTrigger: Long = 0L,
) {
  val showStorageBar = currentRoute == OlliteRTRoutes.MODELS
  val showMemoryBar = currentRoute == OlliteRTRoutes.STATUS

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
      .background(OlliteRTSurfaceContainerLowest)
      // Push nav content above the system navigation bar while the background
      // color extends behind it (background() is applied before the inset padding).
      .navigationBarsPadding(),
  ) {
    // Storage bar - only on Models page
    if (showStorageBar) {
      StorageBar(storageUpdateTrigger = storageUpdateTrigger)
    }

    // Memory bar - only on Status page
    if (showMemoryBar) {
      MemoryBar()
    }

    // Navigation tabs
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 8.dp)
        .selectableGroup(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      OlliteRTTab.entries.forEach { tab ->
        val selected = currentRoute == tab.route
        OlliteRTNavItem(
          tab = tab,
          selected = selected,
          onClick = { onTabSelected(tab) },
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun StorageBar(storageUpdateTrigger: Long = 0L) {
  val storageInfo = remember(storageUpdateTrigger) { getStorageInfo() }
  val barColor = OlliteRTPrimary
  val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
      .padding(top = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Outlined.Storage,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    // Shows effective available space (free minus system reserve) so the user
    // sees what's actually usable for model downloads — matches the check in
    // DownloadAndTryButton.isStorageLow() which also subtracts the reserve.
    Text(
      text = stringResource(R.string.nav_storage_free_of, storageInfo.effectiveFreeBytes.humanReadableSize(), storageInfo.totalBytes.humanReadableSize()),
      style = MaterialTheme.typography.labelMedium,
      fontFamily = SpaceGroteskFontFamily,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(8.dp))
    // Progress bar shares space with text via weight — min/max keeps it readable at extremes
    Box(
      modifier = Modifier
        .weight(0.4f)
        .widthIn(min = 80.dp, max = 200.dp)
        .height(5.dp)
        .clip(RoundedCornerShape(3.dp))
        .drawBehind {
          // Track (full background)
          drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(3.dp.toPx()),
          )
          // Filled portion (used space)
          val filledWidth = size.width * storageInfo.usedFraction
          if (filledWidth > 0f) {
            drawRoundRect(
              color = barColor,
              size = Size(filledWidth, size.height),
              cornerRadius = CornerRadius(3.dp.toPx()),
            )
          }
        },
    )
  }
}

/**
 * RAM usage bar — shows device available RAM with a usage bar, plus actual app RAM footprint.
 * Polls every 3 seconds on [Dispatchers.IO]:
 * - Device RAM via [ActivityManager.getMemoryInfo]
 * - App PSS via [android.os.Debug.getPss] — not rate-limited unlike
 *   [ActivityManager.getProcessMemoryInfo] which Android throttles to ~5 min intervals.
 *
 * PSS (Proportional Set Size) is the actual physical RAM used by the app — includes
 * JVM heap, native heap, AND mmap'd model pages that are resident in RAM. LiteRT loads
 * models via mmap(), so only the actively-used pages consume RAM (a 2.7 GB model file
 * may only use ~1 GB depending on device memory pressure and inference patterns).
 *
 * Also pushes snapshots to [ServerMetrics] so Prometheus /metrics can expose them.
 */
@Composable
private fun MemoryBar() {
  val context = LocalContext.current

  // Device RAM + app PSS polled every 3 seconds
  var deviceAvailBytes by remember { mutableLongStateOf(0L) }
  var deviceTotalBytes by remember { mutableLongStateOf(0L) }
  var appPssBytes by remember { mutableLongStateOf(0L) }

  // Lifecycle-aware polling: stops reading /proc when the app is backgrounded (STOPPED),
  // resumes automatically when foregrounded (STARTED). Without this, the while(true) loop
  // continues polling memory stats every 3s even when the UI is invisible — wasting CPU/battery.
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner) {
    val activityManager = context.getSystemService(Activity.ACTIVITY_SERVICE) as? ActivityManager
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      while (true) {
        // Run all memory reads on IO thread — getMemoryInfo reads /proc, getPss reads /proc/self/smaps
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
          // Device-level RAM
          val memInfo = ActivityManager.MemoryInfo()
          activityManager?.getMemoryInfo(memInfo)
          deviceAvailBytes = memInfo.availMem
          deviceTotalBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            memInfo.advertisedMem
          } else {
            memInfo.totalMem
          }

          // Process PSS via Debug.getPss() — returns KB, not rate-limited.
          // Unlike ActivityManager.getProcessMemoryInfo() which Android throttles
          // to ~5 minute intervals, Debug.getPss() reads /proc/self/smaps_rollup
          // directly and reflects changes within seconds.
          val pssKb = android.os.Debug.getPss()
          appPssBytes = pssKb * 1024L

          // Push to ServerMetrics for Prometheus /metrics exposure
          val rt = Runtime.getRuntime()
          ServerMetrics.updateMemorySnapshot(
            nativeHeapBytes = android.os.Debug.getNativeHeapAllocatedSize(),
            appHeapUsedBytes = rt.totalMemory() - rt.freeMemory(),
            appTotalPssBytes = appPssBytes,
            deviceAvailRamBytes = deviceAvailBytes,
            deviceTotalRamBytes = deviceTotalBytes,
          )
        }
        delay(3000)
      }
    }
  }

  // Don't render until first poll completes
  if (deviceTotalBytes <= 0) return

  val barColor = OlliteRTPrimary
  val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
  val usedFraction = ((deviceTotalBytes - deviceAvailBytes).toFloat() / deviceTotalBytes.toFloat())
    .coerceIn(0f, 1f)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp)
      .padding(top = 12.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Outlined.Memory,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
    Spacer(modifier = Modifier.width(8.dp))
    // Text block uses weight(1f) so it truncates before pushing the progress bar off-screen
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.nav_storage_free_of, deviceAvailBytes.humanReadableSize(), deviceTotalBytes.humanReadableSize()),
        style = MaterialTheme.typography.labelMedium,
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      // Show actual app RAM (PSS) — includes resident mmap'd model pages
      if (appPssBytes > 0) {
        Text(
          text = stringResource(R.string.nav_memory_app_pss, appPssBytes.humanReadableSize()),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          maxLines = 1,
        )
      }
    }
    Spacer(modifier = Modifier.width(8.dp))
    // Progress bar shares space with text via weight — min/max keeps it readable at extremes
    Box(
      modifier = Modifier
        .weight(0.4f)
        .widthIn(min = 80.dp, max = 200.dp)
        .height(5.dp)
        .clip(RoundedCornerShape(3.dp))
        .drawBehind {
          // Track (full background)
          drawRoundRect(
            color = trackColor,
            cornerRadius = CornerRadius(3.dp.toPx()),
          )
          // Filled portion (used RAM)
          val filledWidth = size.width * usedFraction
          if (filledWidth > 0f) {
            drawRoundRect(
              color = barColor,
              size = Size(filledWidth, size.height),
              cornerRadius = CornerRadius(3.dp.toPx()),
            )
          }
        },
    )
  }
}

private data class StorageInfo(
  val totalBytes: Long,
  val freeBytes: Long,
  /** Free space minus the 3 GB system reserve — what's actually usable for model downloads. */
  val effectiveFreeBytes: Long,
  val usedFraction: Float,
)

private fun getStorageInfo(): StorageInfo {
  return try {
    val stat = StatFs(Environment.getDataDirectory().path)
    val total = stat.totalBytes
    val free = stat.availableBytes
    val effectiveFree = (free - SYSTEM_RESERVED_MEMORY_IN_BYTES).coerceAtLeast(0L)
    val used = (total - free).toFloat() / total.toFloat()
    StorageInfo(
      totalBytes = total,
      freeBytes = free,
      effectiveFreeBytes = effectiveFree,
      usedFraction = used.coerceIn(0f, 1f),
    )
  } catch (e: Exception) {
    StorageInfo(totalBytes = 0L, freeBytes = 0L, effectiveFreeBytes = 0L, usedFraction = 0f)
  }
}

@Composable
private fun OlliteRTNavItem(
  tab: OlliteRTTab,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val targetTextColor = if (selected) OlliteRTPrimary else MaterialTheme.colorScheme.onSurfaceVariant
  val animatedTextColor by animateColorAsState(
    targetValue = targetTextColor,
    animationSpec = tween(250),
    label = "navItemColor",
  )

  // Full touch area — defaultMinSize instead of fixed height to save vertical space on landscape
  Box(
    modifier = modifier
      .defaultMinSize(minHeight = 56.dp)
      .padding(horizontal = 4.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
      ),
    contentAlignment = Alignment.Center,
  ) {
    // Smaller visible selected indicator
    if (selected) {
      Box(
        modifier = Modifier
          .fillMaxWidth(0.75f)
          .height(48.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(OlliteRTPrimary.copy(alpha = 0.20f))
      )
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      val label = stringResource(tab.labelResId)
      Icon(
        imageVector = tab.icon,
        contentDescription = label,
        tint = animatedTextColor,
        modifier = Modifier.size(22.dp),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = animatedTextColor,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}
