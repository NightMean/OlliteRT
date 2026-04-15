package com.ollitert.llm.server.ui.common

import androidx.compose.ui.res.stringResource
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reusable donation dialog showing donation platform options (GitHub Sponsors, Buy Me a Coffee, Ko-fi).
 * Used from both Settings ("Donate" button) and the engagement prompt ("Support Development" action).
 */
@Composable
fun DonateDialog(
  onDismiss: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.dialog_donate_title)) },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = stringResource(R.string.dialog_donate_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        DonateOption(stringResource(R.string.donate_github_sponsors), GitHubConfig.DONATE_GITHUB_SPONSORS, uriHandler, onDismiss)
        DonateOption(stringResource(R.string.donate_buy_me_a_coffee), GitHubConfig.DONATE_BUY_ME_A_COFFEE, uriHandler, onDismiss)
        DonateOption(stringResource(R.string.donate_kofi), GitHubConfig.DONATE_KOFI, uriHandler, onDismiss)
      }
    },
    confirmButton = {},
    dismissButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ),
      ) {
        Text(stringResource(R.string.close))
      }
    },
  )
}

/**
 * Engagement prompt shown after the user has manually started the server N times.
 * Asks the user to support development, star the repo, or dismiss.
 *
 * @param onSupportDevelopment Called when the user taps "Support Development" — caller should open [DonateDialog].
 * @param onDismiss Called when the user taps "Not now" — caller records whether "Don't show again" was checked.
 * @param onStarOnGitHub Called when the user taps "Star on GitHub" — treated as a positive engagement (permanently suppresses).
 */
@Composable
fun EngagementPromptDialog(
  onSupportDevelopment: () -> Unit,
  onStarOnGitHub: () -> Unit,
  onDismiss: (permanentlyDismiss: Boolean) -> Unit,
) {
  var dontShowAgain by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = { onDismiss(dontShowAgain) },
    title = {
      Text(
        text = stringResource(R.string.dialog_engagement_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.dialog_engagement_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Support Development button
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(OlliteRTPrimary.copy(alpha = 0.15f))
            .clickable { onSupportDevelopment() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.Favorite,
            contentDescription = null,
            tint = OlliteRTPrimary,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = stringResource(R.string.label_support_development),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = OlliteRTPrimary,
            modifier = Modifier.weight(1f),
          )
        }

        // Star on GitHub button
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { onStarOnGitHub() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Icon(
            imageVector = Icons.Outlined.StarOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
          )
          Text(
            text = stringResource(R.string.label_star_on_github),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
          )
        }

        // "Don't show this again" checkbox
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { dontShowAgain = !dontShowAgain }
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = dontShowAgain,
            onCheckedChange = { dontShowAgain = it },
            colors = CheckboxDefaults.colors(checkedColor = OlliteRTPrimary),
          )
          Text(
            text = stringResource(R.string.label_dont_show_again),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = { onDismiss(dontShowAgain) }) {
        Text(
          text = stringResource(R.string.button_not_now),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
  )
}

/** A single tappable row in the donate dialog that opens a donation URL. */
@Composable
fun DonateOption(
  label: String,
  url: String,
  uriHandler: androidx.compose.ui.platform.UriHandler,
  onDismiss: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHighest)
      .clickable {
        onDismiss()
        uriHandler.openUri(url)
      }
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Icon(
      imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(18.dp),
    )
  }
}
