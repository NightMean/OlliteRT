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

import java.net.InetAddress

/** Stable values persisted for the HTTP listener's network exposure. */
enum class ServerBindMode(val preferenceValue: String) {
  ALL_INTERFACES("all_interfaces"),
  LOOPBACK("loopback"),
  CUSTOM("custom");

  companion object {
    fun fromPreference(value: String?): ServerBindMode =
      entries.firstOrNull { it.preferenceValue == value } ?: ALL_INTERFACES
  }
}

/** Stable values persisted for application-level client IP filtering. */
enum class ClientIpPolicyMode(val preferenceValue: String) {
  ALLOW_ALL("allow_all"),
  ALLOW_ONLY("allow_only"),
  BLOCK_LISTED("block_listed");

  companion object {
    fun fromPreference(value: String?): ClientIpPolicyMode =
      entries.firstOrNull { it.preferenceValue == value } ?: ALLOW_ALL
  }
}

data class ServerBindConfig(
  val mode: ServerBindMode = ServerBindMode.ALL_INTERFACES,
  val customAddress: String = "",
)

sealed class BindAddressResult {
  data class Valid(val host: String) : BindAddressResult()
  data class Invalid(val input: String) : BindAddressResult()
}

/** Resolves a persisted bind configuration to the exact host passed to both socket bind sites. */
fun ServerBindConfig.resolveHost(): BindAddressResult = when (mode) {
  ServerBindMode.ALL_INTERFACES -> BindAddressResult.Valid("0.0.0.0")
  ServerBindMode.LOOPBACK -> BindAddressResult.Valid("127.0.0.1")
  ServerBindMode.CUSTOM -> {
    val trimmed = customAddress.trim()
    if (parseIpLiteral(trimmed) != null) BindAddressResult.Valid(trimmed)
    else BindAddressResult.Invalid(customAddress)
  }
}

/** Brackets IPv6 literals when inserting a host into an HTTP URL. */
fun formatHostForUrl(host: String): String =
  if (':' in host && !(host.startsWith('[') && host.endsWith(']'))) "[$host]" else host

data class ClientIpPolicyConfig(
  val mode: ClientIpPolicyMode = ClientIpPolicyMode.ALLOW_ALL,
  val rulesText: String = "",
)

sealed class ClientIpPolicyCompileResult {
  data class Success(
    val policy: ClientIpAccessPolicy,
    val normalizedRulesText: String,
  ) : ClientIpPolicyCompileResult()

  data class InvalidRule(
    val position: Int,
    val input: String,
  ) : ClientIpPolicyCompileResult()

  data object EmptyRules : ClientIpPolicyCompileResult()
}

/**
 * Immutable, precompiled access policy used on Ktor request threads.
 *
 * Rules accept exact IPv4/IPv6 literals and CIDR networks. Hostnames are intentionally rejected:
 * access decisions must not depend on DNS or perform network I/O during request admission.
 */
class ClientIpAccessPolicy private constructor(
  val mode: ClientIpPolicyMode,
  private val rules: List<IpNetwork>,
) {
  val ruleCount: Int get() = rules.size

  fun allows(remoteAddress: String): Boolean {
    if (mode == ClientIpPolicyMode.ALLOW_ALL) return true
    val addressBytes = parseIpLiteral(remoteAddress) ?: return false
    val matches = rules.any { it.matches(addressBytes) }
    return when (mode) {
      ClientIpPolicyMode.ALLOW_ALL -> true
      ClientIpPolicyMode.ALLOW_ONLY -> matches
      ClientIpPolicyMode.BLOCK_LISTED -> !matches
    }
  }

  companion object {
    val ALLOW_ALL = ClientIpAccessPolicy(ClientIpPolicyMode.ALLOW_ALL, emptyList())

    fun compile(config: ClientIpPolicyConfig): ClientIpPolicyCompileResult {
      if (config.mode == ClientIpPolicyMode.ALLOW_ALL) {
        return ClientIpPolicyCompileResult.Success(ALLOW_ALL, config.rulesText.trim())
      }

      val tokens = config.rulesText
        .split(Regex("[,\\r\\n]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
      if (tokens.isEmpty()) return ClientIpPolicyCompileResult.EmptyRules

      val uniqueRules = LinkedHashMap<String, Pair<IpNetwork, String>>()
      tokens.forEachIndexed { index, token ->
        val parsed = parseNetwork(token)
          ?: return ClientIpPolicyCompileResult.InvalidRule(index + 1, token)
        uniqueRules.putIfAbsent(parsed.canonicalKey, parsed to token)
      }

      return ClientIpPolicyCompileResult.Success(
        policy = ClientIpAccessPolicy(config.mode, uniqueRules.values.map { it.first }),
        normalizedRulesText = uniqueRules.values.joinToString("\n") { it.second },
      )
    }
  }
}

private class IpNetwork(
  private val networkBytes: ByteArray,
  private val prefixLength: Int,
) {
  val canonicalKey: String = buildString {
    networkBytes.forEach { append("%02x".format(it.toInt() and 0xff)) }
    append('/').append(prefixLength)
  }

  fun matches(addressBytes: ByteArray): Boolean {
    if (addressBytes.size != networkBytes.size) return false
    val fullBytes = prefixLength / 8
    for (index in 0 until fullBytes) {
      if (addressBytes[index] != networkBytes[index]) return false
    }
    val remainingBits = prefixLength % 8
    if (remainingBits == 0) return true
    val mask = (0xff shl (8 - remainingBits)) and 0xff
    return (addressBytes[fullBytes].toInt() and mask) ==
      (networkBytes[fullBytes].toInt() and mask)
  }
}

private fun parseNetwork(input: String): IpNetwork? {
  val slashIndex = input.indexOf('/')
  if (slashIndex != input.lastIndexOf('/')) return null
  val addressText = if (slashIndex >= 0) input.substring(0, slashIndex).trim() else input
  val addressBytes = parseIpLiteral(addressText) ?: return null
  val maxPrefix = addressBytes.size * 8
  val prefixLength = if (slashIndex >= 0) {
    input.substring(slashIndex + 1).trim().toIntOrNull()?.takeIf { it in 0..maxPrefix }
      ?: return null
  } else {
    maxPrefix
  }

  val networkBytes = addressBytes.copyOf()
  val fullBytes = prefixLength / 8
  val remainingBits = prefixLength % 8
  if (remainingBits != 0) {
    val mask = (0xff shl (8 - remainingBits)) and 0xff
    networkBytes[fullBytes] = (networkBytes[fullBytes].toInt() and mask).toByte()
  }
  for (index in (fullBytes + if (remainingBits == 0) 0 else 1) until networkBytes.size) {
    networkBytes[index] = 0
  }
  return IpNetwork(networkBytes, prefixLength)
}

/** Parses numeric IP literals only; inputs that could trigger a DNS lookup are rejected first. */
private fun parseIpLiteral(input: String): ByteArray? {
  val value = input.trim()
  if (value.isEmpty() || '%' in value) return null

  if (':' !in value) {
    val octets = value.split('.')
    if (octets.size != 4) return null
    return ByteArray(4) { index ->
      val octet = octets[index]
      if (octet.isEmpty() || octet.any { !it.isDigit() }) return null
      val numeric = octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
      numeric.toByte()
    }
  }

  if (value.any { it !in "0123456789abcdefABCDEF:." }) return null
  return try {
    InetAddress.getByName(value).address
  } catch (_: IllegalArgumentException) {
    null
  } catch (_: java.net.UnknownHostException) {
    null
  }
}
