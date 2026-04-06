package com.ollite.llm.server.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.ollite.llm.server.ui.theme.OlliteGreen400
import com.ollite.llm.server.ui.theme.OllitePrimary
import com.ollite.llm.server.ui.theme.SpaceGroteskFontFamily

/** Server running state for the status pill in the top bar. */
enum class ServerStatus { STOPPED, LOADING, RUNNING, ERROR }

@Composable
fun OlliteTopBar(
  serverStatus: ServerStatus,
  onSettingsClick: () -> Unit,
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
    // Left: Ollite brand
    Text(
      text = "Ollite",
      color = OllitePrimary,
      fontFamily = SpaceGroteskFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 22.sp,
    )

    // Center: Status pill
    StatusPill(serverStatus = serverStatus)

    // Right: Settings gear
    IconButton(onClick = onSettingsClick) {
      Icon(
        imageVector = Icons.Outlined.Settings,
        contentDescription = "Settings",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp),
      )
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
    ServerStatus.LOADING -> MaterialTheme.colorScheme.onSurfaceVariant to "LOADING"
    ServerStatus.RUNNING -> OlliteGreen400 to "RUNNING"
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
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(RoundedCornerShape(50))
        .background(animatedDotColor)
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface,
      fontWeight = FontWeight.SemiBold,
    )
  }
}
