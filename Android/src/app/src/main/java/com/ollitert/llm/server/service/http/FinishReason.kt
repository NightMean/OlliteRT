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

package com.ollitert.llm.server.service.http

import com.ollitert.llm.server.service.*
import com.ollitert.llm.server.service.http.*
import com.ollitert.llm.server.service.inference.*
import com.ollitert.llm.server.service.inference.*

/**
 * Infers finish_reason from output token count vs max_tokens limit.
 * LiteRT SDK doesn't expose why generation stopped, so we use a heuristic:
 * if estimated output tokens are within [TOLERANCE] of max_tokens, the model
 * likely hit the token limit rather than producing a natural EOS.
 */
object FinishReason {

  const val STOP = "stop"
  const val LENGTH = "length"
  const val TOOL_CALLS = "tool_calls"
  // Used by the Anthropic /v1/messages response shape — maps to the OAI "stop"
  // finish reason for the OAI-shape endpoints, but Anthropic clients distinguish
  // an explicit stop_sequence match from a natural end-of-turn.
  const val STOP_SEQUENCE = "stop_sequence"

  // Token estimation is charLength/4 which can under/overcount.
  // 5% tolerance avoids false negatives at the boundary.
  private const val TOLERANCE = 0.95

  fun infer(completionTokens: Int, maxTokens: Int?): String {
    if (maxTokens == null || maxTokens <= 0) return STOP
    if (completionTokens <= 0) return STOP
    val threshold = (maxTokens * TOLERANCE).toInt()
    return if (completionTokens >= threshold) LENGTH else STOP
  }

  /**
   * Like [infer], but reports [STOP_SEQUENCE] when the streaming truncator matched
   * a configured stop string. Length-vs-stop precedence is unchanged — stop_sequence
   * only takes priority over a natural STOP, never over LENGTH (a model that hit
   * max_tokens and happened to spell out a stop sequence is still length-bound).
   */
  fun inferWithStopSequence(completionTokens: Int, maxTokens: Int?, stopSequenceTriggered: Boolean): String {
    val base = infer(completionTokens, maxTokens)
    return if (base == STOP && stopSequenceTriggered) STOP_SEQUENCE else base
  }
}
