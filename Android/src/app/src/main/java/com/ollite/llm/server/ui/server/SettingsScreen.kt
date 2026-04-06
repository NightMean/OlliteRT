package com.ollite.llm.server.ui.server

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ollite.llm.server.data.LlmHttpPrefs
import com.ollite.llm.server.ui.theme.OlliteDeepBlue
import com.ollite.llm.server.ui.theme.OllitePrimary

@Composable
fun SettingsScreen(
  onBackClick: () -> Unit,
  onBenchmarkClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  // Port state
  var port by remember { mutableIntStateOf(LlmHttpPrefs.getPort(context)) }

  // Bearer token state
  var bearerEnabled by remember { mutableStateOf(LlmHttpPrefs.getBearerToken(context).isNotBlank()) }
  var bearerToken by remember { mutableStateOf(LlmHttpPrefs.getBearerToken(context)) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // Back button
    IconButton(onClick = onBackClick) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = "Back",
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }

    // Heading
    Text(
      text = "Global Settings",
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
      text = "Configure server behavior, authentication, and preferences.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(4.dp))

    // Server Config card
    SettingsCard(
      icon = Icons.Outlined.Tune,
      title = "Server Configuration",
    ) {
      Text(
        text = "Host Port",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = port.toString(),
        onValueChange = { input ->
          input.toIntOrNull()?.let { if (it in 1..65535) port = it }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = OllitePrimary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Default: 11434. Requires server restart to take effect.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // API Authentication card
    SettingsCard(
      icon = Icons.Outlined.Shield,
      title = "API Authentication",
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "Require Bearer Token",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
          checked = bearerEnabled,
          onCheckedChange = { enabled ->
            bearerEnabled = enabled
            if (enabled && bearerToken.isBlank()) {
              bearerToken = LlmHttpPrefs.ensureBearerToken(context)
            } else if (!enabled) {
              LlmHttpPrefs.setBearerToken(context, "")
              bearerToken = ""
            }
          },
          colors = SwitchDefaults.colors(checkedTrackColor = OllitePrimary),
        )
      }
      if (bearerEnabled && bearerToken.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = bearerToken,
          onValueChange = {},
          readOnly = true,
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OllitePrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          ),
          trailingIcon = {
            Row {
              IconButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Bearer Token", bearerToken))
                Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
              }) {
                Icon(
                  imageVector = Icons.Outlined.ContentCopy,
                  contentDescription = "Copy token",
                  tint = OllitePrimary,
                  modifier = Modifier.size(20.dp),
                )
              }
              IconButton(onClick = {
                bearerToken = LlmHttpPrefs.ensureBearerToken(context).let {
                  // Force regenerate by clearing first
                  LlmHttpPrefs.setBearerToken(context, "")
                  LlmHttpPrefs.ensureBearerToken(context)
                }
              }) {
                Icon(
                  imageVector = Icons.Outlined.Refresh,
                  contentDescription = "Regenerate token",
                  tint = OllitePrimary,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Clients must send Authorization: Bearer <token> header.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    // Hugging Face Token card
    SettingsCard(
      icon = Icons.Outlined.Key,
      title = "Hugging Face Token",
    ) {
      Text(
        text = "Authentication for gated models is handled via OAuth when you first download a model that requires it. No manual token entry needed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // System Benchmark card
    SettingsCard(
      icon = Icons.Outlined.Speed,
      title = "System Benchmark",
    ) {
      Text(
        text = "Run comprehensive latency and TFLOPS analysis to evaluate your device's inference performance.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(12.dp))
      Button(
        onClick = onBenchmarkClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = OllitePrimary),
        shape = RoundedCornerShape(50),
      ) {
        Text(
          text = "RUN BENCHMARK",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    // Save button
    Button(
      onClick = {
        LlmHttpPrefs.save(context, LlmHttpPrefs.isEnabled(context), port)
        if (bearerEnabled) {
          LlmHttpPrefs.setBearerToken(context, bearerToken)
        }
        Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(50),
      colors = ButtonDefaults.buttonColors(containerColor = OllitePrimary),
    ) {
      Text(
        text = "SAVE",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
    }

    // Footer
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Ollite v1.0.11",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    Spacer(modifier = Modifier.height(24.dp))
  }
}

@Composable
private fun SettingsCard(
  icon: ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(20.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = OllitePrimary,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    Spacer(modifier = Modifier.height(12.dp))
    content()
  }
}
