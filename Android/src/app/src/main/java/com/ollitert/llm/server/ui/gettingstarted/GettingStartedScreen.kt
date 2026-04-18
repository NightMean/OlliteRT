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

import com.ollitert.llm.server.common.GitHubConfig
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.widthIn
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollitert.llm.server.R
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
        data = Uri.parse("package:${context.packageName}")
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

  // Centered max-width container for tablets — onboarding should feel focused, not stretched.
  // verticalScroll requires unbounded height, so weight() doesn't work for centering — the Box
  // with Alignment.Center handles vertical centering instead.
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
  Column(
    modifier = Modifier
      .widthIn(max = 600.dp)
      .fillMaxWidth()
      .navigationBarsPadding()
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp, vertical = if (isShortScreen) 16.dp else 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    if (isShortScreen) {
      Spacer(modifier = Modifier.height(16.dp))
    } else {
      Spacer(modifier = Modifier.height(32.dp))
    }

    // Hero title with gradient highlight on "On-Device"
    HeroTitle()

    Spacer(modifier = Modifier.height(20.dp))

    // Subtitle
    Text(
      text = stringResource(R.string.getting_started_subtitle),
      style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 17.sp,
        lineHeight = 26.sp,
      ),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 4.dp),
    )

    Spacer(modifier = Modifier.height(40.dp))

    // Setup steps card
    SetupSteps()

    Spacer(modifier = Modifier.height(40.dp))

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

    Spacer(modifier = Modifier.height(28.dp))

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
          Intent(Intent.ACTION_VIEW, Uri.parse(GitHubConfig.REPO_URL))
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

    Spacer(modifier = Modifier.height(24.dp))
  } // Column
  } // Box (max-width wrapper)
}

@Composable
private fun HeroTitle() {
  val gradientBrush = Brush.linearGradient(listOf(OlliteRTPrimary, OlliteRTDeepBlue))

  Text(
    text = buildAnnotatedString {
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
        append(stringResource(R.string.getting_started_hero_line1))
      }
      withStyle(SpanStyle(brush = gradientBrush, fontWeight = FontWeight.Bold)) {
        append(stringResource(R.string.getting_started_hero_highlight))
      }
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
        append(stringResource(R.string.getting_started_hero_line3))
      }
    },
    style = MaterialTheme.typography.displayLarge.copy(
      fontFamily = SpaceGroteskFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 48.sp,
      lineHeight = 56.sp,
      letterSpacing = (-1).sp,
    ),
    textAlign = TextAlign.Center,
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
    verticalArrangement = Arrangement.spacedBy(24.dp),
  ) {
    steps.forEachIndexed { index, (title, description) ->
      SetupStep(number = index + 1, title = title, description = description)
    }
  }
}

@Composable
private fun SetupStep(number: Int, title: String, description: String) {
  Row(
    verticalAlignment = Alignment.Top,
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
