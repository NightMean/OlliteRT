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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerNetworkAccessTest {

  @Test
  fun bindModesResolveToExpectedHosts() {
    assertEquals(
      BindAddressResult.Valid("0.0.0.0"),
      ServerBindConfig(ServerBindMode.ALL_INTERFACES).resolveHost(),
    )
    assertEquals(
      BindAddressResult.Valid("127.0.0.1"),
      ServerBindConfig(ServerBindMode.LOOPBACK).resolveHost(),
    )
    assertEquals(
      BindAddressResult.Valid("192.168.1.20"),
      ServerBindConfig(ServerBindMode.CUSTOM, " 192.168.1.20 ").resolveHost(),
    )
  }

  @Test
  fun customBindRejectsHostnamesAndMalformedAddresses() {
    assertInstance<BindAddressResult.Invalid>(
      ServerBindConfig(ServerBindMode.CUSTOM, "example.com").resolveHost(),
    )
    assertInstance<BindAddressResult.Invalid>(
      ServerBindConfig(ServerBindMode.CUSTOM, "192.168.1.999").resolveHost(),
    )
  }

  @Test
  fun advertisedHostReflectsListenerReachability() {
    val allInterfaces = ServerBindConfig(ServerBindMode.ALL_INTERFACES)
    assertEquals("192.168.1.44", allInterfaces.advertisedHost("192.168.1.44", "0.0.0.0"))
    assertFalse(allInterfaces.isLoopbackOnly("192.168.1.44"))
    assertEquals("localhost", allInterfaces.advertisedHost(null, "0.0.0.0"))
    assertTrue(allInterfaces.isLoopbackOnly(null))

    val loopback = ServerBindConfig(ServerBindMode.LOOPBACK)
    assertEquals("localhost", loopback.advertisedHost("192.168.1.44", "127.0.0.1"))
    assertTrue(loopback.isLoopbackOnly("192.168.1.44"))

    val custom = ServerBindConfig(ServerBindMode.CUSTOM, "10.0.0.7")
    assertEquals("10.0.0.7", custom.advertisedHost("192.168.1.44", "10.0.0.7"))
    assertFalse(custom.isLoopbackOnly(null))
  }

  @Test
  fun allowOnlyMatchesExactAddressesAndCidrs() {
    val policy = compilePolicy(
      ClientIpPolicyMode.ALLOW_ONLY,
      "192.168.1.25\n10.20.0.0/16\n2001:db8::/32",
    )

    assertTrue(policy.allows("192.168.1.25"))
    assertTrue(policy.allows("10.20.200.8"))
    assertTrue(policy.allows("2001:db8::42"))
    assertFalse(policy.allows("192.168.1.26"))
    assertFalse(policy.allows("10.21.0.1"))
    assertFalse(policy.allows("2001:db9::1"))
  }

  @Test
  fun blockListedRejectsMatchesAndAllowsOtherAddresses() {
    val policy = compilePolicy(
      ClientIpPolicyMode.BLOCK_LISTED,
      "192.168.50.0/24, ::1",
    )

    assertFalse(policy.allows("192.168.50.9"))
    assertFalse(policy.allows("::1"))
    assertTrue(policy.allows("192.168.51.9"))
    assertTrue(policy.allows("2001:db8::1"))
  }

  @Test
  fun activePolicyFailsClosedForUnparseableRemoteAddress() {
    assertFalse(compilePolicy(ClientIpPolicyMode.ALLOW_ONLY, "127.0.0.1").allows("client.example"))
    assertFalse(compilePolicy(ClientIpPolicyMode.BLOCK_LISTED, "127.0.0.1").allows("client.example"))
    assertTrue(ClientIpAccessPolicy.ALLOW_ALL.allows("client.example"))
  }

  @Test
  fun compilerRejectsEmptyAndMalformedActiveRules() {
    assertInstance<ClientIpPolicyCompileResult.EmptyRules>(
      ClientIpAccessPolicy.compile(ClientIpPolicyConfig(ClientIpPolicyMode.ALLOW_ONLY, "")),
    )
    val invalid = assertInstance<ClientIpPolicyCompileResult.InvalidRule>(
      ClientIpAccessPolicy.compile(
        ClientIpPolicyConfig(ClientIpPolicyMode.BLOCK_LISTED, "192.168.1.0/33"),
      ),
    )
    assertEquals(1, invalid.position)
    assertEquals("192.168.1.0/33", invalid.input)
  }

  @Test
  fun compilerNormalizesSeparatorsAndRemovesDuplicateNetworks() {
    val compiled = assertInstance<ClientIpPolicyCompileResult.Success>(
      ClientIpAccessPolicy.compile(
        ClientIpPolicyConfig(
          ClientIpPolicyMode.ALLOW_ONLY,
          "192.168.1.4, 192.168.1.4\r\n10.0.1.9/8\n10.200.0.1/8",
        ),
      ),
    )

    assertEquals(2, compiled.policy.ruleCount)
    assertEquals("192.168.1.4\n10.0.1.9/8", compiled.normalizedRulesText)
  }

  @Test
  fun urlFormattingBracketsIpv6Only() {
    assertEquals("192.168.1.20", formatHostForUrl("192.168.1.20"))
    assertEquals("[2001:db8::20]", formatHostForUrl("2001:db8::20"))
    assertEquals("[2001:db8::20]", formatHostForUrl("[2001:db8::20]"))
  }

  private fun compilePolicy(mode: ClientIpPolicyMode, rules: String): ClientIpAccessPolicy {
    val result = ClientIpAccessPolicy.compile(ClientIpPolicyConfig(mode, rules))
    return assertInstance<ClientIpPolicyCompileResult.Success>(result).policy
  }

  private inline fun <reified T> assertInstance(value: Any?): T {
    assertTrue("Expected ${T::class.java.simpleName}, received ${value?.javaClass?.simpleName}", value is T)
    return value as T
  }
}
