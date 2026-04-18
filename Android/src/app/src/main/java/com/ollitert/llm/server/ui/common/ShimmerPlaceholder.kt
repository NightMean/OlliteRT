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

package com.ollitert.llm.server.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/** Animated shimmer placeholder card matching model card shape. */
@Composable
fun ShimmerModelCard(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "shimmer")
  val translateAnim by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1000f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1200, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "shimmerTranslate",
  )

  val shimmerColors = listOf(
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f),
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.3f),
  )
  val brush = Brush.linearGradient(
    colors = shimmerColors,
    start = Offset(translateAnim - 200f, translateAnim - 200f),
    end = Offset(translateAnim, translateAnim),
  )

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .padding(16.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Title line — weight() gives proportional sizing within a Row
      Row {
        Box(
          modifier = Modifier
            .height(20.dp)
            .weight(0.55f)
            .clip(RoundedCornerShape(4.dp))
            .background(brush),
        )
        Spacer(modifier = Modifier.weight(0.25f))
        Box(
          modifier = Modifier
            .height(20.dp)
            .weight(0.2f)
            .clip(RoundedCornerShape(4.dp))
            .background(brush),
        )
      }
      // Description lines
      Box(
        modifier = Modifier
          .height(14.dp)
          .fillMaxWidth(0.9f)
          .clip(RoundedCornerShape(4.dp))
          .background(brush),
      )
      Box(
        modifier = Modifier
          .height(14.dp)
          .fillMaxWidth(0.7f)
          .clip(RoundedCornerShape(4.dp))
          .background(brush),
      )
      // Button placeholder
      Box(
        modifier = Modifier
          .height(42.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(21.dp))
          .background(brush),
      )
    }
  }
}
