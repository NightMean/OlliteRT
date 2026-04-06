package com.ollite.llm.server.data

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
