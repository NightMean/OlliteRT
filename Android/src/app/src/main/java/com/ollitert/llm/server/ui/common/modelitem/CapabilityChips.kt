package com.ollitert.llm.server.ui.common.modelitem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.data.Model

/**
 * Displays capability chips (Text, Vision, Audio, Thinking) for an LLM model.
 * All LLM models show "Text"; other chips appear based on model flags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapabilityChips(
  model: Model,
  modifier: Modifier = Modifier,
) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // All LLM models support text
    CapabilityChip(label = "Text")
    if (model.llmSupportImage) {
      CapabilityChip(label = "Vision")
    }
    if (model.llmSupportAudio) {
      CapabilityChip(label = "Audio")
    }
    if (model.llmSupportThinking) {
      CapabilityChip(label = "Thinking")
    }
  }
}

@Composable
private fun CapabilityChip(
  label: String,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(horizontal = 8.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
