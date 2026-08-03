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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OlliteRTLifecycleProviderTest {

  @Test
  fun `extra stop cannot underflow the next foreground transition`() {
    val provider = OlliteRTLifecycleProvider()

    provider.onActivityStopped()
    provider.onActivityStarted()

    assertTrue(provider.isAppInForeground.value)

    provider.onActivityStopped()

    assertFalse(provider.isAppInForeground.value)
  }

  @Test
  fun `app stays foreground until the last started activity stops`() {
    val provider = OlliteRTLifecycleProvider()

    provider.onActivityStarted()
    provider.onActivityStarted()
    provider.onActivityStopped()

    assertTrue(provider.isAppInForeground.value)

    provider.onActivityStopped()

    assertFalse(provider.isAppInForeground.value)
  }
}
