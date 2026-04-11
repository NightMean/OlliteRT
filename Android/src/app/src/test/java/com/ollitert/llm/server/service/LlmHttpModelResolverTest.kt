package com.ollitert.llm.server.service

import com.ollitert.llm.server.data.AllowedModel
import com.ollitert.llm.server.data.DefaultConfig
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

  @Test
  fun normalizedKeysDistinguishSimilarModelNames() {
    // E2B and E4B must not be confused — this mismatch caused an OOM crash when
    // selectModel returned an uninitialized Model for the wrong variant.
    val e2bKey = LlmHttpBridgeUtils.normalizeModelKey("Gemma_4_E2B_it")
    val e4bKey = LlmHttpBridgeUtils.normalizeModelKey("Gemma_4_E4B_it")
    assert(e2bKey != e4bKey) { "E2B and E4B should have distinct normalized keys: $e2bKey vs $e4bKey" }

    // Verify various client-side name formats all normalize to the same key
    assertEquals(e2bKey, LlmHttpBridgeUtils.normalizeModelKey("gemma-4-e2b-it"))
    assertEquals(e2bKey, LlmHttpBridgeUtils.normalizeModelKey("Gemma 4 E2B IT"))
    assertEquals(e4bKey, LlmHttpBridgeUtils.normalizeModelKey("gemma-4-e4b-it"))
  }

  @Test
  fun selectAllowedModelDoesNotCrossMatchVariants() {
    // When E2B is loaded and client requests E4B, the resolver must NOT return E2B
    val allowlist = listOf(
      allowedModel(name = "Gemma_4_E2B_it"),
      allowedModel(name = "Gemma_4_E4B_it"),
    )

    val matchE2B = LlmHttpModelResolver.selectAllowedModel(allowlist, "Gemma_4_E2B_it")
    val matchE4B = LlmHttpModelResolver.selectAllowedModel(allowlist, "Gemma_4_E4B_it")
    assertEquals("Gemma_4_E2B_it", matchE2B?.name)
    assertEquals("Gemma_4_E4B_it", matchE4B?.name)

    // A completely unknown model returns null
    assertNull(LlmHttpModelResolver.selectAllowedModel(allowlist, "nonexistent-model"))
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
