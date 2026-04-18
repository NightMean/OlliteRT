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

package com.ollitert.llm.server.ui.server.settings

/**
 * Validates CORS allowed origins input.
 * Valid formats: blank (disabled), "*" (allow all), or comma-separated origin URLs
 * with http:// or https:// scheme.
 */
fun isValidCorsOrigins(input: String): Boolean {
  val trimmed = input.trim()
  if (trimmed.isEmpty() || trimmed == "*") return true
  return trimmed.split(",").all { entry ->
    val origin = entry.trim()
    origin.isNotEmpty() && (origin.startsWith("http://") || origin.startsWith("https://")) &&
      origin.substringAfter("://").let { host ->
        host.isNotEmpty() && !host.startsWith("/") && !host.contains(" ")
      }
  }
}
