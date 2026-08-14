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

package com.ollitert.llm.server.service.routes

import com.ollitert.llm.server.service.KtorServer
import com.ollitert.llm.server.service.PayloadBuilders
import com.ollitert.llm.server.service.httpNotFound
import com.ollitert.llm.server.service.httpOkJson
import io.ktor.server.application.call
import io.ktor.server.request.uri
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

/**
 * Model listing and model detail route definitions.
 */
internal fun Routing.modelRoutes(server: KtorServer) {
  with(server) {
    get("/v1/models") { handleModelsList(call) }
    get("/debug/models") { handleModelsList(call) }

    get("/v1/models/{id...}") {
      if (!requireAuth(call)) return@get
      withGetLogging(call) {
        val body = PayloadBuilders.modelDetail(
          defaultModel, call.request.uri, json, keepAliveUnloadedModelName,
        )
        if (body != null) httpOkJson(body)
        else httpNotFound("model_not_found")
      }
    }
  }
}
