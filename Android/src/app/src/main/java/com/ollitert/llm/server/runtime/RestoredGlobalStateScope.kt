/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.ollitert.llm.server.runtime

/** Serializes temporary process-global state and restores it after every outcome. */
internal class RestoredGlobalStateScope<State> {
  private val ownershipLock = Any()

  fun <Value> run(
    capture: () -> State,
    restore: (State) -> Unit,
    action: () -> Value,
  ): Value = synchronized(ownershipLock) {
    val previousState = capture()
    try {
      action()
    } finally {
      restore(previousState)
    }
  }
}
