// Copyright 2025-2026 @NightMean (https://github.com/NightMean)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ollitert.llm.server.service.routes

import com.ollitert.llm.server.service.KtorServer
import io.ktor.server.application.call
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post

/**
 * Server lifecycle and configuration control routes.
 */
internal fun Routing.serverControlRoutes(server: KtorServer) {
  with(server) {
    post("/v1/server/stop") {
      if (!requireServerControl(call)) return@post
      withRequestLogging(call) { _, _, _, _, _, _ ->
        handleServerStop()
      }
    }

    post("/v1/server/reload") {
      if (!requireServerControl(call)) return@post
      withRequestLogging(call) { _, _, _, _, _, _ ->
        handleServerReload()
      }
    }

    post("/v1/server/thinking") {
      if (!requireServerControl(call)) return@post
      withRequestLogging(call) { body, _, _, _, _, _ ->
        handleServerThinking(body)
      }
    }

    post("/v1/server/config") {
      if (!requireServerControl(call)) return@post
      withRequestLogging(call) { body, _, _, _, _, _ ->
        handleServerConfig(body)
      }
    }
  }
}
