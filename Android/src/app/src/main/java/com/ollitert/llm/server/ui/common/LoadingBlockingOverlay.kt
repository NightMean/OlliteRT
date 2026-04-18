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

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * Invisible overlay that intercepts all touch events and shows a toast message.
 * Used to block interaction on a parent Box while a background operation is running
 * (e.g. model loading, server active).
 *
 * Must be called inside a [BoxScope] so it can use [Modifier.matchParentSize].
 */
@Composable
fun BoxScope.LoadingBlockingOverlay(message: String) {
  val context = LocalContext.current
  Box(
    modifier = Modifier
      .matchParentSize()
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
      ) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
      },
  )
}
