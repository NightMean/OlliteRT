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

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
internal fun supportsRuntimeNotificationPermission(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
  sdkInt >= Build.VERSION_CODES.TIRAMISU

/** Platform SoC identifier in lowercase; unknown before Android 12, never guessed from board names. */
val SOC: String by lazy {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    Build.SOC_MODEL.lowercase()
  } else {
    Build.UNKNOWN
  }
}

fun isPixelDevice(): Boolean {
  return Build.MODEL != null && Build.MODEL.lowercase().contains("pixel")
}

fun isPixel10(): Boolean {
  return Build.MODEL != null && Build.MODEL.lowercase().contains("pixel 10")
}
