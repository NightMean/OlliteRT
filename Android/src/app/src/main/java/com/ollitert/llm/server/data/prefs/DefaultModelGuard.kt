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

package com.ollitert.llm.server.data.prefs

import java.util.Locale

/** Process-local protection for stale Settings screens during model removal. */
internal object DefaultModelGuard {
  private val unavailable = mutableSetOf<String>()

  @Synchronized fun beginRemoval(name: String) { unavailable.add(name.lowercase(Locale.ROOT)) }
  @Synchronized fun finishRemoval(name: String, remainsUsable: Boolean) {
    if (remainsUsable) unavailable.remove(name.lowercase(Locale.ROOT))
  }
  @Synchronized fun assign(name: String?, write: () -> Unit): Boolean {
    if (name != null && name.lowercase(Locale.ROOT) in unavailable) return false
    write()
    return true
  }
}
