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

package com.ollitert.llm.server.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelBadgeTest {

  @Test
  fun fromKeyReturnsBestOverall() {
    val badge = ModelBadge.fromKey("best_overall")
    assertNotNull(badge)
    assertTrue(badge is ModelBadge.BestOverall)
    assertEquals("best_overall", badge!!.key)
  }

  @Test
  fun fromKeyReturnsNew() {
    val badge = ModelBadge.fromKey("new")
    assertNotNull(badge)
    assertTrue(badge is ModelBadge.New)
  }

  @Test
  fun fromKeyReturnsFastest() {
    val badge = ModelBadge.fromKey("fastest")
    assertNotNull(badge)
    assertTrue(badge is ModelBadge.Fastest)
  }

  @Test
  fun fromKeyReturnsOtherForUnknown() {
    val badge = ModelBadge.fromKey("experimental")
    assertNotNull(badge)
    assertTrue(badge is ModelBadge.Other)
    assertEquals("experimental", badge!!.key)
  }

  @Test
  fun fromKeyReturnsNullForBlank() {
    assertNull(ModelBadge.fromKey(""))
    assertNull(ModelBadge.fromKey("  "))
  }

  @Test
  fun displayLabelFormatsUnderscoresToSpaces() {
    val badge = ModelBadge.fromKey("low_memory") as ModelBadge.Other
    assertEquals("Low Memory", badge.displayLabel)
  }

  @Test
  fun displayLabelHandlesSingleWord() {
    val badge = ModelBadge.fromKey("experimental") as ModelBadge.Other
    assertEquals("Experimental", badge.displayLabel)
  }
}
