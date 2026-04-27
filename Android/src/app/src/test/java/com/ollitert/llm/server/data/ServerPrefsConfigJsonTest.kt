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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerPrefsConfigJsonTest {

  @Test
  fun roundtripPreservesTypes() {
    val input = mapOf<String, Any>(
      "temperature" to 0.8f,
      "max_tokens" to 1024,
      "top_k" to 40,
      "top_p" to 0.95,
      "thinking" to true,
      "label" to "test",
    )
    val json = encodeInferenceConfig(input)
    val output = decodeInferenceConfig(json)!!
    assertTrue((output["temperature"] as Number).toFloat() == 0.8f)
    assertTrue((output["max_tokens"] as Number).toInt() == 1024)
    assertTrue((output["top_k"] as Number).toInt() == 40)
    assertTrue((output["top_p"] as Number).toDouble() == 0.95)
    assertEquals(true, output["thinking"])
    assertEquals("test", output["label"])
  }

  @Test
  fun decodesLargeIntAsLong() {
    val json = """{"big":${Long.MAX_VALUE}}"""
    val output = decodeInferenceConfig(json)!!
    assertTrue(output["big"] is Long)
  }

  @Test
  fun returnsNullForInvalidJson() {
    assertNull(decodeInferenceConfig("{broken"))
  }

  @Test
  fun returnsNullForNull() {
    assertNull(decodeInferenceConfig(null))
  }
}
