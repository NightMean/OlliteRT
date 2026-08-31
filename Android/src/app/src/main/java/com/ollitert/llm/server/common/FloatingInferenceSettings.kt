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

package com.ollitert.llm.server.common

/** Immutable snapshot of the model values the REST control API can change. */
data class FloatingInferenceSettings(
  val temperature: Double? = null,
  val maxTokens: Int? = null,
  val topK: Int? = null,
  val topP: Double? = null,
  val thinkingEnabled: Boolean? = null,
  val thinkingBudget: Int? = null,
)
