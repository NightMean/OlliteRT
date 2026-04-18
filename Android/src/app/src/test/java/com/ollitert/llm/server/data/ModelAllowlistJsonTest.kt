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

import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAllowlistJsonTest {

  @Test
  fun decodesAllowlistWithKnownFields() {
    val json =
      """
      {
        "models": [
          {
            "name": "Gemma3-1B-IT",
            "modelId": "litert-community/Gemma3-1B-IT",
            "modelFile": "Gemma3-1B-IT.litertlm",
            "description": "test",
            "sizeInBytes": 123,
            "version": "main",
            "defaultConfig": {
              "topK": 40,
              "topP": 0.95,
              "temperature": 0.7,
              "accelerators": "gpu,cpu",
              "maxContextLength": 8192,
              "maxTokens": 2048
            },
            "taskTypes": ["llm_chat"],
            "llmSupportThinking": true
          }
        ]
      }
      """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)

    assertEquals(1, allowlist.models.size)
    assertEquals("Gemma3-1B-IT", allowlist.models.first().name)
    assertEquals(40, allowlist.models.first().defaultConfig.topK)
    assertTrue(allowlist.models.first().llmSupportThinking == true)
  }

  @Test
  fun ignoresUnknownKeys() {
    val json =
      """
      {
        "models": [
          {
            "name": "Gemma3-1B-IT",
            "modelId": "litert-community/Gemma3-1B-IT",
            "modelFile": "Gemma3-1B-IT.litertlm",
            "description": "test",
            "sizeInBytes": 123,
            "defaultConfig": {},
            "taskTypes": ["llm_chat"],
            "extraField": "ignored"
          }
        ],
        "topLevelExtra": true
      }
      """.trimIndent()

    val allowlist = ModelAllowlistJson.decode(json)

    assertEquals(1, allowlist.models.size)
    assertEquals("Gemma3-1B-IT", allowlist.models.first().name)
  }

  @Test(expected = JsonSyntaxException::class)
  fun rejectsMalformedJson() {
    ModelAllowlistJson.decode("""{"models":[{"name":"broken"}""")
  }
}
