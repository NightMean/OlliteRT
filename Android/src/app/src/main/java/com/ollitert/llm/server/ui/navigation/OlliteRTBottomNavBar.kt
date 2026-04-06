package com.ollitert.llm.server.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.ui.theme.OlliteRTDeepBlue
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTSurfaceContainer

enum class OlliteRTTab(val label: String, val icon: ImageVector, val route: String) {
  Models("Models", Icons.Outlined.CloudDownload, OlliteRTRoutes.MODELS),
  Status("Status", Icons.Outlined.BarChart, OlliteRTRoutes.STATUS),
  Logs("Logs", Icons.Outlined.Terminal, OlliteRTRoutes.LOGS),
}

@Composable
fun OlliteRTBottomNavBar(
  currentRoute: String?,
  onTabSelected: (OlliteRTTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
    color = OlliteRTSurfaceContainer,
    tonalElevation = 3.dp,
  ) {
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

  Surface(
    onClick = onClick,
    modifier = modifier
      .height(64.dp)
      .padding(horizontal = 4.dp),
    color = Color.Transparent,
    shape = RoundedCornerShape(16.dp),
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (selected) {
        Box(
          modifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(
              Brush.linearGradient(
                listOf(
                  OlliteRTPrimary.copy(alpha = 0.15f),
                  OlliteRTDeepBlue.copy(alpha = 0.08f),
                )
              )
            )
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
}
