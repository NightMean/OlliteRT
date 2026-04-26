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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointHandlersHelpersTest {

  // ── describeClientSamplerParams ─────────────────────────────────────────

  @Test
  fun describeAllNullReturnsNull() {
    assertNull(describeClientSamplerParams(null, null, null, null))
  }

  @Test
  fun describeTemperatureOnly() {
    assertEquals("temperature=0.5", describeClientSamplerParams(0.5, null, null, null))
  }

  @Test
  fun describeMultipleParams() {
    val result = describeClientSamplerParams(0.5, 0.9, 40, 1024)!!
    assertTrue(result.contains("temperature=0.5"))
    assertTrue(result.contains("top_p=0.9"))
    assertTrue(result.contains("top_k=40"))
    assertTrue(result.contains("max_tokens=1024"))
  }

  @Test
  fun describeTopPOnly() {
    assertEquals("top_p=0.95", describeClientSamplerParams(null, 0.95, null, null))
  }

  @Test
  fun describeMaxTokensOnly() {
    assertEquals("max_tokens=512", describeClientSamplerParams(null, null, null, 512))
  }
}
