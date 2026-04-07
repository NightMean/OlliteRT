package com.ollitert.llm.server.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.ui.theme.OlliteRTGreen400
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

/** Server running state for the status pill in the top bar. */
enum class ServerStatus { STOPPED, LOADING, RUNNING, ERROR }

@Composable
fun OlliteRTTopBar(
  serverStatus: ServerStatus,
  onSettingsClick: () -> Unit,
  onBackClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    // Left: Back arrow OR OlliteRT brand (not both)
    if (onBackClick != null) {
      IconButton(onClick = onBackClick) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
          contentDescription = "Back",
          tint = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(24.dp),
        )
      }
    } else {
      Text(
        text = "OlliteRT",
        color = OlliteRTPrimary,
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
      )
    }

    // Center: Status pill
    StatusPill(serverStatus = serverStatus)

    // Right: Settings gear (hidden when already on Settings)
    if (onBackClick == null) {
      IconButton(onClick = onSettingsClick) {
        Icon(
          imageVector = Icons.Outlined.Settings,
          contentDescription = "Settings",
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(24.dp),
        )
      }
    } else {
      // Placeholder for layout balance
      Spacer(modifier = Modifier.size(48.dp))
    }
  }
}

@Composable
fun StatusPill(
  serverStatus: ServerStatus,
  modifier: Modifier = Modifier,
) {
  val (dotColor, label) = when (serverStatus) {
    ServerStatus.STOPPED -> MaterialTheme.colorScheme.error to "STOPPED"
    ServerStatus.LOADING -> MaterialTheme.colorScheme.onSurfaceVariant to "STARTING"
    ServerStatus.RUNNING -> OlliteRTGreen400 to "RUNNING"
    ServerStatus.ERROR -> MaterialTheme.colorScheme.error to "ERROR"
  }

  val animatedDotColor by animateColorAsState(
    targetValue = dotColor,
    animationSpec = tween(300),
    label = "dotColor",
  )

  Row(
    modifier = modifier
      .clip(RoundedCornerShape(50))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (serverStatus == ServerStatus.LOADING) {
      CircularProgressIndicator(
        modifier = Modifier.size(12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        strokeWidth = 2.dp,
      )
    } else {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(RoundedCornerShape(50))
          .background(animatedDotColor)
      )
    }
    Spacer(modifier = Modifier.width(6.dp))
    AnimatedContent(
      targetState = label,
      transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
      label = "statusLabel",
    ) { targetLabel ->
      Text(
        text = targetLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}
