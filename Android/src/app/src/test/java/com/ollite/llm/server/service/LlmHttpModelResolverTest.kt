package com.ollite.llm.server.service

import com.ollite.llm.server.data.AllowedModel
import com.ollite.llm.server.data.DefaultConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LlmHttpModelResolverTest {

  @Test
  fun picksGemmaAsDefaultWhenPresent() {
    val selected =
      LlmHttpModelResolver.pickDefaultAllowedModel(
        listOf(allowedModel(name = "Other", taskTypes = listOf("llm_chat")), allowedModel(name = "Gemma3-1B-IT", taskTypes = listOf("llm_chat")))
      )

    assertEquals("Gemma3-1B-IT", selected?.name)
  }

  @Test
  fun fallsBackToFirstLlmModelWhenGemmaMissing() {
    val selected =
      LlmHttpModelResolver.pickDefaultAllowedModel(
        listOf(allowedModel(name = "VisionOnly", taskTypes = listOf("vision")), allowedModel(name = "ChatModel", taskTypes = listOf("llm_chat")))
      )

    assertEquals("ChatModel", selected?.name)
  }

  @Test
  fun returnsNullWhenRequestedModelMapsToDefaultSelection() {
    assertNull(LlmHttpModelResolver.selectAllowedModel(emptyList(), null))
    assertNull(LlmHttpModelResolver.selectAllowedModel(emptyList(), "default"))
    assertNull(LlmHttpModelResolver.selectAllowedModel(emptyList(), "local"))
  }

  @Test
  fun matchesRequestedModelByNameOrModelId() {
    val allowlist = listOf(allowedModel(name = "Gemma3-1B-IT", modelId = "litert-community/Gemma3-1B-IT"))

    assertEquals("Gemma3-1B-IT", LlmHttpModelResolver.selectAllowedModel(allowlist, "gemma3-1b-it")?.name)
    assertEquals("Gemma3-1B-IT", LlmHttpModelResolver.selectAllowedModel(allowlist, "litert community gemma3 1b it")?.name)
  }

  private fun allowedModel(name: String, modelId: String = name, taskTypes: List<String> = listOf("llm_chat")) =
    AllowedModel(
      name = name,
      modelId = modelId,
      modelFile = "$name.litertlm",
      description = "test",
      sizeInBytes = 1,
      defaultConfig = DefaultConfig(),
      taskTypes = taskTypes,
    )
}
