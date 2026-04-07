package com.ollitert.llm.server.ui.navigation

import android.os.Environment
import android.os.StatFs
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.ui.common.humanReadableSize
import com.ollitert.llm.server.ui.theme.OlliteRTDeepBlue
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTSurfaceContainerLowest
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

enum class OlliteRTTab(val label: String, val icon: ImageVector, val route: String) {
  Models("Models", Icons.Outlined.ViewInAr, OlliteRTRoutes.MODELS),
  Status("Status", Icons.Outlined.Analytics, OlliteRTRoutes.STATUS),
  Logs("Logs", Icons.Outlined.Terminal, OlliteRTRoutes.LOGS),
}

@Composable
fun OlliteRTBottomNavBar(
  currentRoute: String?,
  onTabSelected: (OlliteRTTab) -> Unit,
  modifier: Modifier = Modifier,
  storageUpdateTrigger: Long = 0L,
) {
  val showStorageBar = currentRoute == OlliteRTRoutes.MODELS

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
      .background(OlliteRTSurfaceContainerLowest),
  ) {
    // Storage bar - only on Models page
    if (showStorageBar) {
      StorageBar(storageUpdateTrigger = storageUpdateTrigger)
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
    Text(
      text = "${storageInfo.freeBytes.humanReadableSize()} FREE",
      style = MaterialTheme.typography.labelMedium,
      fontFamily = SpaceGroteskFontFamily,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.weight(1f))
    // Custom drawn progress bar to avoid LinearProgressIndicator artifacts
    Box(
      modifier = Modifier
        .width(140.dp)
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

private data class StorageInfo(
  val totalBytes: Long,
  val freeBytes: Long,
  val usedFraction: Float,
)

private fun getStorageInfo(): StorageInfo {
  return try {
    val stat = StatFs(Environment.getDataDirectory().path)
    val total = stat.totalBytes
    val free = stat.availableBytes
    val used = (total - free).toFloat() / total.toFloat()
    StorageInfo(totalBytes = total, freeBytes = free, usedFraction = used.coerceIn(0f, 1f))
  } catch (e: Exception) {
    StorageInfo(totalBytes = 0L, freeBytes = 0L, usedFraction = 0f)
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

  // Full touch area
  Box(
    modifier = modifier
      .height(64.dp)
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
          .width(80.dp)
          .height(48.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(OlliteRTDeepBlue.copy(alpha = 0.30f))
      )
    }

    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = tab.icon,
        contentDescription = tab.label,
        tint = animatedTextColor,
        modifier = Modifier.size(22.dp),
      )
      Text(
        text = tab.label,
        style = MaterialTheme.typography.labelSmall,
        color = animatedTextColor,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
  }
}
