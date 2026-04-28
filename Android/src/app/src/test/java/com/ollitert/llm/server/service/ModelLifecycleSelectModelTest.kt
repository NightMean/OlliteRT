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

import android.content.Context
import com.ollitert.llm.server.data.Model
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelLifecycleSelectModelTest {

  private lateinit var lifecycle: ModelLifecycle

  private val testModel = Model(name = "Gemma-4-E2B-it")

  @Before
  fun setUp() {
    val context = mockk<Context>(relaxed = true)
    val allowlistLoader = mockk<AllowlistLoader>(relaxed = true)
    lifecycle = ModelLifecycle(
      context = context,
      allowlistLoader = allowlistLoader,
    )
  }

  @Test
  fun selectModel_returnsOk_whenModelLoadedAndNoModelRequested() {
    lifecycle.defaultModel = testModel
    val result = lifecycle.selectModel(null)
    assertTrue(result is ModelLifecycle.ModelSelection.Ok)
    assertEquals(testModel, (result as ModelLifecycle.ModelSelection.Ok).model)
  }

  @Test
  fun selectModel_returnsOk_whenRequestedModelIsEmpty() {
    lifecycle.defaultModel = testModel
    val result = lifecycle.selectModel("")
    assertTrue(result is ModelLifecycle.ModelSelection.Ok)
  }

  @Test
  fun selectModel_returnsOk_whenRequestedModelIsLocal() {
    lifecycle.defaultModel = testModel
    val result = lifecycle.selectModel("local")
    assertTrue(result is ModelLifecycle.ModelSelection.Ok)
  }

  @Test
  fun selectModel_returnsOk_whenRequestedModelIsDefault() {
    lifecycle.defaultModel = testModel
    val result = lifecycle.selectModel("default")
    assertTrue(result is ModelLifecycle.ModelSelection.Ok)
  }

  @Test
  fun selectModel_returnsOk_whenRequestedModelMatchesNormalized() {
    lifecycle.defaultModel = testModel
    val result = lifecycle.selectModel("gemma_4_e2b_it")
    assertTrue(result is ModelLifecycle.ModelSelection.Ok)
  }

  @Test
  fun selectModel_returns400_whenRequestedModelDoesNotMatch() {
    lifecycle.defaultModel = testModel
    val result = lifecycle.selectModel("llama-3-8b")
    assertTrue(result is ModelLifecycle.ModelSelection.Error)
    assertEquals(400, (result as ModelLifecycle.ModelSelection.Error).statusCode)
    assertTrue(result.message.contains("not loaded"))
  }

  @Test
  fun selectModel_returns503_whenNoModelLoaded() {
    lifecycle.defaultModel = null
    val result = lifecycle.selectModel(null)
    assertTrue(result is ModelLifecycle.ModelSelection.Error)
    assertEquals(503, (result as ModelLifecycle.ModelSelection.Error).statusCode)
  }

  @Test
  fun selectModel_returns503_whenReloading() {
    lifecycle.defaultModel = testModel
    // Simulate reload state via reflection since isReloading setter is private
    val field = ModelLifecycle::class.java.getDeclaredField("isReloading")
    field.isAccessible = true
    field.set(lifecycle, true)

    val result = lifecycle.selectModel("Gemma-4-E2B-it")
    assertTrue(result is ModelLifecycle.ModelSelection.Error)
    assertEquals(503, (result as ModelLifecycle.ModelSelection.Error).statusCode)
    assertTrue(result.message.contains("reloading"))

    field.set(lifecycle, false)
  }
}
