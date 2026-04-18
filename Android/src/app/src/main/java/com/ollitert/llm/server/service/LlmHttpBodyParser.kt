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

data class LlmHttpParsedBody(
  val body: String,
  val bodyLength: Int,
)

/**
 * Parses and measures HTTP request bodies.
 *
 * No body size limit is enforced — multimodal requests carry base64-encoded images
 * that routinely exceed 1 MB. The original 512 KB limit from Google's reference
 * Gallery app was designed for text-only requests and blocked legitimate vision payloads.
 */
object LlmHttpBodyParser {
  fun parse(postData: String?): LlmHttpParsedBody? {
    val body = postData ?: return null
    return LlmHttpParsedBody(body = body, bodyLength = body.length)
  }
}
