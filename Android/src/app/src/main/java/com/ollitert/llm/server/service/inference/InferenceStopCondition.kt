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

import com.ollitert.llm.server.service.http.InferenceRequest

/**
 * Handles stop sequences matching and response format instruction injection.
 */
internal object InferenceStopCondition {

  /**
   * Returns true if [accumulated] text ends with any of the specified [stopSequences].
   */
  fun matchesStopSequence(accumulated: String, stopSequences: List<String>?): Boolean {
    if (stopSequences.isNullOrEmpty()) return false
    for (stop in stopSequences) {
      if (stop.isNotEmpty() && accumulated.endsWith(stop)) {
        return true
      }
    }
    return false
  }

  /**
   * Trims the matched stop sequence from the end of the text if present.
   */
  fun trimStopSequence(text: String, stopSequences: List<String>?): String {
    if (stopSequences.isNullOrEmpty()) return text
    for (stop in stopSequences) {
      if (stop.isNotEmpty() && text.endsWith(stop)) {
        return text.dropLast(stop.length)
      }
    }
    return text
  }

  /**
   * Injects JSON schema or format constraints into the prompt if required by [request].
   */
  fun applyResponseFormat(prompt: String, request: InferenceRequest): String {
    val schema = request.responseFormatSchema ?: return prompt
    val schemaInstruction = "\n\nYour output must strictly conform to the following JSON schema:\n```json\n$schema\n```\nRespond with valid JSON only."
    return prompt + schemaInstruction
  }
}
