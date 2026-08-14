/*
 * Copyright 2025 Google LLC
 * Modifications Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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

package com.ollitert.llm.server.common

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/**
 * Trims verbose LiteRT native stack trace suffixes from error messages for user presentation.
 */
fun cleanUpLiteRtErrorMessage(message: String): String =
  message.substringBefore("=== Source Location Trace")

/** Format a byte count as a human-readable string (e.g. "1.5 kB", "3.20 GB"). */
fun Long.humanReadableSize(si: Boolean = true, extraDecimalForGbAndAbove: Boolean = false): String {
  val bytes = this
  val unit = if (si) 1000 else 1024
  if (bytes < unit) return "$bytes B"
  val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
  val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
  val formatString = if (extraDecimalForGbAndAbove && pre.lowercase() != "k" && pre != "M") "%.2f %sB" else "%.1f %sB"
  return String.format(Locale.US, formatString, bytes / unit.toDouble().pow(exp.toDouble()), pre)
}

/** Int overload for contexts where sizes come as Int (e.g. String.length). */
fun Int.humanReadableSize(si: Boolean = true, extraDecimalForGbAndAbove: Boolean = false): String =
  toLong().humanReadableSize(si, extraDecimalForGbAndAbove)
