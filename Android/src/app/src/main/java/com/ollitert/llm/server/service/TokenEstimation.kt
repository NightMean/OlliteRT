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

import com.ollitert.llm.server.data.CHARS_PER_TOKEN_ESTIMATE

/** Rough token estimate based on [CHARS_PER_TOKEN_ESTIMATE]. */
fun estimateTokens(text: String): Int =
  estimateTokensByLength(text.length)

/** Estimate tokens from a pre-computed character length. */
fun estimateTokensByLength(charLength: Int): Int =
  (charLength / CHARS_PER_TOKEN_ESTIMATE).coerceAtLeast(if (charLength > 0) 1 else 0)

/** Long variant for metrics counters that accumulate token counts. */
fun estimateTokensLong(text: String): Long =
  estimateTokens(text).toLong()

/** Long variant from a pre-computed character length. */
fun estimateTokensLongByLength(charLength: Int): Long =
  estimateTokensByLength(charLength).toLong()
