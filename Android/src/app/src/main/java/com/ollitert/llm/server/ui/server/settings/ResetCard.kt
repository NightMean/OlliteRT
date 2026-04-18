package com.ollitert.llm.server.ui.server.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.ui.server.SettingsViewModel

@Composable
internal fun ResetCard(vm: SettingsViewModel) {
  Column {
    Spacer(modifier = Modifier.height(16.dp))
    Button(
      onClick = { vm.showResetDialog = true },
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(50),
      colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
      ),
    ) {
      Icon(
        imageVector = Icons.Outlined.RestartAlt,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = stringResource(R.string.settings_reset_to_defaults),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
