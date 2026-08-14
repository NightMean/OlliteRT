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

package com.ollitert.llm.server.service.inference

import com.ollitert.llm.server.service.http.FinishReason
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Encapsulates the streaming loop state machine and token event processing.
 */
internal sealed class StreamEvent {
  data class Token(val text: String) : StreamEvent()
  data class Error(val message: String) : StreamEvent()
  data object Done : StreamEvent()
}

internal data class StreamState(
  val accumulatedText: StringBuilder = StringBuilder(),
  val accumulatedThinking: StringBuilder = StringBuilder(),
  var tokenCount: Int = 0,
  var firstTokenMs: Long = -1L,
  var finishReason: FinishReason = FinishReason.STOP,
  var isThinking: Boolean = false,
)

internal object InferenceStreamingLoop {

  /**
   * Processes a token string against thinking tag delimiters (`<thought>`, `</thought>`, `think`, etc.).
   */
  fun processThinkingTags(
    token: String,
    state: StreamState,
    onVisibleToken: (String) -> Unit,
    onThinkingToken: (String) -> Unit,
  ) {
    if (token.contains("<thought>") || token.contains("<think>")) {
      state.isThinking = true
    }

    if (state.isThinking) {
      state.accumulatedThinking.append(token)
      onThinkingToken(token)
      if (token.contains("</thought>") || token.contains("</think>")) {
        state.isThinking = false
      }
    } else {
      state.accumulatedText.append(token)
      onVisibleToken(token)
    }
  }
}
