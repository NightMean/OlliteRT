package com.ollitert.llm.server.ui.gettingstarted

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Spacer(modifier = Modifier.weight(0.8f))

    // Hero title with gradient highlight on "On-Device"
    HeroTitle()

    Spacer(modifier = Modifier.height(20.dp))

    // Subtitle
    Text(
      text = "Turn your device into a local OpenAI-compatible server capable of hosting powerful, optimized LLMs",
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

    Spacer(modifier = Modifier.weight(1f))

    // Notification permission notice
    Text(
      text = "Notification permission is required to keep the server running in the background.",
      style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 4.dp),
    )

    Spacer(modifier = Modifier.height(16.dp))

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
          text = "Get Started",
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
          Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NightMean/ollitert"))
        )
      },
    ) {
      Text(
        text = "Learn More",
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
  }
}

@Composable
private fun HeroTitle() {
  val gradientBrush = Brush.linearGradient(listOf(OlliteRTPrimary, OlliteRTDeepBlue))

  Text(
    text = buildAnnotatedString {
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
        append("Your Private\n")
      }
      withStyle(SpanStyle(brush = gradientBrush, fontWeight = FontWeight.Bold)) {
        append("On-Device")
      }
      withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
        append("\nLLM Server")
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
    Pair("Download a model", "Select from our optimized catalog or import local files"),
    Pair("Start the server", "Initialize the inference engine with local hardware"),
    Pair("Connect your client", "Point any OpenAI-compatible app to your local IP"),
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
