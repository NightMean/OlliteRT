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

package com.ollitert.llm.server.service

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

  // Token estimation is charLength/4 which can under/overcount.
  // 5% tolerance avoids false negatives at the boundary.
  private const val TOLERANCE = 0.95

  fun infer(completionTokens: Int, maxTokens: Int?): String {
    if (maxTokens == null || maxTokens <= 0) return STOP
    if (completionTokens <= 0) return STOP
    val threshold = (maxTokens * TOLERANCE).toInt()
    return if (completionTokens >= threshold) LENGTH else STOP
  }
}
