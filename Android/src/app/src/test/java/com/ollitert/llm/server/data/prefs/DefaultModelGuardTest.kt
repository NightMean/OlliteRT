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

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class DefaultModelGuardTest {
  @Test fun removalRejectsStaleAssignmentsUntilAUsableModelIsConfirmed() {
    val name = "model-${UUID.randomUUID()}"
    var selected: String? = null
    assertTrue(DefaultModelGuard.assign(name) { selected = name })
    DefaultModelGuard.beginRemoval(name)
    assertFalse(DefaultModelGuard.assign(name.uppercase()) { selected = "stale" })
    assertEquals(name, selected)
    assertTrue(DefaultModelGuard.assign("another") { selected = "another" })
    DefaultModelGuard.finishRemoval(name, false)
    assertFalse(DefaultModelGuard.assign(name) { selected = name })
    DefaultModelGuard.finishRemoval(name, true)
    assertTrue(DefaultModelGuard.assign(name) { selected = name })
    assertEquals(name, selected)
  }
}
