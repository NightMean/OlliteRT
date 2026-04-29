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

package com.ollitert.llm.server.ui.gettingstarted

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.GitHubConfig
import com.ollitert.llm.server.ui.theme.OlliteRTDeepBlue
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary
import com.ollitert.llm.server.ui.theme.OlliteRTSurfaceContainerHigh
import com.ollitert.llm.server.ui.theme.OlliteRTSurfaceContainerLow
import com.ollitert.llm.server.ui.theme.SpaceGroteskFontFamily

@Composable
fun GettingStartedScreen(
  onGetStartedClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()
  val context = LocalContext.current
  val configuration = LocalConfiguration.current
  // Landscape phones have very limited vertical space — reduce padding and spacers
  val isShortScreen = configuration.screenHeightDp < 500

  // After notification permission is handled, request battery optimization exemption
  // so the OS doesn't throttle/kill the foreground service while serving inference.
  val batteryOptLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
  ) { _ ->
    // Proceed regardless of whether the user granted the exemption
    onGetStartedClick()
  }

  /** Request battery optimization exemption, or proceed directly if already exempt. */
  fun requestBatteryOptimizationExemption() {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (pm == null || !pm.isIgnoringBatteryOptimizations(context.packageName)) {
      // Show the system dialog asking the user to exempt this app from Doze mode.
      // Uses ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS which is allowed by Google Play
      // for apps that need sustained background work (HTTP servers, media players, etc.).
      val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = "package:${context.packageName}".toUri()
      }
      batteryOptLauncher.launch(intent)
    } else {
      // Already exempt — skip straight to the main app
      onGetStartedClick()
    }
  }

  // Request notification permission on "Get Started" tap (Android 13+),
  // then chain into battery optimization request.
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { _ ->
    // Proceed to battery optimization request regardless of notification result
    requestBatteryOptimizationExemption()
  }

  // Outer Column splits the screen: scrollable content (weight) + pinned bottom actions.
  // The button is always visible regardless of screen size or font scaling.
  Column(
    modifier = modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Scrollable content area — takes all remaining space after the bottom section
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(scrollState),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxWidth()
          .padding(horizontal = 24.dp)
          .padding(top = if (isShortScreen) 12.dp else 32.dp, bottom = if (isShortScreen) 12.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Spacer(modifier = Modifier.height(if (isShortScreen) 8.dp else 16.dp))

        // Hero title with gradient highlight on "On-Device"
        HeroTitle()

        Spacer(modifier = Modifier.height(16.dp))

        // Subtitle
        Text(
          text = stringResource(R.string.getting_started_subtitle),
          style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp,
            lineHeight = 22.sp,
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Setup steps card
        SetupSteps()
      }
      Spacer(modifier = Modifier.weight(1f))
    }

    // Pinned bottom section — always visible
    Column(
      modifier = Modifier
        .widthIn(max = 600.dp)
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp)
        .padding(top = 12.dp, bottom = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Permission notice — notification + battery optimization
      Text(
        text = stringResource(R.string.getting_started_permission_notice),
        style = MaterialTheme.typography.bodyLarge.copy(
          fontSize = 14.sp,
          lineHeight = 20.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 4.dp),
      )

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: notification permission first, then battery opt in the callback
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            // Pre-Android 13: notification permission not needed, go straight to battery opt
            requestBatteryOptimizationExemption()
          }
        },
        modifier = Modifier
          .widthIn(max = 400.dp)
          .fillMaxWidth()
          .height(if (isShortScreen) 52.dp else 64.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              brush = Brush.linearGradient(listOf(OlliteRTPrimary, OlliteRTDeepBlue)),
              shape = RoundedCornerShape(50),
            ),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = stringResource(R.string.getting_started_button),
            style = MaterialTheme.typography.labelLarge.copy(
              fontFamily = SpaceGroteskFontFamily,
              fontSize = 18.sp,
              fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // "Learn More" hyperlink — bold with link icon
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
          context.startActivity(
            Intent(Intent.ACTION_VIEW, GitHubConfig.REPO_URL.toUri())
          )
        },
      ) {
        Text(
          text = stringResource(R.string.getting_started_learn_more),
          style = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
          ),
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.size(16.dp),
        )
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  } // Outer Column
}

@Composable
private fun HeroTitle() {
  val gradientBrush = Brush.linearGradient(listOf(OlliteRTPrimary, OlliteRTDeepBlue))
  val screenHeightDp = LocalConfiguration.current.screenHeightDp
  val heroFontSize = (screenHeightDp * 0.055f).coerceIn(36f, 48f).sp
  val heroLineHeight = (screenHeightDp * 0.065f).coerceIn(44f, 56f).sp

  val line1 = stringResource(R.string.getting_started_hero_line1)
  val highlight = stringResource(R.string.getting_started_hero_highlight)
  val line3 = stringResource(R.string.getting_started_hero_line3)
  Text(
    text = buildAnnotatedString {
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
        append(line1)
      }
      withStyle(SpanStyle(brush = gradientBrush, fontWeight = FontWeight.Bold)) {
        append(highlight)
      }
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
        append(line3)
      }
    },
    style = MaterialTheme.typography.displayLarge.copy(
      fontFamily = SpaceGroteskFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = heroFontSize,
      lineHeight = heroLineHeight,
      letterSpacing = (-1).sp,
    ),
    textAlign = TextAlign.Center,
    modifier = Modifier.clearAndSetSemantics {
      contentDescription = "$line1$highlight$line3".replace("\n", " ").trim()
    },
  )
}

@Composable
private fun SetupSteps() {
  val steps = listOf(
    Pair(stringResource(R.string.getting_started_step1_title), stringResource(R.string.getting_started_step1_desc)),
    Pair(stringResource(R.string.getting_started_step2_title), stringResource(R.string.getting_started_step2_desc)),
    Pair(stringResource(R.string.getting_started_step3_title), stringResource(R.string.getting_started_step3_desc)),
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(
        brush = Brush.horizontalGradient(
          listOf(
            OlliteRTSurfaceContainerLow,
            OlliteRTSurfaceContainerHigh,
          )
        ),
        shape = RoundedCornerShape(24.dp),
      )
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    steps.forEachIndexed { index, (title, description) ->
      SetupStep(number = index + 1, title = title, description = description)
    }
  }
}

@Composable
private fun SetupStep(number: Int, title: String, description: String) {
  val stepCd = stringResource(R.string.getting_started_step_cd, number, title, description)
  Row(
    verticalAlignment = Alignment.Top,
    modifier = Modifier.clearAndSetSemantics {
      contentDescription = stepCd
    },
  ) {
    // Number in a larger square box with lighter background
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(OlliteRTSurfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = number.toString(),
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
        ),
        color = OlliteRTPrimary,
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.SemiBold,
          fontSize = 18.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
      )
    }
  }
}
