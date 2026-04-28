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

import com.ollitert.llm.server.data.BLOCKING_TIMEOUT_SECONDS
import com.ollitert.llm.server.data.RequestPrefsSnapshot

data class InferenceRequest(
  val prompt: String,
  val requestId: String,
  val endpoint: String,
  val timeoutSeconds: Long = BLOCKING_TIMEOUT_SECONDS,
  val images: List<ByteArray> = emptyList(),
  val audioClips: List<ByteArray> = emptyList(),
  val eagerVisionInit: Boolean = false,
  val logId: String? = null,
  val configSnapshot: Map<String, Any>? = null,
  val prefs: RequestPrefsSnapshot? = null,
)
