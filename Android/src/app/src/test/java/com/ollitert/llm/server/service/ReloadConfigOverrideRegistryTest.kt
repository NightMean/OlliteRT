/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ollitert.llm.server.service

import com.ollitert.llm.server.data.model.Model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReloadConfigOverrideRegistryTest {
  @Test
  fun rapidReloadsConsumeOnlyTheirOwnOverrides() {
    val registry = ReloadConfigOverrideRegistry()
    val requestA = registry.register("model-a", mapOf("temperature" to 0.2f))
    val requestB = registry.register("model-b", mapOf("temperature" to 0.8f))

    assertEquals("model-a", registry.take(requestA)?.modelName)
    assertEquals("model-b", registry.take(requestB)?.modelName)
    assertNull(registry.take(requestA))
  }

  @Test
  fun overridesApplyOnlyToTheModelOwnedByTheRequest() {
    val overrides = ReloadConfigOverrides("requested", mapOf("topK" to 12))
    val fallbackModel = Model(name = "fallback", configValues = mapOf("topK" to 40))
    val requestedModel = Model(name = "requested", configValues = mapOf("topK" to 40))

    assertFalse(applyReloadConfigOverrides(fallbackModel, overrides))
    assertEquals(40, fallbackModel.configValues["topK"])
    assertTrue(applyReloadConfigOverrides(requestedModel, overrides))
    assertEquals(12, requestedModel.configValues["topK"])
  }
}
