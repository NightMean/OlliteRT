package com.ollite.llm.server.ui.gettingstarted

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollite.llm.server.ui.theme.OlliteDeepBlue
import com.ollite.llm.server.ui.theme.OlliteOutline
import com.ollite.llm.server.ui.theme.OllitePrimary
import com.ollite.llm.server.ui.theme.OlliteSurfaceContainerLow
import com.ollite.llm.server.ui.theme.SpaceGroteskFontFamily

@Composable
fun GettingStartedScreen(
  onGetStartedClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scrollState = rememberScrollState()

  // Request notification permission on "Get Started" tap (Android 13+)
  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
  ) { _ ->
    // Proceed regardless of whether permission was granted
    onGetStartedClick()
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 24.dp, vertical = 48.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(modifier = Modifier.weight(1f))

    // Hero title with gradient highlight on "On-Device"
    HeroTitle()

    Spacer(modifier = Modifier.height(16.dp))

    // Subtitle
    Text(
      text = "Turn your device into an OpenAI-compatible server. " +
        "Download a model, start the server, and connect any client.",
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 8.dp),
    )

    Spacer(modifier = Modifier.height(40.dp))

    // Setup checklist card
    SetupChecklist()

    Spacer(modifier = Modifier.weight(1f))

    // Buttons
    Spacer(modifier = Modifier.height(32.dp))

    Button(
      onClick = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
          onGetStartedClick()
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(64.dp),
      shape = RoundedCornerShape(50),
      colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
    ) {
      // Gradient background drawn behind the button content
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            brush = Brush.linearGradient(listOf(OllitePrimary, OlliteDeepBlue)),
            shape = RoundedCornerShape(50),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "Get Started",
          style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
          ),
          color = Color.White,
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
      onClick = { /* Learn More — no-op for now */ },
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp),
      shape = RoundedCornerShape(50),
      border = ButtonDefaults.outlinedButtonBorder(true),
    ) {
      Text(
        text = "Learn More",
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun HeroTitle() {
  val gradientBrush = Brush.linearGradient(listOf(OllitePrimary, OlliteDeepBlue))

  Text(
    text = buildAnnotatedString {
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
        append("Your Local\n")
      }
      withStyle(SpanStyle(brush = gradientBrush, fontWeight = FontWeight.Bold)) {
        append("On-Device")
      }
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
        append("\nLLM Hub.")
      }
    },
    style = MaterialTheme.typography.displayMedium.copy(
      fontFamily = SpaceGroteskFontFamily,
      fontWeight = FontWeight.Bold,
      lineHeight = 48.sp,
      letterSpacing = (-1).sp,
    ),
    textAlign = TextAlign.Center,
  )
}

@Composable
private fun SetupChecklist() {
  val steps = listOf(
    "Download a model",
    "Start the server",
    "Connect your client",
  )

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(OlliteSurfaceContainerLow)
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = "Quick Setup",
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )

    steps.forEachIndexed { index, step ->
      SetupStep(number = index + 1, text = step)
    }
  }
}

@Composable
private fun SetupStep(number: Int, text: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Number circle
    Box(
      modifier = Modifier
        .size(32.dp)
        .border(1.dp, OlliteOutline, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = number.toString(),
        style = MaterialTheme.typography.labelLarge,
        color = OllitePrimary,
      )
    }

    Spacer(modifier = Modifier.width(16.dp))

    Text(
      text = text,
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
