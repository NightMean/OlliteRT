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

package com.ollitert.llm.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import javax.inject.Inject
import javax.inject.Singleton

// Shares app foreground/background state between the Activity (which sets it)
// and the foreground Service (which reads it to adjust notification behavior).
// Injected via Hilt rather than using ProcessLifecycleOwner so the Service
// doesn't depend on the lifecycle-process library.
@Singleton
class OlliteRTLifecycleProvider @Inject constructor() {
  private val _isAppInForeground = MutableStateFlow(false)
  val appInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

  var isAppInForeground: Boolean
    get() = _isAppInForeground.value
    set(value) {
      _isAppInForeground.value = value
    }
}
