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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class ReloadConfigOverrides(
  val modelName: String?,
  val values: Map<String, Any>,
)

/** Owns reload-only config until the matching service intent claims it. */
internal class ReloadConfigOverrideRegistry {
  private val pending = ConcurrentHashMap<String, ReloadConfigOverrides>()

  fun register(modelName: String?, values: Map<String, Any>): String {
    val requestId = UUID.randomUUID().toString()
    pending[requestId] = ReloadConfigOverrides(modelName, values.toMap())
    return requestId
  }

  fun take(requestId: String?): ReloadConfigOverrides? {
    return requestId?.let(pending::remove)
  }

  fun discard(requestId: String?) {
    requestId?.let(pending::remove)
  }
}

internal fun applyReloadConfigOverrides(
  model: Model,
  overrides: ReloadConfigOverrides,
): Boolean {
  if (overrides.modelName != null && overrides.modelName != model.name) return false
  model.configValues = overrides.values.toMap()
  return true
}
