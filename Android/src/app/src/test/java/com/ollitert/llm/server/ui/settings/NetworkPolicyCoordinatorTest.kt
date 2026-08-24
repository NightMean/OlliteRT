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

package com.ollitert.llm.server.ui.settings

import android.content.Context
import com.ollitert.llm.server.data.prefs.ClientIpPolicyMode
import com.ollitert.llm.server.data.prefs.ServerBindMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkPolicyCoordinatorTest {

  private lateinit var context: Context
  private lateinit var coordinator: NetworkPolicyCoordinator

  @Before
  fun setUp() {
    context = mockk(relaxed = true)
    // Error messages are localized strings — assert presence, not exact text
    every { context.getString(any()) } returns "error"
    coordinator = NetworkPolicyCoordinator(context)
  }

  @Test
  fun `buildConfig succeeds with valid rules and normalizes rules text`() {
    val result = coordinator.buildConfig(
      bindModePref = ServerBindMode.LOOPBACK.preferenceValue,
      customAddress = "",
      policyModePref = ClientIpPolicyMode.ALLOW_ONLY.preferenceValue,
      rulesText = "192.168.1.10",
    )
    assertTrue(result is NetworkPolicyCoordinator.BuildResult.Success)
    val config = (result as NetworkPolicyCoordinator.BuildResult.Success).config
    assertEquals(ServerBindMode.LOOPBACK, config.bindConfig.mode)
    assertEquals(ClientIpPolicyMode.ALLOW_ONLY, config.policyConfig.mode)
    assertEquals(ClientIpPolicyMode.ALLOW_ONLY, config.compiledPolicy.policy.mode)
    assertTrue(config.compiledPolicy.normalizedRulesText.isNotBlank())
  }

  @Test
  fun `buildConfig succeeds with empty rules in allow-all mode`() {
    val result = coordinator.buildConfig(
      bindModePref = null,
      customAddress = "",
      policyModePref = null,
      rulesText = "",
    )
    assertTrue(result is NetworkPolicyCoordinator.BuildResult.Success)
  }

  @Test
  fun `buildConfig fails when allow-only mode has empty rules`() {
    val result = coordinator.buildConfig(
      bindModePref = null,
      customAddress = "",
      policyModePref = ClientIpPolicyMode.ALLOW_ONLY.preferenceValue,
      rulesText = "",
    )
    assertTrue(result is NetworkPolicyCoordinator.BuildResult.Invalid)
    assertNotNull((result as NetworkPolicyCoordinator.BuildResult.Invalid).message)
  }

  @Test
  fun `validateRulesText accepts clean rules and rejects broken ones`() {
    assertNull(coordinator.validateRulesText(ClientIpPolicyMode.ALLOW_ONLY.preferenceValue, "192.168.1.10"))
    assertNotNull(coordinator.validateRulesText(ClientIpPolicyMode.ALLOW_ONLY.preferenceValue, ""))
    assertNotNull(coordinator.validateRulesText(ClientIpPolicyMode.BLOCK_LISTED.preferenceValue, "not-an-ip"))
  }
}
