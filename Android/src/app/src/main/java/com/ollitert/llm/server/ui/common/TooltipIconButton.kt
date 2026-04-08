package com.ollitert.llm.server.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 40dp icon action button with a long-press tooltip shown below the button.
 * Drop-in replacement for the bare Box+Icon pattern used across the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
  icon: ImageVector,
  tooltip: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
  enabled: Boolean = true,
) {
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
    tooltip = {
      PlainTooltip {
        Text(tooltip)
      }
    },
    state = rememberTooltipState(),
  ) {
    Box(
      modifier = modifier
        .size(40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(backgroundColor)
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = tooltip,
        tint = tint,
        modifier = Modifier.size(22.dp),
      )
    }
  }
}
