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

package com.ollitert.llm.server.ui.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OlliteRTRouteTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun testBenchmarkRouteSerialization() {
    val route = OlliteRTRoute.Benchmark(modelName = "gemma-2b-it")
    val encoded = json.encodeToString(route)
    val decoded = json.decodeFromString<OlliteRTRoute.Benchmark>(encoded)
    assertEquals("gemma-2b-it", decoded.modelName)
  }

  @Test
  fun testRepositoryDetailRouteSerialization() {
    val route = OlliteRTRoute.RepositoryDetail(repoId = "custom-repo-123")
    val encoded = json.encodeToString(route)
    val decoded = json.decodeFromString<OlliteRTRoute.RepositoryDetail>(encoded)
    assertEquals("custom-repo-123", decoded.repoId)
  }

  @Test
  fun testTabRoutesMatchDeclaredRoutes() {
    assertEquals(OlliteRTRoute.Models, OlliteRTTab.Models.route)
    assertEquals(OlliteRTRoute.Status, OlliteRTTab.Status.route)
    assertEquals(OlliteRTRoute.Logs, OlliteRTTab.Logs.route)
  }
}
