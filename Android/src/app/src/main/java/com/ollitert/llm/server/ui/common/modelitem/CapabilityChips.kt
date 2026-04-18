package com.ollitert.llm.server.ui.common.modelitem

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ollitert.llm.server.R
import com.ollitert.llm.server.data.Model
import com.ollitert.llm.server.ui.common.highlightSearchMatches
import com.ollitert.llm.server.ui.theme.OlliteRTPrimary

/**
 * Displays capability chips (Text, Vision, Audio, Thinking) for an LLM model.
 * All LLM models show "Text"; other chips appear based on model flags.
 * Horizontally scrollable so chips that don't fit scroll off-screen.
 */
@Composable
fun CapabilityChips(
  model: Model,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
) {
  Row(
    modifier = modifier.horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    CapabilityChip(label = stringResource(R.string.capability_text), searchQuery = searchQuery)
    if (model.llmSupportImage) {
      CapabilityChip(label = stringResource(R.string.capability_vision), searchQuery = searchQuery)
    }
    if (model.llmSupportAudio) {
      CapabilityChip(label = stringResource(R.string.capability_audio), searchQuery = searchQuery)
    }
    if (model.llmSupportThinking) {
      CapabilityChip(label = stringResource(R.string.capability_thinking), searchQuery = searchQuery)
    }
  }
}

@Composable
private fun CapabilityChip(
  label: String,
  modifier: Modifier = Modifier,
  searchQuery: String = "",
) {
  Row(
    modifier = modifier
      .clip(RoundedCornerShape(6.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .padding(horizontal = 8.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = highlightSearchMatches(label, searchQuery, OlliteRTPrimary),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}
