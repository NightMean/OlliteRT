package com.ollitert.llm.server.ui.server.settings

import android.content.Context
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.BuildConfig
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.service.ServerMetrics
import com.ollitert.llm.server.ui.server.SettingsViewModel
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.worker.UpdateCheckWorker
import java.net.URLEncoder

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SettingsFooter(vm: SettingsViewModel, context: Context) {
  val uriHandler = LocalUriHandler.current

  Spacer(modifier = Modifier.height(12.dp))
  FlowRow(
    modifier = Modifier.align(Alignment.CenterHorizontally),
    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    // What's New
    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .clickable {
          val intent = UpdateCheckWorker.buildUpdateIntent(context, UpdateCheckWorker.GITHUB_RELEASES_URL)
          intent.data?.let { uri -> uriHandler.openUri(uri.toString()) }
        }
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.NewReleases,
        contentDescription = null,
        tint = OlliteRTPrimary,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = stringResource(R.string.settings_whats_new),
        style = MaterialTheme.typography.bodyMedium,
        color = OlliteRTPrimary,
      )
    }

    // Report Issue
    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .clickable {
          val activeModel = ServerMetrics.activeModelName.value ?: "None"
          val deviceInfo = listOf(
            "- App version: OlliteRT v${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_HASH}) [${BuildConfig.CHANNEL}]",
            "- Device: ${Build.MANUFACTURER} ${Build.MODEL}",
            "- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "- LLM Model: $activeModel",
          ).joinToString("\n")
          val encoded = URLEncoder.encode(deviceInfo, "UTF-8")
          val flavorDropdown = when (BuildConfig.CHANNEL) {
            "stable" -> "stable (OlliteRT)"
            "beta" -> "beta (OlliteRT Beta)"
            "dev" -> "dev (OlliteRT Dev)"
            else -> ""
          }
          val flavorParam = if (flavorDropdown.isNotEmpty()) "&flavor=$flavorDropdown" else ""
          val url = "${GitHubConfig.NEW_BUG_REPORT_URL}&device-info=$encoded$flavorParam"
          uriHandler.openUri(url)
        }
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.BugReport,
        contentDescription = null,
        tint = OlliteRTPrimary,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = stringResource(R.string.settings_report_issue),
        style = MaterialTheme.typography.bodyMedium,
        color = OlliteRTPrimary,
      )
    }

    // Donate
    val heartPulse = rememberInfiniteTransition(label = "heartPulse")
    val heartScale by heartPulse.animateFloat(
      initialValue = 1f,
      targetValue = 1.15f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 600),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "heartScale",
    )
    Row(
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .clickable { vm.showDonateDialog = true }
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Outlined.Favorite,
        contentDescription = null,
        tint = OlliteRTPrimary,
        modifier = Modifier
          .size(18.dp)
          .graphicsLayer {
            scaleX = heartScale
            scaleY = heartScale
          },
      )
      Text(
        text = stringResource(R.string.settings_donate),
        style = MaterialTheme.typography.bodyMedium,
        color = OlliteRTPrimary,
      )
    }
  }

  Spacer(modifier = Modifier.height(4.dp))
  Text(
    text = stringResource(R.string.settings_version_footer, BuildConfig.VERSION_NAME, BuildConfig.GIT_HASH),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.align(Alignment.CenterHorizontally),
  )
}
