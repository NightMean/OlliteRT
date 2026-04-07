package com.ollitert.llm.server.ui.server

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.data.LlmHttpPrefs
import com.ollitert.llm.server.ui.navigation.ServerStatus
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

@Composable
fun SettingsScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  serverStatus: ServerStatus = ServerStatus.STOPPED,
  onRestartServer: () -> Unit = {},
) {
  val context = LocalContext.current

  // Port state — use String for text field, track original for change detection
  val savedPort = remember { LlmHttpPrefs.getPort(context) }
  var portText by remember { mutableStateOf(savedPort.toString()) }

  // Port error state
  var portError by remember { mutableStateOf(false) }

  // Restart dialog state
  var showRestartDialog by remember { mutableStateOf(false) }

  // Bearer token state
  var bearerEnabled by remember { mutableStateOf(LlmHttpPrefs.getBearerToken(context).isNotBlank()) }
  var bearerToken by remember { mutableStateOf(LlmHttpPrefs.getBearerToken(context)) }

  // HuggingFace token state
  var hfToken by remember { mutableStateOf(LlmHttpPrefs.getHfToken(context)) }
  var hfTokenVisible by remember { mutableStateOf(false) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
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
        text = "Host Port (1024–65535)",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      OutlinedTextField(
        value = portText,
        onValueChange = { input ->
          // Allow only digits, let user freely type/delete
          portText = input.filter { it.isDigit() }.take(5)
          portError = false
        },
        singleLine = true,
        isError = portError,
        placeholder = {
          Text(
            "8000",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = if (portError) MaterialTheme.colorScheme.error else OlliteRTPrimary,
          unfocusedBorderColor = if (portError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Default: 8000. Requires server restart to take effect.",
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
          colors = SwitchDefaults.colors(checkedTrackColor = OlliteRTPrimary),
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
            focusedBorderColor = OlliteRTPrimary,
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
                  tint = OlliteRTPrimary,
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
                  tint = OlliteRTPrimary,
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
        text = "Required for downloading gated models and private repositories.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = buildAnnotatedString {
          append("Generate a token with ")
          withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Read") }
          append(" permissions from ")
          withLink(LinkAnnotation.Url(
            url = "https://huggingface.co/settings/tokens",
            styles = TextLinkStyles(style = SpanStyle(color = OlliteRTPrimary, textDecoration = TextDecoration.Underline)),
          )) {
            append("HuggingFace token settings")
          }
          append(".")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.height(8.dp))
      OutlinedTextField(
        value = hfToken,
        onValueChange = { hfToken = it },
        singleLine = true,
        placeholder = {
          Text(
            "hf_...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
        },
        visualTransformation = if (hfTokenVisible) {
          VisualTransformation.None
        } else {
          PasswordVisualTransformation()
        },
        trailingIcon = {
          Row {
            IconButton(onClick = { hfTokenVisible = !hfTokenVisible }) {
              Icon(
                imageVector = if (hfTokenVisible) Icons.Outlined.VisibilityOff
                else Icons.Outlined.Visibility,
                contentDescription = if (hfTokenVisible) "Hide token" else "Show token",
                tint = OlliteRTPrimary,
                modifier = Modifier.size(20.dp),
              )
            }
            if (hfToken.isNotEmpty()) {
              IconButton(onClick = { hfToken = "" }) {
                Icon(
                  imageVector = Icons.Outlined.Close,
                  contentDescription = "Clear token",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        },
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = OlliteRTPrimary,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
      )
    }

    // Save button
    Button(
      onClick = {
        if (serverStatus == ServerStatus.LOADING) {
          Toast.makeText(context, "Please wait for the server to finish starting before changing settings", Toast.LENGTH_SHORT).show()
          return@Button
        }
        if (portText.isBlank()) {
          portError = true
          Toast.makeText(context, "A port number is required", Toast.LENGTH_SHORT).show()
          return@Button
        }
        val port = portText.toIntOrNull()
        if (port == null || port !in 1024..65535) {
          portError = true
          Toast.makeText(context, "Port must be between 1024 and 65535", Toast.LENGTH_SHORT).show()
          return@Button
        }

        val portChanged = port != savedPort
        val isServerRunning = serverStatus == ServerStatus.RUNNING || serverStatus == ServerStatus.LOADING

        LlmHttpPrefs.save(context, LlmHttpPrefs.isEnabled(context), port)
        if (bearerEnabled) {
          LlmHttpPrefs.setBearerToken(context, bearerToken)
        }
        LlmHttpPrefs.setHfToken(context, hfToken)

        if (portChanged && isServerRunning) {
          showRestartDialog = true
        } else {
          Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(50),
      colors = ButtonDefaults.buttonColors(containerColor = OlliteRTPrimary),
    ) {
      Text(
        text = "SAVE",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
    }

    // Restart server dialog when port changed
    if (showRestartDialog) {
      AlertDialog(
        onDismissRequest = {
          showRestartDialog = false
          Toast.makeText(context, "Settings saved. Restart server manually to apply port change.", Toast.LENGTH_LONG).show()
        },
        title = { Text("Restart server?") },
        text = {
          Text("The port has been changed. The server needs to restart for the new port to take effect.")
        },
        confirmButton = {
          Button(onClick = {
            showRestartDialog = false
            onRestartServer()
            Toast.makeText(context, "Server restarting with new port", Toast.LENGTH_SHORT).show()
          }) {
            Text("Restart")
          }
        },
        dismissButton = {
          Button(
            onClick = {
              showRestartDialog = false
              Toast.makeText(context, "Settings saved. Restart server manually to apply port change.", Toast.LENGTH_LONG).show()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = MaterialTheme.colorScheme.onSurface,
            ),
          ) {
            Text("Later")
          }
        },
      )
    }

    // Footer
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "OlliteRT v1.0.11",
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
        tint = OlliteRTPrimary,
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
