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
import android.util.Log
import com.ollitert.llm.server.R
import com.ollitert.llm.server.common.getWifiIpAddress
import com.ollitert.llm.server.data.prefs.BindAddressResult
import com.ollitert.llm.server.data.prefs.ClientIpAccessPolicy
import com.ollitert.llm.server.data.prefs.ClientIpPolicyCompileResult
import com.ollitert.llm.server.data.prefs.LOG_ERROR_PREVIEW_LONG_CHARS
import com.ollitert.llm.server.data.prefs.ServerPrefs
import com.ollitert.llm.server.data.prefs.advertisedHost
import com.ollitert.llm.server.data.prefs.isLoopbackOnly
import com.ollitert.llm.server.data.prefs.resolveHost
import java.io.IOException
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket

private const val TAG = "OlliteRT.Network"

internal data class ServerNetworkConfig(
  val bindHost: String,
  val accessPolicy: ClientIpAccessPolicy,
  val advertisedHost: String,
  val isLoopbackOnly: Boolean,
)

/**
 * Encapsulates network configuration resolution, client IP policies, and pre-flight socket probes.
 */
internal object ServerNetworkMonitor {

  /**
   * Resolves the server bind configuration and access policy from preferences.
   * Returns [ServerNetworkConfig] on success, or an error message string on failure.
   */
  fun resolveConfig(context: Context): Pair<ServerNetworkConfig?, String?> {
    val bindConfig = ServerPrefs.getServerBindConfig(context)
    val bindHost = when (val resolved = bindConfig.resolveHost()) {
      is BindAddressResult.Valid -> resolved.host
      is BindAddressResult.Invalid -> {
        return null to context.getString(R.string.error_invalid_bind_address, resolved.input)
      }
    }
    val accessPolicy = when (
      val compiled = ClientIpAccessPolicy.compile(ServerPrefs.getClientIpPolicyConfig(context))
    ) {
      is ClientIpPolicyCompileResult.Success -> compiled.policy
      is ClientIpPolicyCompileResult.EmptyRules,
      is ClientIpPolicyCompileResult.InvalidRule -> {
        return null to context.getString(R.string.error_invalid_client_ip_policy)
      }
    }
    val wifiIp = getWifiIpAddress(context)
    val advertisedHost = bindConfig.advertisedHost(wifiIp, bindHost)
    val isLoopbackOnly = bindConfig.isLoopbackOnly(wifiIp)

    return ServerNetworkConfig(
      bindHost = bindHost,
      accessPolicy = accessPolicy,
      advertisedHost = advertisedHost,
      isLoopbackOnly = isLoopbackOnly,
    ) to null
  }

  /**
   * Pre-flight bind test: Ktor's CIO engine binds the socket asynchronously inside a
   * background coroutine, so a BindException ("Address already in use") thrown during
   * Ktor start propagates as an uncaught FATAL on a Dispatchers.IO worker — the outer
   * try/catch around server.start() never sees it. This typically happens when another
   * installed flavor (dev/beta/stable) is already serving on the same port. Probe the
   * socket synchronously first so we can fail cleanly with the expected error event.
   *
   * @return null on success, or a human-readable failure reason if unavailable.
   */
  fun probePortBind(context: Context, bindHost: String, port: Int): String? {
    return try {
      ServerSocket().use { probe ->
        // SO_REUSEADDR must match what the real Ktor CIO server uses when it binds.
        // With reuseAddress=false the probe is STRICTER than the actual server: it
        // refuses to bind while a prior connection's socket lingers in TIME_WAIT
        // (which happens after stopping the server while a request was in flight),
        // producing a false "port in use" error even though no process is listening
        // and the server would bind fine. reuseAddress=true still throws BindException
        // when another process is actively LISTENing on the port (e.g. another flavor),
        // so genuine collisions are still detected.
        probe.reuseAddress = true
        probe.bind(InetSocketAddress(bindHost, port))
      }
      null
    } catch (e: IOException) {
      val reason = if (e is BindException || e.message?.contains("Address already in use") == true) {
        context.getString(R.string.error_port_in_use, port)
      } else {
        e.message?.take(LOG_ERROR_PREVIEW_LONG_CHARS) ?: context.getString(R.string.error_unknown)
      }
      Log.e(TAG, "Pre-flight bind probe failed on $bindHost:$port: $reason", e)
      context.getString(R.string.error_server_failed_to_start, reason)
    }
  }
}
