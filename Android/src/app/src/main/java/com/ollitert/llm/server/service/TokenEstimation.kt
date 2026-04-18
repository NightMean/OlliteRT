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
